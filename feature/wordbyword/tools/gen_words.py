# -*- coding: utf-8 -*-
"""
Builds words.db for the quran_android word-by-word feature.

Inputs (next to this script):
  db/ayahinfo_1260.db        madani glyph positions
  db/quran.ar.uthmani.v2.db  uthmani text (token order == Tanzil == Quranic Arabic Corpus)
  quran-morphology.txt       Quranic Arabic Corpus morphology (mustafa0x edition, Arabic script)
  wbw/ch_<n>_p<k>.json       quran.com API v4 verses with words (English gloss + transliteration)

Output: words.db (tables words, roots, meta) and a mismatch report on stdout.
"""
import glob, json, os, sqlite3, collections, re
from gen_divine_names import load_glyphs, align, is_word_token, norm

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "words.db")

# ----------------------------------------------------------------------------- morphology

def load_morphology():
    """(sura, ayah, word) -> dict(root, lemma, pos) taken from the stem segment."""
    words = collections.defaultdict(list)
    with open(os.path.join(HERE, "quran-morphology.txt"), encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 4:
                continue
            loc, form, tag, features = parts[0], parts[1], parts[2], parts[3]
            s, a, w, seg = (int(x) for x in loc.split(":"))
            feats = features.split("|")
            root = next((x[5:] for x in feats if x.startswith("ROOT:")), "")
            lemma = next((x[4:] for x in feats if x.startswith("LEM:")), "")
            is_affix = "PREF" in feats or "SUFF" in feats
            words[(s, a, w)].append(dict(form=form, tag=tag, root=root, lemma=lemma, affix=is_affix, feats=feats))
    result = {}
    for key, segs in words.items():
        stem = next((x for x in segs if x["root"]), None) or next((x for x in segs if not x["affix"]), None) or segs[0]
        # a few useful sub-features for display
        extra = [x for x in stem["feats"] if x in ("PN", "ADJ", "PERF", "IMPF", "IMPV", "PASS", "ACT", "PCPL", "VN", "1", "2", "3")]
        result[key] = dict(root=stem["root"], lemma=stem["lemma"], pos=stem["tag"], extra="|".join(extra))
    return result

# ----------------------------------------------------------------------------- quran.com words

def load_wbw():
    """(sura, ayah) -> list of dict(uthmani, imlaei, translit, gloss) in verse order (words only)."""
    verses = {}
    for path in glob.glob(os.path.join(HERE, "wbw", "ch_*_p*.json")):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for v in data["verses"]:
            s, a = (int(x) for x in v["verse_key"].split(":"))
            ws = []
            for w in sorted(v["words"], key=lambda x: x["position"]):
                if w.get("char_type_name") != "word":
                    continue
                raw = w.get("text_uthmani") or w.get("text") or ""
                ws.append(dict(
                    # drop pause marks / hizb signs so chips show the bare word
                    uthmani=re.sub("[ۖ-ۭ]", "", raw).strip(),
                    imlaei=w.get("text_imlaei") or "",
                    translit=(w.get("transliteration") or {}).get("text") or "",
                    gloss=((w.get("translation") or {}).get("text") or "").strip(),
                ))
            verses[(s, a)] = ws
    return verses

# ----------------------------------------------------------------------------- build

def main():
    glyphs = load_glyphs()
    v2 = sqlite3.connect(os.path.join(HERE, "db", "quran.ar.uthmani.v2.db"))
    ayahs = v2.execute("select sura,ayah,text from arabic_text order by sura,ayah").fetchall()
    ayah_ids = {(s, a): i for i, (s, a, _) in enumerate(ayahs, start=1)}
    morph = load_morphology()
    wbw = load_wbw()
    print("morphology words:", len(morph), " wbw verses:", len(wbw))

    if os.path.exists(OUT):
        os.remove(OUT)
    out = sqlite3.connect(OUT)
    out.executescript("""
    CREATE TABLE words(
      ayah_id INTEGER NOT NULL, sura INTEGER NOT NULL, ayah INTEGER NOT NULL,
      word INTEGER NOT NULL, position INTEGER NOT NULL,
      uthmani TEXT NOT NULL, translit TEXT NOT NULL, gloss_en TEXT NOT NULL, gloss_ar TEXT NOT NULL,
      root TEXT NOT NULL, lemma TEXT NOT NULL, pos TEXT NOT NULL, pos_extra TEXT NOT NULL,
      PRIMARY KEY(sura, ayah, word)
    );
    CREATE INDEX words_ayah_id ON words(ayah_id);
    CREATE INDEX words_root ON words(root);
    CREATE TABLE roots(root TEXT PRIMARY KEY, count INTEGER NOT NULL);
    CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
    """)

    stats = collections.Counter()
    root_counts = collections.Counter()
    rows = []
    for s, a, text in ayahs:
        toks = text.split()
        mapping, exact = align(toks, glyphs[(s, a)])
        word_positions = [mapping[i] for i, t in enumerate(toks) if is_word_token(t)]
        v2_words = [t for t in toks if is_word_token(t)]
        qc = wbw.get((s, a), [])
        n_morph = sum(1 for k in morph if k[0] == s and k[1] == a) if False else None  # computed below
        morph_words = [morph[(s, a, w)] for w in range(1, 400) if (s, a, w) in morph]

        n = len(qc)
        same_v2 = n == len(v2_words)
        same_morph = n == len(morph_words)
        stats["ayahs"] += 1
        if not same_v2:
            stats["mismatch_v2"] += 1
        if not same_morph:
            stats["mismatch_morph"] += 1
        if not exact:
            stats["fallback_align"] += 1

        for i, w in enumerate(qc):
            word = i + 1
            position = word_positions[i] if same_v2 and i < len(word_positions) and word_positions[i] else 0
            m = morph_words[i] if same_morph else dict(root="", lemma="", pos="", extra="")
            uth = w["uthmani"] if w["uthmani"] else (v2_words[i] if same_v2 else "")
            rows.append((ayah_ids[(s, a)], s, a, word, position, uth, w["translit"], w["gloss"], "",
                         m["root"], m["lemma"], m["pos"], m["extra"]))
            if m["root"]:
                root_counts[m["root"]] += 1
            if position == 0:
                stats["no_position"] += 1

    out.executemany("insert into words values(?,?,?,?,?,?,?,?,?,?,?,?,?)", rows)
    out.executemany("insert into roots values(?,?)", root_counts.items())
    out.execute("insert into meta values('version','1')")
    out.execute("insert into meta values('mushaf','madani')")
    out.execute("insert into meta values('sources','Quranic Arabic Corpus (morphology, GPL) via github.com/mustafa0x/quran-morphology; quran.com API v4 (word by word English, transliteration)')")
    out.commit()
    out.execute("VACUUM")
    out.close()
    print("words:", len(rows), "roots:", len(root_counts))
    print("stats:", dict(stats))
    print("db size:", os.path.getsize(OUT))

if __name__ == "__main__":
    main()

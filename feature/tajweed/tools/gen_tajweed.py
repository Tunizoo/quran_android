# -*- coding: utf-8 -*-
"""
Builds tajweed.db for the quran_android tajweed colouring feature.

Inputs:
  ../../divinenames/tools/db/ayahinfo_1260.db        madani glyph positions (word boxes)
  ../../divinenames/tools/db/quran.ar.uthmani.v2.db  uthmani text, used to align words to glyph positions
  tajweed/ch_<n>_p<k>.json                            quran.com API v4 verses with words and the
                                                      text_uthmani_tajweed field (<rule class=...> markup)

Output: tajweed.db with
  words(sura, ayah, position, text, spans)  text = the word without markup or pause marks,
                                            spans = "start-end:rule;..." in UTF-16 char offsets of text
  meta(key, value)
Words of ayahs whose quran.com word count differs from the uthmani text are skipped (the page image
is left untouched for them); the script prints how many.
"""
import glob, json, os, re, sqlite3, sys, collections

HERE = os.path.dirname(os.path.abspath(__file__))
DN_TOOLS = os.path.normpath(os.path.join(HERE, "..", "..", "divinenames", "tools"))
sys.path.insert(0, DN_TOOLS)
from gen_divine_names import load_glyphs, align, is_word_token  # noqa: E402

JSON_DIR = os.environ.get("TAJWEED_JSON", os.path.join(HERE, "tajweed"))
OUT = os.path.join(HERE, "tajweed.db")

# rule classes used by quran.com; anything else (custom-*) is ignored
RULES = {
    "ham_wasl", "slnt", "laam_shamsiyah", "madda_normal", "madda_permissible", "madda_necessary",
    "madda_obligatory", "qalaqah", "ikhafa_shafawi", "ikhafa", "idgham_shafawi", "iqlab",
    "idgham_ghunnah", "idgham_wo_ghunnah", "idgham_mutajanisayn", "idgham_mutaqaribayn", "ghunnah",
}
TAG = re.compile(r"<rule class=([a-z_\-]+)>|</rule>")
# pause marks (06D6-06DB), end of ayah, rub el hizb, sajdah sign and direction/joiner controls;
# the small tajweed marks (06DC, 06DF-06E8, 06EA-06ED) are part of the word and kept
STRIP = re.compile("[ۖ-ۛ۝۞۩‌-‏]")


def parse(markup):
    """Returns (plain text, [(start, end, rule)]) with nested rules flattened (outer known rule wins)."""
    text, spans, stack, pos = [], [], [], 0
    for m in TAG.finditer(markup):
        chunk = markup[pos:m.start()]
        chunk = STRIP.sub("", chunk)
        text.append(chunk)
        pos = m.end()
        cur = sum(len(t) for t in text)
        if m.group(0) == "</rule>":
            if stack:
                rule, start = stack.pop()
                if rule in RULES and not any(s[2] in RULES and s[0] <= start and s[1] >= cur for s in spans):
                    # only keep if no enclosing known rule already covers the range
                    outer = [r for r, _ in stack if r in RULES]
                    if not outer and cur > start:
                        spans.append((start, cur, rule))
        else:
            stack.append((m.group(1), cur))
    text.append(STRIP.sub("", markup[pos:]))
    return "".join(text), spans


MARKS = set("\u064b\u064c\u064d\u064e\u064f\u0650\u0651\u0652\u0653\u0654\u0655\u0656\u0657\u0658\u0670"
            "\u06d6\u06d7\u06d8\u06d9\u06da\u06db\u06dc\u06df\u06e0\u06e1\u06e2\u06e3\u06e4\u06e5\u06e6\u06e7\u06e8\u06ea\u06eb\u06ec\u06ed"
            "\u08f0\u08f1\u08f2\u0640")
HAMZA = set("\u0621\u0623\u0624\u0625\u0626\u0654\u0655")
NOONISH = re.compile("[\u0646\u0645\u064b\u064c\u064d\u08f0\u08f1\u08f2\u06e2\u06ed]")
SHADDA, SUKUN = "\u0651", "\u06e1\u0652"
MADDAH = "\u0653"
MADD_LETTERS = set("\u0627\u0648\u064a\u0649\u0670\u06e6\u06e5")
IDGHAM = {"idgham_ghunnah", "idgham_wo_ghunnah", "idgham_shafawi", "idgham_mutajanisayn", "idgham_mutaqaribayn"}
IDGHAM_GHUNNAH = {"idgham_ghunnah", "idgham_shafawi"}
IKHFA = {"ikhafa", "ikhafa_shafawi", "iqlab"}


def base_after(text, i):
    """Index of the next base letter after position i, or None."""
    j = i + 1
    while j < len(text) and text[j] in MARKS and text[j] != "\u0640":
        j += 1
    return j if j < len(text) else None


def marks_after(text, j):
    out = ""
    k = j + 1
    while k < len(text) and text[k] in MARKS:
        out += text[k]
        k += 1
    return out


def madd_spans(text):
    """Madd spans derived from the maddah sign of the Madani script (the data source lacks the
    obligatory madd and marks the separated madd inconsistently)."""
    result = []
    for i, ch in enumerate(text):
        if ch != MADDAH:
            continue
        # the letter carrying the madd: walk back over marks; a small (dagger) alef is itself the
        # madd letter, but the span starts at the base letter it sits on
        start = i - 1
        letter = None
        while start > 0 and text[start] in MARKS:
            if text[start] == "ٰ" and letter is None:
                letter = text[start]
            start -= 1
        if letter is None:
            letter = text[start]
        j = base_after(text, i)
        nxt = text[j] if j is not None else None
        if letter not in MADD_LETTERS:
            # muqatta'at letters (alif-lam-mim, ayn-sin-qaf): six counts, except ayn which may be shortened
            rule = "madda_permissible" if letter == "\u0639" else "madda_necessary"
        elif nxt is None or (nxt == "\u0627" and base_after(text, j) is None):
            # end of word (possibly followed by a silent alef): the separated madd before a hamza
            rule = "madda_permissible"
        elif nxt in HAMZA or nxt == "\u0640":
            # yaa-ayyuha and haa-antum / haa-ulaa are written as one word but the madd is separated
            if letter == "\u0670" and text[0] in "\u064a\u0647" and start <= 4:
                rule = "madda_permissible"
            else:
                rule = "madda_obligatory"
        else:
            after = marks_after(text, j)
            if SHADDA in after or any(c in after for c in SUKUN):
                rule = "madda_necessary"
            else:
                rule = "madda_obligatory"
        result.append((start, i + 1, rule))
    return result


def refine(words):
    """Post-processes the spans of one ayah so that the colouring follows the printed tajweed
    mushaf: silent (grey) first letter of every idgham, green only on the letter that carries the
    ghunnah, no colour on the letter after an ikhfa/iqlab, and madd classes from the maddah sign."""
    out = []
    for wi, (text, spans) in enumerate(words):
        prev_last = words[wi - 1][1][-1][2] if wi > 0 and words[wi - 1][1] else None
        new = []
        for (b, e, rule) in sorted(spans):
            seg = text[b:e]
            if rule in IDGHAM:
                is_target = b == 0 and prev_last == rule
                if is_target:
                    if rule in IDGHAM_GHUNNAH:
                        new.append((b, e, "ghunnah"))
                    # merged letter without ghunnah stays in the text colour
                else:
                    new.append((b, e, "slnt"))
            elif rule in IKHFA:
                m = list(NOONISH.finditer(seg))
                if not m:
                    continue  # the letter after the noon/tanween: not coloured in the printed mushaf
                end = b + m[-1].end()
                while end < e and text[end] in MARKS:
                    end += 1
                new.append((b, end, rule))
            elif rule.startswith("madda_") and rule != "madda_normal":
                if rule == "madda_permissible" and wi == len(words) - 1:
                    new.append((b, e, rule))  # madd before the stop at the end of the ayah
                # other source madd spans are replaced by the derived ones below
            else:
                new.append((b, e, rule))
        derived = madd_spans(text)
        new = [x for x in new if not any(d[0] < x[1] and x[0] < d[1] and x[2].startswith("madda_") for d in derived)]
        new.extend(derived)
        out.append((text, sorted(set(new))))
    return out


def load_tajweed():
    verses = {}
    for path in glob.glob(os.path.join(JSON_DIR, "ch_*_p*.json")):
        with open(path, encoding="utf-8-sig") as f:
            data = json.load(f)
        for v in data["verses"]:
            s, a = (int(x) for x in v["verse_key"].split(":"))
            words = []
            for w in sorted(v["words"], key=lambda x: x["position"]):
                if w.get("char_type_name") != "word":
                    continue
                words.append(parse(w.get("text_uthmani_tajweed") or w.get("text_uthmani") or ""))
            verses[(s, a)] = words
    return verses


def main():
    glyphs = load_glyphs()
    v2 = sqlite3.connect(os.path.join(DN_TOOLS, "db", "quran.ar.uthmani.v2.db"))
    ayahs = v2.execute("select sura,ayah,text from arabic_text order by sura,ayah").fetchall()
    tajweed = load_tajweed()
    print("verses with tajweed markup:", len(tajweed))

    if os.path.exists(OUT):
        os.remove(OUT)
    out = sqlite3.connect(OUT)
    out.executescript("""
    CREATE TABLE words(
      sura INTEGER NOT NULL, ayah INTEGER NOT NULL, position INTEGER NOT NULL,
      text TEXT NOT NULL, spans TEXT NOT NULL,
      PRIMARY KEY(sura, ayah, position)
    );
    CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
    """)
    stats = collections.Counter()
    rule_counts = collections.Counter()
    rows = []
    for s, a, text in ayahs:
        toks = text.split()
        mapping, exact = align(toks, glyphs[(s, a)])
        positions = [mapping[i] for i, t in enumerate(toks) if is_word_token(t)]
        words = tajweed.get((s, a))
        if words is None or len(words) != len(positions) or any(p is None for p in positions):
            stats["skipped_ayahs"] += 1
            continue
        if not exact:
            stats["fallback_align"] += 1
        words = refine(words)
        for (wtext, spans), pos in zip(words, positions):
            if not wtext.strip():
                stats["empty_word"] += 1
                continue
            for _, _, r in spans:
                rule_counts[r] += 1
            rows.append((s, a, pos, wtext, ";".join(f"{b}-{e}:{r}" for b, e, r in spans)))
        stats["ayahs"] += 1
    out.executemany("insert into words values(?,?,?,?,?)", rows)
    out.execute("insert into meta values('version','2')")
    out.execute("insert into meta values('mushaf','madani')")
    out.execute("insert into meta values('source','quran.com API v4 text_uthmani_tajweed (tajweed rules), word positions aligned to ayahinfo_1260')")
    out.commit()
    out.execute("VACUUM")
    out.close()
    print("words:", len(rows), dict(stats))
    for r, c in sorted(rule_counts.items(), key=lambda x: -x[1]):
        print(f"  {r:22s} {c}")
    print("db size:", os.path.getsize(OUT))


if __name__ == "__main__":
    main()

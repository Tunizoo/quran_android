# -*- coding: utf-8 -*-
"""
Post-processes tajweed.db so that two letters of different tajweed colours are never drawn as one
ligature glyph.

The Uthmanic font merges many letter pairs into a single glyph (lam+noon, kaf+lam, beh+reh ...). A
glyph can only have one colour, so e.g. in «ٱلنَّاسِ» the grey lam (lam shamsiyah) and the green
noon (ghunnah) came out in the same colour. A ZERO WIDTH JOINER between the two letters keeps them
joined but stops the ligature (HarfBuzz runs the Arabic ligating features with manual ZWJ), so each
letter is its own glyph again.

Where to insert it is decided with the real shaper: the candidate words (adjacent letters of
different colours whose pair occurs in a ligature of the font) are laid out by headless Chrome with
the bundled font, and a pair is treated as a ligature when Chrome reports the same extent for both
letters (it splits a ligature's advance evenly between its characters).

Run after gen_tajweed.py:  python ligature_breaks.py
Then bump TajweedDatabase.BUNDLED_VERSION.
"""
import html, json, os, re, sqlite3, subprocess, sys, tempfile, unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", ".."))
DB = os.path.join(ROOT, "feature", "tajweed", "src", "main", "assets", "tajweed.db")
FONT = os.path.join(ROOT, "app", "src", "madani", "assets", "uthmanic_hafs_ver12.otf")
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
ZWJ = "\u200d"
TATWEEL = "\u0640"

# rule class -> colour group (same grouping as TajweedColorGroup in the app); None = not coloured
GROUPS = {
    "madda_necessary": "necessary", "madda_obligatory": "obligatory", "madda_permissible": "permissible",
    "ghunnah": "ghunnah", "ikhafa": "ghunnah", "ikhafa_shafawi": "ghunnah", "idgham_ghunnah": "ghunnah",
    "idgham_shafawi": "ghunnah", "iqlab": "ghunnah",
    "qalaqah": "qalqalah",
    "ham_wasl": "silent", "slnt": "silent", "laam_shamsiyah": "silent", "idgham_wo_ghunnah": "silent",
    "idgham_mutajanisayn": "silent", "idgham_mutaqaribayn": "silent",
}


def is_base(ch):
    return unicodedata.category(ch).startswith("L") and ch != TATWEEL


def parse_spans(spans):
    result = []
    for item in spans.split(";") if spans else []:
        rng, rule = item.split(":")
        a, b = (int(x) for x in rng.split("-"))
        result.append([a, b, rule])
    return result


def colours(text, spans):
    cols = [None] * len(text)
    for a, b, rule in spans:
        group = GROUPS.get(rule)
        for i in range(a, min(b, len(text))):
            cols[i] = group
    return cols


def font_ligature_pairs():
    """Adjacent letter pairs (marks ignored) that occur inside a ligature of the font."""
    from fontTools.ttLib import TTFont
    font = TTFont(FONT)
    gsub = font["GSUB"].table
    reverse = {}
    for cp, glyph in font.getBestCmap().items():
        reverse.setdefault(glyph, cp)
    singles = {}
    ligatures = []
    for lookup in gsub.LookupList.Lookup:
        for sub in lookup.SubTable:
            table = sub.ExtSubTable if lookup.LookupType == 7 else sub
            kind = type(table).__name__
            if kind == "SingleSubst":
                for a, b in table.mapping.items():
                    singles.setdefault(b, a)
            elif kind == "LigatureSubst":
                for first, ligs in table.ligatures.items():
                    for lig in ligs:
                        ligatures.append([first] + lig.Component)

    def char_of(glyph):
        seen = set()
        while glyph in singles and glyph not in seen:
            seen.add(glyph)
            glyph = singles[glyph]
        cp = reverse.get(glyph)
        return chr(cp) if cp else None

    pairs = set()
    for components in ligatures:
        letters = [c for c in (char_of(g) for g in components) if c and is_base(c)]
        for a, b in zip(letters, letters[1:]):
            pairs.add((a, b))
    return pairs


def candidate_boundaries(text, cols, pairs):
    """Indices j of base letters whose previous base letter has another colour and may ligate with it."""
    result = []
    prev = None
    for i, ch in enumerate(text):
        if ch == TATWEEL or ch == ZWJ:
            prev = None
            continue
        if not is_base(ch):
            continue
        if prev is not None and cols[prev] != cols[i] and (text[prev], ch) in pairs:
            result.append((prev, i))
        prev = i
    return result


def measure(texts):
    """Per-character extent widths of every text as laid out by Chrome with the bundled font."""
    font_url = "file:///" + FONT.replace(os.sep, "/")
    body = "".join(f'<text x="10" y="100">{html.escape(t)}</text>' for t in texts)
    script = """
document.fonts.ready.then(() => {
  const out = [];
  document.querySelectorAll('text').forEach(t => {
    const n = t.getNumberOfChars(); const a = [];
    for (let i = 0; i < n; i++) a.push(+t.getExtentOfChar(i).width.toFixed(2));
    out.push(a);
  });
  const pre = document.createElement('pre'); pre.id = 'out'; pre.textContent = JSON.stringify(out);
  document.body.appendChild(pre);
});
"""
    page = (
        '<!doctype html><meta charset="utf-8"><style>@font-face{font-family:U;src:url("%s")} '
        'svg{direction:ltr} text{font-family:U;font-size:100px}</style><svg width="2000" height="200">%s</svg>'
        "<script>%s</script>" % (font_url, body, script)
    )
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "measure.html")
        with open(path, "w", encoding="utf-8") as f:
            f.write(page)
        dom = subprocess.run(
            [CHROME, "--headless=new", "--disable-gpu", "--virtual-time-budget=60000", "--dump-dom",
             "file:///" + path.replace(os.sep, "/")],
            capture_output=True, check=True).stdout.decode("utf-8", "replace")
    match = re.search(r'<pre id="out">(.*?)</pre>', dom, re.S)
    if not match:
        raise RuntimeError("Chrome did not return measurements")
    return json.loads(html.unescape(match.group(1)))


def main():
    db = sqlite3.connect(DB)
    rows = db.execute("select sura, ayah, position, text, spans from words").fetchall()
    # joiners of an earlier run are removed first (the colours may have changed since), so the script
    # can be run again after tanween_marks.py
    stripped = 0
    for s, a, p, text, spans in rows:
        if ZWJ not in text:
            continue
        parsed = parse_spans(spans)
        for pos in sorted((i for i, ch in enumerate(text) if ch == ZWJ), reverse=True):
            text = text[:pos] + text[pos + 1:]
            for span in parsed:
                if span[0] > pos:
                    span[0] -= 1
                if span[1] > pos:
                    span[1] -= 1
        db.execute("update words set text = ?, spans = ? where sura = ? and ayah = ? and position = ?",
                   (text, ";".join(f"{x}-{y}:{rule}" for x, y, rule in parsed), s, a, p))
        stripped += 1
    if stripped:
        print("joiners of a previous run removed from", stripped, "words")
        rows = db.execute("select sura, ayah, position, text, spans from words").fetchall()
    pairs = font_ligature_pairs()
    print("ligature letter pairs in the font:", len(pairs))

    # distinct (text, spans) combinations that have at least one candidate boundary
    candidates = {}
    for s, a, p, text, spans in rows:
        key = (text, spans)
        if key in candidates:
            continue
        parsed = parse_spans(spans)
        bounds = candidate_boundaries(text, colours(text, parsed), pairs)
        if bounds:
            candidates[key] = bounds
    print("distinct words to shape:", len(candidates))

    keys = list(candidates)
    widths = []
    for start in range(0, len(keys), 2000):
        widths.extend(measure([k[0] for k in keys[start:start + 2000]]))

    breaks = {}
    for key, w in zip(keys, widths):
        text = key[0]
        if len(w) != len(text):
            continue  # characters Chrome does not address one by one; leave the word alone
        positions = [j for i, j in candidates[key] if abs(w[i] - w[j]) < 0.011]
        if positions:
            breaks[key] = positions
    print("distinct words with a two-colour ligature:", len(breaks))

    updated = 0
    for s, a, p, text, spans in rows:
        positions = breaks.get((text, spans))
        if not positions:
            continue
        parsed = parse_spans(spans)
        for pos in sorted(positions, reverse=True):
            text = text[:pos] + ZWJ + text[pos:]
            for span in parsed:
                if span[0] >= pos:
                    span[0] += 1
                if span[1] > pos:
                    span[1] += 1
        new_spans = ";".join(f"{x}-{y}:{rule}" for x, y, rule in parsed)
        db.execute("update words set text = ?, spans = ? where sura = ? and ayah = ? and position = ?",
                   (text, new_spans, s, a, p))
        updated += 1
    db.execute("insert or replace into meta values('ligature_breaks', ?)", (str(updated),))
    db.commit()
    db.execute("VACUUM")
    db.close()
    print("words updated:", updated)
    examples = list(breaks.items())[:12]
    for (text, spans), positions in examples:
        print("  ", text, positions, spans)


if __name__ == "__main__":
    main()

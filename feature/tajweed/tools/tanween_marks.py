# -*- coding: utf-8 -*-
"""
Post-processes tajweed.db so that the rules of the tanween colour the tanween sign only.

The letter that carries a tanween is pronounced normally; what is merged (idgham, grey), hidden
(ikhfa, green) or turned into a meem (iqlab, green) is the noon sound of the tanween. The spans
written by gen_tajweed.py start on the carrier letter (older renderers could not colour a mark on
its own); this script moves their start onto the tanween sign itself: the doubled vowel
(U+064B..U+064D), or the small meem (U+06E2 / U+06ED) written in place of the second vowel in iqlab.
A silent letter after the tanween (the alef of «نَارًا» is not in the span; the alef maqsura of
«هُدًى» is) keeps its colour.

The renderers draw a span that starts on a combining mark in two passes (see TajweedDrawHelper and
tools/print/gen_mushaf.py).

Order: gen_tajweed.py -> tanween_marks.py -> ligature_breaks.py, then bump
TajweedDatabase.BUNDLED_VERSION.
"""
import os, sqlite3, sys, collections

HERE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.normpath(os.path.join(HERE, "..", "src", "main", "assets", "tajweed.db"))

TANWEEN = set("ًٌٍ")
IQLAB_MARK = set("ۭۢ")
RULES = {"slnt", "ikhafa", "iqlab", "idgham_ghunnah", "idgham_wo_ghunnah"}


def trim(text, spans):
    """Moves the start of tanween spans onto the tanween sign. Returns (spans, changed)."""
    changed = False
    result = []
    for a, b, rule in spans:
        if rule in RULES:
            for i in range(a, min(b, len(text))):
                if text[i] in TANWEEN or text[i] in IQLAB_MARK:
                    if i > a:
                        a = i
                        changed = True
                    break
        result.append((a, b, rule))
    return result, changed


def main():
    db = sqlite3.connect(DB)
    rows = db.execute("select sura, ayah, position, text, spans from words where spans != ''").fetchall()
    if any("‍" in r[3] for r in rows):
        print("run this before ligature_breaks.py (the database already contains joiners)")
        sys.exit(1)
    updated = 0
    by_rule = collections.Counter()
    for s, a, p, text, spans in rows:
        parsed = []
        for item in spans.split(";"):
            rng, rule = item.split(":")
            x, y = (int(v) for v in rng.split("-"))
            parsed.append((x, y, rule))
        trimmed, changed = trim(text, parsed)
        if not changed:
            continue
        for old, new in zip(parsed, trimmed):
            if old != new:
                by_rule[new[2]] += 1
        db.execute("update words set spans = ? where sura = ? and ayah = ? and position = ?",
                   (";".join(f"{x}-{y}:{rule}" for x, y, rule in trimmed), s, a, p))
        updated += 1
    db.execute("insert or replace into meta values('tanween_marks', ?)", (str(updated),))
    db.commit()
    db.close()
    print("words updated:", updated, dict(by_rule))


if __name__ == "__main__":
    main()

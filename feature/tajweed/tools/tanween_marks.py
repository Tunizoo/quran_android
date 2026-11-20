# -*- coding: utf-8 -*-
"""
Post-processes tajweed.db so that the rules of the tanween colour the tanween sign only.

The letter that carries a tanween is pronounced normally; what is merged (idgham, grey), hidden
(ikhfa, green) or turned into a meem (iqlab, green) is the noon sound of the tanween. The spans
written by gen_tajweed.py start on the carrier letter (older renderers could not colour a mark on
its own); this script moves their start onto the tanween sign itself: the doubled vowel
(U+064B..U+064D), or the small meem (U+06E2 / U+06ED) written in place of the second vowel in iqlab.
The small meem written over a noon («مِنۢ بَعۡدِ») is different: there the noon itself is the letter
that turns into a meem, so the span is the noon and its sign. The hidden meem (ikhfa shafawi) is
coloured alone, without the letter before it that the source includes.
A silent letter after the tanween (the alef of «نَارًا» is not in the span; the alef maqsura of
«هُدًى» is) keeps its colour.

Spans that start on a short vowel (the source includes the vowel before a madd letter or a merged
meem) are moved onto the letter after it, so that the only spans starting on a combining mark are
the ones that colour a mark on its own. The renderers repaint that mark over the complete word
(see MarkGlyphs / TajweedDrawHelper and tools/print/gen_mushaf.py); the word text is never drawn
with a mark removed, because the font picks letter forms by the marks around them.

Order: gen_tajweed.py -> tanween_marks.py -> ligature_breaks.py, then bump
TajweedDatabase.BUNDLED_VERSION. The script only moves span starts, so it can be run again on a
database that already went through ligature_breaks.py.
"""
import os, sqlite3, sys, collections, unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.normpath(os.path.join(HERE, "..", "src", "main", "assets", "tajweed.db"))

TANWEEN = set("ًٌٍ")
IQLAB_MARK = set("ۭۢ")
RULES = {"slnt", "ikhafa", "iqlab", "idgham_ghunnah", "idgham_wo_ghunnah"}


SHORT_VOWELS = set("َُِ")
ZWJ = "‍"


NOON = "ن"
TATWEEL = "ـ"


def is_mark(ch):
    return unicodedata.category(ch).startswith("M")


def is_letter(ch):
    return unicodedata.category(ch).startswith("L") and ch != TATWEEL


def trim(text, spans):
    """
    Moves the start of tanween spans onto the tanween sign, and the start of spans that begin on a
    short vowel onto the letter after it: the source includes the vowel before a madd letter or a
    merged meem («ٱلرَّحِيمِ» 6-8 = kasra + yeh, «لَهُم» 3-5 = damma + meem), but that vowel belongs
    to the previous letter and is not part of the rule. Returns (spans, changed).
    """
    changed = False
    result = []
    for a, b, rule in spans:
        if a < len(text) and text[a] in SHORT_VOWELS:
            j = a
            while j < min(b, len(text)) and (is_mark(text[j]) or text[j] == ZWJ):
                j += 1
            if j < min(b, len(text)):
                a = j
                changed = True
        if rule in RULES:
            for i in range(a, min(b, len(text))):
                if text[i] in TANWEEN or text[i] in IQLAB_MARK:
                    # the small meem over a noon («مِنۢ بَعۡدِ») marks the noon itself, which is the
                    # letter turned into a meem: the noon keeps the colour
                    target = i - 1 if text[i] in IQLAB_MARK and i > 0 and text[i - 1] == NOON else i
                    if target != a:
                        a = target
                        changed = True
                    break
        if rule == "ikhafa_shafawi":
            # the source colours the letter before the hidden meem too («هُم بِهِ» = heh + meem)
            last = max((i for i in range(a, min(b, len(text))) if is_letter(text[i])), default=a)
            if last > a:
                a = last
                changed = True
        result.append((a, b, rule))
    return result, changed


def main():
    db = sqlite3.connect(DB)
    rows = db.execute("select sura, ayah, position, text, spans from words where spans != ''").fetchall()
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

# -*- coding: utf-8 -*-
"""Prints the coloured spans of a few well known ayahs so the rules can be checked by eye."""
import os, sqlite3, sys

HERE = os.path.dirname(os.path.abspath(__file__))
DB = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "tajweed.db")
LABEL = {
    "madda_necessary": "لازم٦", "madda_obligatory": "مد٤٥", "madda_permissible": "عارض",
    "ghunnah": "غنة", "ikhafa": "إخفاء", "ikhafa_shafawi": "إخفاء-م", "iqlab": "إقلاب",
    "qalaqah": "قلقلة", "slnt": "رمادي", "ham_wasl": "وصل", "laam_shamsiyah": "شمسية", "madda_normal": "",
}
AYAHS = [(1, 1), (1, 2), (1, 7), (2, 1), (2, 3), (2, 4), (2, 6), (2, 8), (2, 19), (2, 255), (2, 257),
         (3, 1), (7, 1), (10, 1), (19, 1), (36, 1), (42, 2), (68, 42), (81, 1), (81, 2), (112, 1), (112, 4),
         (113, 1), (114, 1), (2, 282)]

c = sqlite3.connect(DB)
for s, a in AYAHS:
    rows = c.execute("select position,text,spans from words where sura=? and ayah=? order by position", (s, a)).fetchall()
    out = []
    for pos, text, spans in rows:
        tags = []
        for item in spans.split(";") if spans else []:
            rng, rule = item.split(":")
            b, e = (int(x) for x in rng.split("-"))
            lab = LABEL.get(rule, rule)
            if lab:
                tags.append(f"{text[b:e]}={lab}")
        out.append(text + ("[" + " ".join(tags) + "]" if tags else ""))
    print(f"{s}:{a}  " + "  ".join(out))
    print()

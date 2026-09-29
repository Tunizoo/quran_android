# Divine names data generator

Regenerates src/main/assets/divine_names.db.

Inputs (copy them into a db/ folder next to this script; both ship with the app data):
- ayahinfo_1260.db (madani glyph positions, from the ayahinfo download)
- quran.ar.uthmani.v2.db (uthmani text database)

Run: python gen_divine_names.py

It writes divine_names.db and review.txt (one line per occurrence with context) for manual review.
Add or adjust names in the NAMES list; every name carries its matching mode and curated exclusions.

Attributes (sifat) are listed in ATTRIBUTES with explicit (sura, ayah, token regex) occurrences, because words
such as hand, face or love also describe creatures. The generator prints a WARNING for any listed ayah where
nothing matched. The `names.kind` column is `name` or `attribute`; bump `DivineNamesDatabase.BUNDLED_VERSION`
after regenerating so installed copies are refreshed.

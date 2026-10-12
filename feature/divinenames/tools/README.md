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

## Translations

The explanations (`meaning`, `note`, `evidence_ref`, sources line) are written in Arabic in this
script and translated in `translations/<lang>.py` (see `translations/README.md` for the format and
the conventions; `translations/source_ar.json` is the Arabic dump used as the source). The
Quranic evidence stays Arabic. `gen_translations.py` validates each file
(`--check <lang>`) and writes the `translations` table; `gen_divine_names.py` calls it at the end,
so regenerating the names keeps the translations. The app reads the row of the app language and
falls back to Arabic (`DivineNamesDatabase.appLanguage()`); Android's legacy codes are used for the
folder-like names ("in" for Indonesian).

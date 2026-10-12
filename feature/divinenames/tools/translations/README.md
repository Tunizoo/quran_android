# Translations of the divine names / attributes explanations

`source_ar.json` is the Arabic content of `divine_names.db` (dumped from the `names` table).
Each `<lang>.py` translates, for every one of the 106 entries, the fields `meaning`, `note` and
`evidence_ref`. The fields `arabic`, `transliteration` and `evidence` (the Quranic text) are never
translated. `gen_translations.py` validates the files and writes them into the `translations`
table of `divine_names.db`; the app falls back to Arabic for languages that have no file.

## File format (`<lang>.py`)

```python
# -*- coding: utf-8 -*-
LANG = "en"
SOURCES_NAMES = "References: ..."        # translation of the sources line of the names
SOURCES_ATTRIBUTES = "References: ..."   # translation of the sources line of the attributes
ENTRIES = {
    "allah": {
        "meaning": "...",
        "evidence_ref": "Al-Hashr 22",
        "note": "...",                   # "" when the Arabic note is empty
    },
    ...  # all 106 ids, in the order of source_ar.json
}
```

Validate with `python feature/divinenames/tools/gen_translations.py --check <lang>`.

## Conventions

- Faithful translation of the whole Arabic text, nothing added, nothing dropped, same tone. The
  explanations follow the methodology of the Salaf (Ahl as-Sunnah): affirm what Allah affirmed for
  Himself without tahrif (distorting the meaning), ta'til (denying it), takyif (asking how) or
  tamthil (likening it to creation). Keep the passages that reject the Ash'ari / Mu'tazili
  reinterpretations (ta'wil) exactly as they are.
- Technical terms stay in transliteration with a short gloss the first time they appear in an
  entry: istiwa (rising over the Throne), tawhid al-uluhiyyah, ta'wil, ghunnah... Use the usual
  spelling of the target language.
- Quranic quotations inside the text (between ﴿ ﴾ or « ») are translated by meaning, keeping the
  brackets. Hadith quotations (between « ») are translated, keeping the brackets. Keep ﷺ as is.
- `evidence_ref`: for Quran use the sura name as commonly written in the target language plus the
  ayah number (en "Al-Hashr 22", fr "Al-Hashr 22", tr "Haşr 22", in "Al-Hasyr 22", de/es/ru use
  the usual transliteration of that language, ur/fa may keep the Arabic name). For hadith use the
  usual formula of the language for رواه مسلم ("Narrated by Muslim"), متفق عليه ("Agreed upon"),
  رواه البخاري معلقًا ("Reported by al-Bukhari in mu'allaq form").
- Sura and ayah numbers mentioned inside notes stay as they are (e.g. "At-Tawbah 128").
- Book titles in the sources line stay transliterated (optionally with a translated gloss in
  parentheses); author names in the usual spelling of the language.
- Names of Allah mentioned inside explanations: transliterated form as in the `transliteration`
  field (Ar-Rahman, Al-Aziz...), optionally with the meaning.
- Plain text only: no markdown, no HTML, no newlines inside a string.

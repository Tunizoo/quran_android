# Tajweed data generator

Regenerates src/main/assets/tajweed.db from the quran.com API word markup.

1. Download the words of every chapter with the tajweed field into a tajweed/ folder next to this
   script (one file per page of results): https://api.quran.com/api/v4/verses/by_chapter/{c}?words=true&word_fields=text_uthmani,text_uthmani_tajweed&per_page=50&page={p}
2. Make sure ../../divinenames/tools/db/ holds ayahinfo_1260.db and quran.ar.uthmani.v2.db (see that README).
3. Run: python gen_tajweed.py

Each word row stores the bare word text and its tajweed spans (char offsets + rule class) keyed by
the glyph position in the madani page database, so the app can repaint exactly that word box.

## Tanween marks

The letter carrying a tanween is pronounced normally; the rule (idgham in grey, ikhfa and iqlab in
green) applies to the noon sound of the tanween. After `gen_tajweed.py`, run
`python tanween_marks.py`: it moves the start of those spans onto the tanween sign (or the small
meem of an iqlab). A base letter and its marks are one glyph cluster with a single colour, so the
renderers draw such words in two passes: the mark's cluster in the mark colour underneath, then the
word without the mark in its normal colours on top (`TajweedDrawHelper.measure`,
`tools/print/gen_mushaf.py: word_layers`). Run it before `ligature_breaks.py`.

## Ligature breaks

The Uthmanic font draws many letter pairs as one ligature glyph, and a glyph has a single colour,
so two letters of different tajweed colours came out in the same colour (e.g. the grey lam and the
green noon of «ٱلنَّاسِ»). After `gen_tajweed.py`, run `python ligature_breaks.py`: it inserts a
ZERO WIDTH JOINER between such letters (only where the font really ligates them, checked by
shaping the candidate words with headless Chrome and the bundled font) and shifts the span offsets.
The letters stay joined but are separate glyphs. Bump `TajweedDatabase.BUNDLED_VERSION` afterwards.

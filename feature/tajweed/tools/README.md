# Tajweed data generator

Regenerates src/main/assets/tajweed.db from the quran.com API word markup.

1. Download the words of every chapter with the tajweed field into a tajweed/ folder next to this
   script (one file per page of results): https://api.quran.com/api/v4/verses/by_chapter/{c}?words=true&word_fields=text_uthmani,text_uthmani_tajweed&per_page=50&page={p}
2. Make sure ../../divinenames/tools/db/ holds ayahinfo_1260.db and quran.ar.uthmani.v2.db (see that README).
3. Run: python gen_tajweed.py

Each word row stores the bare word text and its tajweed spans (char offsets + rule class) keyed by
the glyph position in the madani page database, so the app can repaint exactly that word box.

## Ligature breaks

The Uthmanic font draws many letter pairs as one ligature glyph, and a glyph has a single colour,
so two letters of different tajweed colours came out in the same colour (e.g. the grey lam and the
green noon of «ٱلنَّاسِ»). After `gen_tajweed.py`, run `python ligature_breaks.py`: it inserts a
ZERO WIDTH JOINER between such letters (only where the font really ligates them, checked by
shaping the candidate words with headless Chrome and the bundled font) and shifts the span offsets.
The letters stay joined but are separate glyphs. Bump `TajweedDatabase.BUNDLED_VERSION` afterwards.

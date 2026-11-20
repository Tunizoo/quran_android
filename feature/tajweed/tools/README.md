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
meem of an iqlab). It also moves spans that start on a short vowel (the source includes the vowel
before a madd letter or a merged meem: «لَهُم» 3-5 = damma + meem) onto the letter after it. Run it
before `ligature_breaks.py`; it can be run again on a database that already has the ZWJ breaks
(`ligature_breaks.py` removes the joiners of its previous run first, so run it again afterwards).
Two more corrections of the source spans happen here: the small meem over a noon («مِنۢ بَعۡدِ») keeps
the noon coloured with it (the noon is the letter that turns into a meem), and the hidden meem
(ikhfa shafawi, «هُم بِهِ») is coloured without the letter before it.

A base letter and its marks are one glyph cluster, painted in a single colour, and this font picks
letter forms by the marks around them, so **a word must never be drawn from a text with a mark
removed** (doing so displaced letters, e.g. the meem of «لَهُم», in database version 8).

- App (`TajweedDrawHelper.measure`, `MarkGlyphs`): the complete word is drawn once; on Android 12+
  the text is shaped with and without the mark (`TextRunShaper`), the glyphs only present in the
  complete run are the mark, and they are repainted in the mark colour with `Canvas.drawGlyphs`.
  If any other glyph differs (or on older Android) the whole cluster takes the colour.
- Print (`tools/print/gen_mushaf.py: word_layers`): two layers (the mark's cluster underneath,
  the word without the mark on top), but the page script first paints both variants on a canvas
  and shows the complete word with the cluster coloured when anything but the mark differs.

## Overlapping word boxes

The calligrapher sometimes lets a word reach over its neighbour (the kaf of «قَبۡلِكَ» above
«فَأَمۡلَيۡتُ», 13:32), so 370 pairs of neighbouring word boxes overlap. Both renderers split the
overlap in the middle for drawing (`drawTargets`, `split_overlaps`), keep the full box for
erasing the original ink, and let those words be squeezed down to 0.6× of the page scale.

## Ligature breaks

The Uthmanic font draws many letter pairs as one ligature glyph, and a glyph has a single colour,
so two letters of different tajweed colours came out in the same colour (e.g. the grey lam and the
green noon of «ٱلنَّاسِ»). After `gen_tajweed.py`, run `python ligature_breaks.py`: it inserts a
ZERO WIDTH JOINER between such letters (only where the font really ligates them, checked by
shaping the candidate words with headless Chrome and the bundled font) and shifts the span offsets.
The letters stay joined but are separate glyphs. Bump `TajweedDatabase.BUNDLED_VERSION` afterwards.

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

The same holds for every span that starts or ends between a letter and its marks (the qalqalah
of «لَشَدِيدٞ» covers the dal but not its tanween; about 10,000 spans): a colour change inside a
cluster makes Android and Chrome shape the mark apart from its letter and misplace it. Both
renderers therefore change colour only between clusters and repaint a differing mark on top as
described above (14,821 words; in 1,026 of them the mark cannot be isolated and the cluster takes
one colour).

## Overlapping word boxes

The calligrapher sometimes lets a word reach over its neighbour (the kaf of «قَبۡلِكَ» above
«فَأَمۡلَيۡتُ», 13:32), so 370 pairs of neighbouring word boxes overlap. Both renderers split the
overlap in the middle for drawing (`drawTargets`, `split_overlaps`) and keep the full box for
erasing the original ink.

## One letter size per page

The calligrapher justifies the lines by lengthening words (kashida), so the boxes of the words are
not in proportion to their width in the text font: a word stretched into its own box had visibly
larger letters than its neighbours wherever the line had room. Both renderers now set the words of
a *run* together (`layoutRuns`, `layoutPage`): the words that follow each other on a line with
nothing left from the page image between them (ayah markers and pause marks end a run, so every
word stays on its side of them). A run gets one horizontal stretch, at most 1.06× of the page font
size, and the room that is left goes between its words; only when that would add more than 0.3 em
per gap is the run stretched further. The page font size is the box/text ratio below which lies a
quarter of the text of the page, so only the densest runs are squeezed.

A word is therefore not always exactly on its glyph rectangle. The app records where each word is
drawn in `GlyphPlacements` (`common/glyphbounds`); the highlights of the divine names and the
covers of the memorization mode read it, for drawing and for hit tests.

## Ligature breaks

The Uthmanic font draws many letter pairs as one ligature glyph, and a glyph has a single colour,
so two letters of different tajweed colours came out in the same colour (e.g. the grey lam and the
green noon of «ٱلنَّاسِ»). After `gen_tajweed.py`, run `python ligature_breaks.py`: it inserts a
ZERO WIDTH JOINER between such letters (only where the font really ligates them, checked by
shaping the candidate words with headless Chrome and the bundled font) and shifts the span offsets.
The letters stay joined but are separate glyphs. Bump `TajweedDatabase.BUNDLED_VERSION` afterwards.

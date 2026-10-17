# Printable mushaf (tajweed colours + names and attributes of Allah)

`gen_mushaf.py` turns the app's data into a print-ready A4 PDF: every madani page is drawn from the
page image (ornaments, ayah markers, sura headers) with all words re-rendered in the Uthmanic font,
coloured by the tajweed rules of `tajweed.db`, the names of Allah highlighted in gold and His
attributes in yellow (`divine_names.db`), and their explanations printed as footnotes under the
page. An introduction (legend, methodology, the explanation of «الله» and «رب» which occur on most
pages) opens the book and a full index of the names and attributes closes it.

Rendering is done by headless Chrome from generated HTML/SVG (Chrome shapes Arabic and embeds the
fonts), in parts of N pages, merged with `pypdf`.

## Inputs

- Page images `page001.png … page604.png`, 1260 px wide, as the app downloads them
  (`https://quran.app/data/images1260.zip`, or pulled from a debug install with
  `adb exec-out "run-as <pkg> tar cf - -C files/quran_android width_1260" | tar xf -`).
- `feature/divinenames/tools/db/ayahinfo_1260.db` (word boxes), `feature/tajweed/src/main/assets/tajweed.db`,
  `feature/divinenames/src/main/assets/divine_names.db`, `app/src/madani/assets/uthmanic_hafs_ver12.otf`.
- Amiri fonts (`Amiri-Regular.ttf`, `Amiri-Bold.ttf`, https://github.com/aliftype/amiri) for the footnotes.
- `quran.ar.wordsmeaningssiraj.db` (meanings of the difficult words per ayah, «السراج في بيان غريب
  القرآن» by al-Khudayri), the app's Arabic "Word Meanings" download:
  `https://android.quran.com/data/getTranslation.php?id=85&ext=zip` (FTS table `verses(sura, ayah, text)`).
- Google Chrome at `C:\Program Files\Google\Chrome\Application\chrome.exe` (edit `CHROME` otherwise).

## Run

```
python tools/print/gen_mushaf.py --pages <dir with page001.png> --fonts <dir with Amiri ttf> \
    --gharib <quran.ar.wordsmeaningssiraj.db> [--from 1 --to 604] [--part 40] [--out dist/mushaf-tajweed-asma.pdf]
```

## How the page is laid out

- The word boxes come from `ayahinfo_1260.db`; small glyphs (pause marks) and glyphs without a word
  in `tajweed.db` (ayah markers, ornaments) are left as drawn on the image. Every word box is
  covered with the paper colour and the word is drawn as SVG text with `<tspan>` colours.
- A script in the page measures each word, takes the median box/width ratio of the page as the font
  size (one letter height per page, like the app) and stretches each word horizontally into its box
  (0.85–1.45× of the page scale, `textLength`), the way the calligrapher stretches with kashida.
- Footnotes start with the meanings of the difficult words of the page's ayahs (two columns, each
  run prefixed with the ayah number), then list the distinct names/attributes of the page in reading order; when the full
  explanations exceed ~420 characters only the first sentence of each is printed (the index at the
  end has the full text). «الله» and «رب» are explained once in the introduction.
- Colours and legend are those of the app (Dar Al-Maarifa tajweed colours; madd of two counts is
  not coloured).

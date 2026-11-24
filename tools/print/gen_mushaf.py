# -*- coding: utf-8 -*-
"""
Builds a printable mushaf (PDF, A4) from the app's data: every madani page is re-rendered with the
Uthmanic font over the page image, the words coloured with the tajweed rules of tajweed.db, the
names and attributes of Allah highlighted (divine_names.db) and explained in footnotes under the
page. Rendering is done by headless Chrome (HTML/SVG -> PDF), then the parts are merged with pypdf.

Usage:
  python gen_mushaf.py --pages DIR_WITH_page001.png [--from 1 --to 604] [--out mushaf.pdf]

Inputs (relative to the repository unless given):
  feature/tajweed/src/main/assets/tajweed.db
  feature/divinenames/src/main/assets/divine_names.db
  feature/divinenames/tools/db/ayahinfo_1260.db
  app/src/madani/assets/uthmanic_hafs_ver12.otf
  common/ui/core/src/main/res/values-ar/sura_names.xml
  pages/data/madani/.../MadaniDataSource.kt (juz table)
  --fonts DIR containing Amiri-Regular.ttf and Amiri-Bold.ttf (footnotes)
"""
import argparse, html, os, re, sqlite3, subprocess, sys, json, unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

PAGE_W, PAGE_H = 1260, 2038
PAPER = "#fdf8e7"

COLORS = {
    "madda_necessary": "#9C0D0D",
    "madda_obligatory": "#E3200A",
    "madda_permissible": "#F28C00",
    "ghunnah": "#1E8E3E", "ikhafa": "#1E8E3E", "ikhafa_shafawi": "#1E8E3E",
    "idgham_ghunnah": "#1E8E3E", "idgham_shafawi": "#1E8E3E", "iqlab": "#1E8E3E",
    "qalaqah": "#1A73E8",
    "ham_wasl": "#9E9E9E", "slnt": "#9E9E9E", "laam_shamsiyah": "#9E9E9E",
    "idgham_wo_ghunnah": "#9E9E9E", "idgham_mutajanisayn": "#9E9E9E", "idgham_mutaqaribayn": "#9E9E9E",
}
LEGEND = [
    ("#9C0D0D", "مد لازم ٦"), ("#E3200A", "مد ٤-٥ حركات"), ("#F28C00", "مد جائز ٢-٤-٦"),
    ("#1E8E3E", "غنة"), ("#1A73E8", "قلقلة"), ("#9E9E9E", "لا يُلفظ"),
]
NAME_FILL, ATTR_FILL = "rgba(224,162,27,0.45)", "rgba(255,242,77,0.6)"
# names too frequent for per-page notes; explained once in the introduction
FREQUENT = {"allah", "rabb"}
ARABIC_DIGITS = str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩")


def ar(n):
    return str(n).translate(ARABIC_DIGITS)


def load_sura_names():
    text = open(os.path.join(ROOT, "common/ui/core/src/main/res/values-ar/sura_names.xml"), encoding="utf-8").read()
    block = text.split('name="sura_names"')[1].split("</string-array>")[0]
    return re.findall(r"<item>(.*?)</item>", block)


def load_juz_pages():
    path = os.path.join(ROOT, "pages/data/madani/src/main/kotlin/com/quran/labs/androidquran/pages/data/madani/MadaniDataSource.kt")
    text = open(path, encoding="utf-8").read()
    block = text.split("pageForJuzArray = intArrayOf(")[1].split(")")[0]
    return [int(x) for x in re.findall(r"\d+", re.sub(r"/\*.*?\*/", "", block))]


def juz_of(page, juz_pages):
    j = 1
    for i, start in enumerate(juz_pages, start=1):
        if page >= start:
            j = i
    return j


def first_sentence(text):
    for sep in ("،", "؛", ". ", ":"):
        i = text.find(sep, 35)
        if i != -1:
            return text[:i].rstrip() + "…"
    return text


def is_mark(ch):
    return unicodedata.category(ch).startswith("M")


def parse_spans(spans):
    """'start-end:rule;...' -> [(start, end, colour)] for the coloured rules, sorted."""
    parts = []
    for item in spans.split(";") if spans else []:
        rng, rule = item.split(":")
        a, b = (int(x) for x in rng.split("-"))
        colour = COLORS.get(rule)
        if colour:
            parts.append((a, b, colour))
    parts.sort()
    return parts


HIDDEN = "none"


def markup_of(text, cols):
    """Markup of `text` with one colour per character: None = the text colour, HIDDEN = not drawn."""
    out = []
    i = 0
    while i < len(text):
        j = i + 1
        while j < len(text) and cols[j] == cols[i]:
            j += 1
        chunk = html.escape(text[i:j])
        out.append(chunk if cols[i] is None else f'<tspan fill="{cols[i]}" stroke="{cols[i]}">{chunk}</tspan>')
        i = j
    return "".join(out)


def word_layers(word_text, spans):
    """
    Returns (under, top, fallback) tspans markup.

    A letter and its marks are one glyph cluster. A <tspan> that starts or ends inside a cluster
    makes the browser shape the marks apart from their letter and misplaces them (the tanween
    landing on the dal of «لَشَدِيدٞ»), so every layer only changes colour between clusters.

    A mark whose colour differs from its letter's (the tanween of an idgham on a letter pronounced
    normally; the plain tanween of a qalqalah letter) needs two passes: `under` is the whole word,
    invisible except that cluster, drawn in the mark's colour, and `top` is the word without the
    mark in the letters' colours, drawn over it.

    This font picks letter forms by the marks around them, so the word without the mark is not
    always the same drawing. The page script compares the two renderings and, when anything but the
    mark differs, shows `fallback` instead: the complete word with one colour per cluster (the
    letter's, or the rule's when the letter has none). `under` and `fallback` are None when no
    cluster has two colours.
    """
    n = len(word_text)
    cols = [None] * n
    for a, b, colour in parse_spans(spans):
        for i in range(max(a, 0), min(b, n)):
            cols[i] = colour
    under = [HIDDEN] * n
    top = [None] * n
    fallback = [None] * n
    removed = set()
    i = 0
    while i < n:
        j = i + 1
        while j < n and is_mark(word_text[j]):
            j += 1
        base = cols[i]
        differing = [k for k in range(i + 1, j) if cols[k] != base]
        cluster = base
        if differing:
            mark_colour = cols[differing[0]]
            removed.update(k for k in differing if cols[k] == mark_colour)
            for k in range(i, j):
                under[k] = mark_colour
            if base is None:
                cluster = mark_colour
        for k in range(i, j):
            top[k] = base
            fallback[k] = cluster
        i = j
    if not removed:
        return None, markup_of(word_text, top), None
    kept = [k for k in range(n) if k not in removed]
    return (markup_of(word_text, under),
            markup_of("".join(word_text[k] for k in kept), [top[k] for k in kept]),
            markup_of(word_text, fallback))


def split_overlaps(entries):
    """
    The calligrapher sometimes lets a word reach over its neighbour (the kaf of «قَبۡلِكَ» above
    «فَأَمۡلَيۡتُ» in 13:32), so their boxes overlap; set side by side in the text font the two words
    would cover each other. The overlap of neighbours on a line is split in the middle. Sets the
    `dx1`/`dx2` drawing edges of each entry.
    """
    lines = {}
    for e in entries:
        e["dx1"], e["dx2"] = float(e["x1"]), float(e["x2"])
        lines.setdefault(e["line"], []).append(e)
    for words in lines.values():
        words.sort(key=lambda e: -e["x2"])  # right to left
        for prev, cur in zip(words, words[1:]):
            if cur["dx2"] - prev["dx1"] <= 4:
                continue
            middle = (cur["dx2"] + prev["dx1"]) / 2
            if middle - cur["dx1"] < 12 or prev["dx2"] - middle < 12:
                continue
            prev["dx1"] = middle
            cur["dx2"] = middle


def assign_runs(entries, anchors):
    """
    Words that follow each other on a line, with nothing left from the page image between them, are
    set together as one run (see layoutPage): sets the `run` number of each entry. `anchors` are the
    (line, centre x) of the glyphs that stay as drawn on the page: ayah markers, pause marks.
    `edges` tells whether such a glyph (rather than the end of the line) is on the right and on the
    left of the run: room may be left next to it, while the ends of the lines stay aligned.
    """
    lines = {}
    for e in entries:
        lines.setdefault(e["line"], []).append(((e["x1"] + e["x2"]) / 2, e))
    for line, x in anchors:
        lines.setdefault(line, []).append((x, None))
    run = 0
    for items in lines.values():
        items.sort(key=lambda item: -item[0])  # right to left
        words, anchor_right = [], False
        for _, e in items + [(0, "end of line")]:
            if isinstance(e, dict):
                words.append(e)
                continue
            for word in words:
                word["run"], word["edges"] = run, (anchor_right, e is None)
            run += 1
            words, anchor_right = [], True


class Data:
    def __init__(self, gharib_path=None):
        self.tajweed = sqlite3.connect(os.path.join(ROOT, "feature/tajweed/src/main/assets/tajweed.db"))
        self.names = sqlite3.connect(os.path.join(ROOT, "feature/divinenames/src/main/assets/divine_names.db"))
        self.glyphs = sqlite3.connect(os.path.join(ROOT, "feature/divinenames/tools/db/ayahinfo_1260.db"))
        self.sura_names = load_sura_names()
        self.juz_pages = load_juz_pages()
        self.name_info = {
            r[0]: dict(id=r[0], arabic=r[1], meaning=r[2], evidence=r[3], ref=r[4], note=r[5], kind=r[6], order=r[7], sources=r[8])
            for r in self.names.execute("select id, arabic, meaning, evidence, evidence_ref, note, kind, sort_order, sources from names")
        }
        # page where each name was first explained in a footnote (filled while rendering in order)
        self.explained_on = {}
        self.occurrences = {}
        for s, a, p, nid in self.names.execute("select sura, ayah, position, name_id from occurrences"):
            self.occurrences[(s, a, p)] = nid
        # meanings of the difficult words per ayah (al-Siraj fi bayan gharib al-Quran, al-Khudayri)
        self.gharib = {}
        if gharib_path:
            db = sqlite3.connect(gharib_path)
            for s, a, t in db.execute("select sura, ayah, text from verses"):
                t = (t or "").strip()
                if t and t != "...":
                    self.gharib[(s, a)] = t

    def page_glyphs(self, page):
        return self.glyphs.execute(
            "select sura_number, ayah_number, position, line_number, min_x, min_y, max_x, max_y "
            "from glyphs where page_number = ? order by line_number, position", (page,)).fetchall()

    def word(self, s, a, p):
        return self.tajweed.execute("select text, spans from words where sura=? and ayah=? and position=?", (s, a, p)).fetchone()


def render_page(data, page, images_dir):
    glyphs = data.page_glyphs(page)
    if not glyphs:
        return "", []
    covers, names_rects, texts = [], [], []
    entries, anchors = [], []
    page_names = []  # distinct name ids in reading order
    page_ayahs = []  # (sura, ayah) in reading order
    first_sura = None
    for s, a, p, line, x1, y1, x2, y2 in glyphs:
        if first_sura is None:
            first_sura = s
        if (s, a) not in page_ayahs:
            page_ayahs.append((s, a))
        w, h = x2 - x1, y2 - y1
        if w < 20 or h < 40:
            anchors.append((line, (x1 + x2) / 2))
            continue  # pause marks and the like stay as drawn on the page
        word = data.word(s, a, p)
        if word is None:
            anchors.append((line, (x1 + x2) / 2))
            continue  # ayah marker or ornament
        nid = data.occurrences.get((s, a, p))
        if nid and nid not in page_names:
            page_names.append(nid)
        entries.append(dict(line=line, x1=x1, y1=y1, x2=x2, y2=y2, text=word[0], spans=word[1], nid=nid))

    split_overlaps(entries)
    assign_runs(entries, anchors)
    for e in entries:
        x1, y1, x2, y2 = e["x1"], e["y1"], e["x2"], e["y2"]
        w, h = x2 - x1, y2 - y1
        cy = y1 + h / 2
        # the page script sets the words of a run side by side and moves the highlight with its word
        geo = f'data-x1="{e["dx1"]:.1f}" data-x2="{e["dx2"]:.1f}" data-run="{e["run"]}" y="{cy:.1f}"'
        geo += (' data-er="1"' if e["edges"][0] else "") + (' data-el="1"' if e["edges"][1] else "")
        if e["nid"]:
            fill = ATTR_FILL if data.name_info[e["nid"]]["kind"] == "attribute" else NAME_FILL
            key = f"n{page}-{len(names_rects)}"
            geo += f' data-nm="{key}"'
            names_rects.append(f'<rect id="{key}" x="{e["dx1"]-3:.1f}" y="{y1-2}" width="{e["dx2"]-e["dx1"]+6:.1f}" '
                               f'height="{h+4}" rx="6" fill="{fill}"/>')
        covers.append(f'<rect x="{x1-1}" y="{y1-1}" width="{w+2}" height="{h+2}" fill="{PAPER}"/>')
        under, top, fallback = word_layers(e["text"], e["spans"])
        if under is None:
            texts.append(f'<text {geo}>{top}</text>')
        else:
            # measured on the full word; the top layer copies its geometry (see layoutPage)
            texts.append(f'<text {geo}>{under}</text><text class="top" y="{cy:.1f}">{top}</text>'
                         f'<text class="fb" y="{cy:.1f}">{fallback}</text>')

    img = f"file:///{os.path.join(images_dir, f'page{page:03d}.png').replace(os.sep, '/')}"
    svg = (
        f'<svg class="mushaf" viewBox="0 0 {PAGE_W} {PAGE_H}">'
        f'<rect width="{PAGE_W}" height="{PAGE_H}" fill="{PAPER}"/>'
        f'<image href="{img}" width="{PAGE_W}" height="{PAGE_H}"/>'
        f'<g>{"".join(covers)}</g><g>{"".join(names_rects)}</g><g class="words">{"".join(texts)}</g></svg>'
    )

    # footnotes: the names and attributes of this page (the very frequent ones are in the intro).
    # On a crowded page, names already explained earlier only get a reference to that page.
    notes = [data.name_info[n] for n in page_names if n not in FREQUENT]
    dense = len(notes) > 6
    seen_before = [n for n in notes if dense and n["id"] in data.explained_on]
    to_explain = [n for n in notes if n not in seen_before]
    total = sum(len(n["meaning"]) for n in to_explain)
    items = []
    for n in to_explain:
        meaning = n["meaning"] if (total <= 420 and len(to_explain) <= 6) else first_sentence(n["meaning"])
        kind = ' <span class="kind">(صفة)</span>' if n["kind"] == "attribute" else ""
        items.append(f'<li><b>{html.escape(n["arabic"])}</b>{kind}: {html.escape(meaning)}</li>')
        data.explained_on.setdefault(n["id"], page)
    if seen_before:
        refs = "، ".join(f'<b>{html.escape(n["arabic"])}</b> (ص {ar(data.explained_on[n["id"]])})' for n in seen_before)
        items.append(f'<li class="ref">تقدّم شرح: {refs}.</li>')
    dense_class = " dense" if len(items) > 6 else ""
    names_html = f'<ol class="notes{dense_class}">{"".join(items)}</ol>' if items else ""

    # meanings of the difficult words of the page, one run per ayah
    gharib_items = []
    for s, a in page_ayahs:
        text = data.gharib.get((s, a))
        if not text:
            continue
        body = html.escape(" ".join(line.strip() for line in text.splitlines() if line.strip()))
        gharib_items.append(f'<span class="ay"><span class="an">{ar(a)}</span> {body}</span>')
    gharib_html = f'<div class="gharib">{" ".join(gharib_items)}</div>' if gharib_items else ""
    footnotes = f'<div class="foot">{gharib_html}{names_html}</div>'

    sura = data.sura_names[first_sura - 1]
    juz = juz_of(page, data.juz_pages)
    legend = "".join(f'<span class="sw" style="background:{c}"></span><span class="lb">{t}</span>' for c, t in LEGEND)
    return (
        f'<section class="page"><div class="hdr"><span>سورة {sura}</span><span>الجزء {ar(juz)}</span></div>'
        f'{svg}{footnotes}<div class="ftr"><span class="legend">{legend}</span><span class="num">{ar(page)}</span></div></section>'
    ), page_names


CSS = """
@page { size: A4; margin: 9mm 10mm; }
@font-face { font-family: "Uthmanic"; src: url("%(uth)s"); }
@font-face { font-family: "Amiri"; src: url("%(amiri)s"); }
@font-face { font-family: "Amiri"; font-weight: bold; src: url("%(amirib)s"); }
html, body { margin: 0; padding: 0; }
body { font-family: "Amiri", "Traditional Arabic", serif; direction: rtl; color: #222; }
.page { height: 278mm; box-sizing: border-box; overflow: hidden; display: flex; flex-direction: column; page-break-after: always; break-after: page; }
.hdr { display: flex; justify-content: space-between; font-size: 10.5pt; font-weight: bold; color: #5b4a1e; padding: 0 2mm 1mm; }
.mushaf { width: 130mm; height: 210mm; align-self: center; direction: ltr; border: 0.3mm solid #c9b989; border-radius: 2mm; }
.mushaf text { font-family: "Uthmanic"; font-size: 100px; dominant-baseline: alphabetic; fill: #111; stroke: #111; stroke-width: 1.3px; stroke-linejoin: round; paint-order: stroke fill; }
.mushaf text.fb { display: none; }
.foot { flex: 1; overflow: hidden; margin: 1.2mm 1mm 0; border-top: 0.3mm solid #c9b989; padding-top: 1mm; }
.gharib { column-count: 2; column-gap: 5mm; font-size: 7.3pt; line-height: 1.38; text-align: justify; }
.gharib .ay { display: inline; }
.gharib .an { font-weight: bold; color: #8a6500; }
.notes { margin: 1mm 0 0; padding: 0; font-size: 8pt; line-height: 1.38; }
ol.notes { list-style: none; border-top: 0.2mm dotted #c9b989; padding-top: 0.8mm; }
.notes li { margin: 0 0 0.6mm 0; text-align: justify; }
.notes.dense { column-count: 2; column-gap: 5mm; font-size: 7.4pt; line-height: 1.28; }
.notes.dense li { break-inside: avoid; margin-bottom: 0.4mm; }
.notes li.ref { column-span: all; color: #555; margin-top: 0.8mm; }
.notes b { color: #8a6500; }
.kind { color: #7a6a00; font-size: 7.5pt; }
.ftr { display: flex; justify-content: space-between; align-items: center; font-size: 7.5pt; color: #444; padding-top: 1mm; }
.legend .sw { display: inline-block; width: 3.2mm; height: 3.2mm; border-radius: 0.6mm; vertical-align: middle; margin: 0 0.8mm 0 1.8mm; }
.legend .lb { vertical-align: middle; }
.num { font-size: 10pt; font-weight: bold; }
.intro { font-size: 10.8pt; line-height: 1.65; padding: 2mm 6mm; }
.intro h1 { text-align: center; font-size: 26pt; margin: 12mm 0 3mm; color: #5b4a1e; }
.intro h2 { font-size: 14pt; color: #5b4a1e; margin: 4mm 0 1mm; }
.intro p { margin: 1.5mm 0; }
.intro .sub { text-align: center; font-size: 14pt; color: #8a6500; margin-bottom: 8mm; }
.index { font-size: 9.5pt; line-height: 1.55; column-count: 2; column-gap: 7mm; padding: 2mm 3mm; }
.index .e { break-inside: avoid; margin-bottom: 2.4mm; }
.index .e b { color: #8a6500; font-size: 11pt; }
.index .ev { color: #1d5c2a; }
.index .rf { color: #666; font-size: 8pt; }
.index .nt { color: #555; font-size: 8.5pt; }
"""

JS = """
// Is the word without its mark the same drawing as the complete word, minus the mark? Both are
// painted on a canvas; every pixel inked by the shortened word must be inked by the complete one.
const probe = document.createElement('canvas');
probe.width = 1000; probe.height = 360;
const pctx = probe.getContext('2d', { willReadFrequently: true });
const probeCache = new Map();
function inkOf(text) {
  pctx.clearRect(0, 0, probe.width, probe.height);
  pctx.font = '100px Uthmanic';
  pctx.direction = 'rtl';
  pctx.textAlign = 'right';
  pctx.fillStyle = '#000';
  pctx.fillText(text, probe.width - 40, 220);
  return pctx.getImageData(0, 0, probe.width, probe.height).data;
}
function sameDrawing(full, reduced) {
  const key = full + '|' + reduced;
  if (probeCache.has(key)) return probeCache.get(key);
  let ok = Math.abs(pctx.measureText(full).width - pctx.measureText(reduced).width) < 0.5;
  if (ok) {
    const a = inkOf(reduced), b = inkOf(full);
    let stray = 0, extra = 0;
    for (let i = 3; i < a.length; i += 4) {
      if (a[i] > 160 && b[i] < 48) stray++;
      if (b[i] > 160 && a[i] < 48) extra++;
    }
    ok = stray <= 2 && extra > 0;
  }
  probeCache.set(key, ok);
  return ok;
}
// Setting the words of a run (the same values as TajweedDrawHelper): the share of the text of a
// page that is denser than the page font size and gets squeezed; the stretch of a run relative to
// the page font size; the room (em) added between two words before a run is stretched further.
const FONT_QUANTILE = 0.25, MIN_STRETCH = 0.6, MAX_STRETCH = 1.06;
const MAX_EXTRA_GAP = 0.3, LAST_RESORT_STRETCH = 1.45;
function layoutPage(svg) {
  // words whose mark cannot be coloured on its own are shown complete, with the cluster coloured
  for (const top of Array.from(svg.querySelectorAll('text.top'))) {
    const under = top.previousElementSibling, fb = top.nextElementSibling;
    if (sameDrawing(under.textContent, top.textContent)) { fb.remove(); continue; }
    for (const name of ['data-x1', 'data-x2', 'data-run', 'data-er', 'data-el', 'data-nm']) if (under.hasAttribute(name)) fb.setAttribute(name, under.getAttribute(name));
    fb.classList.remove('fb');
    under.remove(); top.remove();
    document.body.dataset.fallbacks = (+(document.body.dataset.fallbacks || 0) + 1);
  }
  const texts = Array.from(svg.querySelectorAll('text:not(.top)'));
  if (!texts.length) return;
  // The calligrapher justifies the lines by lengthening words (kashida), so the boxes of the words
  // are not in proportion to their width in the text font. Stretching every word into its own box
  // gives letters of a different size from one word to the next; instead the words of a run (words
  // side by side with nothing from the page image between them) are set together: one stretch for
  // the whole run, kept within narrow limits, and the room that is left goes between the words and
  // next to the ayah marker or pause mark at an end of the run (never at the end of the line).
  const runs = new Map();
  pctx.font = '100px Uthmanic';
  for (const t of texts) {
    t.setAttribute('font-size', '100');
    // the box of a word on the page is the box of its ink: centre the ink of the text in it
    const ink = pctx.measureText(t.textContent);
    const d = { t, w: Math.max(t.getBBox().width, 1), x1: +t.dataset.x1, x2: +t.dataset.x2,
                cy: +t.getAttribute('y'), rise: (ink.actualBoundingBoxAscent - ink.actualBoundingBoxDescent) / 2 };
    if (!runs.has(t.dataset.run)) runs.set(t.dataset.run, []);
    runs.get(t.dataset.run).push(d);
  }
  const list = [];
  for (const words of runs.values()) {
    words.sort((a, b) => b.x2 - a.x2);  // right to left
    let box = 0, nat = 0;
    for (const d of words) { box += d.x2 - d.x1; nat += d.w; }
    list.push({ words, box, nat, ratio: box / nat });
  }
  // one font size for the page: the box/text ratio below which lies FONT_QUANTILE of the text
  const sorted = list.slice().sort((a, b) => a.ratio - b.ratio);
  let total = 0, seen = 0, ps = sorted[0].ratio;
  for (const r of sorted) total += r.nat;
  for (const r of sorted) { seen += r.nat; ps = r.ratio; if (seen >= total * FONT_QUANTILE) break; }
  for (const r of list) {
    const n = r.words.length;
    let s = Math.min(Math.max(r.ratio / ps, MIN_STRETCH), MAX_STRETCH) * ps;
    // a run of few words in a wide space: beyond some room between the words, stretch them after all
    const markRight = !!r.words[0].t.dataset.er, markLeft = !!r.words[0].t.dataset.el;
    const slots = n - 1 + (markRight ? 1 : 0) + (markLeft ? 1 : 0);
    const room = MAX_EXTRA_GAP * 100 * ps * Math.max(slots, 1);
    if (r.box - s * r.nat > room) s = Math.min((r.box - room) / r.nat, LAST_RESORT_STRETCH * ps);
    const slack = r.box - s * r.nat;
    let extra = 0, right = r.words[0].x2;
    if (slack >= 0 && slots > 0) { extra = slack / slots; if (markRight) right -= extra; }
    else if (slack < 0 && n > 1) extra = slack / (n - 1);  // too dense even squeezed: the words close up
    else right -= slack / 2;
    for (let i = 0; i < n; i++) {
      const d = r.words[i], L = d.w * s, left = right - L;
      d.t.setAttribute('font-size', (100 * ps).toFixed(2));
      d.t.setAttribute('textLength', L.toFixed(1));
      d.t.setAttribute('lengthAdjust', 'spacingAndGlyphs');
      d.t.setAttribute('x', left.toFixed(1));
      d.t.setAttribute('y', (d.cy + d.rise * ps).toFixed(1));
      const top = d.t.nextElementSibling;
      if (top && top.classList.contains('top')) {
        for (const name of ['font-size', 'textLength', 'lengthAdjust', 'x', 'y']) top.setAttribute(name, d.t.getAttribute(name));
      }
      if (d.t.dataset.nm) {
        const mark = document.getElementById(d.t.dataset.nm);
        mark.setAttribute('x', (left - 3).toFixed(1));
        mark.setAttribute('width', (L + 6).toFixed(1));
      }
      if (i + 1 < n) right = left - (d.x1 - r.words[i + 1].x2) - extra;
    }
  }
}
document.fonts.ready.then(() => { document.querySelectorAll('svg.mushaf').forEach(layoutPage); document.body.dataset.ready = '1'; });
"""


def intro_html(data):
    allah = data.name_info["allah"]
    rabb = data.name_info["rabb"]
    legend = "".join(f'<span class="sw" style="background:{c}"></span><span class="lb">{t}</span> &nbsp; ' for c, t in LEGEND)
    return f"""
<section class="page intro">
  <h1>القرآن الكريم</h1>
  <div class="sub">مصحف التجويد الملوَّن مع أسماء الله وصفاته وشرحها</div>
  <h2>أحكام التجويد</h2>
  <p>لُوِّنت الكلمات على أحكام رواية حفص عن عاصم بألوان مصحف التجويد المعروف: <span class="legend">{legend}</span>
  ولا يُلوَّن المد الطبيعي (حركتان). الحرف الرمادي لا يُلفظ (همزة الوصل، اللام الشمسية، الحرف المدغَم بغير غنة).</p>
  <h2>أسماء الله وصفاته</h2>
  <p>أسماء الله الحسنى مظللة بالذهبي، وصفاته الواردة بألفاظ أخرى (كالاستواء واليد والوجه والمحبة) مظللة بالأصفر، وتجد شرحها في حاشية الصفحة.
  الشروح ملخصات على منهج أهل السنة والجماعة: إثبات ما أثبته الله لنفسه من غير تحريف ولا تعطيل ولا تكييف ولا تمثيل.
  ولأن لفظ الجلالة و«رب» يتكرران في أكثر الصفحات، شُرحا هنا مرة واحدة:</p>
  <p><b>{allah["arabic"]}</b>: {html.escape(allah["meaning"])}</p>
  <p><b>{rabb["arabic"]}</b>: {html.escape(rabb["meaning"])}</p>
  <p>وفي آخر المصحف فهرس كامل بالأسماء والصفات وشرحها ودليلها.</p>
  <h2>معاني الكلمات</h2>
  <p>في حاشية كل صفحة بيان لمعاني الكلمات الغريبة في آياتها، مرقّمة برقم الآية، من كتاب «السراج في بيان غريب القرآن» للدكتور محمد بن عبد العزيز الخضيري.</p>
  <h2>المراجع</h2>
  <p style="font-size:10pt">{html.escape(allah.get("sources", "") or "القواعد المثلى (ابن عثيمين)، فقه الأسماء الحسنى (عبد الرزاق البدر)، شرح أسماء الله الحسنى (سعيد القحطاني)، العقيدة الواسطية وشرحها، تفسير السعدي.")}</p>
</section>"""


def index_html(data):
    entries = sorted(data.name_info.values(), key=lambda n: n["order"])
    items = []
    for n in entries:
        kind = ' <span class="kind">(صفة)</span>' if n["kind"] == "attribute" else ""
        ev = f'<div class="ev">﴿ {html.escape(n["evidence"])} ﴾ <span class="rf">{html.escape(n["ref"])}</span></div>' if n["evidence"] else (f'<div class="rf">{html.escape(n["ref"])}</div>' if n["ref"] else "")
        note = f'<div class="nt">{html.escape(n["note"])}</div>' if n["note"] else ""
        items.append(f'<div class="e"><b>{html.escape(n["arabic"])}</b>{kind}: {html.escape(n["meaning"])}{ev}{note}</div>')
    return f'<section class="intro"><h2 style="text-align:center;margin-top:2mm">فهرس أسماء الله وصفاته</h2></section><div class="index">{"".join(items)}</div>'


def build_html(data, pages, images_dir, fonts, with_intro, with_index):
    uth = "file:///" + os.path.join(ROOT, "app/src/madani/assets/uthmanic_hafs_ver12.otf").replace(os.sep, "/")
    amiri = "file:///" + os.path.join(fonts, "Amiri-Regular.ttf").replace(os.sep, "/")
    amirib = "file:///" + os.path.join(fonts, "Amiri-Bold.ttf").replace(os.sep, "/")
    body = []
    if with_intro:
        body.append(intro_html(data))
    for page in pages:
        section, _ = render_page(data, page, images_dir)
        body.append(section)
    if with_index:
        body.append(index_html(data))
    return (
        '<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><title>مصحف</title>'
        f"<style>{CSS % dict(uth=uth, amiri=amiri, amirib=amirib)}</style></head><body>{''.join(body)}"
        f"<script>{JS}</script></body></html>"
    )


def print_pdf(html_path, pdf_path):
    cmd = [CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer", "--virtual-time-budget=60000",
           f"--print-to-pdf={pdf_path}", "file:///" + html_path.replace(os.sep, "/")]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pages", required=True, help="directory with page001.png … page604.png (1260 px wide)")
    ap.add_argument("--fonts", required=True, help="directory with Amiri-Regular.ttf and Amiri-Bold.ttf")
    ap.add_argument("--gharib", help="quran.ar.wordsmeaningssiraj.db (meanings of the difficult words)")
    ap.add_argument("--from", dest="start", type=int, default=1)
    ap.add_argument("--to", dest="end", type=int, default=604)
    ap.add_argument("--out", default=os.path.join(ROOT, "dist", "mushaf.pdf"))
    ap.add_argument("--work", default=os.path.join(HERE, "build"))
    ap.add_argument("--part", type=int, default=20, help="pages per Chrome run")
    ap.add_argument("--no-intro", action="store_true")
    ap.add_argument("--no-index", action="store_true")
    args = ap.parse_args()

    os.makedirs(args.work, exist_ok=True)
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    data = Data(args.gharib)
    pages = list(range(args.start, args.end + 1))
    parts = [pages[i:i + args.part] for i in range(0, len(pages), args.part)]
    pdfs = []
    for i, part in enumerate(parts):
        html_path = os.path.join(args.work, f"part{i:02d}.html")
        pdf_path = os.path.join(args.work, f"part{i:02d}.pdf")
        with open(html_path, "w", encoding="utf-8") as f:
            f.write(build_html(data, part, os.path.abspath(args.pages), os.path.abspath(args.fonts),
                               with_intro=(i == 0 and not args.no_intro),
                               with_index=(i == len(parts) - 1 and not args.no_index)))
        print_pdf(html_path, pdf_path)
        pdfs.append(pdf_path)
        print(f"part {i + 1}/{len(parts)}: pages {part[0]}-{part[-1]} -> {os.path.getsize(pdf_path) // 1024} KB", flush=True)

    from pypdf import PdfWriter
    writer = PdfWriter()
    for pdf in pdfs:
        writer.append(pdf)
    with open(args.out, "wb") as f:
        writer.write(f)
    print("written", args.out, os.path.getsize(args.out) // 1024, "KB")


if __name__ == "__main__":
    main()

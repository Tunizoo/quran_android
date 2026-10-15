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
import argparse, html, os, re, sqlite3, subprocess, sys, json

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


def spans_of(word_text, spans):
    """Splits a word into (text, colour) runs from the 'start-end:rule;...' span string."""
    runs = []
    cursor = 0
    parts = []
    for item in spans.split(";") if spans else []:
        rng, rule = item.split(":")
        a, b = (int(x) for x in rng.split("-"))
        colour = COLORS.get(rule)
        if colour:
            parts.append((a, b, colour))
    parts.sort()
    for a, b, colour in parts:
        if a < cursor:
            continue
        if a > cursor:
            runs.append((word_text[cursor:a], None))
        runs.append((word_text[a:b], colour))
        cursor = b
    if cursor < len(word_text):
        runs.append((word_text[cursor:], None))
    return runs


class Data:
    def __init__(self):
        self.tajweed = sqlite3.connect(os.path.join(ROOT, "feature/tajweed/src/main/assets/tajweed.db"))
        self.names = sqlite3.connect(os.path.join(ROOT, "feature/divinenames/src/main/assets/divine_names.db"))
        self.glyphs = sqlite3.connect(os.path.join(ROOT, "feature/divinenames/tools/db/ayahinfo_1260.db"))
        self.sura_names = load_sura_names()
        self.juz_pages = load_juz_pages()
        self.name_info = {
            r[0]: dict(id=r[0], arabic=r[1], meaning=r[2], evidence=r[3], ref=r[4], note=r[5], kind=r[6], order=r[7], sources=r[8])
            for r in self.names.execute("select id, arabic, meaning, evidence, evidence_ref, note, kind, sort_order, sources from names")
        }
        self.occurrences = {}
        for s, a, p, nid in self.names.execute("select sura, ayah, position, name_id from occurrences"):
            self.occurrences[(s, a, p)] = nid

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
    page_names = []  # distinct name ids in reading order
    first_sura = None
    for s, a, p, line, x1, y1, x2, y2 in glyphs:
        if first_sura is None:
            first_sura = s
        w, h = x2 - x1, y2 - y1
        if w < 20 or h < 40:
            continue  # pause marks and the like stay as drawn on the page
        word = data.word(s, a, p)
        if word is None:
            continue  # ayah marker or ornament
        text, spans = word
        nid = data.occurrences.get((s, a, p))
        if nid:
            fill = ATTR_FILL if data.name_info[nid]["kind"] == "attribute" else NAME_FILL
            names_rects.append(f'<rect x="{x1-3}" y="{y1-2}" width="{w+6}" height="{h+4}" rx="6" fill="{fill}"/>')
            if nid not in page_names:
                page_names.append(nid)
        covers.append(f'<rect x="{x1-1}" y="{y1-1}" width="{w+2}" height="{h+2}" fill="{PAPER}"/>')
        tspans = "".join(
            f'<tspan fill="{c}" stroke="{c}">{html.escape(t)}</tspan>' if c else html.escape(t) for t, c in spans_of(text, spans)
        )
        cx, cy = x1 + w / 2, y1 + h / 2
        texts.append(f'<text data-w="{w}" data-cx="{cx:.1f}" y="{cy:.1f}">{tspans}</text>')

    img = f"file:///{os.path.join(images_dir, f'page{page:03d}.png').replace(os.sep, '/')}"
    svg = (
        f'<svg class="mushaf" viewBox="0 0 {PAGE_W} {PAGE_H}">'
        f'<rect width="{PAGE_W}" height="{PAGE_H}" fill="{PAPER}"/>'
        f'<image href="{img}" width="{PAGE_W}" height="{PAGE_H}"/>'
        f'<g>{"".join(covers)}</g><g>{"".join(names_rects)}</g><g class="words">{"".join(texts)}</g></svg>'
    )

    # footnotes: the names and attributes of this page (the very frequent ones are in the intro)
    notes = [data.name_info[n] for n in page_names if n not in FREQUENT]
    total = sum(len(n["meaning"]) for n in notes)
    items = []
    for n in notes:
        meaning = n["meaning"] if (total <= 420 and len(notes) <= 6) else first_sentence(n["meaning"])
        kind = ' <span class="kind">(صفة)</span>' if n["kind"] == "attribute" else ""
        items.append(f'<li><b>{html.escape(n["arabic"])}</b>{kind}: {html.escape(meaning)}</li>')
    dense = " dense" if len(notes) > 6 else ""
    footnotes = f'<ol class="notes{dense}">{"".join(items)}</ol>' if items else '<div class="notes"></div>'

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
.page { height: 278mm; display: flex; flex-direction: column; page-break-after: always; break-after: page; }
.hdr { display: flex; justify-content: space-between; font-size: 10.5pt; font-weight: bold; color: #5b4a1e; padding: 0 2mm 1mm; }
.mushaf { width: 140mm; height: 226mm; align-self: center; direction: ltr; border: 0.3mm solid #c9b989; border-radius: 2mm; }
.mushaf text { font-family: "Uthmanic"; font-size: 100px; dominant-baseline: central; fill: #111; stroke: #111; stroke-width: 1.3px; stroke-linejoin: round; paint-order: stroke fill; }
.notes { flex: 1; overflow: hidden; margin: 1.5mm 1mm 0; padding: 0; font-size: 8.4pt; line-height: 1.4; }
ol.notes { list-style: none; border-top: 0.3mm solid #c9b989; padding-top: 1.2mm; }
.notes li { margin: 0 0 0.6mm 0; text-align: justify; }
.notes.dense { column-count: 2; column-gap: 5mm; font-size: 7.4pt; line-height: 1.28; }
.notes.dense li { break-inside: avoid; margin-bottom: 0.4mm; }
.notes b { color: #8a6500; }
.kind { color: #7a6a00; font-size: 7.5pt; }
.ftr { display: flex; justify-content: space-between; align-items: center; font-size: 7.5pt; color: #444; padding-top: 1mm; }
.legend .sw { display: inline-block; width: 3.2mm; height: 3.2mm; border-radius: 0.6mm; vertical-align: middle; margin: 0 0.8mm 0 1.8mm; }
.legend .lb { vertical-align: middle; }
.num { font-size: 10pt; font-weight: bold; }
.intro { font-size: 11.5pt; line-height: 1.8; padding: 4mm 6mm; }
.intro h1 { text-align: center; font-size: 26pt; margin: 30mm 0 4mm; color: #5b4a1e; }
.intro h2 { font-size: 15pt; color: #5b4a1e; margin: 6mm 0 2mm; }
.intro .sub { text-align: center; font-size: 14pt; color: #8a6500; margin-bottom: 20mm; }
.index { font-size: 9.5pt; line-height: 1.55; column-count: 2; column-gap: 7mm; padding: 2mm 3mm; }
.index .e { break-inside: avoid; margin-bottom: 2.4mm; }
.index .e b { color: #8a6500; font-size: 11pt; }
.index .ev { color: #1d5c2a; }
.index .rf { color: #666; font-size: 8pt; }
.index .nt { color: #555; font-size: 8.5pt; }
"""

JS = """
function layoutPage(svg) {
  const texts = Array.from(svg.querySelectorAll('text'));
  if (!texts.length) return;
  const data = texts.map(t => { t.setAttribute('font-size', '100'); const b = t.getBBox(); return { t, w: Math.max(b.width, 1), box: +t.dataset.w }; });
  const ratios = data.map(d => d.box / d.w).sort((a, b) => a - b);
  const ps = ratios[ratios.length >> 1];
  for (const d of data) {
    const s = Math.min(Math.max(d.box / d.w, ps * 0.85), ps * 1.45);
    const L = d.w * s;
    d.t.setAttribute('font-size', (100 * ps).toFixed(2));
    d.t.setAttribute('textLength', L.toFixed(1));
    d.t.setAttribute('lengthAdjust', 'spacingAndGlyphs');
    d.t.setAttribute('x', (+d.t.dataset.cx - L / 2).toFixed(1));
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
    data = Data()
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

"""Documento di controllo finale dei testi di Sorte (italiano · inglese · correzioni), da aprire in Pages.
Uso: ./gradlew :app:testDebugUnitTest --tests '*TestiTest*' && python3 tools/testi_controllo.py
Scrive TESTI_Sorte_controllo.docx nella cartella del progetto (non tocca il documento Pages su iCloud)."""
import html, json, os, re
from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt, RGBColor, Cm

R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = json.load(open(f"{R}/app/build/testi/testi.json"))
GREY = RGBColor(0x80, 0x80, 0x80)
doc = Document()
doc.styles["Normal"].font.name = "Helvetica Neue"; doc.styles["Normal"].font.size = Pt(10)
for s in doc.sections: s.left_margin = s.right_margin = s.top_margin = s.bottom_margin = Cm(1.6)

def shade(cell, hex_):
    tcPr = cell._tc.get_or_add_tcPr(); sh = OxmlElement("w:shd")
    sh.set(qn("w:val"), "clear"); sh.set(qn("w:color"), "auto"); sh.set(qn("w:fill"), hex_); tcPr.append(sh)
def h(text, level): doc.add_heading(text, level=level)
def note(text):
    r = doc.add_paragraph().add_run(text); r.italic = True; r.font.color.rgb = GREY; r.font.size = Pt(9)
def table(rows, label=None):
    hdr = ([label] if label else []) + ["Italiano", "English", "Correzioni"]
    t = doc.add_table(rows=1, cols=len(hdr)); t.style = "Table Grid"; t.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, x in enumerate(hdr):
        c = t.rows[0].cells[i]; c.text = x; shade(c, "EFEFEF")
        for r in c.paragraphs[0].runs: r.bold = True; r.font.size = Pt(9)
    for row in rows:
        cells = t.add_row().cells
        for i, x in enumerate(list(row) + [""]):
            cells[i].text = x
            for r in cells[i].paragraphs[0].runs: r.font.size = Pt(9.5)
        shade(cells[-1], "FFF7D6")
    ws = ([2.4] if label else []) + [6.0, 6.0, 4.0]
    for row in t.rows:
        for i, w in enumerate(ws): row.cells[i].width = Cm(w)
    doc.add_paragraph()

p = doc.add_paragraph(); r = p.add_run("SORTE · controllo finale dei testi"); r.bold = True; r.font.size = Pt(24)
note("Tutti i testi pubblici così come sono ora. Scrivi solo nella colonna gialla dove vuoi cambiare qualcosa; "
     "l'inglese posso adattarlo io dalla tua correzione italiana.")

# ── 2. APP ANDROID
h("1 · Il manuale (Come si gioca)", 1)
note("Uguale nell'app Android e nella web app, nell'ordine in cui appare. Nella web app mancano le parti sul retro del Phone (3) e sui widget.")
KIND = {"capitolo": "Capitolo", "titoletto": "Titoletto", "paragrafo": "Paragrafo", "riga": "Risposta",
        "esempio": "Esempio", "punto": "Elenco", "galleria": "Galleria"}
rows = []
for a, b in zip(T["manuale_it"], T["manuale_en"]):
    if a[0] == "galleria": rows.append(("Galleria", "(le 12 carte)", "(the 12 cards)")); continue
    if a[0] == "capitolo": rows.append(("Capitolo", f"{a[1]} · {a[2]}", f"{b[1]} · {b[2]}")); continue
    if a[0] == "riga": rows.append(("Risposta", f"{a[1]} — {a[2]}", f"{b[1]} — {b[2]}")); continue
    if a[0] == "esempio": rows.append(("Esempio", " → ".join(a[1:]), " → ".join(b[1:]))); continue
    rows.append((KIND[a[0]], a[1], b[1]))
table(rows, label="Tipo")
h("2 · App Android", 1)
h("Le scritte sulla matrice", 2)
table([(a[0], a[1], b[1]) for a, b in zip(T["matrice_it"], T["matrice_en"])], label="Risultato")
h("Interfaccia", 2)
table([(k, a[1], b[1]) for (k, _), a, b in zip(T["interfaccia_it"], T["interfaccia_it"], T["interfaccia_en"])], label="Dove")
h("Nomi e descrizioni dei Glyph Toys e dei widget", 2)
def strings(path): return dict(re.findall(r'<string name="([^"]+)">(.*?)</string>', open(path).read()))
si = strings(f"{R}/app/src/main/res/values-it/strings.xml"); se = strings(f"{R}/app/src/main/res/values/strings.xml")
table([(k, html.unescape(si.get(k, "")), html.unescape(se.get(k, ""))) for k in se], label="Chiave")
h("Crediti", 2)
note("Nella prima riga il nome porta al profilo Instagram.")
table(list(zip(T["crediti_it"], T["crediti_en"])))

# ── 1. LANDING
h("3 · Landing (mattiagiordano27.github.io/sorte)", 1)
src = open(f"{R}/docs/index.html").read()
body = src[src.index("<body"):]
pairs = re.findall(r'data-en="([^"]*)"\s+data-it="([^"]*)"', body)
seen, rows = set(), []
for en, it in pairs:
    en, it = html.unescape(en), html.unescape(it)
    if (it, en) in seen or en in ("EN", "IT"): continue
    seen.add((it, en)); rows.append((it, en))
table(rows)
h("Elenco delle funzioni", 2)
def items(block_id):
    b = body[body.index(f'id="{block_id}"'):]; b = b[:b.index("</div>")]
    return [re.sub(r"<[^>]+>", "", html.unescape(x)).strip() for x in re.findall(r"<(?:h3|li)>(.*?)</(?:h3|li)>", b, re.S)]
table(list(zip(items("news-it"), items("news-en"))))

# ── 3. WEB APP
h("4 · Web app (mattiagiordano27.github.io/sorte/app)", 1)
note("Usa gli stessi testi dell'app Android (manuale, interfaccia, crediti). Qui solo i testi in più.")
js = open(f"{R}/docs/app/app.js").read()
blk = js[js.index("const WEB = {"):js.index("};", js.index("const WEB = {"))]
it_b, en_b = blk[blk.index("it: {"):blk.index("en: {")], blk[blk.index("en: {"):]
kv = lambda b: dict(re.findall(r"(\w+): '([^']*)'(?:\s*\+\s*\n\s*'([^']*)')?", b) and
                    [(k, a + c) for k, a, c in re.findall(r"(\w+): '([^']*)'(?:\s*\+\s*\n\s*'([^']*)')?", b)])
ki, ke = kv(it_b), kv(en_b)
rows = [(k, html.unescape(ki[k]).replace("<br>", " / "), html.unescape(ke.get(k, "")).replace("<br>", " / ")) for k in ki]
si_, se_ = re.findall(r"\['([^']+)', '([^']+)'\]", it_b), re.findall(r"\['([^']+)', '([^']+)'\]", en_b)
rows += [("installazione", f"{a}: {b}", f"{c}: {d}") for (a, b), (c, d) in zip(si_, se_)]
table(rows, label="Chiave")

# ── 4. PUBBLICAZIONE
h("5 · Nota della release v0.27 (GitHub)", 1)
rel = open(f"{R}/pubblicazione/release_v0.27.md").read().split("\n---\n")
table([(rel[1].strip() if len(rel) > 1 else "", rel[0].strip())])
h("6 · Post per Nothing Community (bozza)", 1)
post = open(f"{R}/pubblicazione/post_nothing_community.md").read()
en = post[post.index("## English"):post.index("## Italiano")].replace("## English", "").strip(" \n-")
it = open(f"{R}/pubblicazione/post_nothing_community_IT.txt").read().strip()
table([(it, en)])
h("7 · Didascalie social (bozze)", 1)
soc = open(f"{R}/pubblicazione/social.md").read()
parts = re.split(r"\n## ", soc)[1:]
table([(p.split("\n", 1)[1].strip(), "") if "Italiano" in p.split("\n")[0] else ("", p.split("\n", 1)[1].strip()) for p in parts])

out = f"{R}/TESTI_Sorte_controllo.docx"; doc.save(out); print("ok:", out)

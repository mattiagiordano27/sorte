"""Documento dei testi di Sorte, da aprire in Pages (Word .docx).
Uso: ./gradlew :app:testDebugUnitTest --tests '*TestiTest*' && python3 tools/testi.py
Scrive TESTI_Sorte.docx nella cartella del progetto."""
import json, os, re
from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt, RGBColor, Cm

R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = json.load(open(f"{R}/app/build/testi/testi.json"))
RED = RGBColor(0xD7, 0x19, 0x21); GREY = RGBColor(0x80, 0x80, 0x80)

doc = Document()
st = doc.styles["Normal"]; st.font.name = "Helvetica Neue"; st.font.size = Pt(10)
for s in doc.sections:
    s.left_margin = s.right_margin = Cm(1.8); s.top_margin = s.bottom_margin = Cm(1.8)

def shade(cell, hex_):
    tcPr = cell._tc.get_or_add_tcPr(); sh = OxmlElement("w:shd")
    sh.set(qn("w:val"), "clear"); sh.set(qn("w:color"), "auto"); sh.set(qn("w:fill"), hex_); tcPr.append(sh)

def h(text, level):
    p = doc.add_heading(text, level=level)
    for r in p.runs: r.font.color.rgb = RED if level == 1 else RGBColor(0, 0, 0)

def note(text):
    p = doc.add_paragraph(); r = p.add_run(text); r.italic = True; r.font.color.rgb = GREY; r.font.size = Pt(9)

def table(rows, header=("Italiano (attuale)", "English (current)", "Tua versione"), label=None, widths=(6.2, 6.2, 5.6), highlight=False):
    cols = len(header) + (1 if label else 0)
    t = doc.add_table(rows=1, cols=cols); t.style = "Table Grid"; t.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr = ([label] if label else []) + list(header)
    for i, txt in enumerate(hdr):
        c = t.rows[0].cells[i]; c.text = txt
        for r in c.paragraphs[0].runs: r.bold = True; r.font.size = Pt(9)
        shade(c, "F0F0F0")
    for row in rows:
        cells = t.add_row().cells
        for i, txt in enumerate(row):
            cells[i].text = txt
            for r in cells[i].paragraphs[0].runs: r.font.size = Pt(9.5)
        if highlight: shade(cells[-1], "FFF7D6")
        else: shade(cells[-1], "FAFAFA")
    ws = ([2.6] if label else []) + list(widths)
    for row in t.rows:
        for i, w in enumerate(ws): row.cells[i].width = Cm(w)
    doc.add_paragraph()

KIND = {"capitolo": "Capitolo", "titoletto": "Titoletto", "paragrafo": "Paragrafo", "riga": "Tabella",
        "esempio": "Esempio", "punto": "Elenco", "galleria": "Galleria"}

# ── copertina
p = doc.add_paragraph(); r = p.add_run("SORTE"); r.bold = True; r.font.size = Pt(30)
p = doc.add_paragraph(); r = p.add_run("Tutti i testi dell'app, da rivedere e riscrivere"); r.font.size = Pt(13)
note("Ogni testo è in una tabella: italiano attuale · inglese attuale · colonna gialla/grigia vuota per la tua versione. "
     "Scrivi solo dove vuoi cambiare; il resto resta com'è. L'inglese posso ritradurlo io dalla tua versione italiana. "
     "Le parti in evidenza (gialle) sono le tabelle delle risposte: quelle che volevi riscrivere per prime.")

# ── 1. la tabella delle risposte (in evidenza)
h("1 · Le risposte dell'oracolo chiuso", 1)
note("Probabilità (dado da cento): NO 22% · NO, MA… 22% · MAH 12% · SÌ, MA… 22% · SÌ 22%.")
it_rows = [b for b in T["manuale_it"] if b[0] == "riga"]; en_rows = [b for b in T["manuale_en"] if b[0] == "riga"]
table([(f"{a[1]} — {a[2]}", f"{b[1]} — {b[2]}", "") for a, b in zip(it_rows, en_rows)], highlight=True)
h("Le scritte sulla matrice", 2)
note("Quello che compare sui LED: parola principale / «ma» (che arriva dopo una pausa). Spazio sulla matrice: ~4 lettere grandi, ~5 piccole.")
table([(a[0], a[1], b[1], "") for a, b in zip(T["matrice_it"], T["matrice_en"])], label="Risultato")

# ── 2. il manuale, capitolo per capitolo
h("2 · Il manuale (Come si gioca)", 1)
note("Nell'ordine in cui appare nell'app. «Galleria» = qui compaiono le 12 carte (non è testo).")
section = []; title = "Introduzione"
def flush():
    if section:
        h(title, 2); table(section, label="Tipo"); section.clear()
for a, b in zip(T["manuale_it"], T["manuale_en"]):
    if a[0] == "capitolo":
        flush(); title = f"Capitolo {a[1]} · {a[2]}"
        section.append(("Capitolo", f"{a[1]} · {a[2]}", f"{b[1]} · {b[2]}", "")); continue
    if a[0] == "galleria":
        section.append(("Galleria", "(le 12 carte)", "(the 12 cards)", "")); continue
    if a[0] == "riga":
        continue                                       # già nella sezione 1
    section.append((KIND[a[0]], a[1], b[1], ""))
flush()

# ── 3. interfaccia
h("3 · Interfaccia dell'app", 1)
table([(k, a[1], b[1], "") for (k, _), a, b in zip(T["interfaccia_it"], T["interfaccia_it"], T["interfaccia_en"])], label="Dove")

h("Nomi e descrizioni dei Glyph Toy e dei widget", 2)
def strings(path):
    s = open(path).read(); return dict(re.findall(r'<string name="([^"]+)">(.*?)</string>', s))
si = strings(f"{R}/app/src/main/res/values-it/strings.xml"); se = strings(f"{R}/app/src/main/res/values/strings.xml")
table([(k, si.get(k, ""), se.get(k, ""), "") for k in se], label="Chiave")

# ── 4. crediti
h("4 · Crediti", 1)
table([(a, b, "") for a, b in zip(T["crediti_it"], T["crediti_en"])])

# ── 5. bozze per la pubblicazione
h("5 · Bozze per la pubblicazione", 1)
note("Da riscrivere con la tua voce. GitHub e Nothing Community sono in inglese (pubblico internazionale); tieni anche l'italiano se vuoi.")
h("Descrizione breve (GitHub, store)", 2)
table([("Sorte — oracolo per scrittura creativa, divinazione e gioco di ruolo. Tre strumenti: un oracolo sì/no fatto di vapore, "
        "un oracolo aperto fatto di fuoco che lascia i simboli del mazzo di Mattia Giordano, e dadi. Su Nothing Phone (3) "
        "vivono anche sul retro, come Glyph Toy.",
        "Sorte — an oracle for creative writing, divination and role-playing. Three tools: a yes/no oracle made of steam, an "
        "open oracle made of fire that leaves symbols from Mattia Giordano's oracle deck, and dice. On Nothing Phone (3) they "
        "also live on the back, as Glyph Toys.", "")])
h("Post per Nothing Community", 2)
note("Dove: nothing.community → la sezione dedicata ai progetti della community / Glyph Toys (controlla il nome esatto al momento). "
     "Allega 2–4 immagini o un breve video del retro del telefono, e il link alla pagina Releases di GitHub.")
post_en = ("Title: Sorte — an oracle for creative writing, divination and role-playing (3 Glyph Toys)\n\n"
    "Hi everyone! I'm Mattia Giordano, an artist from Turin. I draw in dots: images that disappear from afar and only "
    "reveal themselves up close. The Glyph Matrix felt like the natural home for them, so I made Sorte.\n\n"
    "• Closed Oracle — ask a yes/no question, long press: a puff of steam clears and leaves the answer (NO · NO, BUT… · "
    "MEH · YES, BUT… · YES). The “but” arrives after a breath.\n"
    "• Open Oracle — a small flame touches the paper, the fire burns it and leaves a symbol from my personal oracle deck.\n"
    "• Dice — d4 to d100 as silhouettes: turn the phone face down and shake (or tilt it like a tray while charging).\n\n"
    "It's free and open source, works as a normal app on any Android 10+, and has three round widgets. Sounds are "
    "steam, fire and dice on felt (they can be turned off).\n\n"
    "How to install: download the APK from GitHub Releases (link), allow installing from your browser, open Sorte and "
    "tap “Add to Glyph Toys”.\n\n"
    "Feedback welcome — especially on how the animations look on your matrix!")
post_it = ("Titolo: Sorte — un oracolo per scrittura creativa, divinazione e gioco di ruolo (3 Glyph Toy)\n\n"
    "Ciao a tutti! Sono Mattia Giordano, artista di Torino. Disegno a puntini: immagini che da lontano scompaiono e si "
    "rivelano solo da vicino. La Glyph Matrix mi è sembrata la loro casa naturale, così ho fatto Sorte.\n\n"
    "• Oracolo chiuso — fai una domanda sì/no, pressione lunga: uno sbuffo di vapore si dirada e lascia la risposta.\n"
    "• Oracolo aperto — una fiammella tocca la carta, il fuoco la brucia e lascia un simbolo del mio mazzo oracolare.\n"
    "• Dadi — dal d4 al d100 come sagome: capovolgi il telefono e scuoti.\n\n"
    "È gratis e open source, funziona come app su qualsiasi Android 10+ e ha tre widget tondi.\n\n"
    "Come installarlo: scarica l'APK da GitHub Releases (link), consenti l'installazione dal browser, apri Sorte e tocca "
    "«Aggiungi ai Glyph Toy».")
table([(post_it, post_en, "")])

out = f"{R}/TESTI_Sorte.docx"
doc.save(out); print("ok:", out)

"""Carosello di presentazione (1080×1350) e grafica in evidenza, in inglese e italiano,
nello stile delle schede "novità": etichetta in alto, nome a puntini, una frase, la matrice.
Uso (dopo tools/presentazione.py): python3 tools/carosello.py → presentazione/grafica/"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
M = f"{R}/presentazione/matrice"
G = f"{R}/presentazione/grafica"
W, H = 1080, 1350
WHITE, GREY, DIM = (242, 242, 242), (140, 140, 140), (85, 85, 85)

def doto(size):
    f = ImageFont.truetype(f"{R}/app/src/main/res/font/doto.ttf", size)
    f.set_variation_by_axes([100, 900])          # puntini tondi, peso pieno (come nell'app)
    return f
MONO = ImageFont.truetype("/System/Library/Fonts/Menlo.ttc", 26)
BODY = ImageFont.truetype("/System/Library/Fonts/HelveticaNeue.ttc", 40)

def frame(name, i):
    return Image.open(f"{M}/{name}/frame_{i:05d}.png")

def symbol(n):
    return Image.open(f"{M}/simboli/{n}.png")

def wrap(d, text, font, width):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if d.textlength(t, font=font) <= width: cur = t
        else: lines.append(cur); cur = w
    lines.append(cur); return lines

def card(label, title, body, visual, path):
    img = Image.new("RGB", (W, H), (0, 0, 0)); d = ImageDraw.Draw(img)
    d.text((80, 80), label.upper(), font=MONO, fill=GREY)
    v = visual.resize((760, 760)); img.paste(v, ((W - 760) // 2, 150))
    d.text((80, 950), title, font=doto(96), fill=WHITE)
    y = 1090
    for line in wrap(d, body, BODY, W - 160):
        d.text((80, y), line, font=BODY, fill=GREY); y += 54
    img.save(path)

def grid(names, cols, size=760):
    out = Image.new("RGB", (size, size), (0, 0, 0)); cell = size // cols
    for k, n in enumerate(names):
        out.paste(symbol(n).resize((cell - 8, cell - 8)), ((k % cols) * cell + 4, (k // cols) * cell + 4))
    return out

def row(images, size=760):
    out = Image.new("RGB", (size, size), (0, 0, 0)); w = size // len(images)
    for k, im in enumerate(images): out.paste(im.resize((w - 10, w - 10)), (k * w + 5, (size - w) // 2))
    return out

SYM = ["finestra", "stella", "palma", "porta", "lucchetto", "recinto", "scatola", "nuvole", "sedia", "bicchiere", "presa_elettrica", "chiave"]
VIS = {
    "cover": symbol("chiave"),
    "closed": frame("01_oracolo_chiuso", 120),
    "open": frame("03_oracolo_aperto", 150),
    "dice": frame("04_dadi_d20", 130),
    "symbols": grid(SYM, 4),
    "widgets": row([frame("01_oracolo_chiuso", 60), frame("03_oracolo_aperto", 70), frame("04_dadi_d20", 10)]),
    "free": symbol("porta"),
}
TEXT = {
    "en": [
        ("Sorte 0.27", "Sorte", "An oracle for creative writing, divination and role-playing.", "cover"),
        ("Glyph Toy · 01", "Closed Oracle", "For questions you can answer with a yes or a no. A small cloud falls onto hot stones, turns to rain, then to steam, and rises again leaving the answer.", "closed"),
        ("Glyph Toy · 02", "Open Oracle", "A little flame catches the paper. What the fire leaves behind is a symbol to read.", "open"),
        ("Glyph Toy · 03", "Dice", "d4 to d100 as silhouettes. Shake it face down; while charging, lift one edge and set it back down.", "dice"),
        ("Symbols", "The deck", "Twelve cards from Mattia Giordano's personal oracle deck, drawn in dots.", "symbols"),
        ("Everywhere", "Widgets", "Three round widgets on Android, Glyph Toys on Nothing Phone (3), and a web version for iPhone, iPad and any computer.", "widgets"),
        ("Free", "Open source", "Free on GitHub and in your browser. Concept, development, drawings and sounds by Mattia Giordano.", "free"),
    ],
    "it": [
        ("Sorte 0.27", "Sorte", "Un oracolo per scrittura creativa, divinazione e gioco di ruolo.", "cover"),
        ("Glyph Toy · 01", "Oracolo chiuso", "Per le domande a cui si risponde sì o no. Una piccola nuvola cade sulle pietre calde, si fa pioggia, poi vapore, e risale lasciando la risposta.", "closed"),
        ("Glyph Toy · 02", "Oracolo aperto", "Una fiammella brucia la carta. Quello che il fuoco lascia è un simbolo da leggere.", "open"),
        ("Glyph Toy · 03", "Dadi", "Dal d4 al d100, come sagome. Scuoti a faccia in giù; in carica, sollevane un lato e riappoggialo.", "dice"),
        ("Simboli", "Il mazzo", "Dodici carte del mazzo oracolare personale di Mattia Giordano, disegnate a puntini.", "symbols"),
        ("Ovunque", "Widget", "Tre widget tondi su Android, Glyph Toys su Nothing Phone (3) e una versione web per iPhone, iPad e qualsiasi computer.", "widgets"),
        ("Gratis", "Open source", "Gratis su GitHub e nel browser. Ideazione, sviluppo, disegni e suoni di Mattia Giordano.", "free"),
    ],
}
for lang, slides in TEXT.items():
    d = f"{G}/carosello_{lang.upper()}"; os.makedirs(d, exist_ok=True)
    for k, (label, title, body, vis) in enumerate(slides, 1):
        card(label, title, body, VIS[vis], f"{d}/{k:02d}_{vis}.png")

# grafica in evidenza 1024×500, inglese e italiano
key = Image.open(f"{R}/app/src/main/assets/icone/chiave.png").convert("L")
pts = [(x, y) for y in range(25) for x in range(25) if key.getpixel((x, y)) > 100]
cx = (min(p[0] for p in pts) + max(p[0] for p in pts)) / 2; cy = (min(p[1] for p in pts) + max(p[1] for p in pts)) / 2
for lang, l1, l2 in [("EN", "AN ORACLE FOR CREATIVE WRITING,", "DIVINATION AND ROLE-PLAYING"),
                     ("IT", "ORACOLO PER SCRITTURA CREATIVA,", "DIVINAZIONE E GIOCO DI RUOLO")]:
    fg = Image.new("L", (1024, 500), 0); d0 = ImageDraw.Draw(fg)
    for x, y in pts:
        px = 250 + (x - cx) * 17; py = 250 + (y - cy) * 17; d0.ellipse((px - 6.2, py - 6.2, px + 6.2, py + 6.2), fill=255)
    a = np.clip(np.asarray(fg, float) + np.asarray(fg.filter(ImageFilter.GaussianBlur(8)), float) * 0.5, 0, 255).astype(np.uint8)
    img = Image.fromarray(a).convert("RGB"); d = ImageDraw.Draw(img)
    mono = ImageFont.truetype("/System/Library/Fonts/Menlo.ttc", 19)
    d.text((436, 140), "Sorte", font=doto(130), fill=WHITE)
    d.text((446, 300), l1, font=mono, fill=GREY); d.text((446, 328), l2, font=mono, fill=GREY)
    d.text((446, 388), "MATTIA GIORDANO", font=mono, fill=DIM)
    img.save(f"{G}/evidenza_1024x500_{lang}.png")
old = f"{G}/evidenza_1024x500.png"
if os.path.exists(old): os.remove(old)
print("ok")

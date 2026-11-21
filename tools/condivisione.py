"""Immagine di anteprima per i link alla versione web (WhatsApp, social): 1200×630, docs/media/og_app.png.
(La landing ha la sua: docs/media/og.png.)"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
W, H = 1200, 630
WHITE, GREY, DIM = (242, 242, 242), (150, 150, 150), (95, 95, 95)
def doto(size):
    f = ImageFont.truetype(f"{R}/app/src/main/res/font/doto.ttf", size); f.set_variation_by_axes([100, 900]); return f
mono = lambda s: ImageFont.truetype("/System/Library/Fonts/Menlo.ttc", s)

def _key_dots(cx, cy, step):
    key = Image.open(f"{R}/app/src/main/assets/icone/chiave.png").convert("L")
    pts = [(x, y) for y in range(25) for x in range(25) if key.getpixel((x, y)) > 100]
    mx = (min(p[0] for p in pts) + max(p[0] for p in pts)) / 2; my = (min(p[1] for p in pts) + max(p[1] for p in pts)) / 2
    fg = Image.new("L", (W, H), 0); d = ImageDraw.Draw(fg); r = step * 0.37
    for x, y in pts:
        px = cx + (x - mx) * step; py = cy + (y - my) * step; d.ellipse((px - r, py - r, px + r, py + r), fill=255)
    a = np.clip(np.asarray(fg, float) + np.asarray(fg.filter(ImageFilter.GaussianBlur(8)), float) * 0.5, 0, 255).astype(np.uint8)
    return Image.fromarray(a).convert("RGB")

def text(img, lines, x):
    d = ImageDraw.Draw(img)
    d.text((x, 150), "Sorte", font=doto(150), fill=WHITE)
    y = 345
    for t, col in lines:
        if t is None: y += 22; continue
        d.text((x, y), t, font=mono(24), fill=col); y += 36

# web app: il tondo con la risposta, preso dalla schermata
shot = Image.open(f"{R}/docs/media/web.png").convert("RGB").crop((420, 115, 860, 555)).resize((470, 470), Image.LANCZOS)
img = Image.new("RGB", (W, H), (0, 0, 0)); img.paste(shot, (55, 80))
text(img, [("In your browser", GREY), ("iPhone · iPad · any computer", GREY), (None, None),
           ("Free · works offline", DIM), ("MATTIA GIORDANO", DIM)], 590)
img.save(f"{R}/docs/media/og_app.png", optimize=True)
print("ok")

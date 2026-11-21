"""Genera icone SEGNAPOSTO 25x25 per l'oracolo aperto (bianco su nero).
Vanno sostituite con le icone vere: basta cancellare i file segnaposto_*.png."""
import math, sys
from PIL import Image, ImageDraw

out = sys.argv[1]
W = 255

def icon(name, fn):
    im = Image.new("L", (25, 25), 0)
    fn(ImageDraw.Draw(im))
    im.save(f"{out}/segnaposto_{name}.png")

def luna(d):
    d.ellipse((4, 4, 20, 20), fill=W); d.ellipse((9, 2, 23, 16), fill=0)
def occhio(d):
    d.ellipse((3, 7, 21, 17), outline=W); d.ellipse((9, 9, 15, 15), fill=W)
def onda(d):
    for y0 in (7, 12, 17):
        pts = [(x, y0 + round(2 * math.sin(x / 2.2))) for x in range(3, 22)]
        d.line(pts, fill=W)
def stella(d):
    pts = []
    for i in range(10):
        r = 10 if i % 2 == 0 else 4
        a = -math.pi / 2 + i * math.pi / 5
        pts.append((12 + r * math.cos(a), 12.5 + r * math.sin(a)))
    d.polygon(pts, fill=W)
def porta(d):
    d.rectangle((7, 9, 17, 21), outline=W); d.arc((7, 4, 17, 14), 180, 360, fill=W)
    d.point((15, 15), fill=W)
def chiave(d):
    d.ellipse((3, 8, 11, 16), outline=W); d.line((11, 12, 21, 12), fill=W)
    d.line((18, 12, 18, 15), fill=W); d.line((21, 12, 21, 16), fill=W)

for n, f in [("luna", luna), ("occhio", occhio), ("onda", onda), ("stella", stella), ("porta", porta), ("chiave", chiave)]:
    icon(n, f)
print("ok")

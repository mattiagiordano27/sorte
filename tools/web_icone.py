"""Icone dell'app web: la chiave a puntini su nero (192, 512, 180 per iPhone, maskable)."""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
H = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs", "app")
key = Image.open(f"{H}/icone/chiave.png").convert("L")
pts = [(x, y, key.getpixel((x, y)) / 255) for y in range(25) for x in range(25) if key.getpixel((x, y)) > 60]
cx = (min(p[0] for p in pts) + max(p[0] for p in pts)) / 2; cy = (min(p[1] for p in pts) + max(p[1] for p in pts)) / 2
span = max(max(p[0] for p in pts) - min(p[0] for p in pts), max(p[1] for p in pts) - min(p[1] for p in pts)) + 1
def icon(size, fill):
    S = size * 4; im = Image.new("L", (S, S), 0); d = ImageDraw.Draw(im)
    step = S * fill / span; r = step * 0.38
    for x, y, v in pts:
        px = S / 2 + (x - cx) * step; py = S / 2 + (y - cy) * step
        d.ellipse((px - r, py - r, px + r, py + r), fill=int(255 * v))
    a = np.clip(np.asarray(im, float) + np.asarray(im.filter(ImageFilter.GaussianBlur(step * 0.5)), float) * 0.45, 0, 255).astype(np.uint8)
    return Image.fromarray(a).resize((size, size), Image.LANCZOS).convert("RGB")
icon(192, 0.62).save(f"{H}/icon-192.png"); icon(512, 0.62).save(f"{H}/icon-512.png")
icon(180, 0.62).save(f"{H}/icon-180.png"); icon(512, 0.48).save(f"{H}/icon-maskable.png")
print("ok")

"""Striscia di fotogrammi dell'animazione: python3 tools/film.py [ogni_n] → app/build/anteprime/film.png"""
import os, sys
from PIL import Image, ImageDraw
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
N, FULL = 25, 4095
every = int(sys.argv[1]) if len(sys.argv) > 1 else 4
rows = [l.rstrip("\n").split("\t") for l in open(f"{ROOT}/app/build/anteprime/film.txt")]
rows = rows[::every] + [rows[-1]]
cell, pad, cols = 7, 10, 8
tile = N * cell
img = Image.new("RGB", (cols * (tile + pad) + pad, ((len(rows) + cols - 1) // cols) * (tile + pad + 16) + pad), (18, 18, 18))
d = ImageDraw.Draw(img)
for k, (ms, data) in enumerate(rows):
    px = [int(v) for v in data.split(",")]
    ox = pad + (k % cols) * (tile + pad); oy = pad + (k // cols) * (tile + pad + 16)
    d.ellipse((ox - 2, oy - 2, ox + tile + 2, oy + tile + 2), fill=(0, 0, 0))
    for y in range(N):
        for x in range(N):
            if (x - 12) ** 2 + (y - 12) ** 2 > 156: continue
            l = 28 + px[y * N + x] * 227 // FULL
            d.rectangle((ox + x * cell + 1, oy + y * cell + 1, ox + (x + 1) * cell - 1, oy + (y + 1) * cell - 1), fill=(l, l, l))  # LED quadrati
    d.text((ox, oy + tile + 3), f"{ms} ms", fill=(150, 150, 150))
img.save(f"{ROOT}/app/build/anteprime/film.png"); print("ok")

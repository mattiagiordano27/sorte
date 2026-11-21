"""Immagini della landing e del README. Regola:
- landing (docs/media/): PUNTI TONDI come nella matrice dell'app → closed_en/it, open, dice, sym_*;
- README su GitHub, sezione Glyph Toy: LED QUADRATI come il retro del telefono → docs/media/readme/glyph_*.gif.
Uso: ./gradlew :app:testDebugUnitTest --tests '*PresentazioneTest*' && python3 tools/landing_media.py"""
import os
from PIL import Image, ImageDraw

R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = f"{R}/app/build/presentazione"; OUT = f"{R}/docs/media"
N, FULL, SS = 25, 4095, 3                      # SS = sovracampionamento per cerchi morbidi
inside = lambda x, y: (x - 12) ** 2 + (y - 12) ** 2 <= 156

def render(px, size=400):
    """Come la matrice dell'app: sfondo nero, punti tondi, spenti grigio scuro (#1C1C1C), accesi fino al bianco."""
    S = size * SS; img = Image.new("L", (S, S), 0); d = ImageDraw.Draw(img)
    pitch = S / (N + 1); r = pitch * 0.36
    for y in range(N):
        for x in range(N):
            if not inside(x, y): continue
            v = px[y * N + x] / FULL; l = round(28 + (255 - 28) * v)
            cx = pitch * (x + 1); cy = pitch * (y + 1)
            d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=l)
    return img.resize((size, size), Image.LANCZOS)

def render_square(px, size=400):
    """Come il retro del telefono (Glyph Matrix): LED quadrati con un leggero alone di luce."""
    import numpy as np
    from PIL import ImageFilter
    S = size * SS; cell = S / (N + 2); led = cell * 0.8; off = (cell - led) / 2
    lit = Image.new("L", (S, S), 0); dl = ImageDraw.Draw(lit); base = Image.new("L", (S, S), 0); db = ImageDraw.Draw(base)
    for y in range(N):
        for x in range(N):
            if not inside(x, y): continue
            x0 = cell * (x + 1) + off; y0 = cell * (y + 1) + off
            db.rectangle((x0, y0, x0 + led, y0 + led), fill=16)
            v = px[y * N + x] * 255 // FULL
            if v: dl.rectangle((x0, y0, x0 + led, y0 + led), fill=v)
    glow = lit.filter(ImageFilter.GaussianBlur(S / 90))
    a = np.maximum(np.asarray(base, float), np.asarray(lit, float)) + np.asarray(glow, float) * 0.45
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).resize((size, size), Image.LANCZOS)

def rows(name):
    out = []
    for line in open(f"{SRC}/{name}.txt"):
        t, _, data = line.rstrip("\n").split("\t"); out.append((int(t), [int(v) for v in data.split(",")]))
    return out

def gif(names, path, size=400, min_ms=40, draw=None):
    draw = draw or render
    frames, durs = [], []
    for name in names:
        rs = rows(name)
        for i, (t, px) in enumerate(rs):
            dur = (rs[i + 1][0] - t) if i + 1 < len(rs) else 1500
            if frames and px == frames[-1][0]: durs[-1] += dur; continue
            frames.append((px, None)); durs.append(dur)
    # fotogrammi troppo brevi per una GIF: si accorpano al precedente
    out, od = [], []
    for (px, _), d in zip(frames, durs):
        if out and od[-1] < min_ms: out[-1] = px; od[-1] += d
        else: out.append(px); od.append(d)
    imgs = [draw(px, size) for px in out]
    imgs[0].save(path, save_all=True, append_images=imgs[1:], duration=[max(min_ms, int(d)) for d in od], loop=0, optimize=True)
    print(os.path.basename(path), len(imgs), "fotogrammi", os.path.getsize(path) // 1024, "KB")

gif(["01_oracolo_chiuso_EN"], f"{OUT}/closed_en.gif")
gif(["01_oracolo_chiuso"], f"{OUT}/closed_it.gif")
gif(["03_oracolo_aperto"], f"{OUT}/open.gif")
gif(["04_dadi_d20", "04_dadi_d6"], f"{OUT}/dice.gif")
# README, sezione Glyph Toy: quadrati
gif(["01_oracolo_chiuso_EN"], f"{OUT}/readme/glyph_closed.gif", draw=render_square, min_ms=80)
gif(["03_oracolo_aperto"], f"{OUT}/readme/glyph_open.gif", draw=render_square, min_ms=80)
gif(["04_dadi_d20", "04_dadi_d6"], f"{OUT}/readme/glyph_dice.gif", draw=render_square, min_ms=80)
for line in open(f"{SRC}/05_simboli.txt"):
    n, _, data = line.rstrip("\n").split("\t")
    render([int(v) for v in data.split(",")], 240).save(f"{OUT}/sym_{n}.png", optimize=True)
print("simboli: ok")

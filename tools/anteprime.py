"""Disegna come appariranno i toy sulla Glyph Matrix e rigenera le anteprime.
Uso:  ./gradlew :app:testDebugUnitTest && python3 tools/anteprime.py
Scrive app/build/anteprime/foglio.png e app/src/main/res/drawable/{preview_*,ic_launcher_fg}.xml"""
import glob, os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app")
N, FULL = 25, 4095
inside = lambda x, y: (x - 12) ** 2 + (y - 12) ** 2 <= 156

frames = []
for line in open(f"{APP}/build/anteprime/frames.txt"):
    name, data = line.rstrip("\n").split("\t")
    frames.append((name, [int(v) for v in data.split(",")]))
for p in sorted(glob.glob(f"{APP}/src/main/assets/icone/*.png")):
    im = Image.open(p).convert("LA").resize((N, N), Image.NEAREST)
    frames.append((os.path.basename(p)[:-4], [l * a // 255 * FULL // 255 for l, a in im.getdata()]))
by = dict(frames)

# foglio di controllo
cell, pad, lab, cols = 12, 24, 26, 6
tile = N * cell
rows = (len(frames) + cols - 1) // cols
sheet = Image.new("RGB", (cols * (tile + pad) + pad, rows * (tile + pad + lab) + pad), (18, 18, 18))
d = ImageDraw.Draw(sheet)
for i, (name, px) in enumerate(frames):
    ox = pad + (i % cols) * (tile + pad); oy = pad + (i // cols) * (tile + pad + lab)
    d.ellipse((ox - 4, oy - 4, ox + tile + 4, oy + tile + 4), fill=(0, 0, 0))
    for y in range(N):
        for x in range(N):
            if not inside(x, y): continue
            l = 28 + px[y * N + x] * (255 - 28) // FULL
            d.rectangle((ox + x * cell + 1, oy + y * cell + 1, ox + (x + 1) * cell - 2, oy + (y + 1) * cell - 2), fill=(l, l, l))  # LED quadrati come sulla Glyph Matrix
    d.text((ox, oy + tile + 8), name, fill=(170, 170, 170))
sheet.save(f"{APP}/build/anteprime/foglio.png")

def vector(px, launcher=False):
    size, pitch, led = (108, 2.2, 1.7) if launcher else (512, 17.82, 13.05)
    off = (size - pitch * N) / 2 + (pitch - led) / 2
    groups = {}
    for y in range(N):
        for x in range(N):
            if not inside(x, y): continue
            v = px[y * N + x]
            if launcher and v == 0: continue
            l = 0x1C if v == 0 else 0x55 + v * (0xFF - 0x55) // FULL
            if launcher: l = 0xFF
            c = f"#{l:02X}{l:02X}{l:02X}"
            groups.setdefault(c, []).append(f"M{off + x * pitch:.2f},{off + y * pitch:.2f}h{led:.2f}v{led:.2f}h-{led:.2f}z")
    s = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"',
         f'    android:width="{size}dp" android:height="{size}dp"',
         f'    android:viewportWidth="{size}" android:viewportHeight="{size}">']
    if not launcher:
        s.append('  <path android:fillColor="#000000" android:pathData="M256,256m-256,0a256,256 0,1 1,512 0a256,256 0,1 1,-512 0"/>')
    for c, p in sorted(groups.items()):
        s.append(f'  <path android:fillColor="{c}" android:pathData="{"".join(p)}"/>')
    s.append("</vector>\n")
    return "\n".join(s)

res = f"{APP}/src/main/res/drawable"
icon = next((px for n, px in frames if n.startswith(("segnaposto_occhio",))), None) \
    or next((px for n, px in frames if not n.startswith(("IT ", "EN ", "d", "closed", "open", "idle", "aria"))), by["open_idle"])
open(f"{res}/preview_closed.xml", "w").write(vector(by["closed_wait"]))
open(f"{res}/preview_open.xml", "w").write(vector(by["open_flame"]))
open(f"{res}/preview_dice.xml", "w").write(vector(by["idle_d20"]))
# icona dell app: tools/icona_app.py (chiave a puntini)
print("ok:", f"{APP}/build/anteprime/foglio.png")

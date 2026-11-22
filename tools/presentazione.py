"""Materiale di presentazione: animazioni della Glyph Matrix come sequenze PNG 1080×1080 a 25 fps + audio WAV
sincronizzato + GIF di anteprima; i simboli come immagini singole.
Uso: ./gradlew :app:testDebugUnitTest --tests '*PresentazioneTest*' && python3 tools/presentazione.py
Scrive in _VIDEO/matrice/."""
import bisect, glob, os, wave
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = f"{R}/app/build/presentazione"
OUT = f"{R}/_VIDEO/matrice"
N, FULL, SIZE, FPS = 25, 4095, 1080, 25
inside = lambda x, y: (x - 12) ** 2 + (y - 12) ** 2 <= 156

def render(px, size=SIZE):
    """Un fotogramma: LED quadrati su nero, spenti appena visibili, con un leggero alone di luce."""
    cell = size / (N + 2); led = cell * 0.8; off = (cell - led) / 2
    lit = Image.new("L", (size, size), 0); dl = ImageDraw.Draw(lit)
    base = Image.new("L", (size, size), 0); db = ImageDraw.Draw(base)
    for y in range(N):
        for x in range(N):
            if not inside(x, y): continue
            x0 = cell * (x + 1) + off; y0 = cell * (y + 1) + off
            db.rectangle((x0, y0, x0 + led, y0 + led), fill=16)
            v = px[y * N + x] * 255 // FULL
            if v: dl.rectangle((x0, y0, x0 + led, y0 + led), fill=v)
    glow = lit.filter(ImageFilter.GaussianBlur(size / 90))
    a = np.maximum(np.asarray(base, float), np.asarray(lit, float)) + np.asarray(glow, float) * 0.45
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).convert("RGB")

def load(name):
    rows = []
    for line in open(f"{SRC}/{name}.txt"):
        t, snd, data = line.rstrip("\n").split("\t")
        rows.append((t, snd, [int(v) for v in data.split(",")]))
    return rows

def wav_samples(name):
    with wave.open(f"{R}/app/src/main/res/raw/{name}.wav") as w:
        return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(float) / 32768, w.getframerate()

os.makedirs(OUT, exist_ok=True)
for path in sorted(glob.glob(f"{SRC}/0[1-4]_*.txt")):
    name = os.path.basename(path)[:-4]
    rows = [(int(t), s, px) for t, s, px in load(name)]
    end = rows[-1][0]
    d = f"{OUT}/{name}"; os.makedirs(d, exist_ok=True)
    for old in glob.glob(f"{d}/frame_*.png"): os.remove(old)
    # fotogrammi a 25 fps: a ogni istante si mostra l'ultimo fotogramma già comparso
    times = [r[0] for r in rows]; gif = []; cache = {}
    for f in range(int(end / 1000 * FPS) + 1):
        tau = f * 1000 / FPS
        idx = max(0, bisect.bisect_right(times, tau) - 1)
        if idx not in cache: cache = {idx: render(rows[idx][2])}
        img = cache[idx]
        img.save(f"{d}/frame_{f:05d}.png")
        if f % 2 == 0: gif.append(img.resize((540, 540)))
    gif[0].save(f"{OUT}/{name}.gif", save_all=True, append_images=gif[1:], duration=80, loop=0)
    # audio: ogni suono parte nell'istante del suo fotogramma
    sr = 22050; mix = np.zeros(int((end / 1000 + 4) * sr))
    for t, snd, _ in rows:
        if snd == "-": continue
        s, rate = wav_samples(snd.lower())
        i = int(t / 1000 * sr); mix[i:i + len(s)] += s[: len(mix) - i] * (0.5 if snd == "DADI_CLIC" else 1.0)
    mix = mix / (np.max(np.abs(mix)) + 1e-9) * 0.9
    with wave.open(f"{OUT}/{name}.wav", "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes((mix * 32767).astype(np.int16).tobytes())
    print(f"{name}: {len(glob.glob(d + '/frame_*.png'))} fotogrammi, {end / 1000:.1f} s, audio + gif")

# simboli: immagini singole
sd = f"{OUT}/simboli"; os.makedirs(sd, exist_ok=True)
for line in open(f"{SRC}/05_simboli.txt"):
    n, _, data = line.rstrip("\n").split("\t")
    render([int(v) for v in data.split(",")]).save(f"{sd}/{n}.png")
print("simboli: ok")

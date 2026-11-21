"""Genera i suoni dell'app in app/src/main/res/raw/ (WAV mono 22 kHz).
Usa le stesse funzioni di sintesi del laboratorio (tools/laboratorio.py): tutto sintetizzato da zero.
Uso: python3 tools/suoni.py"""
import os, wave
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = open(os.path.join(HERE, "laboratorio.py")).read()
# prende solo le definizioni (tutto quello che viene prima dell'assemblaggio della pagina)
CUT = SRC.index('os.makedirs(os.path.join(LAB, "suoni"), exist_ok=True)\nfilms, audio, frames_db')
g = {"__file__": os.path.join(HERE, "laboratorio.py")}
exec(SRC[:CUT], g)

OUT = os.path.join(os.path.dirname(HERE), "app", "src", "main", "res", "raw")
os.makedirs(OUT, exist_ok=True)
SR = g["SR"]; np_ = np

def save(name, x, peak=0.85):
    x = np.asarray(x, dtype=float)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    # dissolvenza di 5 ms agli estremi (niente "clic" digitali)
    f = int(0.005 * SR); x[:f] *= np.linspace(0, 1, f); x[-f:] *= np.linspace(1, 0, f)
    x = (x[0::2] + x[1::2][: len(x[0::2])]) / 2 if len(x) % 2 == 0 else (x[:-1][0::2] + x[:-1][1::2]) / 2   # 44,1 → 22 kHz
    pcm = (x * 32767).astype(np.int16)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR // 2); w.writeframes(pcm.tobytes())
    print(f"{name:16s} {len(pcm) / (SR / 2):.2f} s")

def space(x, size=1.0, wet=0.3, tail=0.8):
    return g["riverbero"](x, size, wet, tail=tail)

mix, place, t_axis, noise, lowpass, env_ad = g["mix"], g["place"], g["t_axis"], g["noise"], g["lowpass"], g["env_ad"]

# ORACOLO CHIUSO: pietre calde
save("chiuso_arrivo", space(g["sfrigolio"](0.7, 0.7), 1.0, 0.3, 0.6), 0.6)
pensa = np.zeros(int(2.0 * SR))
place(pensa, lowpass(noise(0.25), 1500) * env_ad(int(0.25 * SR), 0.01, 0.08) * 0.6, 0)        # il mestolo
place(pensa, g["sfrigolio"](1.7, 1.3), 0.05)
place(pensa, g["piano"]("tavolo", 0.35), 1.25)
save("chiuso_pensa", space(pensa, 1.0, 0.3, 0.8))
ma = mix(g["goccia"](vol=0.6), g["sfrigolio"](0.35, 0.7))
save("chiuso_ma", space(ma, 1.0, 0.3, 0.5), 0.6)

# ORACOLO APERTO: fuoco
save("aperto_arrivo", space(g["fiammifero"]()[: int(0.9 * SR)], 1.0, 0.25, 0.4), 0.7)
bru = np.zeros(int(3.4 * SR))
place(bru, g["crepitio_carta"](2.2, 15, 90), 0)
place(bru, g["rombo"](2.2, 400, 1200, 0.1, 0.4), 0)
place(bru, g["crepitio_carta"](1.2, 40, 4) * np.linspace(1, 0, int(1.2 * SR)), 2.2)
save("aperto_pensa", space(bru, 1.0, 0.25, 0.6))

# DADI: resina su feltro
save("dadi_clic", g["impatto"]("resina", 0.6), 0.5)
save("dadi_feltro", mix(lowpass(g["impatto"]("resina", 0.6), 2500), g["piano"]("feltro", 1.0)), 0.75)
save("dadi_posa", g["piano"]("feltro", 0.9), 0.85)

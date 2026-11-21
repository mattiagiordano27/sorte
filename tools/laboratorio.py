"""LABORATORIO: monta i filmati esportati da LaboratorioTest in una pagina HTML con i suoni.
Uso:  ./gradlew :app:testDebugUnitTest --tests '*LaboratorioTest*' && python3 tools/laboratorio.py
Scrive app/build/laboratorio/laboratorio.html (+ i suoni .wav in app/build/laboratorio/suoni/).
Tutti i suoni sono sintetizzati qui da zero (rumore filtrato + sinusoidi): nessun campione esterno."""
import base64, io, json, os, wave
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAB = os.path.join(ROOT, "app", "build", "laboratorio")
SR = 44100
rng = np.random.default_rng(3)

# ───────────── strumenti di sintesi ─────────────

def t_axis(sec): return np.arange(int(sec * SR)) / SR

def lowpass(x, cutoff):
    """Passa-basso a un polo; cutoff può essere un array (filtro che si apre/chiude nel tempo)."""
    cutoff = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x); acc = 0.0
    for i in range(len(x)):
        acc = (1 - a[i]) * x[i] + a[i] * acc
        y[i] = acc
    return y

def noise(sec): return rng.standard_normal(int(sec * SR))

def env_ad(n, attack, decay):
    t = np.arange(n) / SR
    e = np.minimum(t / max(attack, 1e-4), 1.0) * np.exp(-np.maximum(t - attack, 0) / decay)
    return e

def place(track, sound, at, gain=1.0):
    i = int(at * SR); j = min(len(track), i + len(sound))
    if i < len(track): track[i:j] += sound[: j - i] * gain

# ───────────── spazio: riverbero e tono della stanza ─────────────

def comb(x, delay, fb, damp):
    y = np.zeros(len(x) + delay); store = 0.0
    for i in range(len(x)):
        out = y[i]
        store = out * (1 - damp) + store * damp
        y[i + delay] += x[i] + store * fb
    return y[: len(x)]

def allpass(x, delay, g=0.5):
    y = np.zeros_like(x); buf = np.zeros(delay); k = 0
    for i in range(len(x)):
        b = buf[k]; o = -x[i] + b; buf[k] = x[i] + b * g; y[i] = o; k = (k + 1) % delay
    return y

def riverbero(x, size=1.0, wet=0.35, tail=4.0):
    """Riverbero di Schroeder (4 comb + 2 allpass): una stanza grande, morbida."""
    x = np.concatenate([x, np.zeros(int(tail * SR))])
    d = [int(v * size) for v in (1557, 1617, 1491, 1422)]
    w = sum(comb(x, k, 0.84, 0.35) for k in d) / 4
    w = allpass(allpass(w, 225), 556)
    return x * (1 - wet) + w * wet

def stanza(sec, level=1.0):
    """Tono della stanza: fruscio grave + crepitii radi, come un microfono acceso."""
    brown = np.cumsum(noise(sec)); brown -= lowpass(brown, 2)       # rumore marrone senza deriva
    brown = brown / (np.max(np.abs(brown)) + 1e-9)
    air = lowpass(noise(sec), 2500) * 0.15
    cr = np.zeros(int(sec * SR))
    for _ in range(int(sec * 6)):
        k = rng.integers(0, len(cr) - 50); cr[k:k + 3] += rng.standard_normal(3) * 0.4
    return (brown * 0.5 + air + cr) * 0.12 * level

# ───────────── VAPORE DEL DESTINO (liquidi e vapori) ─────────────

def goccia(f0=None, vol=1.0):
    """Una goccia nell'acqua: il 'plic' che sale di tono (risonanza della bolla)."""
    f0 = f0 or rng.uniform(900, 1700)
    sec = 0.12; t = t_axis(sec)
    f = f0 * (1 + 2.2 * t / sec)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * env_ad(len(t), 0.001, 0.035) * vol

def bolle(sec, density):
    """Gorgoglio: tante piccole bolle basse."""
    tr = np.zeros(int(sec * SR)); t = 0.0
    while t < sec:
        place(tr, goccia(rng.uniform(250, 600), rng.uniform(0.15, 0.4)), t)
        t += rng.exponential(1 / density)
    return tr

def sibilo(sec):
    """Il vapore: sibilo che cresce e si apre, come un bollitore lontano."""
    t = t_axis(sec); u = t / sec
    n = noise(sec); hiss = n - lowpass(n, 900 + 1500 * u)                 # passa-alto morbido
    hiss = lowpass(hiss, 3000 + 5000 * u)
    flutter = 0.8 + 0.2 * np.sin(2 * np.pi * 5.3 * t) * u
    return hiss * (u ** 1.5) * flutter * 0.35

def accordo(sec=5.0):
    """Risposta: accordo ambient che si apre lento (re, la, mi, fa#), leggermente scordato, e svanisce."""
    t = t_axis(sec); out = np.zeros_like(t)
    for f in (146.8, 220.0, 329.6, 370.0, 587.3):
        for det in (-0.6, 0.6):
            out += np.sin(2 * np.pi * (f + det) * t + rng.uniform(0, 6.28))
    e = np.minimum(t / 0.9, 1.0) * np.exp(-np.maximum(t - 0.9, 0) / 1.6)
    return lowpass(out * e, 1800) * 0.12

def bloop():
    """Un 'bloop' profondo: una bolla grande che sale."""
    sec = 0.5; t = t_axis(sec)
    f = 110 * (1 + 1.4 * t / sec)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env_ad(len(t), 0.004, 0.14)

def colonna_oracolo(total, idle, think):
    tr = np.zeros(int(total * SR))
    t = 0.4                                                     # attesa: gocce rare
    while t < idle:
        place(tr, goccia(vol=rng.uniform(0.35, 0.6)), t); t += rng.uniform(0.7, 1.4)
    place(tr, sibilo(think + 0.2), idle - 0.1, 1.0)             # pensiero: vapore + bolle
    place(tr, bolle(think, 9) * np.linspace(0.3, 1, int(think * SR)), idle)
    place(tr, bloop(), idle + think, 0.9)                       # risposta
    place(tr, accordo(), idle + think + 0.05, 1.0)
    wet = riverbero(tr, 1.3, 0.42, tail=0.0)[: len(tr)]
    return wet + stanza(total)

# ───────────── DADI (legno su un tavolo, stessa stanza) ─────────────

def clic(strength=1.0, pitch=1.0):
    sec = 0.06; t = t_axis(sec)
    burst = lowpass(noise(sec), 6000) * env_ad(len(t), 0.0005, 0.004)
    body = sum(np.sin(2 * np.pi * f * pitch * t) * np.exp(-t / d) for f, d in [(1450, 0.012), (2380, 0.008), (3900, 0.004)])
    return (burst * 0.6 + body * 0.4) * strength

def tonfo():
    sec = 0.4; t = t_axis(sec)
    out = np.sin(2 * np.pi * 95 * t * (1 - 0.15 * t)) * np.exp(-t / 0.09) * 0.9
    out[: len(clic())] += clic(1.2, 0.8)
    return out

def colonna_dadi(timeline_ms, tumble_until_ms, settle_ms, land_ms):
    total = timeline_ms[-1] / 1000 + 0.6
    tr = np.zeros(int(total * SR)); t = 0.05
    while t < tumble_until_ms / 1000:
        place(tr, clic(0.25 + 0.5 * rng.random(), 0.8 + 0.5 * rng.random()), t)
        t += 0.03 + rng.exponential(0.06)
    for k, ms in enumerate(settle_ms):
        place(tr, clic(1.0 - k * 0.2, 0.9 + 0.1 * rng.random()), ms / 1000)
        place(tr, clic(0.4 - k * 0.08, 1.2), ms / 1000 + 0.025)
    place(tr, tonfo(), land_ms / 1000)
    wet = riverbero(tr, 0.6, 0.18, tail=0.0)[: len(tr)]
    return wet + stanza(total, 0.7)

# ───────────── nuovi paesaggi: nuvole, nebbia, fiato, incenso ─────────────

def vento(sec, strength):
    """Vento: raffiche lente, un fischio appena accennato."""
    t = t_axis(sec)
    gust = 0.5 + 0.5 * np.sin(2 * np.pi * 0.17 * t) * np.sin(2 * np.pi * 0.07 * t + 1)
    n = lowpass(noise(sec), 300 + 900 * gust * strength)
    whistle = np.sin(2 * np.pi * (620 + 60 * np.sin(2 * np.pi * 0.3 * t)) * t) * 0.03 * gust * strength
    return (n * 0.6 + whistle) * (0.3 + 0.7 * gust) * strength

def tuono_lontano(sec=2.5):
    t = t_axis(sec)
    b = np.cumsum(noise(sec)); b -= lowpass(b, 3); b /= np.max(np.abs(b)) + 1e-9
    return lowpass(b, 140) * env_ad(len(t), 0.4, 0.9) * 2.0

def sirena_nebbia(sec=2.2, f=73.4):
    """Sirena da nebbia lontanissima: un suono grave, lungo, smussato."""
    t = t_axis(sec)
    tone = sum(np.sin(2 * np.pi * f * k * t) / k for k in (1, 2, 3, 4))
    e = np.minimum(t / 0.5, 1) * np.minimum((sec - t) / 0.7, 1)
    return lowpass(tone * e, 400) * 0.5

def sciacquio(sec, density=2.5):
    """Acqua che lambisce una riva."""
    tr = lowpass(noise(sec), 250) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.4 * t_axis(sec))) * 0.5
    tr += bolle(sec, density) * 0.4
    return tr

def respiro_umano(sec, period=2.6, strength=1.0):
    """Fiato: inspira/espira, rumore 'soffiato' a banda media."""
    t = t_axis(sec)
    n = noise(sec); band = lowpass(n, 1800) - lowpass(n, 300)
    cyc = np.maximum(0, np.sin(2 * np.pi * t / period)) ** 1.5
    return band * cyc * 0.8 * strength

def espirazione_lunga(sec):
    t = t_axis(sec); n = noise(sec); band = lowpass(n, 2200) - lowpass(n, 250)
    return band * np.sin(np.pi * np.minimum(t / sec, 1)) * 1.1

def dito_sul_vetro(sec):
    """Il dito che scrive nell'appannato: uno stridio sottile e irregolare."""
    t = t_axis(sec)
    f = 950 + 250 * np.sin(2 * np.pi * 3.3 * t) + 80 * lowpass(noise(sec), 20) * 30
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR)
    amp = np.clip(lowpass(np.abs(noise(sec)), 12) * 4, 0, 1)
    return tone * amp * 0.25

def braci_crepitio(sec, density):
    tr = np.zeros(int(sec * SR)); t = 0.0
    while t < sec:
        c = lowpass(noise(0.01), 3500) * env_ad(int(0.01 * SR), 0.0003, 0.002) * rng.uniform(0.2, 0.8)
        place(tr, c, t); t += rng.exponential(1 / density)
    return tr + lowpass(noise(sec), 900) * 0.08

def finale(tr, wet=0.42, size=1.3, room=1.0):
    return mix(riverbero(tr, size, wet, tail=0.0)[: len(tr)], stanza(len(tr) / SR, room))[: len(tr)]

def colonna(kind, total, idle, think):
    tr = np.zeros(int(total * SR)); r0 = idle + think
    if kind == "nuvole":
        place(tr, vento(idle, 0.5), 0); place(tr, vento(think + 0.4, 1.0), idle)
        place(tr, tuono_lontano(), idle + 0.6, 0.8); place(tr, accordo(), r0, 1.0)
    elif kind == "nebbia":
        place(tr, sciacquio(total), 0, 0.8)
        place(tr, sirena_nebbia(), idle + 0.1, 1.0); place(tr, sirena_nebbia(1.6, 55.0), idle + 1.0, 0.7)
        place(tr, accordo(), r0 + 0.2, 1.0)
    elif kind == "fiato":
        place(tr, respiro_umano(idle, 2.6, 0.5), 0); place(tr, espirazione_lunga(think), idle)
        place(tr, dito_sul_vetro(0.9), r0); place(tr, accordo(), r0 + 0.9, 0.9)
    elif kind == "incenso":
        place(tr, braci_crepitio(idle, 4), 0); place(tr, braci_crepitio(think, 14), idle)
        place(tr, sibilo(think) * 0.5, idle); place(tr, accordo(), r0, 1.0)
    else:
        return colonna_oracolo(total, idle, think)
    return finale(tr)

# ───────────── nuovi dadi: mercurio, sassi, costellazione ─────────────

def splash(vol=1.0, low=False):
    sec = 0.35; t = t_axis(sec)
    n = lowpass(noise(sec), 2500 if not low else 900) * env_ad(len(t), 0.002, 0.06)
    return (n * 0.5 + bloop()[: len(t)] * (0.8 if low else 0.3)) * vol

def plop(vol=1.0, pitch=1.0):
    sec = 0.25; t = t_axis(sec)
    f = 300 * pitch * (1 + 2.0 * t / sec)
    return (np.sin(2 * np.pi * np.cumsum(f) / SR) * env_ad(len(t), 0.001, 0.05) + lowpass(noise(sec), 3000) * env_ad(len(t), 0.0005, 0.015) * 0.5) * vol

def campanella(f, vol=1.0, sec=2.5):
    t = t_axis(sec)
    tone = sum(a * np.sin(2 * np.pi * f * k * t) * np.exp(-t / d) for k, a, d in [(1, 1, 1.4), (2.76, 0.3, 0.6), (5.4, 0.12, 0.3)])
    return tone * np.minimum(t / 0.004, 1) * vol * 0.4

PENTA = [523.3, 587.3, 659.3, 784.0, 880.0, 1046.5, 1174.7]

def colonna_dadi_ev(kind, total, events):
    tr = np.zeros(int(total * SR))
    shake = 1.5
    if kind == "sagome":
        t = 0.05
        while t < shake:
            place(tr, clic(0.25 + 0.5 * rng.random(), 0.8 + 0.5 * rng.random()), t); t += 0.03 + rng.exponential(0.06)
        k = 0
        for ms, n in events:
            if n == "rimbalzo": place(tr, clic(1.0 - k * 0.2, 0.95), ms / 1000); k += 1
            if n == "posa": place(tr, tonfo(), ms / 1000)
        return riverbero(tr, 0.6, 0.18, tail=0.0)[: len(tr)] + stanza(total, 0.7)
    if kind == "mercurio":
        t = t_axis(shake)
        slosh = lowpass(noise(shake), 500 + 400 * np.abs(np.sin(2 * np.pi * 2.2 * t))) * 0.6
        place(tr, slosh + bolle(shake, 12) * 0.5, 0)
        for ms, n in events:
            if n == "sciabordio": place(tr, splash(0.6), ms / 1000)
            if n == "fusione": place(tr, bloop(), ms / 1000, 0.9)
            if n == "numero": place(tr, campanella(1318.5, 0.6, 1.5), ms / 1000)
        return finale(tr, 0.3, 0.9, 0.8)
    if kind == "sassi":
        place(tr, sciacquio(total, 1.5), 0, 0.7)
        t = 0.1
        while t < shake:                                            # sassi che sbattono nella mano
            place(tr, clic(0.4 + 0.3 * rng.random(), 0.55 + 0.1 * rng.random()), t); t += 0.05 + rng.exponential(0.09)
        k = 0
        for ms, n in events:
            if n == "rimbalzo": place(tr, plop(1.0 - k * 0.15, 1.3 - k * 0.1), ms / 1000); k += 1
            if n == "tuffo": place(tr, splash(1.0, low=True), ms / 1000)
        return finale(tr, 0.38, 1.4, 0.9)
    if kind == "costellazione":
        for ms, n in events:
            if n == "stella": place(tr, campanella(rng.choice(PENTA), rng.uniform(0.3, 0.7)), ms / 1000)
            if n == "costellazione": place(tr, accordo(), ms / 1000 - 0.3, 1.2)
        return finale(tr, 0.5, 1.5, 0.6)

# ───────────── materia: impatti modali (legno, resina, osso, pietra, porcellana) ─────────────

MATERIALI = {
    # frequenze dei modi (Hz), decadimenti (s), rumore del contatto (cutoff), tono generale
    "legno":     ([980, 1720, 2650, 3900], [0.018, 0.012, 0.008, 0.005], 5000),
    "resina":    ([2300, 3700, 5200, 7100], [0.010, 0.007, 0.005, 0.003], 9000),
    "osso":      ([1650, 2900, 4300, 6000], [0.007, 0.005, 0.004, 0.003], 7000),
    "porcellana":([2900, 5100, 7400], [0.25, 0.14, 0.08], 9000),
    "pietra":    ([420, 1100, 2300], [0.020, 0.012, 0.006], 6000),
}

def mix(*xs):
    n = max(len(x) for x in xs); out = np.zeros(n)
    for x in xs: out[: len(x)] += x
    return out

def impatto(mat, forza=1.0, pitch=None, smorza=1.0):
    freqs, decs, nc = MATERIALI[mat]
    pitch = pitch or rng.uniform(0.92, 1.08)
    sec = max(decs) * 5 + 0.02; t = t_axis(sec)
    body = sum(np.sin(2 * np.pi * f * pitch * t + rng.uniform(0, 6.28)) * np.exp(-t / (d * smorza)) * (0.8 ** k)
               for k, (f, d) in enumerate(zip(freqs, decs)))
    tick = lowpass(noise(sec), nc) * env_ad(len(t), 0.0003, 0.0025)
    return (body * 0.5 + tick * 0.5) * forza

def piano(mat, forza=1.0):
    """Il piano su cui cade il dado: il suo 'corpo' grave."""
    sec = 0.35; t = t_axis(sec)
    if mat == "tavolo":   f, d, lp = (175, 410), 0.06, 1500
    elif mat == "feltro": f, d, lp = (120, 260), 0.025, 500
    elif mat == "pietra": f, d, lp = (330, 900), 0.02, 4000
    else:                 f, d, lp = (150, 300), 0.04, 1200
    body = sum(np.sin(2 * np.pi * x * t) * np.exp(-t / d) for x in f)
    thump = lowpass(noise(sec), lp) * env_ad(len(t), 0.001, d * 0.5)
    return (body * 0.6 + thump * 0.6) * forza

def rotolio(sec, mat, superficie, densita=40):
    """Il dado che rotola: tanti piccoli contatti ravvicinati che rallentano."""
    tr = np.zeros(int(sec * SR)); t = 0.0
    while t < sec:
        u = t / sec
        place(tr, mix(impatto(mat, 0.25 * (1 - u) + 0.05), piano(superficie, 0.08 * (1 - u))), t)
        t += (1 / densita) * (1 + 3 * u) * rng.uniform(0.6, 1.4)
    return tr

def colonna_dadi_mat(kind, total, events):
    tr = np.zeros(int(total * SR)); shake = 1.5
    rim = [ms for ms, n in events if n == "rimbalzo"]; posa = [ms for ms, n in events if n == "posa"][0] / 1000
    if kind == "legno":
        t = 0.05
        while t < shake:                                   # dadi di legno che sbattono tra le mani
            place(tr, impatto("legno", rng.uniform(0.2, 0.6), smorza=0.6), t); t += 0.025 + rng.exponential(0.05)
        for k, ms in enumerate(rim):
            place(tr, mix(impatto("legno", 1 - k * 0.18), piano("tavolo", 0.9 - k * 0.18)), ms / 1000)
        place(tr, rotolio(0.25, "legno", "tavolo"), rim[-1] / 1000 + 0.05)
        place(tr, mix(impatto("legno", 0.7), piano("tavolo", 0.8)), posa)
        return riverbero(tr, 0.55, 0.15, tail=0.0)[: len(tr)] + stanza(total, 0.6)
    if kind == "feltro":
        t = 0.05
        while t < shake:                                   # dadi di resina tra le mani: più acuti e brevi
            place(tr, impatto("resina", rng.uniform(0.2, 0.55)), t); t += 0.02 + rng.exponential(0.045)
        for k, ms in enumerate(rim):                       # sul feltro: colpi attutiti, quasi niente squillo
            place(tr, mix(lowpass(impatto("resina", 0.6 - k * 0.1), 2500), piano("feltro", 1.0 - k * 0.15)), ms / 1000)
        place(tr, lowpass(rotolio(0.3, "resina", "feltro", 30), 2200), rim[-1] / 1000 + 0.05)
        place(tr, piano("feltro", 0.9), posa)
        return riverbero(tr, 0.45, 0.10, tail=0.0)[: len(tr)] + stanza(total, 0.6)
    if kind == "cuoio":
        t = 0.05
        while t < shake:                                   # dentro il bicchiere: colpi cupi, risonanza del cuoio
            c = impatto("resina", rng.uniform(0.3, 0.7)); c = lowpass(c, 1400)
            place(tr, c, t); t += 0.02 + rng.exponential(0.035)
        cup = t_axis(shake); rub = lowpass(noise(shake), 700) * (0.4 + 0.3 * np.abs(np.sin(2 * np.pi * 3 * cup)))
        place(tr, rub * 0.25, 0)                           # sfregare del cuoio
        place(tr, mix(piano("tavolo", 1.3), lowpass(noise(0.08), 900) * 0.6), shake - 0.02)   # bicchiere sbattuto sul tavolo
        for k, ms in enumerate(rim):
            place(tr, mix(impatto("resina", 0.7 - k * 0.12), piano("tavolo", 0.5 - k * 0.1)), ms / 1000 + 0.04)
        place(tr, rotolio(0.25, "resina", "tavolo"), rim[-1] / 1000 + 0.08)
        place(tr, mix(impatto("resina", 0.5), piano("tavolo", 0.6)), posa)
        return riverbero(tr, 0.6, 0.16, tail=0.0)[: len(tr)] + stanza(total, 0.6)
    if kind == "osso":
        t = 0.05
        while t < shake:                                   # ossi tra le mani: secchi, asciutti
            place(tr, impatto("osso", rng.uniform(0.25, 0.6)), t); t += 0.03 + rng.exponential(0.05)
        for k, ms in enumerate(rim):                       # sulla pietra: colpo chiaro + graniglia
            grit = lowpass(noise(0.05), 7000) * env_ad(int(0.05 * SR), 0.001, 0.012) * 0.4
            place(tr, mix(impatto("osso", 1 - k * 0.15), piano("pietra", 0.7 - k * 0.12), grit), ms / 1000)
        scrape = lowpass(noise(0.3), 5000) * np.linspace(0.3, 0, int(0.3 * SR))
        place(tr, scrape * 0.3, rim[-1] / 1000 + 0.05)
        place(tr, mix(impatto("osso", 0.6), piano("pietra", 0.6)), posa)
        return riverbero(tr, 0.7, 0.2, tail=0.0)[: len(tr)] + stanza(total, 0.6)

# ───────────── oracoli: varianti di suono per VAPORE e INCENSO ─────────────

def ebollizione(sec, u0=0.0, u1=1.0):
    """Acqua che sta per bollire: brontolio grave + bollicine sempre più fitte."""
    t = t_axis(sec); u = u0 + (u1 - u0) * t / sec
    rumble = lowpass(noise(sec), 180) * (0.4 + 0.6 * u)
    return rumble * 0.8 + bolle(sec, 4 + 30 * (u0 + u1) / 2) * 0.5 * (0.3 + u)

def versare(sec=1.6):
    """Acqua versata in una tazza: il tono sale mentre si riempie."""
    t = t_axis(sec); u = t / sec
    n = noise(sec)
    res = np.zeros_like(t)
    for f0 in (420, 1050):
        f = f0 * (1 + 1.3 * u)
        res += np.sin(2 * np.pi * np.cumsum(f) / SR) * lowpass(np.abs(n), 60) * 3
    stream = lowpass(n, 3500) * 0.5
    e = np.minimum(t / 0.05, 1) * np.minimum((sec - t) / 0.2, 1)
    return (stream + res * 0.25) * e

def sfrigolio(sec, intensita=1.0):
    """Acqua sulle pietre roventi: sfrigolio + scoppiettii."""
    t = t_axis(sec)
    n = noise(sec); hiss = n - lowpass(n, 2500)
    e = np.exp(-t / (sec * 0.45)) * np.minimum(t / 0.02, 1)
    return (hiss * 0.5 + braci_crepitio(sec, 60) * 0.8) * e * intensita

def fiammifero():
    sec = 1.6; t = t_axis(sec)
    scratch = (noise(sec) - lowpass(noise(sec), 1500)) * env_ad(len(t), 0.005, 0.06) * 0.8
    flare = lowpass(noise(sec), 1200) * env_ad(len(t), 0.07, 0.25) * 0.9
    flame = lowpass(noise(sec), 400) * np.clip((t - 0.3) / 0.2, 0, 1) * (0.25 + 0.1 * np.sin(2 * np.pi * 7 * t))
    return scratch + flare + flame

def soffio_spegne():
    sec = 0.6; t = t_axis(sec); n = noise(sec)
    return (lowpass(n, 1500) - lowpass(n, 200)) * np.sin(np.pi * np.minimum(t / sec, 1)) * 1.1

def campane_vento(sec, density=0.8):
    """Campanelle a vento lontane: tubi metallici, armonici non intonati."""
    tr = np.zeros(int(sec * SR)); t = 0.2
    while t < sec:
        f = rng.choice([1180, 1324, 1487, 1765, 1983])
        tt = t_axis(3.0)
        tone = sum(a * np.sin(2 * np.pi * f * k * tt) * np.exp(-tt / d) for k, a, d in [(1, 1, 1.6), (2.76, 0.4, 0.8), (5.4, 0.2, 0.4)])
        place(tr, tone * rng.uniform(0.08, 0.2), t); t += rng.exponential(1 / density)
    return tr

def campana_sfregata(sec, f=164.8):
    """Campana tibetana sfregata: il tono cresce piano, con il battimento tipico."""
    t = t_axis(sec); u = t / sec
    tone = np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * (f * 2.71 + 1.3) * t) + 0.25 * np.sin(2 * np.pi * f * 2.71 * t)
    rub = lowpass(noise(sec), 600) * 0.15
    return (tone * (0.15 + 0.6 * u ** 1.4) * (0.8 + 0.2 * np.sin(2 * np.pi * 4.5 * t)) + rub) * 0.5

def campana_colpita(f=164.8, sec=5.0):
    t = t_axis(sec)
    tone = sum(a * np.sin(2 * np.pi * f * k * t + 0.6 * np.sin(2 * np.pi * 1.1 * t)) * np.exp(-t / d)
               for k, a, d in [(1, 1, 3.0), (2.71, 0.6, 1.8), (5.15, 0.3, 0.9), (8.4, 0.12, 0.4)])
    return tone * np.minimum(t / 0.003, 1) * 0.5

def colonna_v(kind, var, total, idle, think):
    tr = np.zeros(int(total * SR)); r0 = idle + think
    acc = 0.45                                                 # accordo più discreto: deve restare "materia"
    if kind == "vapore":
        if var == "A":
            return colonna_oracolo(total, idle, think)
        if var == "B":                                         # teiera
            place(tr, impatto("porcellana", 0.35), 0.5); place(tr, impatto("porcellana", 0.2, pitch=1.2), 2.2)
            place(tr, ebollizione(idle, 0, 0.25), 0, 0.6)
            place(tr, mix(ebollizione(think, 0.3, 1.0), sibilo(think) * 0.8), idle)
            place(tr, versare(), r0, 1.0); place(tr, impatto("porcellana", 0.5), r0 + 1.6)
            place(tr, accordo(), r0 + 0.3, acc)
        if var == "C":                                         # pietre calde
            t = 0.6
            while t < idle:
                place(tr, goccia(vol=0.4), t); place(tr, sfrigolio(0.4, 0.4), t); t += rng.uniform(0.8, 1.5)
            place(tr, lowpass(noise(0.3), 1500) * env_ad(int(0.3 * SR), 0.01, 0.1) * 0.6, idle)   # il mestolo d'acqua
            place(tr, sfrigolio(think + 1.2, 1.2), idle + 0.1)
            place(tr, piano("tavolo", 0.4), r0 + 0.2)                                              # scricchiolio del legno
            place(tr, accordo(), r0, acc)
        return finale(tr, 0.38, 1.2)
    if kind == "incenso":
        if var == "A":
            place(tr, braci_crepitio(idle, 4), 0); place(tr, braci_crepitio(think, 14), idle)
            place(tr, sibilo(think) * 0.5, idle); place(tr, accordo(), r0, acc * 1.5)
        if var == "B":                                         # fiammifero
            place(tr, braci_crepitio(idle, 1.5) * 0.6, 0)
            place(tr, fiammifero(), idle); place(tr, braci_crepitio(think, 8), idle + 0.4)
            place(tr, soffio_spegne(), r0 - 0.1); place(tr, accordo(), r0 + 0.3, acc)
        if var == "C":                                         # tempio
            place(tr, campane_vento(idle + think, 0.9), 0); place(tr, braci_crepitio(total, 2) * 0.5, 0)
            place(tr, campana_sfregata(think), idle)
            place(tr, campana_colpita(), r0, 1.0)
        return finale(tr, 0.45, 1.4)

# ───────────── uscita ─────────────

def to_wav_bytes(x):
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.85
    pcm = (x * 32767).astype(np.int16)
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    return buf.getvalue()

def load_film(name):
    times, frames, events = [], [], []
    for line in open(os.path.join(LAB, name + ".txt")):
        parts = line.rstrip("\n").split("\t")
        if parts[0] == "#ev": events.append((int(parts[1]), parts[2])); continue
        times.append(int(parts[0]))
        frames.append(bytes(min(255, int(v) * 255 // 4095) for v in parts[1].split(",")))
    return times, base64.b64encode(b"".join(frames)).decode(), events

# ───────────── FUOCO (oracolo aperto) ─────────────

def rombo(sec, c0, c1, l0, l1):
    t = t_axis(sec); u = t / sec
    return lowpass(noise(sec), c0 + (c1 - c0) * u) * (l0 + (l1 - l0) * u) * (0.85 + 0.15 * np.sin(2 * np.pi * 1.7 * t))

def vuff():
    sec = 0.9; t = t_axis(sec)
    return lowpass(noise(sec), 300 + 900 * np.exp(-t * 4)) * env_ad(len(t), 0.06, 0.3) * 1.6

def scoppio():
    sec = 0.05; return lowpass(noise(sec), 2500) * env_ad(int(sec * SR), 0.0005, 0.006) * rng.uniform(0.6, 1.2)

def crepitio_carta(sec, d0, d1):
    tr = np.zeros(int(sec * SR)); t = 0.0
    while t < sec:
        u = t / sec; c = noise(0.006); c = (c - lowpass(c, 2500)) * env_ad(len(c), 0.0002, 0.0015)
        place(tr, c * rng.uniform(0.3, 1.0), t); t += rng.exponential(1 / (d0 + (d1 - d0) * u))
    n = noise(sec); rustle = (lowpass(n, 6000) - lowpass(n, 1800)) * np.linspace(d0 / d1, 1, int(sec * SR)) * 0.2
    return tr + rustle

def mantice(sec, period):
    t = t_axis(sec); n = noise(sec)
    cyc = np.maximum(0, np.sin(2 * np.pi * t / period)) ** 2
    return lowpass(n, 500 + 400 * cyc) * cyc * 0.9

def incudine():
    sec = 3.0; t = t_axis(sec)
    ring = sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t / d) for f, a, d in [(1180, 1, 1.2), (2950, 0.6, 0.7), (4820, 0.4, 0.45), (6700, 0.25, 0.3)])
    hit = lowpass(noise(sec), 7000) * env_ad(len(t), 0.0003, 0.004)
    return ring * 0.35 + hit * 0.8

def colonna_fuoco(kind, total):
    tr = np.zeros(int(total * SR)); I, T = 3.0, 2.0; r0 = I + T
    if kind == "falo":
        place(tr, rombo(I, 180, 220, 0.25, 0.3), 0); place(tr, braci_crepitio(I, 3), 0)
        place(tr, vuff(), I); place(tr, rombo(T + 0.3, 250, 900, 0.4, 1.0), I); place(tr, braci_crepitio(T, 22), I)
        place(tr, rombo(1.4, 700, 150, 0.8, 0.0), r0)
        t = r0
        while t < total - 0.3: place(tr, scoppio(), t, 0.4); t += rng.exponential(0.35)
    if kind == "carta":
        place(tr, lowpass(noise(I), 1200) * 0.05, 0)
        t = 0.5
        while t < I: place(tr, scoppio(), t, 0.15); t += rng.uniform(0.8, 1.6)
        place(tr, crepitio_carta(T + 0.6, 15, 90), I); place(tr, rombo(T + 0.6, 400, 1200, 0.1, 0.4), I)
        place(tr, crepitio_carta(1.4, 40, 4) * np.linspace(1, 0, int(1.4 * SR)), r0 + 0.6)
    if kind == "fucina":
        place(tr, mantice(I, 3.7), 0); place(tr, braci_crepitio(total, 3), 0, 0.6)
        place(tr, mantice(T, 0.9), I); place(tr, braci_crepitio(T, 15), I)
        place(tr, incudine(), r0); place(tr, sfrigolio(0.9, 0.8), r0 + 0.35)
    return finale(tr, 0.32, 1.1, 0.8)

# ───────────── PIETRE CALDE (oracolo chiuso, tempi rapidi) ─────────────

def colonna_chiuso(total, has_ma):
    tr = np.zeros(int(total * SR)); I, T, R, P = 2.0, 1.2, 0.5, 1.0; r0 = I + T
    t = 0.3
    while t < I - 0.2:
        place(tr, goccia(vol=0.35), t); place(tr, sfrigolio(0.35, 0.35), t); t += rng.uniform(0.6, 1.1)
    place(tr, lowpass(noise(0.25), 1500) * env_ad(int(0.25 * SR), 0.01, 0.08) * 0.6, I)      # il mestolo
    place(tr, sfrigolio(T + 0.5, 1.3), I + 0.05)
    place(tr, piano("tavolo", 0.35), r0 + 0.1)                                               # il legno della panca
    if has_ma:
        place(tr, goccia(vol=0.45), r0 + R + P); place(tr, sfrigolio(0.3, 0.6), r0 + R + P)   # "tss": il ma
    return finale(tr, 0.3, 1.0, 0.7)

os.makedirs(os.path.join(LAB, "suoni"), exist_ok=True)
films, audio, frames_db = [], {}, {}

def add_audio(key, x):
    wav = to_wav_bytes(x)
    open(os.path.join(LAB, "suoni", key + ".wav"), "wb").write(wav)
    audio[key] = base64.b64encode(wav).decode()

def add_film(key):
    times, data, ev = load_film(key)
    frames_db[key] = {"times": times, "frames": data}
    return times, ev

def fiammella(sec):
    """La fiammella d'attesa: un fruscio caldo che tremola."""
    t = t_axis(sec)
    return lowpass(noise(sec), 350) * (0.25 + 0.08 * np.sin(2 * np.pi * 6.3 * t) + 0.05 * np.sin(2 * np.pi * 2.1 * t))

def arrivo_chiuso(): return sfrigolio(0.7, 0.7)
def arrivo_aperto(): return fiammifero()[: int(0.9 * SR)]

def con_arrivo(arr, body, offset):
    tr = np.zeros(int(offset * SR) + len(body)); place(tr, arr, 0); place(tr, body, offset); return tr

OA, OC = 16 * 0.04, 18 * 0.04       # durata degli arrivi (aperto, chiuso)

# APERTO: carta che brucia (attesa = fiammella)
times, _ = add_film("scelto_aperto")
total = times[-1] / 1000 + 2.0
body = colonna_fuoco("carta", total - OA)
body[: int(3.0 * SR)] = 0; place(body, fiammella(3.0), 0); place(body, braci_crepitio(3.0, 1.5) * 0.4, 0)
add_audio("scelto_aperto", finale(con_arrivo(arrivo_aperto(), body, OA), 0.25, 1.0, 0.5))
films.append({"group": "aperto", "title": "APERTO · CARTA CHE BRUCIA", "desc": "Arrivo: scintilla e fiammella. Attesa: solo la fiammella. Pensando: compare la carta, la fiamma la tocca, il fronte avanza; resta il simbolo.",
              "variants": [["simbolo (segnaposto)", "scelto_aperto", "scelto_aperto"]],
              "phases": [[0, "ARRIVO"], [OA * 1000, "ATTESA"], [(OA + 3.0) * 1000, "PENSA"], [(OA + 5.0) * 1000, "RISPOSTA"]]})

# CHIUSO: sbuffo
vs = []
for res, label in [("si", "SÌ"), ("noma", "NO, MA…"), ("mah", "MAH")]:
    key = f"scelto_chiuso_{res}"
    times, _ = add_film(key)
    total = times[-1] / 1000 + 1.5
    add_audio(key, con_arrivo(arrivo_chiuso(), colonna_chiuso(total - OC, res == "noma"), OC))
    vs.append([label, key, key])
films.append({"group": "chiuso", "title": "CHIUSO · SBUFFO", "desc": "Arrivo: uno sbuffo sale e lascia il \"?\". Attesa: il \"?\" con fili di vapore. Pensando: getto che sfrigola; la risposta in mezzo secondo.",
              "variants": vs, "phases": [[0, "ARRIVO"], [OC * 1000, "ATTESA"], [(OC + 2.0) * 1000, "PENSA"], [(OC + 3.2) * 1000, "RISPOSTA"], [(OC + 4.7) * 1000, "…"]]})

# CAROSELLO
times, _ = add_film("carosello")
total = times[-1] / 1000 + 0.5
seg = OC + 1.8 + OA + 1.8
car = np.zeros(int(total * SR))
for g in range(2):
    base = g * seg
    place(car, arrivo_chiuso(), base); place(car, sfrigolio(1.6, 0.25), base + OC)
    place(car, arrivo_aperto(), base + OC + 1.8); place(car, fiammella(1.8), base + OC + 1.8 + OA)
add_audio("carosello", finale(car, 0.25, 1.0, 0.5))
films.append({"group": "carosello", "title": "CAROSELLO", "desc": "Come scorrere i toy col Glyph Button: chiuso → aperto → chiuso → aperto. Si distinguono al primo sguardo?",
              "variants": [["", "carosello", "carosello"]],
              "phases": [[0, "CHIUSO"], [(OC + 1.8) * 1000, "APERTO"], [seg * 1000, "CHIUSO"], [(seg + OC + 1.8) * 1000, "APERTO"]]})

html = """<!doctype html><html lang="it"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Laboratorio oracolo</title>
<style>
:root{--bg:#000;--fg:#f2f2f2;--muted:#8a8a8a;--line:#2a2a2a;--red:#d71921}
body{background:var(--bg);color:var(--fg);font-family:ui-monospace,Menlo,monospace;margin:0;padding:28px 16px}
h1{font-size:15px;letter-spacing:3px;color:var(--red);font-weight:400;margin:0 0 6px}
p.lead{color:var(--muted);font-size:13px;max-width:760px;line-height:1.6;margin:0 0 28px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:20px}
.card{border:1px solid var(--line);border-radius:18px;padding:18px}
.card h2{font-size:13px;letter-spacing:3px;font-weight:400;margin:0 0 4px}
.card p{color:var(--muted);font-size:12px;line-height:1.55;min-height:56px;margin:0 0 12px}
canvas{width:100%;aspect-ratio:1;display:block;border-radius:50%;background:#000;cursor:pointer}
.row{display:flex;justify-content:space-between;align-items:center;margin-top:12px}
button{background:var(--red);color:#fff;border:0;border-radius:99px;padding:10px 18px;font:inherit;font-size:12px;letter-spacing:2px;cursor:pointer}
.phase{font-size:11px;letter-spacing:2px;color:var(--muted)}
label{font-size:12px;color:var(--muted)}
.vars{display:flex;flex-wrap:wrap;gap:6px;margin-top:12px}
.chip{border:1px solid var(--line);border-radius:99px;padding:6px 10px;font-size:11px;letter-spacing:1px;cursor:pointer;color:var(--fg)}
.chip.on{background:var(--fg);color:#000;border-color:var(--fg)}
</style></head><body>
<h1>LABORATORIO · I DUE ORACOLI SCELTI</h1>
<p class="lead">Ogni animazione è come apparirà sulla Glyph Matrix (LED quadrati): attesa, pensiero, risposta.
Premi ▶ (o tocca la matrice) per vederla col suono. Nell'oracolo chiuso scegli il risultato sotto la matrice: l'animazione si adatta.
<br><label><input type="checkbox" id="loop"> ripeti</label></p>
<div class="grid"><div id="carosello"></div><div id="chiuso"></div><div id="aperto"></div></div>
<script>
const FILMS=__FILMS__, AUDIO=__AUDIO__, DB=__DB__;
const N=25, inC=(x,y)=>(x-12)**2+(y-12)**2<=156;
let ctx=null; const buffers={};
async function audioCtx(){ if(!ctx){ ctx=new (window.AudioContext||window.webkitAudioContext)();
  for(const k in AUDIO){ const b=Uint8Array.from(atob(AUDIO[k]),c=>c.charCodeAt(0)).buffer; buffers[k]=await ctx.decodeAudioData(b);} } return ctx; }
function draw(cv,frame){ const g=cv.getContext('2d'); const s=cv.width/N; g.fillStyle='#000'; g.fillRect(0,0,cv.width,cv.width);
  for(let y=0;y<N;y++)for(let x=0;x<N;x++){ if(!inC(x,y))continue; const v=frame[y*N+x]; const l=Math.round(28+v*(227/255));
    g.fillStyle=`rgb(${l},${l},${l})`; g.fillRect(x*s+1,y*s+1,s-2,s-2);} }
FILMS.forEach(f=>{
  const decoded={}; const get=k=>{ if(!decoded[k]){ const d=DB[k]; const raw=Uint8Array.from(atob(d.frames),c=>c.charCodeAt(0));
    const fr=[]; for(let i=0;i<d.times.length;i++) fr.push(raw.subarray(i*625,(i+1)*625)); decoded[k]={times:d.times,frames:fr}; } return decoded[k]; };
  const el=document.createElement('div'); el.className='card';
  el.innerHTML=`<h2>${f.title}</h2><p>${f.desc}</p><canvas width="500" height="500"></canvas><div class="vars"></div><div class="row"><span class="phase">—</span><button>▶ PLAY</button></div>`;
  document.getElementById(f.group).appendChild(el);
  const cv=el.querySelector('canvas'), ph=el.querySelector('.phase'), bt=el.querySelector('.row button'), vb=el.querySelector('.vars');
  let sel=f.variants[0]; draw(cv,get(sel[1]).frames[0]);
  if(f.variants.length>1) f.variants.forEach(v=>{ const b=document.createElement('span'); b.className='chip'+(v===sel?' on':''); b.textContent=v[0];
    b.onclick=()=>{ sel=v; vb.querySelectorAll('.chip').forEach(c=>c.classList.remove('on')); b.classList.add('on'); play(); }; vb.appendChild(b); });
  let raf=null, src=null;
  async function play(){ const c=await audioCtx(); if(raf)cancelAnimationFrame(raf); if(src)try{src.stop()}catch(e){}
    const film=get(sel[1]); src=c.createBufferSource(); src.buffer=buffers[sel[2]]; src.connect(c.destination); const t0=c.currentTime; src.start();
    const end=film.times[film.times.length-1];
    const tick=()=>{ const ms=(c.currentTime-t0)*1000; let i=0; while(i+1<film.times.length&&film.times[i+1]<=ms)i++; draw(cv,film.frames[i]);
      let p='—'; f.phases.forEach(([t,n])=>{ if(ms>=t)p=n; }); ph.textContent=p;
      if(ms<end+200) raf=requestAnimationFrame(tick); else if(document.getElementById('loop').checked) play(); };
    tick(); }
  bt.onclick=play; cv.onclick=play;
});
</script></body></html>"""
html = html.replace("__FILMS__", json.dumps(films)).replace("__AUDIO__", json.dumps(audio)).replace("__DB__", json.dumps(frames_db))
out = os.path.join(LAB, "laboratorio.html")
open(out, "w").write(html)
print("ok:", out)

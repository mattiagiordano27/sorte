// Sorte · motore delle animazioni (stesso disegno dell'app Android, fotogramma per fotogramma).
// Matrice 25×25: solo il cerchio da 489 punti è visibile. Luminosità 0..4095.
'use strict';

const N = 25, FULL = 4095;
const inCircle = (x, y) => (x - 12) * (x - 12) + (y - 12) * (y - 12) <= 156;
const MASK = Array.from({ length: N * N }, (_, i) => inCircle(i % N, (i / N) | 0));
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

// ── casualità ──
// oracolo e dadi: crittografica, nessuno schema prevedibile
const Rng = {
  double() { const a = new Uint32Array(1); crypto.getRandomValues(a); return a[0] / 4294967296; },
  int(n) { return Math.floor(this.double() * n); },
  die(sides) { return this.int(sides) + 1; },
  bool() { return this.double() < 0.5; },
};
// animazioni: generatore con seme (rumore e sbuffi)
class Seeded {
  constructor(seed) { this.s = (seed >>> 0) || 1; this.g = null; }
  next() { let t = (this.s += 0x6D2B79F5); t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }
  nextDouble() { return this.next(); }
  nextInt(n) { return Math.floor(this.next() * n); }
  nextGaussian() {
    if (this.g !== null) { const g = this.g; this.g = null; return g; }
    let u = 0, v = 0; while (u === 0) u = this.next(); v = this.next();
    const r = Math.sqrt(-2 * Math.log(u)); this.g = r * Math.sin(2 * Math.PI * v); return r * Math.cos(2 * Math.PI * v);
  }
}

// ── fotogramma ──
class Frame {
  constructor() { this.px = new Int32Array(N * N); }
  set(x, y, v) { if (x >= 0 && x < N && y >= 0 && y < N) this.px[y * N + x] = clamp(v | 0, 0, FULL); }
  get(x, y) { return (x >= 0 && x < N && y >= 0 && y < N) ? this.px[y * N + x] : 0; }
  toArray() { const a = new Int32Array(N * N); for (let i = 0; i < a.length; i++) a[i] = MASK[i] ? this.px[i] : 0; return a; }
}

// ── rumore morbido (fumo, fiamme, carta) ──
const Noise = {
  h(x, y, s) {
    let k = (Math.imul(x, 374761393) + Math.imul(y, 668265263) + Math.imul(s, 2147483647)) | 0;
    k = Math.imul(k ^ (k >>> 13), 1274126177);
    return ((k ^ (k >>> 16)) & 0xFFFF) / 65535;
  },
  vnoise(x, y, s = 0) {
    const x0 = Math.floor(x), y0 = Math.floor(y), fx = x - x0, fy = y - y0;
    const sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
    const a = this.h(x0, y0, s), b = this.h(x0 + 1, y0, s), c = this.h(x0, y0 + 1, s), d = this.h(x0 + 1, y0 + 1, s);
    return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy;
  },
};

// ── font a pixel ──
class PixelFont {
  constructor(height, glyphs) { this.height = height; this.glyphs = glyphs; }
  glyph(c) { return this.glyphs[c] || this.glyphs[' ']; }
  width(text) { let w = 0; for (const c of text) w += this.glyph(c)[0].length; return w + Math.max(0, [...text].length - 1); }
  drawCentered(f, text, bottom, b = FULL) { this.draw(f, text, Math.floor((N - this.width(text)) / 2), bottom, b); }
  draw(f, text, left, bottom, b = FULL) {
    let x = left;
    for (const c of text) {
      const g = this.glyph(c), top = bottom - g.length + 1;
      g.forEach((line, row) => { for (let col = 0; col < line.length; col++) if (line[col] === '#') f.set(x + col, top + row, b); });
      x += g[0].length + 1;
    }
  }
}
const BIG = new PixelFont(7, {
  ' ': ['...', '...', '...', '...', '...', '...', '...'],
  '0': ['.###.', '#...#', '#..##', '#.#.#', '##..#', '#...#', '.###.'],
  '1': ['..#..', '.##..', '..#..', '..#..', '..#..', '..#..', '.###.'],
  '2': ['.###.', '#...#', '....#', '...#.', '..#..', '.#...', '#####'],
  '3': ['#####', '...#.', '..#..', '...#.', '....#', '#...#', '.###.'],
  '4': ['...#.', '..##.', '.#.#.', '#..#.', '#####', '...#.', '...#.'],
  '5': ['#####', '#....', '####.', '....#', '....#', '#...#', '.###.'],
  '6': ['..##.', '.#...', '#....', '####.', '#...#', '#...#', '.###.'],
  '7': ['#####', '....#', '...#.', '..#..', '.#...', '.#...', '.#...'],
  '8': ['.###.', '#...#', '#...#', '.###.', '#...#', '#...#', '.###.'],
  '9': ['.###.', '#...#', '#...#', '.####', '....#', '...#.', '.##..'],
  'A': ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
  'H': ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
  'M': ['#...#', '##.##', '#.#.#', '#.#.#', '#...#', '#...#', '#...#'],
  'D': ['####.', '#...#', '#...#', '#...#', '#...#', '#...#', '####.'],
  'E': ['#####', '#....', '#....', '####.', '#....', '#....', '#####'],
  'I': ['.###.', '..#..', '..#..', '..#..', '..#..', '..#..', '.###.'],
  'Ì': ['.#...', '..#..', '.....', '.###.', '..#..', '..#..', '..#..', '..#..', '..#..', '.###.'],
  'N': ['#...#', '#...#', '##..#', '#.#.#', '#..##', '#...#', '#...#'],
  'O': ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
  'S': ['.###.', '#...#', '#....', '.###.', '....#', '#...#', '.###.'],
  'Y': ['#...#', '#...#', '.#.#.', '..#..', '..#..', '..#..', '..#..'],
  '?': ['.###.', '#...#', '....#', '...#.', '..#..', '.....', '..#..'],
});
const SMALL = new PixelFont(5, {
  ' ': ['..', '..', '..', '..', '..'],
  '.': ['.', '.', '.', '.', '#'],
  'A': ['.#.', '#.#', '###', '#.#', '#.#'],
  'B': ['##.', '#.#', '##.', '#.#', '##.'],
  'M': ['#...#', '##.##', '#.#.#', '#...#', '#...#'],
  'T': ['###', '.#.', '.#.', '.#.', '.#.'],
  'U': ['#.#', '#.#', '#.#', '#.#', '###'],
});

// ── risposte dell'oracolo chiuso ──
// d100: 1–22 No, 23–44 No ma, 45–56 Mah, 57–78 Sì ma, 79–100 Sì
const Answer = { NO: 'NO', NO_BUT: 'NO_BUT', TIE: 'TIE', YES_BUT: 'YES_BUT', YES: 'YES' };
const ClosedOracle = {
  fromRoll(d) { return d <= 22 ? 'NO' : d <= 44 ? 'NO_BUT' : d <= 56 ? 'TIE' : d <= 78 ? 'YES_BUT' : 'YES'; },
  ask() { return this.fromRoll(Rng.die(100)); },
};
const DICE = [4, 6, 8, 10, 12, 20, 100];

const Art = {
  qualifier(a, it) { return (a === 'NO_BUT' || a === 'YES_BUT') ? (it ? 'MA...' : 'BUT') : null; },
  answer(a, it, subChars = Infinity, mainBottom = null) {
    const f = new Frame();
    const main = (a === 'NO' || a === 'NO_BUT') ? 'NO' : a === 'TIE' ? (it ? 'MAH' : 'MEH') : (it ? 'SÌ' : 'YES');
    const sub = this.qualifier(a, it);
    if (sub === null || subChars <= 0) BIG.drawCentered(f, main, mainBottom ?? 15);
    else {
      BIG.drawCentered(f, main, 12);
      const left = Math.floor((N - SMALL.width(sub)) / 2);
      SMALL.draw(f, sub.slice(0, subChars), left, 19, 2600);
    }
    return f;
  },
};

// ── gli elementi: fuoco (oracolo aperto) e vapore (oracolo chiuso) ──
const DT = 0.04;
const F_REVEAL = 1.4, F_HOLD = 1.2;
const S_THINK = 1.2, S_REVEAL = 0.5, S_PAUSE = 1.0, S_MA = 0.5, S_HOLD = 1.0;
const CLOUD_APP = 0.55;
const ease = u => { u = clamp(u, 0, 1); return u * u * (3 - 2 * u); };
const toFrame = v => { const f = new Frame(); for (let i = 0; i < v.length; i++) f.px[i] = (clamp(v[i], 0, 1) * FULL) | 0; return f; };
const lvl = (f, i) => f.px[i] / FULL;

const Elements = {
  flame(t, h = 1, cx = 12, base = 17) {
    const v = new Float64Array(N * N);
    const height = (7.5 + 1.2 * Noise.vnoise(t * 3, 0, 21)) * h;
    const sway = (Noise.vnoise(t * 2.2, 5, 22) - 0.5) * 2.2;
    for (let k = 0; k < v.length; k++) {
      const x = k % N, y = (k / N) | 0;
      const up = (base - y) / height;
      if (up < -0.25 || up > 1) continue;
      const half = 3.6 * Math.sin(Math.PI * clamp(up + 0.3, 0, 1) / 1.3) * (1 - up * 0.45);
      const dx = x - (cx + sway * up * up);
      const inside = half - Math.abs(dx);
      if (inside <= -0.4) continue;
      const core = clamp(1 - Math.abs(dx) / (half + 0.01), 0, 1) * (1 - up * 0.6);
      const edge = clamp((inside + 0.4) / 1.2, 0, 1);
      const hot = Math.pow(core, 1.6) * (1 - up * 0.5);
      const flick = 0.9 + 0.1 * Noise.vnoise(x * 0.9, y * 0.9 - t * 6, 23);
      v[k] = Math.min(1, edge * (0.22 + 0.78 * hot) * flick + ((hot > 0.55 && up < 0.6) ? 0.35 : 0));
    }
    return v;
  },

  openArrival() {
    const out = [];
    for (let i = 0; i < 16; i++) {
      const t = i * DT, v = new Float64Array(N * N);
      if (i < 3) { const x = 6 + i * 3; v[16 * N + x] = 1; v[16 * N + x - 1] = 0.5; }
      else {
        const a = (i - 3) * DT, flash = Math.exp(-a * 10);
        for (let k = 0; k < v.length; k++) v[k] = flash * 0.7 * Math.exp(-Math.hypot((k % N) - 12, ((k / N) | 0) - 15) / 3);
        const f = this.flame(t, ease(a / 0.35)); for (let k = 0; k < v.length; k++) v[k] = Math.max(v[k], f[k]);
      }
      out.push(toFrame(v));
    }
    return out;
  },

  /** La carta brucia dalla fiammella e da due salti del fuoco; resta il simbolo. */
  paper(symbol, idleS = 0, thinkS = 1.8, r = new Seeded(5)) {
    const out = [], total = idleS + thinkS + F_REVEAL + F_HOLD;
    const ign = [[12, 15], [5, 8], [19, 9]], delay = [0, 4, 6];
    const dist = new Float64Array(N * N);
    for (let k = 0; k < dist.length; k++) {
      const fiber = Noise.vnoise((k % N) / 3, ((k / N) | 0) / 3, 4) * 4;
      let m = Infinity; for (let n = 0; n < 3; n++) m = Math.min(m, Math.hypot((k % N) - ign[n][0], ((k / N) | 0) - ign[n][1]) + delay[n]);
      dist[k] = m + fiber;
    }
    for (let i = 0; i * DT < total; i++) {
      const t = i * DT, thinkT = t - idleS, v = new Float64Array(N * N);
      if (thinkT < 0) { const f = this.flame(t); v.set(f); }
      else {
        const paperLvl = 0.18 * ease(thinkT / 0.25);
        const front = Math.max(0, thinkT - 0.25) / (thinkS + F_REVEAL * 0.6) * 26;
        for (let k = 0; k < v.length; k++) {
          const d = dist[k];
          v[k] = d < front - 1.6 ? (symbol.px[k] > 0 ? lvl(symbol, k) * (0.9 + 0.1 * Math.sin(t * 9 + k)) : 0)
               : d < front ? 0.75 + 0.25 * r.nextDouble() : paperLvl;
        }
        if (thinkT < 0.4) { const f = this.flame(t, 1 - thinkT / 0.4); for (let k = 0; k < v.length; k++) v[k] = Math.max(v[k], f[k]); }
      }
      out.push(toFrame(v));
    }
    out[out.length - 1] = symbol;
    return out;
  },

  openIdle(t) { return toFrame(this.flame(t)); },

  // la nuvola: sagoma presa dalla carta "le nuvole"
  CLOUD_SHAPE: ['...####....', '...#####...', '..########.', '###########', '###########', '.#########.'],
  shapeAt(u, w) {
    const S = this.CLOUD_SHAPE, CW = S[0].length, CH = S.length; let s = 0;
    for (let oy = -1; oy <= 1; oy++) for (let ox = -1; ox <= 1; ox++) {
      const x = Math.floor(u + ox * 0.45), y = Math.floor(w + oy * 0.45);
      if (y >= 0 && y < CH && x >= 0 && x < CW && S[y][x] === '#') s++;
    }
    return s / 9;
  },
  cloudLevels(t, dy = 0, squash = 0, gain = 1, scale = CLOUD_APP) {
    const v = new Float64Array(N * N), CW = 11, CH = 6;
    const sc = scale / CLOUD_APP * 0.95;
    const sx = (1 + 0.6 * squash) * sc * (1 + 0.06 * Math.sin(t * 1.15));
    const sy = (1 - 0.55 * squash) * sc * (1 + 0.06 * Math.sin(t * 1.15 + 0.8));
    const cx = 12 + Math.sin(t * 0.5) * 1.1, cy = 12.4 + Math.sin(t * 0.8 + 1) * 0.5 + dy;
    for (let k = 0; k < v.length; k++) {
      const x = k % N, y = (k / N) | 0;
      const wob = 0.35 * (Noise.vnoise(x * 0.45 + t * 0.6, y * 0.45, 33) - 0.5);
      const f = this.shapeAt((x - cx) / sx + CW / 2 + wob, (y - cy) / sy + CH / 2 + wob * 0.6);
      if (f <= 0) continue;
      const body = clamp((f - 0.2) / 0.6, 0, 1);
      const swirl = Noise.vnoise((x - 12) * 0.5 + t * 0.55, (y - 12) * 0.5 - t * 0.7, 32);
      v[k] = Math.min(1, body * (0.26 + 0.38 * swirl) * gain);
    }
    return v;
  },
  closedIdle(t, scale = CLOUD_APP) { return toFrame(this.cloudLevels(t, 0, 0, 1, scale)); },

  /** La nuvola cade sulle pietre come una goccia, con i filamenti, e si schiaccia. */
  cloudFall(t0, scale = CLOUD_APP) {
    const n = 14, roots = [-0.55, -0.2, 0.15, 0.5], out = [];
    for (let i = 0; i < n; i++) {
      const t = t0 + i * DT, u = (i + 1) / n, fall = u * u;
      const squash = clamp((u - 0.75) / 0.25, 0, 1), dy = 7.5 * fall;
      const v = this.cloudLevels(t, dy, squash, 1 + 0.9 * squash, scale);
      const grow = clamp(u / 0.7, 0, 1) * (1 - squash);
      const bottom = 12.4 + dy + 3 * (scale / CLOUD_APP * 0.95);
      roots.forEach((rx, k) => {
        const x0 = 12 + rx * 9 * scale + Math.sin(t * 1.3 + k) * 0.5;
        const len = (3 + k % 2) * grow * (scale / CLOUD_APP);
        const strand = (x, y, ang, l, a0) => {
          for (let s2 = 0; s2 < l; s2 += 0.5) {
            const px = x + Math.sin(ang) * s2 + 0.4 * Math.sin(s2 * 1.7 + t * 4 + k), py = y + Math.cos(ang) * s2;
            const xi = Math.round(px), yi = Math.round(py);
            if (xi >= 0 && xi < N && yi >= 0 && yi < N) v[yi * N + xi] = Math.max(v[yi * N + xi], a0 * (1 - s2 / (l + 0.01)));
          }
        };
        const a0 = 0.32 + 0.1 * Math.sin(t * 3 + k);
        strand(x0, bottom, 0, len, a0);
        if (len > 2) strand(x0 + 0.4 * Math.sin(len * 1.7 + t * 4 + k), bottom + len * 0.55, k % 2 === 0 ? 0.6 : -0.6, len * 0.6, a0 * 0.8);
      });
      out.push(toFrame(v));
    }
    return out;
  },

  /** Arrivo dell'oracolo chiuso: uno sbuffo sale e lascia la nuvola. */
  closedArrival(scale = CLOUD_APP, r = new Seeded(3)) {
    const out = [], q = this.closedIdle(0, scale);
    const puffs = Array.from({ length: 40 }, () => [12 + r.nextGaussian() * 3, 24 + r.nextDouble() * 4, r.nextGaussian() * 2, -14 - r.nextDouble() * 8]);
    for (let i = 0; i < 18; i++) {
      const t = i * DT, v = new Float64Array(N * N);
      for (const p of puffs) {
        const x = p[0] + p[2] * t, y = p[1] + p[3] * t;
        for (let oy = -1; oy <= 1; oy++) for (let ox = -1; ox <= 1; ox++) {
          const xx = Math.round(x) + ox, yy = Math.round(y) + oy;
          if (xx >= 0 && xx < N && yy >= 0 && yy < N) v[yy * N + xx] += 0.12 * Math.max(0, 1 - t / 0.72);
        }
      }
      const reveal = ease((t - 0.25) / 0.4);
      for (let k = 0; k < v.length; k++) v[k] = Math.max(Math.min(v[k], 0.8), lvl(q, k) * reveal);
      out.push(toFrame(v));
    }
    return out;
  },

  /** Lo sbuffo di vapore dalle pietre calde e la risposta (col "ma…" in ritardo). */
  steam(first, raised, full, seed) {
    const r = new Seeded(seed), out = [], ps = [], glow = new Float64Array(N * N);
    const hasMa = !first.px.every((v, i) => v === full.px[i]);
    const r0 = S_THINK, total = r0 + S_REVEAL + (hasMa ? S_PAUSE + S_MA : 0) + S_HOLD;
    const splat = (x, y, a, v) => {
      const xi = Math.round(x), yi = Math.round(y);
      for (let oy = -1; oy <= 1; oy++) for (let ox = -1; ox <= 1; ox++) {
        const xx = xi + ox, yy = yi + oy;
        if (xx >= 0 && xx < N && yy >= 0 && yy < N) v[yy * N + xx] += a * (ox === 0 && oy === 0 ? 1 : 0.35);
      }
    };
    for (let i = 0; i * DT < total; i++) {
      const t = i * DT, v = new Float64Array(N * N);
      const think = t < r0, revU = clamp((t - r0) / S_REVEAL, 0, 1);
      const rate = think ? 45 : 0;
      if (i === 0) for (let n = 0; n < 18; n++)
        ps.push({ x: 12 + r.nextGaussian() * 2.5, y: 20.5 + r.nextDouble(), vx: r.nextGaussian() * 3, vy: -5 - r.nextDouble() * 5, age: 0.35, life: 1.1 + r.nextDouble() * 0.5 });
      const count = Math.floor(rate * DT + r.nextDouble());
      for (let n = 0; n < count; n++)
        ps.push({ x: 12 + r.nextGaussian() * 2, y: 22, vx: r.nextGaussian() * 4, vy: -10 - r.nextDouble() * 6, age: 0, life: 1 + r.nextDouble() * 0.6 });
      for (const p of ps) {
        const blow = t >= r0 ? 1 + revU * 4 : 1;
        p.vx *= 0.94; p.vy = p.vy * 0.97 - (t >= r0 ? 25 * DT : 0);
        p.x += p.vx * DT * blow; p.y += p.vy * DT; p.age += DT;
        splat(p.x, p.y, Math.sin(Math.PI * clamp(p.age / p.life, 0, 1)) * 0.3, v);
      }
      for (let n = ps.length - 1; n >= 0; n--) if (ps[n].age > ps[n].life || ps[n].y < -3) ps.splice(n, 1);
      if (t >= r0) {
        const maTime = hasMa && t >= r0 + S_REVEAL + S_PAUSE;
        const target = maTime ? full : first;
        for (let k = 0; k < v.length; k++) if (first.px[k] > 0 || target.px[k] > 0) {
          const x = k % N, y = (k / N) | 0;
          v[k] = Math.max(v[k], lvl(first, k) * ease(revU * 1.6 - Math.hypot(x - 12, y - 12) / 30));
        }
        if (maTime) {
          const m = t - r0 - S_REVEAL - S_PAUSE;
          for (let k = 0; k < v.length; k++) if (first.px[k] > 0 && m >= 0.12) v[k] = 0;
          for (let k = 0; k < v.length; k++) {
            if (raised.px[k] > 0 && m >= 0.12) v[k] = lvl(raised, k);
            if (m < 0.12 && first.px[k] > 0) v[k] = lvl(first, k);
            if (full.px[k] > 0 && raised.px[k] === 0) v[k] = Math.max(v[k], lvl(full, k) * ease((m - 0.12) / (S_MA * 0.7) - ((k % N) - 4) / 40));
          }
          if (m >= 0.1 && m <= 0.4) for (let n = 0; n < 3; n++) ps.push({ x: 6 + r.nextDouble() * 13, y: 21, vx: r.nextGaussian(), vy: -6, age: 0, life: 0.5 });
        }
      }
      for (let k = 0; k < glow.length; k++) glow[k] = Math.max(Math.min(v[k], 1), glow[k] * 0.55);
      out.push(toFrame(glow));
    }
    out[out.length - 1] = full;
    return out;
  },
};

// ── dadi come sagome ──
const DiceArt = (() => {
  const FILL = 1100, R = 11, SAMPLES = [[-0.25, -0.25], [0.25, -0.25], [-0.25, 0.25], [0.25, 0.25]];
  const regular = (n, startDeg, r = R) => Array.from({ length: n }, (_, i) => { const a = (startDeg + i * 360 / n) * Math.PI / 180; return [r * Math.cos(a), r * Math.sin(a)]; });
  const shapes = {
    4: [[0, -11], [9, 7], [-9, 7]],
    6: regular(4, 45, 10.5),
    8: [[0, -11], [11, 0], [0, 11], [-11, 0]],
    10: [[0, -12], [8, 4], [0, 12], [-8, 4]],
    12: regular(5, -90, 10.5),
    20: [[0, -10.5], [9, -6], [9, 6], [0, 10.5], [-9, 6], [-9, -6]],
    100: null,
  };
  const numberBottom = s => ({ 4: 17, 10: 17, 12: 16 }[s] ?? 15);
  const inside = (sides, x, y) => {
    const poly = shapes[sides]; if (!poly) return x * x + y * y <= R * R;
    let c = false;
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      const [xi, yi] = poly[i], [xj, yj] = poly[j];
      if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c;
    }
    return c;
  };
  const prune = m => m.map((on, i) => {
    const x = i % N, y = (i / N) | 0, at = (a, b) => a >= 0 && a < N && b >= 0 && b < N && m[b * N + a] ? 1 : 0;
    return on && at(x - 1, y) + at(x + 1, y) + at(x, y - 1) + at(x, y + 1) >= 2;
  });
  const mask = (sides, p) => {
    const a = -p.angle * Math.PI / 180, ca = Math.cos(a), sa = Math.sin(a);
    const m = Array.from({ length: N * N }, (_, i) => {
      const px = i % N - 12 - p.dx, py = ((i / N) | 0) - 12 - p.dy; let hits = 0;
      for (const [ox, oy] of SAMPLES) { const sx = px + ox, sy = py + oy; if (inside(sides, (sx * ca - sy * sa) / p.scale, (sx * sa + sy * ca) / p.scale)) hits++; }
      return hits >= 2;
    });
    return prune(m);
  };
  const fill = (f, m, v) => m.forEach((on, i) => { if (on) f.px[i] = v; });
  const outline = (f, m, v) => m.forEach((on, i) => {
    if (!on) return; const x = i % N, y = (i / N) | 0;
    if (x === 0 || y === 0 || x === N - 1 || y === N - 1 || !m[i - 1] || !m[i + 1] || !m[i - N] || !m[i + N]) f.px[i] = v;
  });
  const number = (f, text, sides, v) => {
    const left = Math.floor((N - BIG.width(text)) / 2), bottom = numberBottom(sides);
    if (v > 0) {
      const halo = new Frame(); BIG.draw(halo, text, left, bottom, 1);
      for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) if (halo.get(x, y) > 0)
        for (let oy = -1; oy <= 1; oy++) for (let ox = -1; ox <= 1; ox++) if (halo.get(x + ox, y + oy) === 0) f.set(x + ox, y + oy, 0);
    }
    BIG.draw(f, text, left, bottom, v);
  };
  const pose = (angle = 0, scale = 1, dx = 0, dy = 0) => ({ angle, scale, dx, dy });
  const randomPose = () => pose(Rng.double() * 360, 0.78 + Rng.double() * 0.22, Rng.int(5) - 2, Rng.int(5) - 2);
  return {
    SETTLE_DELAYS: [80, 110, 150, 200], LAND_DELAY: 260,
    idle(sides) { const f = new Frame(), m = mask(sides, pose()); fill(f, m, FILL); number(f, String(sides), sides, FULL / 2); return f; },
    tumble(sides, p) { const f = new Frame(); fill(f, mask(sides, p), FILL); return f; },
    Tumbler: class {
      constructor() { this.angle = Rng.double() * 360; this.dir = Rng.bool() ? 1 : -1; this.dx = 0; this.dy = 0; }
      next() {
        this.angle += this.dir * (35 + Rng.double() * 30);
        this.dx = clamp(this.dx + Rng.int(3) - 1, -2, 2); this.dy = clamp(this.dy + Rng.int(3) - 1, -2, 2);
        return pose(this.angle, 0.8 + Rng.double() * 0.2, this.dx, this.dy);
      }
    },
    settlePoses() {
      const start = randomPose(), tilt = (Rng.bool() ? 1 : -1) * (25 + Rng.double() * 20);
      return [0.7, -0.4, 0.2, -0.08].map(k => pose(tilt * k, 1 - Math.abs(k) * 0.12, start.dx * Math.abs(k), start.dy * Math.abs(k)));
    },
    face(sides, value) {
      const f = new Frame(), m = mask(sides, pose());
      if (value === sides) { fill(f, m, FILL); outline(f, m, FULL); }
      else if (value === 1) outline(f, m, FILL);
      else fill(f, m, FILL);
      number(f, String(value), sides, FULL);
      return f;
    },
  };
})();

// ── la regia (uguale all'app) ──
const FRAME_MS = 40, IDLE_MS = 60, HOOK_FRAMES = 6;
const blend = (a, b, k) => { const f = new Frame(); for (let i = 0; i < f.px.length; i++) f.px[i] = Math.max(0, (a.px[i] * (1 - k) + b.px[i] * k) | 0); return f; };
const steps = (frames, sound = null) => frames.map((f, i) => ({ wait: FRAME_MS, frame: f, haptic: null, sound: i === 0 ? sound : null }));

const Scripts = {
  hook(from, s) { return from ? s.map((st, i) => i < HOOK_FRAMES ? { ...st, frame: blend(from, st.frame, (i + 1) / (HOOK_FRAMES + 1)) } : st) : s; },
  closedArrival() { return steps(Elements.closedArrival(), 'chiuso_arrivo'); },
  closedIdle(t) { return Elements.closedIdle(t); },
  closedAsk(a, it, symbol, idleT) {
    const first = Art.answer(a, it, 0), raised = Art.answer(a, it, 0, 12), full = Art.answer(a, it);
    const fall = steps(Elements.cloudFall(idleT));
    const out = fall.concat(steps(Elements.steam(first, raised, full, Rng.int(10000)), 'chiuso_pensa'));
    const revealAt = fall.length + Math.floor((S_THINK + S_REVEAL * 0.6) / DT);
    if (out[revealAt]) out[revealAt] = { ...out[revealAt], haptic: 'thud' };
    if (Art.qualifier(a, it)) {
      const maAt = fall.length + Math.floor((S_THINK + S_REVEAL + S_PAUSE) / DT);
      if (out[maAt]) out[maAt] = { ...out[maAt], haptic: 'tick', sound: 'chiuso_ma' };
    }
    if (a === 'TIE' && symbol) {
      out.push(...steps(Elements.paper(symbol, 0, 1.4), 'aperto_pensa'));
      out[out.length - 1] = { ...out[out.length - 1], haptic: 'thud' };
    }
    return out;
  },
  openArrival() { return steps(Elements.openArrival(), 'aperto_arrivo'); },
  openIdle(t) { return Elements.openIdle(t); },
  openAsk(symbol) {
    const out = steps(Elements.paper(symbol, 0, 1.8), 'aperto_pensa');
    const at = Math.min(out.length - 1, Math.floor((1.8 + F_REVEAL * 0.6) / DT));
    out[at] = { ...out[at], haptic: 'thud' };
    return out;
  },
  diceSettle(sides, value) {
    const poses = DiceArt.settlePoses();
    const s = poses.map((p, i) => ({ wait: DiceArt.SETTLE_DELAYS[i], frame: DiceArt.tumble(sides, p), haptic: 'tick', sound: 'dadi_feltro' }));
    s.push({ wait: DiceArt.LAND_DELAY, frame: DiceArt.face(sides, value), haptic: 'thud', sound: 'dadi_posa' });
    return s;
  },
};

/** Un simbolo PNG (bianco su nero, 25×25) → fotogramma. */
function frameFromImage(img) {
  const c = document.createElement('canvas'); c.width = N; c.height = N;
  const g = c.getContext('2d', { willReadFrequently: true }); g.imageSmoothingEnabled = false; g.drawImage(img, 0, 0, N, N);
  const d = g.getImageData(0, 0, N, N).data, f = new Frame();
  for (let i = 0; i < N * N; i++) {
    const lum = ((d[i * 4] * 299 + d[i * 4 + 1] * 587 + d[i * 4 + 2] * 114) / 1000) | 0;
    f.px[i] = ((lum * d[i * 4 + 3] / 255) | 0) * FULL / 255 | 0;
  }
  return f;
}

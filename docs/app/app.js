// Sorte · web. Stessa regia dell'app Android: GIOCA (matrice, suggerimento, dadi, schede), MANUALE, IMPOSTAZIONI.
'use strict';

const $ = s => document.querySelector(s);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const LANDING = 'https://mattiagiordano27.github.io/sorte/';
const INSTAGRAM = 'https://www.instagram.com/mattiogiordana/';

// ── preferenze (solo comodità: se il browser non le salva, si riparte dai valori di base) ──
const Prefs = {
  get(k, d) { try { const v = localStorage.getItem('sorte.' + k); return v === null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem('sorte.' + k, JSON.stringify(v)); } catch {} },
};
let lang = Prefs.get('lingua', 'auto');
const italian = () => lang === 'it' || (lang === 'auto' && (navigator.language || '').toLowerCase().startsWith('it'));
const tx = () => TESTI.ui[italian() ? 'it' : 'en'];

const WEB = {
  it: {
    enter: 'Tocca per entrare<br>meglio con l’audio',
    hintClosedDesk: 'Fai una domanda sì/no, poi clicca o premi spazio',
    hintOpenDesk: 'Chiedi chi, cosa, come, poi clicca o premi spazio',
    hintDiceDesk: 'Clicca o premi spazio per lanciare',
    desk: 'Sul computer: clic sul tondo o barra spaziatrice; 1, 2 e 3 cambiano strumento, le frecce cambiano dado.',
    auto: 'Automatico',
    sounds: 'Suoni', on: 'Attivi', off: 'Spenti',
    soundsHelp: 'Vapore, fuoco e dadi. Meglio con le cuffie.',
    shake: 'Scuoti per chiedere', shakeOnTxt: 'Attivo', shakeOffTxt: 'Spento',
    shakeHelp: 'Su iPhone e iPad Safari chiede il permesso del movimento a ogni apertura: attivalo solo se vuoi chiedere scuotendo il telefono.',
    install: 'Installala come app',
    installSteps: [['iPhone e iPad', 'in Safari tocca Condividi, poi «Aggiungi alla schermata Home».'], ['Android', 'menu ⋮, poi «Installa app».'],
      ['Computer', 'in Chrome o Edge, l’icona di installazione nella barra degli indirizzi; su Mac anche Safari, File › Aggiungi al Dock.']], installNote: 'Una volta installata funziona anche offline.',
    android: 'Su Android',
    androidBody: 'L’app per Android ha anche i widget, e su Nothing Phone (3) i tre strumenti vivono sul retro, sulla Glyph Matrix.',
    androidLink: 'Scarica l’app',
    privacy: ['Sorte non raccoglie e non vende nessun dato: niente account, pubblicità o tracciamento.', 'Lingua e dado scelti restano solo su questo dispositivo.', 'Dopo la prima apertura funziona offline.'],
    credits: 'Crediti', independent: 'App indipendente, non affiliata a Nothing.',
  },
  en: {
    enter: 'Tap to enter<br>best with sound on',
    hintClosedDesk: 'Ask a yes/no question, then click or press space',
    hintOpenDesk: 'Ask who, what, how, then click or press space',
    hintDiceDesk: 'Click or press space to roll',
    desk: 'On a computer: click the circle or press the space bar; 1, 2 and 3 switch tool, the arrow keys change die.',
    auto: 'Auto',
    sounds: 'Sounds', on: 'On', off: 'Off',
    soundsHelp: 'Steam, fire and dice. Best with headphones.',
    shake: 'Shake to ask', shakeOnTxt: 'On', shakeOffTxt: 'Off',
    shakeHelp: 'On iPhone and iPad, Safari asks for motion access every time you open Sorte: turn it on only if you want to ask by shaking the phone.',
    install: 'Install it as an app',
    installSteps: [['iPhone and iPad', 'in Safari tap Share, then “Add to Home Screen”.'], ['Android', 'menu ⋮, then “Install app”.'],
      ['Computer', 'in Chrome or Edge, the install icon in the address bar; on a Mac, also Safari, File › Add to Dock.']], installNote: 'Once installed it works offline too.',
    android: 'On Android',
    androidBody: 'The Android app also has widgets, and on Nothing Phone (3) the three tools live on the back, on the Glyph Matrix.',
    androidLink: 'Get the app',
    privacy: ["Sorte doesn't collect or sell any data: no account, no ads, no tracking.", "Your language and die stay on this device only.", "After the first visit it works offline."],
    credits: 'Credits', independent: 'Independent app, not affiliated with Nothing.',
  },
};
const wx = () => WEB[italian() ? 'it' : 'en'];
const touch = matchMedia('(pointer:coarse)').matches;
// iPhone e iPad: il movimento richiede un permesso, che Safari chiede di nuovo a ogni apertura
const motionAsk = typeof DeviceMotionEvent !== 'undefined' && typeof DeviceMotionEvent.requestPermission === 'function';
let shakeOn = Prefs.get('scuoti', !motionAsk);

// ── suoni (Web Audio: partono subito, senza ritardo) ──
const Sound = {
  names: ['chiuso_arrivo', 'chiuso_pensa', 'chiuso_ma', 'aperto_arrivo', 'aperto_pensa', 'dadi_clic', 'dadi_feltro', 'dadi_posa'],
  gain: { dadi_clic: 0.45, chiuso_arrivo: 0.6, aperto_arrivo: 0.7 },
  raw: {}, buf: {}, live: [], ctx: null, on: Prefs.get('suoni', true),
  prefetch() { this.names.forEach(n => this.raw[n] = fetch('suoni/' + n + '.wav').then(r => r.arrayBuffer()).catch(() => null)); },
  async unlock() {
    if (!this.ctx) {
      const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return;
      this.ctx = new AC();
      // iOS: un suono vuoto dentro il tocco sblocca l'audio
      const s = this.ctx.createBufferSource(); s.buffer = this.ctx.createBuffer(1, 1, 22050); s.connect(this.ctx.destination); s.start(0);
      await Promise.all(this.names.map(async n => {
        const ab = await this.raw[n]; if (!ab) return;
        try { this.buf[n] = await new Promise((ok, ko) => this.ctx.decodeAudioData(ab.slice(0), ok, ko)); } catch {}
      }));
    }
    if (this.ctx.state === 'suspended') try { await this.ctx.resume(); } catch {}
  },
  play(n) {
    if (!this.on || !this.ctx || !this.buf[n] || document.hidden) return;
    const src = this.ctx.createBufferSource(), g = this.ctx.createGain();
    src.buffer = this.buf[n];
    // i colpi dei dadi variano un po' di tono e volume, come colpi veri
    if (n === 'dadi_clic' || n === 'dadi_feltro') src.playbackRate.value = 0.88 + Math.random() * 0.3;
    g.gain.value = (this.gain[n] ?? 1) * (n === 'dadi_clic' ? 0.5 + Math.random() * 0.5 : 1);
    src.connect(g).connect(this.ctx.destination); src.start();
    this.live.push(src); src.onended = () => { this.live = this.live.filter(s => s !== src); };
  },
  stopAll() { this.live.forEach(s => { try { s.stop(); } catch {} }); this.live = []; },
};
Sound.prefetch();

const haptic = h => { if (h && navigator.vibrate && !document.hidden) try { navigator.vibrate(h === 'thud' ? 28 : 8); } catch {} };

// ── la matrice ──
const canvas = $('#matrix'), g2 = canvas.getContext('2d');
let frame = new Frame(), dirty = true;
function drawMatrix(c, f) {
  const g = c.getContext('2d'), size = c.width, px = f.toArray();
  g.fillStyle = '#000'; g.fillRect(0, 0, size, size);
  const pitch = size / (N + 1), r = pitch * 0.36;
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    if (!inCircle(x, y)) continue;
    const v = px[y * N + x] / FULL, l = Math.round(28 + (255 - 28) * v);
    g.fillStyle = `rgb(${l},${l},${l})`;
    g.beginPath(); g.arc(pitch * (x + 1), pitch * (y + 1), r, 0, Math.PI * 2); g.fill();
  }
}
function fitCanvas() {
  const s = Math.round(canvas.clientWidth * Math.min(3, window.devicePixelRatio || 1));
  if (s && canvas.width !== s) { canvas.width = s; canvas.height = s; dirty = true; }
}
function show(f) { frame = f; dirty = true; }
(function loop() { if (dirty) { dirty = false; drawMatrix(canvas, frame); } requestAnimationFrame(loop); })();
// iPhone: l'altezza davvero visibile (senza le barre di Safari), anche dopo la rotazione
// altezza visibile: i browser moderni (iPhone compreso) la danno giusta con 100dvh; window.innerHeight solo per i vecchi,
// perché su iPhone aperto dalla Home può valere meno dello schermo all'avvio e tagliare le schede in basso
const setH = () => { if (!(window.CSS && CSS.supports('height', '100dvh'))) document.documentElement.style.setProperty('--H', window.innerHeight + 'px'); fitCanvas(); };
addEventListener('resize', setH); addEventListener('orientationchange', () => setTimeout(setH, 250)); setH();

// ── simboli dell'oracolo aperto ──
const SYMBOLS = ['bicchiere', 'chiave', 'finestra', 'lucchetto', 'nuvole', 'palma', 'porta', 'presa_elettrica', 'recinto', 'scatola', 'sedia', 'stella'];
const symbols = {};
const symbolsReady = Promise.all(SYMBOLS.map(n => new Promise(ok => {
  const im = new Image(); im.onload = () => { symbols[n] = frameFromImage(im); ok(); }; im.onerror = ok; im.src = 'icone/' + n + '.png';
})));
const randomSymbol = () => { const k = Object.keys(symbols); return k.length ? symbols[k[Rng.int(k.length)]] : null; };

// ── stato del gioco ──
let mode = 0, dieIdx = Math.min(DICE.length - 1, Math.max(0, Prefs.get('dado', 5)));
let busy = false, holding = false, idleToken = 0, idleClock = 0, entered = false;

let playToken = 0;
/** Mostra i passi. [unlockMs] = quanto prima della fine si può già fare una nuova domanda. */
async function play(steps, unlockMs = 0) {
  const token = ++playToken;
  let left = steps.reduce((a, s) => a + s.wait, 0);
  for (const s of steps) {
    await sleep(s.wait);
    if (token !== playToken) return false;      // è partita una nuova domanda: questa si ferma qui
    show(s.frame); left -= s.wait;
    if (!document.hidden) { haptic(s.haptic); if (s.sound) Sound.play(s.sound); }
    if (unlockMs && busy && left <= unlockMs) busy = false;
  }
  return token === playToken;
}

/** Arrivo e attesa continua (oracoli); per i dadi la sagoma ferma. */
async function arrive() {
  const token = ++idleToken;
  if (mode === 2) { show(DiceArt.idle(DICE[dieIdx])); return; }
  busy = true;
  await play(mode === 0 ? Scripts.closedArrival() : Scripts.openArrival());
  busy = false;
  if (token !== idleToken) return;
  let t = 0;
  while (token === idleToken) {
    show(mode === 0 ? Scripts.closedIdle(t) : Scripts.openIdle(t));
    idleClock = t; await sleep(IDLE_MS); t += IDLE_MS / 1000;
  }
}

/** Oracoli: la domanda parte dall'attesa e ci si aggancia. */
async function ask() {
  if (busy || mode === 2) return;
  busy = true; idleToken++; Sound.stopAll();
  const m = mode, from = frame;
  await symbolsReady;
  const steps = m === 0 ? Scripts.closedAsk(ClosedOracle.ask(), italian(), randomSymbol(), idleClock)
                        : (randomSymbol() ? Scripts.openAsk(randomSymbol()) : []);
  // il risultato resta acceso, ma dopo poco si può già chiedere di nuovo
  if (await play(Scripts.hook(from, steps))) busy = false;   // bloccato fino alla fine: ritmo riflessivo
}

/** Dadi: rotola finché [holding] (tocco = un attimo, scuotere = quanto scuoti), poi si posa. */
async function roll() {
  if (busy) return;
  busy = true; holding = true; Sound.stopAll();
  const s = DICE[dieIdx], tumbler = new DiceArt.Tumbler();
  for (let i = 0; holding || i < 10; i++) {
    show(DiceArt.tumble(s, tumbler.next()));
    if (Math.random() < 0.4) Sound.play('dadi_clic');
    await sleep(55);
  }
  await play(Scripts.diceSettle(s, Rng.die(s)));
  busy = false;
}

function tap() {
  if (!entered) return;
  if (mode !== 2) { ask(); return; }
  if (busy) return;
  roll(); setTimeout(() => { holding = false; }, 900);
}

function setMode(i) {
  if (busy || mode === i) return;
  Sound.stopAll(); mode = i; renderPlay(); arrive();
}
function setDie(i) {
  if (busy || dieIdx === i) return;
  haptic('tick'); dieIdx = i; Prefs.set('dado', i); show(DiceArt.idle(DICE[i])); renderDice();
}

// ── scuotere il telefono: oracoli = domanda; dadi = rotolano finché scuoti ──
const Shake = {
  g: [0, 0, 0], samples: 0, shaking: false, last: 0, peaks: [],
  on(e) {
    const a = e.accelerationIncludingGravity; if (!a || a.x === null) return;
    const v = [a.x, a.y, a.z];
    if (this.samples === 0) this.g = v.slice(); else for (let i = 0; i < 3; i++) this.g[i] = 0.85 * this.g[i] + 0.15 * v[i];
    if (++this.samples < 15) return;
    const force = Math.hypot(v[0] - this.g[0], v[1] - this.g[1], v[2] - this.g[2]), now = performance.now();
    if (force > 5) {
      this.last = now;
      if (!this.shaking) {
        if (!this.peaks.length || now - this.peaks[this.peaks.length - 1] > 120) this.peaks.push(now);
        while (this.peaks.length && now - this.peaks[0] > 800) this.peaks.shift();
        if (this.peaks.length >= 2) { this.shaking = true; this.peaks = []; if (entered && !document.hidden && !pageOpen()) (mode === 2 ? roll() : ask()); }
      }
    } else if (this.shaking && now - this.last > 450) { this.shaking = false; holding = false; }
  },
  listening: false,
  async enable() {
    if (!shakeOn) return;
    try { if (motionAsk) await DeviceMotionEvent.requestPermission(); } catch {}
    if (!this.listening) { this.listening = true; addEventListener('devicemotion', e => { if (shakeOn) this.on(e); }); }
  },
};

// app in secondo piano o schermo bloccato: silenzio subito
document.addEventListener('visibilitychange', () => { if (document.hidden) { Sound.stopAll(); holding = false; } });

// ── disegno dell'interfaccia ──
function renderPlay() {
  const t = tx(), w = wx();
  document.documentElement.lang = italian() ? 'it' : 'en';
  $('#tagline').textContent = t.tagline;
  $('#btnManual').setAttribute('aria-label', t.manual); $('#btnSettings').setAttribute('aria-label', t.settings);
  const hint = touch ? [t.hintClosed, t.hintOpen, t.hintDice][mode] : [w.hintClosedDesk, w.hintOpenDesk, w.hintDiceDesk][mode];
  $('#hint').textContent = (touch && !shakeOn) ? hint.replace(' o scuoti', '').replace(' or shake', '') : hint;
  $('#tabs').innerHTML = '';
  [t.closed, t.open, t.dice].forEach((label, i) => {
    const b = document.createElement('button'); b.className = 'tab label' + (mode === i ? ' sel' : ''); b.textContent = label;
    b.onclick = () => setMode(i); $('#tabs').appendChild(b);
  });
  renderDice();
  $('#enterText').innerHTML = w.enter;
}
function renderDice() {
  const d = $('#dice'); d.classList.toggle('on', mode === 2); d.innerHTML = '';
  DICE.forEach((s, i) => {
    const b = document.createElement('button'); b.className = 'die' + (dieIdx === i ? ' sel' : '');
    b.setAttribute('aria-label', 'd' + s);
    b.innerHTML = `<small>D</small><b${s === 100 ? ' class="long"' : ''}>${s}</b>`; b.onclick = () => setDie(i); d.appendChild(b);
  });
}

const esc = s => s.replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
function pageHead(label, title) {
  return `<div class="bar"><button class="round" data-back aria-label="Back">←</button><span class="label">${esc(label)}</span></div><h2 class="dot">${esc(title)}</h2>`;
}

/** Il manuale dell'app, senza le parti che riguardano solo il telefono (retro del Phone (3), widget). */
function manualBlocks() {
  const it = italian(), src = TESTI.manual[it ? 'it' : 'en'], out = [];
  for (let i = 0; i < src.length; i++) {
    const b = src[i];
    if (b[0] === 'Heading' && (b[1].includes('Nothing Phone (3)') || /widget/i.test(b[1]))) {
      while (src[i + 1] && src[i + 1][0] === 'Para') i++;
      continue;
    }
    if (b[0] === 'Para' && b[1].includes('sorte/app')) continue;   // qui sei già nel browser
    out.push(b);
    if (b[0] === 'Para' && i > 0 && src[i - 1][0] === 'Heading' && src[i - 1][1] === (it ? "Nell'app" : 'In the app')) out.push(['Para', wx().desk]);
  }
  return out;
}

function renderManual() {
  const t = tx(), it = italian();
  let h = pageHead(t.manual, t.manualTitle);
  for (const b of manualBlocks()) {
    switch (b[0]) {
      case 'Chapter': h += `<div class="chapter"><div class="num dot">${b[1]}</div><h3 class="dot">${esc(b[2])}</h3></div>`; break;
      case 'Heading': h += `<div class="head label">${esc(b[1])}</div>`; break;
      case 'Para': h += `<p>${esc(b[1])}</p>`; break;
      case 'Rule': h += `<div class="rule"><span>${esc(b[1])}</span></div>`; break;
      case 'Row': h += `<div class="row"><div class="ans">${esc(b[1])}</div><p>${esc(b[2])}</p></div>`; break;
      case 'Example': h += `<div class="ex"><div class="q">${esc(it ? '— ' + b[1] : '“' + b[1] + '”')}</div><div class="r">→&nbsp; ${esc(b[2].toUpperCase())}</div><div>${esc(b[3])}</div></div>`; break;
      case 'Symbols':
        h += '<div class="symbols">' + SYMBOLS.map(n => {
          const key = n.replace('_', ' ');
          const name = it ? (TESTI.symIt[key] || key) : 'the ' + (TESTI.symEn[key] || key);
          return `<div><canvas data-sym="${n}" width="300" height="300"></canvas><span class="label">${esc(name)}</span></div>`;
        }).join('') + '</div>';
    }
  }
  $('#manual .in').innerHTML = h;
  symbolsReady.then(() => document.querySelectorAll('[data-sym]').forEach(c => symbols[c.dataset.sym] && drawMatrix(c, symbols[c.dataset.sym])));
}

function renderSettings() {
  const t = tx(), w = wx();
  const chips = (opts, cur, fn) => `<div class="chips">${opts.map(([v, name]) =>
    `<button class="chip label${cur === v ? ' sel' : ''}" data-${fn}="${v}">${esc(name)}</button>`).join('')}</div>`;
  let h = pageHead(t.settings, t.settings);
  h += `<div class="label muted">${esc(t.language)}</div>` + chips([['auto', w.auto], ['it', 'Italiano'], ['en', 'English']], lang, 'lang');
  h += `<div class="sect label muted">${esc(w.sounds)}</div>` + chips([['1', w.on], ['0', w.off]], Sound.on ? '1' : '0', 'snd');
  h += `<p class="muted">${esc(w.soundsHelp)}</p>`;
  if (motionAsk) {
    h += `<div class="sect label muted">${esc(w.shake)}</div>` + chips([['1', w.shakeOnTxt], ['0', w.shakeOffTxt]], shakeOn ? '1' : '0', 'shk');
    h += `<p class="muted">${esc(w.shakeHelp)}</p>`;
  }
  h += `<div class="sect label muted">${esc(w.install)}</div>` + w.installSteps.map(([d, t]) => `<div class="rule"><span><b>${esc(d)}</b>: ${esc(t)}</span></div>`).join('') + `<p class="muted">${esc(w.installNote)}</p>`;
  h += `<div class="sect label muted">${esc(w.android)}</div><p>${esc(w.androidBody)}</p>`;
  h += `<div class="chips"><a class="chip label" href="${LANDING}" target="_blank" rel="noopener">${esc(w.androidLink)} ↗</a></div>`;
  h += `<div class="sect label muted">Privacy</div>` + w.privacy.map(l => `<p>${esc(l)}</p>`).join('');
  h += `<div class="sep"></div><div class="label muted">${esc(w.credits)}</div><div style="height:10px"></div>`;
  TESTI.credits[italian() ? 'it' : 'en'].forEach((l, i) => {
    let t = esc(l.startsWith('Glyph Matrix SDK') ? w.independent : l);
    if (i === 0) t = t.replace('Mattia Giordano', `<a class="ig" href="${INSTAGRAM}" target="_blank" rel="noopener">Mattia Giordano</a>`);   // una sola volta
    h += `<p class="muted">${t}</p>`;
  });
  h += `<div class="label muted" style="margin-top:10px">v${TESTI.version}</div>`;
  $('#settings .in').innerHTML = h;
  document.querySelectorAll('[data-lang]').forEach(b => b.onclick = () => { lang = b.dataset.lang; Prefs.set('lingua', lang); renderPlay(); renderSettings(); });
  document.querySelectorAll('[data-shk]').forEach(b => b.onclick = () => { shakeOn = b.dataset.shk === '1'; Prefs.set('scuoti', shakeOn); if (shakeOn) Shake.enable(); renderPlay(); renderSettings(); });
  document.querySelectorAll('[data-snd]').forEach(b => b.onclick = () => { Sound.on = b.dataset.snd === '1'; Prefs.set('suoni', Sound.on); if (!Sound.on) Sound.stopAll(); renderSettings(); });
}

// ── pagine ──
const pageOpen = () => !!document.querySelector('.page.on');
function openPage(id) {
  (id === 'manual' ? renderManual : renderSettings)();
  const p = $('#' + id); p.classList.add('on'); p.scrollTop = 0;
  p.querySelector('[data-back]').onclick = closePages;
}
function closePages() { document.querySelectorAll('.page').forEach(p => p.classList.remove('on')); }
$('#btnManual').onclick = () => openPage('manual');
$('#btnSettings').onclick = () => openPage('settings');

// ── comandi ──
canvas.addEventListener('click', tap);
addEventListener('keydown', e => {
  if (e.metaKey || e.ctrlKey || e.altKey) return;
  if (e.key === 'Escape') { closePages(); return; }
  if (!entered) { if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); enter(); } return; }
  if (pageOpen()) return;
  if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); if (!e.repeat) tap(); }
  else if (e.key >= '1' && e.key <= '3') setMode(+e.key - 1);
  else if (mode === 2 && e.key === 'ArrowRight') setDie(Math.min(DICE.length - 1, dieIdx + 1));
  else if (mode === 2 && e.key === 'ArrowLeft') setDie(Math.max(0, dieIdx - 1));
});

// ── ingresso: il primo tocco sblocca audio e movimento, poi arriva la nuvola ──
async function enter() {
  if (entered) return;
  entered = true;
  Shake.enable();
  await Promise.race([Sound.unlock(), sleep(1500)]);
  $('#enter').classList.add('out'); setTimeout(() => { $('#enter').style.display = 'none'; }, 600);
  fitCanvas(); arrive();
}
$('#enter').addEventListener('click', enter);

renderPlay(); fitCanvas(); show(new Frame());

if ('serviceWorker' in navigator && location.protocol === 'https:') navigator.serviceWorker.register('sw.js').catch(() => {});

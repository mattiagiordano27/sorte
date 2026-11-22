// Offline: con la rete prende sempre la versione nuova (e la salva); senza rete usa la copia salvata.
const VERSIONE = 'sorte-web-0.27-13';
const FILE = ["./", "index.html", "engine.js", "testi.js", "app.js", "manifest.webmanifest", "font/doto.ttf", "icon-180.png", "icon-192.png", "icon-512.png", "icone/bicchiere.png", "icone/chiave.png", "icone/finestra.png", "icone/lucchetto.png", "icone/nuvole.png", "icone/palma.png", "icone/porta.png", "icone/presa_elettrica.png", "icone/recinto.png", "icone/scatola.png", "icone/sedia.png", "icone/stella.png", "suoni/aperto_arrivo.wav", "suoni/aperto_pensa.wav", "suoni/chiuso_arrivo.wav", "suoni/chiuso_ma.wav", "suoni/chiuso_pensa.wav", "suoni/dadi_clic.wav", "suoni/dadi_feltro.wav", "suoni/dadi_posa.wav"];
self.addEventListener('install', e => e.waitUntil(caches.open(VERSIONE)
  .then(c => c.addAll(FILE.map(u => new Request(u, { cache: 'reload' })))).then(() => self.skipWaiting())));
self.addEventListener('activate', e => e.waitUntil(caches.keys()
  .then(k => Promise.all(k.filter(n => n !== VERSIONE).map(n => caches.delete(n)))).then(() => self.clients.claim())));
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  e.respondWith(fetch(e.request, { cache: 'no-store' }).then(r => {
    if (r.ok) { const copy = r.clone(); caches.open(VERSIONE).then(c => c.put(e.request, copy)); }
    return r;
  }).catch(() => caches.match(e.request, { ignoreSearch: true })));
});

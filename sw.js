/* MATHOS — service hors ligne */
const CACHE = 'mathos-22bc4f0fb2';
const FICHIERS = ['./', 'index.html', 'manifest.webmanifest', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/icon-maskable-512.png', 'icons/apple-touch-icon.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(FICHIERS)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const r = e.request;
  if (r.method !== 'GET' || new URL(r.url).origin !== self.location.origin) return;
  if (r.mode === 'navigate') {
    e.respondWith(fetch(r).then(rep => { if (rep.ok) { const copie = rep.clone(); caches.open(CACHE).then(c => c.put('index.html', copie)); } return rep; }).catch(() => caches.match('index.html')));
  } else e.respondWith(caches.match(r).then(m => m || fetch(r)));
});

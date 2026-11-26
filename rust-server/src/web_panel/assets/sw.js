const CACHE_NAME = 'shod-panel-shell-2.5.0-pwa4';
const base = self.location.pathname.replace(/\/sw\.js$/, '');
const SHELL_ASSETS = ['/manifest.webmanifest', '/pwa-icon.svg', '/pwa-icon-192.png', '/pwa-icon-512.png', '/qrcode.min.js'].map(p => (base ? base : '') + p);

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    try {
      const cache = await caches.open(CACHE_NAME);
      await cache.addAll(SHELL_ASSETS);
    } catch (_) {}
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(names.filter(name => name !== CACHE_NAME).map(name => caches.delete(name)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', event => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin || url.pathname.startsWith('/api/') || url.pathname.includes('/api/')) return;

  if (SHELL_ASSETS.includes(url.pathname)) {
    event.respondWith(caches.match(request).then(cached => cached || fetch(request)));
  }
});

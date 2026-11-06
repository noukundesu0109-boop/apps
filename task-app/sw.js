const CACHE = 'tasks-app-202611060400';
const SHELL = ['./', './index.html', './app.webmanifest', './icon-192.png', './icon-512.png', './icon-maskable-512.png', './apple-touch-icon.png'];
self.addEventListener('install', (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting())) });
self.addEventListener('activate', (e) => { e.waitUntil((async () => {
  const keys = await caches.keys(); await Promise.all(keys.filter((k) => k.startsWith('tasks-app-') && k !== CACHE).map((k) => caches.delete(k))); await self.clients.claim()
})()) });
self.addEventListener('fetch', (e) => {
  const req = e.request; if (req.method !== 'GET') return
  const url = new URL(req.url); if (url.origin !== location.origin) return
  const freshFirst = req.mode === 'navigate' || url.pathname.endsWith('/app.webmanifest')
  e.respondWith((async () => {
    const cache = await caches.open(CACHE)
    if (freshFirst) {
      try {
        const res = await fetch(req)
        if (res.ok) cache.put(req, res.clone())
        return res
      } catch (err) {
        const hit = await cache.match(req, { ignoreSearch: true }) || (req.mode === 'navigate' ? await cache.match('./index.html') : undefined)
        if (hit) return hit
        throw err
      }
    }
    const hit = await cache.match(req, { ignoreSearch: true })
    if (hit) return hit
    try { const res = await fetch(req); if (res.ok) cache.put(req, res.clone()); return res }
    catch (err) { if (req.mode === 'navigate') { const f = await cache.match('./index.html'); if (f) return f } throw err }
  })())
});

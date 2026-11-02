const OLD_PREFIX = 'rakuten-shell-';
const FROM = '/apps/rakuten';
const TO = '/apps/rakuten-app';
self.addEventListener('install', function (event) { self.skipWaiting(); });
self.addEventListener('activate', function (event) {
  event.waitUntil((async function () {
    var keys = await caches.keys();
    await Promise.all(keys.filter(function (key) { return key.indexOf(OLD_PREFIX) === 0; }).map(function (key) { return caches.delete(key); }));
    var windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    await Promise.all(windows.map(function (client) {
      try {
        var url = new URL(client.url);
        if (url.pathname === FROM || url.pathname.indexOf(FROM + '/') === 0) {
          url.pathname = TO + url.pathname.slice(FROM.length);
          return client.navigate(url.href);
        }
      } catch (e) {}
    }));
    await self.registration.unregister();
  })());
});

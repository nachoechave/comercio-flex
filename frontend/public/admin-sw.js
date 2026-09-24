const VERSION = 'v2';
const SHELL_CACHE = `comercio-flex-admin-shell-${VERSION}`;
const STATIC_CACHE = `comercio-flex-admin-static-${VERSION}`;
const ADMIN_PATH = /^\/admin(?:\/|$)|^\/tiendas\/[^/]+\/admin(?:\/|$)|^\/superadmin(?:\/|$)/;
const PRECACHE = [
  '/admin',
  '/admin-offline.html',
  '/admin.webmanifest',
  '/assets/comercio-flex/favicon-32x32.png',
  '/assets/comercio-flex/icon-192x192.png',
  '/assets/comercio-flex/icon-512x512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE)
      .then((cache) => cache.addAll(PRECACHE))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys
          .filter((key) => key.startsWith('comercio-flex-admin-') && ![SHELL_CACHE, STATIC_CACHE].includes(key))
          .map((key) => caches.delete(key)),
      ))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;

  if (request.mode === 'navigate') {
    if (!ADMIN_PATH.test(url.pathname)) return;
    event.respondWith(networkFirstAdminNavigation(request));
    return;
  }

  if (isSafeStaticAsset(request, url)) {
    event.respondWith(cacheFirstStatic(request));
  }
});

async function networkFirstAdminNavigation(request) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(SHELL_CACHE);
      await cache.put(request, response.clone());
    }
    return response;
  } catch {
    return (
      await caches.match(request) ||
      await caches.match('/admin') ||
      await caches.match('/admin-offline.html')
    );
  }
}

function isSafeStaticAsset(request, url) {
  if (url.pathname.startsWith('/assets/comercio-flex/')) return true;
  return ['script', 'style', 'font'].includes(request.destination);
}

async function cacheFirstStatic(request) {
  const cached = await caches.match(request);
  if (cached) return cached;

  const response = await fetch(request);
  if (response.ok) {
    const cache = await caches.open(STATIC_CACHE);
    await cache.put(request, response.clone());
  }
  return response;
}

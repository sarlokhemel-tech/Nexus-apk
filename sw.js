// sw.js - Service Worker for Nexus Web Push
self.addEventListener('push', function(event) {
  let payload = {};
  try { payload = event.data.json(); } catch (e) { payload = {title:'Nexus', body: event.data ? event.data.text() : 'New message'}; }
  const title = payload.title || 'Nexus';
  const options = {
    body: payload.body || 'New message',
    icon: payload.icon || '/static/icon-192.png',
    badge: payload.badge || '/static/badge-72.png',
    data: payload.data || {}
  };
  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', function(event) {
  event.notification.close();
  event.waitUntil(clients.matchAll({type: 'window'}).then(clientList => {
    for (const c of clientList) if (c.url && 'focus' in c) return c.focus();
    return clients.openWindow('/');
  }));
});

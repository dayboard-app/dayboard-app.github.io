/*
  Dayboard's service worker.

  It does one thing: decide what happens when a timer notification is clicked. The
  notifications themselves are shown by the page, through this worker's
  registration, which is the only route that supports a vibration pattern and the
  only one that works at all on Android.

  There is deliberately no `fetch` handler and no caching. Dayboard is not offline-
  capable, and a worker that served stale files would turn every deploy into a
  question of which version somebody happened to be running.

  There is also no `push` handler yet. Reaching a user's *other* devices needs a
  server to push to them; this worker only shows what the open page asks it to.
*/

self.addEventListener('notificationclick', function (event) {
  event.notification.close();

  // Where the notification came from, put there when it was shown. The fallback is
  // this worker's own scope - the directory it was registered from - rather than the
  // host root, which would be a different copy of the app whenever this one is served
  // under a path.
  var url =
    (event.notification.data && event.notification.data.url) || self.registration.scope;

  event.waitUntil(
    self.clients
      // `includeUncontrolled` matters: a tab loaded before this worker took over is
      // not controlled by it, and without this the click would open a second copy
      // of an app the user already has open.
      .matchAll({ type: 'window', includeUncontrolled: true })
      .then(function (windows) {
        for (var i = 0; i < windows.length; i++) {
          if (windows[i].url.indexOf(url) === 0 && 'focus' in windows[i]) {
            return windows[i].focus();
          }
        }

        return self.clients.openWindow(url);
      })
  );
});

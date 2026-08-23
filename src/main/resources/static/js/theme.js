/*
 * Theme toggle: light default, dark available, choice persisted.
 * The anti-flash script in base.html sets the initial data-theme before
 * first paint; this only reacts to the user.
 */
(function () {
  'use strict';

  var button = document.getElementById('themeToggle');
  if (!button) return;

  function apply(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    try { localStorage.setItem('theme', theme); } catch (e) { /* private mode */ }
  }

  button.addEventListener('click', function () {
    var current = document.documentElement.getAttribute('data-theme');
    apply(current === 'dark' ? 'light' : 'dark');
  });
})();

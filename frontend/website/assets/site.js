// Fills contact details from site-config.js and runs the mobile menu. No tracking, no cookies.
(function () {
  var s = window.SITE || {};
  function digits(v) { return String(v || '').replace(/[^0-9+]/g, ''); }
  document.querySelectorAll('[data-site]').forEach(function (el) {
    var key = el.getAttribute('data-site');
    var value = s[key];
    var empty = !value || (Array.isArray(value) && value.length === 0);
    var holder = el.closest('[data-site-optional]');
    if (empty) {
      if (holder) { holder.hidden = true; }
      return;
    }
    if (key === 'phone' || key === 'whatsapp') {
      if (el.tagName === 'A') { el.href = key === 'phone' ? 'tel:' + digits(value) : 'https://wa.me/' + digits(value).replace('+', ''); }
      if (!el.hasAttribute('data-keep-text')) { el.textContent = value; }
    } else if (key === 'email') {
      if (el.tagName === 'A') { el.href = 'mailto:' + value; }
      el.textContent = value;
    } else if (key === 'mapUrl' || key === 'bookingUrl') {
      el.href = value;
    } else if (Array.isArray(value)) {
      el.textContent = '';
      value.forEach(function (line, i) { if (i) { el.appendChild(document.createElement('br')); } el.appendChild(document.createTextNode(line)); });
    } else {
      el.textContent = value;
    }
  });
  // Show "call" actions only when a phone number is set, "contact" otherwise.
  document.querySelectorAll('[data-when-phone]').forEach(function (el) { el.hidden = !s.phone; });
  document.querySelectorAll('[data-when-no-phone]').forEach(function (el) { el.hidden = !!s.phone; });
  var year = document.getElementById('year');
  if (year) { year.textContent = String(new Date().getFullYear()); }
  var button = document.querySelector('.menu-button');
  var nav = document.getElementById('site-nav');
  if (button && nav) {
    button.addEventListener('click', function () {
      var open = nav.classList.toggle('open');
      button.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
  }
})();

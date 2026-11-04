// Fills contact details from site-config.js and runs the mobile menu. No tracking, no cookies.
(function () {
  var s = window.SITE || {};
  function digits(v) { return String(v || '').replace(/[^0-9+]/g, ''); }
  function tel(v) { var d = digits(v); return d.charAt(0) === '0' ? 'tel:+91' + d.slice(1) : 'tel:' + d; }
  document.querySelectorAll('[data-site]').forEach(function (el) {
    var key = el.getAttribute('data-site');
    var value = s[key];
    var empty = !value || (Array.isArray(value) && value.length === 0);
    var holder = el.closest('[data-site-optional]');
    if (empty) {
      if (holder) { holder.hidden = true; } else { el.hidden = true; }
      return;
    }
    var keepText = el.hasAttribute('data-keep-text');
    if (key === 'phone' || key === 'mobile') {
      if (el.tagName === 'A') { el.href = tel(value); }
      if (!keepText) { el.textContent = value; }
    } else if (key === 'whatsapp') {
      if (el.tagName === 'A') { el.href = 'https://wa.me/' + digits(value).replace('+', ''); el.rel = 'noopener'; }
      if (!keepText) { el.textContent = value; }
    } else if (key === 'email') {
      if (el.tagName === 'A') { el.href = 'mailto:' + value; }
      if (!keepText) { el.textContent = value; }
    } else if (key === 'mapUrl' || key === 'bookingUrl') {
      el.href = value;
      el.rel = 'noopener';
    } else if (Array.isArray(value)) {
      el.textContent = '';
      value.forEach(function (line, i) { if (i) { el.appendChild(document.createElement('br')); } el.appendChild(document.createTextNode(line)); });
    } else {
      el.textContent = value;
    }
  });
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

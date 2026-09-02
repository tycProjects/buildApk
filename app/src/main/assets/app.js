/* nhzterm reference UI (§10)
 *
 * One consumer of nhzterm-api — not the daemon's owner. It speaks the same
 * JSON messages an external client would, just over the JavascriptInterface
 * bridge instead of a LocalSocket (§3).
 */
(function () {
  'use strict';

  var pending = {};        // request id -> callback
  var reqId = 0;
  var term = null;
  var sessionId = null;
  var ready = false;

  var prefs = {
    theme: 'Dracula',
    font: 'JetBrains Mono',
    size: 13,
    keys: true
  };

  // ---- transport ---------------------------------------------------------

  function send(obj) {
    if (!window.NhzBridge) return;
    window.NhzBridge.send(JSON.stringify(obj));
  }

  function request(method, params, cb) {
    var id = 'r' + (++reqId);
    if (cb) pending[id] = cb;
    send({ type: 'request', id: id, method: method, params: params || {} });
  }

  /* Native -> JS entry point. Named on window because the bridge calls it
   * by name from evaluateJavascript. */
  window.__nhzterm_recv = function (raw) {
    var msg;
    try { msg = JSON.parse(raw); } catch (e) { return; }

    if (msg.type === 'hello_ack') {
      if (msg.accepted) { ready = true; createSession(); }
      else if (term) term.write('\r\n\x1b[31mhandshake rejected: ' + msg.reason + '\x1b[0m\r\n');
      return;
    }
    if (msg.type === 'output') {
      if (term && msg.data) term.write(msg.data);
      return;
    }
    if (msg.type === 'session_status_changed') {
      if (msg.status === 'finished' && term) {
        term.write('\r\n\x1b[90m[session finished]\x1b[0m\r\n');
      }
      return;
    }
    if (msg.type === 'response') {
      var cb = pending[msg.id];
      if (cb) { delete pending[msg.id]; cb(null, msg.result); }
      return;
    }
    if (msg.type === 'error') {
      var ecb = pending[msg.id];
      if (ecb) { delete pending[msg.id]; ecb(msg, null); }
      else if (term) term.write('\r\n\x1b[31m' + msg.code + ': ' + msg.message + '\x1b[0m\r\n');
      return;
    }
  };

  // ---- terminal ----------------------------------------------------------

  function fit() {
    if (!term) return;
    var el = document.getElementById('term');
    // Measure a real glyph rather than guessing: font fallback on Android
    // means the actual advance width is rarely what we asked for.
    var probe = document.createElement('span');
    probe.style.cssText = 'position:absolute;visibility:hidden;white-space:pre;' +
      'font-family:' + JSON.stringify(prefs.font) + ',monospace;font-size:' + prefs.size + 'px';
    probe.textContent = '0'.repeat(100);
    document.body.appendChild(probe);
    var cw = probe.getBoundingClientRect().width / 100;
    document.body.removeChild(probe);
    if (!cw || cw < 1) cw = prefs.size * 0.6;

    var ch = Math.ceil(prefs.size * 1.35);
    var cols = Math.max(20, Math.floor(el.clientWidth / cw));
    var rows = Math.max(5, Math.floor(el.clientHeight / ch));

    if (cols !== term.cols || rows !== term.rows) {
      term.resize(cols, rows);
      if (sessionId) request('session.resize', { session_id: sessionId, cols: cols, rows: rows });
    }
  }

  function initTerm() {
    term = new Terminal({
      cursorBlink: true,
      fontFamily: JSON.stringify(prefs.font) + ', monospace',
      fontSize: prefs.size,
      theme: window.NHZ_THEMES[prefs.theme],
      scrollback: 5000,          // matches the daemon's ring buffer (§8)
      allowProposedApi: true,
      convertEol: false
    });
    term.open(document.getElementById('term'));

    term.onData(function (d) {
      if (sessionId) request('session.write', { session_id: sessionId, data: d });
    });

    window.addEventListener('resize', fit);
    setTimeout(fit, 50);
  }

  function createSession() {
    request('session.create', { shell: 'nhzsh', cols: term.cols, rows: term.rows },
      function (err, res) {
        if (err) { term.write('\r\n\x1b[31m' + err.code + '\x1b[0m\r\n'); return; }
        sessionId = res.session_id;
        request('session.attach', { session_id: sessionId }, function (e2, r2) {
          if (r2 && r2.scrollback) term.write(r2.scrollback);
          fit();
        });
      });
  }

  // ---- extra keys (§10.6) + modifier latching ----------------------------

  var ctrlLatched = false, altLatched = false;

  function keySeq(k) {
    switch (k) {
      case 'ESC': return '\x1b';
      case 'TAB': return '\t';
      case 'HOME': return '\x1b[H';
      case 'END': return '\x1b[F';
      case 'UP': return '\x1b[A';
      case 'DOWN': return '\x1b[B';
      case 'LEFT': return '\x1b[D';
      case 'RIGHT': return '\x1b[C';
      case 'PGUP': return '\x1b[5~';
      case 'PGDN': return '\x1b[6~';
      case 'SLASH': return '/';
      case 'DASH': return '-';
    }
    return '';
  }

  function write(data) {
    if (!sessionId) return;
    request('session.write', { session_id: sessionId, data: data });
  }

  function setupKeys() {
    document.querySelectorAll('.key').forEach(function (el) {
      el.addEventListener('click', function (ev) {
        ev.preventDefault();
        var k = el.getAttribute('data-k');
        if (k === 'CTRL') {
          ctrlLatched = !ctrlLatched;
          el.classList.toggle('latched', ctrlLatched);
          return;
        }
        if (k === 'ALT') {
          altLatched = !altLatched;
          el.classList.toggle('latched', altLatched);
          return;
        }
        write(keySeq(k));
      });
    });

    // A latched CTRL/ALT applies to the NEXT typed character, which is the
    // only workable model on a touch keyboard with no real modifier keys.
    if (term) {
      term.attachCustomKeyEventHandler(function (e) {
        if (e.type !== 'keydown') return true;
        if (!ctrlLatched && !altLatched) return true;
        if (e.key.length !== 1) return true;

        var out;
        if (ctrlLatched) {
          var c = e.key.toUpperCase().charCodeAt(0);
          out = (c >= 64 && c < 128) ? String.fromCharCode(c - 64) : e.key;
        } else {
          out = '\x1b' + e.key;
        }
        write(out);
        ctrlLatched = altLatched = false;
        document.querySelectorAll('.key.latched').forEach(function (n) {
          n.classList.remove('latched');
        });
        return false;
      });
    }
  }

  // ---- volume key emulation (§10.7) --------------------------------------

  var VOLUP_MAP = {
    e: '\x1b', t: '\t', w: '\x1b[A', s: '\x1b[B', a: '\x1b[D', d: '\x1b[C',
    l: '|', h: '~', u: '_', p: '\x1b[5~', n: '\x1b[6~',
    b: '\x1bb', f: '\x1bf', '.': '\x1c'
  };

  /* Called from native when a volume key is chorded with a letter. */
  window.__nhzterm_volkey = function (mod, key) {
    key = (key || '').toLowerCase();
    if (mod === 'down') {                        // Vol Down = Ctrl
      var c = key.toUpperCase().charCodeAt(0);
      if (c >= 64 && c < 128) write(String.fromCharCode(c - 64));
      return;
    }
    if (key === 'q' || key === 'k') { toggleKeys(); return; }
    if (key >= '1' && key <= '9') { write('\x1b[' + (10 + parseInt(key, 10)) + '~'); return; }
    if (key === '0') { write('\x1b[21~'); return; }
    if (VOLUP_MAP[key]) write(VOLUP_MAP[key]);
  };

  function toggleKeys() {
    prefs.keys = !prefs.keys;
    document.body.classList.toggle('keys-visible', prefs.keys);
    savePrefs();
    setTimeout(fit, 60);
  }

  // ---- long-press context menu (§10.3) -----------------------------------

  var pressTimer = null, lastTouch = { x: 0, y: 0 };

  function selectedText() {
    return term && term.hasSelection() ? term.getSelection() : '';
  }

  function looksLikeUrl(s) {
    return /^(https?:\/\/|www\.)\S+$/i.test((s || '').trim());
  }

  function showMenu(x, y) {
    var m = document.getElementById('menu');
    var sel = selectedText();
    // "Open" is conditional — only meaningful when a link is highlighted (§10.3).
    document.getElementById('mi-open').style.display = looksLikeUrl(sel) ? 'block' : 'none';
    m.style.left = Math.min(x, window.innerWidth - 200) + 'px';
    m.style.top = Math.min(y, window.innerHeight - 200) + 'px';
    m.style.display = 'block';
    document.getElementById('overlay').classList.add('on');
  }

  function hideMenus() {
    document.getElementById('menu').style.display = 'none';
    document.getElementById('submenu').style.display = 'none';
    document.getElementById('overlay').classList.remove('on');
  }

  function setupMenu() {
    var host = document.getElementById('term');

    host.addEventListener('touchstart', function (e) {
      if (!e.touches.length) return;
      lastTouch = { x: e.touches[0].clientX, y: e.touches[0].clientY };
      pressTimer = setTimeout(function () { showMenu(lastTouch.x, lastTouch.y); }, 500);
    }, { passive: true });

    ['touchend', 'touchmove', 'touchcancel'].forEach(function (ev) {
      host.addEventListener(ev, function () { clearTimeout(pressTimer); }, { passive: true });
    });

    host.addEventListener('contextmenu', function (e) {
      e.preventDefault();
      showMenu(e.clientX, e.clientY);
    });

    document.getElementById('overlay').addEventListener('click', hideMenus);

    document.getElementById('menu').addEventListener('click', function (e) {
      var a = e.target.getAttribute('data-a');
      if (!a) return;
      if (a === 'more') {
        var sm = document.getElementById('submenu');
        var sel = selectedText();
        document.getElementById('mi-openurl').style.display = looksLikeUrl(sel) ? 'block' : 'none';
        sm.style.left = document.getElementById('menu').style.left;
        sm.style.top = document.getElementById('menu').style.top;
        sm.style.display = 'block';
        document.getElementById('menu').style.display = 'none';
        return;
      }
      doAction(a);
      hideMenus();
    });

    document.getElementById('submenu').addEventListener('click', function (e) {
      var a = e.target.getAttribute('data-a');
      if (!a) return;
      doAction(a);
      hideMenus();
    });
  }

  function doAction(a) {
    var sel = selectedText();
    switch (a) {
      case 'copy':
        if (window.NhzUi) window.NhzUi.copy(sel);
        break;
      case 'paste':
        if (window.NhzUi) write(window.NhzUi.paste());
        break;
      case 'open':
      case 'openurl':
        if (window.NhzUi && looksLikeUrl(sel)) window.NhzUi.openUrl(sel.trim());
        break;
      case 'share':
        if (window.NhzUi) window.NhzUi.share(sel);
        break;
      case 'refresh':
        // Recovers a frozen VIEW without killing the session (§10.3): re-request
        // scrollback and repaint from the daemon's authoritative state.
        if (sessionId) {
          term.clear();
          request('session.attach', { session_id: sessionId }, function (e, r) {
            if (r && r.scrollback) term.write(r.scrollback);
          });
        }
        break;
      case 'kill':
        // §9 — targets the tracked FOREGROUND pid, never the shell.
        if (sessionId) {
          request('process.kill', { session_id: sessionId }, function (err) {
            if (err && window.NhzUi) window.NhzUi.toast('Nothing running');
          });
        }
        break;
      case 'style':   document.getElementById('styler').classList.add('on'); break;
      case 'screenon': if (window.NhzUi) window.NhzUi.keepScreenOn(); break;
      case 'help':    if (window.NhzUi) window.NhzUi.help(); break;
      case 'settings': if (window.NhzUi) window.NhzUi.settings(); break;
      case 'report':  if (window.NhzUi) window.NhzUi.report(); break;
    }
  }

  // ---- style picker (§11) -------------------------------------------------

  function savePrefs() {
    try { localStorage.setItem('nhzterm.prefs', JSON.stringify(prefs)); } catch (e) {}
  }

  function loadPrefs() {
    try {
      var raw = localStorage.getItem('nhzterm.prefs');
      if (raw) prefs = Object.assign(prefs, JSON.parse(raw));
    } catch (e) {}
  }

  function buildStyler() {
    var tl = document.getElementById('theme-list');
    Object.keys(window.NHZ_THEMES).forEach(function (name) {
      var d = document.createElement('div');
      d.className = 'opt' + (name === prefs.theme ? ' sel' : '');
      d.textContent = name;
      d.onclick = function () {
        prefs.theme = name;
        term.options.theme = window.NHZ_THEMES[name];
        savePrefs();
        tl.querySelectorAll('.opt').forEach(function (o) { o.classList.remove('sel'); });
        d.classList.add('sel');
      };
      tl.appendChild(d);
    });

    var fl = document.getElementById('font-list');
    window.NHZ_FONTS.forEach(function (f) {
      var d = document.createElement('div');
      d.className = 'opt' + (f === prefs.font ? ' sel' : '');
      d.textContent = f;
      d.style.fontFamily = JSON.stringify(f) + ', monospace';
      d.onclick = function () {
        prefs.font = f;
        term.options.fontFamily = JSON.stringify(f) + ', monospace';
        savePrefs(); fit();
        fl.querySelectorAll('.opt').forEach(function (o) { o.classList.remove('sel'); });
        d.classList.add('sel');
      };
      fl.appendChild(d);
    });

    var sl = document.getElementById('size-list');
    [10, 11, 12, 13, 14, 16, 18, 20].forEach(function (sz) {
      var d = document.createElement('div');
      d.className = 'opt' + (sz === prefs.size ? ' sel' : '');
      d.textContent = sz + ' px';
      d.onclick = function () {
        prefs.size = sz;
        term.options.fontSize = sz;
        savePrefs(); fit();
        sl.querySelectorAll('.opt').forEach(function (o) { o.classList.remove('sel'); });
        d.classList.add('sel');
      };
      sl.appendChild(d);
    });

    document.getElementById('styler-close').onclick = function () {
      document.getElementById('styler').classList.remove('on');
    };
  }

  // ---- boot ---------------------------------------------------------------

  function boot() {
    loadPrefs();
    document.body.classList.toggle('keys-visible', prefs.keys);
    initTerm();
    setupKeys();
    setupMenu();
    buildStyler();

    // Handshake first — nothing else is permitted before it (§6.2).
    send({
      type: 'hello',
      protocol_version: 1,
      token: window.NhzBridge ? window.NhzBridge.token() : ''
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();

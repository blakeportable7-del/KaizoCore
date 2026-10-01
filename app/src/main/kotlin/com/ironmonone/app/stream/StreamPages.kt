package com.ironmonone.app.stream

import java.net.URLEncoder

/**
 * The three pages the stream server writes itself: the setup guide at /, the game
 * page at /game and the attempt counter at /attempts.html. (The tracker page lives
 * in assets/stream/tracker.html, loaded by the Play screen.)
 *
 * They are strings, not assets, because the hub starts with only a directory in
 * hand, no Context, and a page in the APK would need one. The scripts avoid the
 * dollar sign and use single quotes, so a Kotlin raw string holds them untouched.
 *
 * Copy: plain, dry, no em dashes. Added 2026-09-29 for the stream kit; the pages say the kit
 * works for any game, and only a run has an attempt counter.
 *
 * 2026-09-30 (UX audit P0-16 to P0-19): the guide got a test of the picture, two ways into OBS (scenes
 * already made, or import), what to expect on screen, the sound advice, and a cure for a changed address
 * that keeps the streamer's layout. The game page no longer writes words over a picture that has been
 * on air. The attempt counter has a demo (?demo=1), as the tracker has.
 */
object StreamPages {

    /**
     * The setup guide. [base] is the address the visitor used, like http://192.168.1.50:8642. Opened on the
     * phone itself (see [onPhone]) it says so and leaves out the downloads: a scene made there would point
     * at the phone.
     */
    fun setup(base: String, token: String): String {
        val k = "k=" + URLEncoder.encode(token, "UTF-8")
        val urls = ObsScene.urls(base, token)
        val here = onPhone(base)
        return SETUP
            .replace("%PHONE_NOTE%", if (here) PHONE_NOTE else "")
            .replace("%DOWNLOADS%", if (here) "" else DOWNLOADS)
            .replace("%SCENE%", "/obs-scene.json?$k")
            .replace("%SCENE_TOP%", "/obs-scene.json?$k&top=1")
            .replace("%FILE%", ObsScene.FILE)
            .replace("%FILE_TOP%", ObsScene.FILE_TOP)
            .replace("%TEST%", esc("$base/game?$k&debug=1"))
            .replace("%GAME%", esc(urls.getValue(ObsScene.GAME)))
            .replace("%TRACKER%", esc(urls.getValue(ObsScene.TRACKER)))
            .replace("%ATTEMPTS%", esc(urls.getValue(ObsScene.ATTEMPTS)))
            .replace("%STATE%", esc("$base/state.json?$k"))
            .replace("%COUNT%", esc("$base/attempts?$k"))
            .replace("%GAME_W%", ObsScene.GAME_W.toString())
            .replace("%GAME_H%", ObsScene.CANVAS_H.toString())
            .replace("%TRACKER_W%", ObsScene.TRACKER_W.toString())
            .replace("%TRACKER_H%", ObsScene.TRACKER_H.toString())
            .replace("%ATTEMPTS_H%", ObsScene.ATTEMPTS_H.toString())
    }

    fun game(): String = GAME

    fun attempts(): String = ATTEMPTS

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /**
     * True when the guide was opened at the phone itself: localhost, 127.x.x.x, [::1] or 0.0.0.0. The scene and
     * the addresses it would write then name the phone, not the PC that runs OBS. [base] is "http://host" or
     * "http://host:port", as the server builds it.
     */
    internal fun onPhone(base: String): Boolean {
        val authority = base.substringAfter("://").substringBefore('/')
        val host = (if (authority.startsWith("[")) authority.substringBefore(']') + "]" else authority.substringBefore(':')).lowercase()
        return host == "localhost" || host == "[::1]" || host == "0.0.0.0" || LOOPBACK.matches(host)
    }

    private val LOOPBACK = Regex("127(\\.[0-9]{1,3}){3}")

    private val PHONE_NOTE = """<div class="note warn"><b>Open this page on your PC, not on the phone.</b> A scene made here would point back at the phone.</div>"""

    /** The two scene downloads, named as the browser will save them. Left out when the guide is open on the phone. */
    private val DOWNLOADS = """<p><a class="btn" href="%SCENE%" download="%FILE%">Download the OBS scene</a></p>
<p class="small">Saves as <b>%FILE%</b> in your Downloads folder. You only need it if you are new to OBS.</p>
<p class="small">Playing a DS game and want only the top screen? <a href="%SCENE_TOP%" download="%FILE_TOP%">Download the top screen version</a> instead. It saves as <b>%FILE_TOP%</b>.</p>
"""

    private val SETUP = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>KaizoCore stream kit</title>
<style>
:root{--bg:#0b0b0b;--panel:#161616;--line:#3a3a3a;--ink:#e8e8e8;--dim:#9a9a9a;--gold:#ffd54a;--blue:#7fb8ff}
html,body{margin:0;background:var(--bg);color:var(--ink)}
body{font:16px/1.5 "Segoe UI",Roboto,Arial,sans-serif;padding:24px 16px 64px}
main{max-width:760px;margin:0 auto}
h1{font-size:28px;margin:0 0 8px}
h2{font-size:20px;margin:36px 0 8px;display:flex;align-items:center;gap:10px}
h2 .n{display:inline-flex;width:30px;height:30px;align-items:center;justify-content:center;border:2px solid var(--gold);color:var(--gold);font-size:16px;border-radius:2px}
p,li{color:var(--ink)}
.lead{color:var(--dim);margin:0}
.small{color:var(--dim);font-size:14px}
a{color:var(--blue)}
.btn{display:inline-block;padding:12px 20px;background:var(--gold);color:#111;font-weight:700;text-decoration:none;border-radius:2px}
.btn.alt{padding:10px 18px;background:transparent;color:var(--gold);border:2px solid var(--gold)}
ol,ul{padding-left:22px}
li{margin:6px 0}
table{width:100%;border-collapse:collapse;margin:8px 0 4px;font-size:14px}
th{color:var(--dim);font-weight:normal;text-align:left;border-bottom:1px solid var(--line);padding:4px 8px 4px 0}
td{border-bottom:1px solid #222;padding:8px 8px 8px 0;vertical-align:top}
.addr{display:flex;gap:6px}
.addr input{flex:1;min-width:0;box-sizing:border-box;background:#111;border:1px solid var(--line);color:var(--ink);padding:6px;font:13px/1.2 Consolas,monospace}
.addr button{background:#222;border:1px solid var(--line);color:var(--ink);padding:0 12px;cursor:pointer;font:inherit;font-size:13px}
.note{border:1px solid var(--line);background:var(--panel);padding:10px 14px;margin:20px 0;border-radius:2px}
.note.warn{border-color:var(--gold)}
code{font:13px Consolas,monospace;color:var(--gold)}
</style></head><body><main>
<h1>KaizoCore stream kit</h1>
<p class="lead">The game's picture and sound go from this phone to OBS over your Wi-Fi. No cable, no screen mirroring, no capture card.</p>
<p class="small">It works for any game you play in KaizoCore: Kaizo IronMON, Nuzlocke, ROM hacks and games from your library, on GBA, Game Boy and DS.</p>

%PHONE_NOTE%
<h2><span class="n">1</span> Download the OBS scene</h2>
%DOWNLOADS%<p class="small">Start a game on the phone and turn STREAM on first, then test the picture.</p>
<p><a class="btn alt" href="%TEST%">Open the picture in this tab</a></p>
<p>You should see your game. Click the page once to turn the sound on. If you do, the phone side works and any problem left is in OBS.</p>

<h2><span class="n">2</span> Add it to OBS</h2>
<p><b>Already use OBS scenes?</b> Do not import. In your gameplay scene choose <b>Sources</b>, <b>+</b>, <b>Browser</b>, and add the three addresses from the table below, one source each. On the game source tick <b>Control audio via OBS</b> and <b>Use custom frame rate</b>, 60.</p>
<p><b>New to OBS?</b> Download the scene in step 1, then:</p>
<ol>
<li>In OBS, open the <b>Scene Collection</b> menu and choose <b>Import Scene Collection</b>.</li>
<li>Click <b>Browse...</b>, pick the file from your Downloads folder, then click <b>Import</b>.</li>
<li>Open the <b>Scene Collection</b> menu again and choose <b>KaizoCore stream</b>.</li>
<li>Add your own webcam and mic to it.</li>
</ol>

<h2><span class="n">3</span> What you should see</h2>
<p>The game on the left, the tracker box on the right, and <b>KaizoCore game</b> moving in the Audio Mixer. Viewers see only these: never the phone's buttons, menus or camera.</p>
<p class="small">The tracker box shows the game's tracker where KaizoCore has one, and a line saying so where it does not. The scene puts the attempt counter under the tracker. The attempt counter shows in a Kaizo IronMON run and in a Nuzlocke on a randomized game. In a standard Nuzlocke, a ROM hack or any other game it stays blank: there is no attempt number. The scene is laid out for a 1920 x 1080 canvas, which is OBS's default. On another size, drag the three sources where you want them.</p>

<h2>While you stream</h2>
<ul>
<li><b>Keep KaizoCore in front.</b> A home swipe, the power button, a call or a permission prompt pauses the game, and the picture in OBS freezes. While the stream is on, a notification takes you straight back.</li>
<li>Plug the phone in: streaming uses power and warms the phone.</li>
<li>Turn on Do Not Disturb.</li>
</ul>

<h2>Sound</h2>
<ul>
<li>Your mic can hear the phone's speaker, and then viewers hear the game twice, the second time late. Wear headphones on the phone, or mute the phone. The phone's mute does not touch the stream's sound.</li>
<li>To hear the game in your PC headphones: Advanced Audio Properties, Audio Monitoring, Monitor and Output for KaizoCore game. It will run a little behind the phone. OBS 32.2 and newer call that choice Monitoring Enabled.</li>
<li><b>Fast forward:</b> the phone goes quiet, but the stream keeps sending sound, sped up. Mute KaizoCore game in OBS, or stay at 1x on air.</li>
</ul>

<h2>If nothing shows</h2>
<ul>
<li><b>An error box in OBS</b> means the phone was not streaming when OBS started. Turn Stream on, then switch to another scene and back. Still an error? Right-click the source, choose Properties, press <b>Refresh cache of current page</b>.</li>
<li><b>Same Wi-Fi.</b> The phone and the PC have to be on the same network. A guest network usually blocks it, and so does a VPN on either one.</li>
<li><b>A game has to be running.</b> Open a game on the phone and check that the menu says STREAM ON. The picture waits for the game.</li>
<li><b>Address changed?</b> Open this page again at the new address. In OBS, right-click each KaizoCore source, choose Properties and paste its new address from the table below: game, tracker, attempts. You do not need to import again. To stop it happening, give the phone a fixed address in your router's settings (often called an address reservation).</li>
<li><b>Picture but no sound.</b> In OBS, right-click <b>KaizoCore game</b>, choose Properties and check that <b>Control audio via OBS</b> is ticked. Then check that it is not muted in the Audio Mixer.</li>
<li><b>Choppy picture or sound.</b> Use 5 GHz Wi-Fi and keep the phone near the router. A PC on a cable helps too.</li>
<li><b>Still nothing?</b> Add <code>&amp;debug=1</code> to the game address. A small box in the corner shows the picture size, pictures a second and the sound state. Take it off before you go live.</li>
<li>Every address here only works while STREAM is on in the app.</li>
</ul>

<h2>The links, one at a time</h2>
<p>Each of these is one <b>Browser</b> source in OBS. Use <b>Copy</b>, then paste it into the source's URL box.</p>
<table>
<tr><th>Source</th><th>Address</th><th>Size</th></tr>
<tr><td>Game, picture and sound</td><td><div class="addr"><input readonly value="%GAME%"><button type="button">Copy</button></div></td><td>%GAME_W% x %GAME_H%. Tick <b>Control audio via OBS</b> and <b>Use custom frame rate</b>, 60.</td></tr>
<tr><td>Tracker</td><td><div class="addr"><input readonly value="%TRACKER%"><button type="button">Copy</button></div></td><td>%TRACKER_W% x %TRACKER_H%</td></tr>
<tr><td>Attempt counter</td><td><div class="addr"><input readonly value="%ATTEMPTS%"><button type="button">Copy</button></div></td><td>%TRACKER_W% x %ATTEMPTS_H%</td></tr>
</table>
<p class="small"><b>Placing the boxes before a game runs.</b> Add <code>&amp;demo=battle</code> to the tracker address and <code>&amp;demo=1</code> to the attempt address. Take them off before you go live.</p>
<p class="small">The game address ends in <code>&amp;int=1</code>, which keeps every pixel the same size. That is the sharpest picture, but it leaves a border, most on a DS with both screens showing. Take it off to fill the box. You can also add these to the end of the game address: <code>&amp;top=1</code> shows only the top DS screen, <code>&amp;smooth=1</code> softens the picture instead of keeping hard pixel edges, <code>&amp;audio=0</code> leaves the sound off.</p>
<p class="small">For your own overlays: <a href="%STATE%">the tracker's raw data</a> and <a href="%COUNT%">the attempt number as plain text</a>. After a Kaizo IronMON run ends, the tracker box's Randomizer data tab shows every species as randomized.</p>
<div class="note">The <code>k=</code> part of each address is a password for these pages. Keep the addresses off your stream.</div>
<script>
var rows = document.querySelectorAll('.addr');
for (var i = 0; i < rows.length; i++) {
  (function (row) {
    var input = row.querySelector('input'), btn = row.querySelector('button');
    input.onclick = function () { input.select(); };
    btn.onclick = function () {
      input.select();
      var done = function () { btn.textContent = 'Copied'; setTimeout(function () { btn.textContent = 'Copy'; }, 1500); };
      if (navigator.clipboard && window.isSecureContext) { navigator.clipboard.writeText(input.value).then(done, function () { document.execCommand('copy'); done(); }); }
      else { try { document.execCommand('copy'); } catch (e) { } done(); }
    };
  })(rows[i]);
}
</script>
</main></body></html>
"""

    private val ATTEMPTS = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>KaizoCore attempts</title>
<style>
:root{--w:420px}
html,body{margin:0;background:transparent}
body{font:15px/1.25 "Segoe UI",Roboto,Arial,sans-serif;color:#e8e8e8}
#box{box-sizing:border-box;width:var(--w);background:#0b0b0b;border:2px solid #3a3a3a;padding:10px 16px;display:flex;justify-content:space-between;align-items:baseline;visibility:hidden}
#box.on{visibility:visible}
#label{color:#9a9a9a;letter-spacing:2px;font-size:15px}
#n{color:#ffd54a;font-size:52px;font-weight:700;font-variant-numeric:tabular-nums;line-height:1.1}
</style></head><body>
<div id="box"><span id="label">ATTEMPT</span><span id="n">-</span></div>
<script>
(function () {
  var q = new URLSearchParams(location.search);
  var k = q.get('k') || '';
  if (q.get('w')) document.documentElement.style.setProperty('--w', q.get('w') + 'px');
  if (q.get('label')) document.getElementById('label').textContent = q.get('label');
  var n = document.getElementById('n');
  var box = document.getElementById('box');
  // Only a run has attempts (Kaizo IronMON, and a Nuzlocke on a randomized game). In any other game the
  // box stays invisible: the last run's number would be a wrong one.
  function show(v, counted) {
    var t = String(v == null ? '' : v).trim();
    if (counted && /^[0-9]+/.test(t)) { n.textContent = t; box.className = 'on'; }
    else { box.className = ''; }
  }
  function poll() {
    fetch('/attempts?k=' + encodeURIComponent(k)).then(function (r) { return r.text(); }).then(function (t) { show(t, t.trim() !== ''); }, function () { });
  }
  function live() {
    var es = new EventSource('/events?k=' + encodeURIComponent(k));
    es.addEventListener('state', function (e) {
      try { var s = JSON.parse(e.data); show(s ? s.attempt : null, !!(s && s.run === true)); } catch (x) { }
    });
    es.onerror = function () { es.close(); setTimeout(live, 2000); };
  }
  // ?demo=1 shows a sample number and asks the phone for nothing, so the box can be placed while no run is on
  // (2026-09-30, UX audit). It is the 812 the tracker's own demo shows.
  if (q.get('demo')) { show(812, true); return; }
  poll();
  setInterval(poll, 5000);
  live();
})();
</script></body></html>
"""

    private val GAME = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>KaizoCore game</title>
<style>
html,body{margin:0;height:100%;overflow:hidden;background:transparent}
body{font:14px/1.35 "Segoe UI",Roboto,Arial,sans-serif;color:#e8e8e8}
#c{position:absolute;left:0;top:0;width:0;height:0;image-rendering:crisp-edges;image-rendering:pixelated}
.note{position:absolute;left:12px;box-sizing:border-box;max-width:80%;padding:8px 12px;background:#0b0b0b;border:2px solid #3a3a3a;border-radius:2px;display:none}
@keyframes wait-in{from{opacity:0}to{opacity:1}}
/* The help text and the mark come in after a pause. OBS loads this page again each time its scene comes up, and a
   page that connects quickly must not flash words over a live stream. The animation starts afresh each time they show. */
#msg{bottom:12px;animation:wait-in .3s ease-out 1.2s both}
#mark{position:absolute;left:10px;bottom:10px;width:12px;height:12px;border-radius:50%;background:#ffd54a;box-shadow:0 0 0 2px rgba(11,11,11,.8);display:none;animation:wait-in .3s ease-out 2s both}
#tap{top:12px;cursor:pointer;border-color:#ffd54a;color:#ffd54a}
#dbg{position:absolute;right:8px;top:8px;padding:4px 8px;background:rgba(11,11,11,.85);border:1px solid #3a3a3a;font:12px/1.3 Consolas,monospace;white-space:pre;display:none}
</style></head><body>
<canvas id="c"></canvas>
<div id="msg" class="note"></div>
<div id="mark"></div>
<div id="tap" class="note">Click here for sound. Your browser holds it back until you do.</div>
<div id="dbg"></div>
<script>
(function () {
  'use strict';
  var q = new URLSearchParams(location.search);
  var K = q.get('k') || '';
  var TOP = q.get('top') === '1';          // DS: only the top screen
  var SOUND = q.get('audio') !== '0';
  var WHOLE = q.get('int') === '1';        // whole-number scale only: every pixel the same size
  var SMOOTH = q.get('smooth') === '1';
  var DEBUG = q.get('debug') === '1';
  var CUSHION = 0.12;                      // seconds of sound held back so Wi-Fi jitter does not become crackle

  var cv = document.getElementById('c');
  var g = cv.getContext('2d');
  var msg = document.getElementById('msg');
  var mark = document.getElementById('mark');
  var tap = document.getElementById('tap');
  var dbg = document.getElementById('dbg');
  if (SMOOTH) cv.style.imageRendering = 'auto';

  var ws = null, tries = 0;
  var everPicture = false;                 // a picture has been on this source: from then on it never shows words
  var troubled = false;
  var IN_OBS = typeof window.obsstudio !== 'undefined';   // OBS's own browser source, which plays sound with no click
  var sw = 0, sh = 0;                      // size of what is on the canvas
  var busy = false, waiting = null, seqIn = 0, seqShown = 0;
  var actx = null, nextAt = 0, pos = 0, tailL = 0, tailR = 0, haveTail = false, lastSoundAt = 0;
  var stat = { pics: 0, bytes: 0, dropped: 0, at: performance.now(), fps: 0, kbps: 0 };

  function say(t) { msg.textContent = t; msg.style.display = t ? 'block' : 'none'; }
  // Words are for setting up. Once a picture has been on the source it is inside a live stream, so a lost
  // connection is only this small mark, until the next picture arrives (2026-09-30, UX audit).
  function trouble(on) { if (on === troubled) return; troubled = on; mark.style.display = on ? 'block' : 'none'; }

  // ---- the socket: it comes back by itself when Wi-Fi drops or the phone restarts.
  function connect() {
    var url = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/game.ws?k=' + encodeURIComponent(K);
    var s = new WebSocket(url);
    s.binaryType = 'arraybuffer';
    ws = s;
    s.onopen = function () { tries = 0; if (!everPicture) say('Connected. Waiting for the game on the phone.'); };
    s.onmessage = function (ev) {
      if (typeof ev.data === 'string') return;             // the hello: nothing to do
      var b = ev.data;
      if (b.byteLength < 8) return;
      stat.bytes += b.byteLength;
      var tag = new Uint8Array(b, 0, 1)[0];
      if (tag === 1) onPicture(b); else if (tag === 2) onSound(b);
    };
    s.onclose = function () {
      if (ws !== s) return;
      ws = null; tries++;
      if (everPicture) trouble(true);
      else say(tries < 4 ? 'Lost the phone. Trying again.' : 'Cannot reach the phone. Is the stream on, and is this the address the app shows?');
      setTimeout(connect, Math.min(3000, 300 + tries * 400));
    };
    s.onerror = function () { try { s.close(); } catch (e) { } };
  }

  // ---- pictures: PNG, decoded one at a time. A newer picture replaces one still waiting.
  function delayMs() {
    // Sound is held back CUSHION seconds; the picture waits the same, so they stay together.
    if (!SOUND || !actx || actx.state !== 'running' || performance.now() - lastSoundAt > 600) return 0;
    return Math.min(400, CUSHION * 1000 + (actx.outputLatency || actx.baseLatency || 0) * 1000);
  }
  function onPicture(buf) {
    trouble(false);                                         // a picture came in on a live socket: the mark can go
    var due = performance.now() + delayMs();
    if (busy) { if (waiting) stat.dropped++; waiting = { buf: buf, due: due }; return; }
    decode(buf, due);
  }
  function decode(buf, due) {
    busy = true;
    var mine = ++seqIn;
    var blob = new Blob([new Uint8Array(buf, 8)], { type: 'image/png' });
    var done = function (img) {
      busy = false;
      if (waiting) { var w = waiting; waiting = null; decode(w.buf, w.due); }
      if (!img) return;
      if (due - performance.now() <= 3 && !queue.length) show(img, mine); else hold(img, due, mine);
    };
    if (window.createImageBitmap) {
      createImageBitmap(blob).then(done, function () { done(null); });
    } else {
      var u = URL.createObjectURL(blob), im = new Image();
      im.onload = function () { URL.revokeObjectURL(u); done(im); };
      im.onerror = function () { URL.revokeObjectURL(u); done(null); };
      im.src = u;
    }
  }
  // Pictures wait here for their moment (the sound's cushion, about 140 ms, so eight or nine at 60 a
  // second). At most 30, half a second of them, are kept: a page whose timers are held back (a browser
  // source that is not on screen) must not pile up decoded pictures, and a bound below the cushion would
  // drop every picture before its turn.
  var queue = [], timer = 0;
  function hold(img, due, mine) {
    queue.push({ img: img, due: due, mine: mine });
    while (queue.length > 30) { var old = queue.shift(); if (old.img.close) old.img.close(); }
    if (!timer) arm();
  }
  function arm() {
    if (!queue.length) { timer = 0; return; }
    timer = setTimeout(release, Math.max(0, queue[0].due - performance.now()));
  }
  function release() {
    timer = 0;
    var now = performance.now(), newest = null;
    while (queue.length && queue[0].due <= now + 2) {
      if (newest && newest.img.close) newest.img.close();          // two are due: only the newer is worth drawing
      newest = queue.shift();
    }
    if (newest) show(newest.img, newest.mine);
    arm();
  }
  function show(img, mine) {
    if (mine < seqShown) { if (img.close) img.close(); return; }
    seqShown = mine;
    draw(img);
    if (img.close) img.close();
    stat.pics++;
  }
  function draw(img) {
    var cw = img.width, ch = img.height;
    if (TOP) {
      if (cw === 256 && ch >= 384) ch = 192;        // two screens stacked: the top one is the first 192 rows
      else if (ch === 192 && cw >= 512) cw = 256;   // side by side: the top one is on the left
    }
    if (cv.width !== cw || cv.height !== ch) { cv.width = cw; cv.height = ch; }
    g.imageSmoothingEnabled = false;
    g.drawImage(img, 0, 0, cw, ch, 0, 0, cw, ch);
    sw = cw; sh = ch;
    layout();
    if (!everPicture) { everPicture = true; say(''); }
  }
  function layout() {
    if (!sw) return;
    var W = window.innerWidth, H = window.innerHeight;
    var s = Math.min(W / sw, H / sh);
    if (WHOLE && s >= 1) s = Math.floor(s);
    var dw = Math.max(1, Math.round(sw * s)), dh = Math.max(1, Math.round(sh * s));
    cv.style.width = dw + 'px'; cv.style.height = dh + 'px';
    cv.style.left = Math.round((W - dw) / 2) + 'px'; cv.style.top = Math.round((H - dh) / 2) + 'px';
  }
  window.addEventListener('resize', layout);

  // ---- sound. Each message is a few milliseconds of 16-bit stereo. They are turned into
  // buffers at the sound card's own rate (a buffer at any other rate is resampled per
  // piece by the browser, and that clicks at every seam), joined edge to edge, and
  // played CUSHION seconds behind arrival. The speed of playback is nudged by up to 3
  // percent to keep that cushion steady against the phone's clock and Wi-Fi jitter.
  function soundInit() {
    if (actx || !SOUND) return;
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    try { actx = new AC({ latencyHint: 'interactive' }); } catch (e) { actx = null; return; }
    actx.onstatechange = showTap;
    showTap();
  }
  // Not inside OBS: there the sound needs no click, so the note could only ever be words on the stream.
  function showTap() { tap.style.display = (actx && actx.state === 'suspended' && !IN_OBS) ? 'block' : 'none'; }
  function wake() { if (actx && actx.state === 'suspended') actx.resume(); }
  tap.onclick = wake;
  document.addEventListener('pointerdown', wake);
  document.addEventListener('keydown', wake);

  function onSound(buf) {
    if (!SOUND) return;
    soundInit();
    if (!actx) return;
    var dv = new DataView(buf);
    var rate = dv.getUint32(4, true);
    var n = (buf.byteLength - 8) >> 2;
    if (n < 2 || rate < 3000 || rate > 768000) return;
    if (actx.state === 'suspended') actx.resume();
    lastSoundAt = performance.now();
    var pcm = new Int16Array(buf, 8, n * 2);

    var now = actx.currentTime;
    var ahead = nextAt - now;
    if (ahead < 0.005) { nextAt = now + CUSHION; ahead = CUSHION; haveTail = false; }   // ran dry: begin again with the cushion
    else if (ahead > 0.5) { haveTail = false; stat.dropped++; return; }                   // far ahead of real time: skip this piece
    if (!haveTail) { pos = 0; tailL = pcm[0]; tailR = pcm[1]; haveTail = true; }

    var steer = Math.max(-0.03, Math.min(0.03, (ahead - CUSHION) * 0.25));
    var step = (rate / actx.sampleRate) * (1 + steer);          // source frames used per output frame
    var count = Math.max(0, Math.ceil((n - 1 - pos) / step));
    if (count > 0) {
      var out = actx.createBuffer(2, count, actx.sampleRate);
      var L = out.getChannelData(0), R = out.getChannelData(1);
      var p = pos;
      for (var j = 0; j < count; j++) {
        var i = Math.floor(p), f = p - i;
        var l0, r0;
        if (i < 0) { l0 = tailL; r0 = tailR; } else { l0 = pcm[2 * i]; r0 = pcm[2 * i + 1]; }
        var i1 = i + 1;
        if (i1 > n - 1) i1 = n - 1;
        L[j] = (l0 + (pcm[2 * i1] - l0) * f) / 32768;
        R[j] = (r0 + (pcm[2 * i1 + 1] - r0) * f) / 32768;
        p += step;
      }
      var src = actx.createBufferSource();
      src.buffer = out;
      src.connect(actx.destination);
      src.start(nextAt);
      nextAt += count / actx.sampleRate;
      pos = p - n;
    } else {
      pos = pos - n;
    }
    tailL = pcm[2 * n - 2]; tailR = pcm[2 * n - 1];
  }

  if (DEBUG) {
    dbg.style.display = 'block';
    setInterval(function () {
      var t = performance.now(), dt = (t - stat.at) / 1000;
      stat.fps = stat.pics / dt; stat.kbps = stat.bytes * 8 / 1000 / dt;
      dbg.textContent = 'size ' + sw + 'x' + sh + '\n' + stat.fps.toFixed(1) + ' pictures/s\n' + stat.kbps.toFixed(0) + ' kbit/s\n' +
        'sound ' + (actx ? actx.state : 'off') + ', ahead ' + (actx ? Math.max(0, nextAt - actx.currentTime).toFixed(3) : '-') + ' s\n' +
        'dropped ' + stat.dropped;
      stat.pics = 0; stat.bytes = 0; stat.at = t;
    }, 1000);
  }
  connect();
})();
</script></body></html>
"""
}

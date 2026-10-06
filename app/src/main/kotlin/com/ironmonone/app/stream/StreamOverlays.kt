package com.ironmonone.app.stream

/**
 * Two browser-source pages the server writes itself (streamer list items 1 and 3, 2026-10-05), strings like
 * StreamPages' and for the same reason: the hub has no Context. Their scripts avoid the dollar sign and use single
 * quotes, so a Kotlin raw string holds them untouched.
 *
 *  - /gameover: see-through while the run goes on. When a Kaizo IronMON run ends (the game-over popup's latch, through
 *    StreamHub.gameOver and the `gameover` event) a card comes in: what ended the run, the attempt, badges, time
 *    played, the run's best so far, the line the popup shows, and the run's GachaMon card when there is one (its facts;
 *    the card itself is drawn on the phone by Compose and is not served). It goes when the next run goes in, when Retry
 *    undoes the loss, or after `&hold=` seconds. `&demo=1` shows a sample card. `&w=` sets the width.
 *  - /timer: the attempt's time played (StreamTimer says exactly what it counts) and a split a badge, with how far
 *    ahead or behind the best earlier run with splits on the same settings. See-through when no run is in Play.
 *    `&demo=1` shows a sample. `&splits=0` leaves the splits off. `&w=` sets the width.
 *
 * Both mark their box data-panel for the looks (StreamThemes). Copy: plain, dry, no em dashes.
 */
object StreamOverlays {

    fun gameOver(): String = GAME_OVER

    fun timer(): String = TIMER

    private val MON = Regex("/mon/([0-9]{1,4})\\.png")

    /** The species a /mon/25.png address asks for, or null when [path] is not one. */
    fun monRoute(path: String): Int? = MON.matchEntire(path)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

    private val GAME_OVER = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>KaizoCore game over</title>
<style>
:root{--w:900px;--bg:#0b0b0b;--panel:#161616;--line:#3a3a3a;--ink:#e8e8e8;--dim:#9a9a9a;--gold:#ffd54a;--red:#ff5d5d;--green:#69d36e;--blue:#7fb8ff}
html,body{margin:0;background:transparent;overflow:hidden}
body{font:16px/1.3 "Segoe UI",Roboto,Arial,sans-serif;color:var(--ink);padding:12px 0}
#card{--accent:var(--red);box-sizing:border-box;width:var(--w);max-width:100%;margin:0 auto;background:var(--bg);border:2px solid var(--line);border-top:4px solid var(--accent);padding:18px 22px;visibility:hidden;opacity:0}
#card.won{--accent:var(--green)}
#card.on{visibility:visible;opacity:1;animation:card-in .7s cubic-bezier(.2,.9,.25,1.12) both}
#card.off{visibility:visible;animation:card-out .45s ease-in both}
@keyframes card-in{0%{opacity:0;transform:translateY(48px) scale(.96)}100%{opacity:1;transform:none}}
@keyframes card-out{0%{opacity:1;transform:none}99%{opacity:0;transform:translateY(24px) scale(.98)}100%{opacity:0;visibility:hidden}}
#head{display:flex;justify-content:space-between;align-items:baseline;gap:12px}
#title{font-size:40px;font-weight:800;letter-spacing:6px;color:var(--accent)}
#attempt{color:var(--dim);font-size:16px;letter-spacing:2px}
#attempt b{color:var(--gold);font-size:30px;margin-left:6px}
.sweep{height:2px;background:linear-gradient(90deg,transparent,var(--accent),transparent);margin:6px 0 14px;transform-origin:left}
#card.on .sweep{animation:sweep .9s .2s ease-out both}
@keyframes sweep{from{transform:scaleX(0)}to{transform:scaleX(1)}}
#main{display:flex;gap:18px;align-items:center}
.pic{width:96px;height:96px;image-rendering:pixelated;flex:none;display:none}
.pic.ok{display:block}
#label{color:var(--dim);font-size:14px;letter-spacing:3px}
#cause{font-size:30px;font-weight:700}
#where{color:var(--dim);font-size:15px}
#quote{font-style:italic;color:var(--blue);margin-top:10px;font-size:16px}
#stats{display:flex;flex-wrap:wrap;gap:12px 32px;margin-top:14px;padding-top:12px;border-top:1px solid var(--line)}
.stat span{display:block;color:var(--dim);font-size:12px;letter-spacing:2px}
.stat b{font-size:26px;font-variant-numeric:tabular-nums}
#best{margin-top:8px;color:var(--dim);font-size:14px}
#best.new{color:var(--gold);font-weight:700;letter-spacing:2px}
#gacha{display:none;margin-top:14px;padding:12px 14px;border:1px solid var(--line);background:var(--panel);gap:14px;align-items:center}
#gacha.on{display:flex}
#card.on #gacha.on{animation:card-in .6s .45s both}
#gname{font-size:20px;font-weight:700}
.stars{display:flex;gap:3px;margin:3px 0}
.star{display:block;width:16px;height:16px;background:var(--gold);clip-path:polygon(50% 0,61% 35%,98% 35%,68% 57%,79% 91%,50% 70%,21% 91%,32% 57%,2% 35%,39% 35%)}
.star.off{background:#444}
.mini{color:var(--dim);font-size:13px}
</style></head><body>
<div id="card" data-panel>
<div id="head"><div id="title" class="kc-glow">RUN OVER</div><div id="attempt" class="kc-label"></div></div>
<div class="sweep"></div>
<div id="main"><img id="kpic" class="pic" alt=""><div><div id="label" class="kc-label"></div><div id="cause" class="kc-big"></div><div id="where"></div></div></div>
<div id="quote"></div>
<div id="stats" class="kc-rule"></div>
<div id="best"></div>
<div id="gacha"><img id="gpic" class="pic" alt=""><div><div id="gfrom" class="mini kc-label"></div><div id="gname"></div><div id="gstars" class="stars"></div><div id="gfacts" class="mini"></div><div id="gmoves" class="mini"></div></div></div>
</div>
<script>
(function () {
  var q = new URLSearchParams(location.search);
  var k = q.get('k') || '';
  if (q.get('w')) document.documentElement.style.setProperty('--w', q.get('w') + 'px');
  // Seconds the card stays up; 0 (the default) keeps it until the next run goes in.
  var HOLD = parseInt(q.get('hold') || '0', 10) || 0;
  var card = document.getElementById('card');
  // The card on screen (its run's key), whether it is coming in or going out, and a card whose &hold= ran out: that
  // one stays down for its run, a reconnect or a refresh of its data does not bring it back.
  var shownKey = null, phase = '', won = false, holdTimer = 0, dismissed = null;
  function el(id) { return document.getElementById(id); }
  function text(id, t) { el(id).textContent = t == null ? '' : String(t); }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function clock(s) {
    s = Math.max(0, Math.floor(s || 0));
    var h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
    var mm = h > 0 && m < 10 ? '0' + m : String(m);
    return (h > 0 ? h + ':' : '') + mm + ':' + (x < 10 ? '0' : '') + x;
  }
  // A Pokemon's picture from the phone, shown only once it has loaded and is more than the empty one.
  function pic(id, species) {
    var im = el(id);
    im.className = 'pic';
    if (species == null) return;
    im.onload = function () { if (im.naturalWidth > 1) im.className = 'pic ok'; };
    im.src = '/mon/' + species + '.png?k=' + encodeURIComponent(k);
  }
  function stat(label, value) { return '<div class="stat"><span class="kc-label">' + esc(label) + '</span><b>' + esc(value) + '</b></div>'; }
  function look() { card.className = (won ? 'won' : 'lost') + (phase ? ' ' + phase : ''); }
  function fill(d) {
    won = !!d.won;
    look();
    text('title', d.won ? 'RUN WON' : 'RUN OVER');
    el('attempt').innerHTML = d.attempt != null ? 'ATTEMPT<b>' + esc(d.attempt) + '</b>' : '';
    text('label', d.won ? 'BEAT THE GAME' : (d.label || ''));
    text('cause', d.won ? (d.title || '') : (d.cause || ''));
    // The place, when the line above names a trainer and not where (a wild Pokemon's line has it already).
    text('where', d.trainer && d.location ? d.location : '');
    pic('kpic', d.killer ? d.killer.species : null);
    text('quote', d.quote ? '"' + d.quote + '"' : '');
    var s = stat('BADGES', d.badges != null ? d.badges : 0);
    if (d.seconds) s += stat('TIME', clock(d.seconds));
    if (d.fallen) s += stat(d.won ? 'LEAD' : 'FELL', 'Lv.' + d.fallen.level + ' ' + d.fallen.name);
    el('stats').innerHTML = s;
    var best = el('best');
    best.className = d.newBest ? 'new' : '';
    best.textContent = d.newBest ? 'NEW PERSONAL BEST' : (d.best || '');
    var g = d.gachamon, box = el('gacha');
    if (g) {
      box.className = 'on kc-rule';
      text('gfrom', g.from === 'prize' ? 'GACHAMON PRIZE CARD' + (g.trainer ? ', FROM ' + String(g.trainer).toUpperCase() : '') : 'GACHAMON CARD');
      text('gname', g.name + '  Lv.' + g.level + (g.shiny ? '  shiny' : ''));
      var st = '';
      for (var i = 0; i < 5; i++) st += '<i class="star' + (i < (g.stars || 0) ? '' : ' off') + '"></i>';
      el('gstars').innerHTML = st;
      text('gfacts', 'Battle power ' + g.power + (g.ability ? '  \u00b7  ' + g.ability : ''));
      text('gmoves', (g.moves || []).join('  \u00b7  '));
      pic('gpic', g.species);
    } else {
      box.className = '';
      pic('gpic', null);
    }
  }
  function hide() {
    if (holdTimer) { clearTimeout(holdTimer); holdTimer = 0; }
    if (phase === 'on') { phase = 'off'; look(); }
    shownKey = null;
  }
  // The same card again (a reconnect, a prize card made a moment later) refreshes what it says without coming in again.
  function show(d) {
    if (!d) { dismissed = null; hide(); return; }
    if (d.key === dismissed) return;
    var again = d.key === shownKey;
    fill(d);
    if (again) return;
    shownKey = d.key;
    phase = ''; look();
    void card.offsetWidth;          // the animation starts again for a new card
    phase = 'on'; look();
    if (holdTimer) clearTimeout(holdTimer);
    holdTimer = 0;
    if (HOLD > 0) holdTimer = setTimeout(function () { holdTimer = 0; dismissed = shownKey; hide(); }, HOLD * 1000);
  }
  function live() {
    var es = new EventSource('/events?k=' + encodeURIComponent(k));
    es.addEventListener('gameover', function (e) { try { show(JSON.parse(e.data)); } catch (x) { } });
    es.onerror = function () { es.close(); setTimeout(live, 2000); };
  }
  if (q.get('demo')) {
    show({ key: 'demo', won: false, attempt: 812, label: 'LOST TO', cause: 'Lv.21 Sandile (Hiker Marcos)', trainer: 'Hiker Marcos',
      location: 'Route 3', killer: null, quote: 'Devastating!', badges: 3, seconds: 3723, fallen: { name: 'Mothim', level: 23 },
      newBest: false, best: 'Best: 5 badges, attempt 640',
      gachamon: { from: 'prize', trainer: 'Hiker Marcos', name: 'Sandile', level: 21, stars: 3, power: 412, ability: 'Moxie',
        moves: ['Bite', 'Sand Tomb', 'Leer'], shiny: false, species: null } });
    return;
  }
  live();
})();
</script></body></html>
"""

    private val TIMER = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>KaizoCore run timer</title>
<style>
:root{--w:420px;--bg:#0b0b0b;--panel:#161616;--line:#3a3a3a;--ink:#e8e8e8;--dim:#9a9a9a;--gold:#ffd54a;--red:#ff5d5d;--green:#69d36e;--blue:#7fb8ff}
html,body{margin:0;background:transparent}
body{font:15px/1.25 "Segoe UI",Roboto,Arial,sans-serif;color:var(--ink)}
#box{box-sizing:border-box;width:var(--w);background:var(--bg);border:2px solid var(--line);padding:10px 16px 12px;visibility:hidden}
#box.on{visibility:visible}
#top{display:flex;justify-content:space-between;align-items:baseline;color:var(--dim);font-size:13px;letter-spacing:2px}
#state{color:var(--gold)}
#state.over{color:var(--red)}
#state.won{color:var(--green)}
#t{font-size:56px;font-weight:700;font-variant-numeric:tabular-nums;line-height:1.1;color:var(--gold)}
#t.paused{opacity:.6}
table{width:100%;border-collapse:collapse;margin-top:6px;font-variant-numeric:tabular-nums}
th{font-weight:normal;color:var(--dim);font-size:12px;text-align:left;border-bottom:1px solid var(--line);padding:2px 6px 2px 0}
td{padding:3px 6px 3px 0;font-size:15px}
.n{text-align:right}
tr.todo td{color:var(--dim)}
td.ahead{color:var(--green)}
td.behind{color:var(--red)}
td.even{color:var(--ink)}
td.next{color:var(--dim)}
#bestline{color:var(--dim);font-size:12px;margin-top:6px}
</style></head><body>
<div id="box" data-panel>
<div id="top" class="kc-label"><span id="label">RUN TIME</span><span id="state"></span></div>
<div id="t" class="kc-big">0:00</div>
<table id="splits" class="kc-rule"></table>
<div id="bestline"></div>
</div>
<script>
(function () {
  var q = new URLSearchParams(location.search);
  var k = q.get('k') || '';
  if (q.get('w')) document.documentElement.style.setProperty('--w', q.get('w') + 'px');
  var SPLITS = q.get('splits') !== '0';
  var box = document.getElementById('box');
  var t = document.getElementById('t');
  var D = null, base = 0, at = 0, running = false, lastShown = 0, lastAttempt = null;
  function now() { return (typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now(); }
  function el(id) { return document.getElementById(id); }
  function clock(s) {
    s = Math.max(0, Math.floor(s || 0));
    var h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
    var mm = h > 0 && m < 10 ? '0' + m : String(m);
    return (h > 0 ? h + ':' : '') + mm + ':' + (x < 10 ? '0' : '') + x;
  }
  function delta(s) { return s < 0 ? '-' + clock(-s) : s > 0 ? '+' + clock(s) : '0:00'; }
  // Between the phone's updates (one a second while it counts) the page counts on by itself, never more than 2.5 s
  // past the last one (a dropped connection does not run on), and never backwards while it counts.
  function render() {
    if (!D || D.run !== true) { box.className = ''; return; }
    box.className = 'on';
    var ms = base + (running ? Math.min(Math.max(0, now() - at), 2500) : 0);
    if (running) { ms = Math.max(ms, lastShown); lastShown = ms; }
    t.textContent = clock(ms / 1000);
    t.className = 'kc-big' + (running || D.ended ? '' : ' paused');
  }
  function drawRest(d) {
    var st = el('state');
    st.textContent = d.ended === 'WON' ? 'RUN WON' : d.ended ? 'RUN OVER' : (d.running ? '' : 'PAUSED');
    st.className = d.ended === 'WON' ? 'won' : d.ended ? 'over' : '';
    el('label').textContent = d.attempt != null ? 'ATTEMPT ' + d.attempt : 'RUN TIME';
    var rows = SPLITS ? (d.splits || []) : [];
    var best = d.best;
    var h = '';
    if (rows.length) {
      h = '<tr><th>Split</th><th class="n">Time</th><th class="n">' + (best ? 'vs best' : '') + '</th></tr>';
      for (var i = 0; i < rows.length; i++) {
        var r = rows[i], cell;
        if (r.delta != null) cell = '<td class="n ' + (r.delta < 0 ? 'ahead' : r.delta > 0 ? 'behind' : 'even') + '">' + delta(r.delta) + '</td>';
        else if (r.at == null && r.best != null) cell = '<td class="n next">best ' + clock(r.best) + '</td>';
        else cell = '<td class="n"></td>';
        h += '<tr class="' + (r.at == null ? 'todo' : 'done') + '"><td>Badge ' + r.badge + '</td><td class="n">' + (r.at != null ? clock(r.at) : '-') + '</td>' + cell + '</tr>';
      }
    }
    el('splits').innerHTML = h;
    el('bestline').textContent = SPLITS && best ? 'Against attempt ' + best.attempt + (best.won ? ', a win' : ', ' + best.badges + (best.badges === 1 ? ' badge' : ' badges')) : '';
  }
  function take(d) {
    D = d;
    if (!d || d.run !== true) { render(); return; }
    running = !!d.running && !d.ended;
    // Stopped, or another run: the phone's time exactly, even if the page had counted on a little past it.
    if (!running || d.attempt !== lastAttempt) lastShown = 0;
    lastAttempt = d.attempt;
    base = d.ms || 0; at = now();
    drawRest(d);
    render();
  }
  function live() {
    var es = new EventSource('/events?k=' + encodeURIComponent(k));
    es.addEventListener('timer', function (e) { try { take(JSON.parse(e.data)); } catch (x) { } });
    es.onerror = function () { es.close(); setTimeout(live, 2000); };
  }
  setInterval(render, 250);
  if (q.get('demo')) {
    take({ run: true, attempt: 812, ms: 2712000, running: true, ended: null,
      splits: [{ badge: 1, at: 754, best: 799, delta: -45 }, { badge: 2, at: 1890, best: 1818, delta: 72 },
        { badge: 3, at: 2655, best: 2655, delta: 0 }, { badge: 4, at: null, best: 3410, delta: null }, { badge: 5, at: null, best: 4471, delta: null }],
      best: { attempt: 640, badges: 5, won: false, seconds: 4800 } });
    return;
  }
  live();
})();
</script></body></html>
"""
}

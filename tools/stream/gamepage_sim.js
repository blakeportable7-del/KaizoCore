// KaizoCore stream kit (2026-09-29): runs the /game page's own script against a virtual clock, a fake
// WebSocket and a fake AudioContext, to check what a browser would only show by ear and by eye.
//
//   node tools/stream/gamepage_sim.js          (exit code 0 and "ALL OK" when it passes)
//
// The script under test is read out of StreamPages.kt, so this checks the page the phone serves, not a copy.
//
// What it proves:
//   - sound pieces are joined without a gap or a jump at the seams: with the speed nudge switched off,
//     the output equals one continuous resampling of the whole stream, sample for sample;
//   - the sound cushion holds near 0.12 s through a phone clock 0.5 percent fast or slow, fast-forward
//     at 4x, Wi-Fi jitter of 40 ms, a 400 ms stall, and a browser that will not start the sound;
//   - the picture queue is bounded when the page's timers are held back, and it shows the newest picture;
//   - the layout: fit, whole-number scale, the DS top screen, both DS arrangements;
//   - the socket comes back by itself, with the key.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

// STREAM_PAGES_KT points the run at another copy of the file, which is how the checks are shown to fail on a broken page.
const kt = fs.readFileSync(process.env.STREAM_PAGES_KT || path.join(__dirname, '../../app/src/main/kotlin/com/ironmonone/app/stream/StreamPages.kt'), 'utf8').replace(/\r\n/g, '\n');
const open = 'private val GAME = """';
const from = kt.indexOf(open);
if (from < 0) throw new Error('GAME page not found in StreamPages.kt');
const html = kt.substring(from + open.length, kt.indexOf('"""', from + open.length));
const SCRIPT = html.substring(html.indexOf('<script>') + 8, html.lastIndexOf('</script>'));

let failures = 0, checks = 0;
function check(cond, msg) { checks++; if (!cond) { failures++; console.log('FAIL: ' + msg); } }
const near = (a, b, tol) => Math.abs(a - b) <= tol;

// ------------------------------------------------------------------------------------------------ the fake browser

function boot(o = {}) {
  const w = {
    now: 1000, timers: [], nextTimer: 1, draws: [], sockets: [], created: 0, closed: 0,
    ctx: null, audioRate: o.audioRate || 48000, audioDrift: o.audioDrift || 0, audioState: o.audioState || 'running', throttle: !!o.throttle,
    needGesture: !!o.needGesture, gestured: false, listeners: {},
  };
  const els = {};
  const el = (id) => els[id] || (els[id] = {
    id, style: {}, textContent: '', className: '', width: 0, height: 0, onclick: null,
    getContext() { return g2d; }, addEventListener() {},
  });
  const g2d = { imageSmoothingEnabled: true, drawImage(img) { w.draws.push(img); } };

  class FakeAC {
    constructor() {
      this.state = w.audioState; this.sampleRate = w.audioRate; this.currentTime = 0; this.scheduled = [];
      this.outputLatency = 0.02; this.onstatechange = null; w.ctx = this;
    }
    createBuffer(ch, len, rate) {
      const data = [new Float32Array(len), new Float32Array(len)];
      return { length: len, sampleRate: rate, duration: len / rate, getChannelData: (i) => data[i] };
    }
    createBufferSource() {
      const self = this;
      return { buffer: null, connect() {}, start(when) { self.scheduled.push({ when, buffer: this.buffer }); } };
    }
    get destination() { return {}; }
    resume() {
      if (w.needGesture && !w.gestured) return Promise.resolve();          // autoplay policy: stays suspended
      this.state = 'running'; w.audioState = 'running'; if (this.onstatechange) this.onstatechange(); return Promise.resolve();
    }
  }
  class FakeWS {
    constructor(url) { this.url = url; this.closed = false; w.sockets.push(this); }
    close() { if (!this.closed) { this.closed = true; if (this.onclose) this.onclose(); } }
  }

  const sandbox = {
    URLSearchParams, Blob, Uint8Array, Int16Array, DataView, Float32Array, Math, Promise, String, Number, Object,
    encodeURIComponent, console,
    location: { search: o.search || '?k=abcd', protocol: 'http:', host: 'phone:8642' },
    performance: { now: () => w.now },
    document: { getElementById: el, addEventListener(type, fn) { (w.listeners[type] = w.listeners[type] || []).push(fn); } },
    setTimeout: (fn, ms) => { const id = w.nextTimer++; w.timers.push({ at: w.now + Math.max(0, ms || 0), fn, id }); return id; },
    clearTimeout: (id) => { w.timers = w.timers.filter((t) => t.id !== id); },
    setInterval: () => 0,
    WebSocket: FakeWS,
    URL: { createObjectURL: () => 'blob:x', revokeObjectURL() {} },
  };
  sandbox.window = {
    AudioContext: o.noAudio ? undefined : FakeAC, innerWidth: o.width || 1500, innerHeight: o.height || 1080,
    addEventListener() {},
    createImageBitmap: (blob) => Promise.resolve({ id: ++w.created, width: w.picW || 240, height: w.picH || 160, close() { w.closed++; } }),
  };
  sandbox.createImageBitmap = sandbox.window.createImageBitmap;
  sandbox.window.AudioContext && (sandbox.AudioContext = sandbox.window.AudioContext);
  vm.createContext(sandbox);
  vm.runInContext(o.script || SCRIPT, sandbox);

  w.el = el;
  w.fire = (type) => { for (const fn of w.listeners[type] || []) fn({}); };
  w.gesture = () => { w.gestured = true; w.fire('pointerdown'); };
  w.ws = () => w.sockets[w.sockets.length - 1];
  // Move the clock. Timers run in order; the sound card's clock runs with it, off by audioDrift.
  w.advance = async (ms) => {
    const target = w.now + ms;
    for (;;) {
      if (w.throttle) break;
      const due = w.timers.filter((t) => t.at <= target).sort((a, b) => a.at - b.at)[0];
      if (!due) break;
      w.timers = w.timers.filter((t) => t !== due);
      const dt = due.at - w.now; w.now = due.at;
      if (w.ctx && w.audioState === 'running') w.ctx.currentTime += dt / 1000 * (1 + w.audioDrift);
      due.fn(); await Promise.resolve(); await Promise.resolve();
    }
    const dt = target - w.now; w.now = target;
    if (w.ctx && w.ctx.state === 'running') w.ctx.currentTime += dt / 1000 * (1 + w.audioDrift);
    await Promise.resolve(); await Promise.resolve();
  };
  w.fireTimers = async () => {   // let held-back timers run at last
    w.throttle = false; await w.advance(0); await w.advance(1000);
  };
  w.send = async (buf) => { w.ws().onmessage({ data: buf }); await Promise.resolve(); await Promise.resolve(); await Promise.resolve(); };
  w.open = () => { if (w.ws().onopen) w.ws().onopen(); };
  return w;
}

function soundMessage(freq, rate, startFrame, frames) {
  const buf = new ArrayBuffer(8 + frames * 4), dv = new DataView(buf);
  dv.setUint8(0, 2); dv.setUint8(2, 2); dv.setUint32(4, rate, true);
  for (let i = 0; i < frames; i++) {
    const v = Math.round(9000 * Math.sin(2 * Math.PI * freq * (startFrame + i) / rate));
    dv.setInt16(8 + i * 4, v, true); dv.setInt16(8 + i * 4 + 2, v, true);
  }
  return buf;
}
function pictureMessage(counter) {
  const buf = new ArrayBuffer(8 + 4), dv = new DataView(buf);
  dv.setUint8(0, 1); dv.setUint32(4, counter, true);
  return buf;
}

// The output timeline of everything the page scheduled: [{when, length, data}] in time order.
function timeline(w) {
  return w.ctx.scheduled.slice().sort((a, b) => a.when - b.when).map((s) => ({ when: s.when, length: s.buffer.length, data: s.buffer.getChannelData(0), rate: s.buffer.sampleRate }));
}
function gapsIn(tl) {
  let gaps = 0, worst = 0;
  for (let i = 1; i < tl.length; i++) {
    const end = tl[i - 1].when + tl[i - 1].length / tl[i - 1].rate;
    const e = Math.abs(tl[i].when - end);
    if (e > 1e-9) { gaps++; worst = Math.max(worst, e); }
  }
  return { gaps, worst };
}
function join(tl) {
  const n = tl.reduce((s, b) => s + b.length, 0), out = new Float32Array(n);
  let at = 0; for (const b of tl) { out.set(b.data, at); at += b.length; }
  return out;
}

// Feeds `count` sound pieces of `frames` frames, one every `everyMs` (a function of the index), and records the cushion.
async function feed(w, o) {
  const cushion = [];
  let start = 0;
  for (let i = 0; i < o.count; i++) {
    await w.send(soundMessage(o.freq || 440, o.rate || 32768, start, o.frames));
    start += o.frames;
    if (w.ctx) cushion.push(w.ctx.scheduled.length ? (w.ctx.scheduled[w.ctx.scheduled.length - 1].when + w.ctx.scheduled[w.ctx.scheduled.length - 1].buffer.duration - w.ctx.currentTime) : 0);
    await w.advance(typeof o.everyMs === 'function' ? o.everyMs(i) : o.everyMs);
  }
  return cushion;
}
const maxOf = (a) => { let m = -Infinity; for (let i = 0; i < a.length; i++) if (a[i] > m) m = a[i]; return m; };
const minOf = (a) => { let m = Infinity; for (let i = 0; i < a.length; i++) if (a[i] < m) m = a[i]; return m; };
const stats = (a) => ({ min: minOf(a), max: maxOf(a), mean: a.reduce((x, y) => x + y, 0) / a.length });

// ------------------------------------------------------------------------------------------------ the checks

(async () => {
  const REAL = 1000 * 546 / 32768;     // 546 frames at 32768 Hz, in milliseconds

  // 1. Seams. With the speed nudge off, chunking must not change the output at all.
  {
    const noSteer = SCRIPT.replace(/var steer = Math\.max\(-0\.03, Math\.min\(0\.03, \(ahead - CUSHION\) \* 0\.25\)\);/, 'var steer = 0;');
    check(noSteer !== SCRIPT, 'the steer line is where the test expects it');
    const w = boot({ script: noSteer });
    w.open();
    await feed(w, { count: 300, frames: 546, everyMs: REAL });
    const tl = timeline(w), g = gapsIn(tl), out = join(tl);
    check(g.gaps === 0, `seams: ${g.gaps} gap(s) between pieces, worst ${g.worst}`);
    // The reference: one continuous linear resampling of the whole sine.
    const step = 32768 / 48000, n = 300 * 546;
    let maxDiff = 0, compared = 0;
    for (let j = 0; j < out.length; j++) {
      const p = j * step, i = Math.floor(p);
      if (i + 1 >= n) break;
      const a = Math.round(9000 * Math.sin(2 * Math.PI * 440 * i / 32768)) / 32768, b = Math.round(9000 * Math.sin(2 * Math.PI * 440 * (i + 1) / 32768)) / 32768;
      maxDiff = Math.max(maxDiff, Math.abs(out[j] - (a + (b - a) * (p - i)))); compared++;
    }
    check(compared > 200000 && maxDiff < 1e-6, `seams: pieces differ from one continuous resampling by up to ${maxDiff} over ${compared} samples`);
    check(near(maxOf(out), 9000 / 32768, 0.002), 'seams: the level is right');
  }

  // 2. Steady arrival: the cushion sits at 0.12 s and never breaks.
  {
    const w = boot(); w.open();
    const c = await feed(w, { count: 1800, frames: 546, everyMs: REAL });     // 30 s
    const s = stats(c.slice(60));
    check(near(s.mean, 0.12, 0.02) && s.min > 0.08 && s.max < 0.2, `steady: cushion ${JSON.stringify(s)}`);
    check(gapsIn(timeline(w)).gaps === 0, 'steady: no gap in 30 s');
    check(timeline(w).length === 1800, `steady: every piece was played (${timeline(w).length})`);
  }

  // 3. The phone's clock 0.5 percent fast, and 0.5 percent slow: the nudge takes it up without a drop or a gap.
  for (const [name, factor] of [['fast', 1.005], ['slow', 0.995]]) {
    const w = boot(); w.open();
    const c = await feed(w, { count: 3600, frames: 546, everyMs: REAL / factor });   // 60 s
    const s = stats(c.slice(300));
    check(s.min > 0.06 && s.max < 0.25, `phone ${name}: cushion ${JSON.stringify(s)}`);
    check(gapsIn(timeline(w)).gaps === 0, `phone ${name}: no gap in 60 s`);
    check(timeline(w).length === 3600, `phone ${name}: nothing dropped (${timeline(w).length} of 3600)`);
  }

  // 4. The sound card's clock 0.5 percent off from the PC's: same thing from the other side.
  for (const drift of [0.005, -0.005]) {
    const w = boot({ audioDrift: drift }); w.open();
    const c = await feed(w, { count: 3600, frames: 546, everyMs: REAL });
    const s = stats(c.slice(300));
    check(s.min > 0.05 && s.max < 0.3, `card drift ${drift}: cushion ${JSON.stringify(s)}`);
    check(gapsIn(timeline(w)).gaps === 0, `card drift ${drift}: no gap in 60 s`);
  }

  // 5. Fast-forward at 4x: the rate on the wire is 131072, four times the frames per piece.
  {
    const w = boot(); w.open();
    const c = await feed(w, { count: 1200, frames: 546 * 4, rate: 131072, everyMs: REAL });
    const s = stats(c.slice(60));
    check(near(s.mean, 0.12, 0.03) && s.min > 0.07 && s.max < 0.25, `4x: cushion ${JSON.stringify(s)}`);
    check(gapsIn(timeline(w)).gaps === 0, '4x: no gap');
  }

  // 6. Wi-Fi jitter: every piece up to 40 ms early or late, the pace still real time.
  {
    let seed = 12345; const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
    const w = boot(); w.open();
    // Each piece leaves the phone on time and the network holds it back by up to 80 ms (so 40 ms either
    // side of the average), first in, first out. The pace over time is exactly real time.
    const N = 1800, arrive = [];
    let prev = 0;
    for (let i = 0; i <= N; i++) { prev = Math.max(prev, i * REAL + rnd() * 80); arrive.push(prev); }
    const c = await feed(w, { count: N, frames: 546, everyMs: (i) => arrive[i + 1] - arrive[i] });
    const g = gapsIn(timeline(w));
    check(g.gaps === 0, `jitter of up to 80 ms: ${g.gaps} gap(s) in 30 s`);
    check(minOf(c.slice(60)) > 0.0, 'jitter: never ran dry');
    check(timeline(w).length === N, `jitter: nothing dropped (${timeline(w).length})`);
  }

  // 7. A 400 ms stall: the sound runs dry once, starts again with its cushion, and carries on gapless.
  {
    const w = boot(); w.open();
    await feed(w, { count: 120, frames: 546, everyMs: REAL });
    await w.advance(400);
    await feed(w, { count: 240, frames: 546, everyMs: REAL });
    const tl = timeline(w), g = gapsIn(tl);
    check(g.gaps === 1, `stall: exactly one gap (${g.gaps})`);
    const after = tl.slice(-100);
    check(gapsIn(after).gaps === 0 && maxOf(join(after)) < 0.3, 'stall: gapless and sane afterwards');
  }

  // 8. A browser that will not start the sound: the clock is frozen. Pieces are dropped past half a second.
  {
    const w = boot({ audioState: 'suspended', needGesture: true }); w.open();
    for (let i = 0; i < 200; i++) await w.send(soundMessage(440, 32768, i * 546, 546));
    check(w.ctx.state === 'suspended', 'suspended: still suspended without a click');
    check(w.ctx.scheduled.length < 45, `suspended: bounded (${w.ctx.scheduled.length} scheduled of 200)`);
    check(w.el('tap').style.display === 'block', 'suspended: the click-for-sound note is showing');
    w.gesture();                                   // the streamer clicks
    check(w.ctx.state === 'running' && w.el('tap').style.display === 'none', 'a click starts the sound and hides the note');
    const before = w.ctx.scheduled.length;
    for (let i = 0; i < 60; i++) { await w.send(soundMessage(440, 32768, (200 + i) * 546, 546)); await w.advance(REAL); }
    check(w.ctx.scheduled.length - before >= 55, `after the click pieces are played again (${w.ctx.scheduled.length - before} of 60)`);
  }

  // 9. audio=0: no sound context at all.
  {
    const w = boot({ search: '?k=abcd&audio=0' }); w.open();
    await w.send(soundMessage(440, 32768, 0, 546));
    check(w.ctx === null, 'audio=0: no AudioContext was made');
  }

  // 10. Pictures. With sound running they wait for its cushion: every one must still be shown, in order,
  // about 140 ms after it arrived, and the queue must stay small.
  {
    const w = boot(); w.open();
    let maxAlive = 0;
    for (let i = 0; i < 300; i++) {
      await w.send(soundMessage(440, 32768, i * 546, 546));
      await w.send(pictureMessage(i + 1));
      maxAlive = Math.max(maxAlive, w.created - w.closed);
      await w.advance(REAL);
    }
    await w.advance(500);
    check(w.draws.length >= 285, `sound and pictures together: ${w.draws.length} of 300 pictures drawn`);
    const ids = w.draws.map((d) => d.id);
    check(ids.every((v, i) => i === 0 || v > ids[i - 1]), 'sound and pictures together: in order');
    check(maxAlive <= 14, `sound and pictures together: at most ${maxAlive} pictures held`);
    check(w.created - w.closed === 0, `sound and pictures together: every picture closed at the end (${w.created - w.closed} open)`);
  }
  {
    // The delay itself: with the cushion the first picture shows about 140 ms after it arrived, not at once.
    const w = boot(); w.open();
    for (let i = 0; i < 30; i++) { await w.send(soundMessage(440, 32768, i * 546, 546)); await w.advance(REAL); }
    const at = w.draws.length;
    await w.send(pictureMessage(1));
    check(w.draws.length === at, 'the picture is not shown the moment it arrives while sound is running');
    await w.advance(100);
    check(w.draws.length === at, 'nor after 100 ms');
    await w.advance(60);
    check(w.draws.length === at + 1, 'but by 160 ms it is');
  }
  {
    const w = boot(); w.open();
    // Sound running, so pictures are held for the cushion.
    await feed(w, { count: 20, frames: 546, everyMs: REAL });
    w.throttle = true;                                  // a browser source that is not on screen
    for (let i = 1; i <= 200; i++) { await w.send(pictureMessage(i)); w.now += 16; }
    const alive = w.created - w.closed;
    check(alive <= 32, `held-back timers: ${alive} decoded pictures kept alive`);
    await w.fireTimers();
    const shown = w.draws[w.draws.length - 1];
    check(shown && shown.id >= w.created - 30, `the picture drawn last is among the newest (${shown && shown.id} of ${w.created})`);
    check(w.created - w.closed <= 2, `after the timers ran ${w.created - w.closed} pictures are still open`);
  }
  {
    // No sound: a picture is drawn as soon as it decodes, in order, and none is left open.
    const w = boot({ noAudio: true }); w.open();
    for (let i = 1; i <= 30; i++) { await w.send(pictureMessage(i)); await w.advance(16); }
    check(w.draws.length === 30, `no sound: ${w.draws.length} of 30 pictures drawn`);
    const ids = w.draws.map((d) => d.id);
    check(ids.every((v, i) => i === 0 || v > ids[i - 1]), 'no sound: in order');
    check(w.created - w.closed === 0, 'no sound: every picture closed');
    check(w.el('msg').textContent === '' && w.el('msg').style.display === 'none', 'the first picture clears the message');
  }

  // 11. Layout.
  {
    const w = boot({ width: 1500, height: 1080 }); w.open();
    await w.send(pictureMessage(1)); await w.advance(0);
    const cv = w.el('c');
    check(cv.width === 240 && cv.height === 160, 'layout: canvas keeps the picture size');
    check(cv.style.width === '1500px' && cv.style.height === '1000px', `fit: ${cv.style.width} x ${cv.style.height}`);
    check(cv.style.left === '0px' && cv.style.top === '40px', `fit: centred at ${cv.style.left}, ${cv.style.top}`);
    const w2 = boot({ width: 1500, height: 1080, search: '?k=abcd&int=1' }); w2.open();
    await w2.send(pictureMessage(1)); await w2.advance(0);
    check(w2.el('c').style.width === '1440px' && w2.el('c').style.height === '960px', `int=1: ${w2.el('c').style.width} x ${w2.el('c').style.height}`);
    check(w2.el('c').style.left === '30px' && w2.el('c').style.top === '60px', 'int=1: centred');
    const w3 = boot({ width: 100, height: 100, search: '?k=abcd&int=1' }); w3.open();
    await w3.send(pictureMessage(1)); await w3.advance(0);
    check(w3.el('c').style.width === '100px', 'int=1 in a window smaller than the picture still fits it');
  }
  for (const [name, pw, ph, top, expectW, expectH] of [
    ['DS stacked, both screens', 256, 384, false, 256, 384],
    ['DS stacked, top only', 256, 384, true, 256, 192],
    ['DS stacked with a gap, top only', 256, 400, true, 256, 192],
    ['DS side by side, top only', 512, 192, true, 256, 192],
    ['DS side by side, both', 512, 192, false, 512, 192],
    ['GBA, top flag ignored', 240, 160, true, 240, 160],
    ['GB, top flag ignored', 160, 144, true, 160, 144],
  ]) {
    const w = boot({ search: top ? '?k=abcd&top=1' : '?k=abcd' });
    w.picW = pw; w.picH = ph; w.open();
    await w.send(pictureMessage(1)); await w.advance(0);
    check(w.el('c').width === expectW && w.el('c').height === expectH, `${name}: canvas ${w.el('c').width}x${w.el('c').height}, wanted ${expectW}x${expectH}`);
  }

  // 12. The socket: the key rides along, and it comes back by itself.
  {
    const w = boot({ search: '?k=a%20b' }); w.open();
    check(w.ws().url === 'ws://phone:8642/game.ws?k=a%20b', `socket url ${w.ws().url}`);
    check(w.sockets.length === 1, 'one socket to begin with');
    w.ws().close();
    check(/Lost the phone/.test(w.el('msg').textContent), `lost: "${w.el('msg').textContent}"`);
    await w.advance(2000);
    check(w.sockets.length === 2, 'it tried again');
    for (let i = 0; i < 4; i++) { w.ws().close(); await w.advance(4000); }
    check(w.sockets.length >= 5, 'and keeps trying');
    check(/Cannot reach the phone/.test(w.el('msg').textContent), `after four failures: "${w.el('msg').textContent}"`);
    w.open();
    check(/Connected/.test(w.el('msg').textContent), 'connected again');
  }

  console.log(`${checks} checks, ${failures} failure(s)`);
  console.log(failures === 0 ? 'ALL OK' : 'FAILED');
  process.exit(failures === 0 ? 0 : 1);
})().catch((e) => { console.log('CRASH', e && e.stack || e); process.exit(2); });

// KaizoCore stream kit (2026-09-30, UX audit): runs the script of one stream page under a fake browser and reports
// what it left on screen, for the JVM tests beside it (PageRunner.kt). Nothing here judges: the tests do.
//
//   node pages_runner.js job.json      prints one JSON object on stdout
//
// job:    { page: "tracker" | "attempts" | "game", file: "<the page's HTML>", search: "?k=abcd", steps: [ ... ],
//           plain: "<what the plain-text /attempts answers>", obs: <true inside OBS>, audioState: "suspended" }
// answer: { initial: {...}, steps: [ {...}, ... ] }   what the page showed after it loaded, then after each step
//
// steps:
//   tracker, attempts   { state: <snapshot> }       a server-sent "state" event
//   tracker             { fetch: {status, body} }   what /dex.json answers from now on; { call: "<code>" } runs page code
//                       { advance: <ms> }           the clock moves, and the page's timers with it
//   attempts            { plain: "7" }              the plain-text answer changes, and the 5 second poll comes round
//   game                { open: true }              the socket opens
//                       { picture: true }           a picture arrives (the fake browser decodes it at once)
//                       { sound: true }             a little sound arrives (this is what makes the page ask for a click)
//                       { close: true }             the socket drops
//                       { advance: <ms> }           the clock moves, and the page's timers with it
//   favorite            { reply: {status, etag, body} }   what the favorite's picture answers from now on (null: the
//                                                   phone cannot be reached); job.reply is the first, job.path the
//                                                   page's own address, like "/favorite/4"
//                       { advance: <ms> }           the clock moves, and the page's timers with it (it asks every 2 s)
//
// The page's own script is cut out of the HTML, so this runs the page the phone serves, not a copy.
'use strict';
const fs = require('fs');
const vm = require('vm');

const job = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const html = fs.readFileSync(job.file, 'utf8').replace(/\r\n/g, '\n');
const script = html.substring(html.indexOf('<script>') + 8, html.lastIndexOf('</script>'));

const settle = async () => { for (let i = 0; i < 6; i++) await Promise.resolve(); };

// A virtual clock: timers run in order as it moves, and never on their own.
function clock() {
  const t = { now: 1000, timers: [], next: 1 };
  t.setTimeout = (fn, ms) => { const id = t.next++; t.timers.push({ at: t.now + Math.max(0, ms || 0), fn, id, every: 0 }); return id; };
  t.clearTimeout = (id) => { t.timers = t.timers.filter((x) => x.id !== id); };
  t.setInterval = (fn, ms) => { const id = t.next++; t.timers.push({ at: t.now + ms, fn, id, every: ms }); return id; };
  t.advance = async (ms) => {
    const target = t.now + ms;
    for (;;) {
      const due = t.timers.filter((x) => x.at <= target).sort((a, b) => a.at - b.at)[0];
      if (!due) break;
      t.now = due.at;
      if (due.every) due.at += due.every; else t.timers = t.timers.filter((x) => x !== due);
      due.fn();
      await settle();
    }
    t.now = target;
    await settle();
  };
  return t;
}

// Elements that remember what the page wrote to them, made when the page first asks for one.
function fakeDocument() {
  const els = {};
  const vars = {};
  const listeners = {};
  const g2d = { imageSmoothingEnabled: true, draws: 0, drawImage() { g2d.draws++; } };
  const el = (id) => els[id] || (els[id] = {
    id, style: {}, textContent: '', innerHTML: '', className: '', value: '', width: 0, height: 0, onclick: null,
    getContext() { return g2d; }, addEventListener() {}, focus() {},
  });
  const document = {
    getElementById: el,
    documentElement: { style: { setProperty(k, v) { vars[k] = v; } } },
    addEventListener(type, fn) { (listeners[type] = listeners[type] || []).push(fn); },
  };
  return { document, el, vars, g2d };
}

class FakeEventSource {
  constructor(url) { this.url = url; this.listeners = {}; FakeEventSource.made.push(this); }
  addEventListener(type, fn) { this.listeners[type] = fn; }
  close() { this.closed = true; }
}

function trackerHost() {
  FakeEventSource.made = [];
  const d = fakeDocument();
  const c = clock();
  // What /dex.json answers ({status, body}); none: the phone is unreachable.
  let dexReply = null;
  const sandbox = {
    URLSearchParams, location: { search: job.search || '' }, document: d.document, EventSource: FakeEventSource,
    setTimeout: c.setTimeout, clearTimeout: c.clearTimeout,
    fetch: () => dexReply
      ? Promise.resolve({ ok: dexReply.status === 200, status: dexReply.status, json: () => Promise.resolve(dexReply.body) })
      : Promise.reject(new Error('offline')),
    console, JSON, Math, String, Number, Object, Array, Promise,
  };
  sandbox.window = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(script, sandbox);
  return {
    seen: () => ({
      own: d.el('own').innerHTML, enemy: d.el('enemy').innerHTML, over: d.el('over').innerHTML,
      ownDisplay: d.el('own').style.display || '', enemyClass: d.el('enemy').className, overClass: d.el('over').className,
      sources: FakeEventSource.made.length,
    }),
    step: async (s) => {
      if (s.fetch) dexReply = s.fetch;
      if (s.state) {
        const es = FakeEventSource.made[FakeEventSource.made.length - 1];
        es.listeners.state({ data: JSON.stringify(s.state) });
      }
      // The page's own code, as a tap would run it ("overTab='dex';loadDex()").
      if (s.call) vm.runInContext(s.call, sandbox);
      await settle();
      if (s.advance) await c.advance(s.advance);
    },
  };
}

function attemptsHost() {
  FakeEventSource.made = [];
  const d = fakeDocument();
  const c = clock();
  let plain = job.plain || '';
  let fetches = 0;
  const sandbox = {
    URLSearchParams, encodeURIComponent, location: { search: job.search || '' }, document: d.document, EventSource: FakeEventSource,
    setTimeout: c.setTimeout, clearTimeout: c.clearTimeout, setInterval: c.setInterval,
    fetch: () => { fetches++; return Promise.resolve({ text: () => Promise.resolve(plain) }); },
    console, JSON, Math, String, Number, Object, Array, Promise, RegExp,
  };
  sandbox.window = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(script, sandbox);
  return {
    seen: () => ({
      n: d.el('n').textContent, box: d.el('box').className, label: d.el('label').textContent, width: d.vars['--w'] || '',
      fetches, sources: FakeEventSource.made.length,
    }),
    step: async (s) => {
      if (s.state) FakeEventSource.made[FakeEventSource.made.length - 1].listeners.state({ data: JSON.stringify(s.state) });
      if (s.plain !== undefined) { plain = s.plain; await c.advance(5000); }
      await settle();
    },
    settle,
  };
}

function gameHost() {
  const d = fakeDocument();
  const c = clock();
  const sockets = [];
  class FakeSocket {
    constructor(url) { this.url = url; this.closed = false; sockets.push(this); }
    close() { if (!this.closed) { this.closed = true; if (this.onclose) this.onclose(); } }
  }
  const audioState = job.audioState || 'running';
  class FakeAudio {
    constructor() { this.state = audioState; this.sampleRate = 48000; this.currentTime = 0; this.outputLatency = 0.02; this.onstatechange = null; }
    createBuffer(ch, len, rate) {
      const data = [new Float32Array(len), new Float32Array(len)];
      return { length: len, sampleRate: rate, duration: len / rate, getChannelData: (i) => data[i] };
    }
    createBufferSource() { return { buffer: null, connect() {}, start() {} }; }
    get destination() { return {}; }
    resume() { return Promise.resolve(); }     // a browser that holds the sound back until a click: it stays as it is
  }
  const sandbox = {
    URLSearchParams, Blob, Uint8Array, Int16Array, DataView, Float32Array, Math, Promise, String, Number, Object, encodeURIComponent, console,
    location: { search: job.search || '?k=abcd', protocol: 'http:', host: 'phone:8642' },
    performance: { now: () => c.now }, document: d.document,
    setTimeout: c.setTimeout, clearTimeout: c.clearTimeout, setInterval: () => 0,
    WebSocket: FakeSocket, URL: { createObjectURL: () => 'blob:x', revokeObjectURL() {} },
  };
  sandbox.window = {
    AudioContext: FakeAudio, innerWidth: 1500, innerHeight: 1080, addEventListener() {},
    createImageBitmap: () => Promise.resolve({ width: 240, height: 160, close() {} }),
  };
  if (job.obs) sandbox.window.obsstudio = { pluginVersion: '2.0.0' };    // what OBS's browser source puts on every page
  sandbox.createImageBitmap = sandbox.window.createImageBitmap;
  vm.createContext(sandbox);
  vm.runInContext(script, sandbox);

  const picture = () => { const b = new ArrayBuffer(12); new DataView(b).setUint8(0, 1); return b; };
  const sound = () => { const b = new ArrayBuffer(8 + 32 * 4); const v = new DataView(b); v.setUint8(0, 2); v.setUint8(2, 2); v.setUint32(4, 32768, true); return b; };
  const last = () => sockets[sockets.length - 1];
  return {
    seen: () => ({
      msg: { text: d.el('msg').textContent, display: d.el('msg').style.display || '' },
      mark: { display: d.el('mark').style.display || '' },
      tap: { display: d.el('tap').style.display || '' },
      sockets: sockets.length, drawn: d.g2d.draws,
    }),
    step: async (s) => {
      if (s.open) last().onopen();
      if (s.picture) { last().onmessage({ data: picture() }); await settle(); await c.advance(0); }
      if (s.sound) { last().onmessage({ data: sound() }); await settle(); }
      if (s.close) last().close();
      if (s.advance) await c.advance(s.advance);
      await settle();
    },
  };
}

// A favorite's page (2026-10-03, StreamFavorites): what it asks the phone for, and which picture it shows. The fake
// browser has a picture loaded the moment its address is set, as a small PNG from the phone is.
function favoriteHost() {
  const d = fakeDocument();
  const c = clock();
  let reply = job.reply || null;
  const asked = [];
  let made = 0, revoked = 0;
  const img = d.el('f');
  Object.defineProperty(img, 'src', { get() { return img._src || ''; }, set(v) { img._src = v; if (img.onload) img.onload(); } });
  const sandbox = {
    URLSearchParams, encodeURIComponent, console, JSON, Math, String, Number, Object, Array, Promise,
    location: { search: job.search || '?k=abcd', pathname: job.path || '/favorite/1' },
    document: d.document, setTimeout: c.setTimeout, clearTimeout: c.clearTimeout, setInterval: c.setInterval,
    fetch: (url, opts) => {
      asked.push({ url, cache: (opts && opts.cache) || '' });
      if (!reply) return Promise.reject(new Error('offline'));
      const r = reply;
      return Promise.resolve({
        ok: r.status === 200, status: r.status,
        headers: { get: (h) => (String(h).toLowerCase() === 'etag' ? (r.etag || null) : null) },
        blob: () => Promise.resolve({ body: r.body }),
      });
    },
    URL: { createObjectURL: (b) => 'blob:' + (++made) + ':' + b.body, revokeObjectURL: () => { revoked++; } },
  };
  sandbox.window = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(script, sandbox);
  const lastAsked = () => asked[asked.length - 1] || { url: '', cache: '' };
  return {
    seen: () => ({
      src: img.src, visibility: img.style.visibility || '', rendering: img.style.imageRendering || '',
      fetches: asked.length, url: lastAsked().url, cache: lastAsked().cache, made, revoked,
    }),
    step: async (s) => {
      if (s.reply !== undefined) reply = s.reply;
      await settle();
      if (s.advance) await c.advance(s.advance);
      await settle();
    },
  };
}

(async () => {
  const host = { tracker: trackerHost, attempts: attemptsHost, game: gameHost, favorite: favoriteHost }[job.page]();
  await settle();
  const out = { initial: host.seen(), steps: [] };
  for (const s of job.steps || []) { await host.step(s); out.steps.push(host.seen()); }
  process.stdout.write(JSON.stringify(out));
})().catch((e) => { process.stderr.write('pages_runner: ' + (e && e.stack || e) + '\n'); process.exit(2); });

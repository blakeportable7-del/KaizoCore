// Drives the WebAssembly build of sprite_core.h for SpriteCoreWasmTest (Kotlin talks to this
// over stdin and stdout, one command a line, one answer a line).
//
//   W <kind> <offset> <hex>          write bytes into a buffer      -> "="
//   R <kind> <offset> <length>       read bytes                     -> "= <hex>"
//   F <kind> <offset> <length> <b>   fill                           -> "="
//   C <fn> <arg>...                  call an export, args are numbers, 0x hex, or @kind:offset for
//                                    a pointer into a buffer; the answer is the result as unsigned
//   Q                                quit
// Buffer kinds: 0 IWRAM, 1 EWRAM, 2 palette RAM, 3 frame, 4 sprite, 5 scratch.
'use strict';
const fs = require('fs');
const readline = require('readline');

const wasm = fs.readFileSync(process.argv[2]);
WebAssembly.instantiate(wasm, {}).then(({ instance }) => {
  const ex = instance.exports;
  const mem = () => new Uint8Array(ex.memory.buffer);
  const base = (kind) => ex.h_buf(kind);
  const num = (t) => {
    if (t.startsWith('@')) {
      const [k, o] = t.slice(1).split(':');
      return base(parseInt(k, 10)) + parseInt(o, 10);
    }
    return Number(t) | 0;
  };
  const rl = readline.createInterface({ input: process.stdin });
  rl.on('line', (line) => {
    const t = line.trim().split(/\s+/);
    try {
      switch (t[0]) {
        case 'W': {
          const kind = parseInt(t[1], 10), off = parseInt(t[2], 10), hex = t[3] || '';
          const m = mem(), b = base(kind);
          if (off + hex.length / 2 > ex.h_size(kind)) throw new Error('write past buffer');
          for (let i = 0; i < hex.length; i += 2) m[b + off + i / 2] = parseInt(hex.substr(i, 2), 16);
          console.log('=');
          break;
        }
        case 'R': {
          const kind = parseInt(t[1], 10), off = parseInt(t[2], 10), len = parseInt(t[3], 10);
          if (off + len > ex.h_size(kind)) throw new Error('read past buffer');
          const m = mem(), b = base(kind);
          let out = '';
          for (let i = 0; i < len; i++) out += m[b + off + i].toString(16).padStart(2, '0');
          console.log('= ' + out);
          break;
        }
        case 'F': {
          const kind = parseInt(t[1], 10), off = parseInt(t[2], 10), len = parseInt(t[3], 10), v = parseInt(t[4], 10);
          if (off + len > ex.h_size(kind)) throw new Error('fill past buffer');
          const m = mem(), b = base(kind);
          for (let i = 0; i < len; i++) m[b + off + i] = v;
          console.log('=');
          break;
        }
        case 'C': {
          const fn = ex[t[1]];
          if (typeof fn !== 'function') throw new Error('no export ' + t[1]);
          const r = fn.apply(null, t.slice(2).map(num));
          console.log('= ' + ((r === undefined ? 0 : r) >>> 0));
          break;
        }
        case 'Q':
          console.log('=');
          process.exit(0);
          break;
        default:
          throw new Error('unknown command ' + t[0]);
      }
    } catch (e) {
      console.log('! ' + e.message);
    }
  });
  console.log('ready');
}).catch((e) => { console.log('! ' + e.message); process.exit(1); });

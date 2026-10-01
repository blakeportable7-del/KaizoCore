"""The slice of BizHawk's Lua API a BizHawk bot script uses, served by the app's test port in lockstep.

A BizHawk script calls joypad.set() for the next frame, then emu.frameadvance(), then reads memory. Here
emu.frameadvance() is one POST /frame to the port: the buttons, exactly one frame, and the game's RAM read after
it, the same order BizHawk keeps. Memory reads are then answered from that copy, so a script can read as often as
it likes per frame at no cost.

Used for PokeBot (MIT, Kyle Coburn and Michael Jondahl), the Red and Yellow speedrun bot, whose scripts expect
BizHawk's Game Boy WRAM domain: an address is an offset into WRAM. The core's system RAM starts at 0x02000000
(bank 0 at +0x0000, bank 1 at +0x1000), so offset o is read at 0x02000000 + o.
"""
import os
import time

# Plain Lua 5.1, what BizHawk 1.x ran: LuaJIT lacks 5.1's implicit `arg` in vararg functions, which PokeBot uses.
from lupa import lua51 as lj

WRAM_BASE = 0x02000000
WRAM_SIZE = 0x2000

# BizHawk's Game Boy button names, as scripts write them in joypad.set tables.
NAMES = {'Up': 'UP', 'Down': 'DOWN', 'Left': 'LEFT', 'Right': 'RIGHT', 'A': 'A', 'B': 'B',
         'Start': 'START', 'Select': 'SELECT'}


def _fold(f, args):
    out = int(args[0])
    for a in args[1:]:
        out = f(out, int(a))
    return out


class BizHawk:
    def __init__(self, port, script_dir, *, cgb=False, on_screenshot=None, log=print, frames_per_call=1):
        self.port = port
        self.cgb = cgb                          # Yellow runs as a Game Boy Color game: BizHawk's WRAM domain is 32 KB
        self.on_screenshot = on_screenshot
        self.log = log
        self.frames_per_call = frames_per_call
        self.pending = set()
        self.reset_pending = False
        self.frames = 0
        self.wram = bytes(WRAM_SIZE)
        self.calls = 0
        self.started = time.time()
        self.lua = lj.LuaRuntime(unpack_returned_tuples=True)
        g = self.lua.globals()
        # One folder, or several searched in order (a fork's own files first, then the bot it builds on).
        dirs = [script_dir] if isinstance(script_dir, str) else list(script_dir)
        self.script_dir = dirs[0]
        g.package.path = ''.join(d.replace('\\', '/') + '/?.lua;' for d in dirs) + g.package.path
        self._install(g)

    # ---- the API --------------------------------------------------------------------------------------------

    def _install(self, g):
        lua = self.lua

        def readbyte(addr):
            return self.wram[int(addr) % WRAM_SIZE]

        def writebyte(addr, value):
            addr = int(addr) % WRAM_SIZE
            self.port.write(WRAM_BASE + addr, bytes([int(value) & 0xFF]))
            self.wram = self.wram[:addr] + bytes([int(value) & 0xFF]) + self.wram[addr + 1:]

        def domain_size():
            return 0x8000 if self.cgb else WRAM_SIZE

        def joypad_set(table, *_):
            for name, down in table.items():
                name = str(name).replace('P1 ', '')
                if name == 'Power' and down:
                    self.reset_pending = True       # BizHawk's power button: the scripts' way to reset
                    continue
                key = NAMES.get(name)
                if key and down:
                    self.pending.add(key)

        def frameadvance():
            self.advance()

        def framecount():
            return self.frames

        def screenshot(path=None):
            if self.on_screenshot:
                self.on_screenshot(path)

        def reboot_core():
            self.log('bot asked for a reset: resetting the game')
            self.port.reset()
            self.advance()

        memory = lua.table_from({'readbyte': readbyte, 'writebyte': writebyte,
                                 'getcurrentmemorydomainsize': domain_size})
        g.memory = memory
        g.mainmemory = memory
        g.joypad = lua.table_from({'set': joypad_set})
        g.emu = lua.table_from({'frameadvance': frameadvance, 'framecount': framecount})
        noop = lua.eval('function(...) end')
        g.gui = lua.table_from({'text': noop, 'cleartext': noop, 'drawText': noop})
        g.client = lua.table_from({'speedmode': noop, 'pause': noop, 'unpause': noop,
                                   'setscreenshotosd': noop, 'screenshot': screenshot,
                                   'reboot_core': reboot_core})
        # BizHawk's bit library (Lua 5.1 has none), and LuaSocket, which PokeBot's bridge requires and never uses.
        m32 = 0xFFFFFFFF
        g.bit = lua.table_from({
            'band': lambda *a: _fold(lambda x, y: x & y, a) & m32,
            'bor': lambda *a: _fold(lambda x, y: x | y, a) & m32,
            'bxor': lambda *a: _fold(lambda x, y: x ^ y, a) & m32,
            'bnot': lambda x: ~int(x) & m32,
            'lshift': lambda x, n: (int(x) << int(n)) & m32,
            'rshift': lambda x, n: (int(x) & m32) >> int(n),
            'check': lambda x, n: bool(int(x) >> int(n) & 1),
        })
        lua.execute("package.preload['socket'] = function() return {} end")
        g.console = lua.table_from({'log': lambda *a: self.log(' '.join(str(x) for x in a)), 'clear': noop})
        g.print = lambda *a: self.log(' '.join(str(x) for x in a))

    # ---- the frame --------------------------------------------------------------------------------------------

    def advance(self):
        if self.reset_pending:
            self.reset_pending = False
            self.log('bot pressed Power: resetting the game')
            self.port.reset()
        count, parts = self.port.frame(sorted(self.pending), [(WRAM_BASE, WRAM_SIZE)], self.frames_per_call)
        self.pending = set()
        if parts[0] is not None and len(parts[0]) == WRAM_SIZE:
            self.wram = parts[0]
        self.frames = count
        self.calls += 1

    def run(self, main='main.lua', setup=''):
        """Runs the script's main file until it returns or raises. `setup` is Lua run first (settings)."""
        if setup:
            self.lua.execute(setup)
        with open(os.path.join(self.script_dir, main), encoding='utf-8') as f:
            code = f.read()
        return self.lua.execute(code)

    def rate(self):
        dt = max(1e-6, time.time() - self.started)
        return self.calls / dt

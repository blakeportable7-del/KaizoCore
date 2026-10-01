"""kcbot's own tests: nothing here needs a device. Run with the kcbot venv:

    C:/Users/bepor/kcbot-venv/Scripts/python.exe -m unittest discover -s tests -v
"""
import os
import struct
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from kcbot.gen3 import ORDERS, Gen3                    # noqa: E402
from kcbot.symbols import SYSTEM_RAM, Symbols          # noqa: E402


def encrypt_mon(species, level, hp, max_hp, personality, ot_id):
    """A party Pokémon as the game stores it: growth first in the order personality % 24 picks, then encrypted."""
    growth = struct.pack('<HHI', species, 0, 0) + bytes(4)
    blocks = {'G': growth, 'A': bytes(12), 'E': bytes(12), 'M': bytes(12)}
    order = ORDERS[personality % 24]
    plain = b''.join(blocks[c] for c in order)
    key = personality ^ ot_id
    enc = b''.join(struct.pack('<I', struct.unpack_from('<I', plain, i)[0] ^ key) for i in range(0, 48, 4))
    head = struct.pack('<II', personality, ot_id) + bytes(0x18)
    tail = bytes([0, 0, 0, 0, level, 0]) + struct.pack('<HH', hp, max_hp) + bytes(100 - 0x5A)
    mon = head + enc + tail
    assert len(mon) == 100, len(mon)
    return mon


class Gen3PartyTest(unittest.TestCase):
    def test_every_order_decodes_the_species(self):
        for p in range(24):
            personality = 0x1000 + p          # personality % 24 walks all 24 orders
            mon = encrypt_mon(species=277, level=5, hp=19, max_hp=20, personality=personality, ot_id=0xDEADBEEF)
            got = Gen3._mon(mon)
            self.assertEqual(277, got['species'], ORDERS[personality % 24])
            self.assertEqual((5, 19, 20), (got['level'], got['hp'], got['max_hp']))

    def test_the_orders_are_all_24_arrangements(self):
        self.assertEqual(24, len(set(ORDERS)))
        for o in ORDERS:
            self.assertEqual('AEGM', ''.join(sorted(o)))


class SymbolsTest(unittest.TestCase):
    def test_game_boy_wram_goes_to_system_ram(self):
        red = Symbols('red')
        self.assertEqual(SYSTEM_RAM + 0x135E, red.port_address('wCurMap'))
        self.assertEqual(SYSTEM_RAM + 0x1163, red.port_address('wPartyCount'))

    def test_yellow_is_shifted_from_red(self):
        red, yellow = Symbols('red'), Symbols('yellow')
        self.assertEqual(red.port_address('wCurMap') - 1, yellow.port_address('wCurMap'))

    def test_crystal_bank_one(self):
        # pokecrystal 01:dcd7 wPartyCount: bank 1 of WRAM, as the app's tracker reads it (GbcTracker)
        self.assertEqual(SYSTEM_RAM + 0x1CD7, Symbols('crystal').port_address('wPartyCount'))

    def test_gba_addresses_pass_through(self):
        frlg = Symbols('firered-v11')
        self.assertEqual(0x02024284, frlg.port_address('gPlayerParty'))
        self.assertEqual(0x258, frlg.size('gPlayerParty'))


class BizHawkTest(unittest.TestCase):
    """The Lua side, with a fake port."""

    class FakePort:
        def __init__(self):
            self.frames, self.calls, self.resets = 0, [], 0

        def frame(self, buttons, ranges, frames=1):
            self.frames += frames
            self.calls.append(tuple(buttons))
            wram = bytearray(0x2000)
            wram[0x135E] = 0x28
            return self.frames, [bytes(wram)]

        def reset(self):
            self.resets += 1

        def write(self, addr, data):
            return len(data)

    def test_joypad_then_frameadvance_sends_that_frame_only(self):
        from kcbot.bizhawk import BizHawk
        port = self.FakePort()
        bot = BizHawk(port, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'pokebot'))
        bot.lua.execute('joypad.set({A=true, Up=true}); emu.frameadvance(); emu.frameadvance()')
        self.assertEqual([('A', 'UP'), ()], port.calls, 'held for one frame, as BizHawk does')
        self.assertEqual(0x28, bot.lua.eval('memory.readbyte(0xD35E)'), 'an address is an offset into WRAM')
        self.assertEqual(2, bot.lua.eval('emu.framecount()'))

    def test_power_resets_the_game(self):
        from kcbot.bizhawk import BizHawk
        port = self.FakePort()
        bot = BizHawk(port, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'pokebot'))
        bot.lua.execute('joypad.set({Power=true}); emu.frameadvance()')
        self.assertEqual(1, port.resets)

    def test_lua_51_details_the_scripts_use(self):
        from kcbot.bizhawk import BizHawk
        bot = BizHawk(self.FakePort(), '.')
        self.assertEqual(6, bot.lua.eval('bit.band(0x1F, 6)'))
        self.assertEqual(16, bot.lua.eval('bit.rshift(256, 4)'))
        bot.lua.execute('function count(...) return #arg end')
        self.assertEqual(3, bot.lua.eval('count(1, 2, 3)'), 'implicit arg, which LuaJIT lacks')
        bot.lua.execute('local s = require "socket"')


if __name__ == '__main__':
    unittest.main()

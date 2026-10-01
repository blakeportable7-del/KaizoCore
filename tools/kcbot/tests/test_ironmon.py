"""The IronMON fork of PokeBot (pokebot-ironmon/), with a fake port: no device needed.

    C:/Users/bepor/kcbot-venv/Scripts/python.exe -m unittest discover -s tests -v
"""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, HERE)

from kcbot.bizhawk import BizHawk   # noqa: E402

SCRIPTS = [os.path.join(HERE, 'pokebot-ironmon'), os.path.join(HERE, 'pokebot')]

# Gen 1 move numbers
TACKLE, GROWL, WRAP, EXPLOSION, ABSORB, SURF = 33, 45, 35, 153, 71, 57


def yellow(red_address):
    """PokeBot writes Red's WRAM offsets and shifts them for Yellow (util/memory.lua): the same here."""
    return red_address - 1 if 0x0F12 < red_address < 0x1F00 else red_address


class FakePort:
    def __init__(self):
        self.calls = []

    def frame(self, buttons, ranges, frames=1):
        self.calls.append(tuple(buttons))
        return len(self.calls), [None]

    def reset(self):
        pass

    def write(self, addr, data):
        return len(data)


def load_fork():
    """The fork's modules as main.lua loads them, without its endless loop."""
    port = FakePort()
    bot = BizHawk(port, SCRIPTS, cgb=True, log=lambda *a: None)
    bot.lua.execute('''
        VERSION = "2.5.4"
        local Data = require "data.data"
        Data.init()
        Combat = require "ai.combat"
        Strategies = require("ai."..Data.gameName..".strategies")
        Paths = require("data."..Data.gameName..".paths")
        require "ironmon"
    ''')
    return bot, port


def battle(moves, pp, map_id=0x0C):
    """A trainer battle's WRAM in Yellow: our moves and PP, both sides alive at level 10 with plain stats."""
    w = bytearray(0x2000)
    w[yellow(0x135E)] = map_id
    w[yellow(0x1057)] = 2                              # wIsInBattle: a trainer
    for i, (m, p) in enumerate(zip(moves, pp)):
        w[yellow(0x101C) + i] = m
        w[yellow(0x102D) + i] = p
    w[yellow(0x0FED)] = TACKLE                         # the enemy has Tackle
    w[yellow(0x1022)] = 10                             # our level
    w[yellow(0x0FF3)] = 10                             # theirs
    w[yellow(0x1015) + 1] = 30                         # our HP (PokeBot adds the two bytes)
    w[yellow(0x0FE6) + 1] = 30                         # theirs
    for red in (0x1025, 0x1027, 0x1029, 0x102B, 0x0FF6, 0x0FF8, 0x0FFA, 0x0FFC):
        w[yellow(red) + 1] = 20                        # attack, defense, speed, special
    w[yellow(0x116B)] = 0x99                           # a lead in the party
    return bytes(w)


class IronmonMovesTest(unittest.TestCase):
    def best(self, moves, pp, map_id=0x0C):
        bot, _ = load_fork()
        bot.wram = battle(moves, pp, map_id)
        return bot.lua.eval('(function() local m = Combat.bestMove(); return m and m.midx, m and m.name end)()')

    def test_a_banned_move_is_never_picked(self):
        self.assertEqual((2, 'Tackle'), self.best([WRAP, TACKLE], [20, 35]))
        self.assertEqual((2, 'Tackle'), self.best([ABSORB, TACKLE], [20, 35]), 'draining heals: banned')
        self.assertEqual((2, 'Tackle'), self.best([SURF, TACKLE], [15, 35]), 'no HM moves in battle')

    def test_a_status_move_before_a_banned_one(self):
        # With nothing that deals damage, PokeBot takes the first move it may use: Growl, not Wrap.
        self.assertEqual((2, 'Growl'), self.best([WRAP, GROWL], [20, 40]))

    def test_the_starters_banned_move_is_allowed_in_the_lab(self):
        self.assertEqual((1, 'Wrap'), self.best([WRAP, GROWL], [20, 40], map_id=40))

    def test_never_explosion_even_in_the_lab(self):
        self.assertEqual((2, 'Growl'), self.best([EXPLOSION, GROWL], [5, 40], map_id=40))

    def test_struggle_when_every_move_is_out(self):
        self.assertEqual((1, 'Struggle'), self.best([TACKLE, GROWL], [0, 0]))

    def test_only_a_banned_move_left_ends_the_run(self):
        with self.assertRaises(Exception) as e:
            self.best([WRAP, TACKLE], [20, 0])
        self.assertIn('KCBOT_RUN_OVER no legal move', str(e.exception))


class IronmonEvolutionTest(unittest.TestCase):
    def press(self, wram):
        bot, port = load_fork()
        bot.wram = wram
        bot.lua.execute('joypad.set({A=true, B=true}); emu.frameadvance()')
        return port.calls[-1]

    def test_b_is_held_back_while_a_pokemon_evolves(self):
        w = bytearray(0x2000)
        w[yellow(0x1121)] = 1          # wEvolutionOccurred
        w[0x0EEA] = 0x24               # wEvoNewSpecies
        w[yellow(0x116B)] = 0x99       # the lead is still the old form
        self.assertEqual(('A',), self.press(bytes(w)))

    def test_b_comes_back_once_it_has_evolved(self):
        w = bytearray(0x2000)
        w[yellow(0x1121)] = 1
        w[0x0EEA] = 0x24
        w[yellow(0x116B)] = 0x24       # the new form
        self.assertEqual(('A', 'B'), self.press(bytes(w)))


class IronmonRouteTest(unittest.TestCase):
    def test_every_strategy_on_the_route_exists(self):
        bot, _ = load_fork()
        missing = bot.lua.eval('''(function()
            local out = {}
            for i, path in ipairs(Paths) do
                for j = 2, #path do
                    local s = path[j].s
                    if s and not Strategies.functions[s] then out[#out + 1] = i..":"..s end
                end
            end
            return table.concat(out, ", ")
        end)()''')
        self.assertEqual('', missing)

    def test_no_speedrun_strategy_is_left(self):
        # The speedrun's own fights and centers assume its Nidoran and its Pikachu.
        bot, _ = load_fork()
        used = bot.lua.eval('''(function()
            local out = {}
            for _, path in ipairs(Paths) do
                for j = 2, #path do if path[j].s then out[#out + 1] = path[j].s end end
            end
            return table.concat(out, " ")
        end)()''').split()
        allowed = {'take', 'dialogue', 'acquire', 'interact', 'dodgePalletBoy', 'ironmonFight', 'ironmonCenter',
                   'routeEnd'}
        self.assertEqual(set(), set(used) - allowed)
        self.assertEqual('routeEnd', [s for s in used if s != 'interact'][-1])


if __name__ == '__main__':
    unittest.main()

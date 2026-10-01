"""The story overlay (pokebot-story/) and run_pokebot's checkpoints, with fakes: no device needed.

    C:/Users/bepor/kcbot-venv/Scripts/python.exe -m unittest discover -s tests -v
"""
import os
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, HERE)

from kcbot.bizhawk import BizHawk   # noqa: E402
import run_pokebot                  # noqa: E402

STORY = [os.path.join(HERE, 'pokebot-story'), os.path.join(HERE, 'pokebot')]


class FakePort:
    def __init__(self):
        self.calls, self.resets, self.states = [], 0, 0

    def frame(self, buttons, ranges, frames=1):
        self.calls.append(tuple(buttons))
        return len(self.calls), [None]

    def reset(self):
        self.resets += 1

    def write(self, addr, data):
        return len(data)

    def state(self):
        self.states += 1
        return b'state%d' % self.states


def load_story():
    port = FakePort()
    bot = BizHawk(port, STORY, cgb=True, log=lambda *a: None)
    bot.lua.execute('''
        VERSION = "2.5.4"
        local Data = require "data.data"
        Data.init()
        Strategies = require("ai."..Data.gameName..".strategies")
        require "story"
    ''')
    return bot, port


class StoryTest(unittest.TestCase):
    def test_a_death_goes_back_to_the_runner_and_never_resets_the_console(self):
        bot, port = load_story()
        with self.assertRaises(Exception) as e:
            bot.lua.execute('Strategies.death()')
        self.assertIn('KCBOT_DIED', str(e.exception))
        bot.lua.execute('Strategies.reboot(); emu.frameadvance()')
        self.assertEqual(0, port.resets, 'the power button is never pressed')

    def test_stuck_is_a_death_too_and_the_win_is_its_own(self):
        bot, _ = load_story()
        with self.assertRaises(Exception) as e:
            bot.lua.execute('Strategies.reset("Stuck Detected", 25001, nil, true)')
        self.assertIn('KCBOT_DIED Stuck Detected', str(e.exception))
        with self.assertRaises(Exception) as e:
            bot.lua.execute('Strategies.hardReset("won", "Finished the game in 1:52:00")')
        self.assertIn('KCBOT_WON', str(e.exception))

    def test_a_game_under_way_finds_its_place_on_the_route(self):
        # PokeBot never called Walk.init, so a restart walked the first path wherever the player stood.
        main = open(os.path.join(HERE, 'pokebot-story', 'main.lua'), encoding='utf-8').read()
        self.assertIn('if hasAlreadyStartedPlaying then Walk.init() end', main)
        self.assertIn('require "story"', main)


class FakeWatch:
    """What Checkpoints reads: the map, the battle byte and whether the game proper has begun."""
    w = {'map': 0, 'battle': 1}

    def __init__(self):
        self.mem = [0, 0]
        self.ok = True

    def byte(self, off):
        return self.mem[off]

    def playing(self):
        return self.ok


class CheckpointsTest(unittest.TestCase):
    def setUp(self):
        self.port = FakePort()
        self.dir = tempfile.mkdtemp()
        self.c = run_pokebot.Checkpoints(self.port, os.path.join(self.dir, 'yellow-checkpoint.state'))
        self.w = FakeWatch()

    def frames(self, map_id, n=1, battle=0):
        self.w.mem = [map_id, battle]
        for _ in range(n):
            self.c.after_frame(self.w)

    def test_a_checkpoint_is_taken_once_on_the_frame_after_arriving(self):
        self.frames(0x26)                       # arriving: not yet
        self.assertEqual(0, self.port.states)
        self.frames(0x26, 50)                   # the next frame: once
        self.assertEqual(1, self.port.states)
        self.frames(0x27, 2)
        self.assertEqual([0x26, 0x27], [m for m, _ in self.c.saved])
        with open(os.path.join(self.dir, 'yellow-checkpoint.state'), 'rb') as f:
            self.assertEqual(b'state2', f.read(), 'the latest is on disk too')

    def test_none_in_battle_or_before_the_game_proper(self):
        self.frames(0x0C, 5, battle=1)
        self.w.ok = False
        self.frames(0x0D, 5)
        self.assertEqual(0, self.port.states)

    def test_three_deaths_at_one_checkpoint_go_back_one_more(self):
        self.frames(0x01, 2)
        self.frames(0x02, 2)
        self.assertEqual(0x02, self.c.back()[0])
        self.assertEqual(0x02, self.c.back()[0])
        self.assertEqual(0x02, self.c.back()[0])
        self.assertEqual(0x01, self.c.back()[0], 'the fourth death goes back a map')

    def test_only_the_last_five_are_kept(self):
        for m in range(1, 9):
            self.frames(m, 2)
        self.assertEqual([4, 5, 6, 7, 8], [m for m, _ in self.c.saved])


if __name__ == '__main__':
    unittest.main()

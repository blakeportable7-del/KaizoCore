"""Plays Pokémon Red or Yellow in KaizoCore with PokeBot, the speedrun bot, frame by frame through the test port.

    python run_pokebot.py red|yellow [--shots DIR] [--frames-per-call N]

Needs a debug build of KaizoCore on the device (a players' build has no test port) and the game in the library
as red-u or yellow-u. The bot opens the game from the library, where it resumes where it was last left.

While it plays it keeps a log line a minute (frames, calls a second, where it is, badges, team), takes a
screenshot of the whole phone screen (game and tracker) each time a badge is won or a gym or the Elite Four is
entered, and after the game is beaten. Those screenshots are the wiki's gameplay pictures.
"""
import argparse
import os
import re
import sys
import time

from kcbot.bizhawk import BizHawk
from kcbot.device import Device
from kcbot.port import NotRunning, Port
from kcbot.symbols import SYSTEM_RAM, Symbols

HERE = os.path.dirname(os.path.abspath(__file__))

# WRAM offsets per game, from pret's symbols (Yellow's layout is shifted from Red's). A party mon is 44 bytes:
# species first, level at +0x21.
def wram_offsets(game):
    sym = Symbols(game)
    off = lambda name: sym.port_address(name) - SYSTEM_RAM
    return {k: off(v) for k, v in dict(map='wCurMap', badges='wObtainedBadges', count='wPartyCount',
                                       mon1='wPartyMon1', battle='wIsInBattle', x='wXCoord', y='wYCoord').items()}

# Gym and Elite Four maps (pokered constants/map_constants.asm)
MILESTONE_MAPS = {
    0x36: 'pewter-gym', 0x41: 'cerulean-gym', 0x5C: 'vermilion-gym', 0x86: 'celadon-gym',
    0x9D: 'fuchsia-gym', 0xB2: 'saffron-gym', 0xA6: 'cinnabar-gym', 0x2D: 'viridian-gym',
    0xF5: 'lorelei', 0xF6: 'bruno', 0xF7: 'agatha', 0x71: 'lance', 0x78: 'champion',
}


class Watch:
    """Reads the bot's copy of WRAM after each frame: logs, and screenshots at the milestones."""

    def __init__(self, bot, device, shots, game):
        self.bot, self.device, self.shots, self.game = bot, device, shots, game
        self.w = wram_offsets(game)
        self.badges = None
        self.map = None
        self.steady = 0
        self.shot_map = None
        self.last_log = 0
        os.makedirs(shots, exist_ok=True)

    def byte(self, off):
        return self.bot.wram[off]

    def team(self):
        n = min(self.byte(self.w['count']), 6)
        out = []
        for i in range(n):
            base = self.w['mon1'] + i * 44
            out.append(f'#{self.byte(base)} L{self.byte(base + 0x21)}')
        return ' '.join(out)

    def shot(self, what):
        name = f'{self.game}-{time.strftime("%Y%m%d-%H%M%S")}-{what}.png'
        path = os.path.join(self.shots, name)
        self.device.screenshot(path)
        print('screenshot', path, flush=True)

    def playing(self):
        """In the game proper: a team of one to six, all of them real species. Before that (the title, a reset)
        WRAM holds leftovers, and a map byte of 0xF6 there is not Bruno's room."""
        n = self.byte(self.w['count'])
        return 1 <= n <= 6 and all(0 < self.byte(self.w['mon1'] + i * 44) < 0xBF for i in range(n))

    def stalled(self):
        """A finding when the player has not moved for five minutes of real time outside a battle: a bot stuck
        in a cutscene or against a wall. Logged once per stall, with a screenshot."""
        now = time.time()
        where = (self.byte(self.w['map']), self.byte(self.w['x']), self.byte(self.w['y']), self.byte(self.w['battle']))
        if where != getattr(self, 'last_where', None):
            self.last_where, self.moved_at, self.stall_logged = where, now, False
            return
        if not self.stall_logged and where[3] == 0 and now - self.moved_at > 300:
            self.stall_logged = True
            print(f'STALL: no movement for 5 minutes at map {where[0]:#04x} ({where[1]},{where[2]})', flush=True)
            self.shot(f'stall-map{where[0]:02x}')

    def after_frame(self):
        self.log_line()
        self.stalled()
        if not self.playing():
            self.steady = 0
            return
        badges = bin(self.byte(self.w['badges'])).count('1')
        cur = self.byte(self.w['map'])
        # A map counts once it has held for two seconds of the game's time: no shots from a warp's in-between.
        self.steady = self.steady + 1 if cur == self.map else 0
        if self.badges is None:
            self.badges = badges
        if badges > self.badges:
            self.shot(f'badge{badges}')
        if self.steady == 120 and cur in MILESTONE_MAPS and cur != self.shot_map:
            self.shot(MILESTONE_MAPS[cur])
            self.shot_map = cur
        self.badges, self.map = badges, cur

    def log_line(self):
        now = time.time()
        if now - self.last_log > 60:
            self.last_log = now
            cur = self.byte(self.w['map'])
            badges = bin(self.byte(self.w['badges'])).count('1')
            team = self.team() if self.playing() else '(not in the game yet)'
            print(f'frames {self.bot.frames}  {self.bot.rate():.0f} calls/s  map {cur:#04x} '
                  f'({self.byte(self.w['x'])},{self.byte(self.w['y'])})  badges {badges}  battle {self.byte(self.w['battle'])}  '
                  f'team {team}', flush=True)


def attach(d):
    """The port of an app that is already armed and running a game, or None. A bot restarted mid-game carries on
    without closing the game (PokeBot picks up from where the player stands)."""
    token = d.sh('cat /data/data/com.ironmonone.app/files/bot/token 2>/dev/null').strip()
    if not token:
        return None
    # A bot that died mid-call leaves the port busy for up to 5 seconds (its call times out, and a lockstep with
    # nobody calling hands the game back after 3), so give it a few tries.
    port = Port(token, port=d.direct_port() or 8650, timeout=12)
    for _ in range(3):
        try:
            a = port.ping()['frames']
            time.sleep(1)
            if port.ping()['frames'] > a:
                port.timeout = 120
                port.close()
                print('attached to the running game', flush=True)
                return port
        except Exception as e:
            print('attach: not yet,', type(e).__name__, flush=True)
            port.close()
            time.sleep(3)
    return None


# PokeBot's scripts with the story overlay searched first: a death reloads a checkpoint instead of starting over.
STORY = [os.path.join(HERE, 'pokebot-story'), os.path.join(HERE, 'pokebot')]
MAX_RELOADS = 60


class Checkpoints:
    """The game's state as the player arrives on each map, the last few kept. A death goes back to the latest; three
    deaths there, and the one before it. Arrival is the start of that map's path on PokeBot's route, so the reloaded
    game finds its place again (Walk.init, which pokebot-story/main.lua calls)."""
    KEEP = 5

    def __init__(self, port, path):
        self.port, self.path = port, path
        self.saved = []          # (map, state), oldest first
        self.pending = False
        self.last_map = None
        self.deaths = 0          # deaths since the latest checkpoint

    def after_frame(self, watch):
        if not watch.playing() or watch.byte(watch.w['battle']):
            return
        cur = watch.byte(watch.w['map'])
        if cur != self.last_map:
            # The first frame on a new map can still be the warp's; the state is taken on the next one.
            self.last_map, self.pending = cur, True
            return
        if self.pending:
            self.pending = False
            state = self.port.state()
            self.saved.append((cur, state))
            del self.saved[:-self.KEEP]
            self.deaths = 0
            with open(self.path, 'wb') as f:
                f.write(state)

    def back(self):
        self.deaths += 1
        if self.deaths > 3 and len(self.saved) > 1:
            self.saved.pop()
            self.deaths = 1
        return self.saved[-1] if self.saved else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('game', choices=['red', 'yellow'])
    ap.add_argument('--shots', default=os.path.join(HERE, 'shots'))
    ap.add_argument('--frames-per-call', type=int, default=1)
    ap.add_argument('--serial')
    a = ap.parse_args()

    d = Device(a.serial)
    port = attach(d)
    if port is None:
        token = d.arm()
        d.restart_app()
        if not d.open_library_game(f'{a.game}-u'):
            sys.exit(f'could not open {a.game}-u from the library')
        time.sleep(6)
        # On the emulator its own port redirect is quicker than adb forward; on a phone adb forward is the way.
        port = Port(token, port=d.direct_port() or 8650)
    print('app', d.version(), 'ping', port.ping(), flush=True)
    port.lock(True)
    os.environ.setdefault('KCBOT_RUNS', os.path.join(a.shots, 'runs.txt'))
    checkpoints = Checkpoints(port, os.path.join(a.shots, f'{a.game}-checkpoint.state'))
    watch = None
    reloads = 0
    try:
        while True:
            # A fresh Lua runtime for every start: PokeBot keeps its place on the route in module state.
            bot = BizHawk(port, STORY, cgb=(a.game == 'yellow'),
                          on_screenshot=lambda p: watch.shot('bot'), frames_per_call=a.frames_per_call)
            if watch is None:
                watch = Watch(bot, d, a.shots, a.game)
            watch.bot = bot

            def advance_and_watch(advance=bot.advance):
                advance()
                watch.after_frame()
                checkpoints.after_frame(watch)
            bot.advance = advance_and_watch

            try:
                bot.run('main.lua')
                break
            except NotRunning as e:
                print('the game stopped running:', e, flush=True)
                break
            except Exception as e:
                text = str(e)
                if 'KCBOT_WON' in text:
                    watch.shot('hall-of-fame')
                    print('the game is beaten:', text[-200:], flush=True)
                    break
                m = re.search(r'KCBOT_DIED ?(.*)', text)   # . stops at the end of the line
                if not m:
                    raise
                back = checkpoints.back()
                reloads += 1
                if back is None or reloads > MAX_RELOADS:
                    print(f'died ({m.group(1)}) with nothing to go back to after {reloads} reloads; stopping', flush=True)
                    break
                print(f'died ({m.group(1).strip()}); reloading the checkpoint on map {back[0]:#04x} '
                      f'(reload {reloads})', flush=True)
                port.load(back[1])
    finally:
        try:
            port.lock(False)
        except Exception:
            pass
        if watch is not None:
            print(f'stopped after {watch.bot.calls} calls, {watch.bot.frames} frames', flush=True)

if __name__ == '__main__':
    main()

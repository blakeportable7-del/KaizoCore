"""Kaizo IronMON on Pokémon Yellow, played by PokeBot under the Kaizo rules, one run after another.

    python run_ironmon_gen1.py [--runs N] [--log DIR] [--serial S]

Each run is a new seed the app rolls (Kaizo IronMON, Pokémon Yellow, Kaizo, Start attempt N; after a loss, New game
on the game-over popup). PokeBot plays it from the title screen with the rules in pokebot-ironmon/ironmon.lua: one
Pokémon, never fights a wild one, items only in battle, no banned moves, talks to Mom, heals only at the Viridian and
Pewter Centers. The route ends after Brock (most Kaizo runs end long before).

When the lead faints the bot stops and presses A through the battle's last lines, and the runner checks the app:

- the game-over popup appears, and its attempt number is one more than the last run's;
- what the popup says beat the player matches the game's own memory at the loss (the enemy's name and level);
- New game rolls the next seed, which boots to the title screen.

Every run is a line in runs.jsonl; anything wrong is a line in findings.jsonl with a screenshot. Crashes and ANRs
from the device's logs are findings too, and the app's memory is logged after every run.

Needs a debug build of the app (a players' build has no test port) and Pokémon Yellow prepared for Kaizo IronMON.
"""
import argparse
import json
import os
import re
import time

from kcbot.bizhawk import BizHawk
from kcbot.device import Device
from kcbot.port import NotRunning, Port
from kcbot.symbols import SYSTEM_RAM, Symbols
from run_pokebot import Watch

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPTS = [os.path.join(HERE, 'pokebot-ironmon'), os.path.join(HERE, 'pokebot')]

# The app's words, as its screens show them.
GAME_OVER = 'GAME OVER'
NEW_GAME = 'New game (new seed)'
NEW_RUN_YES = 'YES, NEW RUN'
GAME_NAME = 'Pokémon Yellow (U)'

# Gen 1's text encoding, enough for a Pokémon's name.
CHARS = {0x7F: ' ', 0xE0: "'", 0xE3: '-', 0xE8: '.', 0xEF: '♂', 0xF5: '♀', 0xF4: ','}
CHARS.update({0x80 + i: chr(ord('A') + i) for i in range(26)})
CHARS.update({0xA0 + i: chr(ord('a') + i) for i in range(26)})
CHARS.update({0xF6 + i: str(i) for i in range(10)})


def gb_text(data):
    out = ''
    for b in data:
        if b == 0x50:
            break
        out += CHARS.get(b, '?')
    return out


def squash(name):
    """A name for comparing: letters only, upper case ("MR.MIME", "Mr. Mime" -> "MRMIME")."""
    return re.sub(r'[^A-Z]', '', name.upper())


class Stuck(Exception):
    pass


class RunWatch(Watch):
    """Watch's log line, milestone shots and stall check, but no stall before the run has its Pokémon: the title,
    the names and Oak's talk hold the player still for minutes on end."""

    def stalled(self):
        if not self.playing():
            self.last_where = None
            return
        super().stalled()


class Runs:
    def __init__(self, device, log_dir, max_minutes):
        self.d = device
        self.log_dir = log_dir
        self.max_minutes = max_minutes
        os.makedirs(log_dir, exist_ok=True)
        self.sym = Symbols('yellow')
        self.last_attempt = None
        self.port = None
        self.run_no = 0

    # ---- records --------------------------------------------------------------------------------------------

    def record(self, name, **fields):
        fields['time'] = time.strftime('%Y-%m-%d %H:%M:%S')
        with open(os.path.join(self.log_dir, name), 'a', encoding='utf-8') as f:
            f.write(json.dumps(fields, ensure_ascii=False) + '\n')

    def shot(self, what):
        path = os.path.join(self.log_dir, f'{time.strftime("%Y%m%d-%H%M%S")}-{what}.png')
        try:
            self.d.screenshot(path)
            return path
        except Exception:
            return None

    def finding(self, what, **fields):
        print('FINDING:', what, fields, flush=True)
        self.record('findings.jsonl', what=what, screenshot=self.shot('finding'), **fields)

    def health(self):
        crashes = self.d.crashes()
        if 'com.ironmonone.app' in crashes:
            self.finding('crash', log=crashes[-4000:])
            self.d.clear_crashes()
        for line in self.d.anrs():
            self.finding('anr', line=line)
        self.d.adb('logcat', '-c')
        return self.d.memory_kb()

    # ---- the game's memory ------------------------------------------------------------------------------------

    def off(self, name):
        return self.sym.port_address(name) - SYSTEM_RAM

    def snapshot(self, wram):
        """What the game says at this moment: the lead, the enemy in battle, the kind of battle."""
        b = lambda name, n=1: wram[self.off(name):self.off(name) + n]
        return {
            'battle': b('wIsInBattle')[0],
            'enemy': gb_text(b('wEnemyMonNick', 11)),
            'enemy_level': b('wEnemyMonLevel')[0],
            'lead': gb_text(b('wPartyMonNicks', 11)),
            'lead_level': wram[self.off('wPartyMon1') + 0x21],
            'map': b('wCurMap')[0],
            'badges': bin(b('wObtainedBadges')[0]).count('1'),
            'party': b('wPartyCount')[0],
        }

    # ---- the app ----------------------------------------------------------------------------------------------

    def texts(self):
        return [t.strip() for t, _, _ in self.d.ui() if t.strip()]

    def popup_texts(self):
        texts = self.texts()
        return texts if GAME_OVER in texts else None

    def read_popup(self, texts):
        attempt = None
        for i, t in enumerate(texts):
            if t.upper() == 'ATTEMPT' and i + 1 < len(texts) and texts[i + 1].isdigit():
                attempt = int(texts[i + 1])
        headline = None
        for i, t in enumerate(texts):
            if t in ('LOST TO', 'FIRST LOSS', 'ENDED ON') and i + 1 < len(texts):
                headline = (t, texts[i + 1])
        return attempt, headline

    def wait_for_popup(self, seconds=150):
        """The lead has fainted: press A through the battle's last lines and the blackout until the app's popup
        shows. The bot is off the game by now, so it runs at the player's speed."""
        end = time.time() + seconds
        while time.time() < end:
            texts = self.popup_texts()
            if texts:
                return texts
            try:
                for _ in range(6):
                    self.port.press('A', hold=4, after=20)
            except NotRunning:
                time.sleep(1)
        return None

    def new_seed_from_popup(self):
        if not self.d.tap_text(NEW_GAME, exact=True, wait=2):
            return False
        return self.d.tap_text(NEW_RUN_YES, exact=True, wait=2)

    def start_attempt_from_run_screen(self):
        """Kaizo IronMON, Pokémon Yellow, Kaizo, Start attempt N, and Start attempt N again in the box that asks
        before ending the run in play."""
        self.d.tap_text('Home', exact=True)
        if not self.d.tap_text('Kaizo IronMON', wait=2):
            return False
        for _ in range(3):
            self.d.swipe_down()
        # Exact: the line under Continue names the Library game, "Play is on Pokémon Yellow (U) from the Library."
        for _ in range(10):
            if self.d.tap_text(GAME_NAME, exact=True, wait=1.5):
                break
            self.d.swipe_up()
        else:
            return False
        for _ in range(6):
            if self.d.tap_text('Kaizo', exact=True, wait=1.5):
                break
            self.d.swipe_up()
        else:
            return False
        starts = [(t, b) for t, _, b in self.d.ui() if t.strip().startswith('Start attempt')]
        if not starts:
            return False
        t, b = max(starts, key=lambda s: s[1][1])
        self.d.tap((b[0] + b[2]) // 2, (b[1] + b[3]) // 2, wait=2)
        # The question, when a run is in play: its own Start attempt button sits above the footer's.
        starts = [(t, b) for t, _, b in self.d.ui() if t.strip().startswith('Start attempt')]
        if len(starts) > 1:
            t, b = min(starts, key=lambda s: s[1][1])
            self.d.tap((b[0] + b[2]) // 2, (b[1] + b[3]) // 2, wait=2)
        return True

    def wait_for_new_game(self, seconds=300):
        """The new seed has booted: the game runs and its party is empty (a game that was just lost has one).
        The randomizer takes a while on a phone, and the app opens Play when it is done."""
        sym_count = self.off('wPartyCount')
        end = time.time() + seconds
        while time.time() < end:
            try:
                a = self.port.ping()['frames']
                count = self.port.mem(SYSTEM_RAM + sym_count, 1)
                time.sleep(1)
                if self.port.ping()['frames'] > a and count and count[0] == 0:
                    return True
            except Exception:
                pass
            time.sleep(2)
        return False

    # ---- one run ----------------------------------------------------------------------------------------------

    def play(self):
        """PokeBot from the title screen to the run's end. Returns (how, reason, the game's memory at the end)."""
        self.port.lock(True)
        bot = BizHawk(self.port, SCRIPTS, cgb=True, log=lambda *a: print(*a, flush=True))
        os.environ['KCBOT_RUNS'] = os.path.join(self.log_dir, 'pokebot-runs.txt')
        watch = RunWatch(bot, self.d, self.log_dir, 'yellow')
        started = time.time()
        advance = bot.advance

        def advance_and_watch():
            advance()
            watch.after_frame()
            if getattr(watch, 'stall_logged', False):
                raise Stuck('no movement outside battle for 5 minutes')
            if time.time() - started > self.max_minutes * 60:
                raise Stuck(f'the run passed {self.max_minutes} minutes')
        bot.advance = advance_and_watch

        how, reason = 'error', ''
        try:
            bot.run('main.lua')
            how = 'returned'
        except Stuck as e:
            how, reason = 'stuck', str(e)
        except NotRunning as e:
            how, reason = 'not-running', str(e)
        except Exception as e:
            # Lua's own errors, and a Python one raised under it that came back as a Lua error.
            text = str(e)
            m = re.search(r'KCBOT_RUN_OVER ?([^\n]*)', text)
            if m:
                how, reason = ('lost' if m.group(1).startswith('death') else 'over'), m.group(1).strip()
            elif 'KCBOT_ROUTE_END' in text:
                how = 'route-end'
            elif 'Stuck' in text:
                how, reason = 'stuck', text[-300:]
            elif 'NotRunning' in text:
                how, reason = 'not-running', text[-300:]
            else:
                how, reason = 'error', text[-1500:]
        finally:
            try:
                self.port.lock(False)
            except Exception:
                pass
        end = self.snapshot(bot.wram)
        end['frames'] = bot.calls
        end['minutes'] = round((time.time() - started) / 60, 1)
        return how, reason, end

    def one_run(self):
        self.run_no += 1
        t0 = time.time()
        how, reason, end = self.play()
        rec = {'run': self.run_no, 'how': how, 'reason': reason, 'game': end}
        print('run', self.run_no, how, reason, end, flush=True)

        if how == 'lost':
            texts = self.wait_for_popup()
            if not texts:
                self.finding('no game-over popup after the lead fainted', run=self.run_no, game=end)
                rec['popup'] = None
                next_by = 'run-screen'
            else:
                attempt, headline = self.read_popup(texts)
                rec['attempt'], rec['headline'] = attempt, headline
                rec['popup'] = ' | '.join(texts)[:800]
                rec['popup_shot'] = self.shot(f'attempt{attempt}-gameover')
                if attempt is None:
                    self.finding('the popup shows no attempt number', popup=rec['popup'])
                elif self.last_attempt is not None and attempt != self.last_attempt + 1:
                    self.finding('attempt did not go up by one', was=self.last_attempt, now=attempt)
                self.last_attempt = attempt
                # What beat the player, by the game: the enemy in battle when the lead fainted.
                if end['battle'] and end['enemy']:
                    said = headline[1] if headline else ''
                    if f"Lv.{end['enemy_level']} " not in said or squash(end['enemy']) not in squash(said):
                        self.finding('the popup names another killer than the game',
                                     game=f"Lv.{end['enemy_level']} {end['enemy']}", popup=said)
                next_by = 'popup'
        else:
            # The run is not lost (Brock beaten, or the bot stuck): the run screen's Start attempt ends it.
            rec['shot'] = self.shot(how)
            if how in ('stuck', 'over', 'error', 'not-running'):
                self.finding(f'run ended without a loss: {how}', run=self.run_no, reason=reason, game=end)
            next_by = 'run-screen'

        rec['memory_kb'] = self.health()
        rec['seconds'] = round(time.time() - t0, 1)
        self.record('runs.jsonl', **rec)

        ok = self.new_seed_from_popup() if next_by == 'popup' else self.start_attempt_from_run_screen()
        if not ok:
            self.finding('could not start the next seed', via=next_by, screen=' | '.join(self.texts())[:600])
            return False
        if not self.wait_for_new_game():
            self.finding('the next seed did not boot', via=next_by, screen=' | '.join(self.texts())[:600])
            return False
        if next_by == 'run-screen':
            self.last_attempt = None   # a run that was not lost may count no attempt; start the count again
        return True


def connect(d):
    token = d.sh('cat /data/data/com.ironmonone.app/files/bot/token 2>/dev/null').strip() or d.arm()
    return Port(token, port=d.direct_port() or 8650, timeout=120)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--runs', type=int, default=0, help='stop after this many runs (0: never)')
    ap.add_argument('--log', default='C:/Users/bepor/KaizoCore-bot-shots/ironmon-yellow')
    ap.add_argument('--max-minutes', type=int, default=60, help='a run longer than this ends as stuck')
    ap.add_argument('--start', action='store_true', help='start a new attempt first (else play the game on screen)')
    ap.add_argument('--serial')
    a = ap.parse_args()

    d = Device(a.serial)
    runs = Runs(d, a.log, a.max_minutes)
    runs.port = connect(d)
    print('app', d.version(), flush=True)
    if a.start:
        if not runs.start_attempt_from_run_screen() or not runs.wait_for_new_game():
            raise SystemExit('could not start a Kaizo attempt on Pokémon Yellow: ' + ' | '.join(runs.texts())[:600])
    done = 0
    while a.runs == 0 or done < a.runs:
        if not runs.one_run():
            print('could not go on to the next run; trying the run screen in 10 seconds', flush=True)
            time.sleep(10)
            if not runs.start_attempt_from_run_screen() or not runs.wait_for_new_game():
                raise SystemExit('stopped: the app would not start another attempt')
        done += 1


if __name__ == '__main__':
    main()

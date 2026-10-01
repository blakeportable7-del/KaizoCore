"""The IronMON soak: Kaizo IronMON runs on a Gen 3 game, one after another, for as long as it is left running.

    python run_ironmon_soak.py firered-v11 [--runs N] [--log DIR]

Each run: the new seed boots, the bot continues from the intro-skip save (the run's in-game save with no Pokémon
in it, kept by the app across runs), picks a starter, fights until the run is lost, and checks the app:

- the game-over popup appears, and its attempt number is one more than the last run's;
- what the popup says beat the player matches the game's own memory at the moment of the loss;
- NEW RUN brings up the next seed.

Every run is a line in runs.jsonl, with the app's memory, and anything wrong is a line in findings.jsonl with a
screenshot. Crashes and ANRs from the device's logs are findings too.

Needs a debug build of the app, the game in the library, and the run's intro-skip save already in place (make it
once: a new game played to just before choosing the starter, saved in the game).
"""
import argparse
import json
import os
import re
import sys
import time

from kcbot.device import Device
from kcbot.gen3 import Gen3
from kcbot.port import NotRunning, Port

# The app's words, as its screens show them.
GAME_OVER = 'GAME OVER'
NEW_GAME = 'New game (new seed)'
NEW_RUN_YES = 'YES, NEW RUN'

# Library titles the Kaizo IronMON screen lists games by (its cards show the game's name).
GAME_CARD = {'firered-v11': 'FireRed (U) v1.1', 'emerald': 'Emerald (U)', 'leafgreen': 'LeafGreen (U)'}


class Soak:
    def __init__(self, device, port, game, log_dir):
        self.d, self.port, self.game = device, port, game
        self.g3 = Gen3(port, game)
        self.log_dir = log_dir
        os.makedirs(log_dir, exist_ok=True)
        self.last_attempt = None
        self.run_no = 0

    # ---- records --------------------------------------------------------------------------------------------

    def record(self, name, **fields):
        fields['time'] = time.strftime('%Y-%m-%d %H:%M:%S')
        with open(os.path.join(self.log_dir, name), 'a', encoding='utf-8') as f:
            f.write(json.dumps(fields) + '\n')

    def finding(self, what, **fields):
        shot = os.path.join(self.log_dir, f'finding-{time.strftime("%Y%m%d-%H%M%S")}.png')
        try:
            self.d.screenshot(shot)
        except Exception:
            shot = None
        print('FINDING:', what, fields, flush=True)
        self.record('findings.jsonl', what=what, screenshot=shot, **fields)

    def health(self):
        crashes = self.d.crashes()
        if 'com.ironmonone.app' in crashes:
            self.finding('crash', log=crashes[-4000:])
            self.d.clear_crashes()
        for line in self.d.anrs():
            self.finding('anr', line=line)
        self.d.adb('logcat', '-c')
        return self.d.memory_kb()

    # ---- the game -------------------------------------------------------------------------------------------

    def press(self, *keys, hold=6, after=10):
        self.port.press(*keys, hold=hold, after=after)

    def wait_state(self, want, seconds=60, poke=None):
        """Waits for the game's screen to be one of `want`, pressing `poke` now and then to move text along."""
        end = time.time() + seconds
        while time.time() < end:
            s = self.g3.state()
            if s in want:
                return s
            if poke:
                self.press(poke)
            else:
                time.sleep(0.3)
        return None

    def continue_from_title(self):
        """The title screen, then CONTINUE on the main menu: the intro-skip save."""
        for _ in range(40):
            s = self.g3.state()
            if s == 'overworld':
                return True
            if s in ('title', 'main-menu'):
                self.press('A', after=20)
            else:
                self.press('A', after=20)
        return self.g3.state() == 'overworld'

    def pick_starter(self):
        """Faces the table and takes the ball in front, answers yes, declines a nickname, and plays until the
        player holds a Pokémon. The intro-skip save stands in front of a ball."""
        for _ in range(60):
            if self.g3.party():
                return True
            self.press('UP', after=4)
            self.press('A', after=30)
        # still no Pokémon: try the answers the prompts want (YES is A, the nickname NO is B)
        for _ in range(40):
            if self.g3.party():
                return True
            self.press('B', after=20)
        return bool(self.g3.party())

    def fight_until_done(self, seconds=600):
        """In battle, press A: FIGHT, then the move under the cursor, and through the text. Out of battle, walk
        in the grass or toward the exit so the next battle comes. Stops when the app's game-over popup shows."""
        end = time.time() + seconds
        step = 0
        last_battle = None
        while time.time() < end:
            if self.popup_up():
                return last_battle
            s = self.g3.state()
            if s == 'battle':
                try:
                    last_battle = self.g3.battle_mons()
                except Exception:
                    pass
                self.press('A', after=12)
            elif s == 'overworld':
                # back and forth: down out of the lab and on through the grass
                self.port.press(('DOWN', 'LEFT', 'DOWN', 'RIGHT')[step % 4], hold=16, after=4)
                step += 1
            else:
                self.press('A', after=12)
        return last_battle

    # ---- the app ----------------------------------------------------------------------------------------------

    def screen_texts(self):
        return [t.strip() for t, _, _ in self.d.ui() if t.strip()]

    def popup_up(self):
        if time.time() - getattr(self, '_last_ui', 0) < 3:
            return False
        self._last_ui = time.time()
        return any(t == GAME_OVER for t in self.screen_texts())

    def read_popup(self):
        texts = self.screen_texts()
        joined = ' | '.join(texts)
        attempt = None
        for i, t in enumerate(texts):
            if t.upper().startswith('ATTEMPT'):
                m = re.search(r'(\d+)', t) or (re.search(r'(\d+)', texts[i + 1]) if i + 1 < len(texts) else None)
                if m:
                    attempt = int(m.group(1))
                    break
        return attempt, joined

    def new_run(self):
        if not self.d.tap_text(NEW_GAME, exact=True):
            return False
        time.sleep(1.5)
        if not self.d.tap_text(NEW_RUN_YES, exact=True):
            return False
        return True

    # ---- one run ----------------------------------------------------------------------------------------------

    def one_run(self):
        self.run_no += 1
        t0 = time.time()
        rec = {'run': self.run_no}
        if not self.continue_from_title():
            self.finding('did not reach the overworld from the title', run=self.run_no, state=self.g3.state())
            return False
        if not self.pick_starter():
            self.finding('no starter after the lab', run=self.run_no, state=self.g3.state())
            return False
        rec['starter'] = self.g3.party()[0]
        last = self.fight_until_done()
        rec['last_battle'] = last
        if not self.popup_up() and not any(t == GAME_OVER for t in self.screen_texts()):
            self.finding('no game-over popup within the time', run=self.run_no, last_battle=last)
            return False
        attempt, text = self.read_popup()
        rec['attempt'] = attempt
        rec['popup'] = text[:800]
        if self.last_attempt is not None and attempt is not None and attempt != self.last_attempt + 1:
            self.finding('attempt did not go up by one', was=self.last_attempt, now=attempt)
        self.last_attempt = attempt
        # what beat the player, by the game: the enemy battler in the last battle
        if last and last[1]['species']:
            rec['beaten_by_level'] = last[1]['level']
            if f"Lv.{last[1]['level']}" not in text and f"Lv. {last[1]['level']}" not in text:
                self.finding('popup level differs from the game', run=self.run_no, game=last[1], popup=text[:400])
        rec['seconds'] = round(time.time() - t0, 1)
        rec['memory_kb'] = self.health()
        if not self.new_run():
            self.finding('could not start the next run from the popup', run=self.run_no, popup=text[:400])
            return False
        rec['new_run_tapped'] = True
        self.record('runs.jsonl', **rec)
        print('run', rec, flush=True)
        # the next seed boots
        time.sleep(5)
        return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('game', choices=sorted(GAME_CARD))
    ap.add_argument('--runs', type=int, default=0, help='stop after this many runs (0: never)')
    ap.add_argument('--log', default='C:/Users/bepor/KaizoCore-bot-shots/soak')
    a = ap.parse_args()

    d = Device()
    token = d.sh('cat /data/data/com.ironmonone.app/files/bot/token 2>/dev/null').strip() or d.arm()
    port = Port(token, port=d.direct_port() or 8650)
    soak = Soak(d, port, a.game, a.log)
    done = 0
    while a.runs == 0 or done < a.runs:
        try:
            ok = soak.one_run()
        except NotRunning as e:
            soak.finding('the game stopped running', error=str(e))
            ok = False
        if not ok:
            print('run did not complete; waiting before the next try', flush=True)
            time.sleep(10)
        done += 1


if __name__ == '__main__':
    main()

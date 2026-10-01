"""The phone or emulator the bot plays on: adb, the app, and the things a long run watches.

One adb driver at a time: nothing else may drive the same device while a bot runs.
"""
import os
import re
import secrets
import subprocess
import time

ADB = os.environ.get('KCBOT_ADB', 'C:/Users/bepor/Android/Sdk/platform-tools/adb.exe')
PACKAGE = 'com.ironmonone.app'
FILES = f'/data/data/{PACKAGE}/files'
PORT = 8650


class Device:
    def __init__(self, serial=None):
        self.serial = serial
        self.env = dict(os.environ, MSYS2_ARG_CONV_EXCL='*')

    # ---- adb ----------------------------------------------------------------------------------------------

    def adb(self, *args, timeout=60, check=False):
        cmd = [ADB] + (['-s', self.serial] if self.serial else []) + list(args)
        r = subprocess.run(cmd, capture_output=True, timeout=timeout, env=self.env)
        if check and r.returncode != 0:
            raise RuntimeError(f'adb {" ".join(args)}: {r.stderr.decode("utf-8", "replace")[:300]}')
        return r

    def sh(self, command, timeout=60):
        """A shell command on the device (root on the emulator), its output as text."""
        return self.adb('shell', command, timeout=timeout).stdout.decode('utf-8', 'replace')

    def app_uid(self):
        # "userId=10211" on older Android, "appId=10211" on Android 14.
        m = re.search(r'(?:userId|appId)=(\d+)', self.sh(f'dumpsys package {PACKAGE}'))
        return int(m.group(1)) if m else None

    # ---- the bot's port -----------------------------------------------------------------------------------

    def arm(self):
        """Write files/bot/enabled and a fresh token, restart the app so the port starts, forward the port.
        Returns the token. Needs a debug build (a players' build has no port) and a root shell."""
        token = secrets.token_hex(16)
        uid = self.app_uid()
        self.sh(f'mkdir -p {FILES}/bot && echo {token} > {FILES}/bot/token && touch {FILES}/bot/enabled'
                f' && chown -R {uid}:{uid} {FILES}/bot && chmod 700 {FILES}/bot && chmod 600 {FILES}/bot/*'
                f' && restorecon -RF {FILES}/bot')
        self.adb('forward', f'tcp:{PORT}', f'tcp:{PORT}', check=True)
        return token

    def is_emulator(self):
        return (self.serial or '').startswith('emulator-') or self.sh('getprop ro.hardware').strip() in ('ranchu', 'goldfish')

    def direct_port(self, host_port=8651):
        """On the emulator only: its console's port redirect to the port (a round trip there is a fraction of adb
        forward's). Returns the host port to connect to, or None when this is not an emulator."""
        if not self.is_emulator():
            return None
        self.adb('emu', 'redir', 'del', f'tcp:{host_port}')
        r = self.adb('emu', 'redir', 'add', f'tcp:{host_port}:{PORT}')
        return host_port if r.returncode == 0 else None

    def disarm(self):
        self.sh(f'rm -rf {FILES}/bot')
        self.adb('forward', '--remove', f'tcp:{PORT}')

    # ---- the app ------------------------------------------------------------------------------------------

    def start_app(self, wait=8):
        self.adb('shell', 'am', 'start', '-n', f'{PACKAGE}/.MainActivity')
        time.sleep(wait)

    def stop_app(self):
        self.adb('shell', 'am', 'force-stop', PACKAGE)

    def restart_app(self, wait=9):
        self.stop_app()
        time.sleep(1)
        self.start_app(wait)

    def pid(self):
        out = self.sh(f'pidof {PACKAGE}').strip()
        return int(out.split()[0]) if out else None

    def version(self):
        m = re.search(r'versionName=(\S+)', self.sh(f'dumpsys package {PACKAGE}'))
        return m.group(1) if m else None

    # ---- what a long run watches --------------------------------------------------------------------------

    def crashes(self):
        """The crash buffer (Java and native crashes of any app); clear it after reading with clear_crashes()."""
        return self.adb('logcat', '-b', 'crash', '-d').stdout.decode('utf-8', 'replace')

    def clear_crashes(self):
        self.adb('logcat', '-b', 'crash', '-c')

    def anrs(self):
        """App-not-responding lines for this app from the main log since it was last cleared."""
        out = self.adb('logcat', '-d', '-s', 'ActivityManager:E').stdout.decode('utf-8', 'replace')
        return [l for l in out.splitlines() if 'ANR in' in l and PACKAGE in l]

    def memory_kb(self):
        """The app's total PSS in kB, or None when it is not running."""
        m = re.search(r'TOTAL PSS:\s+(\d+)', self.sh(f'dumpsys meminfo {PACKAGE}'))
        if not m:
            m = re.search(r'TOTAL\s+(\d+)', self.sh(f'dumpsys meminfo {PACKAGE}'))
        return int(m.group(1)) if m else None

    def screenshot(self, path):
        png = self.adb('exec-out', 'screencap', '-p', timeout=30).stdout
        with open(path, 'wb') as f:
            f.write(png)
        return path

    # ---- the app's screens, by their words ----------------------------------------------------------------

    def ui(self):
        """Every visible element as (text, content description, (x1, y1, x2, y2))."""
        self.sh('uiautomator dump /sdcard/kcbot-ui.xml')
        xml = self.sh('cat /sdcard/kcbot-ui.xml')
        out = []
        for m in re.finditer(r'<node [^>]*>', xml):
            n = m.group(0)
            t = re.search(r' text="([^"]*)"', n)
            d = re.search(r'content-desc="([^"]*)"', n)
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
            if t and d and b:
                out.append((t.group(1), d.group(1), tuple(map(int, b.groups()))))
        return out

    def tap_text(self, label, exact=False, wait=1.5):
        for t, d, b in self.ui():
            for s in (t, d):
                s2 = s.strip()
                if s2 and ((s2 == label) if exact else (label.lower() in s2.lower())):
                    self.adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
                    time.sleep(wait)
                    return True
        return False

    def tap(self, x, y, wait=1.0):
        self.adb('shell', 'input', 'tap', str(x), str(y))
        time.sleep(wait)

    def swipe_up(self, wait=1.2):
        """Scrolls the content down (the finger moves up)."""
        self.adb('shell', 'input', 'swipe', '540', '1700', '540', '800', '400')
        time.sleep(wait)

    def swipe_down(self, wait=1.2):
        """Scrolls the content back up (the finger moves down)."""
        self.adb('shell', 'input', 'swipe', '540', '800', '540', '1700', '400')
        time.sleep(wait)

    def open_library_game(self, name, tries=8):
        """Home, Play any game, then the Play button on the card whose title is `name` (the file's name, e.g.
        'red-u'). The game opens where it was last left. True when the Play button was found and tapped."""
        self.tap_text('Home', exact=True)
        self.tap_text('Play any game')
        time.sleep(2)
        for _ in range(tries):
            nodes = self.ui()
            # A card's title is the file's name, "  (playing)" after it for the game in progress.
            titles = [b for t, d, b in nodes if t.strip().split('  (')[0].strip() == name]
            # The card's own Play button: on the left, and above the bottom bar, whose Play tab must never count.
            plays = [b for t, d, b in nodes if t.strip() == 'Play' and b[0] < 300 and b[3] < 2100]
            for tb in titles:
                below = [pb for pb in plays if 0 < pb[1] - tb[3] < 260]
                if below:
                    pb = min(below, key=lambda b: b[1])
                    self.tap((pb[0] + pb[2]) // 2, (pb[1] + pb[3]) // 2, wait=4)
                    return True
            self.swipe_up()
        return False

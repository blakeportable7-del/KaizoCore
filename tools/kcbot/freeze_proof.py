"""Proof for the AYN Thor freeze (2026-09-30): the app must never stop responding when its emulation thread does.

Holds the emulation thread for 8 seconds through the test port, taps File > Save state straight after (the main
thread then asks the emulation thread for a snapshot), and taps the screen again while it waits, which is when
Android starts its 5-second input clock. Afterwards it looks for Android's "ANR in com.ironmonone.app".

    python freeze_proof.py [apk]          install apk first (a debug build), else use what is installed

With the fix the save gives up after 2 seconds ("Save failed - game not running.") and no ANR is logged. With the
old unbounded wait the main thread waits the full 8 seconds and Android logs an ANR.
"""
import sys
import time

from kcbot.device import Device
from kcbot.port import Port

GAME = 'red-u'
STALL_MS = 8000


def main():
    d = Device()
    if len(sys.argv) > 1:
        r = d.adb('install', '-r', sys.argv[1], timeout=300)
        print('install:', r.stdout.decode().strip().splitlines()[-1:])
    print('app', d.version())
    token = d.arm()
    d.restart_app()
    if not d.open_library_game(GAME):
        sys.exit('could not open ' + GAME)
    time.sleep(6)
    port = Port(token)
    p1 = port.ping()
    time.sleep(1)
    p2 = port.ping()
    print('running:', p1['frames'], '->', p2['frames'])
    if p2['frames'] <= p1['frames']:
        sys.exit('the game is not running')

    d.adb('logcat', '-c')
    d.tap(963, 219, wait=1.2)                     # File
    save = next(((t, b) for t, _, b in d.ui() if t.strip() == 'Save state'), None)
    if save is None:
        sys.exit('no Save state button in the File menu')
    sx, sy = (save[1][0] + save[1][2]) // 2, (save[1][1] + save[1][3]) // 2

    port.stall(STALL_MS)
    time.sleep(0.2)
    t0 = time.time()
    d.adb('shell', 'input', 'tap', str(sx), str(sy))      # the main thread now waits on the emulation thread
    time.sleep(0.5)
    d.adb('shell', 'input', 'tap', '540', '400')           # an input event while it waits
    time.sleep(STALL_MS / 1000 + 6)

    log = d.adb('logcat', '-d').stdout.decode('utf-8', 'replace')
    anr = [l for l in log.splitlines() if 'ANR in com.ironmonone.app' in l]
    gave_up = [l for l in log.splitlines() if 'did not answer in time' in l]
    ui = ' | '.join(t for t, _, _ in d.ui() if t.strip())
    print('seconds:', round(time.time() - t0, 1))
    print('ANR lines:', anr or 'none')
    print('gave-up lines:', len(gave_up))
    print('screen says "Save failed":', 'Save failed' in ui)
    d.screenshot('freeze-proof.png')
    p3 = port.ping()
    print('game running again:', p3['frames'] > p2['frames'])
    print('RESULT:', 'NO FREEZE' if not anr else 'FROZE (ANR)')


if __name__ == '__main__':
    main()

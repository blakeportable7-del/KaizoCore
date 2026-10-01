"""Client for the app's test port (app/src/debug, bot/BotPort.kt).

The port runs inside a debug build of KaizoCore, on 127.0.0.1:8650 of the phone or emulator, reached through
`adb forward tcp:8650 tcp:8650`. It reads and writes the game's memory and holds buttons for an exact number of
the game's frames. See BotPort.kt for the routes.

Addresses are the app's bus addresses: 0x02000000 + offset is the core's system RAM (GBA EWRAM, DS main RAM,
Game Boy WRAM with bank n at 0x1000 * n); other addresses go through the core's memory map (GBA IWRAM at
0x03000000, and so on).
"""
import http.client
import json
import struct


class NotRunning(Exception):
    """The game is not running: no game open, or the app in the background."""


BUTTONS = ('A', 'B', 'X', 'Y', 'L', 'R', 'START', 'SELECT', 'UP', 'DOWN', 'LEFT', 'RIGHT')


class Port:
    def __init__(self, token, host='127.0.0.1', port=8650, timeout=120):
        self.token = token
        self.host = host
        self.port = port
        self.timeout = timeout
        self._conn = None

    # ---- plumbing -----------------------------------------------------------------------------------------

    def _connection(self):
        if self._conn is None:
            self._conn = http.client.HTTPConnection(self.host, self.port, timeout=self.timeout)
        return self._conn

    def _call(self, method, path, params=None, body=None):
        q = '&'.join(f'{k}={v}' for k, v in (params or {}).items() if v is not None)
        url = path + ('?' + q if q else '')
        headers = {'X-Bot-Token': self.token}
        for attempt in (1, 2):
            conn = self._connection()
            try:
                conn.request(method, url, body=body, headers=headers)
                resp = conn.getresponse()
                data = resp.read()
                break
            except (ConnectionError, http.client.HTTPException, OSError):
                # A dropped keep-alive connection (the app restarted, adb forward came back): one fresh try.
                self.close()
                if attempt == 2:
                    raise
        if resp.status == 503:
            raise NotRunning(data.decode('utf-8', 'replace'))
        if resp.status != 200:
            raise RuntimeError(f'{method} {path}: {resp.status} {data[:200]!r}')
        return data

    def close(self):
        if self._conn is not None:
            try:
                self._conn.close()
            finally:
                self._conn = None

    @staticmethod
    def _parts(data, count, at=0):
        out = []
        for _ in range(count):
            (n,) = struct.unpack_from('>i', data, at)
            at += 4
            if n < 0:
                out.append(None)
            else:
                out.append(data[at:at + n])
                at += n
        return out

    @staticmethod
    def _ranges(ranges):
        return ','.join(f'{a:x}:{n}' for a, n in ranges)

    # ---- the routes ---------------------------------------------------------------------------------------

    def ping(self):
        return json.loads(self._call('GET', '/ping'))

    def mem(self, addr, n):
        """n bytes at addr, read between two frames. None when unmapped."""
        data = self._call('GET', '/mem', {'a': f'{addr:x}', 'n': n})
        return data if data else None

    def mems(self, ranges):
        """Several (addr, n) ranges read in the same gap between frames; None for an unmapped one."""
        data = self._call('GET', '/mems', {'r': self._ranges(ranges)})
        return self._parts(data, len(ranges))

    def write(self, addr, data):
        return json.loads(self._call('POST', '/mem', {'a': f'{addr:x}'}, bytes(data)))['wrote']

    def press(self, *buttons, hold=6, after=2):
        """Hold the buttons for `hold` of the game's frames, release, then wait `after` frames."""
        keys = ','.join(b.upper() for b in buttons)
        return json.loads(self._call('POST', '/press', {'k': keys, 'f': hold, 'r': after}))['frames']

    def wait(self, frames):
        return json.loads(self._call('POST', '/wait', {'f': frames}))['frames']

    def touch(self, x, y, hold=8, after=2):
        """Touch the game view at normalized x, y (-1..1 across the whole view) for `hold` frames."""
        return json.loads(self._call('POST', '/touch', {'x': f'{x:.4f}', 'y': f'{y:.4f}', 'f': hold, 'r': after}))['frames']

    def frame(self, buttons=(), ranges=(), frames=1):
        """Hold exactly `buttons`, then read `ranges`: returns (frame count, [bytes per range]).
        In lockstep (lock()) this runs exactly `frames` frames with the buttons and reads after them; otherwise
        the buttons hold from the next frame on and memory is read as it stands."""
        data = self._call('POST', '/frame', {'k': ','.join(b.upper() for b in buttons) or None,
                                             'r': self._ranges(ranges) or None,
                                             'f': frames if frames != 1 else None})
        (count,) = struct.unpack_from('>q', data, 0)
        return count, self._parts(data, len(ranges), 8)

    def state(self):
        """The bot's own state of the game (not one of the player's slots)."""
        return self._call('GET', '/state')

    def load(self, state):
        return json.loads(self._call('POST', '/state', body=bytes(state)))['loaded']

    def lock(self, on=True):
        """Lockstep on or off: while on, the game runs only the frames this bot asks for with frame()."""
        return json.loads(self._call('POST', '/lock', {'on': 1 if on else 0}))['locked']

    def reset(self):
        """The console's reset button: back to the title screen. The in-game save stays."""
        return json.loads(self._call('POST', '/reset'))

    def stall(self, ms):
        """Hold the emulation thread for ms milliseconds (tests that the app never freezes waiting on it)."""
        return json.loads(self._call('POST', '/stall', {'ms': ms}))

"""Records the game's raw sound from KaizoCore's stream (/game.ws): the core's own PCM, before the resampler.

    python ws_record.py <token> <seconds> <out.pcm>   (adb forward tcp:8642 tcp:8642 first)

The wire (GameStream.kt, GameWire): binary messages start with 8 bytes, tag 2 for sound, the sample rate as a
little-endian u32 at 4, then interleaved 16-bit stereo.
"""
import base64
import os
import socket
import struct
import sys
import time

token, seconds, out = sys.argv[1], float(sys.argv[2]), sys.argv[3]
s = socket.create_connection(('127.0.0.1', 8642), timeout=10)
key = base64.b64encode(os.urandom(16)).decode()
s.sendall(('GET /game.ws?k=%s HTTP/1.1\r\nHost: 127.0.0.1:8642\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n'
           'Sec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\n\r\n' % (token, key)).encode())
buf = b''
while b'\r\n\r\n' not in buf:
    buf += s.recv(4096)
head, buf = buf.split(b'\r\n\r\n', 1)
assert b' 101 ' in head.split(b'\r\n')[0], head[:200]


def need(n):
    global buf
    while len(buf) < n:
        chunk = s.recv(65536)
        if not chunk:
            raise EOFError
        buf += chunk
    out_, buf = buf[:n], buf[n:]
    return out_


def send(opcode, payload):
    mask = os.urandom(4)
    hdr = bytes([0x80 | opcode])
    n = len(payload)
    hdr += bytes([0x80 | n]) if n < 126 else bytes([0x80 | 126]) + struct.pack('>H', n)
    s.sendall(hdr + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(payload)))


pcm = bytearray()
rate = None
t0 = time.time()
msgs = 0
while True:
    b0, b1 = need(2)
    op = b0 & 0x0F
    n = b1 & 0x7F
    if n == 126:
        n = struct.unpack('>H', need(2))[0]
    elif n == 127:
        n = struct.unpack('>Q', need(8))[0]
    payload = need(n)
    if op == 9:
        send(10, payload)
    elif op == 8:
        break
    elif op == 2 and payload[0] == 2:
        rate = struct.unpack('<I', payload[4:8])[0]
        pcm += payload[8:]
        msgs += 1
        if rate and len(pcm) >= seconds * rate * 4:
            break
send(8, b'')
s.close()
open(out, 'wb').write(pcm)
print('rate %s, %d messages, %.1f s of sound in %.1f s' % (rate, msgs, len(pcm) / 4 / (rate or 1), time.time() - t0))

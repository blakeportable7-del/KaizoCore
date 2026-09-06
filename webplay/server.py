"""IronMON One web player.

Streams the Android emulator's screen (adb screenrecord, raw h264) over chunked
HTTP and turns browser clicks/keys back into adb input events. Pure stdlib on
purpose: the global pip is fragile on this machine, and this needs nothing.
"""
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse, parse_qs

ADB = r"C:\Users\bepor\Android\Sdk\platform-tools\adb.exe"
PORT = 8777
HERE = Path(__file__).parent

# Fire-and-forget one process per input. A piped long-lived `adb shell` looked
# cheaper but silently swallowed every command on Windows; this path is the one
# that has worked all along.
def send(cmd: str):
    subprocess.Popen(
        [ADB, "shell"] + cmd.split(),
        stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):  # quiet
        pass

    def _ok(self, body=b"", ctype="text/plain"):
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}

        if u.path == "/":
            self._ok((HERE / "index.html").read_bytes(), "text/html; charset=utf-8")

        elif u.path == "/video":
            self.send_response(200)
            self.send_header("Content-Type", "video/h264")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            # screenrecord stops at 3 minutes; loop it into one response.
            while True:
                p = subprocess.Popen(
                    [ADB, "exec-out", "screenrecord", "--output-format=h264",
                     "--bit-rate", "6000000", "-"],
                    stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
                try:
                    while True:
                        chunk = p.stdout.read(4096)
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                except (ConnectionError, OSError):
                    p.kill()
                    return
                p.wait()
                time.sleep(0.1)

        elif u.path == "/tap":
            send(f"input tap {int(float(q['x']))} {int(float(q['y']))}")
            self._ok()

        elif u.path == "/swipe":
            send("input swipe {x} {y} {x} {y} {ms}".format(
                x=int(float(q["x"])), y=int(float(q["y"])), ms=int(q.get("ms", 200))))
            self._ok()

        else:
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()


if __name__ == "__main__":
    print(f"IronMON One web player on http://127.0.0.1:{PORT}")
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()

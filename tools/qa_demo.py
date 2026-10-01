"""Drive the rc30 QA gate's staged states on a device or emulator (docs/QA-RC30.md).

    python tools/qa_demo.py install <apk>          install over the current build (keeps app data)
    python tools/qa_demo.py demo <mode> [out.png]  open a staged state, screenshot it, list its text
    python tools/qa_demo.py logs                   crashes, ANRs and VerifyErrors since the log was cleared
    python tools/qa_demo.py clear                  clear the log before a pass

Modes: gba-battle gba-wild gba-over gb-battle gb-over nds-battle nds-wild nds-over (Demo.kt).
Uses the SDK's adb (not the one on PATH, which is scrcpy's). One adb driver at a time.
"""
import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "C:/Users/bepor/Android/Sdk/platform-tools/adb.exe")
PKG = "com.ironmonone.app"
BAD = re.compile(r"FATAL EXCEPTION|ANR in|VerifyError|java\.lang\.\w+Error|AndroidRuntime: Process|Fatal signal")


def adb(*args, text=True, check=False):
    # logcat carries UTF-8 from every app; Windows would decode it as cp1252 and fail.
    if text:
        return subprocess.run([ADB, *args], capture_output=True, encoding="utf-8", errors="replace", check=check)
    return subprocess.run([ADB, *args], capture_output=True, check=check)


def texts():
    adb("shell", "uiautomator", "dump", "/sdcard/qa-ui.xml")
    xml = adb("shell", "cat", "/sdcard/qa-ui.xml").stdout
    return [t for t in re.findall(r'text="([^"]+)"', xml)]


def logs():
    out = adb("logcat", "-d", "-b", "main,system,crash").stdout
    hits = [l for l in out.splitlines() if BAD.search(l)]
    return hits


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    cmd = argv[0]
    if cmd == "install":
        r = adb("install", "-r", argv[1])
        print(r.stdout.strip() or r.stderr.strip())
        return 0 if "Success" in r.stdout else 1
    if cmd == "clear":
        adb("logcat", "-c")
        print("log cleared")
        return 0
    if cmd == "logs":
        hits = logs()
        print("\n".join(hits) if hits else "no crash, ANR or VerifyError in the log")
        return 1 if hits else 0
    if cmd == "demo":
        mode = argv[1]
        out = argv[2] if len(argv) > 2 else f"qa-{mode}.png"
        adb("shell", "am", "force-stop", PKG)
        adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity", "--es", "demo", mode)
        time.sleep(float(os.environ.get("QA_WAIT", "9")))
        png = adb("exec-out", "screencap", "-p", text=False).stdout
        with open(out, "wb") as f:
            f.write(png)
        seen = texts()
        print(f"{mode}: {out} ({len(png) >> 10} KB), {len(seen)} text items")
        for t in seen:
            print("  ", t)
        hits = logs()
        if hits:
            print("LOG:")
            print("\n".join(hits))
            return 1
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

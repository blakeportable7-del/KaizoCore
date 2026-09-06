#!/usr/bin/env bash
# The page's screenshot set, captured from whatever device adb sees.
#
#   tools/screenshots.sh            -> site/img/01-library.png ... 05-achievements.png
#
# Start from the Play tab with a RUN IN PROGRESS (a party on the tracker):
# the library shot is taken from the ROMs tab, the rest from the Play
# screen's FILE row. A run at the title screen gives an empty tracker and an
# OBS source that says "No Pokémon yet", which is why the emulator's set was
# only good for four of the six; the real set is taken on Blake's phone.
# 06-stream.png is a browser screenshot of the OBS source and is taken by
# hand: FILE > STREAM, open the URL it prints on the PC, screenshot.
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
IMG=site/img; mkdir -p "$IMG"
T="python tools/tap.py"      # the uiautomator tap helper (scratchpad/tap.py in the session notes)

dump() { adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb shell cat /sdcard/u.xml; }
has() { dump | grep -q "text=\"$1"; }
shot() { adb exec-out screencap -p > "$IMG/$1.png"; echo "$1"; }
row() { has "SAVE" || has "STATES" || { $T "=FILE" >/dev/null; sleep 1; }; }
find_in_row() {   # scroll the FILE row until a button is visible
  local y; y=$(dump | grep -o 'text="STATES"[^>]*bounds="\[[0-9]*,[0-9]*\]' | grep -o '[0-9]*\]$' | tr -d ']' | head -1)
  for _ in 1 2 3 4 5 6; do has "$1" && return 0; adb shell input swipe 900 "${y:-950}" 200 "${y:-950}" 500; sleep 0.8; done
}

$T " ROMS" >/dev/null; sleep 2; has "CLEAN ROMS" && shot 01-library
$T " PLAY" >/dev/null; sleep 3; shot 02-play
row; find_in_row STATES; $T "=STATES" >/dev/null; sleep 2; shot 03-states; adb shell input keyevent 4; sleep 1
row; find_in_row SETTINGS; $T "=SETTINGS" >/dev/null; sleep 2; shot 04-settings; adb shell input keyevent 4; sleep 1
row; find_in_row ACHIEVEMENTS; $T "ACHIEVEMENTS" >/dev/null; sleep 2; shot 05-achievements; adb shell input keyevent 4; sleep 1
echo "now: FILE > STREAM, open the printed URL on the PC, save as $IMG/06-stream.png"

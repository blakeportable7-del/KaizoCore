# Claude, take over KaizoCore PC from here

Blake Porterfield is handing you this session. Work in the files already on this PC. Do not start a new tree, do not clone a fresh copy, and do not reset anything.

Read this file first, then `C:\Users\bepor\KaizoCore-pc-port\FRIDAY-HANDOFF.md` and `C:\Users\bepor\KaizoCore-pc-port\PLAN.md`. Where an older note disagrees with Blake's later words in this file, this file wins.

## The files

- Source, the only tree to edit: `C:\Users\bepor\IronMonOne-wt-pc`, branch `feat/pc-tracker`. Last commit is `440d0032`. Everything since then is uncommitted. Do not revert that tree to `440d0032`. Do not commit or push unless Blake asks.
- The program Blake launches: `C:\Users\bepor\KaizoCorePC-Grok Edit`. The jar is `KaizoCore-PC.jar`. The BizHawk script is `kaizocore-hns.lua` in that same folder. `favorites.txt` is his nine favorites, first name Aggron. `tracker window.txt` says `size=3`.
- A second copy of the package, not the one he plays: `C:\Users\bepor\KaizoCore-PC-test\KaizoCore-PC-1.0.0-rc38.1-fix2\KaizoCore-PC`.
- BizHawk 2.11.1: `C:\Users\bepor\Downloads\BizHawk-2.11.1-win-x64`. His game is Heart & Soul Nat. Dex Kaizo. The play ROM's CRC in the script is `F4E28E3D` since fix6 (patches 0001-0060: Whirlpool needs a Pokemon that knows it, Red without Hail; before that `AA092306`, patches 0001-0058, 2026-10-11: SET battles, a ball in any trainer battle and the client's easter eggs; was `1C824440`, before that `CDC719C2` and `09405F53`). A run made on 1C824440 stays on it, and this tracker refuses it.
- Heart & Soul source, only if a game change is required: WSL Ubuntu, `/root/hns/pokehns-expansion`. Rebuild with `make hns` there. Do not use `tools/hns/build.sh`. Do not wipe that tree. Do not publish a ROM or the Heart & Soul UPS or BPS.

Gradle, from the worktree: `.\gradlew.bat --no-daemon ... --console=plain`. PowerShell has no `&&` and no heredoc. Quote a `--tests` filter in single quotes and do not start it with `*`. Shell commands need unrestricted permissions on this machine or they do not run.

## How the three programs talk

BizHawk runs the game and `kaizocore-hns.lua` once a frame. The script writes a slice of game RAM to `session\ram.bin`. The jar's session loop reads that, draws the card, and writes `session\panel.txt`. Clicks go the other way: the script writes `session\input.req`, the jar draws again. With the card in its own window, `panel.txt` says `window=1` and BizHawk does not draw the card. BizHawk only draws the GachaMon card and the repel icon over the game, from `overlay.txt`.

BizHawk 2.11 keeps drawings on the client surface until `gui.clearGraphics("client")`. The script's `clientFresh` clears that surface only when what is drawn changes, then draws again the same frame. Do not remove that. A closed GachaMon card used to stay painted over the game.

The jar does not embed the lua script. A script edit is installed by copying `desktop\package\kaizocore-hns.lua` into the play folder. Blake then reloads it in BizHawk's Lua Console. A jar edit is `:desktop:kaizocorePcJar`, then copy `desktop\build\libs\KaizoCore-PC.jar` onto the play folder. Do not copy that jar while `javaw` is running from it. Do not kill that `javaw`. Do not edit BizHawk's `config.ini` while BizHawk is open. BizHawk rewrites it on exit.

Do not claim a fix is done until it has been seen in the program Blake launches.

## Where yesterday started (2026-10-08)

`FRIDAY-HANDOFF.md` is the record of that day. In short, the PC tracker already tracked a Heart & Soul run from BizHawk, drew the card, took clicks, and had Play as your Pokémon, the on-screen keyboard, and a New Run combo that asks for a second hold. The open Friday list was the streamer's setup: the card beside the game, the log, favorites, and the game scenes (Lechonk in Goldenrod, the Hall of Fame room). Black 2 rules were withdrawn. The streamer's pool is Emerald Nat. Dex Kaizo.

## Where tonight is (2026-10-09, about 7pm)

Blake plays with the card in its own window, titled **KaizoCore Tracker**, next to BizHawk. The size menu on the KaizoCore PC window is Small, Medium, Large, Extra large (2× through 5×). The edges cannot be dragged, on purpose, so an OBS Window Capture stays sharp. His file says Medium.

The jar in the play folder was copied at 18:42 while the tracker was still open, so the window on screen is the previous program until he closes KaizoCore PC and starts `KaizoCore PC.bat` from that folder again. The lua in the play folder is the 18:04 script. Reload it in the Lua Console after a script change. BizHawk does not hot-reload.

### In the source, and in that 18:42 jar

- Move names no longer stop at x 74. PP, Pow, and Acc sit further right (`ppX` 90, `powX` 110, `accX` 128 in `PcBesteon.kt` `MovesArea`). A name that still does not fit is drawn one step smaller, down to 6, instead of being clipped. On the open window, Muddy Water shows as "MUDDY WATE" and Magical Leaf as "MAGICAL LEA". That is the bug this jar is meant to fix. It has not been seen after a restart.
- Medium is 3 screen pixels per card pixel, 450 by 480 for the card, which is the streamer's panel in the 2026-10-07 frames (`PLAN.md` round 3: the 150×160 card cropped at 450×480). Windows display scaling is no longer applied a second time. Before this, a 200% screen turned Medium into 6×, 900 by 960. The window stays a bit taller than his panel because a battle-effects line is always under the card, so the capture does not jump when a battle starts. `TrackerWindow.measure` in `desktop\src\main\kotlin\com\ironmonone\pc\TrackerWindow.kt`.
- The card is composed at 3× at most and enlarged with nearest-neighbour when the window is bigger (`PanelOut.COMPOSE_MAX`, `TrackerWindow.showCard`). At 6× a redraw was 30 to 160 ms.
- Nature plus and minus on the stat row are the same width. Plus is blue, minus is red (`NatureMark` in `PcBesteon.kt`). Seen in a render, not proved on his restarted window.
- Clicks that look clickable open a screen: held item, HP and level, badges, bag rows, move type, ban marks, BST, last attack, the info card's picture and weaknesses. Item text comes from the game. This is written and compiled. It is not proved by clicking through the restarted tracker.
- GachaMon and the repel icon no longer stick on the game after they close (`clientFresh` in the lua). Proved in a separate BizHawk, not in his window. He must reload the script to have it.
- With `kaizocore-dev.txt` beside the script (the play folder has it, a release must not), an empty item-PC slot receives Heart & Soul's Rare Candy Box, item 880, plus the dev items. A brand-new game already grants the box. A continued save did not. Proved in a separate BizHawk.
- BizHawk rewind was turned off in `config.ini` while BizHawk was closed (it had been on, uncompressed, 512 MB, a state every 5 frames). Speed stays 400% and frameskip stays 4. If he had BizHawk open after that edit, exiting would have written his old settings back. Check `Rewind.Enabled` before assuming it is off.
- The lua polls less: the ability popup every other frame, overlay file every other frame while a card is open, a snapshot the jar has not taken yet is left for 8 frames, and the game window is measured only while something is drawn over the game.

### Not done

- Aggron is the first favorite. Blake says that picture is still the mega. The code looks up pack id 384, Gen 3 walking sheet `walkingpals/idle/384.png`, and treats `walkingpals-ow/idle/306-mega.png` as Aggron-M only (`natdex-map.tsv` id 1078). He has rejected that result more than once. Look at the open favorites strip and at those two files before changing the lookup. Do not point base Aggron at the mega sheet.
- Walking pals on the card should use the animated sheet, and the game's own sprite when there is no animated sheet. Stand-ins are allowed on the favorites strip only. `PcPictures.kt` `posed`, `chosen`, and `sprite` do not do that yet.
- Anywhere Blake can read the name "besteon" should say KaizoCore PC, and the tracker should say Blake made it. User-facing strings are in `AboutScreen.kt`, `GachaMonScreen.kt`, `Licences.kt` (the credit lines, not the MIT licence text), `desktop\package\README.txt`, `kaizocore-pc.ini`, and the lua `say` at the "not Heart & Soul" line. `PcWordsTest` scans those `say` strings, the README, and the ini, and rejects an em dash and the word "AI". Do not rename the code identifiers `PcBesteon`, `BesteonCtx`, or the `/besteon/` assets unless a screen shows them.
- `PcMarksTest` "plus is Positive, minus Negative" fails at `PcMarksTest.kt:51`. That test is the opponent-card note marks, not the nature glyphs. It failed before the nature-mark edit. Do not treat a green `compileKotlin` as a green test run.
- The phase list in `PLAN.md` is still open past the live card: start screen, log viewer Gen 3 layout, theme on the waiting screen and the status window, gift-sub credits. Do not drop `FRIDAY-HANDOFF.md`'s list.

## What Blake has ruled

- Do not commit or push. Do not publish a ROM or the Heart & Soul patch.
- Do not kill the tracker's `javaw`, and do not overwrite the play-folder jar while that process is running.
- In-BizHawk card scale stays at 2× or below. The separate tracker window is allowed to be larger. Its menu tops out at 5×.
- Do not rip official Nintendo sprites. Credit any sheet you add.
- Prove a tracker change in the launched program. A compile is not that proof.

## Later tonight (2026-10-09, about 10:30pm), from the session Blake moved this work to

Installed in the play folder: `KaizoCore-PC.jar` (22:29) and `kaizocore-hns.lua` (21:02). Backups beside them:
`KaizoCore-PC-1842-before-aggron.jar`, `kaizocore-hns-1804-before-lag.lua.bak`. Reload the script after starting the jar.
None of it has been seen in Blake's launched program yet.

- Aggron on the favorites strip: Sprite Collab's base Aggron (`walkingpals/idle/384.png`, byte-identical to the collab's)
  reads as the mega. Blake picked the game's own follower, read from the build ROM at runtime (`HnsPics.follower`,
  `PcPictures.GAME_FOLLOWER = {306}`, `PanelOut.bannerFollower`). Nothing is shipped. `GameFollowerTest`.
- Lag: measured in the spare BizHawk (`KaizoCore-pc-port/proto/BizHawk`, config put back) at Blake's settings. Defender
  scans each newly rewritten file on its first open, about 20 ms on BizHawk's thread. Fixed: the jar holds `jar.lock` and
  the script reads one byte of it for liveness, so `jar.txt` changes only when its content does; window-mode `panel.txt`
  carries only seq/window/battle/repel/typing and is written on change; the script no longer opens `ram.bin` before a
  snapshot. 238.9 fps, no frame over 16 ms (was 18 to 30 hitches of 16 to 57 ms in 30 s). `JarLockTest` (proved red with
  the lock removed). `bizhawk/test/heartbeat-check.lua` still expects the old 3-second rule.
- Contrast: every `PixText` with a `LocalInkGround` is kept at 4.5:1 on it (`readableInk`, app/PcTracker.kt); the card's
  root ground is `Pc.Ground`, and the page/lower-box areas declare theirs. Painted themes pick white or near-black text by
  contrast. Tracker window: favorites names and the effects line follow contrast. `TextContrastTest` renders 22 cards and
  screens under 450 auto themes and checks every word against what is really behind it: 110,032 words under 4.5 before,
  0 after (proved red with the rule off). `CONTRAST_THEMES=n` checks the first n only.
- Sprites may overflow their slot (Blake), but one that would reach the word column at x 36 is shrunk at its feet to stop
  before it (`PcPictures.posed(maxRight)`).
- `PcMarksTest` passes: it expected the raw theme colour; the card draws the readable one.
- Still failing: `KaizoCorePcPinTest` (pinned seed bytes moved, likely the uncommitted randomizer rules). Not re-pinned.
- Not done: the log viewer over the game has no contrast grounds yet; card pictures (README item above); the naming;
  the PLAN.md and FRIDAY-HANDOFF.md lists.

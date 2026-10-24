# Bug scan plan (Blake, 2026-10-07)

Two scans, so bugs like the Farfetch'd dark screen and today's PC tracker gaps are found by us, not by players.

## 1. Heart & Soul game scan (mGBA, headless, tools/hns/mgba)

The Ilex Forest fade (rc38) was a scene that left the screen dark. Scenes like it are found by machine, not by playing:

- **Every map:** warp into each map of the comfort build (mapgrid.py already walks the map table), wait, read the
  screen. Fail when the screen stays dark or one colour, the player cannot move after a few seconds, or the game
  stops answering input (a softlock).
- **Every scripted scene:** run each event script the game has (cutscenes, chases, gym leader talks, gifts, the
  Day Care, trades) from its trigger with a stub save, and check the same three things after it ends, plus that the
  fade and palette are back to normal (the Farfetch'd case).
- **Every battle type:** single, double (Kaizo turns them into singles), tag, the first rival, trainers with
  two opponents, wild, roamers, the Safari: the battle starts, ends, and the right side wins when the right side
  faints. A trainer's Pokemon never leaves a battle (the rc38.1 flee fix stays covered).
- **Every item use on the field and in battle,** TMs from gyms included (names and descriptions follow the roll).
- **Both pools and every mode** (10 modes x Vanilla and Nat. Dex), a few seeds each.
- Output: one list of failures with the map or script, a screenshot and the seed; new failures become tests.

## 2. Tracker scan, every game the app tracks, Black 2 first

Today's PC lessons apply to the phone trackers too: wrong sprite colours, frozen values, marks and colours, type
changes, rule marks. For Black 2 (and White 2, then the rest):

- **Live, not stills:** drive a real run with the test bots (tools/kcbot, debug BotPort) and check every tracker
  element changes when it should: HP, levels, moves seen, encounters, abilities revealed, heals, badges, the
  carousel, last damage, type changes (Conversion, Color Change, Soak, Roost, -ate abilities, forms).
- **Every picture against its source:** every species, form and item the Black 2 tracker draws, compared with the
  game's own data, so a wrong palette like the Scizor one shows up in a test.
- **Shared code changed today** (stat mark minus sign, mark colours, the Heart & Soul BST line, battle type reads):
  run the Black 2 tracker's tests and a bot run after each, so a Heart & Soul fix never breaks Black 2.
- **Rule marks and bans** follow Black 2 Kaizo's own rules page, not Heart & Soul's.
- **Evolution line:** "Lv. 6 (FRIEND)" for friendship evolvers, letters filling as friendship rises, and the
  level, stone or trade method otherwise, exactly as besteon's NDS tracker shows it on stream (Blake's Golbat
  frame, 2026-10-07).
- Output: the same failure list format; each failure becomes a test.

## When

After the PC tracker round and the weekly reset (2026-10-09, about 5pm), so the scans run on plan usage. The game
scan first (players hit game bugs on stream), then the Black 2 tracker scan.

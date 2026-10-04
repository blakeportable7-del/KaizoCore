# rc34 QA (2026-10-02 to 2026-10-03, shipped)

## Shipped (2026-10-03)

GitHub release v1.0.0-rc34, marked Latest (the public repo's tag on its rc34 snapshot f9a38840; the private repo's
tag on master 5e484ba6, built from 74c24d0d), site deploy 6ac190776c298e11fea7da36 (rollback: 6abf48fbe6c05b16d00d97f7,
rc33's page). APK 155,159,807 bytes, SHA-256 c7e4301e3d67be5d816a7b14c6f068eb169ca57adb9fcb3cccfbad4da50ae6ec,
versionCode 44 (43 was the first rc34 build, 202bea59, never published). Blake: "fix 1,4,6,and 7 don't stop and then
release after it's fixed".

release.sh on 74c24d0d: 4,992 tests on the real dumps, 0 failures, 0 errors, 0 skipped; signed with the debug key alone
(no release key pinned yet); every native library 16 KB aligned and stripped; mapping and symbols kept in dist.

The release check on that exact APK, on the emulator (AVD ironmon, Android 14):

| Check | Result |
|---|---|
| Installs over the public rc33 with data kept; versionName 1.0.0-rc34+74c24d0d, versionCode 44 | pass |
| DS: HeartGold opens in Play in landscape, one audio start, no audio errors | pass |
| Landscape: no bar; dragging the tracker's left edge widens it about 250 px and back | pass |
| GBA: the MaxDex FireRed run in portrait and landscape; a 601 BST lead carries the X | pass |
| Game Boy: Red in portrait and landscape, the tracker on its card | pass |
| The next run is stamped 1.0.0-rc34+74c24d0d 44 | pass |
| No crash, ANR or VerifyError in logcat at any step | pass |
| rc33 updates itself through More > Check now > Update (latest.json, the site's APK, the installer), comes back as rc34 with its runs, and Check now says "You have the newest build." | pass |

The site, before the deploy: every gate of build_release.py --kaizocore-release passed, and the built folder against
the live deploy's files differed in exactly the release's own files (the page, latest.json, netlify.toml's 301 for the
old APK, sitemap.xml's dates, the rc34 APK in and rc33's out); three more paths differed only in case (Netlify lowercases
paths) with identical bytes. After it: the audit tool answers with a business, 10 functions deployed, latest.json names
versionCode 44, the page links the rc34 APK twice, the APK from the site and from GitHub both hash to c7e4301e, the rc33
link 301s to the page, and the notes link answers 200. The GitHub wiki was pushed with the rc34 notes.

The first in-app update attempt stopped mid-download on the emulator: its network had degraded after long uptime (444 ms
to 8.8.8.8). After a cold boot (59 ms) the update went through.

## Measured in the packages

- Black 2 PSS 1,276 MB to 745 MB, White 2 1,246 MB to 760 MB, DS load 13-34 s to about 5 s (the ROM held once).
- melonDS built here from libretro/melonDS 66b5d263 with two patches: no thread or memory left per load (12 loads of
  Black 2 flat), DS sound identical sample for sample to the shipped core in Black 2, White 2, Platinum and HeartGold.

## Not yet seen on a device

- A DS double or triple battle with the notebook recording every opponent, and the stream overlay following the swap.
- Favorites in OBS changing as they are edited, in OBS itself.
- The log viewer's trainer portraits, badges and moving icons after a real game over.
- Play as a shiny lead on a phone, and Unown walking as its letter.
- Continuing one Platinum save on the emulator shows "A communication error has occurred" and a white screen, with the
  old core too; seen on no other save yet. Found 2026-10-04: not the save, the core's own firmware. The save (AAA,
  0:02:58, saved twice in the player's room, map 415; both blocks' checksums good) never reaches Continue: Platinum's
  main menu looks for wireless partners (Ranger, Wii, Mystery Gift) whenever a save exists, and the firmware the core
  makes when no firmware.bin is imported has an all-0xFF Wi-Fi block (no MAC address, no radio calibration, a bad CRC;
  the game reads its MAC as FF:FF:FF:FF:FF:FF), so the wireless library will not start and the menu stops on the
  error. A sets both screens white and resets, which never finishes. Every Platinum save with data does it on every
  cold boot (Reset, or opening the game with no left-off snapshot); a new game never runs that search, which is why it
  looked like one save. A real DS with a working firmware would not. Fix ready on fix/platinum-comm-error: core patch
  0003 writes the Wi-Fi block melonDS 0.9.4 writes. On the emulator the same save then reaches the menu and continues
  into the player's room; HeartGold boots to its new game (no HeartGold save with data on the emulator), Black 2
  continues the run in Aspertia City, White 2 reaches its menu. It rebuilds the core and changes the MAC address
  every DS game sees to 00:09:BF:11:22:33, so a Gen 4 save made before it takes the game's one-day clock penalty
  once, on its next continue; and "Random MAC address" in DS settings then really randomizes, a new address (and that
  penalty) on every boot. rc34.1 hides that row and always sends it off (Blake: "put in 34.1").
- Resuming the Black 2 run closed KaizoCore twice (2026-10-03 22:31 and 23:21, the same auto slot both times):
  SIGSEGV at 0xd8 on the GL thread, GPU3D::YSort from GPU3D::VBlank (rc34 core 0x11f130; the nightly before it
  crashed at 0x11b250, also YSort, so rc34's patches did not cause it). Both tombstones show the list VBlank sorts
  filled to entry 286 and null from 287 on: every translucent polygon's slot unwritten, so NumOpaquePolygons was far
  larger than the opaque polygons. Cause: melonDS 0.9.3's state brings VertexNumInPoly back but not PolygonMode, so a
  state saved mid-quad and loaded into a fresh core (mode 0) let SubmitVertex write a fifth vertex past
  TempVertexBuffer; in this build NumOpaquePolygons sits right behind it (llvm-nm). Core patch 0004 starts a new
  polygon instead and VBlank counts the opaque polygons from their flags. That auto slot was overwritten before it
  could be kept, and the run's current snapshots never land mid-polygon, so the original crash was not replayed. On
  the fixed core: 10 resumes of the run in a row, no crash; Black 2 loaded 12 times in one process, PSS 770 to 819 MB
  and level, 30 to 31 threads, one audio start each and no audio errors; Platinum continues into the player's room;
  HeartGold and White 2 boot (their library saves hold no game).

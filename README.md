<h1 align="center">KaizoCore</h1>

<p align="center"><b>Kaizo IronMON on your phone.</b><br>
Emulator, randomizer, patcher and the IronMON tracker in one Android app.</p>

<p align="center">
  <a href="https://github.com/blakeportable7-del/KaizoCore/releases/latest"><img alt="Download the latest beta" src="https://img.shields.io/github/v/release/blakeportable7-del/KaizoCore?include_prereleases&label=download&style=for-the-badge&color=d93a3a"></a>
  <img alt="Android 8 or newer" src="https://img.shields.io/badge/android-8%2B-3ddc84?style=for-the-badge">
  <img alt="GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-blue?style=for-the-badge">
  <img alt="Free" src="https://img.shields.io/badge/price-free-555?style=for-the-badge">
</p>

<p align="center"><img src="docs/img/landscape-emerald.jpg" width="820" alt="Emerald in landscape: the game on the left, the IronMON tracker docked beside it with Wattson's Magneton on the opponent card"></p>

Playing IronMON today takes a PC, a patcher, a randomizer, an emulator, a tracker that only runs on a computer, and OBS. KaizoCore does all of that on an Android phone. Add your game, pick a mode, tap **Start new run**, and the tracker is under the game when you get your starter.

<p align="center">
  <img src="docs/img/tracker-gba.jpg" width="200" alt="The tracker under Emerald on a phone">
  <img src="docs/img/tracker-ds.jpg" width="200" alt="The tracker under HeartGold on a phone">
  <img src="docs/img/run.jpg" width="200" alt="The Run screen: pick the game and the mode, Start new run">
  <img src="docs/img/game-over.jpg" width="200" alt="The game over popup with the attempt count">
</p>

## What it does

**Tracks the run.** The app has its own copy of the PC IronMON tracker, reading the game while you play: your party, the opponent, moves seen, stat notes, abilities as they are revealed, badges, heals, evolutions and friendship. Tap an ability or a move to read what it does. It covers Gen 1 through Gen 5, and the sprites come from your own ROM. When a run ends you get the PC tracker's game over popup: continue, retry the battle, save the attempt, grade your notes, inspect the log, or roll a new seed.

**Randomizes on the phone.** Universal Pokémon Randomizer ZX is built into the app, not a second download. Pick Standard, Kaizo, Super Kaizo, Survival, Ultimate or Kaizo Doubles from the official settings files, or change any setting in the editor. Gen 1's two-pass randomization is done for you, and the log opens full screen on the phone.

**Patches and hacks.** The library says what each dump is and whether it is clean. Patches (BPS and IPS) are checked against the game they were made for, so a wrong patch is refused instead of making a broken ROM. The Hacks tab lists the ROM hacks made for your game and opens each creator's own page. Nat. Dex for Emerald and FireRed v1.1 is built in.

**Plays everything IronMON runs on.** Game Boy, Game Boy Color, GBA and DS, with eight save slots with screenshots, an auto-save, rewind, fast forward, slow motion, controllers, custom button layouts and skins. In landscape the tracker docks beside the game; on DS both screens and the tracker fit without overlapping.

**Streams.** The tracker is an OBS browser source on your own network, and the attempt counter is a text source beside it, so your overlay works the way it does with the PC setup. Clean View hides everything but the game for screen capture. Cheats and rewind switch themselves off on a tracked run.

**Keeps your progress.** RetroAchievements, with a hardcore mode that locks the helpers. Saves sync to the cloud through Android's file picker, and backups never include a ROM.

<p align="center">
  <img src="docs/img/library.jpg" width="200" alt="The library, sorted into clean ROMs, patched games and patches">
  <img src="docs/img/hacks.jpg" width="200" alt="The Hacks tab: pick a game, pick a hack, patch it on the phone">
  <img src="docs/img/editor.jpg" width="200" alt="Every randomizer setting, editable on the phone">
  <img src="docs/img/states.jpg" width="200" alt="Eight save state slots with screenshots">
</p>

## Every game the tracker knows

<p align="center"><img src="docs/img/games.jpg" width="820" alt="Title screens of Red, Blue, Yellow, Gold, Silver, Crystal, Ruby, Sapphire, Emerald, FireRed, LeafGreen, Diamond, Pearl, Platinum, HeartGold, SoulSilver, White, Black 2 and White 2, each booted in the app"></p>

Red, Blue and Yellow; Gold, Silver and Crystal; Ruby, Sapphire, Emerald, FireRed and LeafGreen; Diamond, Pearl, Platinum, HeartGold and SoulSilver; Black, White, Black 2 and White 2. Each title above is a player's own dump, booted in the app.

<p align="center"><img src="docs/img/landscape-heartgold.jpg" width="820" alt="HeartGold in landscape: the top screen, the bottom screen and the tracker side by side"></p>

## Get it

1. Download the APK from the [latest release](https://github.com/blakeportable7-del/KaizoCore/releases/latest) or from [willowcreek.group/kaizocore](https://willowcreek.group/kaizocore). Both list the SHA-256.
2. Open it on the phone. Android asks once to let your browser or file manager install apps.
3. **Library** tab, **All files**, **Add files**, pick your dump (a zip is fine).
4. **Run** tab, pick the game and a mode, **Start new run**. Then play.

To update, install the new APK over the old one. Uninstalling deletes your games, saves, notes and attempt count.

**You need your own game dumps.** KaizoCore contains no ROMs and downloads none, and this repository never will. Android 8 or newer; a DS game wants a phone with at least 3 GB of RAM.

Found a bug? In the app go to **More**, **Backup and info**, **Report a bug**, or [open an issue](https://github.com/blakeportable7-del/KaizoCore/issues). Never attach a ROM or a save.

## Credits

Built on [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) with the mGBA, melonDS and Gambatte cores; [Universal Pokémon Randomizer ZX](https://github.com/Ajarmar/universal-pokemon-randomizer-zx) and the Nat. Dex fork; rcheevos for [RetroAchievements](https://retroachievements.org). The tracker follows the [Ironmon-Tracker](https://github.com/besteon/Ironmon-Tracker) by Besteon and the [NDS-Ironmon-Tracker](https://github.com/Brian0255/NDS-Ironmon-Tracker). Nat. Dex Extension by CyanSMP64, used with permission. Press Start 2P by CodeMan38, under the SIL Open Font License.

KaizoCore is free software under the [GPL-3.0](LICENSE). It is not affiliated with Nintendo, Game Freak, The Pokémon Company, IronMON or RetroAchievements. Screenshots show the player's own copy of each game running in the app.

A side project from [Willow Creek Group](https://willowcreek.group), St. Clairsville, Ohio. If it saved you an evening of setup, you can [chip in a few dollars](https://willowcreek.group/kaizocore#support). Nothing is unlocked; it is a thank-you.

Build notes and the development log are in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

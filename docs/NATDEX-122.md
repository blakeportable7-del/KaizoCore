# Nat. Dex 1.2.2 in KaizoCore, beside 1.2.1

The Nat. Dex Extension's release v1.2.2 (CyanSixFour, 2026-11-07) came into KaizoCore on 2026-11-20 as a game of its
own, beside 1.2.1. Blake: "Don't hurt the players who are on the old system", and he chose side by side over updating
in place. A first build replaced 1.2.1 in place (branch feat/natdex-122) and was dropped for that reason. That build
would have:
- remade a player's 1.2.1 game at launch, so the save in front of the starters stopped opening (Nat. Dex refuses
  another version's save);
- left a 1.2.1 copy in the library with no tracker;
- left a player without the vanilla game unable to randomize Nat. Dex at all.

## What changed upstream

- **The game:**
  - the Accelerator, a key item that toggles double speed, two game updates a frame;
  - random battle music;
  - Emerald's Safari Zone step counter and its fixed RNG;
  - FireRed's Oak Pokedex softlock fixed.
- **The battle code:** only the Accelerator's doubled waits, Present's message, Intimidate's pop-up and the stat-change
  animation (docs/HACK-RULES-NATDEX.md).
- **The data:**
  - Aura Guard, ability 320, a placeholder;
  - five Mega forms' abilities;
  - the Accelerator, item 379;
  - the key items pocket grows from 30 to 32 slots, which moves everything after it in SaveBlock1 by 8;
  - most EWRAM addresses moved.
- **The randomizer:** Mega Zygarde's moves; 1.2.2's offsets and FireRed starter script offsets; VERSION 909.
- **Unchanged:** the twenty settings files and the sprites are byte for byte 1.2.1's, and the wiki's ruleset page has
  not changed since 2026-06-29.

## How the two live together

| Piece | 1.2.1 | 1.2.2 |
|---|---|---|
| Game kind | EMERALD_NATDEX_121, FIRERED_NATDEX_121, as before | EMERALD_NATDEX_122, FIRERED_NATDEX_122 (`natDex122`) |
| Ids and stats | emerald-natdex-121, firered-natdex-121 | emerald-natdex-122, firered-natdex-122: their own attempts and stats |
| CRC | EBFDCE4B, 33779943 | 7B4291C4, 34AAFED7 |
| Made by | no longer made; a game already made stays | the bundled patches (NatDexInfo.buildOf, RomKind.natDexMade) |
| Randomizer | engine-natdex (com.dabomstew.pkrandom), unchanged, so Heart & Soul does not move | engine-natdex122 (com.dabomstew.pkrandom122), refuses any other CRC |
| Tracker | the ROM's own address slots, NatDexFixed 10201 | the same slots, NatDexFixed 10202 |
| Settings, rules | the v1.2 files and books | the same |

A phone that copied the 1.2.1 patch out before the update gets 1.2.2's from the APK the next time it makes the game
(PrepStore.patchFileOrBundled). The make notice tells a player who has 1.2.1 that it stays.

## Checked by

- **Engines:** engine-natdex122/test randomizes both 1.2.2 dumps deterministically. NatDexSideBySideTest covers the
  kinds, each engine's gate, the patch footers and the bundled patches. GameBuildRomTest reads both releases.
- **Tracker:** the tracker suite reads both releases' dumps. That covers trainer pictures, starters, save offsets,
  slot reads, trainers, catch code, the overworld scan, the header pictures and ability descriptions.
- **Heart & Soul:** its pinned seeds are untouched, because engine-natdex did not change.
- **Upstream known issue:** the Accelerator may hide the last attack's damage, as Cyan says it does in BizHawk. It is
  measured on the emulator before release.

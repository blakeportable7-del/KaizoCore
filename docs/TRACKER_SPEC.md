# Tracker implementation spec (GBA, Gen III)

**Provenance, and why it is clean.** Architecture and field *names* below are derived
from `billgreenwald/ironmon_emu`'s **publicly published** documentation
(`CLAUDE_ARCHITECTURE.md` + `claude_docs/`, read 2026-08-30). That app's source is
closed and none of it was read. **No numeric addresses appear in those docs** — they
say to take constants from the Lua tracker, which is exactly what we do:
`besteon/Ironmon-Tracker` (**MIT**). Reading published docs and MIT source is legal;
behaviour comparison against the shipping app is our correctness oracle.
`CyanSMP64/NatDexExtension` is **unlicensed** — see the NatDex section below before
using anything from it beyond interface facts.

**Why port rather than embed the Lua.** Considered seriously and rejected on evidence.
The tracker is **99,752 LOC across 104 files**, and the reusable part is small: memory
access is confined to `Memory.lua` (141 lines, which already has an mGBA branch needing
only `read8`/`write8/16/32`) plus `Program.lua`. The bulk is a desktop drawing layer we
cannot use — 440+ `gui.draw*` calls and ~60 `forms.*` calls across roughly 60 files —
and on mGBA specifically the tracker renders to an **80x50 ASCII console buffer**
(`MGBA.lua`), not the graphical tracker anyone recognises. Hosting it would mean a
2,000–4,000 line Kotlin shim for dialogs and drawing that we would then own forever,
on top of a Lua VM, to avoid a port of roughly 5,000 lines of real logic. It also
shells out via `os.execute` in eight places, all dead on Android.

Read `Memory.lua`, `Program.lua`, `Battle.lua` and `Tracker.lua` as the **specification**
and port the address tables as data. If a Lua extension host is ever wanted, the live
option is **gudzpoz/luajava** (v4.1.0, Jan 2026, MIT, prebuilt Android natives);
`luaj/luaj` has not shipped since 2019. Performance was never the objection: the
tracker's own heavy pass runs every 10 frames, about 6 Hz.

## Data flow

```
JNI getMemoryRange(addr, len) -> ByteArray        (over the mGBA bus)
  -> MemoryBridge      injected reader lambda; readU8/U16/U32 little-endian;
                       returns null while the core is loading
  -> TrackerPoller     250ms loop on Dispatchers.Default; owns the readers below
       GameSettings    ROM identification (see Detection)
       DataHelper      addressesFor(game, version, language) -> GameAddresses?
       PokemonDecoder  XOR decrypt + 24 substructure orderings
       RouteReader / StatsReader / BagReader / LearnsetReader
  -> StateFlow<TrackerState>   Disconnected | NoGameLoaded | Active{...}
  -> UI (Compose panels)
```

Keep the logic platform-free and inject platform needs (file store, asset reader,
key-value store) as seam interfaces. Maps onto our `Tracker` / `AttachResult` in
`core-api` without change.

## Detection

| What | Where |
|---|---|
| Game code (4 chars: `BPRE` FR, `BPGE` LG, `AXVE` Ruby, `AXPE` Sapphire, `BPEE` Emerald) | `0x080000AC` |
| ROM version byte (0 / 1 / 2) | `0x080000BC` |
| Title, 12 chars | `0x080000A0` |

Confirmed independently by `tools/RomTool.java`: NatDex leaves the header untouched
(a patched Emerald still reads `POKEMON EMER` / `BPEE`), so **header identification
cannot distinguish NatDex.** Use the CRC-32 gate for that: Emerald NatDex 1.2.1 is
`ebfdce4b`, FireRed NatDex 1.2.1 is `33779943`.

`GameAddresses` holds per game+version+language: partyCount, partyBase,
baseStatsTable, levelUpLearnsets, enemyParty, battleTypeFlags, battleMons,
battlersCount, battleWeather, sideStatuses, sideTimers, battleOutcome, battleResults,
gMapHeader, saveBlock1Ptr, saveBlock1IsPointer, gameStatsOffset, saveBlock2Ptr,
encryptionKeyOffset, bag pocket offsets/sizes, trainerBattleOpponent.

## Structures

**Party Pokémon, 100 bytes.** PID `0x00`, OTID `0x04`, nickname `0x08`–`0x11`
(10 bytes, `0xFF` terminated, Gen III charmap), **encrypted block `0x20`–`0x4F`**
(48 bytes), status `0x50`, level `0x54`, stats HP/Atk/Def/Spe/SpA/SpD as u16 from
`0x56`.

Decrypt: `key = PID xor OTID`, XOR across 12 u32 words. Substructure order is
`SUBSTRUCTURE_ORDER[PID % 24]`, each 12 bytes at `order[n] * 12`:

- **G** species, item, exp, ppBonuses, friendship
- **A** 4 moves + 4 PP
- **E** 6 EVs
- **M** Pokérus, IV word at `+4` (5-bit fields; **bit 31 is the ability slot**)

Derived: nature `PID % 25`; shiny `(otId xor pid xor (pid>>16) xor (otId>>16)) < 8`;
gender `(PID and 0xFF) < genderRatio`; hidden power from IVs.

Empty slot test is `personality == 0`. The published docs describe **no checksum
validation** — add one anyway if cheap, since a torn read during a frame is otherwise
indistinguishable from real data.

**Base stats**: 28 bytes at `baseStatsTable + species * 28`.
**Battle mons** (`gBattleMons`): slot size `0x58`; species `0x00`, **moves `0x0C`**,
stat stages `0x18`, level `0x1C`, curHP `0x22`, maxHP `0x24`, status `0x28`.
**Learnsets**: pointer at `levelUpLearnsets + species * 4`; 2-byte entries (bits 0–8
move, 9–15 level) terminated by `0xFFFF`.
**Map layout**: u16 at `gMapHeader + 0x12`.

## Gotchas — each of these is a paid-for bug

- Wild vs trainer is **bit 3** of `battleTypeFlags`, not bit 0. Repeatedly got wrong.
  This one also gates B-to-Run, which must be wild-only.
- Battle moves live at **`0x0C`, not `0x14`**. Wrong offset corrupts silently.
- `saveBlock1Ptr` is a **pointer** in FR/LG/Emerald but **direct** in Ruby/Sapphire.
  R/S also use **key = 0** (no XOR) and do not encrypt bag quantities.
- ROM type IDs are **not 0-based** (11 = Fire, 12 = Water; 9/10 unused). Never remap
  before a TypeChart lookup.
- Never read any address before game + version resolve.
- FR/LG v1.0 and v1.1 differ in `baseStatsTable`; non-English FR/LG differ in
  `saveBlock2Ptr`.
- Learnset loops need a 100-entry failsafe as well as the `0xFFFF` sentinel.
- Ruby/Sapphire battle addresses in the reference are unverified placeholders. We
  ship FR and Emerald only, so do not inherit them.

## Lookup tables

Vanilla Gen III sizes: species 386, moves 354 (+ stats and descriptions), natures 25,
abilities 76, BST 386, experience 6 curves, items 376, type chart 18x18, evolutions,
route names, Gen III charmap.

**NatDex 1.2.x carries 1200+ species**, so every species-indexed table roughly triples
and must come from `NatDexExtension`, not from the vanilla Lua tracker.

## NatDex — cheap to detect, but legally blocked

**Revised 2026-08-30 after reading the NatDexExtension Lua.** This section previously
called NatDex detection "the single largest unknown." Technically it is close to
trivial; the real obstacle turned out to be licensing.

ironmon_emu's public docs contain nothing about NatDex — documented support is vanilla
FR/LG/R/S/E only. But the extension's own source shows the mechanism plainly:

- **Identification** is a single magic-number read: `Memory.read32(0x08000170) == 1258`
  (`NatDexExtension.lua`, `checkIfNatDexROM()`). Cross-check against our CRC gate
  (`ebfdce4b` Emerald, `33779943` FireRed) rather than trusting either alone.
- **Addresses** come from a pointer table the hack bakes into ROM at roughly
  `0x08000150`–`0x080002xx`, read by a ~40-line block. That is where 1.2.0's
  reorganisation is absorbed, and why detection survives new releases.

Estimated at an afternoon of Kotlin, not a milestone. `AttachResult.Failed` must still
be loud: a blank tracker is a release blocker.

**Licensing: CLEARED 2026-08-30.** `CyanSMP64/NatDexExtension` carries no LICENSE file
(GitHub API: `license: null`), so all rights were reserved. The author has since
granted permission covering the extension's Pokémon and move data and its sprites,
**both as a Kotlin port and as bundled files**. The grant is recorded verbatim in
`NOTICE`.

Four conditions ride with it, and two shape the build rather than the paperwork:

- Credit **CyanSixFour / CyanSMP64** and link the repo in the **app About screen** and
  the repo README. The README is done; **the About screen is an unbuilt release
  requirement**, so it belongs in the app milestone, not in a licence file nobody opens.
- **Nothing may imply this is an official IronMON or Nat. Dex release.** The app name
  contains the IronMON word, so About needs an explicit unaffiliated/unendorsed line.
- No ROM shipped, hosted or linked. Already a project rule; now also a licence term.
- Vendored randomizer stays GPL-3.0 with copyright notices intact.

So the NatDex tables and sprites can be used directly rather than reconstructed, which
removes the largest data-entry task from this milestone. The species-indexed tables go
from 386 entries to 1200+ by import, not by transcription.

## Cadence

One 250ms coroutine off the main thread, one state emission per tick, UI subscribes
passively. No read budgeting or emulator-pause handshake is described, and stale reads
are explicitly tolerated as acceptable for cosmetic values. Start the poller only after
the memory reader is set; stop it before the reader is cleared.

# RogueMon as a KaizoCore mode (research, 2026-09-29)

Blake: "ROGUEMON lets get it up, do your research and see if it is viable to add to the modes", and, after a
Twitch screenshot of empty menu frames in a RogueMon Poke Mart: "the current roguemon software has bugs, so ours
will need to be better than what is already offered".

**Verdict: yes, with limits.** It is a pinned, bundled-build mode and an XL job. It is not an IronMON settings
variant. No RogueMon ROM was built or run for this: the PC has no FireRed v1.1 dump. Every ROM claim comes from
patch headers, footers and literal data, the release notes, and the Lua and Java code. Clones are in
`C:/Users/bepor/ironmon-ref/`: `Roguemon`, `Roguemon-IronmonExtension`, `roguemon-releases` (branch `beta`) and
`ironmon-randomizer-smart`.

## What it is

Crozwords' FireRed roguelike (rules: github.com/Crozwords/Roguemon; builds: itch.io, for BizHawk 2.10 on Windows;
leaderboard: roguemon.gg).

- **Start:** the game opens in an in-game Ascension Tower. You pick A1 to A3 and one of 20 types (the 18,
  Typeless, Random), then a starter.
- **Your Pokemon:** you catch up to 5 with Oak's balls, or take 5 level-8 pivots, and lock one after the first
  trainer.
- **Route:** a forced, linear route in segments. You clear a segment in one go with no Pokemon Center.
- **After each gym:** a badge, the gym's TM, a higher cap, a prize (choose 1 of 3), a buy phase, then cleansing.
- **Caps:** HP and status caps by badge, from 150/3 up to 700/7.
- **Curses:** 40 of them, placed on segments (A1 one, A2 five, A3 five plus two gyms).
- **Evolutions:** every Pokemon evolves at least once, into a same-type Pokemon at about BST+100. Item evolutions
  all become "Roguestone".
- **Win:** beat the League without the chosen Pokemon fainting. The long goal is a win with every type.

The ROM enforces most of it (a "Rule Enforcement" mode since v1.3.2, and more blocks in v2). The tracker drives
the prize, shop, checklist and cleansing screens through a command queue in the ROM. The player keeps a few rules
by hand (no stealing, no wild EXP farming, the shiny rule).

## The build

- **What a release is:** `drumstix576/roguemon-releases` (beta branch, v2.2.11-beta.0 on 2026-09-22) is the whole
  v2 tracker bundle: 157 Lua files, 1,795 PNGs, 60 `.rnqs` presets, two `.bps` patches and `randomizer.jar`.
- **Base game:** both patches apply to **FireRed (USA) v1.1**, source CRC32 `84ee4776`, which KaizoCore knows as
  `FIRERED_U_V11`. They do not apply to v1.0, to KaizoCore's Nat. Dex FireRed, or to LeafGreen.
- **Output:** a 32 MiB ROM with game code `RGMN`. Only 9.6 MB of it is copied from vanilla.
  - "split" is v2.2.11-beta.0 `e796bab4`, 19.8 MB, with modern moves and the physical/special split.
  - "classic" is `55ca5a1a`, with Gen 3 mechanics plus Fairy.
- **Churn:** the ROM CRC changed in all 20 v2 tags, about 7 months of releases. Expect a new build every 2 to 5
  weeks. The ROM's config-block layout stamp changed 7 times in 7 months and has held for 8 weeks.

## Randomizing

Stock UPR ZX cannot do it. v2's `randomizer.jar` is ZX 4.6.1 plus a closed `RoguemonRomHandler` (no public
source) with its own 60-byte settings layout (a forced-type byte and 64-bit tweaks). The public GPL-3.0 fork
(something-smart/ironmon-randomizer) stops at the v1 era (2025-04-06).

- **Bundling the jar:** M. It is Java 8 bytecode. It needs package relocation (it shares `com.dabomstew.pkrandom`
  with engine-natdex) and a check that its `java.awt.image.BufferedImage` references never run on Android.
- **Porting the handler with no source:** XL.

## Tracking

- **Where the ROM keeps its data:** the v2 ROM publishes a config block of about 580 named offsets, found through
  a header pointer to `RGMNRGMN` (in v2.2.11-beta.0, `0x200` points to `0x08B22340`). It also has a tracker-data
  struct with a command queue, and segment, prize and curse state in a third save block.
- **What we can reuse:** neither the v1 nor the v2 layout matches the Nat. Dex FireRed addresses KaizoCore
  knows. `GbaTracker.resolve` refuses the header safely.
- **What the extension is:** about 35,300 lines of BizHawk Lua (113 `forms.*` dialogs, 64 `gui.*` draws, 21
  memory-write watches).

## Bugs, and how ours is better

There are no public issues. Bugs go to their Discord, and the release notes' Known Issues are the only record.
Two issues are old:
- "some in-battle tracker functions don't work when retrying a battle": 15 releases running.
- "a new segment with the prize checklist unresolved is likely to soft-lock": 7 weeks running.

Blank-menu and visual glitches appear on both the tracker side and the ROM side (for example v1.1.1 "Duplicator
blank prize menu", v2.2.3 "avoid empty display", v2.2.6 "reverted Instant Text due to visual glitches"). The
empty FRLG frames in Blake's screenshot are the game's own window art, which the extension never draws, so they
are most likely a ROM window.

**Recommendation:**
- KaizoCore owns every screen and every decision (prize choice and its task screens, buy phase, checklist,
  cleansing, curses, caps, segments, attempts and wins) as Compose screens with tests. That removes the tracker
  side of these bugs.
- The ROM stays the mechanics and enforcement engine, pinned to one release we have QA'd.
- Safety nets:
  - an automatic save state before every prize, shop and checklist step;
  - a warning before a new segment while a prize or checklist is pending (the documented soft-lock);
  - refuse to start while Rule Enforcement is off;
  - Fast text rather than Instant.
- We cannot fix a window or a soft-lock inside the ROM.

**Not recommended:** rebuilding RogueMon's rules on our own Nat. Dex ROM with memory writes. It would drop the
battle changes, curses and enforcement, and drift from a ruleset that changes monthly.

## Plan and size

| Step | Size |
|---|---|
| ROM prep: RomKinds per pinned release by footer CRC, the split patch bundled (+19.8 MB), a PrepOptions row | S to M |
| Randomizer: bundle and relocate their jar, the 60 presets, seeds, the ascension and type bytes | M to L |
| Config-block resolver and a base tracker that reads the new layout's tables and names from the ROM | L to XL |
| RogueMon screens and the command-queue protocol: caps, segments, curses, checklist, shop, cleansing, Roguestone, about 14 prize task types | XL |
| Run flow: the ROM's "await randomization" handshake, randomize, reload with a save state | L |
| Home mode: ascension and type picker, an attempts and wins ledger | M |
| Tests: CRC pins, synthetic config-block tests, emulator QA of gym, prize, shop, cleansing, next segment | L |

A first version cannot skip the prize, checklist and shop protocol, because the ROM waits on it. It can skip the
leaderboard, GachaMon, the soundboard, the log viewer, head-to-head seeds, the Classic profile and the extra
stat screens.

## Credits and licences (as published)

| Who | What | Licence |
|---|---|---|
| Crozwords | creator, rules, itch.io, roguemon.gg | Roguemon repo: none |
| BigMurph619 | rules and images | none |
| something-smart | v1 tracker extension | none |
| something-smart | randomizer fork | GPL-3.0 |
| Jason Harvey (alienth) | v1 tracker and ROM-side work, jbps | none seen |
| drumstix576 | v2 releases and tracker | none |
| Reilnur | evolution pool data | none |
| kerrymilan, AndreVolpi, TakeJoshyy | pull requests | none |
| CyanSMP64 | Nat. Dex lineage | none (already in NOTICE) |
| besteon | Ironmon-Tracker | MIT |
| Dabomstew, Ajarmar | UPR ZX | GPL-3.0 |

Blake's standing ruling is that KaizoCore has the licensing it needs. These are recorded for the credits.

# Kaizo modes on other ROM hacks (research, 2026-09-30)

Blake: "i want to research how to play kaizo modes on other pokemon rom hacks". No hack ROM was run. The facts come
from patch files (footers and bytes), source, docs and public metadata; the patch-analysis scripts are in the
2026-09-29 session scratchpad (`patches/`), and the reference clones in `C:/Users/bepor/ironmon-ref/`.

## Bottom line

1. **IronMON on hacks is a small scene.** It is IronMON-native hacks and patches: Nat. Dex (supported),
   RogueMon (docs/research/roguemon.md), IroneMon Red Kaizo, Krizz Kaizo, Factory Kaizo, Subpar Kaizo, Triple Bond,
   the Faster and Super Kaizo patches. The big hack communities play Nuzlockes with each hack's own options:

   | Discord | Members, 2026-09-29 |
   |---|---|
   | Crystal Clear | 130k |
   | Radical Red | 118k |
   | Unbound | 108k |
   | Emerald Rogue | 80k |
   | Pokemon IronMON | 72k |
   | Elite Redux | 36k |

   There is no published IronMON or Kaizo ruleset for Radical Red, Unbound, Renegade Platinum, Redux, Elite Redux,
   Run & Bun, Inclement Emerald or Imperium.
2. **Only in-place patches play as the base game.** Every popular hack is one of four kinds of rebuild:
   - a CFRU/expansion build;
   - a decomp or disassembly rebuild;
   - a DS archive hack;
   - a GB rebuild.

   Radical Red keeps FireRed's header and leaves the old vanilla stat tables intact while the game reads new copies,
   so a header match would show vanilla FireRed's stats. KaizoCore already guards this: a Library game is tracked
   only on a CRC match (GameSession.trackerKind), and an unknown hack plays untracked. A future pinned hack kind must
   also choose its tracker map by kind, not by header. GameMap.resolve and the Game Boy path both choose by header
   today.
3. **Stock UPR ZX and FVX do not randomize hacks.** Their maintainers say so in writing (ZX #46 and #294, FVX #30),
   and ZX 4.6.1 (the version KaizoCore vendors) crashes on a CFRU build (#873). Working hack randomizers are forks
   (Speedchoice, Nat. Dex, RogueMon, a Black 2 Redux fork, Triple Bond), and Android cannot run their jars, so each
   fork must be vendored as source.
4. **Tracking needs an address source per hack.** CFRU (Radical Red, Unbound) keeps vanilla FireRed 1.0 RAM symbols
   (30 of 32) but stores the party unencrypted and moves the ROM tables. Decomp and expansion builds need per-build
   addresses. Three sources exist:
   - the official tracker's address JSON;
   - a self-describing ROM (the Nat. Dex slot table, RogueMon's config block);
   - build artifacts (Inclement Emerald ships a map, Polished Crystal and Shin a `.sym`).
5. **The Kaizo rules' skeleton transfers:** one main Pokemon, game over on the lead's faint, catch-only in the
   wild, no healing items outside battle.
   - **Settings-bound rules cannot be enforced on a hack's own randomizer:** +50% levels, boss +3, EXP
     standardization, no trainer held items.
   - **Shop, TM and healing rules break** on hacks with Rare Candy marts, cheap TMs or auto-heal.
   - **The BST and ban lists must be re-derived** from each hack's data.

## What each hack is (measured where marked)

| Hack | Base | Patched CRC32 | What it is |
|---|---|---|---|
| Radical Red 4.1 | FireRed 1.0 US (`dd88761c`) | `fba55dd8`, 32 MiB (measured) | CFRU + Dynamic Pokemon Expansion. 13 of the 14 table pointers UPR reads are moved. Built-in randomizer, deterministic from the Trainer ID. Hardcore mode |
| Unbound 2.1.1.1 | FireRed 1.0 | `4b3d4957` (from third-party patches; confirm) | CFRU. Built-in randomizer; badge level caps 15 to 66, or 20 to 75 |
| Blaze Black 2 / Volt White 2 Redux 1.4.1 | clean B2/W2, xdelta | not measured | DS; Fairy is type 0x11; ARM9 and overlays changed. A ZX fork exists for Black 2 only |
| Emerald Kaizo 1.1 (SHF, 2026) | Emerald (`1f1c08fb`) | `2500c267` (measured) | Decomp rebuild. QoL toggles: Rare Candy in marts, ¥1 TMs |
| Emerald Legacy 1.1.4 | Emerald | `9dd7c03b` (measured) | Decomp rebuild with vanilla data formats |
| Inclement Emerald 1.13 | Emerald | not measured | Decomp + expansion; ships `pokeemerald.map` |
| Elite Redux 2.65 beta 2 | Emerald | `b9cd3ba4` (measured) | Expansion, monthly releases; in-game randomizer |
| Renegade Platinum 1.3.0 | Platinum (rev 0 or 1) | not measured | DS; its author says UPR is incompatible |
| Crystal Kaizo (2026) | **Crystal 1.1** (`3358e30a`) | `a9a7c996` (measured) | Rebuilt layout. KaizoCore has no Crystal 1.1 kind yet (XS to add) |
| Shin Pokemon Red/Blue 1.25.0 | Red, Blue | `699f59ef`, `eba69fdd` (measured) | Rebuild, but WRAM identical to vanilla (author's `.sym`); built-in randomizer and Nuzlocke mode |
| RogueMon 2.2.11-beta.0 | FireRed 1.1 (`84ee4776`) | split `e796bab4`, classic `55ca5a1a` (measured) | Expansion-style; config block at the pointer at 0x200. Not Nat. Dex (0x170 holds 0x401) |

All six in-place patches KaizoCore bundles touch none of the 14 table pointers UPR reads, which makes that a useful
admission test for "plays as base". Radical Red touches 13 of them.

## Shortlist for KaizoCore (demand x feasibility)

| # | What | Size | Why first, what could go wrong |
|---|---|---|---|
| 0 | **A generic "plays as the base game" gate** plus the IronMON patch family: Faster, Super Kaizo, the Platinum and HGSS IronMON patches, Moemon sprite patches, the Gen 1/2 QoL patches | M, then XS-S per patch | What IronMON players actually run. Admission test: known base CRC, in-place patch, table pointers untouched, a ZX load and round trip. The tracker map is chosen by pinned kind. Necessary, not sufficient: tables can shift in place (Blue Kaizo 2026) |
| 1 | Radical Red | L | Biggest demand. No KaizoCore randomizing (the game's own options); a CFRU decoder (unencrypted party, moved tables, new bag). The stale vanilla tables are the trap. No seeds, so no run codes |
| 2 | Unbound | M after #1 | Shares the CFRU decoder; a new region's maps and routes; needs an RTC |
| 3 | Black 2 / White 2 Redux | M | NDS tracker 6.3.11's pointer scheme (being ported now for vanilla B2W2), a move sidecar, the Fairy type; the ZX fork covers Black 2 only |
| 4 | Emerald rebuilds (Emerald Kaizo, Legacy; Inclement has a map) | L, then M each | A per-build map from a `.map` or `.sym` or a scan, a ZX ini entry per release |
| 5 | Renegade Platinum | M-L | Partial randomizing only (wilds, movesets, types, stats); trainers stay Drayano's |
| 6 | Game Boy: Blue Kaizo 2016, Intense Indigo, Shin, Crystal Kaizo | S-M each | A per-kind Gen 1/2 map; Crystal 1.1 kind; Shin's WRAM is vanilla |
| 7 | Elite Redux | XL, watch | Monthly re-pinning, four abilities per Pokemon |
| 8 | RogueMon | XL | docs/research/roguemon.md; phase 1 on feat/roguemon |

Not first:
- **Emerald Rogue:** procedural.
- **Run & Bun:** no source, no randomizer.
- **Imperium:** header pointers zeroed.
- **Seaglass:** no source.
- **Polished Crystal, Crystal Clear:** cannot be randomized.
- **Old Drayano hacks:** ZX fails on them.
- **hg-engine builds:** need a fork.

## Nuzlocke is the natural fit

The big hack communities already play Nuzlockes. KaizoCore's Nuzlocke engine is game-agnostic, so a hack's tracker
map plus an area normalizer makes the Nuzlocke ledger work on it, with no randomizer at all. For Radical Red and
Unbound that is the cheapest way to be useful: their built-in randomizer, KaizoCore's tracker and ledger.

## Open questions that need real ROMs (on zz- clones)

1. Load each hack headless in the vendored ZX and record the first exception.
2. Measure the patched CRCs still unknown: Renegade, Redux, Sacred Gold, Inclement, Run & Bun, Seaglass, Rogue,
   Imperium 1.3.1.
3. Confirm Radical Red, Unbound and Emerald Kaizo RAM on a device: bag, save-block base, trainers, EXP tables.
4. Confirm Unbound's patch format and base.
5. Measure the WRAM of Yellow Kaizo, Legacy and Crystal Kaizo+.

## Licences as found

- **GPL-3.0:** UPR, ZX, FVX and every randomizer fork.
- **Trackers:** Ironmon-Tracker MIT, NDS tracker GPL-3.0, Gen 1/2 trackers MIT.
- **CFRU:** no licence file; its README asks that nothing built on it be sold, including donations.
- **Most hacks:** no licence. Shin and the SHF Kaizo patches ask for credit.

Blake's standing ruling is that KaizoCore has the licensing it needs. These are recorded for the credits.

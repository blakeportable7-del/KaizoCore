# Repos to add to KaizoCore

Research report, written 2026-09-29. Research only: the only thing this research created or changed is this file.

## 0. How to read this

**Method.** GitHub reads through `gh api` (licence field, LICENSE text where the API said NOASSERTION, head commit of the default branch, latest release, README and source), plus web search. "Last commit" is the head commit of the default branch, not `pushed_at`. Nothing was downloaded or run and no patch was applied. The already-in list comes from `NOTICE`, `PrepOptions.kt`, `HackLinks.kt`, `docs/ADDON-DESIGNS.md`, `docs/3DS-RESEARCH.md`, `TRACKER-PARITY.md` and `ironmon-ref/IRONMON-MODES-REPORT.md`. Things your own modes report already recommends are tagged "(in your modes report)" and are not ranked.

**Blocked or unreadable.** romhacking.net sits behind a Cloudflare challenge (not bypassed), pokecommunity.com answers 403, hackdex.app answers 403/429, web.archive.org is blocked by the fetch tool. Anything about those pages comes from search snippets or from the projects' own GitHub repos and is marked "unverified". Many IronMON quality-of-life patches live only in the IronMON Discord #resources channel (besteon/Ironmon-Tracker discussion 488 says so). That channel is not searchable, so patches that exist only there are not in this report.

**Verdicts** (KaizoCore is GPL-3.0):
- **SHIP**: MIT, BSD, Apache-2.0, GPL-3.0 or GPL-2.0-or-later, LGPL, MPL-2.0 (file level), CC-BY-4.0, CC-BY-SA-4.0. Keep notices and credit.
- **LINK**: no licence file, all rights reserved, or non-commercial. Link out only.
- **ASK**: LINK, but the author is reachable and the project is worth a message.
- NOTICE precedent: Blake's 2026-09-28 "we have all licensing" already covers the mode patches he chose to bundle. I record licence status as found and leave that call to him.
- **Effort**: XS under a day, S 1-3 days, M 1-2 weeks, L 3-6 weeks, XL 2+ months.
- A bare `owner/name` is a GitHub repo: URL is `https://github.com/owner/name`.
- **ROM?** says what must come from the player's own dump. No entry needs KaizoCore to ship or download a ROM, BIOS or key.

## 1. Top 10, ranked by value to players

Ranking logic: what removes the most friction or adds the most for a phone player, weighed against licence risk and effort. Four of the ten (entries 2 to 5) are patch gap-fillers: the app offers no quality-of-life patch at all for Diamond, Black or Ruby/Sapphire (Faster Sapphire is in 3.1), and only Super Kaizo builds (smart AI forced on) for Platinum and HeartGold, so a Kaizo or Standard player on those games has nothing.

| # | Repo | Licence | Verdict | Effort | One line |
|---|---|---|---|---|---|
| 1 | UTDZac/SpriteIsMe-IronmonExtension | MIT | SHIP | M | Your character becomes the lead Pokemon (the owner's ask, Gen 3 only) |
| 2 | SentorG/NewPlatPatch | none | LINK/ASK | XS-S | Platinum "GIGA" quality-of-life patch 3.1 without smart AI |
| 3 | PyroMikeGit/IronMONHGSS | none | LINK/ASK | XS-S | HeartGold quality-of-life patch without smart AI |
| 4 | SentorG/Diamond-IronMON | none | LINK/ASK | XS-S | Diamond has no patch in the app at all |
| 5 | Brian0255/Pokemon-Black-Intro-Patch | none | LINK/ASK | XS-S | Black intro speed-up, by the NDS tracker's author |
| 6 | DrMaple/IronMONPatchEditor | GPL-3.0 | SHIP | M | Skip the intro speech with a preset name, instant heal, step items |
| 7 | kelseyyoung Ironmon Maps (3 repos) | MIT | SHIP (data), link (maps) | XS / M | Trainer, item and portal maps for FRLG, Emerald, HGSS |
| 8 | Hackdex-App/hackdex-website | MIT | SHIP/link | XS-S | Living catalogue of hack patch pages, plus a base ROM checksum table |
| 9 | Following Platinum (Mikelan98, AdAstra) | fan project | LINK | XS | The lead walks behind you in Platinum |
| 10 | romanrdecaro-arch/pokebot-3ds + Grenin430/PermaLocke | MIT / GPL-3.0 | SHIP | S-M | Licensable groundwork for the 3DS probe, plus Azahar RPC facts |

### 1. UTDZac/SpriteIsMe-IronmonExtension (the owner's ask)
- URL: https://github.com/UTDZac/SpriteIsMe-IronmonExtension
- Licence: MIT (LICENSE in repo). SHIP. Last commit 2024-03-18, release v1.4 the same day.
- What a player gets: the lead Pokemon's Walking Pals sprite is drawn over the overworld figure, out of battle. It walks while a D-pad direction is held, idles otherwise, sleeps after a while or when the lead is asleep, faints at 0 HP, and faces 8 ways. It can be pinned to a species, use a default when the party is empty, or use a custom sprite folder. v1.3 added a Stream Connect redeem, v1.4 the "random" and "default" redeems.
- How it works: a Lua extension, not a ROM patch and not a decomp build. `drawSpriteOnScreen()` runs each frame inside the Gen 3 tracker, skips when the map is invalid, in battle or under an overlay, and draws at the screen centre (the standing player is always centred in Gen 3). `startup()` returns at once unless `Main.IsOnBizhawk()`, so mGBA users never had it. It forces the tracker's "Allow sprites to walk" on. Needs tracker 8.4.1 or newer.
- Optional ROM patch in the same repo: `FRLG_Invisible_Trainer_-_Maple_Compatible.ips`, 24,840 bytes, 3,300 records between 0x35BC2A and 0x369DF5, committed 2023-12-22 by UTDZac. It blanks the player's own overworld pictures so the trainer does not show under the icon. FireRed 1.1 only per your design doc, which attributes it to Ropan; the commit is UTDZac's, so confirm authorship before shipping the IPS.
- Games: Gen 3 tracker games only (FireRed, LeafGreen, Ruby, Sapphire, Emerald). Nothing for Game Boy, Game Boy Color, DS or 3DS. See section 2.
- Fit: Kotlin overlay, already designed in `docs/ADDON-DESIGNS.md` (2026-09-28). The art is the Walking Pals set you already bundle (PMD Sprite Collab, CC BY-NC); credit UTDZac in About. Ship the IPS only as an optional FireRed 1.1 setting, or hide the trainer some other way.
- Effort: M. ROM: reads live RAM; icon fallback from the player's own ROM; ships nothing.

### 2. SentorG/NewPlatPatch (Platinum GIGA patch 3.1)
- URL: https://github.com/SentorG/NewPlatPatch . Licence: none (no LICENSE file), LINK/ASK, NOTICE precedent applies. Last commit 2025-02-05, release v3.1 2025-02-12, four xdelta files of about 22.9 MB (plain, FLX = frame limiter off, Snowy, Snowy FLX).
- Adds: the standard Platinum IronMON quality-of-life build. Dialogue overhaul to the Hall of Fame, Mom moved off the TV, NPCs moved or made static, signs removed, name-rater Psyduck, instant honey encounters (highest level 23), beds heal the party, time-of-day trainers made all-day, fixes for the Rival 2, free-Potion, Dex and bike-shop crashes, uncapped friendship NPC.
- Why it matters: you bundle only `Platinum.Super.Kaizo.IronMON.xdelta`. That file is derived from GIGA 3 but is "completely separate", sets smart AI flags and rewrites the Great Marsh binoculars, so it is wrong for Kaizo, Standard, Ultimate or Survival. For those modes Platinum currently gets no patch.
- Needs Platinum 1.0 (CRC 9253921d, your `PLATINUM_U`; Hackdex's table calls it "Platinum (Rev 0) USA", same CRC). The author offers a Snowy variant and says it gives a small edge to some fishing pivots; offer the plain one.
- Fit: bundle as an asset, add the output `RomKind` (measure its CRC by applying to the pinned dump), one `PrepOptions` row, a NOTICE entry with SHA-256, exactly as for the Faster B2W2 and Super Kaizo files. Effort: XS-S. ROM: applies to the player's own Platinum dump.

### 3. PyroMikeGit/IronMONHGSS (HeartGold, no smart AI)
- URL: https://github.com/PyroMikeGit/IronMONHGSS . Licence: none, LINK/ASK. Default-branch head 2022-10-23, latest release v0.2.2 on 2023-08-13, xdelta files 8.7 to 10.2 MB (Limiter or NoLimiter, each also with ToggleBGM).
- Adds: custom title, SilverstarStream's intro skip, dialogue cut until Goldenrod, removed scenes (Lyra's VS Recorder, PokeGear prompt, Kimono Girl, Day Care talk, Pokeathlon intro), name raters in Cherrygrove and Violet, optional frame-limiter removal. README says it works with ZX 4.4.0 and 4.5.1 and the NDS tracker.
- Why it matters: your HeartGold offer is Super Kaizo 0.0.3 only, which includes smart AI and the contest Celebi. Plain Kaizo, Standard and Survival HeartGold runs have no patch.
- Caveats: SoulSilver support unverified (every asset is named `HGPyroIronMON...`); ZX 4.6.1 not mentioned (README predates it). Check by applying to the pinned dump and running one randomize.
- Fit and effort as entry 2. ROM: player's HeartGold dump.

### 4. SentorG/Diamond-IronMON
- URL: https://github.com/SentorG/Diamond-IronMON . Licence: none, LINK/ASK. Last commit 2025-05-15, release v1.1 2025-07-27, two xdelta files of about 124 KB (plain and FLX).
- Adds: the only quality-of-life patch for Diamond I found. Rowan skip and faster character selection, Set mode on by default, dialogue overhaul to the Hall of Fame, hands-free briefcase at Lake Verity, National Dex at the Sandgem lab, faster healing, fishing and HMs, name raters, friendship NPC in Sandgem, Poketch preloaded (Clock, Memo Pad, Pedometer, Pokemon List, History, Color Changer), instant honey trees, all-day trainers, no sinking tiles, static Iron Island trainer.
- Needs Diamond (USA) Rev 5. That is your `DIAMOND_U` (CRC 84427823, the same CRC Hackdex lists for Rev 5). Diamond only; Pearl is not covered. The author warns Diamond and Pearl code is fragile for randomizer compatibility, so test one seed per release.
- Fit and effort as entry 2. ROM: player's Diamond dump.

### 5. Brian0255/Pokemon-Black-Intro-Patch
- URL: https://github.com/Brian0255/Pokemon-Black-Intro-Patch . Licence: none, LINK/ASK (Brian0255 is the NDS tracker's author; GPL-3.0 for the tracker, not stated for the patch). Default-branch head 2023-09-27, release 1.2.1 on 2024-03-20 (xdelta 20 KB, plus a Smart AI variant of 22 KB).
- Adds: Black 1 has no patch in the app. This one cuts most text, skips the Bianca house, lab and post-lab sequences, the catch tutorial, the Pokecenter tutorial and the Route 2 call, shortens Ghetsis to a walk-away, makes the C-Gear optional, and is made to go through the Ajarmar randomizer.
- Caveats: Black only (White not mentioned), base revision not stated. Measure the output CRC on your pinned Black dump like the others.
- Fit and effort as entry 2. ROM: player's Black dump.

### 6. DrMaple/IronMONPatchEditor
- URL: https://github.com/DrMaple/IronMONPatchEditor . Licence: GPL-3.0, SHIP (port the logic). Last commit and release 1.2 on 2023-06-08 (a 79 KB WinForms exe; C# source in the repo).
- Adds, applied to a ROM that already carries the Faster patch: Instant PC on or off ("Normal" keeps Nurse Joy's confirmation for Survival), skip the professor's speech with a preset gender, player name and (FireRed) rival name, and "Always" spawn Step Items in FireRed (the author says this is legal). Cuts the per-reset ritual to nothing.
- How: direct byte writes at fixed offsets after checking the game code and a patch marker. FireRed: 0x780034 player name, 0x78003C rival name, 0x780044 gender, 0x479DA0 step items. Emerald: 0x5E8631 name, 0x3085A gender, 0x271935 and 0x27191E Instant PC, 0xE40000 speech.
- Version trap: 1.2 dates from June 2023, when the current patches were Faster FireRed 1.1 and Faster Emerald 1.2.1, and its notes say it works with Faster FireRed "except the Insta PC toggle". You bundle 1.3.2 of both, so every offset must be re-derived by diffing 1.3.2 before trusting it. Do not copy the table blindly.
- Fit: a "Reset speed" section in Prepare that writes into the patched dump before randomizing. Effort: M (offset re-derivation and tests). ROM: writes into the player's patched dump.

### 7. Kelsey Young's Ironmon Maps (kelseyyoung)
- URLs: hub https://ironmonmaps.com , repos https://github.com/kelseyyoung/FRLGIronmonMap , https://github.com/kelseyyoung/EmeraldIronmonMap , https://github.com/kelseyyoung/HGSSIronmonMap , shared code https://github.com/kelseyyoung/IronmonMapUtils . Licence: MIT on all four. SHIP for code and data. Last commits 2026-08-26 (FRLG, Emerald), 2026-09-04 (HGSS).
- Adds: interactive maps with trainer hover (Pokemon count, levels, movement notes), clickable cave and ladder portals, item catalogues. The Gen 3 tracker's own "click a trainer" feature (9.3.0) covers only the trainer part.
- Data worth having as plain files: `src/data/trainers.ts` (44 KB FRLG, 59 KB Emerald), `items.ts` (16 to 19 KB), `portals.ts` (20 to 32 KB). They can seed the Items checklist and tap-a-trainer items that are OPEN in `TRACKER-PARITY.md` without touching the map art.
- Do not bundle the map renders: `FullHoenn.webp` is 22 MB and the zips run 9 to 38 MB, all Nintendo tile art.
- Fit: link out from a Guides row (works in a phone browser); port the three data files for the checklist. Effort: XS for the link, M for the checklist. ROM: none for the link; the checklist reads save flags from the player's game.
- Related: jwunderl/OverworldItems (section 3.3) built the same idea as a Gen 3 extension; its licence is none, so build from the MIT catalogue instead.

### 8. Hackdex-App/hackdex-website (hackdex.app)
- URL: https://github.com/Hackdex-App/hackdex-website , site https://www.hackdex.app . Licence: MIT in LICENSE.md (GitHub shows NOASSERTION because of a branding notice: the name "Hackdex" is reserved, and its legal pages are excluded). SHIP for code. Last commit 2026-09-29, 79 stars.
- Adds: a maintained catalogue of Pokemon hack pages (screenshots, tags, versions, BPS or xdelta patch files), patch-only storage, in-browser patching. Your Hacks tab is a hand-kept list of 29 links; this is where new hacks show up first.
- Three uses: (a) deep-link per hack from the Hacks tab, subject to your own "creator's home, never a mirror" rule in `HackLinks.kt` (Hackdex hosts author-uploaded patches, so decide whether that counts); (b) `src/data/baseRoms.ts` (MIT) is a CRC32 and SHA-1 table of about 40 Pokemon dumps you can diff against `RomIdentity` (it agrees with you on Platinum Rev 0 9253921d and Diamond Rev 5 84427823); (c) do not scrape it, the repo has no public listing API (routes are download, refresh, health and notice only).
- Effort: XS for links, S for the checksum cross-check. ROM: none. I could not open hackdex.app pages; all of this is from the repo.

### 9. Following Platinum (Mikelan98 and AdAstra/LD3005)
- URL: https://www.pokehacking.com/fangames/following-platinum/ (the same site lists Following Renegade Platinum, v1.2.0). No GitHub repo. Licence: fan project, all rights reserved, LINK.
- Last update on its page: 2026-09-12. Version reads 1.1.3 on its own page and 1.1.1 on the list page, so write "1.1.x".
- Adds: HGSS-style walking Pokemon for Platinum. The lead follows (one at a time, all 493 species) with animated HGSS sprites, event scenes adapted so the Pokemon stays in them, a framerate-unlock option (battles only or everywhere), an optional Fairy-type edition.
- Not stated anywhere I could read: base Platinum revision, patch format, and whether the NDS tracker or ZX handle it. No issue in the NDS tracker repo mentions it. Platinum addresses in the NDS tracker use version-pointer offsets, so it may just work; unverified, needs one test.
- Fit: a Hacks-tab row for Platinum. Effort: XS plus one test pass. ROM: player's Platinum dump.

### 10. romanrdecaro-arch/pokebot-3ds and Grenin430/PermaLocke (3DS groundwork)
- URLs: https://github.com/romanrdecaro-arch/pokebot-3ds (MIT, SHIP, last commit 2026-09-18) and https://github.com/Grenin430/PermaLocke (GPL-3.0, SHIP, last commit 2026-09-28, v1.0.7.9). Also https://github.com/santacrab2/PKHeX-Plugins (MIT, last commit 2026-08-31).
- Not a player feature yet. It is the licensable groundwork for your 3DS M0 probe: a working Azahar UDP RPC client (`citra_rpc.py`, 17 KB), PK6 and PK7 parse and decrypt (`parser.py`, 21 KB), PK6 export, and an anchor scan instead of fixed party addresses (`find_offsets.py`, 29 KB) because its own notes say the X/Y heap layout drifts between versions. pokebot-3ds marks its own addresses `verified=False`.
- Facts from PermaLocke's `docs/ARCHITECTURE.md` (written against Azahar's `rpc_server.cpp`, checked on real Ultra Moon) that belong in `docs/3DS-RESEARCH.md`:
  1. Call `SetGetProcess` first. `ReadMemory` answers without it but returns bytes that are not the game's.
  2. UDP 127.0.0.1:45987, 16-byte header `<u32 version=1, u32 requestId, u32 type, u32 dataSize>`, at most 1024 data bytes per packet; ops 1 ReadMemory, 2 WriteMemory, 3 ProcessList, 4 SetGetProcess.
  3. The RPC server is off by default and can only be switched on with emulation stopped (Configure, Debug, Enable RPC Server).
  4. `WriteMemory` is whitelisted and silently drops writes to 0x30000000-0x40000000 while still replying OK. Reads work there. Harmless for a tracker, fatal for any write feature.
  5. It found the Ultra Moon party at 0x330128E4 (six slots of 0x104 bytes) on one run. Your notes list 0x33F7FA44 and a 484-byte stride for USUM. Those may be different structures or builds; reconcile in the probe and treat every fixed address as per build. A 96 MB heap plus linear sweep took about 6 seconds over RPC, so scanning is affordable.
- PKHeX-Plugins `RamOffsets.cs` (MIT) gives box-1-slot-1 addresses: X/Y 1.5 0x8C861C8, OR/AS 1.4 0x8C9E134, S/M 1.2 0x330D9838, US/UM 1.2 0x33015AB0. Box, not party, but a second source.
- Fit: port the RPC client and parsers to Kotlin for the probe. Effort: S-M, inside your 2 to 4 day M0. ROM: needs the player's decrypted 3DS dump inside Azahar; KaizoCore ships none.

## 2. The owner's ask: "turn your character into the Pokemon in your first slot"

The repo the owner means is Sprite Is Me (entry 1). No other repo makes the player's overworld sprite follow the first party slot; web and GitHub searches for it found nothing else. What exists, by game:

- **FireRed, LeafGreen, Ruby, Sapphire, Emerald (Gen 3).**
  - Overlay, dynamic: Sprite Is Me, a Lua script drawing over the game, BizHawk only, MIT.
  - Patch, static: `FRLG_Invisible_Trainer` IPS from the same repo blanks the trainer (FireRed 1.1) so nothing shows under the overlay.
  - Static swap at randomize time: FVX's Custom Player Graphics (section 3.5). It replaces the player's graphics from a pack folder (`data/players/<name>/info.ini`, RomType Gen1, Gen2, RSE or FRLG). 67 packs ship (84 entries across Gen 1, Gen 2, RSE and FRLG), among them Red, Leaf, Brendan, May, Cynthia, Wally, Kirby, Sonic, Link and Mario. The only Pokemon among them is Snorlax, for Gen 1 and Gen 2 only. It is fixed per seed, so it cannot follow slot 1. The mechanism is usable; the bundled packs are other people's characters (Nintendo, Sega, Toby Fox and others), so ship the mechanism, not the packs.
  - Followers (the lead walks behind you rather than being you): Pokemon Emerald Ambulation by Inkbox Software, romhacking.net hack 7266, "all 386 Pokemon follow", the first party slot follows, HGSS sprites, last updated 2022-11-03 (unverified, page blocked; a Chaos variant is 7267). Big hacks with followers (Modern Emerald, Emerald++, FireEmerald by ManicPacer, July 2026, BPS on Hackdex) are decomp-expansion builds that move every address, so the IronMON tracker would need its own address file, as Nat. Dex has.
  - Decomp only: pokeemerald-expansion has `OW_FOLLOWERS_ENABLED` (default FALSE, needs `OW_POKEMON_OBJECT_EVENTS`; https://github.com/rh-hideout/pokeemerald-expansion/blob/master/include/config/overworld.h ) and ghoulslash's Follow Me exists for pokeemerald. Both are code you build from source: no licence file, and the result is a whole new ROM rather than a patch on the player's dump.
  - FireRed: I found no vanilla-layout follower patch. Follower code lives in CFRU (Skeli789, no licence, last commit 2025-01-24), inside Radical Red and Unbound style hacks.
- **Red, Blue, Yellow, Gold, Silver, Crystal.** Yellow has Pikachu following natively. Nothing else found except the Snorlax skin.
- **DS.** HeartGold and SoulSilver have a native follower, nothing to add. Platinum: Following Platinum (entry 9). Diamond, Pearl, Black, White, Black 2, White 2: none found.
- **3DS.** None, and not part of the current plan.

How each one works, so the owner can pick: a script overlay (Sprite Is Me), a ROM patch applied to the player's dump (invisible trainer, Ambulation, Following Platinum), or a decomp build (expansion followers). Only the overlay changes the sprite dynamically with the lead.

## 3. The rest, by area

### 3.1 More patches for games the app supports
Same integration route as entries 2 to 5 (asset, output RomKind with measured CRC, PrepOptions row, NOTICE entry).

- **arexbold/Poke-Plat-Intro-Skip** https://github.com/arexbold/Poke-Plat-Intro-Skip . MIT, SHIP. Head 2022-09-17. Release 0.14 (2022-10-31) lists no attached files; release 0.13 (2022-09-17) has both "OG" and "Rev1" xdelta builds, about 23.6 MB each. Older, but the only Platinum patch with a clean licence and builds for both US revisions. Skips the pre-intro, many cutscenes and the catch tutorial, nicknames at the briefcase, instant Pokecenter heals, name rater in Sandgem. Effort XS-S.
- **PyroMikeGit/IronMonPlat** https://github.com/PyroMikeGit/IronMonPlat . None, LINK. Release v0.2.8b 2023-06-08, Platinum 1.0. Superseded by GIGA (entry 2).
- **SentorG/PlatFossilPatch** https://github.com/SentorG/PlatFossilPatch . None, LINK. v1.0 2025-01-12: GIGA 3 plus reviving fossils at the Oreburgh museum. The author says a fossil pivot asterisks the run. Skip.
- **DrMaple/Faster-FireRed-Super-Kaizo** https://github.com/DrMaple/Faster-FireRed-Super-Kaizo . None, LINK/ASK. Head 2024-04-25, release 1.0.2 (two IPS, one "No Blocks"). FireRed Rev 1, includes smart AI and Super Kaizo blocks (Nugget Bridge until Misty, S.S. Anne trainer rooms locked, Rocket Hideout until Erika, hidden items after Mt. Moon removed). This is the FireRed 1.1 Smart AI answer the Super Kaizo README itself recommends (in your modes report).
- **DrMaple/FactoryKaizoIronMON** https://github.com/DrMaple/FactoryKaizoIronMON . None, LINK/ASK. Release 1.1.1 2025-04-10: an IPS plus a custom ZX zip (1.2 MB) for the Factory Kaizo ruleset. Documentation is one line; ask before doing anything.
- **DrMaple/Faster-Sapphire** https://github.com/DrMaple/Faster-Sapphire . None, LINK/ASK. v1.0 2023-06-01, a 12 KB IPS, Sapphire Rev 1 only (SHA-1 4722efb8cd45772ca32555b98fd3b9719f8e60a9). Ruby and Sapphire have no patch in the app. By the author's own words it is not as feature-rich as Faster Emerald. Effort XS-S.
- **DrMaple/ironmon-patches** https://github.com/DrMaple/ironmon-patches . None. Releases: Faster Emerald 1.2.1 with the 6 percent level increase (IPS, 59,888 bytes), the Emerald QoL patch 1.1 (UPS), FRLGItemSpawns (2022-11-03). **DrMaple/FireRed-Tourney-Patch** (FireRedTourneyS12, 2023-10-14) is tournament-specific. Faster FireRed also has a "1.2 No Hidden Item Marks" IPS (8.5 KB) that exists only for 1.2, not 1.3.2.
- Community variants, all no licence, skip unless asked: swoardz/Faster-Emerald-With-Xs (1.2.1withX 2025-08-21), DrSeil/FireRed-Intro-Patch (V7.6 2023-01-28), FDMajora/ironmonqol (1.0.3 2023-03-06), seadogstingray/firered-skill-ironmon-patch (v6 2024-10-20).
- **hzla/PlatPatches** https://github.com/hzla/PlatPatches . None, ASK. Head 2026-09-28, no releases. A browser-side ARM9 patch applier for Platinum with about 40 toggles: faster walk, run and cycle, instant party healing, no poison step damage, VS Seeker QoL, forgettable HMs, no Surf or Waterfall party checks, infinite TMs, level-cap style options. Interesting as a model for entry 6 (options chosen at prepare time, no xdelta to ship), but it needs the author's word and a Kotlin port of assembly patches.
- **dansalvato/firered-nobgm** https://github.com/dansalvato/firered-nobgm . MIT, SHIP. v1.0.0 2024-04-25, disables FireRed music. Tiny; only useful to streamers avoiding music. The app already has audio controls; skip.
- **International.** TensaZangetsu22/Faster-FireRed-FR (French FireRed, "Patch FR 1.3", head 2026-08-18, no licence), Somnides/HGSS-Intro-Ironmon-FR (v1.1.0 2025-07-02), remyml57/HGSS-All-In-One-Ironmon-FR (v1.0.0 2025-05-14), all no licence. The Gen 3 tracker ships game addresses for FireRed Spanish, German, French, Italian and Japan (`GameAddresses/*.json`, MIT) and Piomale's French NDS tracker fork is GPL-3.0 (v6.3.10.2, 2026-06-17). `RomKind` is US-only, so non-English support is a scope decision, not a patch decision.
- **Forum-hosted collections, link-only reading list, not verifiable here:** Sierra's Miscellaneous Patches (pokecommunity thread 333996; says FireRed and Emerald), "Some patch for FireRed" and "Some patch for Emerald" (threads 501531 and 501571), "EM and FR Reusable TMs Patches" (405461), "ROM Hacking Patches Pack" (540279). All blocked to automated fetch.
- **Run indoors, EXP share, PSS-style storage.** I found no standalone patch for a vanilla ROM in a repo. Exalted Emerald (segabl, decomp, no licence) lists running shoes indoors and full EXP for the whole party. Gen 6-style EXP share exists as decomp code (a PokeCommunity thread credits Luno) and a PokeCommunity thread asks for a copy of the FireRed "EXP all" IPS. "PSS" I read as the Gen 8 storage UI; search snippets say it appears inside big hacks (Radical Red, Unbound; unverified) and I found no patch. If the owner meant the physical-special split, it is configurable in pokeemerald-expansion (decomp only), and Corvimae's Kaizo IronMON+ ruleset is the IronMON take on a split era (in your modes report). IronMON modes play one main Pokemon, so shared EXP and storage UIs matter mostly for casual or Nuzlocke play, not for tracked runs.
- **lhearachel/plat-qol** (no licence): the author marks it deprecated because it "breaks the evolution sequence". Do not use.

### 3.2 Hacks for the Hacks tab
The current list is in `HackLinks.kt`. Additions, all LINK, each opened at the creator's own page:
- **Elite Redux** (Emerald-based, four-ability system, difficulty and quality-of-life hack; site elite-redux.com, Hackdex page; version 2.65.x in early 2026 per snippets; source repo https://github.com/Elite-Redux/eliteredux-source has no licence).
- **Pokemon Following Renegade Platinum** and **Pokemon Following Platinum** (pokehacking.com list; entry 9).
- **Yellow Kaizo** https://github.com/CreamElDudJafar/Yellow-Kaizo . None, LINK. Release v1.0.4 2026-09-13. Remaster of Blue Kaizo (by SHF) with Hard and "Not Fun" modes, an instant text option and a Nuzlocke patch variant. Base game assumed to be Yellow from the name; revision not stated.
- **Emerald Ambulation** (romhacking.net 7266; section 2).
- **FireEmerald** (ManicPacer, July 2026, BPS, Kanto and Hoenn in one Emerald ROM with a follower and day/night; from press coverage, unverified) and **Pokemon Modern Emerald** (Hackdex, followers; unverified). Both expansion-based, so not tracker friendly.
- Excluded on purpose: every "ROM download" mirror that showed up in these searches (visualboyadvance.org, romsfun, pokeharbor, pokemerald.com, emulatorhacks and similar). They host pre-patched games, which your `HackLinks` rule and Google Play both reject.

### 3.3 Gen 3 tracker extensions (PC tracker wiki list, then newer ones)
The wiki's "User-Created Extensions" table has 21 rows (local copy `ironmon-ref/Ironmon-Tracker.wiki/Tracker-Add-ons.md`).

| Extension (author) | Repo, last commit | Licence | Verdict, fit, effort |
|---|---|---|---|
| Emerald and FireRed Nat. Dex (CyanSMP64) | CyanSMP64/NatDexExtension, 2026-06-29 | none, permission on file | already in |
| Roguemon (Crozwords, something_smart) | see 3.6 | none | ASK, XL |
| Ironmon Connect (WaffleSmacker) | WaffleSmacker/IronmonConnect-IronmonExtension, 2026-07-11 | MIT plus a credit clause for adaptations to another game (GitHub: NOASSERTION) | LINK: a Twitch viewer extension that needs their service (ironmonconnect.com) and BizHawk sockets; nothing to port |
| Smacker Tracker (WaffleSmacker) | WaffleSmacker/SmackerTracker-IronmonExtension, 2025-03-20 | MIT | SHIP. Port idea: an on-device seed-history page (dashboard HTML by madthehead); you already keep attempts. S-M, no ROM |
| Emerald Archipelago (WollyTD) | MicheleLeva/ArchipelagoEmeraldExtension, 2025-05-24 | none | LINK: needs Archipelago (3.6) |
| Auto Pokemon Themes (Fellshadow) | Fellshadow/Ironmon-Tracker-AutoPokemonThemes, 2025-02-26 | MIT | already in |
| Pokemon Stadium Brows (ninjafriend) | jtigues/BrowsTrackerAddOn, 2025-02-25 | none | skip, cosmetic |
| Walking Pixel Pokemon (eiphzor) | UTDZac/IronmonTracker-WalkingPixelPokemon, 2025-02-25 | MIT | skip: HGSS overworld art (Nintendo's); you have Walking Pals |
| I'm Attached (UTDZac) | UTDZac/ImAttached-IronmonExtension, 2023-09-29 | MIT | SHIP, XS: a heart button on the tracker card; no ROM |
| Auto-Pedometer (ninjafriend) | jtigues/AutoPedometerTrackerExtension, 2025-02-25 | none | reimplement, XS: pedometer on in Mt. Moon and the Underground Tunnels is two map ids |
| Tourney Point Tracker (UTDZac) | UTDZac/CrozwordsTourney-IronmonExtension, 2023-09-01 | MIT | SHIP, S: milestone points for the Crozwords tournament series (v3.3); niche |
| Pokemon Infinite Fusion (UTDZac) | UTDZac/InfiniteFusion-IronmonExtension, 2025-04-15 | MIT | skip: a different game's fusion calculator |
| Calc Atk (UTDZac) | UTDZac/CalcAtk-IronmonExtension, 2025-01-08 | MIT | already in |
| Sprite Is Me (UTDZac) | UTDZac/SpriteIsMe-IronmonExtension, 2024-03-18 | MIT | entry 1 |
| Death Quotes (UTDZac) | UTDZac/DeathQuotes-IronmonExtension, 2023-12-05 | MIT | SHIP, XS: custom game-over quotes; fits the Game Over popup; no ROM |
| Name That Pokemon (ratcityretro) | ratcityretro/NameThatPokemon-IronmonExtension, 2026-02-12 | none | skip: chat redeem |
| Favorites As Sources (UTDZac) | UTDZac/FavoritesAsSources-IronmonExtension, 2026-04-11 | MIT | SHIP, S: OBS image sources for favourites; your browser source already covers it |
| Faster Battle Intro (Subwild) | mdmurphy2/Ironmon-fastforward, 2023-10-25 | none | skip: fast forward exists in the app |
| Encounter Automation (jciii91) | jciii91/ironmon-encounter-automation, 2026-01-28 | GPL-3.0 | SHIP, M; see below |
| Safari Info Command (rudyp) | rudypdev/Pivot-IronmonExtension, 2025-08-15 | none | skip: a chat command |
| Pixel Font (Leopardly) | Leopardly/PixelFontExtension, 2026-01-23 | MIT | skip: the app uses Press Start 2P |

Newer, not on the wiki list:
- **jciii91/ironmon-encounter-automation** https://github.com/jciii91/ironmon-encounter-automation . GPL-3.0, SHIP. Head 2026-01-28, v1.2 on 2026-06-11. Enter a species and or level; it walks and flees until a matching encounter starts (pivot hunting). v1.1 added a fix for the player drifting off the tile. Fit: a small in-app macro over your input layer, off on tracked runs unless Blake decides otherwise (it is on the wiki's verified list, so the community accepts it). Effort M. ROM: reads the encounter from RAM.
- **jwunderl/EncounterDetails-IronmonExtension** https://github.com/jwunderl/EncounterDetails-IronmonExtension . MIT, SHIP. Head 2026-09-09, v2.0 2026-09-11. A per-battle action timeline with enemy HP bars on the 48-pixel ruler, weather and stat stages, viewed through a button on the tracker. Effort M.
- **Enemy HP Ruler** https://github.com/WaffleSmacker/IronmonHpRuler-IronmonExtension (MIT, v1.63 2026-08-22) and https://github.com/jwunderl/HP-Ruler-IronmonExtension (MIT, 2026-08-14). Ticks at every 10 and 25 percent on the enemy HP bar. XS, SHIP.
- **SencodingVT/BerryAlarm** https://github.com/SencodingVT/BerryAlarm . MIT, SHIP, head 2024-04-28. Warns when a valuable held item is worn outside an important area. XS.
- **WaffleSmacker/LabPuddles-IronmonExtension** https://github.com/WaffleSmacker/LabPuddles-IronmonExtension . MIT, head 2026-09-06. Puddles in the lab where earlier runs ended. XS-S, decorative.
- **jwunderl/OverworldItems-IronmonExtension** https://github.com/jwunderl/OverworldItems-IronmonExtension . None, ASK, v1.1 2026-09-11. A FireRed and LeafGreen Items tab: item balls and hidden pickups by floor or dungeon, live from save flags, names hidden until collected. It cites Kelsey Young's MIT item catalogue, so build the checklist from that (entry 7).
- WaffleSmacker's competition and stream helpers (MIT, but each feeds an outside service or Streamer.bot): IronmonVS (ironmonvs.com, needs a Windows monitor exe), IronmonRivals (ironmonrivals.com), IronmonBingo (Streamer.bot), DiscordAlerts, LabGambaTime, IronMob. LINK.
- Others, no value for the phone: Selipnir/EndlessKaizoTracker (MIT, a ruleset plus extension, head 2026-02-07), something-smart/InfiniteRepel (none), Mixone-FinallyHere/AutoNicknamer (MIT, 2023-06-06), JannickHansen/StarterSelection and QuizExtension (none), stableneo and cv-eko soul-link extensions (none).
- Dev guidance worth reading: besteon's "IronMON Community Developer's Guide" gist ca8f899f6a8ffde7c0e870f9d9fc7a38 asks patch makers not to shift the game's address space and to publish SHA-1 of the base and result. That is your compatibility test for every patch above.

### 3.4 NDS tracker, Gen 1 and Gen 2 trackers
- I found no extension loader in the NDS tracker (its tree has themes, network and extras folders only). Add-ons that exist:
  - **Shinnuu/hgss-ironmon-scout-balls** https://github.com/Shinnuu/hgss-ironmon-scout-balls . GPL-3.0, SHIP. Head 2026-07-01, v1.0.1 the same day. After ZX randomizes an HGSS ROM, it patches that ROM in place: for Route 29, 30, 31, 32, 46, Dark Cave and Sprout Tower it reads the walking encounters (encounter NARC a/0/3/7), appends a random-level wild-battle script per species (scripts NARC a/0/1/2, with a private script member and arm9 map-header repoint where the script bank is a stub), and injects a Poke Ball object event (a/0/3/2). Press A on a ball to fight that species at a level in its real range. It lets a player scout early pivots without grinding rare encounters. The Python is about 90 KB, 55 KB of it a charmap table (ndspy, which is GPL-3.0+, is the only dependency; `docs/rom-formats.md` documents the formats) and ZX already has NARC classes, so a Kotlin port after `ZxEngine` runs is realistic. HeartGold and SoulSilver only. Effort M-L. ROM: patches the player's randomized dump.
  - Rapout/HGSS-Ironmon-Scripts https://github.com/Rapout/HGSS-Ironmon-Scripts . None, ASK. Head 2026-05-06, French README. BizHawk scripts: No Encounter toggle, Show IV, ShowHiddenItemsAndTrainers (an overlay that marks hidden items and trainers, which HGSS has no patch for), Items Alerts.
  - Paul-Colin/ironmonstats-tracker https://github.com/Paul-Colin/ironmonstats-tracker . GPL-3.0. A French fork with Platinum key trainers and a live link to a companion app; reference only.
  - Piomale/NDS-Ironmon-Tracker-French (GPL-3.0), arexbold/TripleBondTracker (GPL-3.0, a Triple Bond Challenge fork), SilverstarStream/NDS-Ironmon-Tracker (GPL-3.0 fork, likely the origin of the B2W2 fix in section 4).
- Gen 1 and Gen 2 trackers (mollo010/Ironmon-gen-tracker, MIT, last commit 2024-03-18, v1.2.2; seadogstingray/Ironmon-gen-2-tracker, MIT, v0.4 2023-11-11): both carry only the extension template and screens. I found no published add-ons for either. Gen 3 extensions use Gen 3 APIs; whether they load on the forks is unverified.

### 3.5 Randomizers and their licences
- **upr-fvx/universal-pokemon-randomizer-fvx** https://github.com/upr-fvx/universal-pokemon-randomizer-fvx . GPL-3.0, SHIP. Head 2026-09-28, v1.6.1 on 2026-08-05, 137 stars, a release every few weeks. The actively maintained lineage (a merge of foxoftheasterisk's closer-to-vanilla and voliol's branches, built on ZX). Gen 1 to 7 including 3DS. Adds over ZX: Custom Player Graphics (Gen 1 to 3), palette randomization (Gen 1 to 3 full, 4 and 5 partial), a Base Stat Total panel (random buff or nerf, shuffle, follow evolutions; v1.6.0), a Level Caps section in the log (v1.6.0), Random Every Level and Force Growth for evolutions, "no convergence", battle-style randomization, type-chart randomization, reusable TMs before Gen 5, forgettable HMs Gen 1 to 5. Its version table lists ZX 4.6.1 as id 322, so its `SettingsUpdater` should upgrade the official ZX presets; I did not test that against the `.rnqs` files. Packages are `com.uprfvx.*` plus unprefixed top-level `compressors`, so it still needs the shading you did for ZX. It changes algorithms, so it is a second engine for casual runs, not a drop-in for official rulesets. Effort: L as a second engine, M to lift only Custom Player Graphics and the level-caps log.
- **something-smart/ironmon-randomizer** https://github.com/something-smart/ironmon-randomizer . GPL-3.0, SHIP. Head 2024-03-14, v2.3 on the same day (also a Roguemon build vR.1.1, 2024-06-08). A ZX fork of IronMON tweaks: Rebalance Encounters (its changelog names Black and White Kaizo as the use case), Ban Unown From Wild, Lock Imposter to Ditto, Revert Bad Berries, Speed Up Friendship Evos, 100% Non-Gym TMs, Randomize Type Chart, Strength Scaling, Standardize Stones (Gen 4). Your Survival Revival string came from it. Check whether any official preset you generated relies on a tweak ZX 4.6.1 lacks. Effort S to audit, M to port a tweak.
- Status of the base: ZX 4.6.1 (2024-11-23) is still the latest release and the Ajarmar repo has been idle since. Dabomstew/universal-pokemon-randomizer (GPL-3.0) was archived 2026-04-06. Nothing to update.
- Others, reference only: arexbold/TripleBondRandomizer (GPL-3.0, ZX fork for the Triple Bond HeartGold challenge, 2026-01-03), SilverstarStream/pokemon-entrance-randomizer (GPL-3.0, ZX-based entrance shuffle, Platinum US only, v0.1.1b 2021-12-15), Dabomstew/UPR-Speedchoice (GPL-3.0, 2022-04-25, Speedchoice hacks), bielsoler98/PokemonRandomizerZX (GPL-3.0, another Android port of ZX, 2026-02-26), PyroMikeGit's Smart AI Randomizer jar (in your modes report; no source repo found).

### 3.6 Whole modes and multiworld
- **Roguemon** (rules by Crozwords https://github.com/Crozwords/Roguemon , none; tracker extension by something-smart https://github.com/something-smart/Roguemon-IronmonExtension , none; current builds at https://github.com/drumstix576/roguemon-releases , none, v2.2.11-beta.0 on 2026-09-22). ASK. A FireRed roguelike on the Nat. Dex build: HP and status caps, prize rolls at milestones with items deposited into the bag, permanent-ability prizes, RogueStone (Moon Stone) evolutions, curses, segment tracking, its own randomizer build (the something-smart fork above) and settings strings. It is the most complete "mode with its own automation" in the community and matches the Nat. Dex FireRed you already ship. Effort XL and it needs the authors' permission; the randomizer part alone is GPL-3.0.
- **Archipelago** https://github.com/ArchipelagoMW/Archipelago . MIT with per-folder licences (the Emerald and Red/Blue worlds read as MIT). Head 2026-09-29, 0.6.7 on 2026-04-01, 0.6.8-rc1 on 2026-09-29. In-tree worlds: pokemon_emerald (last change 2026-09-27) and pokemon_rb. MIT third-party worlds for your other games: Platinum https://github.com/ljtpetersen/platinum_archipelago (v0.2.2 2026-09-22) and HeartGold and SoulSilver https://github.com/ljtpetersen/hgss_archipelago (v0.0.8 2026-09-22), both on https://github.com/ljtpetersen/apnds (MIT, DS ROM library), and a Crystal world (its repo not identified; crystal-ap-web bundles it). https://github.com/gerbiljames/crystal-ap-web (MIT, 2026-09-27) runs Archipelago's Python client in a browser by replacing BizHawk's socket with a shim, which is the template for a non-BizHawk client. For KaizoCore this means implementing the Archipelago WebSocket client, per-game RAM item delivery, and its patch formats (Red/Blue base patches are bsdiff4, a format your patcher lacks; RomPatcher.js reads it). High value for a niche crowd; effort XL per game family. ROM: player's dump.
- More community rulesets found beyond your report (rules text only, no licence, no code): Duper Kaizo (Reimittv; needs drumstix576's patch, not in the repo), Full Clearzo (ratcityretro, FRLG), Murphmon (BigMurph619), Maxdex (Tripc423), Subpar Kaizo (arexbold, has a custom randomizer jar and an rnqs), Krizz Kaizo (tehkrizz, a self-contained FireRed 1.1 BPS with a tracker helper, 2023), Ultimate+ (SentorG), Triple Bond Challenge (arexbold; HeartGold, its own 4.8 MB xdelta, tracker fork and ZX fork, 2026-01-11). Already in your report: Kaizo IronMON+, Touring, Minus (Corvimae).

### 3.7 Nuzlocke, soul link and race helpers
Two other reports now sit in this folder (`nuzlocke-trackers.md`, `nuzlocke-variants.md`); I did not read them, so this section may overlap. No Nuzlocke repo is needed for the IronMON tracker; these matter only if you add a Nuzlocke mode (dupes, species and shiny clauses, first-encounter-per-route capture, graveyard), which the tracker's per-area sightings already partly cover.
- **domtronn/nuzlocke.app** https://github.com/domtronn/nuzlocke.app . BSD-3-Clause, SHIP. Head 2023-05-27, 164 stars. `static/api/league` holds 167 boss-battle files (teams, levels, moves, abilities, items) for many games and hacks, plus routes and level caps. Redundant for randomized ROMs (ZX already reads the real trainer data) but useful for an unrandomized Nuzlocke.
- **jynnie/soullocke** (MIT, head 2026-08-20), **Ashenfactory/nuzlocke-tracker** (MIT, 2023-01-10), **diballesteros/nuzlocke** (BSD-3-Clause, 2025-07-29), **joos-too/Soullink-Tracker** (licence NOASSERTION, head 2026-09-28): web trackers; LINK, XS, no ROM. Soul link needs a second player's device anyway.
- **hzla/Desmume-Pokemon-Nuzlockers-Edition** https://github.com/hzla/Desmume-Pokemon-Nuzlockers-Edition . GPL-2.0 (check the source headers for "or later" before porting anything; DeSmuME code that is GPL-2.0-only cannot go into a GPL-3.0 app). v1_1_3 2026-08-18. Automatic trainer-battle logging and a fragsheet, party and box sync over websockets, an in-emulator Pokemon editor, in-game time selection. Idea source for a battle log.
- **hzla/Dynamic-Calc** https://github.com/hzla/Dynamic-Calc . None, LINK. A Showdown calculator fork with an enemy trainer team preview and imports, loads data for hacks. Common with randomizer players; link only.
- **dfoverdx/PokeStreamer-Tools** (none, 2021) and the two IronMON soul-link extensions (none): skip.
- Races and leaderboards: brdy's Kaizo run site https://www.stealmylyrics.com/kaizo/ (submit a run, data, evolution data, reports, patches; the NDS tracker's Evo Data screen links there). A Game Over "submit this run" deep link is XS and needs nobody's code.

### 3.8 Data, calculators, patch tooling
- **smogon/damage-calc** https://github.com/smogon/damage-calc . MIT, SHIP. Head 2026-09-29. The reference damage math for Gen 1 to 9. A Kotlin port of the Gen 3 to 5 paths would give exact damage ranges against the opponent from tracker data; today the app has Calc Atk and last-damage lines only. Effort M. ROM: none.
- **PokeAPI/pokeapi** (BSD-3-Clause, head 2026-09-29, release 2.9.0 2025-01-31) and **PokeAPI/api-data** (BSD-3-Clause): localized names, descriptions and flavor text as static JSON. The ROM already supplies names; only useful if you localize the UI. **PokeAPI/sprites** is NOASSERTION and the art is Nintendo's: do not bundle.
- **pret disassemblies** (pokeemerald, pokefirered, pokecrystal, pokeplatinum, pokeheartgold, pokediamond and the rest) and **rh-hideout/pokeemerald-expansion** (release expansion/1.17.1 2026-09-29): no LICENSE file in any of them, so nothing to bundle. Address and symbol names as facts are fine, as before.
- **veekun/pokedex** (MIT, 2022), **msikma/pokesprite** (MIT code, Nintendo art), **pkmn/engine** (MIT, Zig, a battle simulation engine): no use case now.
- **marcrobledo/RomPatcher.js** https://github.com/marcrobledo/RomPatcher.js . MIT. Reads IPS, UPS, APS (GBA and N64), BPS, RUP, PPF, EBP, BSDiff, VCDiff. You do IPS, UPS, BPS and xdelta; the gaps are APS, RUP, PPF and BSDiff (the last is needed for Archipelago Red/Blue patches). Reference for test vectors. XS-S per format.

### 3.9 Streaming and overlays
KaizoCore already serves the tracker as an OBS browser source. Nothing here is needed:
- pokejgameryt-ship-it/nuzlocke-overlay (MIT, 2026-09-23, PKHeX-based), Readek/Pokemon-Stream-Tool (MIT, VGC overlays), austinmilt/twitch-plays-ironmon (Apache-2.0, a Go chat-votes-inputs bot), WaffleSmacker's Twitch extension (LINK, section 3.3).
- **libretro/common-overlays** https://github.com/libretro/common-overlays . CC-BY-4.0, SHIP with credit. Bezels and gamepad overlays for handheld frames, head 2026-09-14. A skin source if you want more looks. S.
- libretro/libretro-database (CC-BY-SA-4.0, one-way compatible into GPLv3) has cheat files; cheats are off on tracked runs, so skip.

### 3.10 3DS, beyond entry 10
- **azahar-emu/azahar** https://github.com/azahar-emu/azahar . Source headers say "GPLv2 or any later version" (LICENSE is the GPLv2 text), so SHIP. Head 2026-09-28, release 2126.1.2 on 2026-09-20. Its libretro core lives in `src/citra_libretro`. Your notes in `docs/3DS-RESEARCH.md` stand.
- libretro/citra (GPL-2.0): the September 2026 commits are build and CI fixes (NDK r29, glslang, Linux and macOS arm64) on 2023-12 Citra code. Still not an emulation upgrade.
- Citra-Tracker-v2 (kcblack42, none, v1.6.1, 2025-04) and Citra-Tracker (accruenewblue, none, 2024-04-18): superseded by entry 10 as references; Axewc/pokemon-live-tracker (NOASSERTION) is a Spanish-language Omega Ruby note on the Citra GDB stub.
- SJTButler/kaizo-ironmon-3ds (MPL-2.0, 2026-09-22) is an mGBA fork for New 3DS hardware, not a Gen 6 or 7 tracker (already in your notes).

### 3.11 Emulator cores
- **JesseTG/melonds-ds** https://github.com/JesseTG/melonds-ds . GPL-3.0, SHIP. v1.4.0 on 2026-09-27, head the same day. A libretro melonDS remake. v1.4.0 adds a memory map (main RAM, ITCM, DTCM reachable by address) and exposes DTCM at 0x0E000000 for RetroAchievements, falls back to software rendering when the frontend's OpenGL context cannot be used, and its Android build is published as a prebuilt zip (`melondsds_libretro-android-Release.zip`, about 11.8 MB). Your DS core is the pinned LemuroidCores melonDS. Worth a side-by-side on a real phone (memory reads, save states, performance). Effort M, risk medium.
- **Hydr8gon/NooDS** (GPL-3.0, release 2026-08-09): a speed-focused DS core; a GitHub code search found no SET_MEMORY_MAPS (a search can miss, so unverified), which would mean core work before the tracker could read it. Skip unless low-end phones matter.
- libretro/SameBoy (MIT) is an accuracy alternative to Gambatte; no reason to switch.
- Vendored pieces, no reason to move: LibretroDroid 0.14.0 (2026-05-24) only adds viewport alignment; upstream mGBA's last stable is 0.10.5 (2025-03-09).

### 3.12 Themes, sprites, fonts
- No community theme-pack repo exists on GitHub (search for "ironmon theme" returns only Fellshadow's extension). Themes travel as pasted codes in Discord.
- The Gen 3 tracker ships 14 preloaded themes (`Constants.PreloadedThemes`, MIT): Fire Red, Leaf Green, Beach Getaway, Blue Da Ba Dee, Calico Cat and v2, Cotton Candy, GameCube, Item Bag, Neon Lights, Simple Monotone, Team Rocket, USS Galactic, Cozy Fall Leaves. Each is 11 hex colours plus two flags. Free to ship; "theme preset cycle/save" is OPEN in your parity list.
- The NDS tracker ships 15 `.colortheme` files (GPL-3.0): AlolanExeggcutor, Bulbasaur, Chalkboard, ChillBlue, CottonCandy, FireRed, LeafGreen, LightTheme, Neon, Pinky, RedBlueAndGreen, STONKS, Spaceship, VeryBlue, beach.
- Sprites: PMD Sprite Collab is already in (CC BY-NC 4.0, README confirms non-commercial with credit). Decoding art from the player's ROM (`RomSprites`) remains the right source for the game's own art. UTDZac/IronmonTracker-WalkingPixelPokemon (MIT code, HGSS art) and msikma/pokesprite (MIT code, Nintendo art) add nothing you can legally bundle.
- Fonts: Press Start 2P is in; Leopardly/PixelFontExtension (MIT) is redundant.

## 4. Updates to what is already in

| Component | In the app | Upstream now | Worth it? |
|---|---|---|---|
| besteon/Ironmon-Tracker | 9.3.1 (clone 2026-04-12) | v9.4.0, 2026-09-10 | Yes for reference. 9.3.0 added click-a-trainer info (drumstix576), GachaMon v2, Spanish and German UI text, encounter-area data fixes; 9.4.0 a language-use option, a Previous Log fix and a trainer-id lookup fix for ROM hacks. "Tap a trainer" and GachaMon are OPEN in your parity list. Language files: English, Spanish, German, French, Italian, Japanese (MIT). |
| Brian0255/NDS-Ironmon-Tracker | 6.3.10 (clone 2026-04-18) | 6.3.11, 2026-09-27 | Yes, a correctness item. On 2026-09-26 (commit 4a45e1aa) Black 2 and White 2 moved from static addresses to a pointer: read u32 at 0x24, mask 0xFFFFFF, add offsets. Vanilla base works out to 0x204D04 (0x21E42C minus 0x19728). Offsets: parentMapHeader 0x41B44, childMapHeader 0x41B5C, enemyTrainerID 0x5262E, playerBase 0x19728, playerBattleBase 0x53610, enemyBase 0x53B70, playerBattleMonPID 0x91BD0, enemyBattleMonPID 0x91C2C, itemStartNoBattle and itemStartBattle 0x194F8, statStagesStart 0x5661C, HPBattlePlayer 0x5652E, curHPBattlePlayer 0x56530, curBattleLevel 0x56538, curBattleStats 0x5660E, totalMonsParty 0x19724, berryBagStart and berryBagStartBattle 0x195B8, badges 0x21A24, repelSteps 0x2224D, facingDirection 0x38CF8, mapNPCIDStart 0x38CE8, abilityTriggerStart 0x90104, mainBattleDataPtr 0x526A8, doubleTripleFlag 0x900A0, someBattleUIPtr 0x90088; battleStatus stays static (0x1B5138 Black 2, 0x1B5178 White 2). Black 2 and White 2 share the table. `NdsGameMap.B2W2` still holds the old static values, and the app bundles Faster B2/W2. Upstream's release note says the change is so the addresses "work with the base game and any new patches", made with help from that patch's author (SilverstarStream). |
| RetroAchievements/rcheevos | 12.4.0 (vendored) | v12.5.0, 2026-09-14 | Yes, drop-in: a crash fix for `{recall}` of a constant in several alts, and hardening for malformed data. |
| Swordfish90/LibretroDroid | 0.13.2 | 0.14.0, 2026-05-24 | No, viewport alignment only. |
| Universal Pokemon Randomizer ZX | 4.6.1 | 4.6.1 (2024-11-23) | Nothing newer exists. |
| CyanSMP64/NatDexExtension | v1.2.1 | v1.2.1 (2026-06-29) | Current. |
| DrMaple Faster FireRed and Faster Emerald | 1.3.2 and 1.3.2 | same | Current. |
| SilverstarStream/faster_black2_white2 | v1.0 | v1.0 (2026-09-27) | Current. |
| PyroMikeGit/SuperKaizoIronMON HeartGold | 0.0.3 | 0.0.3 | Current. |

## 5. Checked and not recommended

- **billgreenwald/ironmon_emu** https://github.com/billgreenwald/ironmon_emu . Status unchanged: no LICENSE, and the grant wording is still not in NOTICE. The public tree still has no app source (top level: `claude_docs`, `mgba-android-memapi`, `screenshots`, `tools`, README and CHANGELOG); the head is version 3.9.0 on 2026-09-11. Its README says Nat. Dex 1.2.x support reads an address table the ROM exports. That is Cyan's feature, so look for the table's format in Cyan's own repo or wiki, not in this project's code, to keep your clean-room note true.
- **lhearachel/plat-qol**: deprecated by its author, breaks evolutions.
- **ROM-download and pre-patched mirror sites**: excluded, see 3.2.
- **Decomp builds and pokeemerald-expansion for on-device use**: no licence files, and a build is a whole ROM made from source rather than a patch on the player's dump.
- **Citra-Tracker and Citra-Tracker-v2**: no licence and superseded by entry 10.
- **ZSleyer/Encounty** (AGPL-3.0, desktop shiny counter), **kwsch/PKHeX** (C# save editor, licence NOASSERTION): no fit.
- **dfoverdx/PokeStreamer-Tools**, **twitch-plays-ironmon**: no use for the app.

## 6. Loose ends and things to verify by hand

1. romhacking.net hacks 7266 (Emerald Ambulation) and 7267 (Chaos Ambulation): confirm author, patch format, base revision and terms by opening the page in a normal browser.
2. Following Platinum: base revision, patch format, which of 1.1.1 or 1.1.3 is current, and one NDS tracker plus ZX test.
3. IronMONHGSS: does it patch SoulSilver at all?
4. IronMONPatchEditor offsets against Faster FireRed 1.3.2 and Faster Emerald 1.3.2 (apply, diff, then trust).
5. Black intro patch: base revision and whether White works.
6. Elite Redux, FireEmerald and Modern Emerald details come from search snippets; open the hack pages before adding rows.
7. FVX: load one official `.rnqs` preset through its `SettingsUpdater` and diff the output before calling it a drop-in.
8. PermaLocke versus your notes: reconcile 0x330128E4 (0x104 stride) against 0x33F7FA44 (484 stride) for USUM in the M0 probe.
9. hzla/Desmume-Pokemon-Nuzlockers-Edition is GPL-2.0: read the source headers for "or later" before porting.
10. WaffleSmacker's licence is MIT plus an attribution clause for adaptations to another video game; treat as MIT with credit and re-read it before shipping anything.
11. Any patch you bundle: apply it to the pinned dump, record output CRC and SHA-256, and run one randomize plus one tracker session, as you did for the others.

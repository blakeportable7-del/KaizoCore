# MaxDex Kaizo IronMON for KaizoCore (research, 2026-10-02)

Blake (2026-10-02): "do the research and find out how to add" MaxDex Kaizo IronMON by Trip. His description: "Expanded
version of NatDex and MAX Kaizo Ironmon combined. Includes Legends ZA Pokemon, Physical/Special split, and additional
abilities and moves through gen 9 (not all)."

**Verdict: it can be added, and the first version is size L.** MaxDex is a third pinned "expanded dex" build next to
Nat. Dex, but it cannot ride on what KaizoCore ships for Nat. Dex today. KaizoCore's Nat. Dex is 1.2.1; MaxDex is
built on the Nat. Dex **1.1.3** lineage, so it has a different ROM layout, a different randomizer version and settings
format, and different species and move numbering. A first version needs:

- a third randomizer engine: upstream Nat. Dex randomizer v1.1.3 source plus Trip's small delta, which this research
  identified completely;
- a MaxDex tracker map with three new decoders: 3-byte learnsets, a 12-byte move struct with a per-move
  physical/special byte, and MaxDex's own name and ability lists;
- data files converted from MaxDex's tracker extension;
- the 25.7 MB patch, bundled or imported, pinned by CRC (bundled since rc34: section 5, question 2);
- app wiring for a third "dex variant".

**How this was found.**

- Cloned https://github.com/Tripc423/Maxdex and its wiki, and read every file and every wiki page, including the
  deleted Ruleset page from the wiki's git history.
- Compared MaxDex-Randomizer.jar entry by entry with CyanSixFour's official `randomizer-1.1.3.jar`, using `javap`
  (bytecode listings). Nothing from either jar was run.
- Compiled the upstream v1.1.3 source myself to decode the settings file.
- Read MaxDex.bps's own literal bytes in memory to check the ROM layout. No ROM was used, no patch was applied,
  nothing decoded was written to disk, and no game was run.
- KaizoCore was read only, at `C:/Users/bepor/IronMonOne-wt-rc33` (branch fix/rc34-ds-audio, head 76a9a44).
- Downloads and scripts are in the session's research folder.

---

## 1. What it is

### 1.1 Identity and distribution

| | |
|---|---|
| Name | MaxDex Kaizo IronMON. Repo description: "Maxdex kaizo ironmon, Nat dex kaizo combined with an expanded Max kaizo" (https://github.com/Tripc423/Maxdex) |
| Creator | Trip, GitHub `Tripc423`. The extension's header says `author = "Trip"` (MaxDexExtension.lua:4) |
| Thanks | Wiki Credits page: CyanSMP64 "for the base NatDex Extension"; rh-hideout "for Pokeemearld-expansion [sic] for all of the new moves and abilities and animations"; 13 testers (section 6). https://github.com/Tripc423/Maxdex/wiki/Credits |
| Repo | Created 2026-06-19, last push 2026-06-27 (commit 4edff15). No licence file (GitHub API `license: null`). No releases or tags. 0 stars, 0 forks |
| Wiki | Three pages: Home, Credits, Installation. The Ruleset page was deleted on 2026-07-03 (wiki commit 03e86f0, "Destroyed Ruleset"). That is why it did not render on 2026-10-01 |
| Base game | Pokemon FireRed (USA) **v1.1**, CRC32 `84ee4776` (KaizoCore `RomKind.FIRERED_U_V11`, core-api/src/main/kotlin/com/ironmonone/core/Generation.kt:69-78) |
| Patch | `maxdex/MaxDex.bps`: BPS, 25,761,384 bytes. Source 16 MiB, CRC `84ee4776`; target 32 MiB, CRC **`28c12926`**. Patch CRC `3e0d1d95`, no metadata. SHA-256 `65d70b582f14b493863d2c31d1f668301dc370bf46ece9f40ecaf28e94b15057` |
| Randomizer | `maxdex/MaxDex-Randomizer.jar`: 1,275,529 bytes, built 2026-06-25 18:28, SHA-256 `b39836b01f668d1b31502741e62fbb64448f2e38df1eeb970f6b3c9762d0357b`. Reports `VERSION = 904`, `VERSION_STRING = "4.6.0-END112"`. Java 8 bytecode. No source published, no licence text in the jar. Only distributed in this repo (section 2.2) |
| Settings | `maxdex/FRLG MaxDex Kaizo.rnqs`: 112 bytes, settings version 902 (`4.6.0-END110`). Byte-identical (git blob `c2831c6c`) to CyanSixFour's `natdex/rnqs_files/FRLG NatDex Kaizo.rnqs` in NatDexExtension v1.1.3 (https://github.com/CyanSMP64/NatDexExtension/tree/461acb8f/natdex/rnqs_files) |
| Tracker extension | `MaxDexExtension.lua`, `version = "1.0"`, 12,601 lines, SHA-256 `f61fdd7b...`, plus a `maxdex/` folder: `MaxDexAbilities.lua` (289 abilities), `MoveEffectData.lua` (descriptions and effects of moves 0 to 841), `sprites_mons/412.png` to `1280.png` (869 32x32 icons), `sprites_mons/_bak_megaicons/1211-1255.png` (45 old mega icons, unused), `sprites_types/fairy.png`, and `config.ini` (the randomizer's own preferences: batch mode, an output folder on Trip's PC). Requires Ironmon-Tracker v8.5.0+ (`requiredVersion`, MaxDexExtension.lua:9) on BizHawk |
| Rules page | Deleted 2026-07-03. Its last text, before deletion, was only a link to the Nat. Dex ruleset (section 1.3) |
| Current version | Patch `28c12926`, uploaded 2026-06-25 18:34 -0500 (commit 73bb022). Jar of the same commit. Extension 1.0, uploaded 2026-06-27 (commit 4edff15). Nothing since |

Installation, as the wiki's Installation page gives it (https://github.com/Tripc423/Maxdex/wiki/Installation):

1. Put `MaxDexExtension.lua` and the `maxdex` folder in the Ironmon-Tracker's Extensions folder.
2. Patch a vanilla FireRed v1.1 with `MaxDex.bps` using Rom Patcher JS.
3. In the Tracker, use Extensions > Install and enable "Max Dex".
4. Set up New Runs with the patched ROM, `MaxDex-Randomizer.jar` and `FRLG MaxDex Kaizo.rnqs`.

The extension's settings-folder override points at `maxdex/rnqs_files/` (MaxDexExtension.lua:18-22), a folder the repo
does not have, so players pick the file by hand.

Patch history, read from the repo's git history (the CRCs come from each upload's footer):

| Commit | Date (-0500) | File | Target CRC |
|---|---|---|---|
| 5745dae | 2026-06-19 13:50 | MaxNatDex_Beta.bps | 0d907a5d |
| 94b2cf2 | 2026-06-20 07:54 | MaxDex.bps | 2fe2cc69 |
| b1e0787 | 2026-06-25 18:10 | MaxDex.bps | 28c12926 |
| 2e64349 | 2026-06-25 18:22 | MaxDex.bps (reverted) | 2fe2cc69 |
| 73bb022 | 2026-06-25 18:34 | MaxDex.bps (current) | **28c12926** |

Every upload's source is `84ee4776`. The jar was first uploaded as `randomizer-1.1.3-maxnatdex.jar` (5745dae), renamed
on 2026-06-20 (8b931e9), and rebuilt on 2026-06-25 (73bb022, one byte longer). The extension was uploaded on
2026-06-19, 2026-06-20 and 2026-06-27. The 06-27 upload is the one whose addresses match the current patch (section
2.3).

A second repo, **Tripc423/Emerald-MaxDex** ("WIP of emerald MaxDex, gen by gen", created 2026-08-05, last push
2026-08-30), has its own patch, jar, settings file and extension. Its patch (read with HTTP range requests, header and
footer only) maps Emerald (U) `1f1c08fb` to a 32 MiB `b32a0831`. It is out of scope for a first version.

### 1.2 What "MAX Kaizo IronMON" is

No page by that exact name exists. The source is champred's **Move/Ability Expansion (MAX)**:

- **Releases** (https://github.com/champred/pokeemerald/releases): Physical/Special Split Patch (split-v2,
  2024-04-21), FireRed Moveset Expansion (fr-me-v1/v2, May to June 2024), Emerald Moveset Expansion (2024-09-23),
  Emerald MAX (2024-11-30) and "FireRed MAX + MAX.Dex" (fr-max-v1, 2025-01-31, for FireRed v1.1 `84ee4776`).
- **What MAX is**, per its wiki (https://github.com/champred/pokeemerald/wiki): it builds on the Moveset Expansion
  by adding abilities and updating battle mechanics to newer generations. That is the physical/special split, about
  150 Gen 4 and 5 moves and about 50 abilities, with listed exclusions. MAX.Dex variants add Gen 4, or Gen 4 and 5,
  Pokemon.
- **Rules**: it is played with the IronMON rules, the bundled randomizer and the "MAXExtension" tracker extension.
  FireRed MAX ships Ultimate, Kaizo, Doubles and Survival presets, and "Standard difficulty is not supported" (fr-max-v1
  release notes). The wiki adds that MAX.Dex players may keep 4 or 5 favorites, by generation.
- **PC Tracker support**: Ironmon-Tracker knows both extensions by name. See `ironmon_tracker/CustomCode.lua:20-25`
  ("MoveExpansionExtension", "MAXExtension"), `GameSettings.lua:9-13` and the symbol notes at `GameSettings.lua:61-64`,
  which point at champred's builds. Clone: `C:/Users/bepor/ironmon-ref/Ironmon-Tracker`, head c450eca.

Trip's MaxDex calls itself Nat. Dex Kaizo "combined with an expanded Max kaizo". **Unconfirmed:** whether any of
champred's code is in the MaxDex ROM. Trip's credits name CyanSMP64 and rh-hideout, not champred, and nothing in the
repo says more. This would live on Discord or stream, which I did not use.

### 1.3 The rules in full

MaxDex's own rules page, every version (wiki git history, `research folder: Maxdex.wiki`):

| Wiki commit | Date (-0500) | Content |
|---|---|---|
| a2f7584 | 2026-06-19 | Same rules as Nat. Dex (link to the Nat. Dex Ruleset Changes page), plus extra banned abilities: As One-IR, As One-GR [sic, MaxDex's table has As One-SR], Grim Neigh, Chilling Neigh, Moody, Parental Bond. Plus extra banned moves: Draining Kiss, Roost, Psycho Shift, Drain Punch, Heal Pulse, Horn Leech, V-Create, Fell Stinger, Parabolic Charge, Grassy Terrain, Boomburst, Shore Up, Floral Healing, Strength Sap, Purify, No Retreat, Aura Wheel, Life Dew, Jungle Healing, Lunar Blessing, Take Heart, Bitter Blade, Matcha Gotcha, Oblivion Wing, and the 13 Let's Go partner moves (Baddy Bad, Bouncy Bubble, Buzzy Buzz, Floaty Fall, Freezy Frost, Glitzy Glow, Pika Papow, Sappy Seed, Sizzly Slide, Sparkly Swirl, Splishy Splash, Veevee Volley, Zippy Zap) |
| ded5baf | 2026-06-30 | V-Create taken off the move list |
| 71b71da | 2026-07-01 | Ability list removed; Fell Stinger, Boomburst, No Retreat, Aura Wheel and Veevee Volley taken off the move list |
| b906d29 | 2026-07-03 06:27 | Reduced to one line: "Same Rules as NatDex", with the link |
| 03e86f0 | 2026-07-03 09:09 | Page deleted |

Every move name on those lists exists in MaxDex's move table. So **today's rules are**: the core IronMON rules,
FireRed and LeafGreen's updates, and the Nat. Dex Ruleset Changes. KaizoCore already holds all three as text, generated
2026-10-01 by `tools/rules/build_rules.py`, in `app/src/main/assets/rulesets/FRLG-NatDex/kaizo.md`:

- Standard and Ultimate at lines 5-64.
- Kaizo at 65-115. Its "Kaizo Randomizer Settings" are no static level increase, no "Make Evolutions Easier", fully
  evolved at 30 and 3 more Pokemon for bosses. MaxDex's preset matches all four (section 2.2).
- FireRed and LeafGreen's updates at 117-123.
- The Nat. Dex page at 125-174.

The Nat. Dex Ruleset Changes page (https://github.com/CyanSMP64/NatDexExtension/wiki/Nat.-Dex-Ruleset-Changes; clone
`C:/Users/bepor/ironmon-ref/NatDexExtension.wiki/Nat.-Dex-Ruleset-Changes.md`, head 09edb68, 2026-09-29). Every rule,
in my words:

- **For all versions:**
  - The BST limit for banned abilities such as Huge Power goes up to 420 inclusive.
  - Up to 9 favourites, each counting only for the form named.
  - Ultimate and harder: HM moves may be used in battle if they were not taught by the HM items.
  - Kaizo and harder: Deoxys and Shaymin may change form, but the Move Reminder only in their starting form.
  - Hoopa Unbound is never allowed.
  - Survival: with Cut known and no HM01 yet, no banned held items until HM01.
  - Bending the rules may cost Hall of Fame eligibility.
- **v1.2.0+ only:**
  - Standard and Ultimate ban only Strong Legendary and Mythical Pokemon (Serebii's list). Any Legendary or Mythical
    of 600 BST or less may be the starter, except Cosmoem in Ultimate.
  - Kaizo and harder ban 600+ BST as well as Strong Legendary and Mythical, unless obtained by evolution.
  - Evo Kaizo has its own lines.
  - Favourites up to 600 BST, or above in Standard and Ultimate unless Strong Legendary or Mythical.
  - Three banlists, the Kaizo one naming the Legends Z-A megas.
- **v1.0.0 to v1.1.3 only:**
  - Kaizo, Survival and Super Kaizo: BST limit 599, or 600 for a starter, legendaries included. 600+ may come by
    evolution, except 601+ legendaries.
  - Standard and Ultimate: limit 640, and the evolution exception does not apply.
  - Favourites up to 600 BST in Kaizo, Survival and Super Kaizo, or 640 in Standard and Ultimate.
  - Two banlists. Neither names the Z-A megas, which 1.1.3 did not have.

**Open question, answered 2026-10-03:** MaxDex is a 1.1.3-lineage build, but its page pointed at the whole Nat. Dex
page, which has carried both sections since 2026-06-21. KaizoCore's generator dropped the 1.1.3 section because its
Nat. Dex is 1.2.1 (`tools/rules/build_rules.py`, `natdex_changes`), and MaxDex took the v1.2.0+ rules with it.

**Blake's ruling, 2026-10-03: "Max dex is allowed a bst 600 pokemon".** MaxDex follows the v1.0.0 to v1.1.3 section;
Nat. Dex 1.2.1 keeps its own. In KaizoCore (branch fix/rc34-maxdex-rules-favsources):

- **BST X** (`BstRule.lines(..., maxDex = true)`): Kaizo and the modes on it draw own 601, wild 600, and a legendary
  line of 601 that holds even after an evolution. So a 600 BST starter has no X and a 601 one has; a wild Pokemon is
  legal under 600; evolving to 600 or more is fine unless the Pokemon becomes a legendary of 601 or more. Standard and
  Ultimate draw 641 with no legendary line. The tracker cannot tell a starter from a gift, so every Pokemon of yours
  takes the starter's line, as Evo Kaizo's lab line already does.
- **Favorites** (`FavoriteBall.takeable(..., maxDex = true)`): up to 600 BST in Kaizo, Survival and Super Kaizo, 640 in
  Standard and Ultimate, and no Cosmoem rule (that one is v1.2.0+ only). Nine slots, as before.
- **Rules page** (`FRLG-MaxDex/kaizo.md`): the Nat. Dex section keeps the rules marked "v1.0.0 to v1.1.3 only" and
  leaves out the v1.2.0+ ones, banlists included, and the MaxDex section says which rules KaizoCore holds a run to.
- Trip still has not said; if he names v1.2.0+ one day, `maxDexLines` and the `maxDex` branch of `takeable` are the two
  places to change, and the generator's `natdex_changes(drop=...)` the third.

What the build enforces by itself:

- The randomizer never puts a species of 600+ BST, or Unown, in the wild (section 2.2).
- The preset blocks wild legendaries and keeps static encounters at their levels.

### 1.4 How MaxDex differs from plain Nat. Dex Kaizo

| | Nat. Dex 1.2.1 (what KaizoCore ships) | Nat. Dex 1.1.3 | MaxDex (28c12926) |
|---|---|---|---|
| Base / output CRC | 84ee4776 / 33779943 | 84ee4776 / (not pinned) | 84ee4776 / 28c12926 |
| Real species (u32 at 0x08000170) | 1258 | 1210 | **1255** (1210 + 45 Z-A megas) |
| Internal species ids | to 1283; Z-A megas at 1238-1283 | to 1235 | to 1280; Z-A megas at **1236-1280**, other order |
| Moves | table to 847, modern ones placeholders | 1-360 (6 Fairy moves added) | **1-841**: 481 Gen 4-9 moves at 361-841, own numbering |
| Abilities | names to 319; "before I begin to implement current gen moves, Abilities, and mechanics" (v1.2.0 notes, NatDexExtension.wiki/Changelog.md:17) | 1-77 | **1-289**, implemented per Trip's Home page |
| Physical/special | by type; Fairy special | by type; Fairy special | **per move** (byte 4 of each move) |
| Mechanics | Gen 3 plus Fairy, chart to Gen 6 | same | "updated moves and mechanics through gen 9" (Home page), ability pop-ups |
| Species struct / learnsets | 0x24 bytes / 4-byte entries | 0x1C / vanilla 2-byte packing (what the 1.1.3 randomizer reads on a v1.1 ROM; unconfirmed on a ROM) | 0x20 / 3-byte (Trip's randomizer change) |
| Trainer / item entry | 44 / 52 bytes | 40 / 44 | 40 / 44 |
| ROM address table | Game Freak header extended past 0x204 with RAM slots, read by the extension | header only; tracker addresses hardcoded | header only; tracker addresses hardcoded per build |
| Randomizer / settings version | 1.2.1, VERSION 908; refuses settings 906 or older | 1.1.3 jar, 904 | 1.1.3 jar plus Trip's changes, 904; preset is 902 |
| 1.2 features (modern HMs, debug menu, Regional Mineral, form changes, new items) | yes | no | no |

---

## 2. How it works

### 2.1 The patch and the ROM it makes

**Facts from the patch alone.**

- `MaxDex.bps` carries 33,521,914 of the 33,554,432 output bytes as its own literal data. Only 32,518 bytes come from
  the base ROM, at unchanged offsets.
- I decoded it in memory and read the values below. The script is `research folder: bpslit.py`; the probes are
  `bpsprobe*.py`.
- Unknown bytes are the ones copied from the base; I report a value only where its bytes are literal, or where the
  base's bytes are known zeros.

**Header.** 0xA0 to 0xBF is unchanged from the base, so the header still reads "POKEMON FIRE", BPRE, version 1, the
same as vanilla v1.1 and Nat. Dex. The header cannot tell them apart.

**Address table: Game Freak's ROM header at 0x100.** This is the `GFRomHeader` struct of pret's pokefirered,
`src/rom_header_gf.c` (https://github.com/pret/pokefirered/blob/master/src/rom_header_gf.c). MaxDex's values:

| ROM offset | Field | MaxDex value | Checked against |
|---|---|---|---|
| 0x128 | monFrontPics | 0x0824E5D4 | jar ini PokemonFrontSprites |
| 0x130 | monNormalPalettes | 0x08255D78 | |
| 0x138 / 0x13C / 0x140 | monIcons / palette ids / palettes | 0x0841E6A0 / 0x0841FD64 / 0x08420318 | |
| 0x144 | monSpeciesNames | 0x08246018 | jar ini PokemonNames; 11-byte names, last real one at 1280 |
| 0x148 | moveNames | 0x08249723 | jar ini MoveNames; 17-byte names, last at 841 ("Malignant Chain") |
| 0x150 / 0x154 | flagsOffset / varsOffset | 0x1078 / 0x1198 | extension's gameFlagsOffset / gameVarsOffset |
| 0x170 | pokedexCount | **1255** (vanilla 386) | the extension's detection comment, MaxDexExtension.lua:63-69 |
| 0x1BC | speciesInfo (base stats) | 0x08270988 | extension `GS.gBaseStats` |
| 0x1C0 | abilityNames | 0x082A4134 | 17-byte names. 1-77 are mixed case (76 is "-------"); 78-289 upper case ("TANGLED FEET" to "POISON PUPPETEER"); 290 is past the table |
| 0x1C8 | items | 0x084275D8 | 44-byte entries, 0-374 (374 = SAPPHIRE): the vanilla count |
| 0x1CC | moves (battle moves) | 0x08268000 | extension `GS.gBattleMoves` |
| 0x1E4 | bagCountItems | 120 | Nat. Dex's 120-slot Items pocket |

Nat. Dex 1.2's extra RAM-address slots past 0x204, which KaizoCore reads, do not exist here: bytes there are the base
ROM's.

**Tables and structs** (base ROM, before randomizing):

| Item | Address | Format, verified on literal data |
|---|---|---|
| Base stats | 0x08270988, 0x20 per species | See the layout below |
| Battle moves | 0x08268000, 12 per move | See the layout below |
| Learnsets | pointer table 0x0829E474 | 3-byte `{u16 move, u8 level}` ("jambo"), list ends `00 00 FF`. Base lists hold move-0 entries for moves the build lacks (Bulbasaur: `00 00 12`, a level-18 slot); the randomizer fills them |
| Trainers | 0x0823EC00, 40 per trainer | Trainer 414 is BROCK, 438 is TERRY (the rival's default name); class names at 0x0823E690 |
| Experience | 0x0826FCE8 | Vanilla 6 x 101 x u32 layout (medium-fast 0,1,8,27,64,125) |
| TM moves | 0x0849DECC | Vanilla FireRed's: Focus Punch, Dragon Claw, Water Pulse... |
| Starters | 0x08A0A6A1 | Bulbasaur at +0, Charmander at +503, Squirtle at +449. Upstream 1.1.3's `frlgStarter2Offset = 503`, `frlgStarter3Offset = 449` (Gen3Constants.java:96-97 at d53e0824) |
| Friendship to evolve | byte 0x081CD74A | Reads 219, so 220 to evolve, as the extension computes (GetEvolutionTargetSpecies + 0x13E) |
| Front pics and icons | | Entries exist for ids 1281-1283 past the last species, so the egg and ghost stand-ins likely moved there. Unconfirmed |

Base stats layout, from Bulbasaur, Charmander, Squirtle, Pikachu, Eevee, Turtwig, Venusaur-M, Dragonite-M, Scolipede-M
and Baxcalibur-M:

- 0-5 stats; 6-7 types (Fairy = 18); 8 catch rate; 9 unused (0).
- 10-11 EV yield; 12-13 and 14-15 items; 16 gender; 17 egg cycles; 18 friendship (50); 19 growth rate; 20-21 egg
  groups.
- **22-23 ability 1 (u16), 24-25 ability 2 (u16)**.
- 26 safari flee rate; 27 body colour; **28-29 exp yield (u16)**; 30-31 padding.

Battle move layout:

- 0-1 effect (u16); 2 power; 3 type; **4 category (0 physical, 1 special, 2 status)**; 5 accuracy; 6 pp.
- 7 secondary chance; 8 target; 9 priority; 10 flags; 11 padding.
- Fire Punch reads physical, Flamethrower and Psychic special, Roost status, and Flare Blitz has effect 312.

Physical/special is therefore a per-move byte inside each move entry, not a separate table.

**Species: internal ids 1 to 1280.**

- 1-411 are vanilla Gen 3 internal ids, with 252-276 as "?" placeholders.
- 412-1050 are national 387-1025 (internal = national + 25).
- 1051-1235 are Nat. Dex 1.1.3's 185 forms, megas and regional forms.
- **1236-1280 are the 45 Legends Z-A megas**, in this order: Dragonite-M, Raichu-X, Raichu-Y, Clefable-M,
  Victreebel-M, Starmie-M, Meganium-M, Feraligatr-M, Skarmory-M, Chimecho-M, Absol-Z (the extension calls it "Absol
  M", the same as Absol-M at 1085), Staraptor-M, Garchomp-Z ("Garchomp M", as 1092), Lucario-Z ("Lucario M", as
  1093), Froslass-M, Heatran-M, Darkrai-M, Emboar-M, Excadrill-M, Scolipede-M, Scrafty-M, Eelektross-M, Chandelure-M,
  Golurk-M, Chesnaught-M, Delphox-M, Greninja-M, Pyroar-M, Floette-M, Meowstic-M, Malamar-M, Barbaracle-M, Dragalge-M,
  Hawlucha-M, Zygarde-M, Crabominable-M, Golisopod-M, Drampa-M, Magearna-M, Zeraora-M, Falinks-M, Scovillain-M,
  Glimmora-M, Tatsugiri-M, Baxcalibur-M. Names from MaxDexExtension.lua:810-857.
- Species 412-1235 are identical in name, BST and evolution to NatDexExtension 1.1.3's (824 entries compared).

**Moves.**

- 1-354 vanilla.
- 355-360 Nat. Dex's Disarming Voice, Draining Kiss, Play Rough, Fairy Wind, Moonblast, Dazzling Gleam.
- 361-841 pokeemerald-expansion's Gen 4-9 list in its own order, Roost (361) to Malignant Chain (841), minus six:
  Belch, Teatime, Tera Blast, Revival Blessing, Shed Tail, Tera Starstorm.
- MaxDex's ids run 6 to 245 apart from Nat. Dex 1.2.1's for the same move (Psychic Noise is 839 here, 845 there).
  Every MaxDex move name exists in KaizoCore's `natdex/moves.tsv`. "Not all" moves are implemented, per Trip; which
  ones work is unconfirmed without play.

**Abilities.** 1-77 keep vanilla numbering. 78-289 are new (212), in MaxDex's own order: Tangled Feet 78 ... Multiscale
129 ... Thermal Exchange 177 ... As One-IR 269, As One-SR 270 ... Poison Puppeteer 289. This matches
`maxdex/MaxDexAbilities.lua` and the ROM's name table. It is neither expansion's nor Nat. Dex 1.2.1's numbering.

**Items.** No new items beyond Nat. Dex 1.1.x's, which reuse vanilla's unused slots: Dubious Disc 89, Razor Claw 90,
Razor Fang 91, Linking Cord 92, Shiny Stone 99, Dusk Stone 100, Dawn Stone 101, Ice Stone 102, Fairy Feather 226
(MaxDexExtension.lua:8929-8938, ROM item table).

### 2.2 The randomizer

**Where it lives.** Only as `maxdex/MaxDex-Randomizer.jar` in the MaxDex repo. Trip has no randomizer repo or release
(GitHub user `Tripc423` has two repos, both Lua; `gh api users/Tripc423/repos`).

**What it is, exactly.**

- It is CyanSixFour's official **`randomizer-1.1.3.jar`**, from NatDexExtension v1.1.3
  (https://github.com/CyanSMP64/NatDexExtension/blob/461acb8f/natdex/randomizer-1.1.3.jar), with 8 entries changed.
- The other 361 entries are byte-identical: `research folder: natdex-randomizer-1.1.3.jar` against the MaxDex jar,
  zip entry by entry.
- The official 1.1.3 jar also reports 904 "4.6.0-END112": it was built before upstream bumped the version.
- Upstream is CyanSMP64/universal-pokemon-randomizer-zx, branch `natdex`, release v1.1.3, commit
  d53e08242f8609062ffc17375806365a68f31e60 (2024-04-30). That is a fork of UPR ZX 4.6.0 and is **GPL-3.0**.
- Upstream's v1.1.2 and v1.1.3 differ only in `Version.java` and `gen3_offsets.ini` (compare 7530a5c0...d53e0824).

**Trip's 8 changed entries.** I compiled the upstream source with javac `--release 8` and compared method bytecode with
compiler noise removed (`research folder: cmpmethods.py`). I then compared every method's constants and field writes
against the official 1.1.3 jar, order-insensitively (`research folder: constdiff.py`), which lists the complete delta
below. The jar's Gen3RomHandler was recompiled from decompiled source: it calls `ArrayList.add` where upstream calls
`List.add` throughout. The real changes:

1. **`config/gen3_offsets.ini`.** The `[Fire Red (U) 1.1]` entry was rewritten for the MaxDex ROM:
   - counts: PokemonCount=1280, MoveCount=841, ItemCount=374, TrainerEntrySize=40, ItemEntrySize=44;
   - new explicit keys: MoveData, PokemonStats, PokemonNames, MoveNames, PokedexOrder, PokemonFrontSprites,
     MoveTutorCompatibility;
   - moved addresses: PokemonMovesets=0x29E474, PokemonEvolutions=0x28A434, TrainerData=0x23EC00, TmMoves=0x49DECC,
     TypeEffectivenessOffset=0x2A2284, and others;
   - CatchingTutorial offsets set to 0 and RunIndoorsTweakOffset to 0x1FFFFFF;
   - `CRC32=84EE4776` kept (the vanilla value);
   - the instant-text, music-fix, roamer and Ghost-Marowak IPS tweak lines removed from every FireRed, LeafGreen,
     Ruby, Sapphire and Emerald entry.
   The diff is 62 lines against upstream 1.1.3 (`research folder: up_d53e0824.ini`).
2. **`Gen3RomHandler`:**
   - `detectRomInner`: drops the "three Pokedex-order pointers" check, so it accepts any 8/16/32 MB BPRE v1 with the
     wild and map headers. A vanilla FireRed v1.1 or a Nat. Dex ROM would be "detected" and then written with MaxDex
     addresses, so the caller must gate on CRC.
   - `loadedRom`: **sets `jamboMovesetHack = true` unconditionally**. Upstream sets it false and turns it on only
     inside `basicBPRE10HackSupport`, which runs for FireRed 1.0 hacks (Gen3RomHandler.java:419, 451-453, 591-603 at
     d53e0824). This makes the randomizer read and write MaxDex's 3-byte learnsets (`{u16 move, u8 level}`, ending
     `00 00 FF`; Gen3RomHandler.java:2118-2125). `loadedRom` also takes PokedexOrder from the ini when present.
   - `basicBPRE10HackSupport`: trimmed (some offset fallbacks and its own jambo switch removed). It runs only for
     BPRE version 0, so never for MaxDex.
   - `loadMoves` / `saveMoves`: the 12-byte layout above (u16 effect; power +2, type +3, accuracy +5, pp +6, target +8,
     priority +9, flags +10). The category byte +4 is neither read nor written. **The randomizer still assigns
     physical/special by type, Gen 3 style.** It uses that only for move-choice heuristics that the official preset
     turns off (`betterTrainerMovesets`, `movesetsForceGoodDamaging` false), so it does not touch the official game.
   - `loadBasicPokeStats` / `saveBasicPokeStats`: abilities as u16 at +22 and +24.
   - Species stride 28 changed to 32 in `loadPokemonStats`, `savePokemonStats` and `loadPokedex`.
   - `highestAbilityIndex()`: 77 changed to **289**, so random abilities come from all 289.
   - `loadAbilityNames`: reads 312 names. The table has 290; the extra 22 are never chosen.
   - **`bannedForWildEncounters`**: Unown plus **every species with BST 600 or more**, whatever the settings.
3. **`AbstractRomHandler.setPokemonPool`**: the Gen 9 range ends at 1255 instead of 1210 (Terapagos-S), which takes in
   the 45 Z-A megas.
4. **Inner classes** `Gen3RomHandler$1`, `$Factory`, `$RomEntry`, `$StaticPokemon`, `$TMOrMTTextEntry`: recompiled, no
   logic change found.

**Settings.**

- The preset is version 902. The 1.1.3 code reads it: it refuses only versions with a top byte between 1 and 172, and
  passes 902 through `SettingsUpdater` unchanged.
- KaizoCore's engine-natdex refuses it: "version <= 906 ... too old", `engine-natdex/src/com/dabomstew/pkrandom/Settings.java:340-342`.
- Decoded with the self-compiled upstream 1.1.3 classes (`research folder: ReadRnqs.java`). The string as 904 is
  `WRIkEjL8AP8AAgGRAAKeBhsECQEAFAAyCQAuEgAAG/8ABRAw5ATkAoZICTIGBAIyAAUYEEZpcmUgUmVkIChVKSAxLjFMRoqz48M4ig==`.
- What it sets:
  - Levels: trainers and wild +50%; statics not raised.
  - Base stats random; Fluctuating curve; Strong Legendaries slow; abilities random (trapping banned, duplicates
    weighed together).
  - Starters fully random, with random held items; evolutions random (similar strength, same typing, forced change,
    impossible ones changed).
  - Movesets fully random with 4 guaranteed moves and evolution moves for all.
  - Trainers random, fully evolved from 30, rival keeps the starter, +3 for bosses, random names and classes.
  - Wild by area, legendaries blocked, minimum catch rate 4, random held items.
  - Statics random; TMs random with full HM compatibility; TM and HM compatibility random; tutors unchanged.
  - Trades random with OTs randomized; field and pickup items random.
  - Generation limits: every generation allowed, megas allowed, regional forms allowed, Eternamax not.
  - Tweak bits 331824: running shoes indoors, random PC Potion, ban Lucky Egg, balance static levels, run without
    running shoes. `Settings.tweakForRom` drops the ones a ROM does not support.

### 2.3 The tracker extension

**What it does at startup** (MaxDexExtension.lua:573-595):

- **Detection** (MaxDexExtension.lua:61-69): it reads u32 0x08000170 and accepts 1210 to 1999. That also accepts Nat.
  Dex 1.1.3 (1210) and 1.2.x (1258), so with this extension on, a Nat. Dex ROM gets MaxDex's addresses.
- It forces the Gen 7+ icon set and Tracker-side names, then installs the Nat. Dex 1.1.3 data plus its own.
- **Addresses: nothing is read from the ROM's header.**
  - `updateGameSettings` (MaxDexExtension.lua:10334-10524) hardcodes the FireRed block. Party 0x020242D0, enemy party
    0x02024078, party count 0x02024075, battle struct pointer 0x02024034. gBattlerAttacker / Target / script pointer at
    0x02023D7C / D7D / D84. Hit marker 0x02023DE0, taken damage 0x02023D68, weather 0x02023F64, outcome 0x02023ED2,
    communication 0x02023ECA, statuses at 0x02023DC8 to 0x02023F68. Map header 0x0203641C, special vars 0x020366F0 /
    0x0203BB60, special flags 0x02036700, trainer opponent 0x02037CCE, summary screen 0x0203BF6C. gBattleMainFunc
    0x03004BB4, battle results 0x03004BC0, save block pointers 0x03004C38 / 0x03004C3C, tasks 0x03004CC0.
  - Save data: flags 0x1078, vars 0x1198, stats 0x1398, badges = flags + 0x104, encryption key 0x40C, Items pocket of
    120 slots.
  - Battle phase functions: 0x08014011 / 0x0801427D / 0x08014DE1 / 0x08017039.
  - The 16 `BattleScript_*` addresses for move tracking, about 40 ability-script addresses, and the ability pop-up at
    `gBattlerAbility` 0x02022B50 / `BattleScript_AbilityPopUp` 0x081DFD04.
  - Tables: base stats 0x08270988, moves 0x08268000, exp 0x0826FCE8, TMs 0x0849DECC, learnsets 0x0829E474, trainers
    0x0823EC00, class names 0x0823E690.
  - Everything not set (battle type flags, battler count, battle mons, party indexes, turn order) falls through to the
    PC Tracker's vanilla FireRed v1.1 JSON.
  - The block's own comment: "These are this build's values (resync if the ROM is rebuilt)".
- `updateProgramAddresses` (10320-10332): base-stats stride 0x20, BattlePokemon 0x5C with types at 0x22,
  doubles partner 0xB8, and the move-data read offset 2.
- **Core-function relocations** (100-145, 179-530):
  - learnsets read as 3-byte entries;
  - abilities re-read as u16 after `buildData`;
  - move power, type, accuracy, pp and category read live from the ROM, with category re-asserted from byte 4;
  - Freeze-Dry (id 578) super effective on Water;
  - enemy-team validity relaxed for move ids above 360;
  - the ability pop-up reveal: when the game shows a pop-up, the ability is recorded;
  - move summaries replaced by `MoveEffectData` text;
  - `canShowUnknownMoveLearnSets` limited to Open Book;
  - and a replaced **MoveHistoryScreen** (452-530).
- **Data** (626-8806, 9688-10318): species, moves, evolution stones and details, the Fairy type and a Gen 6 type chart
  (10228-10248), BST and evolution updates for vanilla species, move renames, 289 abilities, move and ability
  descriptions (8942-9650), random-evolution data (10694-12381), and internal-to-national id maps (12383-12599).
- **Compared with NatDexExtension 1.1.3** (`research folder: ext_diff.py`): Trip's changes are only the header, the
  detection, the relocations, the 45 Z-A megas, the move placeholders 361-841 (name only; stats come from the ROM),
  the descriptions, the 289-ability table, the struct sizes and the FireRed address block. The random-evolution data
  is 1.1.3's unchanged and names no Z-A mega.

**Behaviour KaizoCore must not copy, or must get right:**

- **Reveals hidden information.** `max_buildOutHistory` (MaxDexExtension.lua:452-530) lists the species' **randomized
  ROM learnset by name**, up to the viewed Pokemon's level, outside Open Book. The base Tracker shows only moves seen in
  battle when learnsets are randomized (`ironmon_tracker/screens/MoveHistoryScreen.lua:95-103`), and so does KaizoCore
  (`app/src/main/kotlin/com/ironmonone/app/MoveHistoryStats.kt:24-33`). Opened on an opponent (TrackerScreen.lua:352),
  it shows the moves that Pokemon most likely has. **KaizoCore should keep its own behaviour.** See section 5.
- **Stale ids** (likely extension bugs; check on a device):
  - `EggId = 1236` and `GhostId = 1237` (MaxDexExtension.lua:9693-9694) are Nat. Dex 1.1.3's values. In MaxDex those
    ids are Dragonite-M and Raichu-X.
  - `offsetExpYield = 0x1A` (9691) is 1.1.3's offset. In MaxDex's 32-byte struct the exp yield is at 0x1C, and 0x1A is
    safari rate and colour.
  - The FireRed block never sets `gBattleScriptingBattler`, so the Tracker uses vanilla 0x02023FDB. The extension's
    second, "Emerald" block, which repeats MaxDex's FireRed RAM symbols value for value wherever both set one, gives
    gBattleScripting at 0x0202400C, so battler = 0x02024023.
- **Update check.** `github = "CyanSMP64/NatDexExtension"` (MaxDexExtension.lua:7, the 06-27 upload), so "Check for
  Updates" compares MaxDex 1.0 with Nat. Dex's latest release.

---

## 3. What KaizoCore already has

**ROM kinds and patch.**

- `RomKind.FIRERED_U_V11` (84ee4776) and `FIRERED_NATDEX_121` (33779943, `isNatDex = true`, made with `.copy()`) are
  in `core-api/src/main/kotlin/com/ironmonone/core/Generation.kt:69-78, 112-120`. `allNatDex` is at :314.
- `RomKind.engine` is derived from `isNatDex` alone (Generation.kt:61). `Engine { ZX, NATDEX }` is in
  `core-api/src/main/kotlin/com/ironmonone/core/Platform.kt:59`.
- The Nat. Dex patch is bundled: `app/src/main/assets/patches/natdex-firered.bps` (26,022,668 bytes, 84ee4776 to
  33779943). Applied by `PrepRun.kt:68-81`, which accepts only an output CRC in `RomKind.allNatDex`.
- Ruleset patches use the generic PATCH path, `PrepRun.kt:50-66`: a bundled asset named `<tag>-<base id>.<ext>`, with
  the output CRC checked against the out-kind (`PrepOptions.kt:68-101`).

**Engines.**

- `engine-natdex`: CyanSMP64 fork at v1.2.1 (71e50737), VERSION 908, package `com.dabomstew.pkrandom`
  (`engine-natdex/PINNED.txt`, `engine-natdex/build.gradle.kts:5-17`).
- `engine-zx`: ZX 4.6.1, renamed to `com.dabomstew.pkrandomzx` and `zx`-prefixed packages so both fit in one APK.
  The rename is commit 133f0f25, recorded in NOTICE:105-114.
- Both share `RandomSource`'s interrupt change (NOTICE:111-112, 120).
- Dispatch is one `when (kind.engine)` in `app/src/main/kotlin/com/ironmonone/app/engine/Randomizers.kt:61-64`, with
  `engineId` at :88-91. Calls are serialized by `engineLock` (:48).
- The Nat. Dex call sequence is `engine/NatDexEngine.kt:33-82`.
- Other engine switches: `engine/GameFacts.kt:57-59` and `GameBuild.kt:278` (`settingsClass`).

**Settings and modes.**

- `RnqsInfo.parse` sets Nat. Dex from a `natdex` token (`app/src/main/kotlin/com/ironmonone/app/RnqsInfo.kt:184-195`);
  the sidecar and engine fallbacks are at :117-175. **"FRLG MaxDex Kaizo.rnqs" would parse as a vanilla FRLG Kaizo
  preset today.**
- `RulesetCatalog.isCompatible` matches family plus the Nat. Dex flag (`RulesetCatalog.kt:59-63`), and `forRom` builds
  modes from the preset files (:103-116).
- `RunPairing` refuses a mismatch with two messages (`RunScreen.kt:918-933`); the engine label is at :262-266.
- `RulesDialog.dirFor` picks `<family>-NatDex` (`RulesDialog.kt:38`).
- Presets ship in `app/src/main/assets/presets/` (ten `FRLG NatDex v1.2 *.rnqs`). Rules pages are in
  `assets/rulesets/FRLG-NatDex/`, generated by `tools/rules/build_rules.py`.

**Tracker (tracker-gba).**

- `GameMap.resolve` reads 0x08000170. **1258** gives the Nat. Dex map, built from the ROM's slot table; anything else
  gets a header-picked vanilla map (`tracker-gba/src/main/kotlin/com/ironmonone/tracker/GbaTracker.kt:683-687,
  777-843, 845-1126`). **A MaxDex ROM (1255) would get the vanilla FireRed v1.1 map today and read garbage.**
- PlayScreen builds the tracker with `GameMap.resolve(reader)` (`app/src/main/kotlin/com/ironmonone/app/PlayScreen.kt:927-928`).
  The ROM-hack research already says a pinned hack must pick its map by kind, not by header
  (`docs/research/kaizo-on-rom-hacks.md:30-34`).
- Library files are tracked only on a CRC match (`GameSession.kt:46-47`).
- Layout switches already in `GameMap`:
  - `baseStatsStride` and `abilitiesAreU16` (GbaTracker.kt:64-67, 100; decoding at :1828-1843). The u16 offsets
    0x16 / 0x18 already match MaxDex.
  - `learnsetWide`, for the 4-byte format only (:250, decoder :3306-3341).
  - `namesFromLists`, which loads `/natdex/species.tsv` and `/natdex/moves.tsv` (:162, :1701-1706).
  - `expandedSpeciesIds` (:178).
  - `abilityScriptTable` (:60).
  - `MoveScripts`, with a Nat. Dex slot reader (`EnemyMoveWatch.kt:9-56`).
  - `TrainerLayout`, vanilla 40-byte by default (GbaTracker.kt:10-32).
- `moveData` reads the vanilla move layout (power +1 ... flags +8; :1751-1764). Category comes from `gen3Category`:
  type 0-8 physical, otherwise special, 0 power status (:1175-1179).
- Ghost stand-in ids are 413 vanilla and 1285 Nat. Dex (:1656-1658, :3720).
- Move tracking allows ids up to 2000 on expanded builds (:4012).
- Abilities of u16 builds are taken from the party (:4106).
- Data files: `natdex/species.tsv` (1283 rows) and `natdex/moves.tsv` (847), extracted ad hoc in commit 46bc82cf
  ("app v0.8.0: enemy panel + full Nat. Dex names"). There is no script for them in tools/.
- `gen3/species-extra-natdex.tsv` comes from `tools/trainer-data/convert_natdex_species.py`, which runs the
  extension's `natDexMons` literal through lupa and asserts 872 rows. `gen3/abilityscripts-natdex.tsv` holds 42
  slot-relative rows.
- `gen3/movedesc.tsv` and `abilitydesc.tsv` cover only 1-354 and 1-77.
- **Random Evos** loads only `/gen3/revos.tsv`, the vanilla Tracker's data, for every GBA map (GbaTracker.kt:2526-2559).

**App.**

- Favorites: 9 slots on Nat. Dex ("You may have up to 9 favourites") and no dex cap
  (`app/src/main/kotlin/com/ironmonone/app/Favorites.kt:68-91`), names from `natdex/species.tsv` (:17-29).
- Sprites: `gbasprites/1..1285.png`, the Nat. Dex extension's 32x32 pack by 1.2.1 id (`PcTracker.kt:345-364`), checked
  by `app/src/test/kotlin/com/ironmonone/app/SpritePackTest.kt`. Walking Pals for forms come through
  `assets/walkingpals-nat/natdex-map.tsv` (`tools/trainer-data/convert_walking_pals_nat.py`).
- `NatDexInfo.kt:21-58` describes the build and offers it.
- `LibraryStore.kt:80, 99, 414` labels Nat. Dex as a patched build.
- The log viewer maps names to ids for 1..1300 on expanded maps (`LogViewer.kt:106`).
- Credits: About lines at `AboutScreen.kt:463-467, 582-587`; NOTICE entries; `ModeCreditsTest.kt:15-24` requires each
  source in both, and each bundled patch's SHA-256 in NOTICE.
- Move bans are keyed by **name**, so MaxDex's numbering is no problem, and the lists already include later-generation
  healing and draining moves (`MoveRule.kt:74, 89-99`).
- `isNatDex` is read in about 50 places across 25 files (`grep -rn isNatDex app/src/main core-api/src/main
  tracker-gba/src/main`).

**Reuse versus new, piece by piece:**

| Piece | Reuse as is | Needs MaxDex work |
|---|---|---|
| Base dump, CRC gate, Prepare and patch path | FIRERED_U_V11; PrepRun PATCH path with CRC check | new out-kind (28c12926), asset or import flow, info text |
| Randomizer | nothing from engine-natdex (1.2.1 refuses 902 settings and the ROM layout differs) | third engine: upstream v1.1.3 plus Trip's delta (section 2.2) |
| Settings | the file itself | dex variant in RnqsInfo, catalog and pairing |
| Tracker RAM map | FireRed v1.1 values for battle type flags, battler count, battle mons, party indexes, turn order (identical in MaxDex) | about 40 MaxDex addresses (section 2.3) |
| ROM tables | the Game Freak header at 0x100 (same slots the Nat. Dex resolver reads at 0x1BC, 0x1C0, 0x1C8, 0x1CC, 0x150, 0x154, 0x1E4) | learnset, trainer and starter addresses (no header field) |
| Base stats | stride and u16-ability switches | stride 0x20; exp yield at 0x1C if used |
| Learnsets | the reader | a 3-byte decoder ending at 00 00 FF |
| Moves | the reader | a second layout and the per-move category byte |
| Names | `namesFromLists` mechanism; all move names exist in natdex/moves.tsv | MaxDex species, moves and abilities lists |
| Type chart | Nat. Dex's Gen 6 overrides (GbaTracker.kt:1450-1460) | Freeze-Dry exception |
| Ability tracking | u16 party path; abilityscripts mechanism | absolute-address table; optional pop-up reveal |
| Move tracking | EnemyMoveWatch | a `MoveScripts` constant |
| Sprites | pack mechanism; Nat. Dex icons for 1-411 | MaxDex's 869 icons by MaxDex id (659 are byte-identical to some 1.2.1 icon) |
| Favorites, rules, credits | the 9-favourite rule, generator, test | variant switch; rules page; credits |
| Random Evos | the screen | a MaxDex table (1.1.3's data, converted) |

---

## 4. How to add it

**Recommended shape.** A **"FireRed + MaxDex 1.0" build** with **one mode, Kaizo**. That matches what Trip ships: one
patch, one jar and one preset, named Kaizo. It needs its own engine, tracker map and data, plus a `dex` variant through
the app. Pin exactly patch `28c12926`, the 2026-06-25 jar and extension 1.0. Trip's addresses change with every
rebuild, as the extension's own comment says, so a new MaxDex.bps means a new pin.

### Step 1. Recognize and build the game (S to M)

- `core-api/.../Generation.kt`:
  - add `enum class Dex { STANDARD, NATDEX, MAXDEX }` (or a second flag `isMaxDex`);
  - add `RomKind.FIRERED_MAXDEX_10 = FIRERED_U_V11.copy(id = "firered-maxdex-10", displayName = "Pokemon FireRed +
    MaxDex 1.0", expectedCrc = 0x28C12926L, natDexCapable = false, dex = MAXDEX)`, modelled like the Nat. Dex kinds
    (no `baseId`, so nothing treats it as vanilla);
  - add `allMaxDex` and add it to `all`;
  - make `engine` three-way.
- `Platform.kt:59`: `Engine { ZX, NATDEX, MAXDEX }`. Every `when (kind.engine)` stops compiling until handled, which
  finds Randomizers.kt, GameFacts.kt and the label sites.
- Audit each `isNatDex` site. Keep "Nat. Dex only" where it means Nat. Dex 1.2.1 (presets, engine, rules folder,
  NatDexInfo, Build your own). Add a shared `expandedDex` for behaviour that holds for both: 9 favourites, no dex cap,
  "already patched", Fairy chart, sprite pack switch.
- `PrepOptions.kt:68-101`: on FIRERED_U_V11 add an option "MaxDex (Nat. Dex plus moves and abilities to Gen 9)", using
  the PATCH path with asset `maxdex-firered-u-v11.bps` and out `FIRERED_MAXDEX_10`. Add a `describe()` line from a new
  `MaxDexInfo.kt` (like `NatDexInfo.kt:21-58`), written from Trip's Home page and the verified facts above.
- `PrepRun.kt`: the PATCH path already checks the output CRC. If the patch is not bundled, add an import-once flow like
  `NEED_NATDEX_PATCH` (`PrepRun.kt:92-94`), with a link to the MaxDex repo.
- Assets: `assets/patches/maxdex-firered-u-v11.bps` (25.7 MB, SHA-256 in NOTICE) **or** import, Blake's call (section
  5; bundled since rc34). Also `assets/presets/FRLG MaxDex Kaizo.rnqs`, the byte-identical 112-byte file.
- `LibraryStore.kt:80, 99, 414`: label MaxDex as a patched build. An imported, already patched MaxDex dump is
  recognized by CRC like Nat. Dex.
- **Tracker recognition: by kind first.** The session knows `FIRERED_MAXDEX_10`, so pass a map choice into the
  tracker (a `GameMap.forKind(kind, memory)` used at PlayScreen.kt:928). Add a guarded fallback in `GameMap.resolve`:
  header BPRE v1 **and** u32 0x08000170 == 1255 **and** u32 0x080001BC == 0x08270988, 0x080001CC == 0x08268000,
  0x08000144 == 0x08246018. These are Game Freak header pointers, all literal in the patch, and randomization does not
  move them. Refuse if any check fails, as the Nat. Dex resolver does (GbaTracker.kt:1117-1125).

### Step 2. The randomizer: `engine-maxdex` (L)

- **Recommended:** vendor upstream as source, the way the other two engines are vendored.
  - New module `engine-maxdex/` with `src` = CyanSMP64/universal-pokemon-randomizer-zx at **d53e0824** (release v1.1.3,
    GPL-3.0, `LICENSE.txt`), renamed to `com.dabomstew.pkrandommd` and `md`-prefixed packages, resource paths included,
    as engine-zx was renamed (NOTICE:105-114).
  - Set `Version` to 904 / "4.6.0-END112" so seeds, settings strings and logs match Trip's jar.
  - Port Trip's changes, each marked `LOCAL MODIFICATION (KaizoCore, MaxDex)`:
    - `gen3_offsets.ini` (Trip's copy);
    - in `Gen3RomHandler.java`: `detectRomInner`, `loadedRom` (jambo on, PokedexOrder from the ini), `loadMoves`,
      `saveMoves`, `loadBasicPokeStats`, `saveBasicPokeStats`, the stride in `loadPokemonStats`, `savePokemonStats`
      and `loadPokedex`, `highestAbilityIndex`, `loadAbilityNames` and `bannedForWildEncounters`;
    - in `AbstractRomHandler.java`: `setPokemonPool`'s 1255.
  - Add the `RandomSource` change the other engines carry.
  - Add `PINNED.txt` (upstream commit, Trip's jar SHA-256, golden hashes) and `build.gradle.kts` (copy of
    engine-natdex's).
  - Add `include(":engine-maxdex")` to `settings.gradle.kts`, `implementation(project(":engine-maxdex"))` to
    `app/build.gradle.kts:106-109`, and keep rules in `proguard-rules.pro` for the new packages.
- **Not recommended:** bundling Trip's jar relocated with a shading step. Android cannot run a jar as is
  (engine-zx/build.gradle.kts notes), the binary would be the one engine in the repo without source, and 361 of its 369
  entries are the upstream 1.1.3 build anyway.
- `app/.../engine/MaxDexEngine.kt`: a copy of `NatDexEngine.kt` on the renamed packages, `ID = "maxdex-1.0"`.
  **Refuse any source whose CRC is not 28c12926** before loading: Trip's detection accepts any BPRE v1.
- `Randomizers.kt:61-64, 88-91` and `GameFacts.kt:57-59`: add the MAXDEX branch. Build your own stays off for MaxDex in
  the first version.
- `ExtraPasses.kt:53`: no pre-pass. FireRed Kaizo is +50%, as the preset already sets.
- Validation: one-time golden runs on Blake's PC. Run Trip's jar and the port on the same `28c12926` base with the
  preset and 3 seeds, compare the output ROM SHA-256 and the logs, and record the hashes in PINNED.txt. A JVM test in
  `engine-maxdex/test` repeats the port's run when the base file is present and skips otherwise (as
  FasterFireRedTest does).

### Step 3. Convert MaxDex's data (M)

Add one committed script, `tools/trainer-data/convert_maxdex.py`, run as `python ... <Maxdex clone> <repo root>`. Like
`convert_natdex_species.py`, it runs the Lua literals through lupa (`research folder: cmp_ext.py` shows the loader). It
writes:

- `tracker-gba/src/main/resources/maxdex/species.tsv`, ids 1-1280. 1-411 come from `natdex/species.tsv` (identical);
  412-1280 from `pokeNameList`, spelled KaizoCore's way ("Dragonite-M"). Disambiguate 1246, 1248 and 1249 as Absol-Z,
  Garchomp-Z and Lucario-Z, because the extension names each the same as an older mega.
- `maxdex/moves.tsv`, 1-841. 1-354 are vanilla names with the extension's renames (MaxDexExtension.lua:10263-10307);
  355-841 come from `natDexMoves`.
- `maxdex/abilities.tsv`, 1-289, from `MaxDexAbilities.lua` with the two renames (10309-10315).
- `gen3/species-extra-maxdex.tsv` (evolution, weight; 1-411 with the extension's evolution overrides; assert 869 new
  rows).
- `gen3/abilityscripts-maxdex.tsv`, absolute addresses in the `abilityscripts-firered11.tsv` format, from the FireRed
  `GS.ABILITIES` lines (10411-10493).
- `gen3/revos-maxdex.tsv` from `overrideTryLoadRevoData`.
- Optional: `gen3/movedesc-maxdex.tsv` and `abilitydesc-maxdex.tsv` from `natDexMoveDescriptions`, `ndNewMoveDescs`,
  `MoveEffectData` and `ndNewAbilDescs`.
- `app/src/main/assets/gbasprites-maxdex/412..1280.png`, copied from `maxdex/sprites_mons` (32x32, about 0.5 MB).

### Step 4. The tracker map and decoders (M to L)

In `tracker-gba/.../GbaTracker.kt`:

- Replace `learnsetWide` with `learnsetFormat { PACKED, WIDE4, JAMBO3 }`. JAMBO3 reads 3-byte entries and ends at
  `00 00 FF`, skipping move-0 entries, as the randomizer's own reader does. The extension stops at the first move 0,
  which on a randomized ROM is the same thing.
- Add `moveLayout { VANILLA, SPLIT }`. In `moveData` (:1751) SPLIT reads power +2, type +3, category +4, accuracy +5,
  pp +6, priority +9, flags +10, and the three `gen3Category` call sites (:1778, :1799, :3814) take the table's
  category. Calc Atk, coverage and the move rows then show real physical and special.
- Add `nameSet` ("natdex" or "maxdex") for the lists loaded at :1701-1706, plus per-map `speciesExtraTable`,
  `revosTable` (:2529), `ghostSpeciesId` and `eggSpeciesId` (:3720).
- Use MaxDex's own non-colliding stand-in for the ghost: 1237 is Raichu-X here.
- Add `GameMap.MAXDEX_FR_10`:
  - RAM: section 2.3's addresses. `scriptingBattler` 0x02024023 (unconfirmed, section 5). Battle type flags
    0x02022B4C, battler count 0x02023BCC, battle mons 0x02023BE4 and party indexes 0x02023BCE (FireRed's, unchanged).
    `battleTerrain` 0x02022B51 (vanilla is 0x02022B50).
  - ROM: base stats 0x08270988 (stride 0x20, u16 abilities, growth 0x13, gender 0x10, friendship 0x12), moves
    0x08268000 (SPLIT), exp 0x0826FCE8, learnsets 0x0829E474 (JAMBO3), trainers 0x0823EC00 / 0x0823E690 (vanilla
    `TrainerLayout`), names 0x08246018 (11), ability names 0x082A4134 (17), items 0x084275D8 (44), front pics
    0x0824E5D4 and palettes 0x08255D78, starters 0x08A0A6A1 with +503 / +449, friendship byte 0x081CD74A.
  - Save: flags 0x1078, vars 0x1198, stats 0x1398, badges 0x117C, key 0x40C, repel 0x1198 + 0x40.
  - Bag: Items 0x310 x 120, Balls 0x568 x 13, Berries 0x684 x 43. These follow from the extension's pocket formula
    (10369-10373) with the vanilla Items offset and pocket sizes in Ironmon-Tracker's
    `GameAddresses/Pokemon FireRed v1.1.json` (Items 0x310, Key Items 30, Balls 13, TM/HM 58, Berries 43). Verify on a
    device.
  - `battleMonSize` 0x5C, with types at 0x22.
  - `abilityScriptTable` "maxdex", `moveScripts = MoveScripts.MAXDEX_FR_10`.
- In `EnemyMoveWatch.kt`, `MoveScripts.MAXDEX_FR_10`:
  - focus punch 0x081DFA3A, snatched 0x081DF9E1;
  - confused 0x081DFB18 / 0x081DFB21 / 0x081DFB5B;
  - woke up 0x081DFA59;
  - in love 0x081DFB82 / 0x081DFB8B;
  - frozen 0x081DFA98 / 0x081DFA9B / 0x081DFA9D;
  - unfroze 0x081DFAA7 / 0x081DFAAC.
  All from MaxDexExtension.lua:10397-10409.
- Type chart: Nat. Dex's overrides plus Freeze-Dry (by name) super effective on Water.
- Do **not** port `max_buildOutHistory`. Keep KaizoCore's seen-moves history.
- Later: the ability pop-up reveal. The game draws the pop-up on screen, so recording it reveals nothing hidden
  (gBattlerAbility 0x02022B50, script 0x081DFD04 + 0..6).

### Step 5. App wiring and UI (M)

- `RnqsInfo.kt:184-195`: add a `maxdex` token (it does not contain "natdex") and a `dex=` sidecar key (:117-125). The
  engine check (:134-146) tries the MaxDex engine's `Settings.read`. Label: "FRLG MaxDex Kaizo".
- `RulesetCatalog.kt:59-63`: match the dex variant. MaxDex gets the Kaizo chip only, with a mode line from its rules
  file.
- `RunScreen.kt:918-933`: three-way pairing messages ("That mode is for the MaxDex version..."), and the engine label
  at :262-266.
- `RulesDialog.kt:38`: `dirFor(family, dex)` gives `FRLG-MaxDex`.
- `Favorites.kt:17-29, 68-91`: names from the build's own table, 9 slots, no cap. **Done in rc34, 2026-10-03**
  (fix/rc34-maxdex-favorites): `Favorites.idOf`, `suggest` and `inGame` with the game read `maxdex/species.tsv` on
  MaxDex 1.0, so the card, the lab line, the stream's pictures and the Kaizo IronMON screen's list use MaxDex's ids
  (the Z-A Megas at 1236 to 1280) and leave out Greninja-B, Squawkabilly-W and Meowstic-F-M.
- `FavoriteBall` and `BstRule` use whichever Nat. Dex rule version Blake picks (section 5). He picked v1.0.0 to
  v1.1.3 on 2026-10-03 (section 1.3).
- `PcTracker.kt:362`: the pack follows the map (`gbasprites-maxdex` above 411).
- `LogViewer.kt:106`: on MaxDex, map log names through the ROM's own name table (0x08246018). The log prints ROM names
  such as "DragoniteM" and "BaxcalibuM", which the display list does not.
- Where the player sees it:
  - Library > Prepare on a FireRed v1.1 dump offers "MaxDex", which makes the build.
  - The Kaizo IronMON screen shows the "FireRed + MaxDex 1.0" card with one mode, Kaizo, its rules, and the engine
    named.
  - In play, the tracker panel shows MaxDex names, icons and abilities (to 289), PHY, SPE or STA from the ROM, and
    Fairy.
  - Optionally, a MaxDex notice under FireRed v1.1, like `NatDexNotice` (NatDexInfo.kt:67-75).
- `AboutScreen.kt` and NOTICE: credits per section 6. `ModeCreditsTest.kt:15-24` gains `Tripc423/Maxdex` and the
  patch's SHA-256.
- Rules page: `tools/rules/build_rules.py` gains a MaxDex family that writes `assets/rulesets/FRLG-MaxDex/kaizo.md`.
  It holds the same sections as FRLG-NatDex plus the Nat. Dex section Blake picks, a short "MaxDex" section (same
  rules as Nat. Dex, per the deleted page), and the MaxDex wiki in its sources with the page's dates.

### Step 6. Tests (M)

- **Core:** `FIRERED_MAXDEX_10` CRC pin; the Engine dispatch is exhaustive.
- **Patch:** read source and target CRC out of the bundled `.bps` footer (as PatcherTest does for Nat. Dex).
- **Prepare:** PrepOptions offers MaxDex only on FIRERED_U_V11; PrepRun refuses a wrong output CRC.
- **Settings and pairing:**
  - RnqsInfo parses "FRLG MaxDex Kaizo.rnqs" as FRLG, Kaizo, MAXDEX;
  - RunPairing refuses MaxDex against a Nat. Dex preset, and the reverse;
  - RulesetCatalog offers only Kaizo on MaxDex.
- **Engine-maxdex:**
  - Version 904;
  - the bundled preset reads, and its settings string equals the one in section 2.2;
  - `bannedForWildEncounters` includes every 600+ BST species;
  - a CRC-gated refusal of a vanilla ROM;
  - the golden test when the base file is present.
- **Tracker:**
  - `MaxDexResolveTest`: synthetic memory with 1255 and the four pointers gives the MaxDex map; 1258 gives Nat. Dex;
    1255 with a wrong pointer is refused;
  - the JAMBO3 decoder on a captured byte run (Bulbasaur's list above);
  - SPLIT decoding: Fire Punch PHY, Flamethrower SPE, Roost STA, Flare Blitz effect 312;
  - base stats: Bulbasaur abilities 65 and 34;
  - `MoveScripts.MAXDEX_FR_10` values;
  - ghost id not a real species.
- **Data:**
  - `maxdex/species.tsv` has 1280 rows with unique names;
  - `moves.tsv` 841; `abilities.tsv` 289 (MaxDexAbilities.lua's order);
  - `species-extra-maxdex.tsv` 1280 rows;
  - sprite pack 412-1280 present (a `SpritePackTest` twin);
  - ModeCreditsTest.
- **Rules:** RunScreenKaizoTest reads the MaxDex mode line back from its file.

### Step 7. What a first version leaves out

| Later step | Size |
|---|---|
| Build your own on MaxDex (Settings reflection on the MaxDex engine) | M |
| Nuzlocke on MaxDex | M |
| Play as your Pokemon on MaxDex. **Done in rc34, 2026-10-03:** `Overworld.resolve` reads MaxDex's overworld out of its own code, as Nat. Dex's is read (`OverworldScan`). On Blake's dump with Trip's patch (`firered-maxdex.gba`, `OverworldScanTest`) all eight functions are found once and SetMainCallback2 confirms gMain (0x03002D20). Every block sits where the extension's RAM puts it: EWRAM 0x9E0 and IWRAM 0x3D0 below FireRed's. The lead is read by MaxDex's own numbering (`SpriteLead.dexOf`) | done |
| Walking Pals for MaxDex ids. **Done in rc34, 2026-10-03, with no table of its own:** `WalkingPals.Dex.MAX_DEX`. Ids to 1235 are Nat. Dex's; the 45 Z-A Megas, 1236 to 1280, are matched by name to Nat. Dex's ids from the two species tables when the sets load (`WalkingPals.maxDexToNatDex`), so natdex-map.tsv's sheets and stand-ins reach them. The tracker icons follow it; Always use keeps the Nat. Dex table's ids on every game | done |
| Ability pop-up reveal; MaxDex move and ability descriptions | S |
| Other MaxDex modes. Trip ships Kaizo only; the 1.1.3 Standard, Ultimate, Survival, Super Kaizo and Kaizo Doubles presets would load in this engine, but they are not MaxDex's | S, if Blake wants them |
| Emerald MaxDex (Tripc423/Emerald-MaxDex, WIP; own patch, jar, preset and extension) | L |

**Size.**

| Step | Size |
|---|---|
| 1. Recognize and build | S to M |
| 2. Engine | L |
| 3. Data | M |
| 4. Tracker | M to L |
| 5. App wiring | M |
| 6. Tests | M |
| **First version overall** | **L** |
| With the later steps | XL |

**Side findings in KaizoCore's existing Nat. Dex support** (not MaxDex work):

- Random Evos reads the vanilla `gen3/revos.tsv` on Nat. Dex builds too (GbaTracker.kt:2529-2531), while the Nat. Dex
  extension ships its own table (NatDexExtension.lua:17855). On a Nat. Dex build that screen shows vanilla pools.
- The `legacy113` branch assumes 4-byte learnsets (GbaTracker.kt:1046-1047). The 1.1.3 randomizer reads a v1.1 Nat.
  Dex ROM's learnsets in the vanilla packed format (jambo off; upstream Gen3RomHandler.java:419, 451-453), so that
  branch would likely misread them. Unconfirmed without a 1.1.x ROM, and it only matters if a 1.1.x build were ever
  pinned.

---

## 5. Open questions for Blake, and what to check on a device

**Questions:**

1. **Rules.** Trip deleted his rules page on 2026-07-03, after reducing it to "Same Rules as NatDex". Does MaxDex
   follow Nat. Dex's v1.2.0+ section or its v1.0.0 to v1.1.3 section? MaxDex is a 1.1.3 build. This sets the
   favourite and BST lines (FavoriteBall, BstRule) and the banlist. Only Trip can answer (Discord or GitHub; I did not
   contact anyone). **Answered by Blake, 2026-10-03: "Max dex is allowed a bst 600 pokemon", so the v1.0.0 to v1.1.3
   section (section 1.3 has what changed).**
2. **Bundle the 25.7 MB patch or import it once?** rc33 is 125.4 MB (`dist/KaizoCore-1.0.0-rc33.apk`), and 100 MB of
   assets are already patches. The patch carries 99.9% of the game as literal data, so it will not compress much.
   **Answered by Blake, 2026-10-03: asked "can MaxDex.bps be in the apk?", told it adds about 26 MB and that Trip's
   repository has no license file, he said "yes". Bundled in rc34 (fix/rc34-maxdex-bundled) as
   `app/src/main/assets/patches/maxdex-firered-u-v11.bps`, Trip's file byte for byte, so a FireRed 1.1 is all MaxDex
   needs. A copy a player added to the library before then is the same file and is used while it is there
   (`MaxDexInfo.patchFile`). The release APK is 155.2 MB with it (155,159,263 bytes, assembleRelease on the branch),
   19.0 MB more than rc34 without it (136.1 MB): the patch deflates from 25,761,384 bytes to 19,020,815 inside the APK.
   Making MaxDex unpacks it on the phone once, whole, unless the player's own copy is in the library.**
3. **Confirm KaizoCore does not copy the MaxDex extension's learnset-by-name Move History** (section 2.3). It shows an
   opponent's randomized moves that the base PC Tracker and Nat. Dex keep hidden.
4. **Kaizo only?** Trip ships one preset. Offer only Kaizo, or also Nat. Dex 1.1.3's other presets on MaxDex?
5. **Credits for champred?** Should champred (MAX) be credited even though Trip's page does not, until Trip says
   whether MAX code is in the build?
6. **Report the extension issues to Trip?** Egg and ghost ids 1236/1237, the exp-yield offset, the scripting battler,
   the update check pointing at Nat. Dex, and detection that also accepts Nat. Dex ROMs. That is Blake's call; I posted
   nothing.
7. **Pin policy.** When Trip pushes a new MaxDex.bps, does KaizoCore follow with a new pin, as with Nat. Dex releases?

**Check on a device once built** (with Blake's FireRed v1.1 dump):

- **Build and boot:**
  - Prepare gives CRC `28c12926`.
  - The engine's output for 3 seeds equals Trip's jar on PC (golden hashes).
  - Title screen, new game, the lab with three balls; starters read at +0/+503/+449 match the balls (FavoriteBall).
- **Party and enemy:** party at 0x020242D0 decodes (vanilla 100-byte struct) with MaxDex names and icons, including a
  Z-A mega if one appears; the enemy card; trainer screens (Brock is trainer 414).
- **Moves:** an id above 360 shows its name and ROM power, accuracy and PP. Category matches the game, for example a
  physical Fire move.
- **Abilities:** an ability id above 77 shows its name, and an ability pop-up procs.
- **Learnsets:** learn levels from the 3-byte table (Moves X/Y (Lv N)).
- **Progress:** EXP bar from 0x0826FCE8; badges after Brock (flags 0x1078 + 0x104); the Items pocket of 120 slots and
  the Balls and Berries offsets (heals in bag, catch rates); repel steps; game stats.
- **Battle detection and move tracking:** gBattleMainFunc 0x03004BB4; moves seen against a confused, frozen or asleep
  opponent.
- **Stand-ins:** the Pokemon Tower ghost battle. Which stand-in ids the ROM uses (1281-1283?) and that Raichu-X and
  Dragonite-M display as themselves.
- **Battle mon struct:** the status2 offset (0x5C struct; KaizoCore defaults 0x50) in Battle Details.
- **Exp yield:** at struct offset 0x1C, if KaizoCore shows it.
- **End of run:** game-over and win detection (Hall of Fame map 218); Safari flag.

---

## 6. Credits to show in the app

| Who | What | Link | Licence file |
|---|---|---|---|
| Trip (Tripc423) | MaxDex: ROM patch, randomizer changes, tracker extension, data and icons | https://github.com/Tripc423/Maxdex | none |
| CyanSixFour (CyanSMP64) | Nat. Dex, the base of MaxDex: species, data, sprites, extension (already credited, with permission on file in NOTICE) | https://github.com/CyanSMP64/NatDexExtension | none |
| CyanSixFour (CyanSMP64) | Nat. Dex randomizer, v1.1.3, which MaxDex's randomizer modifies | https://github.com/CyanSMP64/universal-pokemon-randomizer-zx | GPL-3.0 |
| rh-hideout and contributors | pokeemerald-expansion: MaxDex's new moves, abilities and animations, per Trip's credits; MoveEffectData.lua's text comes from its moves_info.h | https://github.com/rh-hideout/pokeemerald-expansion | none |
| champred | MAX (Move/Ability Expansion) and MAX.Dex, the "MAX Kaizo" MaxDex names. Code reuse unconfirmed | https://github.com/champred/pokeemerald | none |
| Dabomstew, Ajarmar and the UPR ZX team | Universal Pokemon Randomizer ZX (already credited) | https://github.com/Ajarmar/universal-pokemon-randomizer-zx | GPL-3.0 |
| besteon, UTDZac and contributors | Ironmon-Tracker, which the extension runs in (already credited) | https://github.com/besteon/Ironmon-Tracker | MIT |
| MaxDex testers (wiki Credits) | Annadrol, Ehmtae, Micro Mouse, Frankie, Philanthropist, ColeB, Johnnyonthemove, Himmy, ValyaLoona, Demon_Quingar, Pokemon Tabletop Game, xxYungSmurfxx, Yitious | https://github.com/Tripc423/Maxdex/wiki/Credits | |

Pinned files to name in NOTICE:

- `MaxDex.bps`: SHA-256 `65d70b582f14b493863d2c31d1f668301dc370bf46ece9f40ecaf28e94b15057`, 84ee4776 to 28c12926.
- `FRLG MaxDex Kaizo.rnqs`: SHA-256 `af51837c44942be36cb1b407be1fe4bebd6c5d08f0cc95f9325aa1fa7491ec9e`.
- The reference jar: SHA-256 `b39836b01f668d1b31502741e62fbb64448f2e38df1eeb970f6b3c9762d0357b`.
- Upstream source commit `d53e08242f8609062ffc17375806365a68f31e60`.

---

## Files

Everything is under `C:/Users/bepor/AppData/Local/Temp/claude/C--Users-bepor-willowcreek-v2-deploy-zip/c84daccc-781f-450a-becb-7ad5c6ff979d/scratchpad/research folder: `:

- **Clones:** `Maxdex/` (the repo, full history), `Maxdex.wiki/`, `champred.wiki/`, and `up113/` (upstream randomizer
  at d53e0824, shallow).
- **Comparison inputs:** `jar/` and `jar113/` (the two jars unzipped for javap); `natdex-randomizer-1.1.3.jar`;
  `natdexext/` (NatDexExtension.lua at v1.1.3, 1.2.0 dev and 1.2.1); `up_*.ini` (upstream ini at each commit);
  `hist_*.bps` (earlier MaxDex patch uploads from git history).
- **Scripts:** `bpsinfo.py`, `bpslit.py`, `bpsprobe*.py`, `cmpclasses.py`, `cmpmethods.py`, `constdiff.py`,
  `cmp_ext.py`, `ext_diff.py`, `ReadRnqs.java` and `GenR.java`.

No ROM or BIOS was downloaded. The only archives fetched are the two randomizer jars (Java); their entry lists were
checked first and hold no ROM-like file. Nothing in KaizoCore was changed.

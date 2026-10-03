# Paradox Kaizo (Lightorias) as a KaizoCore mode (research, 2026-10-02)

Blake: "its firered but every mon has a random dual type and a sprite recolour based on its type. also you get less
items although every item check lets you choose between one of 3 items or a random surprise item."

**Verdict.** The mode is real and well documented in words, but its files are not public. It is Lightorias's
FireRed challenge, built with Timur (Twitch `timur_bey`), revealed on stream on 2026-09-16, tested from 2026-09-22
and launched on his stream on 2026-09-28. It is a ROM patch (said on stream to sit on DrMaple's Faster FireRed) plus, by every sign, a
modified randomizer and settings. As of 2026-10-01 Lightorias says it is invite-only (SaltyDolphin and XWater have
it) and that he wants to control who gets it for now. KaizoCore cannot build the real mode until Blake gets the
patch, the randomizer (with source) and the settings from Lightorias. Several pieces can be planned and built now,
switched off: the mode key, the rules page, the rule marks, and the spoiler guards the mode's own rules ask for.

Everything below comes from the rules pastebin, the stream VODs' auto captions, the Twitch chat logs, public bot
commands, GitHub and the vendored source. Auto captions misspell names ("Timmer" is `timur_bey`, "Small" is
`smol_z`, "Arman versus" is IronMON VS). Downloaded evidence (text only, no ROMs) is in
the session's research folder beside this file.

## 1. What it is

### Name, people, links

| What | Fact | Source |
|---|---|---|
| Name | Paradox Kaizo (also "Paradox Kaizo IronMON", "Paradox ironMON") | pastebin title; VOD titles |
| Creator | Lightorias ("Light"), Twitch streamer; his panel lists 9 Kaizo IronMON wins, 7 of them on FireRed | https://www.twitch.tv/lightorias (panels), https://www.youtube.com/@Lightorias |
| Who built it | Lightorias and Timur (`timur_bey`); Lightorias says the two of them made it, and that he directs while Timur does the work | VOD IrPEQ5zmiZI 0:25:33-0:25:40; PgTvv49dq1g 5:55:10-5:55:24 |
| Helpers | `smol_z` ("Small", testing, 150 seeds by the first test stream), `swifferrrrr` ("Swiffer") | IrPEQ5zmiZI 0:00:52-0:01:27; chat 2026-09-23 02:07:54 UTC |
| Testers with the files | SaltyDolphin, XWater | PgTvv49dq1g 5:22:09-5:22:22; jlhxN2NcjvM 0:32:12-0:32:16 |
| Rules page | https://pastebin.com/raw/fVXc2MKr, user LightoriasPineapple, created 2026-09-22 20:52 CDT, last edited 2026-09-30 23:50 CDT | pastebin page metadata; local copy `research-paradox/pastebin-fVXc2MKr.txt` |
| Chat commands | `!paradox`, `!paradoxrules` (Lightorias), `!paradox` (SaltyDolphin) all point to the pastebin | Nightbot public API, read 2026-10-02 |
| Discord | https://discord.gg/7KuNCpxzwS (announcements; testers ask for the files there or by DM; not joined) | YouTube descriptions; 6CF_JmUPOjc 0:35:39-0:35:48; PgTvv49dq1g 5:18:21-5:18:29 |

### Base game and version

- **FireRed (USA).** Lightorias, 2026-09-24, explaining the Safari exhibits, says the build is on the Faster FireRed
  patch and names its maker as Maple (9t-76APuzJo 1:10:17-1:10:37). Faster FireRed applies only to **FireRed Rev 1
  (1.1)**, SHA-1
  `dd5945db9b930750cb39d00c84da8571feebf417` (https://github.com/DrMaple/Faster-FireRed README; latest 1.3.2,
  2024-03-19). KaizoCore pins clean 1.1 as CRC `84ee4776` and Faster FireRed 1.3.2 as `33A9FB54`.
  **Unconfirmed:** whether the Paradox patch is made against clean 1.1 or against a Faster FireRed 1.3.2 build, and
  its format (IPS, BPS, UPS or a pre-patched ROM).
- Regular trainer AI, not the Smart AI patch: SaltyDolphin, asked by chat, says it is the regular AI
  (jlhxN2NcjvM 0:27:01). Timur: gym leaders and Cool Trainers already get the smarter AI in the normal settings (chat
  2026-09-28 03:19:29 UTC).
- The patched ROM is its own build: SaltyDolphin's cosmetic changes are missing because the mode needs a specific ROM
  (jlhxN2NcjvM 0:09:01-0:09:11, 3:36:56-3:37:02).

### How it is distributed (status 2026-10-02: not public)

- **Invite only.** 2026-09-23: not public; some other players will get it; ask in Discord (6CF_JmUPOjc
  0:34:54-0:35:48). 2026-09-29: offers it to XWater and says it is not public (PgTvv49dq1g 3:03:46-3:04:14); he is
  rolling it out slowly, does not want it passed around or leaked, and has no timeline (5:17:33-5:22:31); easy setup
  is not his concern yet, after chat asked for a single download (5:24:16-5:24:38). 2026-10-01: bugs are the main
  reason it is not public, and nobody can download it at the moment (POtM7aqJtdI 0:32:59-0:33:12, 3:00:12-3:00:19).
  SaltyDolphin, 2026-10-01: no public release yet, one more patch is planned first (jlhxN2NcjvM 0:27:29-0:27:42,
  2:39:00-2:39:13).
- **What the files are (inferred, not seen):**
  - a **ROM patch**: Lightorias speaks of the patch, updating the patch and a new patch (IrPEQ5zmiZI 0:02:39;
    9t-76APuzJo 0:53:46-0:57:45; POtM7aqJtdI 4:47:12);
  - a **modified randomizer**: types, recolours and evolutions change on every reset (jlhxN2NcjvM 4:22:47-4:22:56,
    7:13:07-7:13:18), and Timur says the colours can be read in the randomizer's source code (chat 2026-09-29
    02:14:58 UTC);
  - **settings** (forced dual types, follow evolutions, random evolutions; see section 2);
  - played with the **standard Ironmon-Tracker** on BizHawk (SaltyDolphin names version 2.10, jlhxN2NcjvM 4:53:12;
    the tracker's log viewer reads the seed's log, 9t-76APuzJo 0:55:04-0:56:22; jlhxN2NcjvM 3:55:04).
  - No tracker extension was mentioned or found.

### Timeline and versions (no version numbers are published)

| Date (ET) | Event | Source |
|---|---|---|
| 2026-09-14 and 09-15 | Stream titles announce the reveal; smol_z posts the plan: beta 9/15 to 9/24, full release 9/25 | Twitch VOD titles; chat 2026-09-15 01:36:22 UTC |
| 2026-09-16 | Reveal: random dual type every attempt, reskinned by type, a whole-dex sheet for one seed, rotating wilds, 14 to 21 pivots | SVf3N1WCQdc 0:09:10-0:24:00, 1:10:56-1:11:48 |
| 2026-09-22 | First stream played (beta patch); pastebin posted | IrPEQ5zmiZI; chat 2026-09-23 02:05:09 UTC |
| 2026-09-23 to 09-27 | Testing streams and patch updates; launch moved to Monday 2026-09-28, 8pm ET | chat 2026-09-23 02:09:45 UTC; 9t-76APuzJo 0:54:43; Twitch VOD titles |
| 2026-09-28 | Launch stream; a YouTube short announces it; the launch build replaces every item with item NPCs | https://www.youtube.com/shorts/4O6K3qgFX4Y; chat 2026-09-29 00:01:37 and 2026-09-30 03:23:01 UTC; POtM7aqJtdI 0:32:35-0:32:46 |
| 2026-09-30 | Pastebin last edited | pastebin |
| 2026-10-01 | A crash going from Route 9 to Route 10 (an item NPC bug) found on stream; Timur fixes it within hours; a new patch due the next day | POtM7aqJtdI 0:41:26-0:42:16, 4:03:52-4:04:49, 4:47:12; chat 2026-10-02 00:11:16-00:39:35 UTC |

Expect a new patch every few days while bugs are fixed.

### The rules in full (paraphrased; exact text at the pastebin and in `research-paradox/pastebin-fVXc2MKr.txt`)

Paradox Kaizo is Lightorias's challenge after Scarlet and Violet's Paradox Pokemon: old Pokemon re-imagined with new
types. Runs should feel different from each other and reward adapting. It is closest to Kaizo IronMON, with a
difficulty between Kaizo and Super Kaizo (both by iateyourpie). The rules are written as changes from Kaizo IronMON.

**Settings**
1. Every Pokemon has random dual types.
2. A Pokemon always evolves into something that shares a type with it; the options change every attempt.
3. Vanilla evolution lines share exact types, except some split evolutions.
4. All Eeveelutions share one type, and each one's other type is unique.
5. Nincada, Ninjask and Shedinja always share the same types (so what hits Shedinja changes every attempt).
6. Shedinja's type is also shown on an exhibit sign in front of the Safari Zone.
7. Every gym leader has six Pokemon.

**Rules**
1. One setup move or one X item per fight; no setup move if you know the opponent has Encore.
2. Swords Dance, Tail Glow and Belly Drum are banned.
3. Draining attacks are allowed.
4. Max the PP of one move only.
5. Pick your ball before looking. Legendaries are allowed; the four pseudo-legendaries are still not.
6. Huge Power and Pure Power are the only banned abilities; special attacks may still be used.
7. Items from Pickup are banned.
8. Do not use the tracker to spoil the types of Pokemon you have not seen: the Coverage Calculator and the
   Notebook's "all" checkbox are banned.
9. Fight Misty right after Nugget Bridge.
10. Fight Erika before the Rocket Hideout.
11. Clear the Fighting Dojo before Koga.

**Late-game pivoting**
1. No forced Safari pivot, unless you run a legendary from the lab.
2. A legendary from the lab is allowed, but you must pivot before Koga.
3. A pivot may re-enter a dungeon its predecessor entered once.
4. One standard pivot per route in the Safari Zone.
5. If you pivot at any point after Brock, you may use one of the three Safari Zone TMs.

**Mods (ROM changes)**
1. Start with 10 Great Balls instead of 5 Poke Balls.
2. All gym leaders have six Pokemon.
3. Two more wild options on Routes 1, 2 and 22 and one more in Viridian Forest (7 more in all).
4. Wild Pokemon appear in a fixed rotation on each route, and all can be level 8.
5. Safari Zone pivots are all at their maximum level, never below 38.
6. Every item is removed and replaced by 61 item NPCs; each offers a choice of three random items or one mystery
   item.
7. One NPC, at the forest exit before Brock, offers a fixed choice: Burn Heal, Antidote, Paralyze Heal or Potion.
8. Cycling Road is banned completely (10 trainers, 5 items).
9. The Seafoam Islands and Power Plant items are banned (12).
10. Items out (all of them), item choices in (61).
11. The Move Tutor (the Move Relearner) is inside the Pokemon Mansion: free to use, but the dungeon is still entered
    once.

**Quality of life**
1. Happiness is maxed automatically in the lab.
2. Some Cut trees removed.
3. Fewer items to pick up.

Kaizo IronMON's own rules (valiant-code's gist, already in KaizoCore's rules pages) apply underneath.

### Where I looked

Found something:
- YouTube search "paradox kaizo ironmon", "lightorias paradox"; Lightorias's channel and streams tab; the VODs
  (auto captions fetched read-only): m2tS-6FWtxs (09-15, only says the announcement is coming), SVf3N1WCQdc
  (09-16), U7jlkveIP2I (09-17), IrPEQ5zmiZI (09-22), 6CF_JmUPOjc
  (09-23), 9t-76APuzJo (09-24), l2aStIA_HzI (09-25), PgTvv49dq1g (09-29), POtM7aqJtdI (10-01), the short
  4O6K3qgFX4Y (09-28, music only); SaltyDolphin jlhxN2NcjvM (10-01). 0YF6-C-jLBQ (SaltyDolphin 10-02): description
  only, no captions yet.
- Pastebin fVXc2MKr and the user page (two pastes; the other is a sub goals list).
- Twitch GQL: channel panels and VOD titles (read-only public client id). Panels hold nothing on Paradox.
- Nightbot and StreamElements public command lists for both channels.
- Twitch chat logs for #lightorias 2026-09-15 to 2026-10-02, https://logs.zonian.dev/channel/lightorias/2026/9/29
  (and each day), saved in `research-paradox/chatlogs/`.
- GitHub: DrMaple/Faster-FireRed; upr-fvx/universal-pokemon-randomizer-fvx (palette code); WaffleSmacker's IronMON VS
  extension (rotating wilds reference); besteon/Ironmon-Tracker issue 553 and PR 552 (hidden-types leak, by
  SJTButler, not about Paradox).

Found nothing:
- Web search: "Paradox Kaizo", "paradox kaizo pokemon", Reddit queries, "ironmon.gg modes Paradox",
  "timur_bey github", item-choice NPC hacks.
- GitHub user, repo, code and issue search: "Paradox Kaizo", "paradox ironmon", "paradoxkaizo", "paradox firered",
  "lightorias", "timur_bey", "timurbey": no repository or code for the mode.
- ironmon.gg (redirects to the IronMON rules gist), the rules gist, UTDZac's settings gist and gist list: no Paradox.
- Ironmon-Tracker wiki (local clone, 2026-09-26) and Nat. Dex Extension wiki (local clone, 2026-09-29): only the Nat.
  Dex "Paradox" Pokemon category, which is unrelated.
- Lightorias's Bluesky (last post 2025-04-09). KaizoCore's repo (no mention).

Could not read:
- Reddit (block page from reddit.com, nothing from old.reddit.com). X/Twitter (embed endpoint rate-limited).
  Instagram and TikTok (login walls). The Discord (rules forbid joining): the patch, any changelog and any setup
  guide live there or in DMs.

## 2. How it works

What the patch, the randomizer and the rules each do. "Stock ZX" means Universal Pokemon Randomizer ZX 4.6.1 as
vendored in `C:/Users/bepor/IronMonOne-wt-rc33/engine-zx` (package `com.dabomstew.pkrandomzx`); paths below are in that
module unless named.

### 2.1 Random dual types (randomizer)

What the mode does:
- Every species is dual-typed, every attempt (pastebin; SVf3N1WCQdc 0:09:19-0:09:26). Timur refers to forced dual
  types and says the type picker is the randomizer's built-in, purely random one (chat 2026-09-17 03:58:38 and
  03:23:33 UTC).
- Linear evolution lines copy exact types (IrPEQ5zmiZI 0:33:49-0:33:54). Scyther and Scizor share types
  (SVf3N1WCQdc 0:19:49-0:20:41).
- Two-way splits copy the pre-evolution exactly: seeing a Poliwhirl tells you the types of both Poliwrath and
  Politoed (SVf3N1WCQdc 0:18:55-0:19:03, as of 2026-09-16).
- Eevee: its primary type becomes every Eeveelution's secondary type; each Eeveelution's primary type is random and
  different from the others (SVf3N1WCQdc 0:20:56-0:21:41: an Electric/Fire Eevee gave Water/Electric,
  Poison/Electric, Psychic/Electric, Steel/Electric, Bug/Electric). Timur: every Pokemon with more than two split
  evolutions does this, in Gen 3 that is Eevee and Tyrogue (chat 2026-09-29 02:28:48-02:29:49 and 02:35:40 UTC).
- Nincada, Ninjask and Shedinja share exact types (SVf3N1WCQdc 0:21:48-0:22:17; IrPEQ5zmiZI 0:21:11-0:21:21).
- Type order matters for looks: Flying/Poison looks different from Poison/Flying (SVf3N1WCQdc 0:19:37-0:19:45).
- Move types are not randomized (SVf3N1WCQdc 0:23:57-0:24:01).

What stock ZX does with "Random (follow evolutions)" plus "Force Dual Types":
- `dualTypeOnly` exists: `Settings.java:68`, read from byte 2 bit 7 at `Settings.java:640`, GUI label "Force Dual
  Types" (`newgui/Bundle.properties:552`). The Nat. Dex engine has the same (`engine-natdex/.../Settings.java:68`,
  `AbstractRomHandler.java:891`).
- `AbstractRomHandler.randomizePokemonTypes` (`romhandlers/AbstractRomHandler.java:418-483`): base species get a
  random primary and, with dual types forced, a random different secondary (431-447); each linear evolution copies
  its pre-evolution's types (448-451).
- **Split evolutions do not copy.** The two-argument `copyUpEvolutionsHelper` (6458-6460) passes
  `copySplitEvos = false`, so every split evolution gets the base action, fresh random types (6413-6418). In Gen 3 a
  split is any evolution from a species with more than one, except Nincada to Ninjask (method 13,
  `LEVEL_CREATE_EXTRA`): `Gen3RomHandler.java:3121-3129`, `RomFunctions.java:54-68`. So stock ZX gives each
  Eeveelution, Shedinja, Poliwrath, Politoed, Vileplume, Bellossom, Slowbro, Slowking, the three Hitmons, Silcoon,
  Cascoon, Huntail and Gorebyss its own random types.
- **So Paradox's type rules are custom code.** They have to run inside type randomization, before random evolutions,
  because ZX picks evolutions from the new types (`Randomizer.java:163-182`; its comment at 177-178 says so).

### 2.2 Random evolutions that share a type (randomizer, stock)

- Lightorias: the evolution looks for something with one shared type and similar BST, within 10% first, widening if
  nothing fits (IrPEQ5zmiZI 0:40:43-0:41:03). Timur: Growlithe can roll a 600+ BST because of how evolutions are
  randomized with dual types (chat 2026-09-23 02:17:44, 2026-09-27 04:34:00 UTC).
- That is stock ZX "Random evolutions" with "Same typing" and "Similar strength": the type filter at
  `AbstractRomHandler.java:5621-5641` (Eevee special case 5624-5629) and the strength pick at 6206-6233 (target BST is
  the vanilla evolution's, plus or minus 10%, widening 5% a round). Growlithe's vanilla target is Arcanine (555), so
  the 500-610 window reaches the 600 BST pseudo-legendaries.
- Unconfirmed: "Limit to three stages", "Force change" and the other evolution switches.

### 2.3 The recolour (randomizer)

What the mode does:
- Every species' sprite is recoloured from its new types, per seed (SVf3N1WCQdc 0:15:31-0:16:07; 6CF_JmUPOjc
  1:52:13-1:52:17; PgTvv49dq1g 5:12:29-5:12:32). A whole-dex sheet per seed exists (the "Paradex"), viewed after a
  death (SVf3N1WCQdc 0:16:01-0:18:18; 9t-76APuzJo 1:57:42, 2:19:16-2:19:24).
- Timur designed it, says it is purely algorithmic, and treats it as work that keeps changing; species get tuned one
  by one (Venusaur's flower, Furret, Stantler, Gengar), within the 16-colour limit, and the colours live in the
  randomizer source (chat 2026-09-17 03:56:21, 04:01:28, 04:05:59; 2026-09-23 02:26:46; 2026-09-24 00:09:26;
  2026-09-29 00:17:22, 02:09:28, 02:14:58 UTC; 6CF_JmUPOjc 1:48:17-1:48:35). Shinies are recoloured too (chat
  2026-09-29 01:33:15 UTC).
- Colours: Dragon is a green-blue, Ice a light blue (IrPEQ5zmiZI 1:09:02-1:09:17; jlhxN2NcjvM 1:15:26-1:15:36). A
  vanilla-typed species can come out near its vanilla colours (9t-76APuzJo 2:20:23-2:20:30).
- Bill's PC (the Eeveelution pictures) and the Safari exhibits let a player read types from colours ahead of time
  (9t-76APuzJo 1:10:02-1:10:19).

What exists to build it from:
- **Stock ZX has no Pokemon palette randomizing.** Its Gen 3 handler reads palettes only to draw the desktop mascot
  and to swap the intro Pokemon (`Gen3RomHandler.java:4404-4420`, 3652-3696); `Settings.java` has no palette option.
  The FRLG palette table pointer is at ROM `0x130` (`constants/Gen3Constants.java:77`, used at
  `Gen3RomHandler.java:445`).
- **UPR FVX does it, GPL-3.0** (https://github.com/upr-fvx/universal-pokemon-randomizer-fvx, a fork of ZX; release
  vFVX1.6.1 2026-08-05; read at commit `9bd19875`, 2026-09-30):
  - settings `PokemonPalettesMod`, follow types, follow evolutions, shiny from normal
    (`random/src/main/java/com/uprfvx/random/Settings.java#L381-L388`, byte 55 at L697-L702);
  - `Gen3to5PaletteRandomizer.java#L68-L101`: per evolution line a `TypeBaseColorList`, then each species' palette
    is refilled part by part from a per-species description (`pokePalettesFRLG.txt`, e.g. Bulbasaur
    `[3,4,5,6/12,13,14,15]`);
  - `TypeBaseColorList.java#L86-L136`: slot 0 takes the primary type's colour, slot 1 the secondary's, so type order
    changes the look, as in Paradox;
  - type colours `random/src/main/resources/data/type_colors/Gen3To5TypeColors.txt` (three per type; the vanilla
    types' values credited to Artemis251's Emerald Randomizer). FVX's Dragon is red, dark red and blue, so
    Paradox's colours are its own;
  - ROM I/O: `romio/.../romhandlers/Gen3RomHandler.java#L4162-L4210` (LZ77 palettes read from the tables at `0x130`
    normal and `0x134` shiny, rewritten compressed at L4450).
- FVX writes a different log layout ("( Pokemon Base Statistics / Types / Abilities {PKST} )", `Bundle.properties`
  line 898) from ZX's "--Pokemon Base Stats & Types--", and the PC tracker's log parser expects ZX's
  (`Ironmon-Tracker/ironmon_tracker/data/RandomizerLog.lua:17-32`). Lightorias reads seed logs in the tracker, so
  **their randomizer is most likely a ZX fork with recolour code added** (inference; ask).

### 2.4 Fewer items (patch)

- All overworld item balls are removed (Timur, chat 2026-09-25 03:47:19 UTC), and the pastebin removes every item,
  hidden ones included. About 70% fewer items than Kaizo (POtM7aqJtdI 1:20:27-1:21:00).
- Some items moved before Brock in early designs; the lab gave several items at once in the beta (IrPEQ5zmiZI
  0:02:19-0:02:46, 0:07:14-0:07:36). Cycling Road, Seafoam and Power Plant are out.

### 2.5 The item choice (patch, plus unknown randomizing)

- 61 NPCs drawn as small pineapples (Lightorias's mascot) stand where items were. Talk to one: three items shown, or
  "get lucky" for a hidden random item (example: Hyper Potion, Nest Ball, Charcoal or get lucky; jlhxN2NcjvM
  0:07:28-0:09:22, 1:07:37-1:07:49; POtM7aqJtdI 4:41:49-4:42:01). One NPC is fixed (a Potion or one of three status
  heals; POtM7aqJtdI 1:18:14-1:18:47). A player can leave an NPC and come back later (1:24:47-1:25:01).
- It is new map events, new scripts and a new overworld sprite: Timur could not add a custom overworld sprite yet on
  2026-09-23 (chat 02:43:40 UTC); the pineapples move (jlhxN2NcjvM 7:14:26). Its bugs crash the game on map changes
  (POtM7aqJtdI 0:41:26-0:42:16; chat 2026-10-02 00:12:22 UTC). Timur says he hardly understands how the NPCs work,
  unlike the recolour he designed (chat 2026-09-29 02:09:28 UTC), so someone else may have written them.
- **Where the randomness comes from is unconfirmed.** Either their randomizer writes each NPC's three items and mystery
  item per seed, or the game picks them when you talk (or once, kept in save data). Stock ZX cannot see these NPCs:
  its field-item finder only takes object events drawn as the item ball whose script starts with the exact item-ball
  pattern (`Gen3RomHandler.java:3816-3831`), plus hidden-item signposts (3860-3872). Either way it is custom code.

### 2.6 Rotating wilds and the other ROM changes (patch)

- Routes 1, 2 and 22 and Viridian Forest hold 4, 6, 5 and 6 species (vanilla 2, 4, 3, 5): 21 early pivots instead of
  14 (SVf3N1WCQdc 1:11:05-1:11:48). No encounter rates: the grass cycles species and level, for example level 6
  Rattata, level 6 Pidgey, level 8 Rattata, level 8 Pidgey, always in that order; each route differs a little
  (SVf3N1WCQdc 0:11:42-0:12:13; 9t-76APuzJo 1:14:09-1:15:30; chat 2026-09-29 00:21:29 UTC). Inspired by IronMON VS
  but not the same code (SVf3N1WCQdc 0:10:53-0:11:22; jlhxN2NcjvM 1:20:48-1:20:52). Fishing and surfing were
  probably not changed (Timur, chat 2026-09-27 04:33:45 UTC). That needs ASM in the wild encounter code plus edited
  tables. A public reference for the idea: IronMON VS ships `IronmonVS/RomPatch/IronMonVS.ips` with a ZX jar in
  https://github.com/WaffleSmacker/IronmonVS-IronmonExtension (MIT).
- 10 Great Balls at the start (9t-76APuzJo 1:13:03-1:13:11), happiness maxed in the lab, the Move Relearner in the
  Pokemon Mansion (IrPEQ5zmiZI 1:27:15-1:27:30), some Cut trees removed, Safari levels raised, the Shedinja exhibit
  sign (its text must come from the randomizer or a script that reads the types; unconfirmed).
- Gym leaders with six Pokemon is listed under settings. ZX's "additional Pokemon for boss trainers"
  (`Settings.java:181`, written at 532) also counts the Elite Four and the champion as bosses
  (`pokemon/Trainer.java:100-103`), so six for gym leaders alone points to a patch edit or custom code. Unconfirmed.

### 2.7 What the PC tracker does with it

- Types: the Ironmon-Tracker shows the randomized types from the ROM (jlhxN2NcjvM 0:26:03-0:26:09). Its "Reveal info
  if randomized" option (default on, `Ironmon-Tracker/ironmon_tracker/Options.lua:29`) hides an opponent's types as
  "?" (`data/DataHelper.lua:432`); Lightorias plays with it on (IrPEQ5zmiZI 0:29:32-0:31:33).
- Banned by the rules: the Coverage Calculator and the Notebook's "All" checkbox (`NotebookPokemonSeen.lua:62-64`,
  which lists unseen species with their ROM types). The coverage ban: 9t-76APuzJo 1:08:23-1:09:07.
- Icons: the PC tracker draws its own PNG icons, so it never shows the recolours.
- Route counts: its route data is static, and a route with more species than vanilla shows a bare count
  (`screens/TrackerScreen.lua:806-814`).
- Random evolutions: its odds table (`data/PokemonRevoData.lua`) is precomputed and knows nothing of a seed's types.

### 2.8 Summary: who does what

| Feature | Patch (ROM) | Randomizer | Rules only |
|---|---|---|---|
| Random dual types, evolution type rules | | yes (custom split rules) | |
| Random evolutions sharing a type | | yes (stock ZX switches) | |
| Recolour by type, shinies | | yes (custom; FVX is the public model) | |
| Item NPCs, fewer items | yes | maybe (per-seed items, unconfirmed) | |
| Rotating wilds, extra species | yes (ASM + tables) | stock ZX randomizes the species | |
| 10 Great Balls, lab happiness, Relearner in Mansion, Cut trees | yes | | |
| Shedinja sign | yes (sign) | probably (text) | |
| Six-Pokemon gym leaders, Safari levels | unconfirmed | unconfirmed | |
| Setup limit, bans, PP Max, pickup, gym order, pivots | | | yes |

## 3. What KaizoCore already has

Paths are in `C:/Users/bepor/IronMonOne-wt-rc33`.

| Area | Code | Reuse for Paradox |
|---|---|---|
| FireRed 1.1 and Faster FireRed 1.3.2 pinned by CRC | `core-api/.../core/Generation.kt:69-78`, 340-350 (`FIRERED_V11_FASTER`), `patched()` 319-320, `allPatched` 371-372 | Add a Paradox kind the same way |
| Identify a build by CRC; only an exact match is tracked or run | `core-patch/.../patch/RomIdentity.kt:189`, 213, 260; `app/.../GameSession.kt:46-58` | A pinned Paradox CRC makes the build known |
| Patch formats | `core-patch/.../patch/Patcher.kt:46-63` (IPS, BPS, UPS; xdelta file to file); BPS source and target CRCs `Bps.kt:15-33` | Apply whatever format Paradox ships |
| Player's own patch flow | Home, ROM Hacks (`docs/wiki/ROM-hacks-and-patches.md`) | Works if the patch stays the player's own file |
| Prepare rows for bundled patches | `app/.../PrepOptions.kt:39-59` (descriptions), 68-98 (`forKind`, asset name at 71) | A Paradox row if it is bundled |
| Modes come from preset files | `app/.../RnqsInfo.kt:41-44` (ruleset keys), 55-60 (labels); `RulesetCatalog.kt:45-48` (order), 59-63 (pairing), 72-77 (games a mode leaves out), 103-116 (`forRom`), 179-199 (mode lines) | New key `paradoxkaizo` |
| Rules pages | `app/src/main/assets/rulesets/FRLG/*.md`, built by `tools/rules/build_rules.py` (SOURCES 40-49, MODE_LABEL 65-67, CHAIN 70-81, MODE_KEYS 84-85); Evo Kaizo is already sourced from a pastebin | Generate `FRLG/paradoxkaizo.md` from fVXc2MKr |
| Starting a run | `app/.../RunStart.kt:26-60` -> `engine/Randomizers.kt:50-75` -> `engine/ZxEngine.kt:26-107`; log kept beside the ROM (`Randomizers.kt:73`, 78) | Paradox engine slots in at `Randomizers.kt:61-64` and `engineId` 88-91 |
| Post-randomize ROM edits | `engine/ViridianRepel.kt` (called at `Randomizers.kt:104`, 121) | Model for a palette or sign pass on the output file |
| Settings strings | `ZxEngine.kt:122-147` (parse, validate, write `.rnqs`) | If Lightorias gives a string, not a file |
| Dual types and random evolutions | stock ZX, section 2.1 and 2.2 | Settings only |
| Types on the tracker | `tracker-gba/.../GbaTracker.kt:1828-1843` (type1, type2 from the ROM's base stats) | Paradox types show with no change |
| Hidden randomized types | `app/.../InfoRules.kt:51-60`; option `TrackerOptions.kt:216` (default on, as the PC) | As is |
| Randomized-data detection | `tracker-gba/.../RandomizedFlags.kt:16-47` | As is |
| Banned-move X | `app/.../MoveRule.kt:189-252` (per mode; SETUP list at 104) | Add `paradoxkaizo` |
| BST X | `app/.../BstRule.kt:28-41` | Paradox: pseudo-legendaries only |
| Favorite in a ball | `app/.../FavoriteBall.kt:74-91`, `FavoriteRules.kt:30` | Needs a ruling (section 5) |
| Coverage Calculator | `app/.../CoverageCalc.kt:64`; opened at `PlayScreen.kt:2569-2593` | Hide in Paradox (banned) |
| Notebook "Include unseen" | `app/.../Notebook.kt:78`, 113, 127; opened at `SideScreens.kt:164-172` | Hide in Paradox (banned) |
| Random evolution odds | `GbaTracker.kt:2529-2556` (bundled `revos.tsv`); `SideScreens.kt:155-158` | Wrong for random types: hide in Paradox |
| Route species and seen count | static table `GbaTracker.kt:2202-2223`, 2254; `PlayScreen.kt:510-519`; bare count when seen exceeds the table `PcTracker.kt:1414` | Add Paradox counts for 4 areas |
| Dungeon maps with item spots | `app/.../FrlgPictures.kt:8-14`, `placeFor` 53 | Item spots are wrong in Paradox: hide them |
| Run codes | `app/.../RunCode.kt` (game id, settings hash, seed, ROM CRC) | A per-release Paradox kind id keeps codes exact |
| Stream | `/dex.json` refused mid-run (`docs/IRONMON-RULES-CHECK.md`); frames only | As is |

Two facts that matter for spoilers:
- **KaizoCore draws tracker art from the ROM on vanilla-numbered games.** `PlayScreen.kt:794-806` uses the bundled
  pack only for Game Boy and Nat. Dex, otherwise `GbaTracker.sprite` (`GbaTracker.kt:1848`), which decodes the ROM's
  front sprite with the ROM's palette (`SpriteDecoder.kt:52`). On a Paradox ROM every card, the notebook, the coverage
  calculator, the random evolution table and the lookups would show the recoloured art, and colour gives away the
  types of species the player has not seen. The PC tracker shows its own icons (`PcTracker.kt:362` is KaizoCore's
  pack loader).
- **The weakness screen ignores hidden types**, as in the PC tracker (open issue
  https://github.com/besteon/Ironmon-Tracker/issues/553, PR 552): `TrackerPanel.kt:225` and 563 pass the real types
  while 227 and 545 hide the chips.

## 4. How to add it

### 4.0 What is blocked

The real mode needs the Paradox patch, their randomizer (with source, since it is a GPL-3.0 ZX or FVX fork) and the
settings. None is public (section 1). Rebuilding the patch from the rules is not sensible: the item NPC scripts, the
rotation ASM and the map edits are weeks of FireRed hacking and would drift from a mode that changes every few days.
Do not ship a homebrew under the name "Paradox Kaizo": it would not be the mode its creator runs.

### 4.1 Version 1, once Lightorias sends the files

1. **Pin the build.** In `core-api/.../Generation.kt`, add `FIRERED_V11_PARADOX` (or one per release, as RogueMon's
   plan does) with `patched(base, id, name, crc, "paradox")` (319-320) and add it to `allPatched` (371-372). Base is
   `FIRERED_U_V11` or `FIRERED_V11_FASTER`, whichever the patch is made against; CRC measured by applying it to the
   pinned dump (or read from a BPS or UPS footer, `Bps.kt:32-33`). Size S.
2. **How the player gets it.** Either a bundled patch with a Prepare row in `PrepOptions.forKind` (68-98) and a line
   in `describe` (39-59), asset `paradox-firered-u-v11.<ext>`, or the player's own copy through Home, ROM Hacks, which
   lands as a known build once the CRC is pinned. Lightorias decides which (section 5). Size S.
3. **Admission test.** A test like `app/src/test/.../FasterFireRedTest.kt` (env `IRONMON_ROMS`, `IRONMON_PATCHES`):
   identified as the pinned kind; every table the tracker reads still where FireRed 1.1 keeps it; ZX loads it and
   round-trips; the 14 table pointers UPR reads untouched (`docs/research/kaizo-on-rom-hacks.md`, shortlist item 0).
   If tables moved, the tracker needs a Paradox map and this grows to L. Size S.
4. **The engine.** Add `PARADOX` to `enum class Engine` (`core-api/.../core/Platform.kt:59`), map the Paradox kind to
   it in `RomKind.engine` (`Generation.kt:61`), add a branch in `Randomizers.randomize` (61-64) and an id in
   `engineId` (88-91), which also keys runs made ahead:
   - With their source (a ZX 4.6.1 fork): diff it against `engine-zx` (`ZxEngine.ID` is `zx-4.6.1`,
     `ZxEngine.kt:23`), apply the Paradox changes to the vendored source behind a flag the run sets, and keep the log
     in ZX's format so KaizoCore's log viewer reads it. The type rules belong in `randomizePokemonTypes`
     (`AbstractRomHandler.java:418-483`) using the four-argument `copyUpEvolutionsHelper` (6394-6456) with a split
     action, never after the ROM is written (evolutions and the log depend on them). Size M.
   - With an FVX fork: port its palette package (`romio/.../graphics/palettes/*`, `Gen3to5PaletteRandomizer`,
     `pokePalettesFRLG.txt`) onto ZX's `Pokemon`, plus their type rules. Size L.
   - With a jar only: dex and relocate it (it shares `com.dabomstew.pkrandom` with `engine-natdex`) and check that its
     `java.awt` code never runs on Android, as `docs/research/roguemon.md` sizes it. Size M to L, and ask for the
     source.
   - The recolour can run as a pass on the output file, like `ViridianRepel`, because it changes nothing else: read
     the final types, rebuild each palette, LZ77-compress, write to free space, repoint the tables at `0x130` and
     `0x134`. Seed it from the run's seed (as `Randomizers.preSeed` does, 132), so a seed and a run code give the same
     colours. Free space must be measured on the Paradox build.
   - If the item NPC choices or the Shedinja sign text are written per seed, they go in the same pass, at script
     offsets Lightorias or Timur give. If the game picks them at runtime, nothing is needed.
5. **Settings and mode.** Bundle their file as `app/src/main/assets/presets/FRLG Paradox Kaizo.rnqs`. Add
   `"paradoxkaizo"` to `RnqsInfo.RULESETS` before `"kaizo"` (41-44) and to `RULESET_LABELS` (55-60); to
   `RulesetCatalog.ORDER` (45-48) after `evokaizo`; a mode line in `LINES` (179-199) written from the rules page.
   Pairing: Paradox only on a `patchTag == "paradox"` build, and only Paradox on that build. `isCompatible` (59-63)
   checks family and Nat. Dex only today, so extend it or `defined` (72-77). Size S.
6. **Rules page.** Save the pastebin as `tools/upr-settings/community/paradox-kaizo-fVXc2MKr.txt`; in
   `tools/rules/build_rules.py` add a SOURCES entry ("Paradox Kaizo rules by Lightorias",
   https://pastebin.com/fVXc2MKr, "last edited 2026-09-30"), MODE_LABEL, CHAIN `KAIZO + ["paradoxkaizo"]`, MODE_KEYS;
   generate `app/src/main/assets/rulesets/FRLG/paradoxkaizo.md`. Size S.
7. **Rule marks** (they state the rule, nothing more):
   - `MoveRule.rules` (189-252): `"paradoxkaizo"` -> `hm(); kaizo(drainLegal = true)`, plus Swords Dance, Tail Glow
     and Belly Drum always banned; the card says one setup move or one X item per fight (a limit, not an X). Leech
     Seed: confirm (Super Kaizo's reading keeps non-attacking drains banned).
   - `BstRule.lines` (34-41): in Paradox the X goes on Dragonite, Tyranitar, Salamence and Metagross (lab and wild),
     not on legendaries; a legendary lab pick gets one line stating the rule (pivot before Koga). Slaking: confirm.
   - `FavoriteBall.takeable` (74-91): off in Paradox until the Favorites Clause is confirmed, because rule 5 says to
     pick the ball before looking.
   Size S.
8. **Spoiler guards for a Paradox run** (the mode's rule 8, and KaizoCore's rule not to show more than the PC):
   - Art: in `PlayScreen.kt:794-806`, a Paradox run uses the bundled pack (`PcTracker.kt:362`) for every species, as
     the PC tracker does. Later, the ROM's recolour may be shown for species seen this run only.
   - Hide the Coverage Calculator (button and `PlayScreen.kt:2569-2593`) and the Notebook's "Include unseen"
     (`Notebook.kt:113`, 127), each with one line: banned by Paradox Kaizo's rules.
   - Hide the random evolution table (`SideScreens.kt:155-158`): its odds assume vanilla types.
   - Species lookups (`TrackerPanel.kt:536-566`): show "?" types for a species not seen this run, since the rules ban
     using the tracker for that (Blake's call, section 5).
   - Weaknesses: make `TrackerPanel.kt:225` and 563 respect hidden types (fixes the issue 553 leak for every mode).
   - Dungeon maps: no item spots on a Paradox build (`FrlgPictures.kt`).
   Size S to M.
9. **Route counts.** A Paradox override for Route 1, Route 2, Route 22 and Viridian Forest (4, 6, 5, 6 species) and
   no rates or level ranges there, on top of `GbaTracker.kt:2202-2223`. Size S. Never show which species comes next in
   the rotation: that would tell the player how to play.
10. **Credits.** NOTICE, the About screen credits (`AboutScreen.kt:462-490`) and `ModeCreditsTest.kt:19-26`
    (`pastebin.com/fVXc2MKr`, and the randomizer's repo once known). Size S.

### 4.2 Tests

- `RnqsLabelTest` and `RulesetCatalogTest`: "FRLG Paradox Kaizo.rnqs" reads as `paradoxkaizo`; offered only on the
  Paradox build; that build offers only Paradox.
- `RunScreenKaizoTest`: the mode line is read back from `FRLG/paradoxkaizo.md`.
- `MoveRuleTest`, `BstRuleTest`, `FavoriteBallTest`: the Paradox cases above.
- A Paradox types test on a synthetic species graph (no ROM): every species dual-typed; linear lines and two-way splits
  copy exactly; Eeveelutions and Hitmons share the pre-evolution's primary as secondary with unique primaries; Nincada,
  Ninjask and Shedinja identical; evolutions picked after types share a type. Must fail with the split rule removed.
- A palette test: same seed, same palettes; colour 0 stays transparent; LZ77 round-trips; only the two palette tables
  and free space change.
- `ParadoxBuildTest` modelled on `FasterFireRedTest`: CRC pin, tables unmoved, ZX round trip.
- Spoiler guard tests: a Paradox run never decodes art from the ROM; coverage, "Include unseen" and random evolutions
  are absent; weaknesses honour hidden types (`InfoRulesTest`, `CoverageCalcTest`, `SpriteArtTest`).
- `ModeCreditsTest`: the new source credited twice.

### 4.3 Later steps

- The Paradex after a loss: the seed's whole dex in its recolours, from the ROM's palettes, opened from the game-over
  screen only (like the log). M.
- Recoloured art for species seen this run. S.
- An item NPC count (found of 61), if the NPCs have event flags. S once the flags are known.
- Shiny recolours on the card once Timur's shiny rule is known. S.
- Emerald and Black/White versions, which Lightorias and Timur mention as later ideas (6CF_JmUPOjc 0:32:56-0:33:05;
  chat 2026-09-21 15:58:34 and 15:59:25 UTC).

### 4.4 Can be built now, switched off

Steps 5 (without the preset), 6, 7, 8 and 9 need no files and change nothing until a Paradox build exists, provided
each is keyed on the Paradox kind or mode and has a test that the off state changes nothing.

### 4.5 Size

| Part | Size |
|---|---|
| Pin, Prepare row or ROM Hacks path, admission test | S |
| Mode key, rules page, rule marks, credits | S |
| Spoiler guards and route counts | S to M |
| Engine with their ZX-fork source | M |
| Engine from an FVX fork or a jar | L |
| Engine rebuilt from the description, without their files | XL, and not the real mode |
| Tests and device QA | M |
| **Version 1 with their source** | **M to L** |

Churn: the patch changed at least five times between 2026-09-23 and 2026-10-02. Each release is a new CRC and, if
bundled, a new app release; a player-supplied patch with a short list of pinned CRCs ages better.

## 5. Open questions

### For Blake

1. Wait for Lightorias's files (recommended), or build only the switched-off pieces now (section 4.4)?
2. Bundle the patch in the APK, or have players bring their own copy from Lightorias? (Section 4.1 step 2.)
3. In a Paradox run, enforce rule 8 in the tracker (hide coverage, "Include unseen", random evolutions, and types of
   unseen species in lookups), or match the PC tracker and leave it to the player?
4. Tracker art: vanilla icons only (PC parity), or the ROM's recolour for species already seen?
5. Fix the weakness leak (issue 553) for every mode, not only Paradox?

### What Blake should ask Lightorias (and Timur) for

1. The files: the patch (format; made against clean FireRed 1.1 or Faster FireRed 1.3.2; the base checksum), the
   randomizer and its source (ZX 4.6.1 fork or FVX fork?), the settings file or string, and any tracker changes
   (edited route data or an extension).
2. A version number for each release, a changelog, and where updates are announced.
3. How he wants KaizoCore players to get it: bundled, or their own copy from him.
4. The type rules exactly: two-way splits (Gloom, Poliwhirl, Slowpoke, Wurmple, Clamperl), Eevee and Tyrogue,
   Nincada; any other special cases.
5. The recolour: the algorithm and colours (or the source), the shiny rule, and how the per-seed Paradex sheet is made.
6. The item NPCs: who picks the items and when (randomizer per seed, or the game at talk time), the item pools for
   the three choices and for "get lucky", whether each NPC has an event flag, and the script layout if the
   randomizer writes them.
7. The rotation: which routes and slots, the level pattern, and whether fishing and surfing are untouched.
8. How the Shedinja sign text and the six-Pokemon gym leaders are done (patch or settings), and the Safari levels.
9. Rule clarifications: does the Favorites Clause apply; are only the four pseudo-legendaries banned (Slaking);
   does "draining attacks" include Leech Seed; is Cycling Road blocked in the ROM or only by rule.
10. Credits: how Timur, smol_z, swifferrrrr and anyone else who built it want to be named.

### What to check on a device once it is built

- The patched build is identified as the Paradox kind; Kaizo IronMON offers only Paradox on it; a run randomizes in
  reasonable time on a phone.
- In game: every Pokemon dual-typed; lines, Eeveelutions and the Nincada line follow the rules; sprites recoloured in
  battle, front and back, shiny too; the same seed gives the same colours; the Shedinja sign matches the seed.
- 61 pineapple NPCs, the fixed forest NPC, "get lucky", an NPC left and revisited; no item balls or hidden items left;
  no crash on map changes (Route 9 to Route 10 is the known one).
- Rotation on Routes 1, 2, 22 and Viridian Forest; the tracker's counts read 4, 6, 5, 6 with no rates.
- Lab: 10 Great Balls, happiness maxed, the ball picker before looking, X on the four pseudo-legendaries only, no
  favorite line.
- Tracker: types shown for seen Pokemon, "?" with "Reveal info if randomized" off and no leak through weaknesses;
  vanilla-coloured art on every screen; no coverage, "Include unseen" or random evolution table; X on Swords Dance,
  Tail Glow and Belly Drum; the log after a loss shows the Paradox types; a run code round-trips.
- The Move Relearner in the Pokemon Mansion; dungeon maps without item spots.

## 6. Credits

| Who | What | Link | Licence as found |
|---|---|---|---|
| Lightorias | Creator of Paradox Kaizo, rules | https://www.twitch.tv/lightorias, https://pastebin.com/fVXc2MKr | none stated (pastebin); no public repo |
| Timur (`timur_bey`) | Co-builder: patch and randomizer changes, the recolour algorithm | Twitch chat in #lightorias | files not public |
| smol_z, swifferrrrr | Testing and help | Twitch chat in #lightorias | n/a |
| SaltyDolphin, XWater | Early players and testers | https://www.twitch.tv/saltydolphin | n/a |
| DrMaple | Faster FireRed, the base Lightorias names (unconfirmed) | https://github.com/DrMaple/Faster-FireRed | none (GitHub reports no licence; already in KaizoCore's NOTICE) |
| iateyourpie | IronMON, Kaizo and Super Kaizo, which Paradox builds on | https://gist.github.com/valiant-code/adb18d248fa0fae7da6b639e2ee8f9c1 | already credited |
| WaffleSmacker | IronMON VS, whose rotating wilds inspired Paradox's | https://github.com/WaffleSmacker/IronmonVS-IronmonExtension | MIT |
| Dabomstew, Ajarmar | Universal Pokemon Randomizer and ZX | already vendored | GPL-3.0 |
| foxoftheasterisk and voliol (UPR FVX); Artemis251 (type colour values) | Palette randomizing and type colours, if FVX code or colours are ported | https://github.com/upr-fvx/universal-pokemon-randomizer-fvx | GPL-3.0 (KaizoCore's own LICENSE is GPL-3.0 too) |
| besteon and contributors | Ironmon-Tracker, the reference for the tracker features above | https://github.com/besteon/Ironmon-Tracker | MIT |

Blake's standing ruling is that KaizoCore has the licensing it needs. These are recorded for the credits.

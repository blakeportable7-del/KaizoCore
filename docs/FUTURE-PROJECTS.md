# Future projects

Things Blake wants in KaizoCore after the current builds, with what is known about each. Newest first.

## 2026-10-01: Banned moves marked on the tracker (from Blake: "notate on the tracker the banned moves with some-kind of annotation depending on the game mode you are playing and game")

- **What:** the tracker marks any move the mode in play bans in this game, on the lead's move list and wherever a
  move is looked up, with one line saying why (for example "Banned in Kaizo IronMON on Red"). Only in a mode with
  rules: nothing shows in Play any game.
- **Sources:** the core IronMON Move Ban List (psydetrack's gist, gist.github.com/psydetrack/884443c4c4054decce2804bb513d8d45,
  linked from the core rules Blake pasted from the IronMON Discord on 2026-10-01); UTDZac's per-game gist (Red, Blue
  and Yellow add Wrap, Bind, Fire Spin and Clamp: the per-game entry below); each mode's own ruleset (Standard,
  Ultimate, Kaizo, Survival, Super Kaizo, the Nuzlocke presets). RogueMon players asked for "banned in this ascension"
  on moves (docs/private/roguemon-discord-mapping.md, feat/roguemon's handoff branch); those bans live in the ROM, so
  ask its developers. Read each list before marking anything; never mark a ban from memory.
- **The IronMON dev team's rule (2026-09-30):** the app must not break the rules, reveal hidden information or tell
  players how to play, so the mark states the rule and nothing more.
- **Fit:** the tracker already knows the game and the mode (GearScope works both out for Tracker Setup), so this is a
  ban table keyed by mode and game plus a mark on the move row. Size: S to M, most of it reading the rules.

## 2026-10-01: IronmonConnect, the run on Twitch (from Blake: "table this to add in the future, make a note for a future release")

- **What it is:** WaffleSmacker's IronmonConnect (github.com/WaffleSmacker/IronmonConnect-IronmonExtension; MIT with a
  clause asking for prominent credit to WaffleSmacker in anything adapted from it; 3 stars, last push 2026-08-27). A PC
  tracker extension (ironmonConnect.lua) writes tracker_output.json after each change: the lead's species, nickname,
  ability, level, stats and four moves, trainers defeated, milestone, stars, badges, route, seed, a faint flag, the Nat.
  Dex flag and the bag by category, and the pivots before Brock and the Safari Zone. IronmonConnect.exe, a Windows
  program shipped as a binary in the repo, signs in to Twitch and sends that to the IronmonConnect Twitch Extension, a
  video overlay viewers click to look up the run. The README's link to the Twitch extension says "coming soon".
- **Fit for KaizoCore:** the tracker already holds every field in that file, so writing it is a mapping. The part a
  phone cannot do is run the .exe: the sender (Twitch sign-in on the phone, then the extension's backend) has to be
  built, and its protocol is not published. Ask WaffleSmacker first, as for RogueMon's leaderboard: the endpoint and
  sign-in, their go, and how they want the credit shown.
- **Size:** S for the file, M to L for the Twitch side, which waits on WaffleSmacker and on the extension going public.

## 2026-10-01: Full Clearzo and MaxDex modes, and the per-game rules page (from Blake)

- **Full Clearzo IronMON** (Typo, Puffsun, ratcityretro; github.com/ratcityretro/FullClearzo): Kaizo IronMON on
  FireRed/LeafGreen plus one rule, every trainer in every dungeon beaten before leaving it (Pokemon Tower to Marowak on
  first entry, Rock Tunnel, Silph Co, Victory Road; Sevii One to Three as one dungeon of 26 trainers, Four to Seven as
  another after the Elite Four). Fit: high, the GBA tracker already knows trainers and maps; a mode is a rule switch plus
  a per-dungeon trainer checklist. Size: M.
- **MaxDex Kaizo IronMON** (Trip, thanks to CyanSixFour; github.com/Tripc423/Maxdex): Nat. Dex expansion with Legends
  Z-A Pokemon, the physical/special split, and Gen 9 moves and abilities (not all). Its ruleset page did not load on
  2026-10-01: read it before scoping. Fit: like Nat. Dex (a patch plus the Nat. Dex tracker tables). Size: M to L.
- **Per-game rule adjustments** (UTDZac's gist, updated 2026-07-05): RBY bans Pokemon over 480 BST and Wrap, Bind,
  Fire Spin, Clamp, and needs the growth patch; GSC is won at Red on Mt. Silver and bans berry trees in Kaizo and
  Survival; FRLG keeps the Sevii Islands closed until after the Elite Four; Emerald is won at Steven too and bars
  Slateport before badge 2 in Kaizo and Survival; HGSS is won at Red; Platinum allows the Underground mining once; BW
  warns off the C-Gear. Check each against docs/IRONMON-RULES-CHECK.md and the trackers before claiming support.

## RogueMon mode (added 2026-09-29; reminder set for 2026-10-06)

**Tabled 2026-10-01** (Blake: "lets table rogueMon, for now"). Not in rc33. The work stays on feat/roguemon and the
rm/* review branches, switched off; nothing about it is sent or posted until Blake picks it up again.

Blake: "add this addition to a future project, remind me in a week". Then, the same day: "ROGUEMON lets get it up,
do your research and see if it is viable to add to the modes". **Researched 2026-09-29: yes with limits, XL.** The
full findings, the bug record and the plan are in docs/research/roguemon.md; the notes below are the first pass.

- **What it is:** Crozwords' FireRed/LeafGreen randomized Pokemon roguelike (https://github.com/Crozwords/Roguemon,
  community Discord linked there, roguemon.gg). One randomized Pokemon; win by locking every type and finishing a run
  without fainting. Prizes at milestones, cursed sections, buy and cleansing phases, three ascensions (A1 to A3),
  Roguestone as the one evolution item, randomized evolutions that keep a type and the BST distribution, a forced
  route. The README says most rules are hard-coded into the game build.
- **The pieces around it** (from docs/research/repos-to-add.md, section on Roguemon, and nuzlocke-trackers.md):
  - Rules: Crozwords/Roguemon (no licence file; 16 stars; last push 2026-07-25).
  - Builds: drumstix576/roguemon-releases (no licence file; v2.2.11-beta.0 on 2026-09-22), on the FireRed Nat. Dex
    base KaizoCore already supports.
  - Tracker extension: something-smart/Roguemon-IronmonExtension (no licence file): HP and status caps, prizes,
    segments with trainer counts, curses.
  - Randomizer: something-smart/ironmon-randomizer (GPL-3.0), a ZX fork with a Roguemon build (vR.1.1).
- **Fit for KaizoCore:** a mode on the Run tab for FireRed Nat. Dex that applies the RogueMon build to the player's
  own ROM and a tracker panel for its caps, prizes, segments and curses, the way the IronMON modes work today.
- **Effort:** XL (weeks). The ruleset and automation are large; the randomizer part alone is GPL-3.0.
- **People:** Crozwords (rules, Twitch), something_smart (tracker extension, randomizer), drumstix576 (builds).

## Parked 2026-10-01: a desktop version, and play-as on DS

- **Windows and Mac** (Blake: "can kaizocore work on PC and Mac?"): Compose Multiplatform for the app plus a small
  libretro host for the cores. Several weeks. A Mac version fills a real gap: BizHawk, which the PC trackers run in,
  has no Mac build.
- **Play as your Pokemon on DS** (Blake: "why can't DS use custom sprites?"): the DS overworld is 3D, so it is a
  per-game texture swap, not a sprite swap. HeartGold and SoulSilver first.

## Queued from the 2026-09-29 plan

- Shipped in rc32 (2026-10-01): Nuzlocke mode, the Home screen with its four modes, Build your own (any starter), and
  Play as your Pokemon on Game Boy Advance games. Play as your Pokemon on Game Boy (Red, Blue, Yellow, Crystal) is on
  branch feat/gb-play-as (docs/research/gb-play-as.md).
- 3DS: docs/3DS-RESEARCH.md and the plan Blake shared with the IronMON dev team. Research only, nothing built.
- The rest of the 2026-09-29 repo list (docs/research/repos-to-add.md: Platinum GIGA patch, IronMON HGSS, Diamond
  IronMON, the Black intro patch, the patch editor, Kelsey Young's maps, Hackdex, Following Platinum): not re-checked
  against rc32.

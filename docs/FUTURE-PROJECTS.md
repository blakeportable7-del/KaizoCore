# Future projects

Things Blake wants in KaizoCore after the current builds, with what is known about each. Newest first.

## RogueMon mode (added 2026-09-29; reminder set for 2026-10-06)

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

## Queued from the 2026-09-29 plan

- Nuzlocke mode: rules in docs/research/nuzlocke-variants.md, per game in nuzlocke-gen1-3.md and nuzlocke-gen4-5.md,
  build plan in nuzlocke-trackers.md (extend KaizoCore's own tracker; Gen 3 first).
- Your character becomes your lead Pokemon: UTDZac/SpriteIsMe-IronmonExtension (MIT), Gen 3; KaizoCore already ships
  the Walking Pals sprites it uses.
- 3DS: docs/3DS-RESEARCH.md and the plan Blake shared with the IronMON dev team.
- Welcome screen and main menu (Play any game, Nuzlocke, ROM hacks, Kaizo); Build your game (any starter).

# KaizoCore and the IronMON rules

Checked for rc32, 2026-09-30, after the IronMON dev team asked that the app not break the rules, not show
players anything they should not see, and not tell them how to play.

Two reviews were run against the rules files in `app/src/main/assets/rulesets` (the ironmon-rulesets text) and
against the PC trackers (Ironmon-Tracker for Gen 1 to 3, NDS-Ironmon-Tracker for DS). Every finding below was fixed
with a test, or is listed under "Still open" with the reason.

## What the app holds to

- **No randomizer data before the run is over.** The randomizer log opens from the game-over screen only. The PC
  tracker also offers it mid-run behind a warning; KaizoCore does not. Open Book, which a player turns on to see
  everything, reads route encounters from it. The stream's "every species as randomized" data is refused until a Kaizo IronMON run has ended.
- **The tracker shows what the PC trackers show**, with their options and their defaults: "Reveal info if
  randomized", "Show data for vanilla game", "Hide stats until summary shown", Open Book.
- **One line the PC trackers do not have (2026-10-01).** In a Kaizo IronMON run's lab on a Game Boy Advance game, the
  tracker names the ball that holds one of the player's favorites, when the mode lets them take it: the Favorites
  Clause, which lets a favorite replace the blind random pick. It names only that ball, never what the others hold,
  and nothing for a favorite past the mode's official limits: one legendary at most; no BST limit in Standard
  and Ultimate; under 600 BST in Kaizo and the modes built on it, 600 and lower in Evo Kaizo; Survival under 580
  with no legendary; Super Kaizo no legendary; Nat. Dex up to 600, and above that in Standard and Ultimate unless
  Strong Legendary or Mythical. Journey, which lets a player take any starter, has no such line (FavoriteBall,
  FavoriteBallTest).
- **No advice on how to play.** There are no bundled tips or strategy lines. The line under each mode says what the
  rules file says, and a test reads it back from the file.
- **A run is a run.** Rewind and cheats are off in Kaizo IronMON runs and in Nuzlockes. Every state load, Time
  Machine restore, battle retry, resume after a crash, File > Restart, kept in-game save and backup restore is
  written on the run's record, and the death card and the shared line say so.

## Fixed in rc32

### What a player or a viewer could see

| Was | Now |
|---|---|
| The stream showed an opponent's two possible abilities from the randomized game in every battle. | Only where the phone's card shows them: an unrandomized game, or Open Book. DS shows none, as the DS tracker does. |
| `/dex.json` (every species as randomized) was served, and linked in the stream guide, during the run. | Refused until the run is over; the guide no longer links it. |
| The stream's "run over" view opened when the tracker read a loss, including a Nuzlocke lead's faint. | It follows the Kaizo IronMON game-over screen, and Retry closes it. |
| The stream sent the opponent's exact HP and max HP (with the level, that gives back its base HP). | A percentage only, as the in-game bar shows. |
| The stream ignored "Reveal info if randomized" and "Hide stats until summary shown". | It hides what the phone hides. |
| DS Tracked Pokémon listed a species' randomized abilities before any were seen. | Only abilities seen this run, else "---". |
| Move info showed an opponent's randomized PP and accuracy that its row hides as "?". | Hidden on the card too, as the PC tracker's move screen does. |

### Rules and records

| Was | Now |
|---|---|
| GAME OVER, Retry and New game appeared in Nuzlockes and library games. | Only in Kaizo IronMON runs. A Nuzlocke ends by its own rules. |
| Gold, Silver and Crystal ran without the pseudo-fluctuating growth patch, which the rules require for Crystal. | Prepare defaults to the patch for those games, and the Kaizo IronMON screen says when a copy lacks it and makes the patched copy in one tap. |
| DS runs ended on the lead fainting in every mode. | The rule follows the mode, as Gen 1 to 3 do: Standard and Ultimate end when the whole party is down, Kaizo Doubles on either of the first two, the rest on the lead. The player can still change it. |
| Red, Blue and Yellow Survival ended the run when the HM friend fainted in the lead slot, which that mode allows. | That mode ends on the highest level fainting. |
| "Keep my in-game save" could carry the last seed's team into a new game. | The save stays on a new seed in every game (Blake, 2026-09-30), and New Game on the title screen is the clean start. When the new run's first party holds a Pokemon of the last run's, Continue brought that team along, and it is written on the run's record (rc34, KeptSave). |
| A backup restore, File > Restart and a reopened "left" moment went back in time with nothing recorded. | All three are on the record. |
| A custom settings file (Build your own, an edited copy) showed and counted as the official mode. | It says "(custom)" on the Kaizo IronMON screen, the new-run question, the Home card, the shared run line and Your stats. |
| Mode lines: Survival left out when the heal count starts; Journey said "one emergency swap"; the favorites line left out the mode's limits; Build your own called every mode official. | Each says what its rules file says. Journey runs show no ball picker, since any starter may be chosen. |
| One attempt count per game: Standard, custom and Nuzlocke games all went into the number on a Kaizo death card. | Counted per settings file, as the PC tracker counts per profile. Each file started from the game's number as it stood. A Nuzlocke counts no attempt. |
| A run replaced before it ended (a re-roll, a bail before the popup) left no line in its history. | It is filed as ended by a new run. |
| Your stats and NEW BEST counted a win after state loads, retries or restarts like any other. | Runs won says how many came after them, a best run names the count, a streak counts clean wins only, and the shared line says "New best, after state loads, retries or restarts". |
| A record did not say when an official file ran without its 60% levels or PART 2, or Super Kaizo without Smart AI. A run code rewrote the player's pass switches. | The record, the card, the shared line and Your stats say "50% levels", "no PART 2", "no Smart AI" or "+6% levels". A code's passes apply to its one build. |
| A run code could rebuild a seed already played, with a clean record. | The build says first which attempt played the seed and how it ended, and the new run's record says it came from a code. |
| "Game is considered over when" could be changed mid-run with nothing recorded. | A change to other than the file's own rule goes on the run's log, and the rule in effect goes on its record and card. |
| HeartGold and SoulSilver Survival's 7 Kanto heals were counted by hand. | Added once when the Johto League is beaten. |
| The DS main panel drew coverage counts, which the DS tracker shows only on its Coverage Calc screen. | Only under COVERAGE CALC in Tracker Setup. |
| The FireRed/LeafGreen guide maps included step by step plans, Saffron Gym's solution and hidden item screenshots from another seed. | Those no longer ship. Seven dungeon maps remain, off by default, labeled "routes and item spots". |
| "Strong against / Resisted by / No effect on" showed under every move's info. | Behind a switch in Tracker Setup, off by default. |
| A backup carried the live run's randomizer log. | Left out until the run is over. |

## Checked and fine

- All 74 bundled settings files decode to the official page's strings, the community modes' own files and the
  Nat. Dex author's files.
- The 60% levels pre-pass and Gen 1's PART 2 run exactly as the settings page describes, on by default only where
  the page calls for them.
- Opponent moves, stats and abilities on the phone: only what was seen, marking boxes for stats, as the PC does.
- Trainer info, route info, catch rates, battle details, the notebook and move history: the PC's own inputs.
- The random ball picker is a fair 1-of-3, lab only, like the PC's.
- Calc Atk uses the move's real power even when the row shows "?", as the PC extension does.
- The game-over screen offers what the PC's does: the log, Retry, Continue.

## Still open

- **Gold, Silver and Crystal Survival:** the 7 heals for Kanto are added by hand with + on the counter; the Game Boy
  tracker has no League flag to watch, as HeartGold and SoulSilver's does.
- **The seven FireRed/LeafGreen maps that remain** still draw routes, item spots and trainer counts (every one of the
  eleven did, not only the four that were dropped). They are off by default and say what they show; drop them too
  if they should go.

## Decided (Blake, 2026-09-30: "go with your recommendations on all four")

1. **FireRed/LeafGreen guide maps:** the step by step plans, Saffron Gym's solution and the screenshots from another
   seed are gone; seven maps stay, off by default.
2. **Type matchups in move info:** behind a switch, off by default.
3. **The live run's randomizer log:** out of backups until the run is over.
4. **Attempts:** counted per settings file, each file starting from the game's number as it stood.

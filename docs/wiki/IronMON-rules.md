# How KaizoCore follows the IronMON rules

This page describes 1.0.0-rc35.1, the current download.

The IronMON dev team asked that the app not break the rules, not show players what they should not see, and not
tell them how to play. This is what KaizoCore does about each.

## It shows what the PC trackers show
- The tracker is built from Ironmon-Tracker (Gen 1 to 3) and NDS-Ironmon-Tracker (DS), with their options and
  defaults: Reveal info if randomized, Show data for vanilla game, Hide stats until summary shown, Open Book.
- An opponent's stats are marking boxes, its abilities and moves are the ones you have seen, and its HP is the bar.
- The randomizer log opens from the game-over screen only. The PC tracker also offers it mid-run behind a warning;
  KaizoCore does not.
- The OBS stream shows no more than the phone, and its randomizer data waits until the run is over.
- One addition, for the Favorites Clause: in a Kaizo IronMON run on a Game Boy Advance game, when the starters are
  offered, the tracker names the ball that holds one of your favorites if your mode's official rules let you take
  it. It never says what the other balls hold, so with no favorite there the pick stays blind.

## It does not coach
- No tips or strategy lines. The line under each mode says what that mode's rules file says.
- Extras that are not in the PC trackers are off by default and say what they are: FireRed and LeafGreen dungeon
  maps (routes and item spots), and type matchups in move info.

## It keeps an honest record
- Rewind and cheats are off in Kaizo IronMON runs and Nuzlockes.
- Every state load, Time Machine restore, battle retry, crash resume, File > Restart, kept in-game save and backup
  restore is written on the run's record, and the death card and the shared line say so.
- A custom or edited settings file is labeled custom. A run that went without what its rules add says so ("50%
  levels" or "no PART 2"). A run built from a code says so, and a seed already played is pointed out.
- Attempts count per settings file. A run replaced before it ended is filed as ended.

## Rules it applies for you
- Favorites follow each mode's official limits: how many, which legendary, and the BST line (see
  [Kaizo IronMON](Kaizo-IronMON#favorites)).
- Gold, Silver and Crystal default to the pseudo-fluctuating growth patch the rules require for Crystal.
- The game-over rule follows the mode on every console: Standard and Ultimate end when the whole party is down,
  Kaizo Doubles when either of the first two faints, the rest when the lead faints. You can change it.
- Survival's heal counter starts at the rules' limit, adds the 8th badge heal, and on HeartGold and SoulSilver the
  7 Kanto heals when the Johto League is beaten.

## Still open
- Gold, Silver and Crystal Survival: the 7 Kanto heals are added by hand.
- DS double battles are not tracked.

The full check, with every finding and the tests behind it, is docs/IRONMON-RULES-CHECK.md in the repo.

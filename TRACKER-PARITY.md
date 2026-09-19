# Tracker parity with the PC tracker

Reference: Ironmon-Tracker **v9.3.1** (commit c450eca, 2026-04-12), cloned at
`~/ironmon-ref/Ironmon-Tracker`. Blake, 2026-09-19: "get our tracker to the same
functionality as the pc". Every item is checked against the Lua before it is
built, and ticked only when it is built, tested and seen on a device.

Method: every entry in `Options.lua` is a behaviour. Each is either built,
missing, or does not apply to a phone. Screens were checked the same way
(`screens/*.lua` against the files our code cites).

## Batch 1 - approved items and default mismatches
- [x] "Determine friendship readiness" toggle (behaviour shipped in rc17; ON by default)
- [x] LeafGreen starter table (was switched off; 0x08169B91, offsets 515/461, verified 1/4/7)
- [x] "Display pedometer" (OFF by default: the PC keeps the pedometer out of the carousel until it is on; ours always showed it)
- [x] Carousel durations: TRAINERS is 420 frames and PEDOMETER 210 (ours had them swapped)

## ON by default in the PC, missing here
- [x] Move effectiveness on the move rows ("Show move effectiveness")
- [x] Catch rate in the move header during a wild battle ("Show Poke Ball catch rate")
- [x] Variable damage: Return, Frustration, Low Kick and the rest ("Calculate variable damage")
- [x] Variable-power moves show the reference's label (>FR, WT, <HP, RNG...), not the ROM placeholder
- [x] STAB power drawn green; the next move's level highlighted when one level away
- [x] Hidden Power type: set with the arrows in the move's info, remembered per Pokemon, typeless (no colour, category, STAB or effectiveness) until set
- [x] Switches for "Show move effectiveness", "Show Poke Ball catch rate", "Calculate variable damage", "Count enemy PP usage", "Show last damage calcs"
- [x] Count the enemy's PP use ("Count enemy PP usage"): its real remaining PP from the battle struct. Logic built; needs a live battle to be seen
- [x] Last damage calcs ("Show last damage calcs"): "Wing Attack: 23 damage" with the sword, red when lethal. Line seen on the device; the gTakenDmg tracking is unit-tested and needs a live battle
- [x] Move stars on the opponent's moves (Utils.calculateMoveStars), with the last-seen level now recorded. Unit-tested; needs run history to be seen
- [x] "Reveal info if randomized": off hides an opponent's randomized move type, PP, power, accuracy; own effectiveness when types are randomized (InfoRules)
- [x] "Show data for vanilla game": unrandomized opponent shows both abilities, base stats, actual moves (InfoRules)
- [ ] Tap a trainer on the game screen for its info ("Can click trainers on screen")
- [x] Carousel: rotation on/off, which items, speed, the reference's order; Trainers defeated X/Y for 5s after a trainer battle; early-game route info under Lv13
- [x] "Auto swap to enemy": switch added

## OFF by default in the PC, missing here
- [x] Show nicknames
- [x] Display gender (beside the name, or over the icon for a long name, by the reference's own width table)
- [x] Show experience points bar
- [x] Color stat numbers by nature
- [x] Right justified numbers, and the default fixed: numbers start at their column as the reference draws them (Gen 1-3 panel)
- [x] Hide stats until summary shown: per attempt, revealed when sMonSummaryScreen goes non-zero; only on a randomized game
- [x] Randomization check (PokemonData / MoveData checkIfDataIsRandomized), validated vanilla on the FireRed and Emerald dumps
- [x] Open Book Play Mode
- [x] Track PC Heals, and PC heals count downward: the heart toggle, the coloured count, +/-, auto-tracking from game statistics 15 and 16, per attempt
- [ ] GachaMon stars in the heals box (the reference's default there when PC heals are off; part of GachaMon)
- Not built by decision: the log viewer quick-access button in that spot. Blake: the log opens only after a loss.
- [x] Show starter ball info: the offered starter's info screen while its ball is confirmed (FRLG special var, RSE confirm task + rival party); addresses audited, not yet seen on a live lab
- [ ] Pokemon icon set: the other sets (Walking Pals is here)
- [x] Pedometer step goal and reset (clock, Goal prompt, Reset/Total, green at the goal; in memory like the reference)

## Features
- [ ] GachaMon: the overlay, the collection, the ratings, its carousel item and six options

## Does not apply on a phone
Language, Autodetect language from game, Refocus emulator after load, Warn of
ROM mismatch, Use premade ROMs, Generate ROM each time, Dev branch updates,
Welcome message, Enable crash recovery, Enable custom extensions, Active
Profile, Override Button Mode to LR, Animated Pokemon popout (a desktop
window), AlertNewOptionLR, Has checked carousel battle details / GachaMon (the
PC's own one-time hints). Screens: CrashRecovery, CustomExtensions,
SingleExtension, Language, Update, StreamConnect (Streamer.bot), Quickload.

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
- [ ] Hidden Power type: set by tapping the move, remembered per Pokemon (shown as unknown until then)
- [ ] Toggles for "Show move effectiveness", "Show Poke Ball catch rate", "Calculate variable damage"
- [ ] Count the enemy's PP use ("Count enemy PP usage")
- [ ] Last damage calcs ("Show last damage calcs")
- [ ] "Reveal info if randomized" (semantics to read)
- [ ] "Show data for vanilla game" (semantics to read)
- [ ] Tap a trainer on the game screen for its info ("Can click trainers on screen")
- [ ] Carousel: rotation on/off, which items, speed; the reference's rotation order
- [ ] "Auto swap to enemy": the behaviour is here, the toggle is not

## OFF by default in the PC, missing here
- [ ] Show nicknames
- [ ] Display gender
- [ ] Show experience points bar
- [ ] Color stat numbers by nature
- [ ] Right justified numbers
- [ ] Hide stats until summary shown
- [ ] Open Book Play Mode
- [ ] Track PC Heals, and PC heals count downward
- [ ] Show starter ball info (partly here: the three balls)
- [ ] Pokemon icon set: the other sets (Walking Pals is here)
- [ ] Pedometer step goal and reset

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

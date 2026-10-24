# KaizoCore PC: animation and stream-interaction plan (exploration, 2026-10-09)

This is the copy (`IronMonOne-wt-pc-anim`, branch `feat/pc-tracker-anim`). Nothing here touches the tracker Blake
plays. The prototype is in `desktop/src/main/kotlin/com/ironmonone/pc/fx/` and is not wired into the live window yet.

**Updated 2026-10-10** (Blake: "build the scenario sandbox first, use the rom assets when you can"). Built since the
first draft: the scenario sandbox (section 6), the game's own art read from the ROM (section 5), items reacting by
their own icon and use (section 4a), and looks for Substitute, Disable, Taunt, Encore, Leech Seed, Perish Song and
weather. The showcase scenario plays 30 seconds of a gym battle through all of it.

## 1. How Lua games animate, and what applies here

The usual Lua toolkit (LÖVE and friends):

- **Delta time.** Each frame gets the seconds since the last one (`update(dt)`), and motion is speed times dt, so it
  runs the same at 30 or 144 fps. BizHawk scripts often count emulated frames instead (`emu.framecount()`), which ties
  the animation to game speed: at 400% it plays four times as fast. Our script already corrects for that (`every()`
  scales by `client.get_approx_framerate`).
- **Tweening and easing** (rxi/flux, tween.lua): a value goes from A to B over a set time through a curve (outBack
  overshoots and settles, outElastic wobbles, inOutSine breathes).
- **Sprite-sheet animation** (kikito/anim8): a grid of frames, each with a duration, played by time. Our Walking Pals
  sheets already work this way (`durations` per frame).
- **Particles**: many small things, each born, moving and fading. They can be stateless: position as a pure function
  of (seed, index, time), so any moment can be drawn and nothing is stored.

**What applies to KaizoCore PC.** In window mode the Lua script draws nothing of the card. The jar draws the card
(Compose, only when the game changes) and the tracker window shows it (Swing). So the animation belongs in the jar's
window, not in Lua. Lua keeps what it draws over the game (repel, GachaMon), which could use the same ideas.

## 2. Architecture (prototype built)

```
game RAM ──Lua──▶ ram.bin ──jar──▶ TrackerState ──FxEvents.between(prev, now)──▶ events
                                        │                                       │
                                        ▼                                       ▼
                         card picture (Compose, on change)     FxLayer: short effects drawn over the card
                                        │                       at window resolution (Java2D), 60 fps,
                                        └───────── TrackerWindow paints card, then FxLayer ◀──┘
```

- **FxEvents** (`FxEvents.kt`): pure function, two states in, a list of events out. Status on/off, stat stage
  changes, damage, heal, faint, level up, badge (by the bit that turned on), league beaten, battle start and won.
  Only compares the same Pokemon (species and personality value), so a switch-in is not "damage".
  `FxEventsTest` covers it.
- **FxLayer** (`Fx.kt`): the effects playing. The window runs its 60 fps timer only while something is playing
  (`active`), exactly as the favorites strip does now, so an idle card costs nothing. A status aura is the one thing
  that keeps it running while the status lasts.
- Effects draw in **card pixels** on a Graphics2D scaled to the window, as crisp squares, so they match the card's
  pixel grid. They can **lift** a piece of the composed card (the stat number that pops, the sprite that shakes)
  without re-composing the card.
- **The walking sprite walks in the layer.** The card picks its walk frame from the clock only when it is drawn,
  and it is drawn only when the game changes, so between changes the sprite stands still (the live tracker too;
  the sandbox's first GIFs showed it). With `PcPictures.cardDrawsWalking` off the card leaves the sprite's slot
  empty and the layer draws the walk frame for the moment (`FxLayer.walker`), with the status icon over it. Hits
  shake it, a faint sinks it and paralysis twitches it through `spriteMotion`. Cost: the timer runs while a
  Pokemon is shown; repaint only the sprite slot, and only when its frame changes (the idle sheets hold a frame for
  most of a second).
- **Lag budget.** The card is still composed only on change (25 to 50 ms each). Effects are a few hundred Java2D
  calls a frame on a 450x480 window. Measure before shipping; keep particle counts small; one option to turn
  animations off.

## 3. Effects catalogue

Built in the prototype (see them with the sandbox, section 6, or `./gradlew :desktop:fxPreview` for each alone).
Statuses use the game's own sprites where the ROM has them (section 5):

| Event | Effect |
|---|---|
| Burn | embers rising off the sprite, yellow to red, flickering |
| Poison | purple bubbles swaying up and popping |
| Paralysis | yellow zigzag flashes and the sprite twitches |
| Freeze | icy wash on the sprite, crystals glinting |
| Sleep | Z after Z drifting up and growing |
| Confusion | stars circling the head (needs the confusion bit, already read in BattleDetails) |
| Attract | pink hearts floating up, beating |
| Status starts | ring burst in the status colour and the word pops ("BURNED!") |
| Stat stage up / down | the number lifts and pops (squashes on a drop), row flashes green / red, "+2" floats up |
| Hit | sprite shakes (damped spring), white flash, damage number floats off HP; big hit shakes the card |
| Level up | gold sparkle column, "LV UP! 7" |
| Faint | sprite sinks and fades |
| Badge won | card dims, rays turn, badge springs in, confetti, "BADGE ACQUIRED" (Blake, 2026-10-10), badge name, "Congratulations!", leader |

Also built (sandbox): an item used (its ROM icon and use, section 4a), the Substitute doll, Disable (lock and
turns on the move's row), Taunt (status moves dimmed, TAUNT n tag), Encore (other moves dimmed, the encored move
pulsing), Leech Seed (seeds on the sprite, green motes draining), Perish Song (music notes and the count), rain and
hail falling, and an accuracy or evasion drop popping at the card's Acc / Eva line.

Proposed next, from effects the tracker **already reads** (`GbaTracker.battleDetails`, about 40 of them, today only
text in the effects line):

| Situation | How the card reacts |
|---|---|
| Disable (move named) | that move's row greys out, a padlock slides over it, pips count the turns |
| Taunt (turns left) | status moves dim, a red "TAUNT 2" tag sits on the move box and ticks down |
| Encore (move named) | other moves dim, the encored move pulses |
| Torment | the last used move gets a slash and dims for one turn |
| Imprison | moves the foe shares with you get a chain icon |
| Leech Seed | green motes travel from the seeded sprite to the other side each turn |
| Substitute | a doll silhouette over the sprite; it cracks when broken |
| Curse / Nightmare | a dark wisp pulses on the sprite each turn |
| Perish Song | a big musical countdown 3, 2, 1 over the card |
| Destiny Bond / Grudge | a ghostly aura; Grudge firing shatters the foe's PP number to 0 |
| Protect / Detect | a shield flashes; repeated uses show the falling odds |
| Reflect / Light Screen / Safeguard / Mist | a translucent pane on that side's edge with turns left |
| Spikes | spikes along the card's lower edge, one row per layer |
| Weather | rain streaks, sand, sun glare or hail drifting behind the card, turns left on the badge |
| Yawn (drowsy) | the sprite wobbles, then the sleep aura starts |
| Truant (loafing) | a sweat drop and "Loafing..." |
| Charge / Stockpile / Focus Energy | glowing counters on the sprite |
| Fly / Dig / Dive | the sprite leaves its slot and a shadow stays; it returns on the hit |
| Recharging | the sprite dims with a "Recharge" tag |

Big moments for the audience: badge, league, shiny, evolution, a critical hit (screen flash), a one-HP survival
("CLUTCH!"), a KO streak, a run ending (a slow, dramatic card).

## 3b. Moves Blake listed (2026-10-10), and what reads them

"The player can't use banned moves but enemies can": every effect below plays on either card, because an opponent
will use moves the player's rules ban. What the tracker already reads each snapshot is in `HnsBattle.kt`
(`battleDetails`); the rest needs one new read each.

| Move | Already read | How the card reacts |
|---|---|---|
| Spikes / Toxic Spikes | yes, layers per side | spikes (ROM art "spikes") along that side's lower edge, one row per layer; purple for Toxic Spikes; a switch-in takes the hit |
| Stealth Rock / Sticky Web | no (side status, one read) | pointed stones float at the edge; a web over the speed row |
| Wish | yes, turns left | a star rises and waits over the card; on the turn it lands it falls into the HP bar (green sparkles, HP counts up) |
| Heal Bell / Aromatherapy | no (instant): the party's status clearing at once | bells ring (ROM "music_notes" art), every status aura in the party bursts away |
| Rest | no (instant): HP to full and SLP set together | HP counts up to full, then the sleep Zs start with a "2 turns" tag |
| Recover / Roost / Soft-Boiled / Slack Off / Milk Drink | no (instant): HP up, no item used, the last move seen | green sparkles from below, HP counts up; Synthesis / Morning Sun / Moonlight show the weather's share (two thirds in sun, a quarter in rain, sand or hail) |
| Future Sight / Doom Desire | yes, turns left and the user | a violet orb (Doom Desire: a gold one) hangs over the target's card with the count; it drops on the hit |
| Curse | yes (cursed) | a Ghost user: a nail and a dark wisp on the target, a quarter of HP each turn; a non-Ghost: Speed down, Attack and Defense up (the stage pops play) |
| Perish Song | yes, the count | built: notes and the big count 3, 2, 1; at 0 both sprites faint together |
| Destiny Bond | yes | a ghostly chain from the user's sprite; if the user faints while it holds, the chain pulls the other side down too |
| Grudge | no (one read) | the same chain, gray; firing shatters the PP number to 0 |

More of the same kind, all on the read list or one read away: Ingrain (roots, yes) and Aqua Ring (yes, water ring);
Nightmare (yes, a dark cloud over the sleep Zs); Protect / Detect / King's Shield (yes, uses: a shield flash and the
falling odds 1, 1/3, 1/9); Endure, Focus Sash and Sturdy holding at 1 HP ("CLUTCH!"); Trick / Switcheroo and Knock Off
(the held item flies off or swaps cards, and that reveals the foe's item); Belly Drum (HP halves, Attack to +6); Pain
Split (both HP bars meet); Haze and Clear Smog (every stage arrow sweeps to 0); Psych Up (the stages copy across);
Baton Pass (the stages ride with the switch); Memento and Explosion / Self-Destruct (the user's card goes dark);
Counter / Mirror Coat (a reflecting flash); Transform and Imposter (the sprite morphs); Bind / Wrap / Fire Spin (yes,
trapped: bands around the sprite); Mean Look / Block (yes, "Can't Escape": a chain on the run line); Yawn (yes,
drowsy: the sprite wobbles, then sleeps); Reflect / Light Screen / Safeguard / Mist / Tailwind / Lucky Chant (yes,
with turns: panes on that side's edge); Trick Room and Gravity (yes, field: the card tilts, the sprite sinks).

### Abilities, the moment the game shows them

Blake (2026-10-10): "add volt absorb, etc ability animations at the same time they are revealed to the player". The
game itself marks the reveal: the expansion shows the ability pop-up and records the ability for the battler
(`RecordAbilityBattle`), so the card plays the ability's effect on that frame and never before. That keeps the
information rule (a foe's ability counts only once shown) and puts the animation where the player sees the pop-up.

| Ability (examples) | Shown when | What plays |
|---|---|---|
| Volt Absorb, Water Absorb, Dry Skin | a move of that type is absorbed | the move's colour pours into the sprite, HP counts up |
| Lightning Rod, Storm Drain, Motor Drive, Sap Sipper | the same | absorbed, then the stat pop (Sp. Atk, Speed, Attack) |
| Flash Fire | a Fire move absorbed | the sprite catches a flame that stays (its Fire moves now hit harder) |
| Levitate | a Ground move misses | the sprite hovers, a dust puff under it |
| Intimidate | on switch-in | the other card shakes, Attack -1 pops |
| Drizzle, Drought, Sand Stream, Snow Warning | on switch-in | the weather rolls in behind the card |
| Sturdy | survives a one-hit KO or a full-HP hit at 1 | "CLUTCH!" and a brace |
| Pressure | on switch-in | a dark ripple; the PP rows tint (each move costs 2) |
| Wonder Guard, Clear Body, Keen Eye, Insomnia, Soundproof | when it blocks something | a shield flash over the sprite (the drop, the status, the move it stopped) |
| Speed Boost, Moody | at the end of a turn | the stat pops |
| Rough Skin, Iron Barbs, Static, Flame Body, Poison Point | on contact | spikes or sparks back at the attacker |

**Built in the sandbox (2026-10-10):** `desktop/.../fx/FxAbility.kt`. Every reveal lights the ability's line on the
card (the words pop from their left edge, a shine runs along them, a gold underline draws in), and its kind plays in the
sprite slot with the game's own sprites: absorbs (sparks, rain drops or seeds streaming in, then a glow and, for a heal,
green sparkles), Flash Fire (embers in, flames at the feet), Levitate (the sprite floats and bobs over a dust puff),
Intimidate (a red glare on its card; on yours the anger mark, a cower and a sweat drop, with the Attack drop popping),
weather setters (the weather gathers, then stays: rain, hail, and new sun and sandstorm looks), Sturdy (the Protect
shield, "STURDY HELD ON"), the blocking abilities (the shield, lighter), Pressure (dark ripples and the PP column
tinting), contact abilities (barbs, sparks, embers, spores bursting off) and the stat abilities (a glow, the stat
popping). The sandbox also does what the ability does: a quarter of HP back, the stat changes, the weather, Sturdy at
1 HP, and it writes the reveal into the tracker's notes so the opponent's card prints it. Live, the hook is
`TrackerState.abilitiesRevealed` (`FxEvents.between` turns each reveal into an `Ability` event; `FxEventsTest` checks
it). `sandbox/scenarios/abilities.txt` plays ten of them. The damage number now rises inside the sprite slot instead of
over the name.

### Enemy abilities that never show (checked against the source, 2026-10-10)

Blake: "compound eye ability enemy gives no notification. only things revealed to the player in battle is revealed and
reflected in changes on the tracker for enemies." The live tracker already works that way for Heart & Soul: an enemy
ability is revealed only while the game's ability pop-up is on screen (`readAbilityTriggers`: `gBattlerAbility` and the
four `BattleScript_AbilityPopUp*` scripts), and the one place that uses an enemy's ability, its move types
(`BattleMoveTypes`), asks `abilityShown` first; nothing reads `EnemyInfo.abilityId`. Its held item is never used.

Fourteen abilities never show a pop-up in Heart & Soul: Compound Eyes, Tinted Lens, Victory Star, Hustle, Unaware,
Sand Veil, Snow Cloak, Tangled Feet, Wonder Skin, No Guard, Super Luck, Merciless, Shell Armor, Battle Armor. They
appear only in the accuracy, crit and damage maths (at most a `RecordAbilityBattle` for the AI). So on an opponent they
change nothing on the card, and play nothing, unless another pop-up names them (your Trace, Role Play or Skill Swap).
Checked to show one: Volt / Water Absorb (`BattleScript_MoveHPDrain`), Intimidate, Pressure and the other switch-in
messages, Speed Boost (`BattleScript_AttackerAbilityStatRaise`), Static / Flame Body (`BattleScript_AbilityStatusEffect`),
Rough Skin, Flash Fire, Sturdy, Levitate (`BattleScript_NoEffectivenessAbility`), Clear Body and the stat-drop
blockers, Keen Eye / Mind's Eye / Illuminate when they stop an accuracy drop. (Sturdy holding on at full HP is behind
Heart & Soul's own `tx_Mode_Sturdy` challenge setting.) An opponent's BrightPowder, Wide Lens, Zoom Lens or Scope Lens
never announce themselves; only Frisk, Knock Off, Trick or Thief name them.

In the copy: `Abilities.SILENT` holds the fourteen and gives them no effect (`AbilitiesTest`), and the sandbox refuses
a reveal the game could not make: `foe ability Compound Eyes` and `foe item Bright Powder` stop with an error until the
line says what showed them (`by Trace`, `by Frisk`). The first abilities video had a Shell Armor reveal and the first
accuracy video a Sand Veil reveal; both were impossible and are gone.

The accuracy and crit maths pick the ability up the same moment, so a revealed Keen Eye changes the Acc column as
it shows (the abilities-items scenario plays exactly that).

## 3c. Lasting effects follow the game, and the move locks (built, 2026-10-10)

Blake: "can the status effects continue until they are gone or cured? ... and like leech seed or spikes", "and moves
locked, like encore, disable, the one that blocks you from non attacking moves". Lasting effects are no longer events.
Each read, `FxLasting.from(battleDetails)` turns the game's own list (the field, each side, each battler, already read
for the Battle Details screen) into what is on, per side, and `FxLayer.sync` starts what is new, keeps what stays (a
count changing does not restart it) and stops what the game dropped: cured, worn off, switched out, the battle over.
Each side keeps its own, so flipping the card loses nothing. Confusion and infatuation last beside a burn or poison
instead of replacing it. Every look lives in a zone of the sprite slot (feet, body, head, the two edges), inside the
card's frame, pixel by pixel.

Only the Nat. Dex Kaizo pool's effects have a look (Blake: "we only have 66 abilities", "same rules as natl dex kaizo
emerald"; the pool read off `HnsUprRules` that day): Spikes (a spike a layer at the feet), Reflect, Light Screen,
Safeguard, Mist (panes down the slot's edges, turns as pips), Leech Seed, Perish Song (the count in the head corner),
Wrap/Bind/Fire Spin/Whirlpool/Sand Tomb/Clamp (bands turning round the body), Curse (the nail and a ghostly spirit),
Nightmare (a black cloud), Ingrain (roots), Focus Energy, Yawn (a Z and a sway), Future Sight and Doom Desire (an orb
with the turns), Wish (a star), Lock-On and Mind Reader (the reticle), Stockpile (an orb a stockpile), Charge (sparks),
Destiny Bond (a ghostly chain), Substitute (the doll), the four weathers. Not in the pool, so no look: Toxic Spikes,
Stealth Rock, Sticky Web, Aurora Veil, Tailwind, Lucky Chant, Heal Block, Throat Chop, Embargo, Gravity, Trick Room, the
terrains, Aqua Ring, Magnet Rise, Laser Focus.

**Move locks** (`FxLayer.drawLocks`): each blocked row dims under a padlock in its lock's colour, Encore's own move
glows, and the locks' names take turns in the moves header's gap. Disable (its move), Encore (all but its move), Taunt
(status moves), recharging (all). Torment and a rampage (Outrage, Thrash, Petal Dance) show their name only, and Imprison
not at all: which move they hold needs the battler's last move (gLastMoves, gLockedMoves), not read yet. Choice items
would need the same read.

Settings has "Battle animations" and "Level-up and item animations" (Battle tab); `FxEventsTest` proves each stops only
its own. The layer draws nothing while the card shows another screen (it drew the walking sprite over Settings).

**Abilities in the pool** (68 under the Kaizo bans, `docs/ABILITY-POPUPS.md` for every trigger): 26 always show a
pop-up, 19 on some triggers (Clear Body and the stat-drop blockers only against status moves and Intimidate; the status
immunities only against status moves), 23 never (Natural Cure among them, so its look is gone). Pickup's pop-up shows
only at the end of a turn when it picks up an item another Pokemon used up; the after-battle pickup is silent.
`Abilities.SILENT` is all 112 of the game's silent abilities, held to `docs/ability-popups.tsv` by `AbilitiesTest`.

## 3d. Design audit, 2026-10-10, and what changed

A critic's pass over every scenario at full size found the system sound and the sprite slot overloaded. All sixteen
findings were fixed the same night ("Fix all"):

- **One moment at a time.** Effects that use the sprite slot (a hit, a faint, an ability, a status's onset, the
  accuracy and evasion labels, an item, a candy, a level, a new move, effort) queue in the game's order and take turns;
  a backlog over 3 s drops the least important. The lasting looks over the body and head step back while a moment
  plays, and the head's looks (Perish, Yawn, Future Sight, Wish, Lock-On, Stockpile, Nightmare, Curse, confusion,
  infatuation) take turns every 2 s instead of piling up.
- **Takeovers play alone:** nothing draws under the badge screen, and it dims harder. Its banner reads "BADGE ACQUIRED".
- **On the pixel grid:** rings, glows and outlines are drawn in whole card pixels (`Px.ring`, `Px.disc`, `Px.box`), and
  the glint across a line is one band, not five fractional stripes.
- **The shield** is a crisp bubble edge with a travelling glint; the Pokemon shows plainly inside it.
- **The opponent's stat pop** grows its label and arrow a little; it no longer blows up your marking box, and no number
  floats by the gear. A stage back to 0 no longer lights up BST.
- **Hearts** at the other statuses' scale; **Future Sight's and Stockpile's orbs** tinted (violet, amber); **Levitate**
  lifts 7 px over a shadow; **item icons** drop into the slot's corner, clear of the face, with no grey bag.
- **Colour:** accuracy changes are amber (harder) and cyan (easier), clear of the Pow column's red and green; Encore's
  lock is magenta, a rampage's teal.
- **Weather** falls inside the sprite slot, a window on the field, not over the card's words.
- **The new move's old row** shows until its turn, then the new one slides in.

## 4a. Items that react by what they are (built in the sandbox)

The item's own icon (read from the ROM) comes out of the game's item bag, pops over the sprite with its name, then its
**use** plays, also read from the ROM (`ItemInfo.battleUsage`):

| Use | Items (examples) | What plays |
|---|---|---|
| 1 restore HP | Potion, Super / Hyper / Max Potion, Fresh Water, Soda Pop, Lemonade, Moomoo Milk, Energy Powder, berries | green sparkles rise; the HP number counts up |
| 2 cure status | Antidote, Burn Heal, Ice Heal, Awakening, Paralyze Heal, Full Heal, Lum Berry | a white ring bursts; the status aura goes |
| 3 heal and cure | Full Restore | both |
| 4 raise a stat | X Attack, X Defense, X Sp. Atk, X Sp. Def, X Speed, X Accuracy | gold arrows rise; the stat (or the Acc mark) pops |
| 5 Mist | Guard Spec. | a mist ring |
| 6 Focus Energy | Dire Hit | the game's Focus Energy sprite |
| 9 revive | Revive, Max Revive | gold stars |
| 10 restore PP | Ether, Max Ether, Elixir, Max Elixir | sparkles; the PP numbers count up |

Live detection (next): the tracker already reads the bag item by item for "Heals in Bag". Comparing those counts
between reads during a battle gives the item used. A trainer's own items (the "Usable Items" already read for the
trainer screen) dropping gives the opponent's Full Restore or X item, which plays on the opponent's card.

## 4. Numbers that move (and stay right)

- **PP.** PP is read from the game each snapshot. A drop of more than 1 in one turn is shown as the number ticking
  down with a red flash, labelled by its cause only when the player could know it: **Pressure** (the game announces it
  on switch-in, `B_MSG_SWITCHIN_PRESSURE`, so it is revealed), **Spite** (a move the player saw), **Grudge** (the foe
  used it). Grudge firing: the PP number cracks and drops to 0.
- **Stats with stages.** For your own Pokemon the tracker knows the exact stats, so the effective stat can be shown and
  counted up or down with a tween when a stage changes: stat x (2 + n) / 2 for +n, x 2 / (2 + n) for -n (attack,
  defense, specials, speed). The game's other multipliers that the player can see (Choice Band, Huge Power, Guts while
  statused, paralysis speed, the stat items) apply the same way. For the opponent the stats are unknown, so arrows
  only (and the player's +/- marks), never a made-up number.
- **Accuracy (built, 2026-10-10).** `tracker-gba/.../HitChance.kt` follows the game's code in its order and with its
  integer maths: `CanMoveSkipAccuracyCalc`, `GetTotalAccuracy`, `DoesOHKOMoveMissTarget` (`src/battle_util.c`) and
  `gAccuracyStageRatios`. Stages: your accuracy minus the target's evasion, -1 75%, -2 60%, -3 50%, -4 43%, -5 36%,
  -6 33%; +1 133%, +2 166%, +3 200% (a 100 move after three Sand Attacks is 50, not 40). Sure hits: Lock-On / Mind
  Reader, No Guard on either side, Stomp-type moves on a Minimized target, never-miss moves, Thunder in rain, Blizzard
  in hail. Abilities: Compound Eyes x1.3, Victory Star x1.1 (and an ally's), Hustle x0.8 on physical moves, Keen Eye /
  Mind's Eye / Illuminate / Unaware ignore evasion, Unaware on the target ignores your accuracy stage, Sand Veil and Snow
  Cloak x0.8 in their weather, Tangled Feet x0.5 while confused, Wonder Skin caps status moves at 50. Items: Wide Lens
  x1.1, Zoom Lens x1.2 moving second, BrightPowder x0.9, Micle Berry x1.2 (x1.4 with Ripen). Field:
  Thunder / Hurricane 50 in sun, Gravity x5/3, fog x0.6, Cloud Nine and Air Lock turn the weather off, Foresight and
  Miracle Eye ignore evasion. One-hit KO moves: accuracy plus the level gap, never against a higher level or Sturdy,
  Sheer Cold -10 from a non-Ice type. Heart & Soul keeps Gen 3's Toxic: it can miss even from a Poison type
  (`B_TOXIC_NEVER_MISS GEN_3`); the ROM's own flags say so. The flags come from the ROM (`gMovesInfo`, `gItemsInfo`
  hold effects), 9 tests (`HitChanceTest`, 3 of them read the comfort build). The Acc column counts to the new number
  with an arrow, and a sure hit reads "--", the card's own mark for a move that never misses.
- **Critical hits (built, same day).** `HitChance.crit`, from `CalcCritChanceStage` with the Gen 7 odds the build uses
  (`B_CRIT_CHANCE GEN_LATEST`): stage 0 is 1 in 24, +1 1 in 8, +2 1 in 2, +3 every hit. Focus Energy and Dire Hit +2,
  Dragon Cheer +1, high-crit moves (Slash, Razor Leaf) +1, Scope Lens +1, Leek on Farfetch'd / Sirfetch'd +2, Lucky
  Punch on Chansey +2, Super Luck +1. Always: Laser Focus, Frost Breath / Storm Throw, Merciless against a poisoned
  target. Never: Battle Armor / Shell Armor, Lucky Chant, and the first rival battle (KaizoCore's own rule). The card has
  no crit column, so for now the odds show in the sandbox caption; proposed for the card: a small crit mark by the sprite while raised (Focus Energy's ROM
  sprite already plays on Dire Hit).
- **Not done, by Blake's call (2026-10-10):** high roll / low roll on damage.
- **Move effectiveness with abilities.** The type chart alone is not the answer. `Tinted Lens` (`src/battle_util.c`)
  doubles damage when the type modifier is 0.5 or less: not very effective becomes neutral, x0.25 becomes x0.5. Others:
  Scrappy (Normal and Fighting hit Ghost), Levitate and Air Balloon (Ground immune), Wonder Guard, Thick Fat, Heatproof,
  Flash Fire, Volt / Water Absorb, Lightning Rod, Storm Drain, Motor Drive, Sap Sipper, Dry Skin, Filter / Solid Rock,
  Fluffy, Ice Scales. Shown only when the player can know the ability: yours always; the foe's only once revealed.
  **Some abilities never reveal.** Tinted Lens is the plain case: the game applies the x2 inside the damage modifier
  (`src/battle_util.c:7573`) with no pop-up and no `RecordAbilityBattle`, so the tracker never learns an opponent has
  it, and its card keeps the plain type chart ("not very effective") and plays no reveal. The only tell is a resisted
  hit landing as neutral, which also makes Calc Atk's estimate of that opponent's attacking stat come out about twice
  too high, since it works backwards from 0.5x. Tinted Lens counts on your own Pokemon only.

### How to keep 66 abilities and the move list accurate

1. **Take the rules from the game's source, not memory.** The Heart & Soul build is pokeemerald-expansion; the source
   is in WSL (`/root/hns/pokehns-expansion`). Each rule cites its function and line.
2. **One table per kind of rule** (accuracy, type modifier by ability, stat multiplier by ability or item, PP cost),
   limited to the abilities and moves the Kaizo pool can actually produce (the run's log and `HnsUprRules` list them).
3. **A golden test per entry**: a case built from the source (Tinted Lens + 0.5 → 1.0; Sand Attack x2 on a 100 move →
   60) so a wrong entry fails the build.
4. **The information rule**: nothing the player could not know mid-run (`tracker-never-reveals-log`). A foe's ability
   counts only after the game shows it; the log is never read for live battle info.

Viewers learn from it: a small caption under an event that names the mechanic ("Pressure: each move costs 2 PP",
"Tinted Lens: not very effective hits as neutral") for a few seconds, with an option to turn captions off.

## 5. Assets: the game's own, read from the ROM (built)

Blake's rule: use the ROM's art when we can. Nothing is shipped: the art is read out of the player's own ROM at
runtime, the same way the tracker already reads front pictures, trainer portraits and followers.

- **Where it is.** `tools/hns/fx_art.py` (runs in WSL on the build directory, like `layout.py`) takes each sprite's
  address and size from the ELF, and the sheet's width and height from the source PNG its INCBIN names. A sheet made
  of parts lists its parts in order; where two INCBINs sit behind an `#if`, the one whose file size matches the
  symbol is the one compiled. It writes `assets/hns/fx-art-kaizo.json` with the ROM's CRC, and `FxRomArt` uses it only
  on that ROM (09405F53). On any other build every effect falls back to its look drawn in code. For production the
  same symbols go into `layout.py`'s `SYMBOLS`, so they come with the layout and its checks.
- **Decoding.** `HnsPics.sheet` (smol or LZ77 tiles with the sprite's own palette), `HnsPics.itemIcon` (24x24, from
  `gItemsInfo[item].iconPic` and `iconPalette`, already in the layout), `HnsPics.itemBattleUsage`.
- **Used now** (26 sprites): embers (burn), poison and toxic bubbles, the shock (paralysis), ice crystals (freeze),
  the Z (sleep), the ducks (confusion, from the game's own confusion animation), red and pink hearts (Attract), the
  Substitute doll (Gen 3's pale green doll), the item bag, confetti and gold stars (badge), sparkles and green
  sparkles (heals, PP), music notes (Perish Song), the Protect shield, spikes, rain drops, hail, the sweat drop
  (Truant), anger marks, leech seeds, Focus Energy.
- **More in the ROM, worth wiring next:** the Light Screen and Reflect walls, sunlight, sand, Stealth Rock and Toxic
  Spikes, the Poke Ball (a catch), the Mega and Tera symbols, the lock-on reticle, the purple flame (Spite, Grudge),
  ghostly spirits (Curse, Destiny Bond), the thought bubble.
- **Item icons:** every item has its own 24x24 icon, so whatever the player or a trainer uses shows its real picture.
- **Drawn in code** only where the ROM has nothing: the padlock (Disable), stat arrows, number pops, rays, banners.
- Badge art (all 16) is already in the jar (`PcAssets.badge`); the gym leader's portrait can come from the ROM
  (`HnsPics.trainer`) for the badge banner.
- Sound: the tracker window has none. If wanted later, short CC0 cues played by the jar with a volume option.
- Not allowed: shipping ripped sprites. Reading the player's ROM is the line, as it is for followers.

## 6. The scenario sandbox (built)

Since 2026-10-10 a scenario can also set the weather and field (`sun`, `sand`, `fog`, `gravity`, `rain`, `hail`, each
with `off`), reveal an ability or item (`foe ability Keen Eye`, `foe item Bright Powder by Frisk`), and set the hit-chance
states (`own foresight`, `own lockon`, `foe minimize`, `own focus energy`, `own micle`). Under the card the GIF prints
the sum behind each odd that moved ("FIRE BLAST: 85 -> 49  Stages -1, Sand Veil x0.8, Wide Lens x1.1, Bright Powder
x0.9") and the crit odds when raised, so the maths can be checked by eye. `sandbox/scenarios/abilities-items.txt`
plays Wide Lens, a sandstorm that changes nothing (Sand Veil cannot be known), Frisk finding the Bright Powder, Sand
Attack, Dire Hit and Lock-On.

`./gradlew :desktop:sandbox -Pscenario=sandbox/scenarios/showcase.txt` plays a scenario through the **real card
renderer** and the effects layer into `desktop/build/sandbox/<name>.gif`, plus a contact sheet and the last frame.
Species, moves (power, accuracy, PP, type, category), abilities and items are the game's own, read from the build
KaizoCore PC makes from the official 2.0.6, so a card looks as it would in a run. Blake describes a situation, it is
written as a scenario, and the tracker shows it.

```
lead Quilava lv 24 hp 70/71 ability Blaze item Charcoal moves Ember, Quick Attack, Smokescreen, Swift
foe Miltank lv 25 moves Rollout, Milk Drink, Attract, Stomp      (wild foe ... for a wild battle)
view own | view foe
foe status PAR | own status none | own status CONFUSE | own status ATTRACT
own stage ACC -1 | foe stage ATK +2
own damage 28 | foe heal 40 | own hp 12
own use Full Restore | own use X Accuracy | foe use Hyper Potion
own substitute on | own substitute off
foe taunt 3 | own disable Swift 4 | own encore Tackle | foe leech seed | own perish 3 | rain | hail off
levelup | badge 3 | faint foe
wait 1.5
```

An item step plays its own icon and its use, and changes the card the way the item would (HP, status, the stat an X
item raises). Next for the sandbox: a demo mode that plays a scenario in the real tracker window (as `--panel-demo`
does for still cards), and steps for accuracy and PP once those numbers move (section 4).

## 7. For the audience

- Everything plays in the tracker window, which OBS already captures: no extra overlay.
- Moments sized for chat: big events (badge, league, shiny, clutch, crit) take the card for 2 to 4 seconds; small
  ones (stat pop, hit) stay inside their row.
- Captions that teach the mechanic (above).
- Chat hooks through the existing Twitch connection (Stream Connect): `!fav` already exists. Possible: a chat
  prediction on "survives this turn", a channel-point "hype" that plays confetti, a counter of KOs this run. Never
  anything that changes the game or gives the player information they would not have.
- A streamer toggle for each group (statuses, numbers, celebrations, captions).

## 8. Recommendation and order

1. ~~Scenario sandbox~~ built (section 6). Keep adding steps as effects land, so each can be seen without playing.
2. Wire the effects into the live window behind an option (events from `Session` after each read, `FxLayer` drawn in
   `TrackerWindow` after the card, timer only while active). Measure the lag at Blake's settings, the way the
   2026-10-09 lag work was measured. Volatile effects and substitutes come from `battleDetails`, already read.
3. Item detection from bag counts and trainer items (section 4a), so items react live.
4. Effective accuracy in the Acc column and your own effective stats, from the game's tables (section 4), with a
   golden test per rule, shown in the sandbox first.
5. PP reactions (Pressure, Spite, Grudge), labelled only when revealed.
6. Ability-aware effectiveness (Tinted Lens first), the same table-and-test method.
7. Production art path: the fx symbols into `layout.py`; more ROM sprites (walls, sun, sand, Stealth Rock, Poke Ball).
8. Captions that name the mechanic, and chat hooks.

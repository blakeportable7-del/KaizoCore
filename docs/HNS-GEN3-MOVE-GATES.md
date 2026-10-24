# Heart & Soul on Nat. Dex Emerald 1.2.1 move rules: what config cannot reach

Goal: the Heart & Soul (pokeemerald-expansion) Kaizo build plays Nat. Dex Emerald 1.2.1's battle mechanics. The B_
settings in `include/config/battle.h` are Gen 3, with `B_UPDATED_TYPE_MATCHUPS` and `B_UPDATED_MOVE_TYPES` at GEN_6. This
file lists every move data row and every move behavior in the Kaizo pool that still differs from Nat. Dex with those
settings, and gives an exact `KAIZOCORE_GEN3_BATTLE` fix for each. Report and proposal only: no source file was edited.

Pool: moves 1 to 354 (Struggle included, it is not rolled but it is used) plus Nat. Dex's 11 working newer moves (Air
Slash, Shadow Sneak, Leaf Storm, Struggle Bug, Disarming Voice, Draining Kiss, Play Rough, Fairy Wind, Moonblast, Play
Nice, Dazzling Gleam). 365 moves.

Source read: `/root/hns/src-gen3mech`, branch `kaizocore-gen3-battle`, commit `1f59cad9` ("KaizoCore: Gen 3 abilities,
Nat. Dex Emerald's moves and Emerald's rules by default"). Every file:line below is at that commit; apply the hunks by
their context if the file has moved on. `KAIZOCORE_GEN3_BATTLE` is TRUE in `include/config/battle.h` and is already used
in every file touched below (moves_info.h, battle_util.c, battle_move_resolution.c, battle_script_commands.c,
battle_hold_effects.c, battle_scripts_1.s), so no new `#include` is needed. `include/battle.h` needs none either (the
hunk there adds a field unconditionally).

Evidence key: **ND** = Nat. Dex Emerald 1.2.1 ROM (`emerald-natdex-121.gba`), read for this file; section letters (A18,
F3...) refer to `docs/HACK-RULES-NATDEX.md`. Nat. Dex keeps vanilla Emerald's battle code for moves 1 to 354 (F3), so a
"Gen 3" behavior below is Nat. Dex's unless stated.

## How the comparison was made

- **Heart & Soul rows**: `src/data/moves_info.h` was compiled for real, not read by eye. A copy of the tree at
  `1f59cad9` was preprocessed with the project's flags (`arm-none-eabi-cpp -iquote include -DMODERN=1 -DPOKEMON_HNS`),
  then compiled natively with a dumper that prints every field of `gMovesInfo[]` (power, type, accuracy, PP, target,
  priority, category, effect, every additional effect with its chance and self flag, contact, protect, Magic Coat,
  Snatch, King's Rock, sound, Mirror Move, Sleep Talk, Encore, Assist, Mimic and Metronome bans, substitute, underground,
  underwater, airborne, minimize, thaw, crit stage, strike count, argument). Configs as the commit has them: move data,
  flags and split GEN_3, move types and type matchups GEN_6, and the commit's other B_ settings (nearly all GEN_3).
- **Nat. Dex rows**: read straight from the ROM, `gBattleMoves` at 0x0831C898, 12 bytes per move: +1 power, +2 type,
  +3 accuracy, +4 PP, +5 secondary chance, +6 target, +7 priority, +8 flags (1 contact, 2 Protect, 4 Magic Coat,
  8 Snatch, 0x10 Mirror Move, 0x20 King's Rock), effect u16 at +10. Sound moves from `sSoundMovesTable` 0x0838F098.
- **Gen 3 behavior** was read from Nat. Dex's battle scripts (`gBattleScriptsForMoveEffects` 0x082DC874, walked
  command by command) and disassembled commands (`gBattleScriptingCommandsTable` 0x0838F4D0), and checked against the
  Heart & Soul code path for each move.
- Result: every pool move's power, type, accuracy, PP, priority, effect and secondary chance now matches Nat. Dex
  (the five HnS power/PP edits and the Ghost/Dark category were already gated in `1f59cad9`). What is left is flags,
  ban lists and code.

## Summary

- **Data mismatches still open: 17** (Section 1, rows D1 to D17). 16 are flag or ban-list rows in `moves_info.h`; one
  (D17) is the King's Rock move set, 69 moves, which is fixed in code because Heart & Soul has no positive King's Rock
  flag. 7 more data rows were found and are already fixed in `1f59cad9` (listed for the record).
- **Behavior gaps still open: 14** (Section 2, rows B1 to B14). 13 have a fix; B14 (where a Gen 3 "double the damage"
  became "double the power") is rounding only, belongs with the damage formula, and gets no per-move fix.
- **The two biggest**: Future Sight and Doom Desire (B1, B2: setting `B_UPDATED_MOVE_TYPES` to GEN_6 switched off the
  expansion's own typeless branch, so Future Sight no longer hits Dark types and gets STAB and the type chart; and the
  damage is computed when it lands, with a crit roll, not when it is used), and Jump Kick / Hi Jump Kick crash damage
  (B4: the expansion's GEN_3 branch gives at least half the TARGET's max HP and crashes into Ghosts; Gen 3 gives half the
  missed damage, capped there, and no crash on an immune target).
- **Fixes needed: 18 hunks** (Section 3, H1 to H18): 1 in `moves_info.h` (16 field edits), 1 in
  `battle_scripts_1.s`, 1 in `include/battle.h`, 9 in `battle_util.c`, 3 in `battle_move_resolution.c`, 2 in
  `battle_script_commands.c`, 1 in `battle_hold_effects.c` (H18 is optional, Weather Ball rounding).

## Section 1: data mismatches

Field names are Heart & Soul's. "Gen 3" in the Nat. Dex column means vanilla Emerald code that Nat. Dex keeps; the ROM
address is where it was read.

### Open

| # | Move | field | Heart & Soul | Nat. Dex | fix |
|---|---|---|---|---|---|
| D1 | Kinesis | magicCoatAffected | FALSE (`B_UPDATED_MOVE_FLAGS >= GEN_4`) | TRUE: flags 0x16, Nat. Dex Emerald added the bit (F3) | H1: `KAIZOCORE_GEN3_BATTLE \|\| ...` |
| D2 | Mimic | mirrorMoveBanned | FALSE (not set) | Mirror Move cannot copy it: flags 0x00 | H1: `.mirrorMoveBanned = KAIZOCORE_GEN3_BATTLE` |
| D3 | Mimic | ignoresSubstitute | TRUE | fails against a substitute: script 0x082DD6B5 runs `jumpifstatus2 target, STATUS2_SUBSTITUTE` first | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D4 | Mimic | sleepTalkBanned | TRUE | Sleep Talk can call it: the Gen 3 ban is only Sleep Talk, Assist, Mirror Move, Metronome (0x08055E58), Focus Punch, Uproar and the two-turn effects Skull Bash, Razor Wind, Sky Attack, Solar Beam, Fly/Dig/Dive/Bounce, Bide (0x08055E1C) | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D5 | Mimic | encoreBanned | TRUE | Encore bans only Struggle, Encore, Mirror Move (Cmd_trysetencore 0x08055872 to 0x0805587C). No visible effect: a Mimic that worked is no longer in the moveset, so Encore fails anyway | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D6 | Play Nice | ignoresSubstitute | TRUE | fails against a substitute: effect 18 (ATTACK_DOWN) runs the Gen 3 stat-drop script, which checks the substitute (0x082DCEF8) | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D7 | Fissure | damagesUnderground | TRUE (hits a Pokemon using Dig) | misses it: the OHKO script uses `accuracycheck NO_ACC_CALC_CHECK_LOCK_ON` and never sets the underground hit marker (A18) | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D8 | Nature Power | sleepTalkBanned | TRUE | Sleep Talk can call it (list in D4) | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D9 | Nature Power | mimicBanned | TRUE | Mimic can copy it: Gen 3 Mimic bans only Metronome, Struggle, Sketch, Mimic (list 0x0838FBA4 up to 0xFFFE) and a transformed user | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D10 | Transform | mimicBanned | TRUE | Mimic can copy it (D9 list) | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D11 | Transform | encoreBanned | TRUE | allowed (D5 list); no visible effect, Transform leaves the moveset | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D12 | Sketch | encoreBanned | TRUE | allowed (D5 list); no visible effect, Sketch leaves the moveset | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D13 | Assist | encoreBanned | TRUE | allowed (D5 list): an encored Assist keeps calling party moves | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D14 | Assist | mimicBanned | TRUE | Mimic can copy it (D9 list) | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D15 | Mirror Move | mimicBanned | TRUE | Mimic can copy it (D9 list) | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D16 | Sleep Talk | mimicBanned | TRUE | Mimic can copy it (D9 list) | H1: `!KAIZOCORE_GEN3_BATTLE` |
| D17 | 69 damaging moves | King's Rock | Heart & Soul applies King's Rock to every damaging move except 24 with `ignoresKingsRock` and every move with its own flinch chance | Gen 3 applies it only to the 140 moves with flag 0x20. **64 moves gain King's Rock in HnS**: Fire/Ice/Thunder Punch, Guillotine, Horn Drill, Body Slam, Poison Sting, Acid, Ember, Flamethrower, Ice Beam, Blizzard, Psybeam, Bubble Beam, Aurora Beam, Thunder Shock, Thunderbolt, Thunder, Fissure, Confusion, Psychic, Lick, Smog, Sludge, Fire Blast, Constrict, Dream Eater, Bubble, Dizzy Punch, Tri Attack, Flame Wheel, Powder Snow, Sludge Bomb, Mud-Slap, Octazooka, Zap Cannon, Icy Wind, Spark, Sacred Fire, Dynamic Punch, Iron Tail, Metal Claw, Crunch, Mirror Coat, Ancient Power, Shadow Ball, Future Sight, Rock Smash, Heat Wave, Facade, Focus Punch, Smelling Salts, Superpower, Knock Off, Secret Power, Luster Purge, Mist Ball, Blaze Kick, Hyper Voice, Poison Fang, Crush Claw, Rock Tomb, Water Spout, Sheer Cold, Covet, Doom Desire, Struggle Bug. **5 lose it**: Rolling Kick, Sky Attack, Snore, Twister, Air Slash (Gen 3 rolls King's Rock on top of their own flinch) | H17 (code, list read from the ROM) |

### Already fixed in `1f59cad9` (found by the same comparison)

| Move | field | Heart & Soul | Nat. Dex | where |
|---|---|---|---|---|
| Poison Sting | power | 30 | 15 | moves_info.h:1166 |
| Mega Drain | PP | 25 | 10 | moves_info.h:2016 |
| Leech Life | power, PP | 60, 10 | 20, 15 | moves_info.h:3846, 3849 |
| Giga Drain | PP | 25 | 5 | moves_info.h:5502 |
| every Ghost move | category by type | special | physical | types_info.h:223 |
| every Dark move | category by type | physical | special | types_info.h:432 |

### Differences that change nothing (no fix)

- **Target**: Haze, Sandstorm, Rain Dance, Sunny Day, Hail, Mud Sport, Water Sport read FIELD, Perish Song ALL_BATTLERS;
  Nat. Dex says USER (0x10). Same effect; their Pressure accounting is already handled in `CancelerPPDeduction`
  (battle_move_resolution.c:949 to 967, `1f59cad9`).
- **Protect flag** on self or field moves (Haze, Milk Drink, Hail, Wish, Imprison, Grudge): Protect only stops moves
  aimed at the protected Pokemon.
- **Mirror Move flag** on self moves (Soft-Boiled, Assist, Grudge): Mirror Move only copies moves that hit its user.
- **Sound flag** on Perish Song and Heal Bell (TRUE in HnS, not in Nat. Dex's sound list): Gen 3 gives Soundproof the
  same immunity through special code in Cmd_trysetperishsong and Cmd_healpartystatus; `B_CHECK_USER_FAILURE` and
  `B_HEAL_BELL_SOUNDPROOF`, both GEN_3 in the commit, cover the user's own Soundproof. Same result.
- **Soft-Boiled accuracy** 0 vs 100, **Nature Power power** 1 vs 0: display and internal only.
- **Spit Up power** 1 vs 100: HnS computes it in code; see B3.
- **Accuracy 0 rows** (Mimic, Pain Split, Role Play and the rest of A21): HnS also shows 0, same as Nat. Dex.

## Section 2: behavior gaps

| # | Move | Heart & Soul behavior | Nat. Dex / vanilla behavior | gated? | fix |
|---|---|---|---|---|---|
| B1 | Future Sight, Doom Desire (and Beat Up) | Typed: Future Sight is Psychic (no effect on Dark, STAB, type chart, Wonder Guard), Doom Desire Steel | Typeless when it lands: the hit script has no typecalc, so it hits Dark types and gets no STAB or chart | Was gated by `B_UPDATED_MOVE_TYPES < GEN_5` in `GetBattleMoveType` (battle_util.c:9962); GEN_6 switches it off | H3 (keep the typeless branch under the gate) and H4 (split them by their listed type, so Doom Desire stays physical as Steel) |
| B2 | Future Sight, Doom Desire | Damage computed when it lands, from the attacker on the field (or the party mon's raw stats if it left), can crit | Damage fixed when the move is used (Cmd_trysetfutureattack 0x080574A8 calls CalculateBaseDamage 0x080694C8 with both battlers' current stats and stages, Helping Hand x1.5), no crit; only the 85 to 100% roll is rolled when it lands | No | H5, H6, H7, H8 |
| B3 | Spit Up | Power 100 x stockpile, normal hit: crit roll and random roll | Cmd_stockpiletobasedamage: base damage at power 100, times the stockpile count, no critcalc, then `adjustsetdamage` (no random roll) | No | H8, H9, H10 |
| B4 | Jump Kick, Hi Jump Kick | On a miss: crash = max(moveDamage, target's max HP / 2); no damagecalc runs on a miss, so in practice half the target's max HP; it also crashes when the target is immune (Ghost) | On a miss: damage the move would have done (damagecalc, typecalc, random roll, no crit) / 2, at least 1, at most target max HP / 2 (manipulatedamage case 1, 0x08053278 to 0x080532A0); no crash at all when the target is immune (script 0x082DD34E, `jumpifbyte` on MOVE_RESULT_DOESNT_AFFECT_FOE) | Config branch `B_RECOIL_IF_MISS_DMG >= GEN_3` exists but is wrong (battle_move_resolution.c:2954 to 2960) | H11 |
| B5 | Double-Edge, Volt Tackle | Recoil = damage x 33 / 100 | MOVE_EFFECT_RECOIL_33: damage / 3 (99 damage: HnS 32, Gen 3 33) | No | H11 |
| B6 | Sleep Talk | Ignores a Choice item when it picks a move (`~(MOVE_LIMITATION_PP \| MOVE_LIMITATION_CHOICE_ITEM)`) | CheckMoveLimitations called with check 0xFD (everything but PP, 0x08055F64), and its Choice Band test (hold effect 29, 0x08041B40) applies | No | H12 |
| B7 | Encore | Lasts 3 turns (B_ENCORE_TIMER 4, or 3 if the target already moved) | `(Random() & 3) + 3`, so 3 to 6, counted down at end of turn the same way (0x080558C2 to 0x080558E0) | No (B_ENCORE_TIMER is a number, not a gen) | H13 |
| B8 | Guillotine, Horn Drill, Fissure, Sheer Cold | Hits on `RandomPercentage(odds)` | Hits only if `Random() % 100 + 1 < odds`, one point less (A18) | No | H14 |
| B9 | King's Rock | See D17 | See D17 | No | H17 |
| B10 | Beat Up | Gen 3 formula (B_BEAT_UP GEN_3) but no random roll, and the crit message is wiped though a crit doubles the damage | Each hit rolls 85 to 100% (`adjustnormaldamage`) and shows its crit | Config path incomplete | H15 |
| B11 | Metronome | B_METRONOME_MOVES GEN_3: moves 1 to 354 only | `(Random() & 0x3FF) + 1`, rerolled above 847 (0x34F), on placeholder effect 214 and on the banned list (0x0805539C to 0x080553F2): the 354 Gen 3 moves plus the 11 newer moves, each 1 in 365 | No | H16 |
| B12 | Sketch | Works through a substitute (HnS script has no substitute check) | Fails against a substitute (script 0x082DD84E, `jumpifstatus2 target, STATUS2_SUBSTITUTE` before copymovepermanently) | No | H2 |
| B13 | Weather Ball | Power 50 to 100 in weather | Power stays 50 and the base damage is doubled (`gBattleScripting.dmgMultiplier = 2`, C3), so (x + 2) x 2 instead of about 2x + 2: a few HP more in Gen 3 | No | H18 (optional) |
| B14 | Facade, Revenge, Pursuit on a switch, Smelling Salts, Stomp/Needle Arm/Astonish/Extrasensory on Minimize, Gust/Twister on Fly, Earthquake/Magnitude on Dig, Surf/Whirlpool on Dive, Charge, Helping Hand | x2 (x1.5) on the power or as a 4096-based modifier after the random roll | x2 (x1.5) on the base damage before STAB, type and random | No | None per move: rounding only, a few HP; it belongs with the damage formula order (D5, D6 in HACK-RULES-NATDEX.md) |

### Checked and matching (or covered by a B_ setting the commit already has at GEN_3)

Struggle (typeless: no STAB at battle_util.c:7431, no chart at 8465/8485; recoil 1/4 of the damage dealt), Hidden Power
(power by `B_HIDDEN_POWER_DMG`, type formula skips Normal and ???, category by type, Counter by
`B_HIDDEN_POWER_COUNTER`), Curse (non-Ghost: Speed -1, Attack +1, Defense +1, fails only when Attack and Defense are
both +6, same script; Ghost: half max HP, works through a substitute in both, type Ghost by `B_UPDATED_MOVE_TYPES`),
Thief and Covet (`B_TRAINERS_KNOCK_OFF_ITEMS` FALSE, `B_RETURN_STOLEN_NPC_ITEMS`, `B_STEAL_WILD_ITEMS`), Knock Off
(`B_KNOCK_OFF_DMG`, `B_KNOCK_OFF_REMOVAL`), Rapid Spin (frees after any hit, substitute included, as Gen 3;
`B_SPEED_BUFFING_RAPID_SPIN`), Spikes (3 layers, 1/8, 1/6, 1/4), Sleep Talk's ban list apart from D4/D8 and B6, Pain
Split (fails against a substitute in both), Present (102/76/26/52 out of 256, heal 1/4), Magnitude (5/10/20/30/20/10/5%),
Counter and Mirror Coat (2x the HP lost, `B_COUNTER_*`), Bide (2 turns, 2x, Ghost immune), Explosion
(`B_EXPLOSION_DEFENSE`), Substitute (1/4 max HP), Baton Pass (`B_BATON_PASS_TRAPPING`), Torment and Taunt, Disable,
Encore apart from B7, Attract, Foresight, Psych Up, Role Play and Skill Swap (all work through a substitute in both),
Swagger and Flatter (blocked by a substitute in both; `jumpifconfusedandstatmaxed` rule), Facade (`B_BURN_FACADE_DMG`;
rounding in B14), Focus Punch (`B_FOCUS_PUNCH_FAILURE`), Uproar (`B_UPROAR_TURNS`), Stockpile and Swallow (1/4, 1/2,
full; fails at 3 or at 0; full HP resets the count), Hail (1/16, 5 turns), Teeter Dance, Snatch (`B_SNATCH`), Magic Coat
(D1 aside), Refresh (poison, paralysis, burn), Grudge, Imprison (`B_IMPRISON`), Secret Power, Camouflage and Nature
Power (`B_SECRET_POWER_EFFECT`, `B_CAMOUFLAGE_TYPES`, `B_NATURE_POWER_MOVES`), Bulk Up, Calm Mind, Dragon Dance,
Recover/Soft-Boiled/Milk Drink/Slack Off (1/2, fail at full HP), Morning Sun/Synthesis/Moonlight (1/2, 2/3 in sun, 1/4
in other weather), Rest (sleep counter 3, fails when asleep, at full HP, under Uproar, with Insomnia/Vital Spirit),
sleep length (`B_SLEEP_TURNS`), freeze (20% thaw, Flame Wheel and Sacred Fire thaw the user), multi-hit
(`B_MULTI_HIT_CHANCE`: 3/8, 3/8, 1/8, 1/8), Fury Cutter (10 to 160), Rollout (doubling, Defense Curl x2), Flail and
Reversal (same table), Return and Frustration, Low Kick (same weight table), Eruption and Water Spout, Triple Kick
(10/20/30), Psywave (`B_PSYWAVE_DMG`), Mimic (copied move gets 5 PP), Transform (5 PP each), Mirror Move, Assist (same
20-move ban list), Metronome's ban list (same 18 moves), Leech Seed, Nightmare, Lock-On, Mean Look/Block/Spider Web,
Yawn, Trick (all blocked by a substitute in both), Protect/Detect/Endure (fail when moving last;
`B_PROTECT_FAILURE_RATE`), secondary effect chances (every row matches).

## Section 3: patch hunks

Line numbers are at `1f59cad9`. Each hunk shows the lines as they are, then as they should be.

### H1. `src/data/moves_info.h`: flags and ban lists (D1 to D16)

Kinesis, line 3661:

```c
        .magicCoatAffected = B_UPDATED_MOVE_FLAGS >= GEN_4,
```
```c
        .magicCoatAffected = KAIZOCORE_GEN3_BATTLE || B_UPDATED_MOVE_FLAGS >= GEN_4, // KaizoCore: Nat. Dex Emerald gives Kinesis the Magic Coat flag
```

Mimic, lines 2800 to 2807:

```c
        .ignoresSubstitute = TRUE,
        .mimicBanned = TRUE,
        .metronomeBanned = B_UPDATED_MOVE_FLAGS >= GEN_2,
        .copycatBanned = TRUE,
        .sleepTalkBanned = TRUE,
        .instructBanned = TRUE,
        .encoreBanned = TRUE,
        .assistBanned = TRUE,
```
```c
        .ignoresSubstitute = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Mimic fails against a substitute
        .mirrorMoveBanned = KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Mimic has no Mirror Move flag
        .mimicBanned = TRUE,
        .metronomeBanned = B_UPDATED_MOVE_FLAGS >= GEN_2,
        .copycatBanned = TRUE,
        .sleepTalkBanned = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Sleep Talk can call Mimic
        .instructBanned = TRUE,
        .encoreBanned = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Encore bans only Struggle, Encore and Mirror Move
        .assistBanned = TRUE,
```

Fissure, line 2487:

```c
        .damagesUnderground = TRUE,
```
```c
        .damagesUnderground = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Fissure misses a Pokemon using Dig
```

Play Nice, line 15248:

```c
        .ignoresSubstitute = TRUE,
```
```c
        .ignoresSubstitute = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Nat. Dex runs the Gen 3 stat-drop script, which a substitute blocks
```

Nature Power, lines 7248 and 7252:

```c
        .sleepTalkBanned = TRUE,
```
```c
        .sleepTalkBanned = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Sleep Talk can call Nature Power
```
```c
        .mimicBanned = TRUE,
```
```c
        .mimicBanned = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Mimic bans only Metronome, Struggle, Sketch and Mimic
```

Transform, lines 3939 and 3943:

```c
        .mimicBanned = TRUE,
```
```c
        .mimicBanned = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Mimic bans only Metronome, Struggle, Sketch and Mimic
```
```c
        .encoreBanned = TRUE,
```
```c
        .encoreBanned = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Encore bans only Struggle, Encore and Mirror Move
```

Sketch, line 4562:

```c
        .encoreBanned = TRUE,
```
```c
        .encoreBanned = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Encore bans only Struggle, Encore and Mirror Move
```

Assist, lines 7441 and 7443:

```c
        .encoreBanned = TRUE,
        .assistBanned = TRUE,
        .mimicBanned = TRUE,
```
```c
        .encoreBanned = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Encore bans only Struggle, Encore and Mirror Move
        .assistBanned = TRUE,
        .mimicBanned = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Mimic bans only Metronome, Struggle, Sketch and Mimic
```

Mirror Move, line 3269, and Sleep Talk, line 5830 (same line in both rows):

```c
        .mimicBanned = TRUE,
```
```c
        .mimicBanned = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Mimic bans only Metronome, Struggle, Sketch and Mimic
```

`.mimicBanned = TRUE,` and `.encoreBanned = TRUE,` occur in many rows: edit them inside the named row only.

### H2. `data/battle_scripts_1.s:3025`: Sketch fails against a substitute (B12)

```
BattleScript_EffectSketch::
	attackcanceler
	copymovepermanently BattleScript_ButItFailed
```
```
BattleScript_EffectSketch::
	attackcanceler
.if KAIZOCORE_GEN3_BATTLE == TRUE @ KaizoCore: Gen 3 Sketch fails against a substitute
	jumpifsubstituteblocks BattleScript_ButItFailed
.endif
	copymovepermanently BattleScript_ButItFailed
```

Sketch's `ignoresSubstitute` is `B_UPDATED_MOVE_FLAGS >= GEN_5`, FALSE here, so the check blocks.

### H3. `src/battle_util.c:9962`, `GetBattleMoveType`: Future Sight and Beat Up stay typeless (B1)

```c
        if (B_UPDATED_MOVE_TYPES < GEN_5
         && (effect == EFFECT_BEAT_UP || effect == EFFECT_FUTURE_SIGHT))
          return TYPE_MYSTERY;
```
```c
        // KaizoCore: B_UPDATED_MOVE_TYPES is GEN_6 for Fairy and Curse, but Gen 3 Future Sight, Doom Desire and Beat Up
        // still hit typeless (no typecalc in their scripts).
        if ((B_UPDATED_MOVE_TYPES < GEN_5 || KAIZOCORE_GEN3_BATTLE)
         && (effect == EFFECT_BEAT_UP || effect == EFFECT_FUTURE_SIGHT))
          return TYPE_MYSTERY;
```

### H4. `src/battle_util.c:9212`, `GetBattleMoveCategory`: split them by their listed type (B1)

Without this, H3 makes them TYPE_MYSTERY's category (special), and Doom Desire would use Sp. Atk; Gen 3 splits by
`gBattleMoves[move].type`, so Doom Desire is physical (Steel), Future Sight special, Beat Up special (Dark, which also
decides Counter or Mirror Coat).

```c
        if (IsBattleMoveStatus(move))
            return DAMAGE_CATEGORY_STATUS;
        return gTypesInfo[GetBattleMoveType(move)].damageCategory;
```
```c
        if (IsBattleMoveStatus(move))
            return DAMAGE_CATEGORY_STATUS;
        if (KAIZOCORE_GEN3_BATTLE && (GetMoveEffect(move) == EFFECT_FUTURE_SIGHT || GetMoveEffect(move) == EFFECT_BEAT_UP))
            return gTypesInfo[GetMoveType(move)].damageCategory; // KaizoCore: Gen 3 splits these by their listed type
        return gTypesInfo[GetBattleMoveType(move)].damageCategory;
```

### H5. `include/battle.h:474`: room for the Gen 3 Future Sight damage (B2)

```c
struct FutureSight
{
    u16 move;
    u16 counter:10;
    u16 battlerIndex:3;
    u16 partyIndex:3;
};
```
```c
struct FutureSight
{
    u16 move;
    u16 counter:10;
    u16 battlerIndex:3;
    u16 partyIndex:3;
    u16 gen3Damage; // KaizoCore: Gen 3 fixes Future Sight's damage when the move is used (KAIZOCORE_GEN3_BATTLE)
};
```

### H6. `src/battle_script_commands.c:9591`, `Cmd_setfutureattack`: fix the damage when the move is used (B2)

```c
    gBattleStruct->futureSight[gBattlerTarget].partyIndex = gBattlerPartyIndexes[gBattlerAttacker];
    gBattleStruct->futureSight[gBattlerTarget].counter = 3;

    if (gCurrentMove == MOVE_DOOM_DESIRE)
```
```c
    gBattleStruct->futureSight[gBattlerTarget].partyIndex = gBattlerPartyIndexes[gBattlerAttacker];
    gBattleStruct->futureSight[gBattlerTarget].counter = 3;
#if KAIZOCORE_GEN3_BATTLE
    {
        // KaizoCore: Gen 3 Cmd_trysetfutureattack: the base damage now, from both battlers' current stats and stages, no
        // critical hit; typeless (H3), so no STAB or type chart. The random roll comes when it lands (H7).
        struct BattleContext ctx = {0};
        ctx.battlerAtk = gBattlerAttacker;
        ctx.battlerDef = gBattlerTarget;
        ctx.move = gCurrentMove;
        ctx.chosenMove = gCurrentMove;
        ctx.moveType = GetBattleMoveType(gCurrentMove);
        ctx.weather = GetWeather();
        ctx.fieldStatuses = gFieldStatuses;
        ctx.abilityAtk = GetBattlerAbility(gBattlerAttacker);
        ctx.abilityDef = GetBattlerAbility(gBattlerTarget);
        ctx.holdEffectAtk = GetBattlerHoldEffect(gBattlerAttacker);
        ctx.holdEffectDef = GetBattlerHoldEffect(gBattlerTarget);
        ctx.typeEffectivenessModifier = UQ_4_12(1.0);
        ctx.isCrit = FALSE;
        ctx.randomFactor = FALSE;
        gBattleStruct->futureSight[gBattlerTarget].gen3Damage = min(CalculateMoveDamageVars(&ctx), 0xFFFF);
    }
#endif

    if (gCurrentMove == MOVE_DOOM_DESIRE)
```

`GetWeather()` is the static helper at battle_script_commands.c:1303, above this function. With `randomFactor` FALSE,
`DoMoveDamageCalcVars` returns the base damage with spread, weather and crit (x1) applied and stops before the
post-roll modifiers.

### H7. `src/battle_util.c:8256`, `CalculateMoveDamage`: Future Sight lands with the stored damage (B2)

```c
    ctx->holdEffectAtk = GetBattlerHoldEffect(ctx->battlerAtk);
    ctx->holdEffectDef = GetBattlerHoldEffect(ctx->battlerDef);

    ctx->typeEffectivenessModifier = CalcTypeEffectivenessMultiplier(ctx);
```
```c
    ctx->holdEffectAtk = GetBattlerHoldEffect(ctx->battlerAtk);
    ctx->holdEffectDef = GetBattlerHoldEffect(ctx->battlerDef);

#if KAIZOCORE_GEN3_BATTLE
    if (GetMoveEffect(ctx->move) == EFFECT_FUTURE_SIGHT && !ctx->aiCalc)
    {
        // KaizoCore: Gen 3 BattleScript_MonTookFutureAttack: the damage fixed by Cmd_setfutureattack, then only the
        // 85 to 100% roll (adjustnormaldamage); no critical hit, no STAB, no type chart.
        ctx->typeEffectivenessModifier = UQ_4_12(1.0);
        ctx->isCrit = FALSE;
        damage = gBattleStruct->futureSight[ctx->battlerDef].gen3Damage;
        if (ctx->randomFactor)
            damage = damage * (DMG_ROLL_PERCENT_HI - RandomUniform(RNG_DAMAGE_MODIFIER, 0, DMG_ROLL_PERCENT_HI - DMG_ROLL_PERCENT_LO)) / 100;
        if (damage == 0)
            damage = 1;
        return GetAdjustedDamage(ctx, damage);
    }
#endif

    ctx->typeEffectivenessModifier = CalcTypeEffectivenessMultiplier(ctx);
```

`futureSight[target]` still holds the entry when it lands (HandleEndTurnFutureSight only counts it down).

### H8. `src/battle_util.c:8151`, `IsCriticalHit`: no crit for Spit Up and Future Sight (B2, B3)

```c
static bool32 IsCriticalHit(struct BattleContext *ctx)
{
    if (KaizoCore_IsFirstRivalBattle())
        return FALSE;
```
```c
static bool32 IsCriticalHit(struct BattleContext *ctx)
{
    if (KaizoCore_IsFirstRivalBattle())
        return FALSE;

    // KaizoCore: Gen 3 Spit Up (Cmd_stockpiletobasedamage) and Future Sight / Doom Desire never run critcalc
    if (KAIZOCORE_GEN3_BATTLE
     && (GetMoveEffect(ctx->move) == EFFECT_SPIT_UP || GetMoveEffect(ctx->move) == EFFECT_FUTURE_SIGHT))
        return FALSE;
```

### H9. `src/battle_util.c:6392`, `CalcMoveBasePower`: Spit Up keeps power 100 (B3)

```c
    case EFFECT_SPIT_UP:
        basePower = 100 * gBattleMons[battlerAtk].volatiles.stockpileCounter;
        break;
```
```c
    case EFFECT_SPIT_UP:
        // KaizoCore: Gen 3 computes the damage at power 100 and multiplies the damage by the count (H10)
        basePower = KAIZOCORE_GEN3_BATTLE ? 100 : 100 * gBattleMons[battlerAtk].volatiles.stockpileCounter;
        break;
```

### H10. `src/battle_util.c:7780` and `7787`, `DoMoveDamageCalcVars`: Spit Up's count and no roll (B3)

```c
    dmg = CalculateBaseDamage(gBattleMovePower, userFinalAttack, gBattleMons[ctx->battlerAtk].level, targetFinalDefense);
    DAMAGE_APPLY_MODIFIER(GetTargetDamageModifier(ctx));
```
```c
    dmg = CalculateBaseDamage(gBattleMovePower, userFinalAttack, gBattleMons[ctx->battlerAtk].level, targetFinalDefense);
    if (KAIZOCORE_GEN3_BATTLE && GetMoveEffect(ctx->move) == EFFECT_SPIT_UP)
        dmg *= gBattleMons[ctx->battlerAtk].volatiles.stockpileCounter; // KaizoCore: Gen 3 base damage x stockpile count
    DAMAGE_APPLY_MODIFIER(GetTargetDamageModifier(ctx));
```

and

```c
    if (ctx->randomFactor)
    {
        dmg *= DMG_ROLL_PERCENT_HI - RandomUniform(RNG_DAMAGE_MODIFIER, 0, DMG_ROLL_PERCENT_HI - DMG_ROLL_PERCENT_LO);
        dmg /= 100;
    }
```
```c
    if (ctx->randomFactor)
    {
        if (!KAIZOCORE_GEN3_BATTLE || GetMoveEffect(ctx->move) != EFFECT_SPIT_UP) // KaizoCore: Gen 3 Spit Up uses adjustsetdamage, no roll
        {
            dmg *= DMG_ROLL_PERCENT_HI - RandomUniform(RNG_DAMAGE_MODIFIER, 0, DMG_ROLL_PERCENT_HI - DMG_ROLL_PERCENT_LO);
            dmg /= 100;
        }
    }
```

The stockpile count is still set during the damage calc; MoveEnd clears it afterwards (battle_move_resolution.c:3023).

### H11. `src/battle_move_resolution.c:2940` and `2993`, `MoveEndMoveBlockRecoil`: crash damage and 1/3 recoil (B4, B5)

```c
            s32 recoil = 0;
            if (B_CRASH_IF_TARGET_IMMUNE == GEN_4 && gBattleStruct->moveResultFlags[gBattlerTarget] & MOVE_RESULT_DOESNT_AFFECT_FOE)
            {
                recoil = GetNonDynamaxMaxHP(gBattlerTarget) / 2;
            }
```
```c
            s32 recoil = 0;
#if KAIZOCORE_GEN3_BATTLE
            // KaizoCore: Gen 3 BattleScript_MoveMissedDoDamage: no crash against an immune target; otherwise the damage
            // the move would have done (no critical hit), halved, at least 1, at most half the target's max HP
            // (Cmd_manipulatedamage DMG_RECOIL_FROM_MISS).
            if (gBattleStruct->moveResultFlags[gBattlerTarget] & MOVE_RESULT_DOESNT_AFFECT_FOE)
                break;
            {
                struct BattleContext ctx = {0};
                ctx.battlerAtk = gBattlerAttacker;
                ctx.battlerDef = gBattlerTarget;
                ctx.move = gCurrentMove;
                ctx.chosenMove = gCurrentMove;
                ctx.moveType = GetBattleMoveType(gCurrentMove);
                ctx.weather = HasWeatherEffect() ? gBattleWeather : B_WEATHER_NONE;
                ctx.fieldStatuses = gFieldStatuses;
                ctx.abilityAtk = GetBattlerAbility(gBattlerAttacker);
                ctx.abilityDef = GetBattlerAbility(gBattlerTarget);
                ctx.holdEffectAtk = GetBattlerHoldEffect(gBattlerAttacker);
                ctx.holdEffectDef = GetBattlerHoldEffect(gBattlerTarget);
                ctx.typeEffectivenessModifier = CalcTypeEffectivenessMultiplier(&ctx);
                ctx.isCrit = FALSE;
                ctx.randomFactor = TRUE;
                recoil = CalculateMoveDamageVars(&ctx) / 2;
                if (recoil == 0)
                    recoil = 1;
                if (recoil > (s32)(GetNonDynamaxMaxHP(gBattlerTarget) / 2))
                    recoil = GetNonDynamaxMaxHP(gBattlerTarget) / 2;
            }
#else
            if (B_CRASH_IF_TARGET_IMMUNE == GEN_4 && gBattleStruct->moveResultFlags[gBattlerTarget] & MOVE_RESULT_DOESNT_AFFECT_FOE)
            {
                recoil = GetNonDynamaxMaxHP(gBattlerTarget) / 2;
            }
```

and close the `#else` right after the old chain's last branch, before `SetPassiveDamageAmount`:

```c
            else
            {
                recoil = 1;
            }
            SetPassiveDamageAmount(gBattlerAttacker, recoil);
```
```c
            else
            {
                recoil = 1;
            }
#endif
            SetPassiveDamageAmount(gBattlerAttacker, recoil);
```

The `break` leaves the switch with `result` still MOVEEND_RESULT_CONTINUE, so the function's own
`gBattleScripting.moveendState++` still runs. `CalculateMoveDamageVars` and `CalcTypeEffectivenessMultiplier` are
declared in battle_util.h (lines 264, 267); `updateFlags` stays 0, so nothing is recorded.

Recoil, line 2993:

```c
                SetPassiveDamageAmount(gBattlerAttacker, gBattleScripting.savedDmg * max(1, GetMoveRecoil(gCurrentMove)) / 100);
```
```c
                if (KAIZOCORE_GEN3_BATTLE && GetMoveRecoil(gCurrentMove) == 33)
                    SetPassiveDamageAmount(gBattlerAttacker, gBattleScripting.savedDmg / 3); // KaizoCore: Gen 3 MOVE_EFFECT_RECOIL_33 is damage / 3
                else
                    SetPassiveDamageAmount(gBattlerAttacker, gBattleScripting.savedDmg * max(1, GetMoveRecoil(gCurrentMove)) / 100);
```

(Take Down, Submission and Struggle are 25: x 25 / 100 equals / 4, no change.)

### H12. `src/battle_move_resolution.c:4191`, `GetSleepTalkMove`: Choice Band applies (B6)

```c
    unusableMovesBits = CheckMoveLimitations(gBattlerAttacker, unusableMovesBits, ~(MOVE_LIMITATION_PP | MOVE_LIMITATION_CHOICE_ITEM));
```
```c
    // KaizoCore: Gen 3 Cmd_trychoosesleeptalkmove checks everything but PP, and its Choice Band test always applies
    unusableMovesBits = CheckMoveLimitations(gBattlerAttacker, unusableMovesBits,
                                             KAIZOCORE_GEN3_BATTLE ? ~MOVE_LIMITATION_PP : ~(MOVE_LIMITATION_PP | MOVE_LIMITATION_CHOICE_ITEM));
```

### H13. `src/battle_script_commands.c:8846`, `Cmd_trysetencore`: 3 to 6 turns (B7)

```c
        // Encore always lasts 3 turns, but we need to account for a scenario where Encore changes the move during the same turn.
        if (HasBattlerActedThisTurn(gBattlerTarget))
            gBattleMons[gBattlerTarget].volatiles.encoreTimer = B_ENCORE_TIMER;
        else
            gBattleMons[gBattlerTarget].volatiles.encoreTimer = B_ENCORE_TIMER - 1;
```
```c
        // Encore always lasts 3 turns, but we need to account for a scenario where Encore changes the move during the same turn.
        if (KAIZOCORE_GEN3_BATTLE)
            gBattleMons[gBattlerTarget].volatiles.encoreTimer = (Random() & 3) + 3; // KaizoCore: Gen 3 Cmd_trysetencore, 3 to 6
        else if (HasBattlerActedThisTurn(gBattlerTarget))
            gBattleMons[gBattlerTarget].volatiles.encoreTimer = B_ENCORE_TIMER;
        else
            gBattleMons[gBattlerTarget].volatiles.encoreTimer = B_ENCORE_TIMER - 1;
```

Check the field width first: `encoreTimer` is declared through `VOLATILE_ENCORE_TIMER ... (u32, B_ENCORE_TIMER)` in
`include/constants/battle.h:251`, sized for the value 4. If that gives 3 bits, 6 fits; if the macro gives exactly the
bits for 4 and no more, change that line's maximum to 6.

### H14. `src/battle_util.c:10674`, `DoesOHKOMoveMissTarget`: one point less (B8)

```c
        if (MoveHasIncreasedAccByTenOnSameType(cv->move) && !IS_BATTLER_OF_TYPE(cv->battlerAtk, GetMoveType(cv->move)))
            odds -= 10;
        if (RandomPercentage(RNG_ACCURACY, odds) && gBattleMons[cv->battlerAtk].level >= gBattleMons[cv->battlerDef].level)
```
```c
        if (MoveHasIncreasedAccByTenOnSameType(cv->move) && !IS_BATTLER_OF_TYPE(cv->battlerAtk, GetMoveType(cv->move)))
            odds -= 10;
        if (KAIZOCORE_GEN3_BATTLE && odds > 0)
            odds--; // KaizoCore: Gen 3 Cmd_tryKO hits only when Random() % 100 + 1 < odds
        if (RandomPercentage(RNG_ACCURACY, odds) && gBattleMons[cv->battlerAtk].level >= gBattleMons[cv->battlerDef].level)
```

### H15. `src/battle_util.c:7855` and `7896`, `DoFixedDamageMoveCalc`: Beat Up's roll and crit message (B10)

```c
    case EFFECT_BEAT_UP:
        if (GetConfig(B_BEAT_UP) < GEN_5)
            dmg = CalcBeatUpDamage(ctx);
        break;
```
```c
    case EFFECT_BEAT_UP:
        if (GetConfig(B_BEAT_UP) < GEN_5)
        {
            dmg = CalcBeatUpDamage(ctx);
            if (KAIZOCORE_GEN3_BATTLE && ctx->randomFactor) // KaizoCore: Gen 3 Beat Up rolls 85 to 100% per hit (adjustnormaldamage)
                dmg = dmg * (DMG_ROLL_PERCENT_HI - RandomUniform(RNG_DAMAGE_MODIFIER, 0, DMG_ROLL_PERCENT_HI - DMG_ROLL_PERCENT_LO)) / 100;
        }
        break;
```

and

```c
    gSpecialStatuses[ctx->battlerDef].criticalHit = FALSE;
```
```c
    if (!(KAIZOCORE_GEN3_BATTLE && GetMoveEffect(ctx->move) == EFFECT_BEAT_UP)) // KaizoCore: Gen 3 Beat Up shows its crit
        gSpecialStatuses[ctx->battlerDef].criticalHit = FALSE;
```

### H16. `src/battle_move_resolution.c:4077`, `GetMetronomeMove`: Nat. Dex's pool (B11)

```c
static enum Move GetMetronomeMove(void)
{
    enum Move move = MOVE_NONE;

#if B_METRONOME_MOVES >= GEN_9
```
```c
static enum Move GetMetronomeMove(void)
{
    enum Move move = MOVE_NONE;

#if KAIZOCORE_GEN3_BATTLE
    // KaizoCore: Nat. Dex Emerald's Metronome rolls 1 to 847 and rerolls placeholder rows and the banned list, so it calls
    // the 354 Gen 3 moves and the 11 newer moves that work, each equally likely.
    static const u16 sNatDexMetronomeExtras[] =
    {
        MOVE_AIR_SLASH, MOVE_SHADOW_SNEAK, MOVE_LEAF_STORM, MOVE_STRUGGLE_BUG, MOVE_DISARMING_VOICE, MOVE_DRAINING_KISS,
        MOVE_PLAY_ROUGH, MOVE_FAIRY_WIND, MOVE_MOONBLAST, MOVE_PLAY_NICE, MOVE_DAZZLING_GLEAM,
    };
    do
    {
        u32 roll = RandomUniform(RNG_METRONOME, 1, MOVES_COUNT_GEN3 - 1 + ARRAY_COUNT(sNatDexMetronomeExtras));
        move = (roll < MOVES_COUNT_GEN3) ? roll : sNatDexMetronomeExtras[roll - MOVES_COUNT_GEN3];
    } while (InvalidMetronomeMove(move));
    return move;
#else
#if B_METRONOME_MOVES >= GEN_9
```

and at the end of the function:

```c
    move = RandomUniformExcept(RNG_METRONOME, 1, moveCount - 1, InvalidMetronomeMove);
    return move;
}
```
```c
    move = RandomUniformExcept(RNG_METRONOME, 1, moveCount - 1, InvalidMetronomeMove);
    return move;
#endif // KAIZOCORE_GEN3_BATTLE
}
```

`MOVES_COUNT_GEN3` is 355 (`include/constants/moves.h:387`, the value of MOVE_ROOST), so rolls 1 to 354 are the Gen 3
moves and 355 to 365 the extras. `InvalidMetronomeMove` (line 4071) rejects placeholders and `metronomeBanned`, which
is the same 18-move list as Gen 3.

### H17. `src/battle_hold_effects.c:198`, `TryKingsRock`: Gen 3's King's Rock list (D17, B9)

Add above `TryKingsRock`:

```c
#if KAIZOCORE_GEN3_BATTLE
// KaizoCore: Gen 3 King's Rock works only for moves with FLAG_KINGS_ROCK_AFFECTED. Read from Nat. Dex Emerald's
// gBattleMoves (0x0831C898, flags bit 0x20) for the 354 Gen 3 moves and the 11 newer ones. Moves with their own flinch
// chance (Rolling Kick, Sky Attack, Snore, Twister, Air Slash) roll King's Rock as well.
static const u16 sKaizoCoreGen3KingsRockMoves[] =
{
    MOVE_POUND, MOVE_KARATE_CHOP, MOVE_DOUBLE_SLAP, MOVE_COMET_PUNCH, MOVE_MEGA_PUNCH, MOVE_PAY_DAY, MOVE_SCRATCH,
    MOVE_VISE_GRIP, MOVE_RAZOR_WIND, MOVE_CUT, MOVE_GUST, MOVE_WING_ATTACK, MOVE_FLY, MOVE_BIND, MOVE_SLAM,
    MOVE_VINE_WHIP, MOVE_DOUBLE_KICK, MOVE_MEGA_KICK, MOVE_JUMP_KICK, MOVE_ROLLING_KICK, MOVE_HORN_ATTACK,
    MOVE_FURY_ATTACK, MOVE_TACKLE, MOVE_WRAP, MOVE_TAKE_DOWN, MOVE_THRASH, MOVE_DOUBLE_EDGE, MOVE_PIN_MISSILE,
    MOVE_SONIC_BOOM, MOVE_WATER_GUN, MOVE_HYDRO_PUMP, MOVE_SURF, MOVE_HYPER_BEAM, MOVE_PECK, MOVE_DRILL_PECK,
    MOVE_SUBMISSION, MOVE_LOW_KICK, MOVE_SEISMIC_TOSS, MOVE_STRENGTH, MOVE_RAZOR_LEAF, MOVE_SOLAR_BEAM, MOVE_PETAL_DANCE,
    MOVE_DRAGON_RAGE, MOVE_FIRE_SPIN, MOVE_ROCK_THROW, MOVE_EARTHQUAKE, MOVE_DIG, MOVE_QUICK_ATTACK, MOVE_RAGE,
    MOVE_NIGHT_SHADE, MOVE_BIDE, MOVE_SELF_DESTRUCT, MOVE_EGG_BOMB, MOVE_WATERFALL, MOVE_CLAMP, MOVE_SWIFT,
    MOVE_SKULL_BASH, MOVE_SPIKE_CANNON, MOVE_HIGH_JUMP_KICK, MOVE_BARRAGE, MOVE_SKY_ATTACK, MOVE_PSYWAVE,
    MOVE_CRABHAMMER, MOVE_EXPLOSION, MOVE_FURY_SWIPES, MOVE_BONEMERANG, MOVE_SLASH, MOVE_STRUGGLE, MOVE_TRIPLE_KICK,
    MOVE_SNORE, MOVE_FLAIL, MOVE_AEROBLAST, MOVE_REVERSAL, MOVE_MACH_PUNCH, MOVE_FEINT_ATTACK, MOVE_BONE_RUSH,
    MOVE_OUTRAGE, MOVE_ROLLOUT, MOVE_FALSE_SWIPE, MOVE_FURY_CUTTER, MOVE_STEEL_WING, MOVE_RETURN, MOVE_FRUSTRATION,
    MOVE_MAGNITUDE, MOVE_MEGAHORN, MOVE_DRAGON_BREATH, MOVE_RAPID_SPIN, MOVE_VITAL_THROW, MOVE_HIDDEN_POWER,
    MOVE_CROSS_CHOP, MOVE_TWISTER, MOVE_EXTREME_SPEED, MOVE_WHIRLPOOL, MOVE_BEAT_UP, MOVE_UPROAR, MOVE_SPIT_UP,
    MOVE_REVENGE, MOVE_BRICK_BREAK, MOVE_ENDEAVOR, MOVE_ERUPTION, MOVE_DIVE, MOVE_ARM_THRUST, MOVE_ICE_BALL,
    MOVE_BLAST_BURN, MOVE_HYDRO_CANNON, MOVE_METEOR_MASH, MOVE_WEATHER_BALL, MOVE_AIR_CUTTER, MOVE_OVERHEAT,
    MOVE_SILVER_WIND, MOVE_TICKLE, MOVE_SIGNAL_BEAM, MOVE_SHADOW_PUNCH, MOVE_SKY_UPPERCUT, MOVE_SAND_TOMB,
    MOVE_MUDDY_WATER, MOVE_BULLET_SEED, MOVE_AERIAL_ACE, MOVE_ICICLE_SPEAR, MOVE_DRAGON_CLAW, MOVE_FRENZY_PLANT,
    MOVE_BOUNCE, MOVE_MUD_SHOT, MOVE_POISON_TAIL, MOVE_VOLT_TACKLE, MOVE_MAGICAL_LEAF, MOVE_LEAF_BLADE, MOVE_ROCK_BLAST,
    MOVE_SHOCK_WAVE, MOVE_WATER_PULSE, MOVE_PSYCHO_BOOST, MOVE_AIR_SLASH, MOVE_SHADOW_SNEAK, MOVE_LEAF_STORM,
    MOVE_DISARMING_VOICE, MOVE_DRAINING_KISS, MOVE_PLAY_ROUGH, MOVE_FAIRY_WIND, MOVE_MOONBLAST, MOVE_DAZZLING_GLEAM,
};

static bool32 KaizoCore_IsGen3KingsRockMove(enum Move move)
{
    for (u32 i = 0; i < ARRAY_COUNT(sKaizoCoreGen3KingsRockMoves); i++)
    {
        if (sKaizoCoreGen3KingsRockMoves[i] == move)
            return TRUE;
    }
    return FALSE;
}
#endif // KAIZOCORE_GEN3_BATTLE
```

(140 entries. Tickle carries the bit in the ROM but never damages, so it never triggers.)

Then in `TryKingsRock`, lines 202 to 206:

```c
    if (!IsBattlerAlive(battlerDef)
     || !IsBattlerTurnDamaged(battlerDef, EXCLUDING_SUBSTITUTES)
     || MoveIgnoresKingsRock(gCurrentMove)
     || MoveHasAdditionalEffect(gCurrentMove, MOVE_EFFECT_FLINCH))
        return effect;
```
```c
    if (!IsBattlerAlive(battlerDef)
     || !IsBattlerTurnDamaged(battlerDef, EXCLUDING_SUBSTITUTES)
#if KAIZOCORE_GEN3_BATTLE
     || !KaizoCore_IsGen3KingsRockMove(gCurrentMove)) // KaizoCore: Gen 3 flag list, flinch moves included
#else
     || MoveIgnoresKingsRock(gCurrentMove)
     || MoveHasAdditionalEffect(gCurrentMove, MOVE_EFFECT_FLINCH))
#endif
        return effect;
```

`EXCLUDING_SUBSTITUTES` already matches Gen 3 (`TARGET_TURN_DAMAGED` is set only by real HP damage).

### H18 (optional). Weather Ball doubles the damage, not the power (B13)

`src/battle_util.c:6399`, `CalcMoveBasePower`:

```c
    case EFFECT_WEATHER_BALL:
        if (ctx->weather & B_WEATHER_ANY)
            basePower *= 2;
        break;
```
```c
    case EFFECT_WEATHER_BALL:
        if (ctx->weather & B_WEATHER_ANY && !KAIZOCORE_GEN3_BATTLE) // KaizoCore: Gen 3 doubles the damage instead (DoMoveDamageCalcVars)
            basePower *= 2;
        break;
```

and in `DoMoveDamageCalcVars`, extend H10's first insertion:

```c
    if (KAIZOCORE_GEN3_BATTLE && GetMoveEffect(ctx->move) == EFFECT_SPIT_UP)
        dmg *= gBattleMons[ctx->battlerAtk].volatiles.stockpileCounter; // KaizoCore: Gen 3 base damage x stockpile count
```
```c
    if (KAIZOCORE_GEN3_BATTLE && GetMoveEffect(ctx->move) == EFFECT_SPIT_UP)
        dmg *= gBattleMons[ctx->battlerAtk].volatiles.stockpileCounter; // KaizoCore: Gen 3 base damage x stockpile count
    else if (KAIZOCORE_GEN3_BATTLE && GetMoveEffect(ctx->move) == EFFECT_WEATHER_BALL && ctx->weather & B_WEATHER_ANY)
        dmg *= 2; // KaizoCore: Gen 3 Weather Ball sets dmgMultiplier 2 on the base damage (Cmd_setweatherballtype)
```

The type change (Water, Rock, Fire, Ice) already matches Gen 3. B14's other "x2 damage" moves could use the same
pattern if the damage formula work wants exact numbers; they are left alone here.

## Unsure

1. **H6 leaves out burn and screens at use time.** Gen 3's CalculateBaseDamage at use applies the attacker's burn
   (Doom Desire is physical) and the target side's Reflect or Light Screen. In Heart & Soul both sit in the post-roll
   modifiers, which `randomFactor = FALSE` skips. A burned Doom Desire user or a screen up when Future Sight is used
   gives Gen 3 about half the stored damage; H6 does not. Not tested in game.
2. **Sleep Talk with a Choice item (H12).** Read from the vanilla code path (check 0xFD, Choice Band test with no
   check bit at 0x08041B40); the in-game result when the user is locked into Sleep Talk itself (all four moves unusable,
   so Sleep Talk fails) was not reproduced.
3. **H13's field width.** `encoreTimer`'s bit width comes from the VOLATILE table macro; confirm it holds 6 before
   applying, or the timer wraps.
4. **Recoil base (H11).** Gen 3 recoil is a fraction of `gHpDealt` (HP actually lost). Heart & Soul uses
   `gBattleScripting.savedDmg`; whether that is capped at the target's remaining HP was not traced.
5. **Pressure on Imprison and Snatch.** Both carry `forcePressure` in Heart & Soul. In Gen 3 they target the user and
   go through the default case of Cmd_ppreduce, which adds a point only when `gBattlerTarget` is not the user and has
   Pressure; what Gen 3 puts in `gBattlerTarget` for a self-target move was not traced. Left with the Pressure work in
   `CancelerPPDeduction`.
6. **Gen 3 "no accuracy roll but misses a semi-invulnerable target"** (`accuracycheck NO_ACC_CALC_CHECK_LOCK_ON`): Ghost
   Curse, Pain Split, Mimic, Role Play, Skill Swap and Yawn fail against a Pokemon in Fly, Dig, Dive or Bounce in Gen 3.
   Heart & Soul's scripts for these have no accuracycheck; whether a generic semi-invulnerable check catches them was not
   traced.
7. **King's Rock on multi-hit moves.** Gen 3 rolls King's Rock once, in the move-end item check; whether Heart & Soul's
   TryKingsRock runs once per hit was not traced.
8. **Confusion self-hit.** Not a move row, but Gen 3 confusion damage goes through CalculateBaseDamage with Pound, so a
   burned Pokemon hits itself for half. Heart & Soul computes it with `randomFactor = FALSE` (battle_move_resolution.c:208),
   which returns before the burn modifier. Worth a look with the damage formula work.
9. **AI.** `battle_ai_*.c` predictions were not reviewed. After H3, H8 to H10 the AI's damage estimates for Future Sight,
   Spit Up and Beat Up follow the same functions, so they should follow; not checked.

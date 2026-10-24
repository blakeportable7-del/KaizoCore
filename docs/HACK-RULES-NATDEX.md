# Nat. Dex 1.2.1 battle rules, read from the ROMs

Emerald Nat. Dex (`.vendor/roms/emerald-natdex-121.gba`) and FireRed Nat. Dex (`.vendor/roms/firered-natdex-121.gba`),
both carrying version bytes `01 02 01` at 0x0800048C and species count 1258 at 0x08000170. Read 2026-10-10 by
disassembling the Thumb code (`arm-none-eabi-objdump -Mforce-thumb`) and dumping the data tables, then comparing with
the vanilla ROMs (`emerald-u.gba`, `firered-u-v10.gba`).

Where the code was found: the hacks' public source (CyanSMP64/pokeemerald commit 20eadb6c "release v1.2.1", and
CyanSMP64/pokefirered commit b84ca974 "release v1.2.1", diffed against their pret merge bases 88222641 and 1dd602d9)
was used only as a map. Every rule below is quoted from ROM bytes or instructions unless it says "(source only)".
"HnS" is Heart & Soul (pokeemerald-expansion, GEN_LATEST battle configs); its side comes from the tracker's own HnS
model (`HitChance.kt`, `MoveRules.kt`, which cite the HnS source) or from the expansion's GEN_LATEST behavior, see Unsure.

## Answer first: Blizzard in hail

**Not a sure hit, in either game.** Blizzard (move 59) rolls its normal 70 accuracy in hail.

- Em: the move row at 0x0831C898 + 59*12 reads power 120, type 15 (Ice), accuracy 70, effect 5 (FREEZE_HIT). The only
  sure-hit tests in AccuracyCalcHelper (0x08048C04) are Lock-On, rain + effect 152, effect 17, effect 78 and move 0x24D:
  `ldrh r0,[r1,#10]; cmp r0,#152` (0x08048D08), `cmp r0,#17` (0x08048D18), `cmp r0,#78` (0x08048D1C),
  `ldr r0,=0x24D; cmp r5,r0` (0x08048D20). No test of weather bit 0x80 (hail) and no compare against move 59 anywhere
  in the helper or in Cmd_accuracycheck (0x08048D58 to 0x080490E6).
- FR: same row at 0x08250C74 + 59*12 (120, Ice, 70, effect 5). AccuracyCalcHelper 0x0801E76C has the same five tests
  (`cmp #152` 0x0801E872, `cmp #17` 0x0801E880, `cmp #78` 0x0801E884, `=0x24D` 0x0801E888) and nothing for hail.
- HnS: sure hit in hail (B_BLIZZARD_HAIL; `MoveRules.adjust` laterRules). **Differs.** Vanilla Gen 3: same as Nat. Dex.

## Summary of differences

### From vanilla Gen 3 (both games unless marked)
1. Fairy type 18 exists and is special by type; the type chart is Gen 6's (Fairy rows added, Steel no longer resists
   Ghost or Dark). (D8, D9)
2. 11 new moves work (Air Slash, Shadow Sneak, Leaf Storm, Struggle Bug, Disarming Voice, Draining Kiss, Play Rough,
   Fairy Wind, Moonblast, Play Nice, Dazzling Gleam); the other 482 new move ids are one empty placeholder row. (F4, F5)
3. Play Nice (589) always hits, by move id. (A20)
4. Draining Kiss heals 75% of the damage. (F6)
5. Disarming Voice is on the Soundproof list. (F6)
6. Sweet Kiss, Charm and Moonlight are Fairy type; Curse is Ghost type (was ???). 30 rows in Em and 29 in FR show
   accuracy 0 instead of 100 (95 for Nature Power), display only. (F3, A21)
7. Light Ball also works for Pikachu-Cap (1156) and Pikachu-Partner (1157). (D12)
8. Fairy Feather (item 226, hold effect 67) boosts Fairy moves by 10%. (D12)
9. Razor Claw (item 90) exists and carries the Scope Lens hold effect: +1 crit stage. (B4)
10. Abilities are u16 (battle struct +0x46); ids 78 to 319 are defined but no battle code reads them. (F2)
11. Every ability reveal shows a Black/White style pop-up (49 script sites plus one C site). (E)
12. A quirk new with u16 abilities: if battler 0 has ability 253 or 254, the damage formula's Cloud Nine/Air Lock check
    looks at Mud Sport/Water Sport instead. (C9)
13. Em only: Kinesis gains the Magic Coat flag; Snatch loses the Mirror Move flag. (F3)

### From Heart & Soul (both games unless marked)
Accuracy: Blizzard in hail, Toxic from a Poison type, No Guard, Minimize + Stomp are not sure hits; Hurricane, Snow
Cloak, Wide Lens, Zoom Lens do not exist or do nothing; Keen Eye does not ignore evasion; Hustle's accuracy cut follows
the move's type, not its category; OHKO hits on roll < chance, so one point lower. Crits: odds 1/16, 1/8, 1/4, 1/3, 1/2
(HnS 1/24, 1/8, 1/2, 1/1); damage x2 (HnS x1.5); no Super Luck; Leek works only for Farfetch'd. Damage: split by type;
random roll applied last (after STAB and type); Explosion and Self-Destruct halve defense; no Rock Sp. Def boost in sand;
in doubles only two-foe moves are halved (Earthquake and Surf are not); badge stat boosts apply. Weather Ball doubles
the final damage instead of the power. Abilities 78 to 319 do nothing. Same as HnS: the accuracy stage table, Thunder
in rain and sun, Compound Eyes, Sand Veil, BrightPowder, Foresight, Lock-On, Sturdy vs OHKO, Focus Energy and Dire Hit
+2, Scope Lens, Battle Armor, crit stage ignoring, weather x1.5/x0.5, Solar Beam halving, Cloud Nine and Air Lock,
Swift Swim and Chlorophyll, STAB 1.5, burn and Guts, the type chart.

### Between the two hacks
FR keeps vanilla FireRed's auto-hit and no-crit rules for the Oak tutorial, the first battle and Pokedude battles; Em
blocks crits in the Wally tutorial and the first battle. Badge boosts read different flags. Kinesis and Snatch flags
changed in Em only. Everything else below is the same code in both.

## Addresses used (ROM code and data; RAM from literal pools)

| Thing | Emerald Nat. Dex | FireRed Nat. Dex |
|---|---|---|
| gBattleScriptingCommandsTable (250 / 249 entries) | 0x0838F4D0 | 0x0820C5CC |
| Cmd_accuracycheck / AccuracyCalcHelper | 0x08048D58 / 0x08048C04 | 0x0801E8C0 / 0x0801E76C |
| Cmd_critcalc / Cmd_damagecalc | 0x08049318 / 0x0804948C | 0x0801EF08 / 0x0801F09C |
| CalculateBaseDamage | 0x080694C8 | 0x0803ED5C |
| Cmd_typecalc / Cmd_adjustnormaldamage | 0x08049784 / 0x0804A07C | 0x0801F390 / 0x0801FC84 |
| Cmd_tryKO / Cmd_weatherdamage / Cmd_setweatherballtype | 0x080545D0 / 0x080549B8 / 0x08058F58 | 0x08029AB0 / 0x08029E98 / 0x0802E3DC |
| Swift Swim / Chlorophyll test (GetWhoStrikesFirst) | 0x0803E970 | 0x0801444C |
| AbilityBattleEffects / CreateAbilityPopUp | 0x08044580 / 0x08085878 | 0x0801A050 / 0x08051868 |
| gBattleMoves (12-byte rows, effect u16 at +10) | 0x0831C898 (header 0x080001CC) | 0x08250C74 |
| gTypeEffectiveness | 0x0838CA94 | 0x08234B40 |
| sAccuracyStageRatios (4 bytes per entry) / sCriticalHitChance | 0x0838F8B8 / 0x0838F8EC | 0x0820C9B0 / 0x0820C9E4 |
| gStatStageRatios / sHoldEffectToType | 0x0835A5BE / 0x0835A604 | 0x0828E099 / 0x0828E0B8 |
| gItems (52-byte rows, holdEffect +24, param +25) | 0x085FC720 | 0x083EFF20 |
| gBattleScriptsForMoveEffects | 0x082DC874 | 0x08188214 |
| BattleScript_AbilityPopUpTarget / BattleScript_AbilityPopUp | 0x082DF717 / 0x082DF721 | 0x0818AFE9 / 0x0818AFF3 |
| sSoundMovesTable | 0x0838F098 | 0x0820C194 |
| RAM: gBattleMons (stride 0x5C; types +0x21/+0x22, item +0x2E, ability u16 +0x46, status2 +0x54) | 0x02024058 | 0x02023E9C |
| RAM: gBattleWeather (u16) / gCritMultiplier / gBattleMoveDamage | 0x020243B0 / 0x020241F6 / 0x020241D4 | 0x020241E4 / 0x0202403A / 0x02024018 |
| RAM: gBattlerAbility / gBattlescriptCurrInstr | 0x02022FC4 / 0x020241F8 | 0x02022E08 / 0x0202403C |

## A. Accuracy

The roll: `calc = ratio[stage] * moveAcc`, then multipliers, then miss if `Random() % 100 + 1 > calc`
(Em 0x08049060 to 0x08049076: `bl Random; movs r1,#100; bl __umodsi3; adds r0,#1; cmp r0,r4; ble hit`;
FR 0x0801EC50 to 0x0801EC64 the same). So calc is the hit percentage.

**A1. Blizzard in hail.** See the top: not a sure hit, both games. HnS: sure hit. Differs.

**A2. Stage ratio table.**
- Em: 0x0838F8B8 `21 64 / 24 64 / 2b 64 / 32 64 / 3c 64 / 4b 64 / 01 01 / 85 64 / a6 64 / 02 01 / e9 64 / 85 32 / 03 01`
  (each padded to 4 bytes): 33/100, 36/100, 43/100, 50/100, 60/100, 75/100, 1/1, 133/100, 166/100, 2/1, 233/100,
  133/50, 3/1. Index is acc stage + 6 - evasion stage, clamped 0..12 (0x08048EB0 to 0x08048ECE).
- FR: 0x0820C9B0, same bytes; clamp at 0x0801EAB8.
- HnS: same table (`HitChance.RATIO`). Same.

**A3. Thunder in rain: sure hit.**
- Em: 0x08048CF4 `ldrh weather; movs r0,#7; ands` then `ldrh r0,[move,#10]; cmp r0,#152; beq sure` (any rain bit,
  Rain Dance or Drizzle), inside the Cloud Nine (13) / Air Lock (77) check (calls at 0x08048CD2 and 0x08048CEA).
- FR: 0x0801E85C to 0x0801E872, same.
- HnS: sure hit. Same.

**A4. Thunder in sun: accuracy 50.**
- Em: 0x08048F0C `weather & 0x60` and `effect == 152` then `movs r1,#50; mov r8,r1` (0x08048F1E), before the stage ratio.
- FR: 0x0801EAFC to 0x0801EB0E, same.
- HnS: 50. Same.

**A5. Hurricane.**
- Em: move 542 row is the placeholder (power 0, type 9, accuracy 0, pp 0, effect 214). It has no rain or sun rule
  (the helper only tests effect 152). It cannot be used (F5).
- FR: same row.
- HnS: working move, sure hit in rain, 50 in sun. Differs.

**A6. Toxic from a Poison-type user: not a sure hit.**
- Em: Toxic (92) row: accuracy 85, effect 33. The Toxic script (0x082DD0F6) rolls with ACC_CURR_MOVE and the helper has
  no type test of the user.
- FR: same (script 0x08188A85).
- HnS: sure hit (B_TOXIC_NEVER_MISS, `sureOnSameType`). Differs.

**A7. No Guard: does nothing.**
- Em: ability id 100 (source constants). No ability compare in Cmd_accuracycheck or the helper except 13, 77
  (weather), 14, 8, 55; none is 100.
- FR: same compares.
- HnS: sure hit both ways. Differs.

**A8. Compound Eyes: x1.3.**
- Em: 0x08048F48 `ldrh ability(+0x46); cmp r0,#14` then `calc*130/100` (`lsls #6; adds; lsls #1; movs r1,#100; bl div`).
- FR: 0x0801EB3C same.
- HnS: x1.3. Same.

**A9. Hustle: x0.8 on physical moves.**
- Em: 0x08048FCA `cmp r0,#55`, then `mov r2,sl; cmp r2,#8; bhi skip` (move TYPE 0..8), then `calc*80/100`.
- FR: 0x0801EBBA / 0x0801EBC0 same.
- HnS: x0.8, but by move category. Differs for moves whose category and type disagree (Play Rough here is special,
  Air Slash and Struggle Bug here are physical).

**A10. Sand Veil: x0.8 in sandstorm.**
- Em: 0x08048F98 target ability `cmp r0,#8`, weather `& 0x18`, then `calc*80/100`; under the Cloud Nine/Air Lock check.
- FR: 0x0801EB88 same.
- HnS: x0.8. Same.

**A11. Snow Cloak: does nothing.** Em/FR: id 82, no compare in the accuracy code. HnS: x0.8 in hail/snow. Differs.

**A12. Keen Eye: does not ignore evasion.**
- Em: no compare with 51 in the accuracy code; the index is always acc + 6 - evasion (0x08048EB0). Keen Eye only blocks
  accuracy drops (AbilityNoSpecificStatLoss, E).
- FR: same.
- HnS: ignores the target's evasion stages. Differs.

**A13. BrightPowder and Lax Incense.**
- Em: target hold effect 22 (EVASION_UP): `cmp r5,#22` then `calc*(100-param)/100` (0x0804904C). gItems: BrightPowder
  (179) param 10 (x0.9), Lax Incense (221) param 5 (x0.95).
- FR: 0x0801EC3C; gItems 0x083EFF20: same params.
- HnS: same formula, reads its own param. Same rule (see Unsure for HnS Lax Incense's number).

**A14. Wide Lens and Zoom Lens: do not exist.**
- Em: no item of either name in gItems ids 1 to 399; the accuracy code reads only the target's item.
- FR: same.
- HnS: x1.1 and x1.2. Differs.

**A15. Foresight and Odor Sleuth: the target's evasion is ignored entirely (also lowered evasion).**
- Em: 0x08048E7C target status2 (+0x54) `& (1<<29)` then index = attacker's acc stage alone (0x08048E94 `ldrb r5,[r0,#30]`).
- FR: 0x0801EA60 to 0x0801EA80, same.
- HnS: `if (foresight) buff = accStage` in `HitChance`. Same.

**A16. Lock-On and Mind Reader: sure hit.**
- Em: 0x08048C0C statuses3 `& 0x18` and gDisableStructs[target].battlerWithSureHit (+21) == attacker then jump to the
  sure path (0x08048C34).
- FR: 0x0801E774 to 0x0801E79A, same.
- HnS: sure hit. Same.

**A17. Minimize then Stomp: double damage, normal accuracy.**
- Em: Stomp (23) effect 150, script 0x082DDF3B `21 00 00 01 00 00 01 <skip>` (jumpifstatus3condition target
  STATUS3_MINIMIZED) then `2e 66 44 02 02 02` (setbyte gBattleScripting+0x0E = 2, the damage multiplier). No accuracy rule.
- FR: script 0x081898AE, same bytes with FR addresses.
- HnS: sure hit and double (`minimizeDouble`). Differs on accuracy.

**A18. OHKO moves (Guillotine 12, Horn Drill 32, Fissure 90, Sheer Cold 329, all accuracy 30).**
- Em: script 0x082DD1FF `01 <fail> ff ff` (accuracycheck NO_ACC_CALC_CHECK_LOCK_ON, so no stages) then `06` typecalc
  then `93` tryKO. Cmd_tryKO 0x080546CC: `chance = acc + atkLv - defLv` (`ldrb [+0x2A]` both, `ldrb r2,[r2,#3]`), hit
  only if `Random()%100+1 < chance` (0x08054708 `adds r0,#1; cmp r0,r4; blt`) and atkLv >= defLv (0x0805472A `bcs`).
  Lock-On with atkLv >= defLv is a sure hit (0x08054740 to 0x08054766). So the real odds are chance - 1.
- FR: script 0x08188B88 same; Cmd_tryKO 0x08029AB0 same compares.
- HnS: same level gap rule, hits on chance (not chance - 1); Sheer Cold -10 for non-Ice users and Ice immunity.
  Differs by one point and on Sheer Cold.

**A19. Sturdy blocks OHKO moves.**
- Em: 0x08054678 target ability `cmp r4,#5` then BattleScript_SturdyPreventsOHKO (0x082DF8E1).
- FR: Cmd_tryKO `cmp r4,#5`, same.
- HnS: blocks (and the HnS-only survive-at-1 option). Same for OHKO.

**A20. Other accuracy facts.**
- Em: sure hits for effect 17 (ALWAYS_HIT: Swift, Faint Attack, Disarming Voice...), effect 78 (Vital Throw) and move
  589 (Play Nice, `=0x24D` at 0x08048D20). Fly (statuses3 0x40), Dig (0x80) and Dive (1<<18) targets are missed unless
  the move's hit-marker bit is set (0x08048C36 to 0x08048CBC).
- FR: same at 0x0801E7A2 to 0x0801E888. FR only: in the first battle (Oak tutorial) and Pokedude battles the player's
  moves always hit (0x0801E8D8 to 0x0801E956, vanilla FireRed's rule).
- HnS: Play Nice also never misses; the rest the same.

**A21. Zero-accuracy status moves.**
- Em: 30 vanilla rows read accuracy 0 instead of 100 (Mimic, Spider Web, Mean Look, Block, Pain Split, Yawn, Role Play,
  Skill Swap, Nightmare, Conversion 2, Memento, Follow Me, Charge, Helping Hand, Wish, Assist, Ingrain, Magic Coat,
  Recycle, Imprison, Refresh, Grudge, Snatch, Camouflage, Tail Glow, Mud Sport, Slack Off, Water Sport, Vital Throw)
  and Nature Power (95 -> 0). Every zero-accuracy row's script either never runs accuracycheck or runs it with
  NO_ACC_CALC / NO_ACC_CALC_CHECK_LOCK_ON (source, checked for all of them), and Vital Throw is sure by effect 78
  (A20), so the 0 is the modern "--" display, not "always misses".
- FR: the same 29 rows without Nature Power.
- HnS: these moves also show "--". Same display.

## B. Critical hits

**B1. Odds per stage.**
- Em: 0x0838F8EC u16 `10 00 08 00 04 00 03 00 02 00`: 1/16, 1/8, 1/4, 1/3, 1/2. Stage capped at 4
  (0x080493FA `cmp r5,#4; bls; movs r5,#4`); crit if `Random() % table[stage] == 0` (0x0804943A to 0x08049452).
- FR: 0x0820C9E4 same bytes; cap at 0x0801EFEA.
- HnS: 1/24, 1/8, 1/2, 1/1 (`HitChance.CRIT_ODDS`). Differs.

**B2. Focus Energy and Dire Hit: +2.**
- Em: 0x08049364 status2 (+0x54) `& (1<<20)` gives 2 (`negs; asrs #31; movs r0,#2; ands`). Dire Hit sets the same bit
  (source only: pokemon.c ITEM0_DIRE_HIT sets STATUS2_FOCUS_ENERGY, unchanged from vanilla).
- FR: 0x0801EF5C same.
- HnS: +2 each. Same.

**B3. High-crit moves: +1 each, by effect.**
- Em: 0x0804938A to 0x080493A4 effect `cmp #43` (HIGH_CRITICAL), `#75` (SKY_ATTACK), `#200` (BLAZE_KICK), `#209`
  (POISON_TAIL). None of the new moves carries these effects.
- FR: 0x0801EF7A to 0x0801EF90 same.
- HnS: per move (criticalHitStage). Same for the Gen 3 moves.

**B4. Scope Lens and Razor Claw: +1.**
- Em: 0x080493D2 attacker hold effect `cmp r6,#41`. gItems: Scope Lens (198) and Razor Claw (90) both hold effect 41.
- FR: 0x0801EFC2; gItems same.
- HnS: +1 each. Same.

**B5. Super Luck: does nothing.** Em/FR: id 106, critcalc compares only abilities 4 and 75. HnS: +1. Differs.

**B6. Leek and Lucky Punch: +2.**
- Em: hold effect 63 and species 113 (Chansey) at 0x080493A8/0x080493B0; hold effect 66 and species 83 (Farfetch'd) at
  0x080493BA/0x080493CA. gItems: Lucky Punch (222) 63, Leek (225) 66. No other species.
- FR: 0x0801EF98 to 0x0801EFBA same.
- HnS: Lucky Punch Chansey; Leek Farfetch'd and Sirfetch'd. Differs (no Sirfetch'd, no Galarian Farfetch'd).

**B7. Battle Armor and Shell Armor: no crits against them.**
- Em: 0x0804940E target ability `cmp r4,#4; beq none; cmp r4,#75; beq none`. Also none if attacker statuses3
  `& 0x8000` (CANT_SCORE_A_CRIT).
- FR: 0x0801EFFE / 0x0801F002 same.
- HnS: same.

**B8. Crit damage: x2.**
- Em: gCritMultiplier = 2 or 1 (0x08049456, 0x08049472); Cmd_damagecalc 0x080494DE
  `damage = CalculateBaseDamage * gCritMultiplier * dmgMultiplier`.
- FR: 0x0801F0F0 same.
- HnS: x1.5. Differs.

**B9. Crits and stat stages.**
- Em: in CalculateBaseDamage, if gCritMultiplier == 2 and the attacker's Atk stage (`ldrsb [+25]`) is <= 6 the raw
  stat is used (0x080699BE to 0x080699C8 `cmp r1,#2; ... cmp r0,#6; ble raw`); the defender's Def stage (+26) is used
  only if < 6 (0x08069A34 to 0x08069A40 `cmp r0,#5; bgt raw`). Special side the same with +28 / +29.
- FR: same compares in 0x0803ED5C.
- HnS: ignores the attacker's drops and the defender's boosts. Same in effect.

**B10. Screens on a crit.** Em/FR: Reflect and Light Screen apply only when gCritMultiplier == 1 (Em 0x08069AB0
`cmp r1,#1`). HnS: ignored on a crit. Same.

**B11. Battles with no crits.** Em: none in the Wally tutorial and the first battle (0x0804942C type flags `& 0x210`).
FR: none in the Old Man tutorial, the first battle before Oak's flag, and Pokedude battles (0x0801F014 to 0x0801F062,
vanilla FireRed). HnS: none in the first rival battle (tracker). Differs in which battle.

## C. Weather

**C1. Fire and Water: x1.5 / x0.5.**
- Em: 0x08069C84 rain (`weather & 1`, the bit both Rain Dance and Drizzle set): Fire `asrs #1`, Water `*15/10`;
  0x08069CD8 sun (`& 0x60`): Fire `*15/10`, Water `asrs #1`. Applied after the base damage, inside the special branch
  (Fire and Water are always special here), under the Cloud Nine/Air Lock check.
- FR: 0x0803F594 onward, same constants.
- HnS: same multipliers. Same.

**C2. Solar Beam halved in rain, sandstorm and hail.**
- Em: 0x08069CC0 `weather & 0x9F` (rain 0x07, sand 0x18, hail 0x80) and `gCurrentMove == 76` then `asrs #1`.
- FR: same (`movs r0,#159`, `cmp r0,#76`).
- HnS: rain, sand, hail, snow, fog (and Solar Blade). Same for the weathers Nat. Dex has.

**C3. Weather Ball.**
- Em: Cmd_setweatherballtype 0x08058F58: if weather is not suppressed and any weather: dmgMultiplier = 2
  (`movs r0,#2; strb [gBattleScripting,#14]`), type 0x8B Water in rain (`&7`), 0x85 Rock in sand (`&0x18`), 0x8A Fire
  in sun (`&0x60`), 0x8F Ice in hail (`&0x80`); otherwise 0x80 Normal.
- FR: 0x0802E3DC, same immediates.
- HnS: type change the same; power 50 to 100. Differs slightly: Nat. Dex doubles the whole result of the base formula
  (including its +2), HnS doubles the power.

**C4. Rock Sp. Def x1.5 in sandstorm: no.** Em/FR: CalculateBaseDamage has no weather test before the stat math; its
only weather code is C1 and C2. HnS: yes. Differs.

**C5. Hail.**
- Em: Cmd_weatherdamage 0x08054A7C: weather `& 0x80`, not Ice (`cmp #15` on +0x21 and +0x22), not Dig/Dive, then
  maxHP / 16 (`lsrs #4`), at least 1. Hail is never a sure hit for Blizzard (A1). No Snow Cloak, Ice Body or Overcoat.
- FR: 0x08029E98 same.
- HnS: hail or snow with its own abilities. Same damage for hail; differs in what else hail does.

**C6. Sandstorm damage.** Em 0x08054A0C to 0x08054A52: not Rock 5, Steel 8, Ground 4 (both types), not Sand Veil
(`ldrh [+0x46]; cmp #8`), not Dig/Dive, maxHP/16. FR: same compares. HnS: same plus later abilities. Same for Gen 3 types.

**C7. Cloud Nine and Air Lock.**
- Em: every weather rule calls AbilityBattleEffects(19 CHECK_ON_FIELD, 0, 13) and (19, 0, 77): accuracy 0x08048CCA,
  Thunder sun 0x08048EE2, Sand Veil 0x08048F64, speed 0x0803E938, weather damage 0x080549C0, Weather Ball 0x08058F60;
  the damage formula uses case 14 with 13 and 77 (0x08069C5C, 0x08069C72).
- FR: same calls (accuracy 0x0801E83A, Thunder sun 0x0801EADA, speed 0x08014414, the damage formula's weather block
  after 0x0803F580).
- HnS: same. Same.

**C8. Swift Swim and Chlorophyll: speed x2.**
- Em: 0x0803E970 `cmp r3,#33` + `weather & 7`, `cmp r3,#34` + `weather & 0x60`, then multiplier 2 (0x0803E990), both
  battlers (0x0803E9AE), under the Cloud Nine/Air Lock check.
- FR: 0x0801444C / 0x0801448A same.
- HnS: x2. Same.

**C9. Quirk: abilities 253 and 254 on battler 0.**
- Em: the damage formula's weather check calls AbilityBattleEffects(14, battler 0, ability, special 0), which copies
  battler 0's ability into gLastUsedAbility (u16, `strh` to 0x020241EE at 0x080446BA), and case 14 then does
  `ldrh; cmp r0,#253` (Mud Sport scan) and `cmp r0,#254` (Water Sport scan) at 0x08045F22. So when battler 0 (the
  player's left battler) has Steely Spirit (253) or Perish Body (254), the Cloud Nine/Air Lock check in the damage
  formula becomes a Mud Sport or Water Sport check. Accuracy, speed and weather damage use case 19 and are not affected.
- FR: same at 0x0801BA4E.
- HnS: no such quirk. Vanilla: impossible (u8 abilities stop at 77).

## D. Damage formula

Order in a plain hit (script BattleScript_HitFromCritCalc, both games): critcalc, damagecalc (base formula, then
x crit, x dmgMultiplier, then Charge x2 for Electric, Helping Hand x1.5: Em 0x080494DE to 0x0804953C), typecalc (STAB,
then the type chart), adjustnormaldamage (random roll, then Focus Band / Endure).

**D1. Physical / special: by the move's TYPE.**
- Em: CalculateBaseDamage `cmp r1,#8; bls physical` (0x080699B2), `cmp #9` sets 0 for ??? (0x08069B20), special for
  type > 9 (0x08069B28). The 2-bit `category` field in the move row is 0 in every row and never read.
- FR: 0x0803ED5C same (`cmp r1,#8`, `cmp r7,#9`, `cmp r0,#9`).
- HnS: per move category. Differs.

**D2. STAB: x1.5.** Em typecalc 0x080497E4 to 0x08049800: user type1 or type2 == move type then `*15/10`, before the
chart. FR 0x0801F3xx same (`movs r1,#10`). HnS: 1.5 (Adaptability 2, HnS only). Same.

**D3. Burn and Guts.**
- Em: physical branch after `/50`: status1 (+0x50) `& 0x10` and ability != 62 then `/2` (0x08069A8C to 0x08069AA2).
  Guts (62) with any status1: Attack x1.5 (0x08069854). Marvel Scale (63): Defense x1.5.
- FR: same compares (`cmp #62`, `cmp #63`).
- HnS: 0.5 unless Guts (and Facade in Gen 6+). Same apart from Facade.

**D4. Reflect and Light Screen.**
- Em: side status `& 1` (physical) / `& 2` (special), only when gCritMultiplier == 1: `/2`, or `2*(x/3)` in a double
  battle with two foes alive (0x08069AB0 to 0x08069AE4, 0x08069BEA to 0x08069C24).
- FR: same.
- HnS: 0.5, or 2732/4096 in doubles. Same in singles, slightly different in doubles.

**D5. Random roll: 85 to 100%, 16 values.**
- Em: Cmd_adjustnormaldamage 0x0804A07C `bl Random; movs r1,#15; ands; movs r1,#100; subs` then
  `damage * that / 100`, at least 1.
- FR: 0x0801FC84 same.
- HnS: 85 to 100%, but applied before STAB and the type chart. Differs in order (and so in rounding).

**D6. The base formula.** Em 0x08069A02 to 0x08069A86: `stat(after stage) * power * (2*level/5 + 2) / def(after stage)
/ 50`, then burn, screens, the doubles cut, minimum 1, then (special branch) weather and Flash Fire x1.5, then `+2`
(0x08069D30 `adds r0,r5,#2`). FR same. HnS: modern formula with 4096-based multipliers. Differs in rounding.

**D7. Explosion and Self-Destruct halve the target's Defense.** Em 0x080699A6 `effect cmp #7; lsrs #1` on defense.
FR same (`cmp r0,#7`). HnS: no (Gen 5+). Differs.

**D8. Type chart.**
- Em: 0x0838CA94, 120 triplets then `fe fe 00` (Foresight marker) and the two Ghost immunities, `ff` end. Against
  vanilla Emerald's 0x0831ACE8 (110 triplets): removed Ghost->Steel 0.5 and Dark->Steel 0.5; added Fairy->Fighting,
  Dragon, Dark x2; Fairy->Fire, Poison, Steel x0.5; Poison->Fairy x2; Steel->Fairy x2; Fighting, Bug, Dark->Fairy x0.5;
  Dragon->Fairy x0. All Fairy rows sit before the Foresight marker.
- FR: 0x08234B40, identical bytes and order.
- HnS: the Gen 6 chart. Same. (The tracker's `Gen3Types` chart with `natDexOverrides` matches this ROM cell for cell.)

**D9. Fairy-type moves.** Em/FR: type 18 is above 9, so every Fairy move is special, including Play Rough (90 power,
type 18 in the row). Damaging Fairy moves: Disarming Voice 40, Draining Kiss 50, Play Rough 90, Fairy Wind 40, Moonblast
95, Dazzling Gleam 80. HnS: Play Rough physical. Differs for Play Rough.

**D10. Spread moves in doubles.** Em: halved only when the move's target is 8 (both foes) and two foes are alive
(0x08069AF2 to 0x08069B16); target 0x20 (foes and ally: Earthquake, Surf, Explosion) is not cut. FR same. HnS: x0.75 for
every spread move. Differs.

**D11. Badge boosts.** Em: x1.1 Attack (badge 1), Defense (badge 5), Sp. Atk and Sp. Def (badge 7) for the player
(0x080695F4 to 0x0806966A, flags 0x867/0x86B/0x86D, `movs #110`). FR: flags 0x820/0x824/0x826 with battle type mask
0x802 (0x0803EDxx). HnS: see Unsure.

**D12. Other multipliers (Gen 3 set).** Em: Huge Power 37 / Pure Power 74 x2 (0x080695E8), Choice Band 29 x1.5, Soul
Dew 34, Deep Sea Tooth 35 / Scale 36 for Clamperl (0x175), Light Ball 45 for Pikachu 25, 1156 and 1157 (0x08069778 to
0x0806978A), Metal Powder 64 for Ditto 132, Thick Club 65 for Cubone/Marowak, Thick Fat 47 halves Sp. Atk vs Fire/Ice,
Hustle 55 Attack x1.5, Plus 57 / Minus 58 x1.5, Mud Sport / Water Sport halve Electric / Fire power, Overgrow 65,
Blaze 66, Torrent 67, Swarm 68 x1.5 power at 1/3 HP or less. Type items from 0x0835A604 (hold effect, type) pairs,
including `43 12` (hold effect 67 Fairy Feather -> Fairy); each +10% (gItems param 10). FR: same compares. HnS has its
own modern set. Same for the Gen 3 items; Fairy Feather is a Nat. Dex addition.

## E. How abilities become visible

**E1. The mechanism: a pop-up, from one script.**
- Em: BattleScript_AbilityPopUp 0x082DF721 = `76 0f 1b` (various BS_ABILITY_BATTLER, VARIOUS_ABILITY_POPUP 27),
  `39 1f 00` (pause 31), `70 0f` (recordlastability), two setbytes, `3c` return. BattleScript_AbilityPopUpTarget
  0x082DF717 = `31 c4 2f 02 02 f1 41 02 02 01` (copy gBattlerTarget into gBattlerAbility 0x02022FC4) then falls in.
  45 `41 21 f7 2d 08` calls to it in the ROM. Cmd_various's pop-up case calls CreateAbilityPopUp(gBattlerAbility, its
  ability) (source), so the pop-up always names gBattlerAbility, whatever the battler byte says.
- FR: 0x0818AFF3 = `76 0f 0d 39 1f 00 70 0f ...` (VARIOUS_ABILITY_POPUP is 13 in FR); Target entry 0x0818AFE9 copies
  gBattlerTarget (0x02024035) into gBattlerAbility 0x02022E08; 45 calls.
- HnS: also only pop-ups, through its four pop-up scripts. Same idea, different addresses.

**E2. The other script sites (4 per ROM, every `76 xx 1b 39` / `76 xx 0d 39` near the scripts):**
Em 0x082DE4BF (Role Play, battler 1, after `copybyte gBattlerAbility, gBattlerAttacker`), 0x082DF74F and 0x082DF76A
(Trace, battler 10: first its own Trace through sABILITY_OVERWRITE, then the traced ability), 0x082DF721 (E1).
FR 0x08189E32, 0x0818B021, 0x0818B03C, 0x0818AFF3.

**E3. One pop-up from C code: Levitate and Wonder Guard.** Em Cmd_resultmessage 0x0804ABEA: if
gBattleCommunication[6] (MISS_TYPE) > 2 then `bl 0x08085878` (CreateAbilityPopUp) for gBattlerTarget's ability (+0x46).
MISS_TYPE 3 is Wonder Guard (B_MSG_AVOIDED_DMG), 4 is Levitate (B_MSG_GROUND_MISS). FR 0x080207A0 `bl 0x08051868`.

**E4. Which abilities pop up.** (Script labels and their abilities from the source; the ROM site count, 45 calls plus
4 direct, matches it in both games.) Drizzle, Drought, Sand Stream, Speed Boost, Trace, Rain Dish, Shed Skin, Forecast,
Intimidate and its blockers (Clear Body, Hyper Cutter, White Smoke), Lightning Rod (when it draws a move), Sturdy (OHKO
only), Damp, Volt Absorb, Water Absorb, Flash Fire, Suction Cups, Clear Body / White Smoke (stat drop), Keen Eye /
Hyper Cutter (specific stat drop), Water Veil, Limber, Immunity, Insomnia and Vital Spirit (blocking sleep moves and
own Rest), Oblivious, Inner Focus (flinch), Own Tempo, Soundproof, Sticky Hold, Color Change, Rough Skin, Cute Charm,
Static / Poison Point / Flame Body / Effect Spore (BattleScript_ApplySecondaryEffect), Synchronize, Liquid Ooze (drain
moves and Leech Seed), Truant, Run Away (fleeing), the status-curing abilities (AbilityCuredStatus), Role Play's copied
ability, plus Levitate and Wonder Guard (E3). Never shown: Stench, Battle Armor, Shell Armor, Sand Veil, Compound Eyes,
Shield Dust, Natural Cure, Serene Grace, Swift Swim, Chlorophyll, Illuminate, Huge Power, Pure Power, Pressure, Thick
Fat, Early Bird, Hustle, Guts, Marvel Scale, Plus, Minus, Overgrow, Blaze, Torrent, Swarm, Rock Head, Arena Trap,
Shadow Tag, Magnet Pull, Pickup, Cloud Nine, Air Lock, Cacophony, and all of 78 to 319.

**E5. The tracker's `abilityscripts-natdex.tsv` against these ROMs.** Every slot (0x08000348 to 0x080003E8) resolves in
both ROMs and 41 of 42 rows land on a `waitmessage`, `pause` or `printstring` opcode byte (a place the script stops
on). **Row `0350 +5` (Insomnia / Vital Spirit blocking Rest) is broken in both:** BattleScript_RestCantSleep is
Em 0x082DD1E3 / FR 0x08188B6F = `10 77 00` (printstring) `12 1f 00` (waitmessage), so the waitmessage is at +3 and +5
(0x082DD1E8 / 0x08188B74) is the 0x00 high byte of the wait time; gBattlescriptCurrInstr never equals it. The same row
is in NatDexExtension.lua line 17768. Several rows sit on scripts shared with non-ability paths (CantMakeAsleep with
Uproar, AbsorbUpdateHp with every drain move, the MoveEffect* status scripts); the tracker's live-ability id check is
what keeps those honest. Simpler and complete for these ROMs: watch for gBattlescriptCurrInstr == AbilityPopUp + 3
(the 31-frame pause after the pop-up shows: Em 0x082DF724, FR 0x0818AFF6) or the Trace / Role Play sites + 3, and read
gBattleMons[gBattlerAbility].ability (+0x46 u16) then; plus MISS_TYPE 3/4 for Wonder Guard / Levitate.

## F. What works and what is a placeholder

**F1. Abilities 1 to 77: Gen 3 behavior** (with the pop-ups of E). Em/FR: every ability compare in the functions read
above uses ids 4..77 only (accuracy 8, 13, 14, 55, 77; crit 4, 75; damage 37, 47, 55, 57, 58, 62, 63, 65 to 68, 74; speed
33, 34; weather damage 8; typecalc 25, 26; tryKO 5). Cacophony (76) does nothing, as in vanilla. HnS: modern versions.

**F2. Abilities 78 to 319: placeholders.** Em/FR: ABILITIES_COUNT 320 (source). No ability id above 77 appears in any
battle code read here, and in the source every reference above 77 (Sweet Veil, Water Bubble, Corrosion, Comatose,
Pastel Veil, Thermal Exchange, Purifying Salt) is inside a comment. Battle form changes run only on fainting and on
freezing (Shaymin), so form abilities (Zen Mode, Schooling, Disguise and so on) do nothing either (source). HnS: all
work. Differs.

**F3. Moves 1 to 354: the vanilla rows, with these changes** (read by diffing every row against the vanilla ROMs'
gBattleMoves). Em: 29 accuracy 100 -> 0 plus Nature Power 95 -> 0 (A21); Curse type 9 -> 7 (Ghost); Sweet Kiss, Charm,
Moonlight type 0 -> 18 (Fairy); Kinesis flags 18 -> 22 (adds Magic Coat); Snatch flags 16 -> 0 (no Mirror Move).
FR: the same 29 accuracy rows and type changes, without Nature Power, Kinesis and the Snatch flags. Power, PP, effects and secondary chances are all unchanged
(Blizzard and Thunder stay 120, Hidden Power and Weather Ball as vanilla).

**F4. The 11 new moves that work** (rows from 0x0831C898 / 0x08250C74; physical/special by type, D1):
Air Slash 403 (Flying 75/95, 30% flinch, physical here), Shadow Sneak 425 (Ghost 40, priority +1), Leaf Storm 437 (Grass
140/90, effect OVERHEAT: Sp. Atk -2), Struggle Bug 522 (Bug 30, both foes, 100% Sp. Atk -1, physical here), Disarming
Voice 574 (Fairy 40, effect ALWAYS_HIT, both foes, sound), Draining Kiss 577 (Fairy 50, ABSORB), Play Rough 583 (Fairy
90/90, 10% Attack -1, special here), Fairy Wind 584 (Fairy 40), Moonblast 585 (Fairy 95, 30% Sp. Atk -1), Play Nice 589
(Attack -1, never misses, not stopped by Protect: flags 20 has no PROTECT bit), Dazzling Gleam 605 (Fairy 80, both foes).

**F5. The other 482 new move ids (355 to 847): one placeholder row.** Em/FR: effect 214 (PLACEHOLDER), power 0, type 9,
accuracy 0, PP 0. gBattleScriptsForMoveEffects[214] = the plain hit script (Em 0x082DCBD0, FR 0x08188570, same pointer
as effect 0/17). With PP 0 it cannot be chosen; if called anyway it rolls accuracy 0, which always misses (calc 0 <
roll 1..100). Includes Hurricane, Judgment, Natural Gift and every other Gen 4+ move. HnS: all real. Differs.

**F6. New move rules in code.** Draining Kiss: Em Cmd_negativedamage 0x08053760 `gCurrentMove == 0x241` (577) then
`hpDealt*3/4` (`lsls #1; ... asrs #2`), else /2; FR 0x08028C66 `=0x241`. Disarming Voice: sound list Em 0x0838F098 / FR
0x0820C194 = 45, 46, 47, 48, 103, 173, 253, 319, 320, 304, 574, end (Soundproof blocks it). Play Nice: A20.

## Unsure

1. **HnS side of the damage rows.** HnS facts in A and B come from the tracker's HnS model (HitChance.kt, MoveRules.kt,
   which cite pokehns-expansion lines). The HnS facts in C and D (crit x1.5, random roll before STAB, no Explosion
   defense halving, Rock Sp. Def in sand, x0.75 spread, screen 2732/4096, no badge boosts, Weather Ball power 100) are
   pokeemerald-expansion GEN_LATEST defaults and were not re-read from the HnS ROM or source in this pass.
2. **HnS Lax Incense number.** Nat. Dex reads 5 (x0.95) from its gItems; HnS's own param was not checked.
3. **Source-only items.** Dire Hit setting the Focus Energy bit (pokemon.c), Cmd_various's pop-up case using
   gBattlerAbility, the pop-up script to ability mapping in E4, the battle form change triggers in F2, Metronome skipping
   placeholder moves, and the reworked contact abilities (Effect Spore: Random()%10 picks poison 0 / paralysis 1 /
   sleep 2, each then 1/3, so 10% in all; Static, Poison Point, Flame Body 1/3 and now skipped when the attacker already
   has a status or is immune by type) were read in the v1.2.1 source, not disassembled.
4. **E4's never-shown list** follows from the absence of a pop-up script for those abilities in the source; the ROM only
   confirms the site counts, not each ability's path.
5. **C9's in-game effect** was read from the code, not reproduced in an emulator.
6. **Placeholder moves when called** (F5): the outcome is derived from the code (accuracy 0 always misses); Lock-On
   would let one hit for a few HP of ??? damage. Not tried in game.
7. **Badge boost details for FR** (D11): the flags and mask were read, the exact battle-type exclusions were not decoded
   bit by bit.

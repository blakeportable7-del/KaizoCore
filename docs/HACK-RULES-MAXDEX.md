# MaxDex 1.0 battle rules, read from the ROM

FireRed MaxDex 1.0 (`.vendor/roms/firered-maxdex.gba`, header BPRE, CRC 28C12926), compared with vanilla FireRed 1.0
(`.vendor/roms/firered-u-v10.gba`). Read 2026-10-10 by disassembling the Thumb code (`arm-none-eabi-objdump -D -b binary
-marm -Mforce-thumb --adjust-vma=0x08000000`) and dumping the data tables straight from the ROM. Every MaxDex rule below
is quoted from instructions or table bytes; nothing was run. No MaxDex source or patch notes exist under
`C:\Users\bepor\IronMonOne` beyond `docs/research/maxdex.md` (layout and tracker-extension notes, no battle rules) and
`tools/maxdex/*.py`.

"HnS" is Heart & Soul (pokeemerald-expansion). Its side was read from the game source in WSL
(`/root/hns/pokehns-expansion`, the build `docs/ABILITY-POPUPS.md` describes). **HnS is not pure GEN_LATEST:**
`include/config/battle.h` sets `B_CRIT_MULTIPLIER GEN_3` (line 6), `B_BADGE_BOOST GEN_3` (30), `B_BURN_DAMAGE GEN_3`
(36), `B_MULTIPLE_TARGETS_DMG GEN_3` (47) and `B_TOXIC_NEVER_MISS GEN_3` (91). The comparisons use those real values.

How the code is laid out: MaxDex is a rebuilt pokefirered, not a CFRU hook patch. Its battle script command table at
0x0826425C keeps pokefirered's order (253 entries; vanilla 248 at 0x0825011C) and the command functions sit in the same
order at new addresses. The commands and helpers this document cites:

| Function | Address | | Function | Address |
|---|---|---|---|---|
| Cmd_attackcanceler | 0x081AF1C4 | | Cmd_typecalc | 0x081B0F34 |
| JumpIfMoveAffectedByProtect | 0x081AFD8C | | ModulateDmgByType | 0x081B0E5C |
| AccuracyCalcHelper | 0x081AFE60 | | Cmd_adjustnormaldamage | 0x081B2044 |
| Cmd_accuracycheck | 0x081B00DC | | Cmd_resultmessage | 0x081B2D78 |
| Cmd_critcalc | 0x081B08B4 | | SetMoveEffect | 0x081B3194 |
| Cmd_damagecalc | 0x081B0C40 | | Cmd_tryKO | 0x081C05DC |
| CalculateBaseDamage | 0x081C8204 | | Cmd_setweatherballtype | 0x081C5088 |
| AbilityBattleEffects | 0x081A7E38 | | CreateAbilityPopUp | 0x08050BCC |
| new command F8 (move-specific fail check, one pointer argument) | 0x081C6064 | | speed calculation | 0x08015B00 area |

Data: moves 0x08268000 (12 bytes: u16 effect, power, type, **category 0 physical / 1 special / 2 status**, accuracy,
pp, chance, target, priority, flags), items 0x084275D8 (44 bytes, hold effect at +18, parameter at +19), ability names
0x082A4134 (17 bytes each, 290 entries; they match `tracker-gba/src/main/resources/maxdex/abilities.tsv` in every slot,
slot 76 is "-------"), battle scripts per effect 0x081DB86C.

RAM used by the battle code (MaxDex values; most are vanilla + 0x10):

| Name | Address | Notes |
|---|---|---|
| gBattleMons | 0x02023BE4 | 0x5C per battler: stats +2..+10, stat stages +0x18..+0x1F (acc +0x1E, eva +0x1F), u16 ability +0x20, types +0x22/+0x23, hp +0x2A, level +0x2C, max hp +0x2E, u16 item +0x30, status1 +0x50, status2 +0x54 |
| third type | 0x02023DFA + battler | one byte per battler, read by STAB, the type chart, Prankster, OHKO, hail damage. 9 (Mystery) = none |
| gBattleWeather | 0x02023F64 (u16) | rain 0x01, sandstorm 0x08, sun 0x20, hail 0x80, strong winds 0x100; field weather writes 0x05/0x18/0x60 |
| terrain / timer | 0x02023DF5 / 0x02023DF6 | 1 Grassy, 2 Misty, 3 Electric, 4 Psychic |
| Gravity timer | 0x02023DF7 | Wonder Room timer 0x02023DF4 |
| gSideStatuses | 0x02023DEE (u16 per side) | Reflect 0x01, Light Screen 0x02, Lucky Chant 0x80 |
| gStatuses3 | 0x02023E24 (u32 per battler) | Lock-On 0x18, on air 0x40, underground 0x80, minimized 0x100, charged 0x200, underwater 0x40000, Laser Focus 0x04000000, Miracle Eye 0x80000000 |
| gDisableStructs | 0x02023E34, 36 per battler | +21 battlerWithSureHit, +26 Glaive Rush flag |
| gCritMultiplier | 0x02023D82 | 2 = crit |
| gBattlerAbility | 0x02022B50 | pop-up battler |
| pop-up ability override | 0x02024032 (u16) | gBattleScripting + 0x26 |
| gLastUsedAbility | 0x02023D7A (u16) | |
| gBattlescriptCurrInstr | 0x02023D84 | |
| gBattleStruct pointer | 0x02024034 | dynamic move type at +0x13 (type \| 0x80) |

## Answer first: Blizzard in hail

**Not a sure hit.** Blizzard (move 59) rolls its normal 70 accuracy in hail, like any other move.

- Move row 0x08268000 + 59*12: effect 5, power 110, type 15 (Ice), category 1, **accuracy 70**. Effect 5's script
  (0x081DCB92) is `setbyte 0x02023ECD, 4` (freeze) then `goto` the plain hit script 0x081DC9ED.
- The only sure-hit tests in AccuracyCalcHelper (0x081AFE60 to 0x081B00D8) are: No Guard on either side
  (`cmp r0,#98` 0x081AFF18 and 0x081AFF26), the target's Glaive Rush flag (`ldrb r0,[r6,#26]` 0x081AFEFE), Lock-On
  (`ands` with 24 at 0x081AFF34, battlerWithSureHit at 0x081AFF3E), rain plus effect 152 (`movs r0,#7; ands`
  0x081B007E, `cmp r0,#152` 0x081B0090), effect 17 (`cmp r2,#17` 0x081B009E), effect 78 (`cmp r2,#78` 0x081B00A2),
  accuracy 0 (0x081B00A6) and move 505 (`ldr r0,=0x1F9` 0x081B00AC). No test of the hail bit 0x80 and no compare
  against move 59 anywhere in the helper or in Cmd_accuracycheck (0x081B00DC to 0x081B067E). The only hail test in
  accuracycheck is Snow Cloak's (`movs r0,#128; ands` 0x081B04FA).
- Nothing else touches it: the new F8 command's move list (0x081C6064) has no 59, and every `cmp rX,#59` in the battle
  code is an ability compare (Forecast) or a controller command.
- **HnS differs:** Blizzard has `.alwaysHitsInHailSnow = B_BLIZZARD_HAIL >= GEN_4` (moves_info.h:1686, config GEN_LATEST)
  and AccuracyCalcHelper makes it a sure hit in hail or snow (battle_util.c:10460).

## Summary: where MaxDex differs from Heart & Soul

Accuracy
1. Blizzard is not a sure hit in hail (HnS: sure hit in hail and snow). (A1)
2. Hurricane gets neither the rain sure-hit nor the 50 accuracy in sun; only Thunder (effect 152) does. (A4)
3. Keen Eye does not ignore the target's evasion; Unaware ignores neither accuracy nor evasion stages. (A11, A12)
4. Wide Lens, Zoom Lens and Micle Berry do not exist (no Gen 4+ hold items at all). Lax Incense is x0.95 (HnS x0.9). (A14)
5. Minimize does not make Stomp-type moves sure hits; they only double damage. (A18)
6. OHKO moves: No Guard does not help, and the roll hits on (chance - 1)%. (A22)

Critical hits
7. Crit odds are the Gen 3 table 1/16, 1/8, 1/4, 1/3, 1/2 (HnS: 1/24, 1/8, 1/2, always). (B1)
8. Razor Wind and Triple Arrows are not high-crit moves. (B4)
9. Stick works only for Farfetch'd 83 (not Sirfetch'd 890 or a Galarian form). (B7)
10. A gStatuses3 bit 0x8000 on the attacker blocks every crit; nothing found sets it. (B11, Unsure)

Weather
11. Sun and rain change only **special** Fire and Water moves; physical ones (Fire Punch, Flare Blitz, Waterfall, 31 in
    all) are untouched. Flash Fire's boost is special-only too. (C2, D15)
12. Snow Warning sets hail; there is no snow. (C1)
13. No Rock Sp. Def x1.5 in sandstorm. (C5)
14. Solar Blade is never halved by weather; Solar Beam is halved in rain, sandstorm and hail. (C3)

Damage
15. Order of operations is Gen 3's: burn, screens, spread and weather inside the base damage, then +2, crit, STAB, type,
    and the 85-100% random roll **last**. (D2, D7)
16. Explosion, Self-Destruct and Misty Explosion halve the target's Defense. (D8)
17. Type-boosting items (Charcoal etc., Fairy Feather) are x1.1, Sea Incense x1.05 (HnS x1.2). (D12)
18. Light Ball raises only Sp. Atk. (D13)
19. Charge doubles Electric damage twice (x4). (D14)
20. Mud Sport and Water Sport halve the move's power (HnS: about x0.33). (D16)
21. In doubles, a spread move that hits all three others is still halved when two foes stand (HnS GEN_3 config: no cut at 3 targets). Screens drop to 2/3 only when two mons are alive on that side. (D5, D6)

Abilities
22. The pop-up exists but has two paths: one script (0x081DFD04) used by 79 call sites, and a direct C call in
    resultmessage for Levitate and Wonder Guard misses. (E1 to E3)
23. Harvest and Healer act with no pop-up and no message. (E6)

Same as HnS (checked): accuracy stage table, Thunder in rain and sun, Toxic from a Poison type (no sure hit in either),
No Guard, Compound Eyes, Hustle, Victory Star, Sand Veil, Tangled Feet, Wonder Skin, BrightPowder, Gravity, Foresight and
Miracle Eye, Lock-On, Illuminate and Mind's Eye, Glaive Rush; Focus Energy +2, Scope Lens and Super Luck +1, Lucky Punch,
Battle Armor and Shell Armor, Lucky Chant, Laser Focus, Merciless, always-crit moves, crit x2 (HnS's
B_CRIT_MULTIPLIER is GEN_3), crits ignoring bad stages; Weather Ball; 5-turn ability weather; the per-move physical and
special split; STAB 1.5 and Adaptability 2.0; burn halving with the Guts exception; badge boosts (HnS B_BADGE_BOOST is
GEN_3); terrain x1.3; the Gen 6 type chart with Fairy.

## A. Accuracy

Cmd_accuracycheck (0x081B00DC) runs: protect check (0x081AFD8C), AccuracyCalcHelper (0x081AFE60, sure hits and sure
misses), then the stage roll. Hit when `Random() % 100 + 1 <= calc` (`bl 0x804aeb4` Random, `adds r0,#1; cmp r0,r4;
ble` 0x081B05FC to 0x081B0612). A miss of a spread move in doubles sets miss type 2, otherwise 0 (0x081B061E to 0x081B0660).

- **A1. Blizzard in hail: no sure hit.** See "Answer first". HnS: sure hit in hail and snow. **Differs.**
- **A2. Stage table.** 0x08264650, 4-byte rows (u8 dividend, u8 divisor): 33/100, 36/100, 43/100, 50/100, 60/100,
  75/100, 1/1, 133/100, 166/100, 2/1, 233/100, 133/50, 3/1. Index = acc stage + 6 - target evasion stage, clamped 0..12
  (`adds r0,#6; ldrb r1,[r1,#31]; subs` 0x081B0316, clamps 0x081B0322 to 0x081B0332). Same as HnS
  (gAccuracyStageRatios, battle_script_commands.c:849).
- **A3. Order after the stage ratio.** calc = ratio x move accuracy (0x081B0386); Compound Eyes; Victory Star (user, then
  a living partner's); Gravity; Wonder Skin cap; Sand Veil; Snow Cloak; Hustle; Tangled Feet; the target's evasion item.
  Each is integer `x N / 100` via `bl 0x81ec074`. HnS applies Wonder Skin to the move accuracy before the stage ratio
  and items after abilities; the end results match except for rounding.
- **A4. Thunder and Hurricane.** Rain sure-hit and the 50 accuracy in sun test **effect 152 only** (0x081B0090 and
  `cmp r0,#152` 0x081B037E, sun mask `movs r0,#96; ands` 0x081B0374), both behind the Cloud Nine (13) / Air Lock (77)
  check (`bl 0x81a7e38` with 19, 13 and 19, 77). Effect 152 is Thunder (87) alone. Hurricane (548) has effect 76, so
  neither rule reaches it; it only gets to hit flying targets (A21). HnS: Thunder same; Hurricane also has
  `alwaysHitsInRain` (moves_info.h:14064) and 50 in sun. **Differs for Hurricane.**
- **A5. Toxic from a Poison type: no sure hit.** Effect 33's script 0x081DD2BF does attackcanceler, attackstring,
  ppreduce, `jumpifability 0, 17 (Immunity)`, substitute and status checks, `jumpiftype 0, 3` and `jumpiftype 0, 8`
  (target Poison or Steel fails), then a plain `accuracycheck 0x081DE5C1, 0` at 0x081DD300. No user-type test. HnS: the
  same (B_TOXIC_NEVER_MISS GEN_3, battle.h:91). **Same.**
- **A6. No Guard (98).** On the user or the target, a sure hit, checked before the Fly/Dig/Dive tests (0x081AFF18,
  0x081AFF26 come before 0x081AFFBC). HnS: same. **Same.**
- **A7. Compound Eyes (14) x130/100** (`cmp r5,#14` 0x081B03B4). **Hustle (55) x80/100** for category 0 (physical)
  moves (`cmp r0,#55` 0x081B0522, `ldrb r0,[r0,#4]; cmp r0,#0` 0x081B0534). **Victory Star (149) x110/100** for the user
  and again for a partner that has it and is alive and present (0x081B03C8, 0x081B03DA to 0x081B0418). HnS: same. **Same.**
- **A8. Sand Veil (8) and Snow Cloak (82) x80/100** on the target, in sandstorm (mask 24, 0x081B04A0) or hail (mask 0x80,
  0x081B04FA), only when weather has effect. HnS: same, Snow Cloak also in snow. **Same (no snow here).**
- **A9. Tangled Feet (78) halves** calc when the target is confused (status2 & 7) (`cmp r0,#78` 0x081B0558, `lsrs r4,r4,#1`
  0x081B056C). HnS: same. **Same.**
- **A10. Wonder Skin (138)**: status-category moves (byte 4 == 2) are capped at 50 (`cmp r0,#138` 0x081B0440, `cmp r4,#50;
  movs r4,#50` 0x081B0458). HnS: same cap. **Same.**
- **A11. Keen Eye (51) does not touch accuracy.** The evasion-ignoring tests in accuracycheck are only: target Foresight
  (status2 0x20000000), target Miracle Eye (gStatuses3 bit 31), user ability **190 Mind's Eye** (`cmp r2,#190`
  0x081B02C6), moves 504, 539, 625 (Chip Away, Sacred Sword, Darkest Lariat; 0x081B02CA to 0x081B02E0) and user
  ability **35 Illuminate** (`cmp r2,#35` 0x081B030E). Keen Eye only blocks accuracy drops (ChangeStatBuffs: `cmp r0,#51`,
  `cmp r0,#35`, `cmp r0,#190` with `cmp r6,#6` at 0x081BF792 to 0x081BF7A4, pop-up script 0x081E052D). HnS: Keen Eye,
  Unaware, Mind's Eye and Illuminate all ignore evasion (GetTotalAccuracy). **Differs for Keen Eye.**
- **A12. Unaware (108) is not read by accuracycheck.** It only resets attack and defense stages in the damage formula
  (D2). HnS: an Unaware user ignores evasion and an Unaware target ignores the user's accuracy stage. **Differs.**
- **A13. Illuminate and Mind's Eye** ignore the target's evasion stage (A11). HnS: same (B_ILLUMINATE_EFFECT GEN_LATEST).
  **Same.**
- **A14. Items.** The only accuracy item test is the target's hold effect 22 (`cmp r5,#22` 0x081B05E8, calc x (100 -
  param) / 100), with the Enigma Berry branch (item 175) reading gEnigmaBerries 0x02023F9C. Hold effect 22: BrightPowder
  (179) param 10 = x0.90, Lax Incense (221) param 5 = x0.95. Wide Lens, Zoom Lens and Micle Berry are not in the item
  table (375 items: vanilla FireRed plus Dubious Disc 89, Razor Claw 90, Razor Fang 91, Linking Cord 92, Shiny, Dusk, Dawn
  and Ice Stone 99 to 102, Fairy Feather 226). HnS: Lax Incense x0.9 (I_LAX_INCENSE_BOOST GEN_LATEST, param 10), Wide Lens,
  Zoom Lens, Micle Berry exist. **Differs.**
- **A15. Gravity (move 362)** sets the byte 0x02023DF7 to 5 (0x081B4D98) and clears Magnet Rise/Telekinesis bits
  0x00C00000; while nonzero, calc x5/3 (`ldr r0,=0x02023DF7` 0x081B041C, `lsls r0,r4,#2; adds; movs r1,#3` 0x081B0424).
  HnS: x5/3. **Same.**
- **A16. Foresight and Odor Sleuth** (effect 113, `setforesight` 0x081C29D8 sets status2 0x20000000) and **Miracle Eye**
  (363, SetMoveEffect 0x081B4D6E sets gStatuses3 bit 31) make the stage index the user's accuracy stage alone, the
  target's evasion ignored (0x081B02E2 to 0x081B02F0). HnS: same. **Same.**
- **A17. Lock-On and Mind Reader**: gStatuses3 & 0x18 on the target with battlerWithSureHit (disable struct +21) equal to
  the user is a sure hit, tested before the semi-invulnerable misses (0x081AFF2C to 0x081AFF44). HnS: same. **Same.**
- **A18. Minimize.** No accuracy rule. Effect 150's script (0x081DE324) is `jumpifstatus3condition 0, 0x100, 1, hit;
  setbyte 0x0202401A (damage multiplier), 2; goto hit`, so Stomp (23), Needle Arm (302), Astonish (310), Extrasensory
  (326) and Steamroller (543) do double damage to a minimized target and roll accuracy normally. HnS: sure hit too
  (B_MINIMIZE_DMG_ACC GEN_LATEST, battle_util.c:10447). **Differs.**
- **A19. Glaive Rush (787)** sets the user's disable-struct byte 26 to 1 (SetMoveEffect 0x081B5384 to 0x081B539A). Moves
  against a battler with that byte always hit (0x081AFEFE) and do x2 (adjustnormaldamage 0x081B2084 to 0x081B2094).
  HnS has the same two effects. **Same.**
- **A20. Sure misses in AccuracyCalcHelper.** A Prankster (146) user's status move against a target with Dark (17) as any
  of its three types on the other side misses (0x081AFE78 to 0x081AFED6). A target vanished by Shadow Force (473) or
  Phantom Force (571) (gBattleStruct + 0x8C bit, set by setsemiinvulnerablebit 0x081C3A68) is always missed
  (0x081AFF46 to 0x081AFF64). HnS: not compared.
- **A21. Semi-invulnerable targets.** Flying (Fly 19, Bounce 340, Sky Drop 513) is missed unless the move's script set
  hit-marker 0x10000, or the move is Smack Down (485), Hurricane (548) or Thousand Arrows (613) (0x081AFF88 to 0x081AFFAC).
  Underground (Dig) needs marker 0x20000, underwater (Dive) 0x40000 (0x081AFFE0 to 0x081B0044). Sky Drop here is a
  Fly copy (setsemiinvulnerablebit 0x081C39F8). HnS: not compared.
- **A22. OHKO moves** (effect 38: Guillotine, Horn Drill, Fissure, Sheer Cold, all accuracy 30). Script 0x081DD3B5:
  `accuracycheck 0x081DE5C1, 0xFFFF` (only protect, Lock-On and the semi-invulnerable tests), `typecalc`, `tryKO`.
  Cmd_tryKO (0x081C05DC): Sheer Cold (329) against a target with Ice as any of its three types misses (0x081C0636 to
  0x081C066E); Sturdy (5) on the target fails with pop-up script 0x081DFF78 (0x081C06D8 to 0x081C06F6); chance = move
  accuracy + (user level - target level), or 20 + level difference for Sheer Cold from a user without Ice
  (0x081C0744 to 0x081C0796); lands when `Random() % 100 + 1 < chance` and user level >= target level (0x081C079E to
  0x081C07DC), so the real rate is (chance - 1)%; the user's own Lock-On skips the roll but keeps the level test
  (0x081C07F4 to 0x081C081C). Accuracy and evasion stages, No Guard and BrightPowder play no part. HnS: level and Sturdy
  tests the same, No Guard makes it a sure hit, `RandomPercentage(odds)` gives odds% and Sheer Cold is -10 for a non-Ice
  user (DoesOHKOMoveMissTarget). **Differs (No Guard, one point).**

## B. Critical hits

Cmd_critcalc (0x081B08B4) writes gCritMultiplier (0x02023D82) = 2 or 1.

- **B1. Odds.** u16 table at 0x08264684: **16, 8, 4, 3, 2** (1/16, 1/8, 1/4, 1/3, 1/2). The stage is capped at 4
  (`cmp r5,#4; movs r5,#4` 0x081B0B42) and a crit is `Random() % table[stage] == 0` (0x081B0BA2 to 0x081B0BBA). HnS:
  sGen7CriticalHitOdds 24, 8, 2, 1, 1 (battle_util.c:7978, B_CRIT_CHANCE GEN_LATEST). **Differs.**
- **B2. Focus Energy +2.** `status2 & 0x100000` gives 2 (`lsls r1,r1,#13; ands; negs; asrs r6,r0,#31; movs r0,#2; ands r6`
  0x081B0A54 to 0x081B0A60); setfocusenergy (0x081C1050) sets that bit. HnS: +2. **Same.**
- **B3. Dire Hit.** Not traced (Unsure).
- **B4. High-crit moves +1.** Effect 43 (`cmp r0,#43` 0x081B0A76: Karate Chop, Razor Leaf, Crabhammer, Slash, Aeroblast,
  Cross Chop, Air Cutter, Leaf Blade, Night Slash, Shadow Claw, Psycho Cut, Stone Edge, Attack Order, Spacial Rend, Drill
  Run, Snipe Shot, Aqua Cutter, Ivy Cudgel), effect 75 except moves 559 and 560 (Sky Attack only; 0x081B0A7E to
  0x081B0A92), effect 200 (Blaze Kick), effect 209 (Poison Tail, Cross Poison) (0x081B0AD2 to 0x081B0ADE) and move 766
  Esper Wing (`ldr r0,=0x2FE` 0x081B0A98). Razor Wind (13, effect 39) and Triple Arrows (769, effect 110) are not on
  it. HnS: Razor Wind and Triple Arrows have criticalHitStage 1. **Differs for those two.**
- **B5. Scope Lens and Razor Claw** (hold effect 41) +1 (`cmp r7,#41` 0x081B0AE2). HnS: +1. **Same.**
- **B6. Super Luck (104) +1** (`cmp r0,#104` 0x081B0B28). HnS: +1. **Same.**
- **B7. Lucky Punch** (hold effect 63) +2 only for species 113 Chansey; **Stick** (hold effect 66) +2 only for species 83
  Farfetch'd (0x081B0AEA to 0x081B0B18). HnS: Lucky Punch Chansey only; Leek also for Galarian Farfetch'd and Sirfetch'd.
  **Differs for Sirfetch'd (890) and Farfetch'd forms.**
- **B8. Battle Armor (4) and Shell Armor (75)** on the target block every crit, guaranteed ones too (0x081B0900,
  0x081B09A6, 0x081B0B54). HnS: same. **Same.**
- **B9. Lucky Chant (387)** sets side bit 0x80 with a 5-turn timer (SetMoveEffect 0x081B4800 to 0x081B4834); the bit
  blocks every crit against that side (`ldrh; movs r0,#128; ands` 0x081B090E, 0x081B09B4, 0x081B0B62). HnS: same. **Same.**
- **B10. Guaranteed crits.** Moves 486 Storm Throw, 530 Frost Breath, 675 Zippy Zap, 743 Wicked Blow, 744 Surging Strikes,
  794 Flower Trick (0x081B08BA to 0x081B08F0); Merciless (223) against a poisoned target (status1 & 0x88, 0x081B0986 to
  0x081B09E8); Laser Focus (635 sets gStatuses3 0x04000000, 0x081B495C) forces the next roll (0x081B0B98) and the bit is
  cleared after every critcalc (0x081B0C12). HnS: same set of mechanisms. **Same.**
- **B11. A gStatuses3 0x8000 bit on the user blocks all crits** (`movs r0,#128; lsls r0,r0,#8; ands` 0x081B092C,
  0x081B09D0, 0x081B0B80). No code that sets it was found (Unsure). HnS: no such rule.
- **B12. Crit damage x2.** damagecalc multiplies by gCritMultiplier (`ldrb r1,[r2]; muls r1,r0` 0x081B0C94); Sniper (96)
  then x3/2 (0x081B0CA0 to 0x081B0CBE). HnS: x2 (B_CRIT_MULTIPLIER GEN_3, battle.h:6), Sniper x1.5. **Same.**
- **B13. What crits ignore.** With a crit, the user's Atk/Sp. Atk stage is used only when above 6 (`cmp r0,#6; ble`
  0x081C9838, 0x081C99DC), the target's Def/Sp. Def stage only when below 6 (`cmp r0,#5; bgt` 0x081C98BC, 0x081C9A60),
  and Reflect and Light Screen are skipped (`cmp r2,#1` 0x081C992A, 0x081C9ABC). HnS: same. **Same.**

## C. Weather

- **C1. Weather states.** Rain Dance and Drizzle write 0x01, Sandstorm and Sand Stream 0x08, Sunny Day, Drought and
  Orichalcum Pulse 0x20, Hail and **Snow Warning 0x80 (hail)**, Delta Stream ORs 0x100; each sets 5 turns
  (gWishFutureKnock + 40 = 5): setrain 0x081BECCC, setsandstorm 0x081C0A7C, setsunny 0x081C322C, sethail 0x081C3BE8,
  Drizzle 0x081A8730, Sand Stream 0x081A876C, Drought 0x081A87A8, Snow Warning 0x081A881E, Delta Stream 0x081A89C8.
  Overworld weather writes the permanent values 0x05, 0x18, 0x60 (0x081A8688 to 0x081A86E8). There is no snow, and no
  duration items. HnS: Snow Warning makes snow (B_SNOW_WARNING GEN_LATEST); 5-turn ability weather same. **Differs (snow).**
- **C2. Fire and Water in sun and rain: special moves only.** The weather block (0x081C9B42 to 0x081C9C1C: rain bit 0x01,
  Fire /2, Water x15/10; sun 0x60, Fire x15/10, Water /2 except Hydro Steam 823 x15/10) sits after the special-move
  damage. Physical moves leave CalculateBaseDamage before it (`ldr r3,[sp,#44]; cmp r3,#0; bne; b.n 0x81c9c46`
  0x081C99C6). 31 Fire and Water moves are physical (Fire Punch, Flare Blitz, Waterfall and others) and get no weather
  change. HnS: GetWeatherDamageModifier works on the move type for every damaging move (battle_util.c:7437). **Differs.**
- **C3. Solar Beam halving.** Weather & 0x9F (rain, sandstorm, hail) with move 76 or 631 halves damage (0x081C9BAC to
  0x081C9BCC). This is inside the special branch, so Solar Blade (631, physical) is never halved. HnS: Solar Beam and Solar
  Blade halved in rain, sandstorm, hail, snow and fog (battle_util.c:6608). **Differs for Solar Blade.**
- **C4. Weather Ball** (0x081C5088): with weather in effect and any of the low 8 weather bits, damage multiplier 2;
  type Water in rain, Rock in sandstorm, Fire in sun, Ice in hail, else Normal. It is special (category 1), so the rain
  and sun x1.5 also apply. HnS: same (plus snow). **Same.**
- **C5. No Rock Sp. Def x1.5 in sandstorm and no Ice Defense boost.** CalculateBaseDamage reads gBattleWeather only with
  masks 0x60 (Protosynthesis, Orichalcum Pulse, Solar Power, Flower Gift), 24 (Sand Force), 1 and 0x9F and 0x60 (the
  weather block). HnS: Rock Sp. Def x1.5 in sandstorm (battle_util.c:7389), Ice Def x1.5 in snow. **Differs.**
- **C6. Cloud Nine (13) and Air Lock (77)** cancel: the accuracy rules (A4, A8), the damage weather block (calls with 14
  at 0x081C9B4E and 0x081C9B64), Weather Ball, the speed abilities, Protosynthesis, Orichalcum Pulse, Solar Power, Flower
  Gift and weather damage. They do **not** cancel Sand Force (0x081C971C reads the weather bit with no ability check).
  HnS: not compared for Sand Force.
- **C7. Speed in weather.** Swift Swim (33) in rain, Chlorophyll (34) in sun, Sand Rush (137) in sand, Slush Rush (164) in
  hail: speed x2 with weather in effect (0x08015B66 to 0x08015BA6). Surge Surfer (229) x2 in Electric Terrain, Unburden
  (197) x2 after losing the item (0x08015C20 to 0x08015C48). HnS: same (Slush Rush also in snow). **Same.**
- **C8. Other weather abilities in the damage formula.** Solar Power (93) Sp. Atk x1.5 in sun (0x081C9462); Flower Gift
  (119) Atk x1.5 for the user and Sp. Def x1.5 for a target with it, in sun (0x081C94B4, 0x081C9500); Protosynthesis (278)
  in sun and Quark Drive (279) in Electric Terrain: x13/10 to Atk or Sp. Atk when that is the highest stat and matches the
  move's category, and the same to the target's Def or Sp. Def (0x081C8E7E to 0x081C900C); Orichalcum Pulse (280) Atk x4/3
  in sun, Hadron Engine (281) Sp. Atk x4/3 in Electric Terrain (0x081C93D6 to 0x081C9440); Sand Force (147) x13/10 power for
  Rock, Ground and Steel moves in sandstorm (0x081C971C). HnS: the same multipliers. **Same.**
- **C9. Weather damage** (0x081C0AA8): 1/16 max HP (`lsrs r0,r0,#4` 0x081C0B72). Sandstorm spares Rock, Ground, Steel (first
  two types only), Sand Veil, Sand Rush, Sand Force, Overcoat (134), Magic Guard (97) and underground or underwater mons;
  hail spares Ice in any of the three types, Ice Body (114), Snow Cloak, Slush Rush, Overcoat and Magic Guard. HnS: same
  for sandstorm and hail.

## D. Damage formula

- **D1. Physical or special by move.** Byte 4 of the move row: 0 physical, 1 special, 2 status (0x081C97C4 to 0x081C97DE).
  Photon Geyser (674) is physical when Atk > Sp. Atk; Shell Side Arm (727) when Atk x Sp. Def >= Sp. Atk x Def. Fairy is
  type 18. HnS: per move. **Same.**
- **D2. Order (CalculateBaseDamage 0x081C8204, then commands).**
  1. Unaware (108): a user with it, or moves 504/539/625, reset the target's Def and Sp. Def stages to 6; a target with it
     resets the user's Atk and Sp. Atk stages to 6, for this calculation only (0x081C8252 to 0x081C8280, restored at
     0x081C9C46).
  2. Base power (move or override), then move-specific power changes, type (dynamic type or the row's; Ion Deluge and
     Electrify turn Normal into Electric, 0x081C886C), ability power changes (Technician, Tough Claws, Iron Fist,
     Sharpness, Strong Jaw, Mega Launcher, Punk Rock, Reckless, Sheer Force, Normalize, the -ate abilities, Steelworker,
     Steely Spirit, Dragon's Maw, Rocky Payload, Transistor x1.3, Water Bubble, Stakeout, auras, Charge, Supreme Overlord,
     ally Power Spot, Battery, Friend Guard, Analytic, Rivalry), terrain.
  3. Stat changes on raw stats: Huge Power and Pure Power, Defeatist, badges, type items, Choice Band, Soul Dew, Deep Sea
     items, Light Ball, Metal Powder, Thick Club, Thick Fat, Hustle, Slow Start, Plus and Minus, Guts, Gorilla Tactics,
     Orichalcum Pulse, Hadron Engine, Marvel Scale, Solar Power, Flower Gift, Flare Boost, Toxic Boost, Heatproof, Fur
     Coat, Grass Pelt, Ice Scales, Water Bubble; Mud Sport and Water Sport; pinch abilities; Sand Force; Explosion.
  4. damage = Atk' x power x (2 x level / 5 + 2) / Def' / 50 (0x081C988C to 0x081C9902), with stages applied through the
     table at 0x0829F901 (10/40 ... 10/10 ... 40/10).
  5. Physical: burn /2 unless Guts, Reflect, spread cut, then minimum 1. Special: Light Screen, spread cut, weather,
     Flash Fire. Mystery type gives 0. Then **+2** (0x081C9C60).
  6. Cmd_damagecalc (0x081B0C40): x gCritMultiplier x damage multiplier, Sniper, Charge x2, Helping Hand x15/10.
  7. Cmd_typecalc (0x081B0F34): STAB, Ground immunity, the type chart one type at a time (each `x mult / 10`), then
     Filter, Solid Rock, Prism Armor, Collision Course, Tinted Lens, Neuroforce, Parental Bond, Adaptability, Multiscale,
     Shadow Shield, Purifying Salt, Fluffy, Dry Skin, Wonder Guard.
  8. Cmd_adjustnormaldamage (0x081B2044): random roll, Glaive Rush x2, Focus Band and Sturdy survival.
  HnS: base + 2, then spread, weather, crit, Glaive Rush, random, then STAB, type, burn, screens and others
  (DoMoveDamageCalcVars, battle_util.c:7757). **Differs in order.**
- **D3. STAB x1.5** when the move type equals type 1, type 2, or the third type (third type ignored if 9)
  (0x081B0FB4 to 0x081B0FF4, `lsls r0,r1,#4; subs; movs r1,#10` = x15/10). **Adaptability (90)** adds x4/3 after the type
  chart (0x081B15EA to 0x081B161C), 2.0 in all. HnS: 1.5 and 2.0 (battle_util.c:7430). **Same totals.**
- **D4. Burn** halves physical damage unless the user has Guts (62) (`movs r1,#16; ands` on status1, `cmp r5,#62`
  0x081C9904 to 0x081C991C). There is no Facade exception in this code. HnS: same with a Facade exception. **Same
  except Facade.**
- **D5. Screens.** Reflect (side bit 0x01) for physical, Light Screen (0x02) for special: /2, or x2/3 in a double battle
  when two mons are alive on the target's side (`bl 0x81c9c78` with 2, `cmp r0,#2`, `/3, x2` 0x081C9928 to 0x081C9978 and
  0x081C9AB0 to 0x081C9B08); skipped on a crit or for an Infiltrator (140) user. Aurora Veil (656) sets both bits for 5
  turns (SetMoveEffect 0x081B48EC to 0x081B4922). HnS: 0.5 singles, 0.667 doubles whatever the count. **Same in singles.**
- **D6. Spread moves.** In doubles, a move targeting both foes (8) or all others (0x20) is halved when two mons are alive on
  the target's side (0x081C997A to 0x081C99B0, 0x081C9B0A to 0x081C9B40). HnS (B_MULTIPLE_TARGETS_DMG GEN_3): x0.5 at
  two targets, no cut at three or more (battle_util.c:7406). **Differs when a move hits three.**
- **D7. Random roll** 85 to 100 percent: damage x (100 - (Random() & 15)) / 100, minimum 1, applied last (0x081B2046 to
  0x081B2074). HnS: 85 to 100, applied before STAB and type. **Differs in position.**
- **D8. Explosion, Self-Destruct and Misty Explosion** (effect 7): the target's Defense is halved (`cmp r0,#7; lsls r0,r1,#15;
  lsrs r0,r0,#16` 0x081C9760 to 0x081C9768; Misty Explosion is special, so it has no effect there). HnS: no halving
  (B_EXPLOSION_DEFENSE GEN_LATEST, battle_util.c:7257). **Differs.**
- **D9. Type chart** at 0x082A2284 (triples attacker, defender, x10 multiplier; 0xFE separates the Normal and Fighting vs
  Ghost rows, 0xFF ends): the complete Gen 6 chart. Fairy (18) attacks Dragon, Fighting, Dark x2 and is resisted by Fire,
  Poison, Steel; Dragon does 0 to Fairy; Poison and Steel hit Fairy x2; Fighting, Bug and Dark are resisted by Fairy.
  Ghost and Dark are neutral on Steel (no such rows). Foresight, Scrappy (112) and Mind's Eye (190) skip the rows after
  0xFE (0x081B11C0 to 0x081B11FE). The third type is applied when it differs from the first two (0x081B12F4 to
  0x081B1328). Freeze-Dry (effect 245) x2 on Water; Flying Press (566) adds Flying; Thousand Arrows and grounded Flying
  (Gravity or the 0x08000000 bit) neutral; Miracle Eye lets Psychic hit Dark; strong winds turn super effective on Flying
  into neutral (0x081B1234 to 0x081B12A8, 0x081B1340). HnS: same chart. **Same.**
- **D10. Badge boosts** for the player's side, not in link or 0x800 battles: Boulder Badge (flag 0x820) Atk x1.1, Soul
  Badge (0x824) Def x1.1, Volcano Badge (0x826) Sp. Atk and Sp. Def x1.1 (0x081C90C4 to 0x081C919A), Thunder Badge (0x822)
  Speed x1.1 (0x08015D02). HnS: badge boosts on too (B_BADGE_BOOST GEN_3, battle_util.c:9165), with its own badges.
  **Same mechanism.**
- **D11. Terrain** x13/10 for Electric, Grass and Psychic moves of a grounded user in their terrain; Misty Terrain halves
  Dragon moves against a grounded target (0x081C8DB8 to 0x081C8E6A). "Grounded" here is only: no Flying in the first two
  types and no Levitate (26); the third type, Gravity, Magnet Rise and Smack Down are not consulted. HnS: x1.3
  (B_TERRAIN_TYPE_BOOST GEN_LATEST). **Same multiplier.**
- **D12. Type-boosting items** (table 0x0829F920, 18 pairs of hold effect and type): Atk (physical) or Sp. Atk (special)
  x (100 + param) / 100 (0x081C919C to 0x081C920A). Param 10 for Charcoal and the rest and Fairy Feather (226), 5 for Sea
  Incense. HnS: x1.2 (I_TYPE_BOOST_POWER GEN_LATEST). **Differs.**
- **D13. Species items.** Choice Band Atk x1.5; Soul Dew Sp. Atk and Sp. Def x1.5 for Latias and Latios (407, 408; not in
  battle-type 0x100); DeepSeaTooth Sp. Atk x2 and DeepSeaScale Sp. Def x2 for Clamperl (373); **Light Ball Sp. Atk x2
  only** for Pikachu (25); Metal Powder Def x2 for Ditto (132); Thick Club Atk x2 for Cubone and Marowak (104, 105; not
  Alolan Marowak 1118) (0x081C9078 to 0x081C92FC). HnS: Light Ball doubles Attack too (B_LIGHT_BALL_ATTACK_BOOST
  GEN_LATEST). **Differs for Light Ball.**
- **D14. Charge (268) x4.** setcharge (0x081C3CEC) sets gStatuses3 0x200. CalculateBaseDamage doubles the power of an
  Electric move (resolved type) while it is set (0x081C8BA4 to 0x081C8BC2), and Cmd_damagecalc doubles the damage again
  when the row's type is Electric (0x081B0CC0 to 0x081B0CF2). HnS: x2 once (battle_util.c:6638). **Differs.**
- **D15. Flash Fire** (activation flag in gBattleResources flags) x1.5 for **special** Fire moves only (0x081C9C1E to
  0x081C9C44, inside the special branch). HnS: every Fire move (battle_util.c:7010). **Differs.**
- **D16. Mud Sport and Water Sport** halve Electric and Fire power (field-sport checks 0xFD and 0xFE, 0x081C9618 to
  0x081C965E). HnS: about x0.33 (B_SPORT_DMG_REDUCTION GEN_LATEST). **Differs.**
- **D17. Pinch abilities.** Overgrow, Blaze, Torrent, Swarm: power x1.5 at HP <= max HP / 3 (0x081C9660 to 0x081C971A).
  HnS: x1.5 on the attacking stat, same result. **Same.**
- **D18. Defensive ability multipliers** in typecalc: Filter (110) and Solid Rock (115) x3/4 on super effective (ignored
  by Mold Breaker 103, Neutralizing Gas 175, Turboblaze 207, Teravolt 208 and moves 666, 667, 674), Prism Armor (171) x3/4,
  Multiscale (129) and Shadow Shield (243) /2 at full HP, Purifying Salt (178) /2 Ghost, Fluffy (167) /2 contact and x2
  Fire, Dry Skin (86) x5/4 Fire; attacker Tinted Lens (109) x2 on not very effective, Neuroforce (244) x5/4 super
  effective, Parental Bond (215) second hit x1/4, Collision Course and Electro Drift (800, 801) x5461/4096 super
  effective (0x081B1436 to 0x081B1736). HnS: same set. **Same.**
- **D19. Minimum damage** 1 for a damaging move that connects (0x081B1736 to 0x081B175E); Mystery-type (9) moves do 0
  (0x081C99BC).

## E. How abilities become visible

- **E1. There is an ability pop-up.** CreateAbilityPopUp (0x08050BCC; loads the pop-up sprite sheets at 0x082A96DC and
  0x082A96E4, reads the name from gAbilityNames 0x082A4134) has exactly two callers in 0x08000000 to 0x08240000:
  1. `various 15, 13`: Cmd_various case 13 (jump table 0x081BDA54 entry 13 = 0x081BDF12) calls it with
     gBattlerAbility (0x02022B50), that battler's ability and the double-battle flag. Battler id 15 in scripts is
     gBattlerAbility (GetBattlerForBattleScript 0x081A4000, entry 15 = 0x081A40A4). This `various 15, 13` appears once,
     in BattleScript_AbilityPopUp.
  2. Cmd_resultmessage at 0x081B2DC0: when the move missed and the miss type byte (0x02023ED0) is above 2, it calls the
     pop-up for **gBattlerTarget** directly, without writing gBattlerAbility. Wonder Guard writes miss type 3 (typecalc
     0x081B1818) and Levitate writes the move type 4 (typecalc 0x081B1156 to 0x081B115C).
- **E2. BattleScript_AbilityPopUp = 0x081DFD04:** `various 15, 13` (0x081DFD04), `pause 0x1F` (0x081DFD07),
  `recordlastability 15` (0x081DFD0A), `setbyte 0x02024032, 0` and `setbyte 0x02024033, 0` (0x081DFD0C, 0x081DFD12),
  `return` (0x081DFD18). The pop-up shows the u16 at 0x02024032 when it is nonzero, else the battler's current ability
  (`ldrh r0,[r1,#38]; cmp r0,#0` on gBattleScripting, 0x08050BE2 to 0x08050BEC).
- **E3. 79 script call sites** (`call 0x081DFD04`, bytes `41 04 FD 1D 08`), no `goto`s and no C code that pushes 0x081DFD04:
  0x081DCB38, 0x081DD339, 0x081DD7B2, 0x081DDB81, 0x081DDB8D, 0x081DDB99, 0x081DDBA5, 0x081DDBB1, 0x081DDBCA, 0x081DDBE3,
  0x081DDBEF, 0x081DDEF2, 0x081DE7E4, 0x081DEAD1, 0x081DFCC5, 0x081DFCCE, 0x081DFCE7, 0x081DFD19, 0x081DFD2C, 0x081DFD3B,
  0x081DFD54, 0x081DFD6E, 0x081DFD87, 0x081DFDA0, 0x081DFDB4, 0x081DFDBD, 0x081DFDC9, 0x081DFDD5, 0x081DFDE1, 0x081DFDED,
  0x081DFDF9, 0x081DFE23, 0x081DFE5D, 0x081DFE77, 0x081DFEFF, 0x081DFF12, 0x081DFF49, 0x081DFF63, 0x081DFF78, 0x081DFF88,
  0x081DFF94, 0x081DFFA6, 0x081DFFCB, 0x081DFFE3, 0x081DFFF5, 0x081E0005, 0x081E0019, 0x081E003F, 0x081E0065, 0x081E0083,
  0x081E0093, 0x081E00A3, 0x081E00B3, 0x081E00C1, 0x081E00D3, 0x081E00E3, 0x081E00EE, 0x081E0121, 0x081E0144, 0x081E0159,
  0x081E0165, 0x081E0380, 0x081E039C, 0x081E03DB, 0x081E040D, 0x081E0434, 0x081E045B, 0x081E0482, 0x081E04BF, 0x081E04F6,
  0x081E052D, 0x081E053F, 0x081E054F, 0x081E055B, 0x081E057B, 0x081E058E, 0x081E0596, 0x081E059D, 0x081E05D5.
- **E4. Abilities whose pop-up script was tied to the code that runs it.** Read from AbilityBattleEffects' compare chains
  (switch-in chain 0x081A8424 to 0x081A8560, end-of-turn chain 0x081A8F10 to 0x081A8F78) and jump tables (MOVE_END: ability -
  9 into 0x081AA2CC, 277 entries; IMMUNITY: ability - 7 into 0x081ABF8C, 66 entries), and from ability compares next to the
  other loaders. Format: ability, script, code that loads it.
  - Switch-in: Neutralizing Gas 0x081DFCE7 (0x081A8588); Hospitality 0x081E0165 (0x081A8610); Drizzle 0x081DFCCE
    (0x081A873C); Sand Stream 0x081DFD6E (0x081A8778); Drought and Orichalcum Pulse 0x081DFF49 (0x081A87B4); Hadron
    Engine, Electric Surge, Grassy Surge, Misty Surge, Psychic Surge 0x081DFCC5 (0x081A87F0, 0x081A88F0, 0x081A8928,
    0x081A8960, 0x081A8998); Snow Warning 0x081DFD87 (0x081A8828); Slow Start 0x081DDBE3 (0x081A886E); Screen Cleaner
    0x081DDBEF (0x081A88B4); Delta Stream 0x081DFDBD (0x081A89D4); Supersweet Syrup 0x081E04F6 (0x081A8A70); Download
    0x081E040D (0x081A8AFA); Dauntless Shield 0x081E040D (0x081A81B0, `cmp r0,#246` 0x081A8152).
  - End of turn: Rain Dish 0x081DFD3B (0x081A8FD0); Ice Body 0x081DFD3B (0x081A90EC); Dry Skin 0x081DFD3B and 0x081DFD54
    (0x081A9160, 0x081A91F6); Solar Power 0x081DFD54 (0x081A926C); Shed Skin 0x081DFE23 (0x081A9330); Hydration 0x081DFE23
    (0x081A9446); Speed Boost 0x081DFD19 (0x081A94C6); Moody 0x081E040D (0x081A9688).
  - Move blocking: Soundproof 0x081E00BF (0x081A971A); Bulletproof 0x081E00BF (`cmp r0,#152` 0x081A9744, loader
    0x081A9842); Telepathy 0x081E00D1 (`cmp r0,#203` 0x081A985C); Armor Tail, Queenly Majesty, Dazzling 0x081E00BF
    (0x081A98D8 to 0x081A98E2, loader 0x081A9910); Good as Gold 0x081E00BF (`cmp r0,#181` 0x081A9934).
  - Absorbing (chain 0x081A9AB4): Volt Absorb, Water Absorb, Dry Skin, Flash Fire, Lightning Rod, Motor Drive, Storm Drain,
    Sap Sipper, Well-Baked Body, Earth Eater use 0x081DFFA5/0x081DFFA4, 0x081DFFE2/0x081DFFE1 and 0x081E03DA/0x081E03D9
    (loaders 0x081A9B4A to 0x081A9E06); the shared "no effect" ending 0x081DFFCA/0x081DFFC9 (0x081A9E40, 0x081A9E62).
  - After a hit (MOVE_END table): Color Change 0x081E054F; Stamina, Berserk, Justified, Rattled, Water Compaction, Steam
    Engine, Thermal Exchange, Anger Point 0x081E045B; Anger Shell 0x081E039C; Cotton Down 0x081E04BF; Gooey and Tangling
    Hair 0x081E04BF; Weak Armor 0x081E0380; Rough Skin, Iron Barbs, Aftermath, Innards Out 0x081E055B; Effect Spore, Poison
    Point, Static, Flame Body 0x081E058D; Toxic Debris 0x081DDB99; Cursed Body 0x081DDB8D; Cute Charm 0x081E057B; Mummy and
    Lingering Aroma 0x081DFDED; Wandering Spirit 0x081DFDF9; Perish Body 0x081DDB81; Sand Spit 0x081DFDA0; Seed Sower
    0x081DFDB4; Electromorphosis and Wind Power 0x081DDBA5. Pickpocket 0x081E00EE (`cmp r0,#199` 0x081A9F9E) and Magician
    0x081E00E3 (`cmp r0,#212` 0x081AA130) run before the table.
  - Others: Oblivious (immunity table) 0x081E059D; Synchronize 0x081E0595 (0x081AC3A4, 0x081AC44E); Truant 0x081E05D5
    (`cmp r0,#54` 0x081A72CA); Sturdy against OHKO 0x081DFF78 (tryKO 0x081C06EC); Sturdy surviving at full HP 0x081DFF88
    (adjustnormaldamage `cmp r0,#5` 0x081B222E, then resultmessage 0x081B2E62); Damp 0x081DFF94 (faintifabilitynotdamp
    0x081BE75E); Suction Cups and Guard Dog 0x081DFFF5 (`cmp r0,#21`, `cmp r0,#180` 0x081BFFA2 to 0x081BFFA6); Keen Eye,
    Illuminate, Mind's Eye (accuracy drops) and Hyper Cutter (attack drops) 0x081E052D (0x081BF7BE, 0x081BF826); Oblivious
    against Attract 0x081E0083 (`cmp r2,#12` 0x081C0D44); Inner Focus 0x081E0093 (`cmp r1,#39` 0x081B3FC8); Own Tempo
    0x081E00B3 (`cmp r1,#20` 0x081B3F14); Sticky Hold 0x081E053F (`cmp r0,#60` at 0x081B6B5A, 0x081B6F10, 0x081C4176);
    Levitate and Wonder Guard through resultmessage (E1).
- **E5. RecordAbilityBattle (0x080D933C) is not a reveal signal.** It stores an ability only for a player-side battler
  (`bl 0x807c130` GetBattlerSide, skip when nonzero), into the AI's battle history. `recordlastability` and the Levitate,
  Wonder Guard and Sturdy paths call it.
- **E6. Silent ability actions found:** Harvest (end-of-turn block 0x081A950C) and Healer (0x081A9000) change the mon through
  `BtlController_EmitSetMonData` (`bl 0x800dfec` 0x081A95B6, 0x081A9074) with no script, so no pop-up and no message.
  Intimidate, the four Ruin abilities, Anticipation, Forewarn, Frisk, Forecast, Trace and Cloud Nine/Air Lock have switch-in
  blocks (0x081A8B0C, 0x081A8B40, 0x081A8BF8, 0x081A8CD4, 0x081A8DB4, 0x081A8E50, 0x081A8E7C, 0x081A8EB8) that load no
  pop-up script themselves (see Unsure).
- **E7. For a tracker:** an opponent's ability is shown when (a) gBattlescriptCurrInstr is inside 0x081DFD04..0x081DFD18:
  battler = gBattlerAbility (0x02022B50), ability = u16 at 0x02024032 if nonzero, else gBattleMons[battler].ability; or
  (b) resultmessage reports a miss with miss type 3 (Wonder Guard) or 4 (Levitate): battler = gBattlerTarget (0x02023D7D).
  This matches what MaxDex's own tracker extension watches for (a) (docs/research/maxdex.md: gBattlerAbility 0x02022B50,
  BattleScript_AbilityPopUp 0x081DFD04) and adds (b). HnS: four contiguous pop-up scripts and one C caller
  (docs/ABILITY-POPUPS.md). **Same idea, different addresses and a second path.**

## F. What works and what looks unfinished

- **F1. Ability ids are real.** The ROM's name table (0x082A4134, 17 bytes, entries 0 to 289) matches the tracker's
  `abilities.tsv` slot for slot; 76 is the unused "-------". Every ability this document names was found compared in code.
- **F2. Items are FireRed's.** No Gen 4+ battle items exist; Razor Claw is a Scope Lens (hold effect 41), Razor Fang a
  King's Rock (30), Fairy Feather a type booster (67). Item-dependent abilities and moves (Klutz, Unburden, Knock Off
  boosts, Acrobatics with no item: effect 239 doubles power when the item is 0, 0x081C82E8) only ever see these items.
- **F3. New move effects are mostly handled by move id.** Many new moves use effect 110, 184 or 248 and SetMoveEffect
  (0x081B3194) switches on gCurrentMove (Gravity 0x081B4D90, Lucky Chant 0x081B4800, Laser Focus 0x081B495C, Miracle Eye
  0x081B4D68, Aurora Veil 0x081B48EC, Glaive Rush 0x081B5384). Command F8 (0x081C6064) holds move-specific fail checks
  (for example Aurora Veil needs hail with weather in effect, 0x081C62A0 to 0x081C62D2).
- **F4. Behaviors that look unintended:** Charge x4 (D14); physical Fire and Water moves ignoring weather and Flash Fire
  (C2, D15); Solar Blade never weather-halved (C3); the crit block on gStatuses3 0x8000 with no writer
  (B11); Normalize boosting only moves that were not already Normal (`ldrb r0,[r0,#3]; cmp r0,#0; beq` skip, 0x081C8A12
  to 0x081C8A36); Sand Force working under Cloud Nine (C6); the third type ignored by sandstorm immunity (C9) and by the
  terrain grounding test (D11); Healer and Harvest silent (E6).
- **F5. Not done:** a full sweep of all 289 abilities and 841 moves for placeholders. Moves whose new effect has no code
  could not be listed from this pass.

## Unsure

- **Dire Hit.** The item effect that sets Focus Energy from a Dire Hit was not located (no `0x100000` OR into status2 found
  outside setfocusenergy and two SetMoveEffect sites). Vanilla FireRed sets the same bit (+2); not confirmed here.
- **gStatuses3 0x8000** (B11): read three times in critcalc, never found written (`movs #128; lsls #8` OR patterns and
  literal 0x8000 searched in 0x08010000 to 0x08030000, 0x080D0000 to 0x080E0000, 0x081A7000 to 0x081C8000, and the
  script `orword` form). If nothing sets it, the rule never fires.
- **Pop-up coverage.** Script call sites not tied to an ability: 0x081DCB38, 0x081DD339, 0x081DD7B2, 0x081DDEF2,
  0x081DE7E4, 0x081DEAD1, 0x081DFD2C, 0x081DFE5D, 0x081DFE77, 0x081DFEFF, 0x081DFF12, 0x081DFF63, 0x081E0005, 0x081E0019,
  0x081E003F, 0x081E0065, 0x081E00A3, 0x081E0121, 0x081E0144, 0x081E0159, 0x081E0434, 0x081E0482 (reached from other
  scripts or from code at 0x081AF91C attackcanceler, 0x081BF734, 0x081B953E, 0x081BA2DE, 0x081BD092, 0x081B74DC,
  0x081AC9F8, 0x081ACA8C). Intimidate, Ruin, Anticipation, Forewarn, Frisk, Trace and Cloud Nine probably reach the pop-up
  through these; not traced.
- **MOVE_END preamble 0x081A9E94:** sets gBattlerAbility to the hit battler, and for a contact hit with a per-battler flag
  in gBattleStruct + 0x205 pushes 0x081E058D (which calls the pop-up) with move effect 67, with no ability compare in
  between. Which effect owns that flag (Beak Blast is a guess) and whether it shows the holder's ability was not checked.
- **Ability id 255 (Power Spot).** The switch-in chain sends 255 to the overworld-weather block (`cmp r3,#255`
  0x081A852C, block 0x081A8640). Whether a Power Spot mon's own switch-in reaches that chain (the 255 marker colliding with
  the u16 ability id) was not traced.
- **HnS comparisons marked "not compared"** (A20, A21, C6 Sand Force) were not read from HnS source.
- **Sheer Cold and Ice users:** the code reads "chance = 20 + level difference when the user has no Ice type" against
  "move accuracy (30) + level difference" otherwise; the Sheer Cold row's accuracy is 30.

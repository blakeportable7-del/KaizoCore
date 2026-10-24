# Heart & Soul on Nat. Dex Emerald 1.2.1 ability rules: what config cannot reach

Goal: the Heart & Soul (pokeemerald-expansion) Kaizo build plays Nat. Dex Emerald 1.2.1's battle mechanics. The
B_ settings in `include/config/battle.h` are Gen 3 (type chart and move types Gen 6). This file lists every ability
behavior in the pool of 68 that Heart & Soul's code applies beyond Gen 3, says which B_ setting already gates it, and
gives an exact `KAIZOCORE_GEN3_BATTLE` gate for the rest. Report and proposal only: no source file was edited.

Source read: `/root/hns/src-gen3mech`, branch `kaizocore-gen3-battle`, commit `f4305974` ("KaizoCore: Nat. Dex
Emerald's Gen 3 battle rules"). Every file:line below is at that commit. The worktree is being edited by another agent
(it had an uncommitted `src/data/moves_info.h` change while this was written), so apply the hunks by their context,
not by line number. `KAIZOCORE_GEN3_BATTLE` is defined TRUE in `include/config/battle.h`, which every C file and
`data/battle_scripts_1.s` already include (through `constants/global.h`), so no new `#include` is needed.

Evidence key: **ND** = Nat. Dex Emerald 1.2.1 ROM address (`emerald-natdex-121.gba`; A1 to F6 refer to sections of
`docs/HACK-RULES-NATDEX.md`); **PE** = vanilla pokeemerald behavior (pret source), which Nat. Dex keeps unless shown
otherwise. AI files (`battle_ai_*.c`) were not reviewed: they choose moves, they do not resolve them. Pop-up presence
alone is not counted as a difference.

## Summary

- **36 of the 68 abilities** play differently from Gen 3 when the B_ settings are left at GEN_LATEST; 2 more (Cloud
  Nine, Air Lock) differ only in what is announced.
- **24** of the 36 are fully handled by the B_ settings that commit `f4305974` already sets to GEN_3 (listed per row;
  Huge Power, Pure Power, Guts and Marvel Scale also need H0 for Ghost and Dark moves).
- **12 abilities still differ with every B_ setting at GEN_3** and need code: Stench, Pickup, Keen Eye, Hustle, Volt
  Absorb, Water Absorb, Drizzle, Drought, Sand Stream, Pressure, Shield Dust, Trace. That is **11 required hunks**
  (H1 to H11 below; Drizzle, Drought and Sand Stream share H7, Volt and Water Absorb share H6).
- **Information only** (what the player is told, not what happens): the switch-in announcement of Pressure, Cloud
  Nine and Air Lock, and Sturdy's OHKO message order. Optional hunks O1 to O3.
- **One finding outside abilities matters more than any of them (H0):** Heart & Soul's `src/data/types_info.h` marks
  Ghost SPECIAL and Dark PHYSICAL. With the Gen 3 by-type split that the commit turns on, Shadow Ball, Shadow Punch,
  Lick, Astonish and Shadow Sneak are special and Crunch, Bite, Faint Attack, Pursuit, Thief and Knock Off are physical.
  Nat. Dex Emerald (and Gen 3) is the opposite: type id <= 8 is physical (Ghost is 7), above 9 special (Dark is 17)
  (ND CalculateBaseDamage `cmp r1,#8; bls physical` 0x080699B2, D1). It also feeds Hustle, Huge Power, Pure Power,
  Guts and Marvel Scale.

## Table

| Ability | Heart & Soul behavior beyond Gen 3 | Gated by (config, value) or UNGATED file:line | Proposed change | Evidence |
|---|---|---|---|---|
| Stench | 10% flinch on the attacker's damaging hits (Gen 5). King's Rock is skipped for a Stench holder because of it. | UNGATED `battle_util.c:4378` (ABILITYEFFECT_MOVE_END_ATTACKER), `battle_hold_effects.c:215` | H1, H2 | PE: Stench has no battle code in Gen 3 (only halves wild encounters). ND E4: never shown. |
| Drizzle | (1) Fails when rain from Rain Dance is already up, so that rain still ends on the move's timer. (2) With B_ABILITY_WEATHER at GEN_3 the new weather keeps `weatherDuration` left over from an earlier move weather, so "permanent" rain can end after a few turns. | B_ABILITY_WEATHER GEN_3 for the 5-turn limit; the rest UNGATED `battle_util.c:2118`, `:2129-2132` (TryChangeBattleWeather) | H7 | ND 0x080448D4 `weather & 4` (permanent rain only) then `strh #5` at 0x080448E0: temporary rain becomes permanent. |
| Speed Boost | None (not on the turn it entered, not at +6, as Gen 3). | none needed | none | PE `isFirstTurn != 2`. ND E4. |
| Battle Armor | None. | none needed | none | ND 0x0804940E `cmp r4,#4` (B7). |
| Sturdy | (1) Survive at 1 HP from full (Gen 5). (2) Information: the level-gap test runs before Sturdy, so a higher-level Sturdy target shows "unaffected" with no Sturdy message. | (1) B_STURDY GEN_3 (also the challenge row `tx_Mode_Sturdy`, set OLD by `new_game.c`). (2) UNGATED `battle_util.c:10629-10639` | O3 (optional, information only) | ND Cmd_tryKO: Sturdy `cmp r4,#5` at 0x08054678 before the level compare at 0x0805472A (A19). |
| Damp | None (Self-Destruct, Explosion; Aftermath not in a Gen 3 pool). | none needed | none | PE Cmd_tryexplosion checks the whole field. |
| Limber | None. | none needed | none | PE. |
| Sand Veil | None (x0.8 accuracy in sand, sand damage immunity). | none needed | none | ND 0x08048F98 (A10), 0x08054A0C (C6). |
| Static | 30% instead of 1/3; Electric types immune to its paralysis (Gen 6). | B_ABILITY_TRIGGER_CHANCE GEN_3, B_PARALYZE_ELECTRIC GEN_3 | none | PE `Random() % 3 == 0`. |
| Volt Absorb | Absorbs status Electric moves too (Thunder Wave fails on it, Gen 4+). | UNGATED `battle_util.c:2447-2449` (CanAbilityAbsorbMove) | H6 | ND 0x08044D7E type 13 then `ldrb r0,[row,#1]; cmp r0,#0` power test at 0x08044D8E: power 0 is never absorbed. |
| Water Absorb | Same as Volt Absorb for status Water moves. | UNGATED `battle_util.c:2451-2454` | H6 | ND 0x08044DD8 type 11, power test 0x08044DE8. |
| Oblivious | Blocks Taunt (Gen 6) and Intimidate (Gen 8); cures Taunt. | B_OBLIVIOUS_TAUNT GEN_3, B_UPDATED_INTIMIDATE GEN_3 | none | PE: Oblivious blocks Attract and Cute Charm only. |
| Cloud Nine | Information: announces itself on switch-in ("effects of weather disappeared"). Suppression itself matches Gen 3. | UNGATED `battle_util.c:3476-3482` | O2 (optional) | ND E4: never shown. Weather checks ND C7. |
| Compound Eyes | None (x1.3). | none needed | none | ND 0x08048F48 (A8). |
| Insomnia | None (blocks sleep, Yawn, own Rest; cures sleep). | none needed | none | PE. |
| Color Change | None (damaging hit of a type it lacks, not Struggle, not through a Substitute). | none needed | none | PE ABILITYEFFECT_MOVE_END. |
| Immunity | None. | none needed | none | PE. |
| Flash Fire | Activates while frozen (Gen 5). Boost applied to Attack, not to damage: rounding only. | B_FLASH_FIRE_FROZEN GEN_3 | none | ND 0x08044E30 type 10, freeze test `status1 & 0x20` at 0x08044E40, no power test (Will-O-Wisp triggers it, as in HnS). |
| Shield Dust | Also stops Smelling Salt's paralysis cure on a single target. Everything else it blocks on Gen 3 moves (statuses, confusion, flinch, Tri Attack, added stat drops, King's Rock) matches Gen 3. | UNGATED `battle_script_commands.c:3522` (MOVE_EFFECT_REMOVE_STATUS) | H9 | PE SetMoveEffect: Shield Dust blocks move effects `<= 9` (statuses, confusion, flinch, Tri Attack); ChangeStatBuffs blocks added drops (`ABILITY_SHIELD_DUST && flags == 0`); Smelling Salt's REMOVE_PARALYSIS is above 9 and lands. Every Gen 1-3 added effect in HnS `moves_info.h` is one of the blocked kinds. |
| Own Tempo | Blocks Intimidate (Gen 8). | B_UPDATED_INTIMIDATE GEN_3 | none | PE. |
| Suction Cups | None for Gen 3 moves (Roar, Whirlwind). | none needed | none | PE. |
| Intimidate | Blocked by Inner Focus, Own Tempo, Oblivious, Scrappy (Gen 8). Substitute still blocks it, as in Gen 3. | B_UPDATED_INTIMIDATE GEN_3 | none | PE BattleScript_IntimidateActivates. |
| Rough Skin | 1/8 instead of 1/16. | B_ROUGH_SKIN_DMG GEN_3 | none | PE `maxHP / 16`. |
| Levitate | Ingrain grounds it (Gen 4). | B_ROOTED_GROUNDING GEN_3 | none | PE. |
| Effect Spore | 30% (Gen 4) or 11/9/10% (Gen 5); Grass types immune (Gen 6). | B_ABILITY_TRIGGER_CHANCE GEN_3 (gives 10/300 each, 10% total), B_POWDER_GRASS GEN_3, B_POWDER_OVERCOAT GEN_3 | none | PE 10%, then 1/3 each. ND source note (Unsure 3): same 10% total. |
| Synchronize | Passes bad poison as bad poison (Gen 5). | B_SYNCHRONIZE_TOXIC GEN_3 | none | PE converts Toxic to poison. |
| Clear Body | None (blocks foe drops not marked certain; Curse excluded). | none needed | none | PE ChangeStatBuffs `!certain && gCurrentMove != MOVE_CURSE`. |
| Natural Cure | None (cures on switch-out only). | none needed | none | PE Cmd_switchoutabilities. |
| Lightning Rod | Electric immunity and Sp. Atk +1 (Gen 5); redirects the ally's moves (Gen 4). | B_REDIRECT_ABILITY_IMMUNITY GEN_3, B_REDIRECT_ABILITY_ALLIES GEN_3 | none | PE: redirects foes' single-target Electric moves in doubles, no immunity. |
| Serene Grace | Doubles King's Rock's flinch (Gen 5). | B_SERENE_GRACE_BOOST GEN_3 | none | PE. |
| Swift Swim | None (x2 speed in rain). | none needed | none | ND 0x0803E970 (C8). |
| Chlorophyll | None (x2 speed in sun). | none needed | none | ND 0x0803E970 (C8). |
| Illuminate | Ignores evasion and blocks accuracy drops (Gen 9). | B_ILLUMINATE_EFFECT GEN_3 | none | PE: no battle effect. |
| Trace | (1) Runs the traced ability's switch-in effect (a traced Intimidate cuts Attack, a traced Drizzle starts rain) (Gen 4+). (2) Cannot trace Trace, and Role Play cannot copy Trace. (3) Role Play's copied ability also runs its switch-in effect. | UNGATED `data/battle_scripts_1.s:6101` (`switchinabilities` in BattleScript_TraceActivates), `src/data/abilities.h:283-284` (`cantBeCopied`, `cantBeTraced` hardcoded TRUE; the comment says B_UPDATED_ABILITY_DATA but the value ignores it), `data/battle_scripts_1.s:3722` (Role Play) | H10, H11 | ND BattleScript_TraceActivates (bytes at 0x082DF74B to 0x082DF770): pop-ups, `printstring 0xD0` (PKMNTRACED), `waitmessage`, `end3`, no switch-in command. PE Trace and Role Play copy any nonzero ability except (Role Play) Wonder Guard. |
| Huge Power | Doubles Attack for physical moves by category. | B_PHYSICAL_SPECIAL_SPLIT GEN_3 (plus H0 for Ghost and Dark moves) | H0 | ND 0x080695E8 (D12). |
| Poison Point | 30% instead of 1/3. | B_ABILITY_TRIGGER_CHANCE GEN_3 | none | PE. |
| Inner Focus | Blocks Intimidate (Gen 8). Flinch blocking matches Gen 3. | B_UPDATED_INTIMIDATE GEN_3 | none | PE SetMoveEffect MOVE_EFFECT_FLINCH. |
| Magma Armor | None. | none needed | none | PE. |
| Water Veil | None. | none needed | none | PE. |
| Soundproof | Howl is a sound move (Gen 8); Uproar ignores Soundproof (Gen 5); Heal Bell and Perish Song rules by generation. Later sound moves (Bug Buzz, Boomburst...) are blocked too; Nat. Dex has none of them. | B_UPDATED_MOVE_FLAGS GEN_3 (Howl), B_UPROAR_IGNORE_SOUNDPROOF GEN_3, B_HEAL_BELL_SOUNDPROOF GEN_3, B_CHECK_USER_FAILURE GEN_3 | none (see "Left alone") | ND sound list 0x0838F098 (F6) = Growl, Roar, Sing, Supersonic, Screech, Snore, Uproar, Metal Sound, Grass Whistle, Hyper Voice, Disarming Voice: exactly HnS's Gen 1-3 sound moves plus Disarming Voice, once Howl drops out. PE Perish Song and Heal Bell skip Soundproof separately. |
| Rain Dish | None (1/16 in rain). | none needed | none | PE. |
| Sand Stream | As Drizzle, for sandstorm. | as Drizzle | H7 | ND 0x08044904 `weather & 0x10` then `strh #0x18` at 0x08044910. |
| Pressure | (1) Extra PP for field moves (Haze, Rain Dance, Sunny Day, Sandstorm, Hail, Mud Sport, Water Sport) used against a Pressure foe. (2) In doubles, an ally's Pressure is not counted for Earthquake-style moves and Perish Song. (3) Information: announces itself on switch-in. | (1)(2) UNGATED `battle_move_resolution.c:949-964` (CancelerPPDeduction). (3) UNGATED `battle_util.c:3309-3315` | H8; O1 (optional) | PE Cmd_ppreduce: FOES_AND_ALLY counts every other battler, BOTH and OPPONENTS_FIELD count the foe side, all else only a targeted foe. Move target bytes (vanilla and ND rows identical): Haze, Rain Dance, Sunny Day, Sandstorm, Hail, Mud Sport, Perish Song = 0x10 USER; Spikes 0x40; Earthquake 0x20. ND E4: Pressure never shown. |
| Thick Fat | None (halves Fire and Ice; HnS halves the attacking stat, Gen 3 halved Sp. Atk: same result while Fire and Ice are special). | none needed | none | ND D12. |
| Early Bird | None (sleep counter falls by 2). | none needed | none | PE. |
| Flame Body | 30% instead of 1/3. | B_ABILITY_TRIGGER_CHANCE GEN_3 | none | PE. |
| Run Away | None (always flees wild battles, ignores trapping). KaizoCore's own no-fleeing-trainer rule is not an ability rule. | none needed | none | PE IsRunningFromBattleImpossible. |
| Keen Eye | Ignores the target's evasion stages (Gen 6). | UNGATED `battle_util.c:10489` (GetTotalAccuracy) | H4 | ND: no compare with 51 in the accuracy code; index is always acc + 6 - evasion at 0x08048EB0 (A12). |
| Hyper Cutter | None. | none needed | none | PE. |
| Pickup | Picks up an item another battler used, at the end of each turn (Gen 5). After-battle pickup matches Gen 3 (10% per empty-handed holder). | UNGATED `battle_util.c:3658-3660` (ABILITYEFFECT_ENDTURN); `battle_end_turn.c:1275` calls it | H3 | PE: Pickup works only after battle (Cmd_pickup). ND E4: Pickup never shown. |
| Hustle | (1) Damage and accuracy by move category. (2) The accuracy cut skips status moves; Gen 3 cuts every move of a physical type, status moves included (Sing, Supersonic, Toxic, Sand Attack, Poison Powder, String Shot...). | (1) B_PHYSICAL_SPECIAL_SPLIT GEN_3. (2) UNGATED `battle_util.c:10527-10529` (GetBattleMoveCategory returns STATUS before the type rule) | H5 (and H0) | ND 0x08048FCA `cmp r0,#55` then `cmp r2,#8; bhi` on the move type only, no power test (A9). |
| Cute Charm | 30% instead of 1/3. Gender rule matches Gen 3 (both gendered, opposite). | B_ABILITY_TRIGGER_CHANCE GEN_3 | none | PE (both genders checked against MON_GENDERLESS). |
| Sticky Hold | None for Gen 3 moves (Thief, Covet, Knock Off, Trick). | none needed | none | PE. |
| Shed Skin | 30% instead of 1/3. | B_ABILITY_TRIGGER_CHANCE GEN_3 | none | PE `Random() % 3 == 0`. |
| Guts | Physical by category. | B_PHYSICAL_SPECIAL_SPLIT GEN_3 (and H0) | H0 | ND 0x08069854 (D3). |
| Marvel Scale | Defense by category. | B_PHYSICAL_SPECIAL_SPLIT GEN_3 (and H0) | H0 | ND D3. |
| Liquid Ooze | Hurts Dream Eater users (Gen 5). | B_DREAM_EATER_LIQUID_OOZE GEN_3 | none | PE. |
| Overgrow | None (x1.5 at 1/3 HP; HnS scales Attack, Gen 3 scaled power: rounding only). | none needed | none | ND D12. |
| Blaze | As Overgrow. | none needed | none | ND D12. |
| Torrent | As Overgrow. | none needed | none | ND D12. |
| Swarm | As Overgrow. | none needed | none | ND D12. |
| Rock Head | None (recoil moves; Struggle not covered, as in Gen 3). | none needed | none | PE. |
| Drought | As Drizzle, for sun. | as Drizzle | H7 | ND 0x08044934 `weather & 0x40` then `strh #0x60` at 0x08044940. |
| White Smoke | None. | none needed | none | PE. |
| Pure Power | As Huge Power. | B_PHYSICAL_SPECIAL_SPLIT GEN_3 (and H0) | H0 | ND 0x080695E8. |
| Shell Armor | None. | none needed | none | ND 0x0804940E (B7). |
| Air Lock | As Cloud Nine. | as Cloud Nine | O2 (optional) | ND E4, C7. |
| Vital Spirit | None (as Insomnia). | none needed | none | PE. |

## Patch hunks

Each hunk shows the lines at commit `f4305974` and their replacement. All compile with `KAIZOCORE_GEN3_BATTLE` either
TRUE or FALSE.

### H0. Ghost physical, Dark special (not an ability, but under every physical/special rule above)

`src/data/types_info.h:223` (inside `[TYPE_GHOST]`)

Before:
```c
        .damageCategory = DAMAGE_CATEGORY_SPECIAL,
```
After:
```c
        .damageCategory = KAIZOCORE_GEN3_BATTLE ? DAMAGE_CATEGORY_PHYSICAL : DAMAGE_CATEGORY_SPECIAL, // KaizoCore: Gen 3 Ghost is physical
```

`src/data/types_info.h:432` (inside `[TYPE_DARK]`)

Before:
```c
        .damageCategory = DAMAGE_CATEGORY_PHYSICAL,
```
After:
```c
        .damageCategory = KAIZOCORE_GEN3_BATTLE ? DAMAGE_CATEGORY_SPECIAL : DAMAGE_CATEGORY_PHYSICAL, // KaizoCore: Gen 3 Dark is special
```

### H1. Stench does nothing in battle

`src/battle_util.c:4378-4379` (AbilityBattleEffects, ABILITYEFFECT_MOVE_END_ATTACKER)

Before:
```c
        case ABILITY_STENCH:
            if (IsBattlerAlive(gBattlerTarget)
```
After:
```c
        case ABILITY_STENCH:
            if (!KAIZOCORE_GEN3_BATTLE // KaizoCore: Stench's 10% flinch is Gen 5; Gen 3 Stench does nothing in battle
             && IsBattlerAlive(gBattlerTarget)
```

### H2. King's Rock works for a Stench holder

`src/battle_hold_effects.c:215`

Before:
```c
    if (ability != ABILITY_STENCH && RandomPercentage(RNG_HOLD_EFFECT_FLINCH, holdEffectParam))
```
After:
```c
    if ((KAIZOCORE_GEN3_BATTLE || ability != ABILITY_STENCH) && RandomPercentage(RNG_HOLD_EFFECT_FLINCH, holdEffectParam)) // KaizoCore: no Stench flinch to stack with
```

### H3. No in-battle Pickup

`src/battle_util.c:3658-3660` (AbilityBattleEffects, ABILITYEFFECT_ENDTURN)

Before:
```c
            case ABILITY_PICKUP:
                if (gBattleMons[battler].item == ITEM_NONE
                 && PickupHasValidTarget(battler))
```
After:
```c
            case ABILITY_PICKUP:
                if (!KAIZOCORE_GEN3_BATTLE // KaizoCore: picking up a used item mid-battle is Gen 5; Gen 3 Pickup works after battle only
                 && gBattleMons[battler].item == ITEM_NONE
                 && PickupHasValidTarget(battler))
```

### H4. Keen Eye does not ignore evasion

`src/battle_util.c:10489` (GetTotalAccuracy)

Before:
```c
    if (atkAbility == ABILITY_UNAWARE || atkAbility == ABILITY_KEEN_EYE || atkAbility == ABILITY_MINDS_EYE
```
After:
```c
    if (atkAbility == ABILITY_UNAWARE || (!KAIZOCORE_GEN3_BATTLE && atkAbility == ABILITY_KEEN_EYE) || atkAbility == ABILITY_MINDS_EYE // KaizoCore: Gen 3 Keen Eye only blocks accuracy drops
```

### H5. Hustle's accuracy cut by type, status moves included

`src/battle_util.c:10527-10529` (GetTotalAccuracy). Reads the same per-type category the B_PHYSICAL_SPECIAL_SPLIT rule
uses (`GetBattleMoveCategory`, `battle_util.c:9209`), without its early return for status moves. Correct for Ghost and
Dark only with H0.

Before:
```c
    case ABILITY_HUSTLE:
        if (IsBattleMovePhysical(move))
            calc = (calc * 80) / 100; // 1.2 hustle loss
```
After:
```c
    case ABILITY_HUSTLE:
        // KaizoCore: Gen 3 cuts every move of a physical type, status moves included (Sing, Toxic, Sand Attack)
        if (KAIZOCORE_GEN3_BATTLE ? gTypesInfo[GetBattleMoveType(move)].damageCategory == DAMAGE_CATEGORY_PHYSICAL
                                  : IsBattleMovePhysical(move))
            calc = (calc * 80) / 100; // 1.2 hustle loss
```

### H6. Volt Absorb and Water Absorb take only moves with power

`src/battle_util.c:2447-2455` (CanAbilityAbsorbMove). Dry Skin is not in the pool and keeps its modern rule.

Before:
```c
    case ABILITY_VOLT_ABSORB:
        if (ctx->moveType == TYPE_ELECTRIC)
            battleScript = AbsorbedByDrainHpAbility(ctx->battlerDef);
        break;
    case ABILITY_WATER_ABSORB:
    case ABILITY_DRY_SKIN:
        if (ctx->moveType == TYPE_WATER)
            battleScript = AbsorbedByDrainHpAbility(ctx->battlerDef);
        break;
```
After:
```c
    case ABILITY_VOLT_ABSORB:
        // KaizoCore: Gen 3 absorbs only moves with power; Thunder Wave paralyzes a Volt Absorb holder
        if (ctx->moveType == TYPE_ELECTRIC && !(KAIZOCORE_GEN3_BATTLE && GetMovePower(ctx->move) == 0))
            battleScript = AbsorbedByDrainHpAbility(ctx->battlerDef);
        break;
    case ABILITY_WATER_ABSORB:
    case ABILITY_DRY_SKIN:
        if (ctx->moveType == TYPE_WATER
         && !(KAIZOCORE_GEN3_BATTLE && ctx->abilityDef == ABILITY_WATER_ABSORB && GetMovePower(ctx->move) == 0))
            battleScript = AbsorbedByDrainHpAbility(ctx->battlerDef);
        break;
```

### H7. Drizzle, Drought and Sand Stream make permanent weather, over a move's weather too

`src/battle_util.c:2118-2119` and `:2129-2132` (TryChangeBattleWeather). With B_ABILITY_WEATHER at GEN_3, weather
with `weatherDuration == 0` never ends (`EndOrContinueWeather`, `battle_util.c:252`). Move weather always sets 5 or 8,
so a nonzero duration means "came from a move".

Before:
```c
    if (gBattleWeather & sBattleWeatherInfo[battleWeatherId].flag)
        return FALSE;
```
After:
```c
    // KaizoCore: Gen 3 Drizzle, Drought and Sand Stream turn the same weather from a move into permanent weather and
    // fail only on weather that is already permanent
    if (gBattleWeather & sBattleWeatherInfo[battleWeatherId].flag
     && !(KAIZOCORE_GEN3_BATTLE && ability != ABILITY_NONE && gBattleStruct->weatherDuration != 0))
        return FALSE;
```

Before:
```c
    if (GetConfig(B_ABILITY_WEATHER) < GEN_6 && ability != ABILITY_NONE)
    {
        gBattleWeather = sBattleWeatherInfo[battleWeatherId].flag;
    }
```
After:
```c
    if (GetConfig(B_ABILITY_WEATHER) < GEN_6 && ability != ABILITY_NONE)
    {
        gBattleWeather = sBattleWeatherInfo[battleWeatherId].flag;
        if (KAIZOCORE_GEN3_BATTLE)
            gBattleStruct->weatherDuration = 0; // KaizoCore: permanent, not the rest of an earlier move weather's timer
    }
```

### H8. Pressure counts PP the Gen 3 way

`src/battle_move_resolution.c:949-960` (CancelerPPDeduction). In singles this changes only the field moves (no extra
PP for Haze, Rain Dance, Sunny Day, Sandstorm, Hail, Mud Sport, Water Sport, and Heart & Soul's later field moves,
which have no Gen 3 counterpart and follow the Gen 3 "targets the user" rule). Spikes, Imprison and Snatch keep their
`forcePressure` flag, which matches Gen 3.

Before:
```c
    if (IsSpreadMove(moveTarget)
     || moveTarget == TARGET_ALL_BATTLERS
     || moveTarget == TARGET_FIELD
     || MoveForcesPressure(ctx->move))
    {
        for (u32 i = 0; i < gBattlersCount; i++)
        {
            if (!IsBattlerAlly(i, ctx->battlerAtk) && IsBattlerAlive(i))
                ppToDeduct += (GetBattlerAbility(i) == ABILITY_PRESSURE);
        }
    }
    else if (moveTarget != TARGET_OPPONENTS_FIELD)
```
After:
```c
    // KaizoCore: Gen 3 Cmd_ppreduce. Field moves targeted the user (no Pressure); moves that hit the user's ally
    // (Earthquake) and Perish Song counted the ally's Pressure too.
    if (IsSpreadMove(moveTarget)
     || moveTarget == TARGET_ALL_BATTLERS
     || (moveTarget == TARGET_FIELD && !KAIZOCORE_GEN3_BATTLE)
     || MoveForcesPressure(ctx->move))
    {
        bool32 countAlly = KAIZOCORE_GEN3_BATTLE && (moveTarget == TARGET_FOES_AND_ALLY || moveTarget == TARGET_ALL_BATTLERS);
        for (u32 i = 0; i < gBattlersCount; i++)
        {
            if ((countAlly ? i != ctx->battlerAtk : !IsBattlerAlly(i, ctx->battlerAtk)) && IsBattlerAlive(i))
                ppToDeduct += (GetBattlerAbility(i) == ABILITY_PRESSURE);
        }
    }
    else if (moveTarget != TARGET_OPPONENTS_FIELD && !(KAIZOCORE_GEN3_BATTLE && moveTarget == TARGET_FIELD))
```

### H9. Shield Dust does not stop Smelling Salt's cure

`src/battle_script_commands.c:3522` (SetMoveEffect, MOVE_EFFECT_REMOVE_STATUS)

Before:
```c
         && (NumAffectedSpreadMoveTargets() > 1 || !IsMoveEffectBlockedByTarget(abilities[gEffectBattler])))
```
After:
```c
         && (NumAffectedSpreadMoveTargets() > 1
          || (KAIZOCORE_GEN3_BATTLE && abilities[gEffectBattler] == ABILITY_SHIELD_DUST) // KaizoCore: Gen 3 Shield Dust lets Smelling Salt cure
          || !IsMoveEffectBlockedByTarget(abilities[gEffectBattler])))
```

### H10. A traced ability does not run its switch-in effect, and Trace can trace Trace

`data/battle_scripts_1.s:6100-6102` (BattleScript_TraceActivates). The `.if` form is the file's own (`.if
B_SKILL_SWAP >= GEN_4` at line 3812; TRUE resolves in this file, see line 4405).

Before:
```
	settracedability BS_SCRIPTING
	switchinabilities BS_SCRIPTING
	return
```
After:
```
	settracedability BS_SCRIPTING
.if KAIZOCORE_GEN3_BATTLE != TRUE @ KaizoCore: Gen 3 Trace never runs the traced ability's switch-in effect
	switchinabilities BS_SCRIPTING
.endif
	return
```

`src/data/abilities.h:284` (inside `[ABILITY_TRACE]`). Apply only together with the script change above: without it,
a Trace that traced Trace would run Trace's switch-in again and keep tracing.

Before:
```c
        .cantBeTraced = TRUE, //B_UPDATED_ABILITY_DATA >= GEN_4
```
After:
```c
        .cantBeTraced = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Trace copies Trace (it then sits inert); B_UPDATED_ABILITY_DATA >= GEN_4
```

### H11. Role Play's copied ability does not activate, and Role Play can copy Trace

`data/battle_scripts_1.s:3722` (BattleScript_EffectRolePlay)

Before:
```
	waitmessage B_WAIT_TIME_LONG
	switchinabilities BS_ATTACKER
	goto BattleScript_MoveEnd
```
After:
```
	waitmessage B_WAIT_TIME_LONG
.if KAIZOCORE_GEN3_BATTLE != TRUE @ KaizoCore: in Gen 3 a copied Intimidate, Drizzle or Trace does not activate
	switchinabilities BS_ATTACKER
.endif
	goto BattleScript_MoveEnd
```

`src/data/abilities.h:283` (inside `[ABILITY_TRACE]`). Needs the script change above for the same reason as H10.

Before:
```c
        .cantBeCopied = TRUE,
```
After:
```c
        .cantBeCopied = !KAIZOCORE_GEN3_BATTLE, // KaizoCore: Gen 3 Role Play copies Trace
```

### Optional, information only (what the player is told)

These change no damage, status or turn. They matter to a Kaizo player because the switch-in message reveals the foe's
ability, which Nat. Dex Emerald never does for these three.

**O1. Pressure is not announced.** `src/battle_util.c:3309-3310`

Before:
```c
        case ABILITY_PRESSURE:
            if (shouldAbilityTrigger)
```
After:
```c
        case ABILITY_PRESSURE:
            if (shouldAbilityTrigger && !KAIZOCORE_GEN3_BATTLE) // KaizoCore: Gen 3 never announces Pressure
```

**O2. Cloud Nine and Air Lock are not announced.** `src/battle_util.c:3476-3478`. The announce script also runs
`BattleScript_ActivateWeatherAbilities` (weather form changes such as Forecast); no pool ability needs it.

Before:
```c
        case ABILITY_CLOUD_NINE:
        case ABILITY_AIR_LOCK:
            if (shouldAbilityTrigger)
```
After:
```c
        case ABILITY_CLOUD_NINE:
        case ABILITY_AIR_LOCK:
            if (shouldAbilityTrigger && !KAIZOCORE_GEN3_BATTLE) // KaizoCore: Gen 3 never announces Cloud Nine or Air Lock
```

**O3. Sturdy answers an OHKO move before the level test.** `src/battle_util.c:10628-10629` (DoesOHKOMoveMissTarget),
insert before the level check:

Before:
```c

    if (gBattleMons[cv->battlerDef].level > gBattleMons[cv->battlerAtk].level)
```
After:
```c

    // KaizoCore: Gen 3 checks Sturdy before the level gap, so a higher-level Sturdy target still shows Sturdy
    if (KAIZOCORE_GEN3_BATTLE && cv->abilities[cv->battlerDef] == ABILITY_STURDY)
    {
        gBattleStruct->moveResultFlags[cv->battlerDef] |= MOVE_RESULT_ONE_HIT_KO_STURDY;
        return TRUE;
    }

    if (gBattleMons[cv->battlerDef].level > gBattleMons[cv->battlerAtk].level)
```

## Left alone on purpose

- **Trace's doubles target choice, Synchronize's pop-up when the status cannot be passed back, Color Change, the
  contact abilities' order of "roll, then check immunity"**: same odds and same outcome as Gen 3.
- **Overgrow, Blaze, Torrent, Swarm, Flash Fire, Huge Power, Pure Power, Hustle, Guts, Marvel Scale, Thick Fat**
  multiply the Attack or Defense stat inside Heart & Soul's 4096-based formula where Gen 3 multiplied power or damage.
  The difference is rounding, part of the damage formula that HACK-RULES-NATDEX.md D6 already lists, not an ability rule.
- **Soundproof and sound moves Nat. Dex Emerald does not have** (Bug Buzz, Snarl, Boomburst and the rest). Nat. Dex has
  no rule for them (they are empty placeholder rows there, F5). Blocking them is the natural reading. If Blake wants the
  literal Nat. Dex list instead, gate `case ABILITY_SOUNDPROOF:` in CanAbilityAbsorbMove (`battle_util.c:2488`) with a
  helper that returns TRUE only for Growl, Roar, Sing, Supersonic, Screech, Snore, Uproar, Metal Sound, Grass Whistle,
  Hyper Voice and Disarming Voice. Perish Song and Heal Bell must stay sound moves either way, because the
  B_HEAL_BELL_SOUNDPROOF and Perish Song code read the flag.
- **Abilities outside battle** (not asked for, noted while reading): `wild_encounter.c:944-962` and `:558` give White
  Smoke, Arena Trap, Sand Veil, Hustle, Vital Spirit and Pressure overworld effects that are Gen 4+; Stench, Illuminate,
  Magnet Pull, Static, Keen Eye, Intimidate, Cute Charm, Synchronize, Flame Body and Magma Armor have Gen 3 Emerald
  overworld effects. `pokemon.c:8388` (Compound Eyes and wild held items) was not checked against Emerald.

## Pop-up file

If H1 to H11 and O1 to O3 go in, `docs/ability-popups.tsv` changes for the Gen 3 build: Stench and Pickup never
trigger; Volt Absorb no longer pops on Thunder Wave; a traced or role-played ability shows no follow-up pop-up of its
own; Pressure, Cloud Nine and Air Lock never pop on switch-in (O1, O2); Sturdy pops on every blocked OHKO (O3).

## Unsure

1. **Perish Song and the ally's Pressure (H8).** From memory of pokeemerald's PressurePPLoseOnUsingPerishSong
   (counts every battler with Pressure except the user). Doubles only; Kaizo plays trainer doubles as singles and the
   wild double chance is 0, so it does not reach a Kaizo run either way.
2. **Pressure on fainted battlers.** Gen 3's COUNT_ON_FIELD and COUNT_OTHER_SIDE do not check HP; H8 keeps Heart &
   Soul's `IsBattlerAlive`. With B_FAINT_SWITCH_IN at GEN_3 a fainted slot is refilled before the next move, so this
   only shows in doubles with an empty slot.
3. **Nat. Dex's own contact-ability and Synchronize code** were not disassembled; the table relies on PE plus
   HACK-RULES-NATDEX.md Unsure 3 (Effect Spore 10% in all; Static, Poison Point, Flame Body 1/3).
4. **Ghost and Dark (H0).** Heart & Soul's `types_info.h` came in with "2.0.6" (commit 167aa6d5); whether the swap is
   deliberate for Heart & Soul's own old-style option was not checked. For the Nat. Dex build it is wrong either way.
5. **Gen 3 Trace waiting for a foe.** In vanilla a Trace that finds no foe stays armed and traces when one appears;
   Heart & Soul only traces on its own switch-in. Not gated: in Gen 3 singles a foe is always present when Trace enters.
6. Line numbers are at `f4305974`; the other agent's next commit may move them.

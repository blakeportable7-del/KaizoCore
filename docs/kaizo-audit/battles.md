# Kaizo audit: battles

Heart & Soul Nat. Dex Kaizo (tree 1 `/root/hns/src-gen3mech` at `1f59cad9`, patches 0001-0045, 0047, 0048; patch 0046;
the uncommitted rc37 work in `/root/hns/pokehns-expansion`; the engine in `engine-hns/`) held to Emerald Nat. Dex Kaizo
IronMON. Read-only audit, 2026-10-10. Move and ability rules (0047/0048) are not repeated here.

Config values quoted are the ones in effect in tree 1 (`include/config/battle.h` unless named). "Emerald" means pret
pokeemerald unless a Nat. Dex ROM address is given. Every Kaizo run carries `HNS_PRESET_MODE_KAIZO` and, since
2026-10-08, `HNS_PRESET_KAIZO_DOUBLES = 1` (`HnsEngine.randomize`, `writePreset(..., kaizoDoubles = true)`).

Counts: BREAKS RULE 7, HARDER 6, INFO 10, OK 23 (listed briefly).

---

## BREAKS RULE

### B1. The move menu shows type effectiveness, read from the foe's real types and ability

- **HnS + KaizoCore.** `B_SHOW_EFFECTIVENESS SHOW_EFFECTIVENESS_ALWAYS` (battle.h:426). Every time the move cursor or the
  target moves, `MoveSelectionDisplayMoveEffectiveness(CheckTypeEffectiveness(...))` runs (battle_controller_player.c:517,
  566, 774, 812, 829, 845, 862, 901, 941, 1098, 1117, 2150). `CheckTypeEffectiveness` (battle_controller_player.c:2403)
  fills `ctx.abilityDef = GetBattlerAbility(battlerDef)` (line 2413) and the defender's hold effect, then calls
  `CalcTypeEffectivenessMultiplier` (battle_util.c:8470), which zeroes Ground moves for Levitate or Air Balloon and every
  non-super-effective move for Wonder Guard (battle_util.c:8405-8460). So four moves and a glance give the foe's
  randomized types, and an "immune" mark on a Ground move names Levitate before it is ever revealed.
- **Emerald.** No indicator: the move window prints PP and TYPE only (pret `MoveSelectionDisplayPpNumber`,
  `MoveSelectionDisplayMoveType`). Blake's standing ruling: never show what Emerald hides (an opponent's ability,
  randomized data).
- **Fix.**
  ```c
  // include/config/battle.h:426
  -#define B_SHOW_EFFECTIVENESS        SHOW_EFFECTIVENESS_ALWAYS
  +#define B_SHOW_EFFECTIVENESS        (KAIZOCORE_GEN3_BATTLE ? SHOW_EFFECTIVENESS_NEVER : SHOW_EFFECTIVENESS_ALWAYS) // KaizoCore: Emerald shows no effectiveness; this one reads the foe's real types and ability
  ```
  Every use is a runtime `if` or a constant initializer (battle_message.c:1613-2174 pick the text colour); no `#if` reads it.

### B2. In a trainer double, the second Pokemon (the HM friend) fights; the ROM cannot tell Kaizo from Kaizo Doubles

- **HnS + KaizoCore.** The engine now writes `HNS_PRESET_KAIZO_DOUBLES = 1` for every Kaizo run (HnsEngine.kt:117,145),
  so `KaizoCore_TrainerDoublesAreSingles()` (kaizocore_play.c:94) is false and the game's own doubles play as doubles,
  as the ruling asks. Nothing limits the player's right slot: in battle_main.c's action loop the only right-slot
  restriction is the wild-double "throwing a ball" branch (battle_main.c:4398, `WILD_DOUBLE_BATTLE` only). Tree 3 adds
  nothing here. And since the byte is 1 for both modes, the ROM no longer knows whether the run is Kaizo or Kaizo
  Doubles, where both Pokemon must fight.
- **Rule.** HNS-KAIZO.md, Ruling 2026-10-08: "The player's second Pokemon cannot attack. On its turn the only action is
  throwing a Poke Ball. That second Pokemon is the caught HM friend". Kaizo rules: "HM friends cannot be used in battle".
- **Fix.** A preset byte of its own, then the action lock.
  ```c
  // include/kaizocore_tables.h
       HNS_PRESET_KAIZO_DOUBLES = HNS_PRESET_LOCKS + HNS_CHALLENGE_ROWS, // non-zero: the game's own trainer doubles stay doubles
  +    HNS_PRESET_SECOND_BALL_ONLY,  // non-zero: Kaizo (not Kaizo Doubles); the right slot of a trainer double does not act
       HNS_PRESET_SIZE,
  // src/battle_main.c, STATE_BEFORE_ACTION_CHOSEN, before the B_RUN_TRAINER_BATTLE branch at line 4406
  +                    else if (KAIZOCORE_GEN3_BATTLE && gHnsChallengePreset[HNS_PRESET_SECOND_BALL_ONLY]
  +                          && (gBattleTypeFlags & (BATTLE_TYPE_TRAINER | BATTLE_TYPE_DOUBLE)) == (BATTLE_TYPE_TRAINER | BATTLE_TYPE_DOUBLE)
  +                          && !(gBattleTypeFlags & (BATTLE_TYPE_MULTI | BATTLE_TYPE_INGAME_PARTNER))
  +                          && position == B_POSITION_PLAYER_RIGHT)
  +                    {
  +                        // KaizoCore (Blake, 2026-10-08): the HM friend never attacks. A ball at a trainer's Pokemon is
  +                        // blocked (Cmd_handleballthrow), so the one action the ruling leaves is no action.
  +                        gChosenActionByBattler[battler] = B_ACTION_NOTHING_FAINTED;
  +                        gBattleCommunication[battler] = STATE_WAIT_ACTION_CONFIRMED_STANDBY;
  +                    }
  ```
  Engine: `writePreset` writes `HNS_PRESET_SECOND_BALL_ONLY = 1` when `kaizo && modeKey(options.modeName) != "kaizodoubles"`
  (pass the mode in), and the layout gains the constant. See Conflicts for the "throw a ball" wording.

### B3. Holding B at the start of a wild battle is a free escape roll

- **HnS + KaizoCore.** Patch 0006 made the hack's QUICK RUN default to "B". `BATTLE_INTRO_STATE_QUICK_RUN`
  (battle_main.c:3824-3858) calls `TryRunFromBattle` during the intro. On failure it prints "Can't escape!" and the
  battle starts normally: the player then picks an action, so the failed roll cost no turn, and `TryRunFromBattle` has
  already done `runTries++` (battle_util.c:637), so the next real RUN gets +30 on the 256 roll.
- **Emerald.** RUN is an action; a failed roll spends the turn and the foe moves (pret `HandleAction_Run`). Blake: no
  free advantages. HnS/kaizo.md line 12 documents the hold-B run as a comfort, so this is Blake's call.
- **Fix.** Let the held B take only an escape Emerald grants outright (faster or equal speed, Smoke Ball, Run Away):
  ```c
  // src/battle_main.c:3836, after the shiny check
  +            if (runPressed && KAIZOCORE_GEN3_BATTLE && !(gBattleTypeFlags & BATTLE_TYPE_SAFARI))
  +            {
  +                u32 me = GetBattlerAtPosition(B_POSITION_PLAYER_LEFT), foe = BATTLE_OPPOSITE(me);
  +                // KaizoCore: a roll Emerald charges a turn for is left to the RUN command.
  +                if (GetBattlerHoldEffect(me) != HOLD_EFFECT_CAN_ALWAYS_RUN && GetBattlerAbility(me) != ABILITY_RUN_AWAY
  +                 && gBattleMons[me].speed < gBattleMons[foe].speed)
  +                    runPressed = FALSE;
  +            }
  ```

### B4. Kurt's Apricorn Balls catch at x4 by type, and the GS Ball at x25.5

- **HnS.** `ComputeBallData` (battle_script_commands.c:10625) has seven `#if IS_HNS` blocks (10649, 10722, 10740, 10759,
  10777, 10787, 10832): Friend Ball x4 on Bug/Grass, Level Ball on Normal/Flying/Ice, Lure Ball on Water/Dragon, Moon Ball
  on Dark/Ghost/Poison, Love Ball on Fairy/Psychic, Fast Ball on Electric/Fighting/Fire, Heavy Ball on Rock/Ground/Steel:
  every one of the 18 types has a x4 ball. Kurt (AzaleaTown_KurtsHouse_hns, `special CreateKurtBallShop`, shop.c:2291-2320)
  trades one Pecha/Rawst/Cheri/Oran/Chesto/Aspear/Persim Berry for one of them (shop.c:128-137), and gives a Fast Ball
  outright. `BALL_GS` is x25.5 on anything that is not Celebi (battle_script_commands.c:10861-10866); the GS Ball is a
  throwable ball (`items.h:16065`, `EFFECT_ITEM_THROW_BALL`).
- **Emerald.** No Apricorn Balls; the best repeatable ball is the Ultra Ball x2 (Net x3 on Water/Bug, Nest, Repeat,
  Timer). The rules allow buying any ball, but these multipliers are not Emerald's catch mechanics, and a Kaizo run is
  catch-only, so catching ease is the pivot.
- **Fix.**
  ```c
  // src/battle_script_commands.c, top of ComputeBallData's switch (10647)
  +    // KaizoCore: Emerald has no Apricorn or GS Ball. In a Gen 3 build they catch as a Poke Ball.
  +    if (KAIZOCORE_GEN3_BATTLE && (ballId == BALL_LEVEL || ballId == BALL_LURE || ballId == BALL_MOON || ballId == BALL_FRIEND
  +     || ballId == BALL_LOVE || ballId == BALL_FAST || ballId == BALL_HEAVY || ballId == BALL_GS))
  +        return;
  ```
  (Keeps the Friend Ball's friendship and the balls themselves; only the multiplier goes.)

### B5. Tree 3 gives every new game the Rare Candy Box (999 Rare Candies), Kaizo runs included

- **Tree 3** (`src/player_pc.c`, diff hunk at 377): `AddPCItem(ITEM_INFINITE_RARE_CANDIES, 1)` unconditionally; its use
  adds 999 Rare Candies (item_use.c:1650). Tree 1 gave it only with `tx_Nuzlocke_RareCandy`, which the Kaizo preset locks
  OFF. The comment says "a mode check here stayed empty" because the preset is applied on load; that is true of the save's
  challenge rows, not of `KAIZOCORE_KAIZO_RUN`, which reads the ROM table and is valid at new game. Not on Blake's
  2026-10-08 list (HNS-KAIZO.md has no Rare Candy line). Cross-area (EXP), listed because it decides every battle.
- **Emerald.** No such item.
- **Fix.**
  ```c
  // src/player_pc.c (tree 3)
  -    AddPCItem(ITEM_INFINITE_RARE_CANDIES, 1);
  +    if (!KAIZOCORE_KAIZO_RUN) // KaizoCore: never in a Kaizo IronMON run (the mode is in the ROM, readable here)
  +        AddPCItem(ITEM_INFINITE_RARE_CANDIES, 1);
  ```

### B6. Any ball works in the Safari Zone at the species' real catch rate

- **HnS.** `ComputeCaptureOdds` (battle_script_commands.c:10897): "HnS lets the player throw any ball in the Safari Zone";
  only a Safari Ball uses `safariCatchFactor`; an Ultra or Apricorn Ball uses the real catch rate and its own multiplier.
- **Emerald.** The Safari bag holds Safari Balls only (x1.5 on the Safari factor).
- **Fix.**
  ```c
  // ComputeBallData, first lines
  +    if (KAIZOCORE_GEN3_BATTLE && (gBattleTypeFlags & BATTLE_TYPE_SAFARI)) { ball->multiplier = 150; return; } // every ball is a Safari Ball
  // ComputeCaptureOdds:10897
  -    if ((gBattleTypeFlags & BATTLE_TYPE_SAFARI) && (!IS_HNS || gLastUsedItem == ITEM_SAFARI_BALL))
  +    if ((gBattleTypeFlags & BATTLE_TYPE_SAFARI) && (!IS_HNS || KAIZOCORE_GEN3_BATTLE || gLastUsedItem == ITEM_SAFARI_BALL))
  ```

### B7. Obedience rises with every badge (HeartGold's ladder), not Emerald's

- **HnS.** `GetAttackerObedienceForAction` (battle_util.c:5745-5760): 10, then 20/30/40/50/60/70/80 for badges 1-7, all
  levels at badge 8. Applies to Pokemon with another OT (in-game trades, which the engine randomizes).
- **Emerald.** 10; 30 with badge 2; 50 with badge 4; 70 with badge 6; all with badge 8. HnS is never stricter and is
  10 levels looser after badges 1, 3, 5 and 7. Small, traded Pokemon only.
- **Fix.**
  ```c
  // battle_util.c:5745
  +#if KAIZOCORE_GEN3_BATTLE
  +    obedienceLevel = 10;
  +    if (FlagGet(FLAG_BADGE02_GET)) obedienceLevel = 30;
  +    if (FlagGet(FLAG_BADGE04_GET)) obedienceLevel = 50;
  +    if (FlagGet(FLAG_BADGE06_GET)) obedienceLevel = 70;
  +#else
       obedienceLevel = 10; ... (the per-badge ladder as now)
  +#endif
  ```

---

## HARDER (stricter than Emerald)

### H1. Trainers use their bag items differently

- **HnS.** `ShouldUseItem` (battle_ai_items.c:29-199): no limit on which item may be used when, so a boss uses its second
  Full Restore with its whole party still standing; it skips healing when it expects to KO you (line 50) or when you
  could still KO it after the heal (`AI_ShouldHeal`, line 212). Boss bags: Johto leaders Potion/Super Potion x2, Clair,
  every Kanto leader, Blue, the Elite Four, Lance, Red, the rival from battle 6 and the Rocket executives Full Restore x2
  (trainers_hns.party). The engine never touches `Items:`.
- **Emerald** (pret `ShouldUseItem`): `if (i != 0 && validMons > (itemsNo - i) + 1) continue;`, so a second item waits
  until the party is down to two; Full Restore at under 1/4 HP whatever the odds. Emerald's leaders 5-8 carry Hyper
  Potions; Full Restores start at the Elite Four.
- **Fix** (keeps HnS's bags, plays Emerald's timing):
  ```c
  // battle_ai_items.c:50 and :61
  -    if (AiExpectsToFaintPlayer(battler))
  +    if (!KAIZOCORE_GEN3_BATTLE && AiExpectsToFaintPlayer(battler))
           return FALSE;
  ...
       for (u32 itemIndex = 0; itemIndex < MAX_TRAINER_ITEMS; itemIndex++)
       {
  +        // KaizoCore: Emerald's gate; used items are zeroed, itemsNo stays, as in pret.
  +        if (KAIZOCORE_GEN3_BATTLE && itemIndex != 0 && validMons > (gBattleHistory->itemsNo - itemIndex) + 1)
  +            continue;
  // AI_ShouldHeal:212
  -        shouldHeal = !AI_OpponentCanFaintAiWithMod(battler, healAmount);
  +        shouldHeal = KAIZOCORE_GEN3_BATTLE || !AI_OpponentCanFaintAiWithMod(battler, healAmount);
  ```

### H2. The move-choice AI is expansion's, not Emerald's scripts

- **HnS.** Flags are close to Emerald's (route trainers "Check Bad Move", bosses "Basic Trainer" = Check Bad Move | Try To
  Faint | Check Viability, constants/battle_ai.h:46), but expansion scores moves with full damage calculations. Switching
  for these flags is Emerald's set (Wonder Guard, absorbers, Natural Cure, the super-effective switch); every smart check
  is gated on `AI_FLAG_SMART_SWITCHING` (battle_ai_switch.c:305, 588, 712, 740, 1153, 1228, 1373). Smart Trainer /
  Prediction is only on post-game rosters.
- **Emerald.** `battle_ai_scripts.s`.
- **Fix.** None short of porting Emerald's AI scripts; noted so the difference is known.

### H3. Trainers keep Heart & Soul's held items on their randomized Pokemon

- **HnS + engine.** RSE NatDex v1.2 Kaizo.rnqs does not randomize trainer held items (byte 48 bits 0-2 all 0, decoded from
  the file), and `HnsGame.writeTrainers` writes `tm.heldItem` back unchanged. Story bosses carry Gen 3 items in numbers
  Emerald's do not (Brock: two Quick Claws, two Hard Stones, Scope Lens, Focus Band; every Kanto leader, Blue, the Elite
  Four and rivals 5-7 similar). Post-game rosters carry Gen 4+ items (Focus Sash x6, Life Orb x6, Assault Vest x7, Loaded
  Dice, Choice Specs, Rocky Helmet...) that Nat. Dex Emerald does not have.
- **Emerald.** Few trainer held items (a Sitrus Berry on an ace, Leftovers on Wallace's).
- **Fix (engine).** In `HnsRandomizer.trainers()`, after the species roll: drop a held item that is not in the pool's
  item list (`poolItems`, the items of vanilla or Nat. Dex Emerald): `if (!inPool(tm.heldItem)) tm.heldItem = 0`.
  Whether to thin the Gen 3 items too is Blake's call.

### H4. Trainers keep Heart & Soul's EVs on randomized Pokemon

- **HnS + engine.** 52 trainer rows carry `EVs:` (mostly post-game: Alola islands, Sinjoh, post-OBC; also MILKMAN, MIHARU,
  YOUKO, SHIZUKO, WESSEL, SKYLAR). `writeTrainers` with `reset` clears moves, ability, gender and Dynamax data but not the
  `ev` pointer (struct TrainerMon, include/data.h:62; applied at battle_main.c:2074-2081).
- **Emerald.** Trainer Pokemon have no EVs.
- **Fix (engine).** In `HnsGame.writeTrainers`, under `if (tm.reset)`: `ms.fields["ev"]?.let { rom.set(it, a, 0) }` (a null
  pointer means no EVs). Same for `teraType` (`ms.fields["teraType"]`).

### H5. Tree 3's evolution fix drops the max-HP gain Emerald gives

- **Tree 3** (`src/evolution_scene.c`, both evolution tasks): saves HP before the species change and writes it back after
  `CalculateMonStats`. Tree 1's `CalculateMonStats` (pokemon.c, "Only add to currentHP if newMaxHP went up") already adds
  `newMax - oldMax`, which tree 3 then undoes.
- **Emerald** (pret `CalculateMonStats`): current HP += new max HP - old max HP. The ruling only says evolving must not
  fully heal.
- **Fix.** `keptHp += GetMonData(mon, MON_DATA_MAX_HP) - oldMaxHp` (read `oldMaxHp` with `keptHp`), then cap. See Unsure 7:
  where the full heal Blake saw comes from was not traced.

### H6. Red's post-OBC team is the one fought after the Sinjoh storyline

- **HnS.** MtSilver_SummitDay_hns/scripts.inc:33-43: with `FLAG_IS_KANTO_CHAMPION` set and `VAR_SINJOH_STORYLINE >= 13`,
  Red is `TRAINER_RED_POSTOBC_HNS`: Smart Trainer / Prediction (omniscient AI, smart switching), Tera Type on Venusaur
  and Tauros (the engine keeps `teraType`), EVs, Blunder Policy. Otherwise `TRAINER_RED_HNS` (Basic Trainer).
- **Rule.** The run is won on Red. A player who does the Sinjoh post-game first meets a harder Red.
- **Fix.** `goto MtSilver_SummitDay_EventScript_RedStandard` unconditionally when `KAIZOCORE_KAIZO_RUN` (a `special` that
  reads it), or accept it.

---

## INFO (what the player is shown, or harmless)

1. **Move description on R.** Tree 3 sets `B_MOVE_DESCRIPTION_BUTTON B_LAST_USED_BALL_BUTTON` (R). The comment says "so
   Start does not open move info"; Start no longer does, but `HandleInputChooseMove` has no "equal to the ball button"
   check (battle_controller_player.c:907-911), so R now opens power, accuracy, category and text, and the move info sign
   shows R (battle_interface.c:3063). Fix if the ruling means "nowhere": `#define B_SHOW_MOVE_DESCRIPTION
   (!KAIZOCORE_GEN3_BATTLE)` (battle.h:312) and add `B_SHOW_MOVE_DESCRIPTION &&` to the branch at line 907, or the
   first A press after an R press is eaten by the invisible submenu (line 886).
2. **Set battles.** Tree 3 makes every new game start on SET (new_game.c:113) and forces SET on every Kaizo save load
   (`KaizoCore_EnforceChallengePreset`). Matches the Kaizo rule; conflicts with the ruling's text (see Conflicts).
3. **HeartGold-style Pokedex** (`POKEDEX_PLUS_HGSS TRUE`, config/pokedex_plus_hgss.h) shows base stats, abilities,
   learnsets and evolutions from the ROM tables the engine randomizes. Cross-area; likely a reveal (see Unsure 3).
4. **Summary IV and EV numbers** (`P_SUMMARY_SCREEN_IV_EV_VALUES TRUE`, config/summary_screen.h:10): own Pokemon only;
   Emerald shows neither.
5. **HP bar colour** (`B_HPBAR_COLOR_THRESHOLD GEN_LATEST`, battle.h:354) turns yellow and red on exact HP fractions;
   Emerald on bar pixels. Marginally more precise read of the foe's HP.
6. **Wild held items** (pokemon.c:8404-8449): 45% none, 45% common, 10% rare; Emerald 45/50/5. Compound Eyes 20/60/20 as
   in Emerald. The items themselves are the engine's roll.
7. **Multi-battle whiteout** (`B_MULTI_BATTLE_WHITEOUT GEN_LATEST`, battle.h:375): in the Rocket Hideout tag battle the
   battle goes on while Lance still stands after the player's side is out; Gen 3 ends it. The Kaizo rule ends the run
   anyway.
8. **Stale doubles text.** kaizocore_tables.h:43 calls `HNS_PRESET_KAIZO_DOUBLES` "the Kaizo Doubles mode" and documents
   0043's singles conversion; the engine writes it for every Kaizo run, so `KaizoCore_TrainerDoublesAreSingles()` and its
   callers (trainer_see.c:496, 623; battle_setup.c:1641; script_pokemon_util.c:111) are dead in every run.
9. **Fishing** (config/fishing.h): Gen 4+ bite odds (Old 25%, Good 50%, Super 75%; Emerald 50% each) and the follower's
   friendship raises the bite chance. Encounter rate only.
10. **Conveniences, no hidden information:** last-used ball on R in wild battles (`B_LAST_USED_BALL`, blocked in trainer
    battles, battle_interface.c:3101); catch-swap into a full party; Start in the action menu toggles HP numbers for the
    player's own side in doubles only (battle_interface.c `SwapHpBarsWithHpText`); fast HP drain; target arrows for
    spread moves.

---

## OK (checked, matches Emerald or the ruling)

- No running or forfeiting a trainer battle in a Kaizo run, Emerald's refusal, no turn spent (battle_main.c:4406, 4613-4618,
  6498).
- A trainer's Pokemon never flees, whatever chose to run (battle_util.c `HandleAction_Run`, 0045); first-rival AI flees
  only outside trainer battles.
- Wild escape formula = Emerald's: speed x128 / foe speed + 30 per try against `Random() & 0xFF`, always when faster;
  Smoke Ball, Run Away; Ghosts not exempt (`B_GHOSTS_ESCAPE GEN_3`) (battle_util.c:545-640).
- Poke Doll and escape items refused in trainer battles (item_use.c:1422).
- Catch formula = Emerald's for Poke, Great, Ultra, Master, Net, Dive, Nest, Repeat, Timer, Premier, Luxury: (3M - 2H) x
  rate / 3M x ball, x2 sleep or freeze, x1.5 poison/burn/paralysis, `odds > 254` caught, four 16-bit shake checks
  (battle_script_commands.c:10883-11085); no critical capture, no badge malus, no low-level bonus. Order of the integer
  steps differs, at most 1 point.
- A ball at a trainer's Pokemon is blocked and used up (`B_TRY_CATCH_TRAINER_BALL GEN_3`).
- Wild doubles off (`B_DOUBLE_WILD_CHANCE 0`, no force flag); DexNav off; chain fishing off; shiny odds 1/8192 from the
  locked FEATURES row (pokemon.c:89-98); Shiny Charm, Lures only in post-game.
- Hidden ability: the engine sets slot 2 = slot 0 (HnsRandomizer.kt:753); trainer abilities reset to 0.
- EXP: yield x level / 7, x1.5 for trainers, split among participants, no catch EXP and no party-wide Exp. Share in a Kaizo
  run (battle_script_commands.c:4198-4246, battle_util.c:9741). Exp yields are modern in both games: Nat. Dex ROM
  species rows (0x08325340, 36 bytes, yield u16 at +30) read Dragonite 300, Blissey 608, Alakazam 250; HnS 300, 635, 250.
- No Exp. Charm, Shiny Charm, Mega Ring, Z-Power Ring, Dynamax Band or Tera Orb anywhere in the story scripts; no story
  trainer holds a Mega Stone, Z-Crystal or orb; Dynamax fields cleared by the engine.
- Badge stat boosts at Gen 3 values on HeartGold's badges (Zephyr Atk, Plain Spe, Mineral Def, Glacier SpA/SpD).
- X items +1 stage on the current battler; Dire Hit = Focus Energy; Guard Spec. = Mist.
- Trainers cannot steal held items; used-up items stay used in a Kaizo run (0034).
- Elite Four: no heal between rooms (no `HealPlayerParty` in PokemonLeague_*_hns).
- Trainer IVs in Emerald-like tiers (0, 12, 24, 31).
- Trainer doubles stay doubles in every Kaizo run; Kaizo Doubles makes every trainer a double except the first rival
  (HnsRandomizer.kt:1317-1321).
- Wild Pokemon pick moves at random (no AI flags) outside post-game Routes 49/50; roamers flee (`AI_FLAG_ROAMING`).
- Start no longer opens the move description (tree 3; see INFO 1 for R).
- No type icons (`B_SHOW_TYPES NEVER`), no opponent summary, no party preview beyond Emerald's ball row, no stat-stage
  display, foe HP as a bar.
- Whiteout money halves (`B_WHITEOUT_MONEY GEN_3`).
- Moves can be reordered in battle with Select (`B_MOVE_REARRANGEMENT_IN_BATTLE GEN_3`), as in Emerald.
- Patch 0046 (hidden-item sparkles) and the rest of tree 3 touch no battle code.
- Tree 3 applies on top of tree 1 without a textual clash (`git apply --check`: battle.h hunk at 346, challenge_menu.c at
  2560, new_game.c at 110). Its battle.h line (349) and challenge_menu.c hunk do not overlap 0047/0048's edits.

---

## Conflicts with the 2026-10-08 ruling (not resolved here)

1. **Set vs Shift.** Ruling: "A new game starts on Shift ... The ROM does not force Set." Tree 3 starts every new game on
   SET and forces SET on every Kaizo save load.
2. **The second Pokemon "throws a ball".** In a trainer battle a thrown ball is blocked and used up, so the ruling's only
   action does nothing. B2's hunk makes the friend skip its turn; letting the bag open with balls only would waste balls
   for the same result.
3. **"Kaizo Doubles mode" byte.** The ruling keeps doubles in every Kaizo run by reusing `HNS_PRESET_KAIZO_DOUBLES`, which
   left the ROM unable to apply a Kaizo-only rule (B2) without a new byte.
4. **Evolution heal.** Ruling: evolving must not fully heal. Tree 3 removes more than the heal (H5).
5. **Move description.** Ruling: Start must not open it, details live on the tracker. Tree 3 moved it to R (INFO 1).

---

## Unsure

1. The GS Ball's reach before Red: an item ball in Ruins of Alph's reward chamber (`RuinsOfAlph_..._EventScript_GSBALL`,
   `FLAG_ITEM_GS_BALL`) and Kurt's scene in Azalea. Whether the engine's field-item roll rewrites that `finditem` was not
   checked.
2. Whether Nat. Dex Emerald 1.2.1 added any move-menu indicator. Assumed none (pret has none; not read from the ROM).
3. Whether the HeartGold-style Pokedex shows stats and abilities for seen-only species. Not run.
4. Expansion's AI against Emerald's scripts was compared by code, not by play.
5. Reach of the post-game boss battles (`setwildbossbattle`, Routes 49/50, Snowswept Cavern, Sinjoh) and of Red post-OBC
   before the Red win; assumed post-game.
6. Prize money formula and Mom's savings (HnS `Mom_AutoDepositFromBattle`, battle_script_commands.c:6257) not compared;
   Mom buying items is a player rule.
7. Where the evolution full heal Blake saw comes from: tree 1's `CalculateMonStats` and `Task_EvolutionScene` add the
   max-HP gain like Emerald. Trace it before choosing H5's fix.
8. Which EV rows (H4) are reachable in the story.
9. Berry supply for Kurt (B4): 32 HnS maps have berry trees, and field items are random; the Emerald berry-tree ban is in
   the RSE game rules, not HnS's.

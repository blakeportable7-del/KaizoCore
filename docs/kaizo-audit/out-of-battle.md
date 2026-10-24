# Heart & Soul Nat. Dex Kaizo: out-of-battle audit (2026-10-10)

Read-only code audit of everything outside battle that changes what a Kaizo run can do or get, held to Emerald
Nat. Dex Kaizo IronMON. Nothing was built, run or played.

Sources:

- **Tree 1**: `/root/hns/src-gen3mech`, branch `kaizocore-gen3-battle`, HEAD `1f59cad9` (2.0.6 + 0001-0045, 0047, 0048).
  `KAIZOCORE_GEN3_BATTLE` is TRUE (include/config/battle.h:8). File:line below is tree 1 unless marked.
- **0046**: `IronMonOne/tools/hns/patches/0046-...hidden-item-sparkles...patch`.
- **Uncommitted**: `git -C /root/hns/pokehns-expansion diff` (branch kaizocore-rc37). Its base is `6dca3c6d`
  (patch 0045), so it does not contain 0046, 0047 or 0048. It reimplements 0046's sparkle in item_use.c and edits
  challenge_menu.c and new_game.c, which 0047/0048 also change. Merging it needs a rebase.
- **Engine**: `IronMonOne-wt-pc/engine-hns/.../engine/HnsEngine.kt` (writePreset) and `hns/HnsRandomizer.kt`.
- **Kaizo preset in effect**: the C build preset (src/kaizocore_tables.c, MODE, DIFFICULTY and CHALLENGES rows, all
  locked) plus `HnsEngine.writePreset(Preset.KAIZO)`: NUZLOCKE tab OFF and locked, GAME MODE CUSTOM, REUSABLE TMS OFF,
  MINTS OFF, SHINY CHANCE 1/8192, ITEM DROP OFF (`KAIZO_RULE_ROWS`, HnsEngine.kt:162), skip-menu on. Rows left free:
  FEATURES RTC type, shiny colours, frontier bans.
- **Emerald reference**: pret pokeemerald source where noted. Nat. Dex Emerald 1.2.1's ROM was not disassembled for
  these mechanics (see Unsure).

Severities: BREAKS RULE, HARDER, INFO, OK, as defined in the brief. "Player-policed" means the rules forbid the action
and the ROM still allows it, the same way Emerald's marts allow buying a Full Restore.

## Counts

| Severity | Count |
|---|---|
| BREAKS RULE | 7 (one of them, the wild cycle, is a Blake-ruled exception) |
| HARDER | 3 |
| INFO | 13 |
| OK | 24 |

Conflicts with the 2026-10-08 ruling: 5, listed in their own section.

---

## BREAKS RULE

### B1. Overworld debug menu is in the ROM (R + Start)

- **Tree 1**: include/config/debug.h:5 `DEBUG_OVERWORLD_MENU TRUE`; field_control_avatar.c:165 opens it with R held and
  Start pressed; start_menu.c:1137 `StartMenuDebugCallback`. The built ELF `/root/hns/out-gen3/pokehns.elf` (10/10)
  still carries `Debug_ShowMainMenu` (0x0810aa60); so do out-rc38 and out-rc38-1b. The menu gives items and Pokemon,
  heals, edits the party, and sets `FLAG_DISABLE_TRAINERS` / `FLAG_DISABLE_ENCOUNTERS` / `FLAG_COLLISION`
  (config/overworld.h:113-115; only debug.c sets them).
- **Rule**: everything it gives is forbidden by Standard rules (no shops, no free items, no extra Pokemon) and by the
  10/08 ruling ("DEBUG comes out of the comfort ROM").
- **Uncommitted**: fixed (`DEBUG_OVERWORLD_MENU FALSE`).
- **Fix**: keep the uncommitted hunk. Nothing else needed; `StartMenuDebugCallback` already checks the define.

### B2. Battle debug menu is in the ROM (Select in battle)

- **Tree 1 and uncommitted**: include/config/debug.h:11 `DEBUG_BATTLE_MENU TRUE`. battle_controller_player.c:398
  sends `B_ACTION_DEBUG` on Select; battle_main.c:4592 opens `CB2_BattleDebugMenu` (battle_debug.c:655), which edits
  HP, stats, held items and more. `BattleDebug_WonBattle` is in every built ELF checked. One stray Select in any battle
  opens it. The uncommitted work turned off only the overworld menu.
- **Rule**: same as B1; the 10/08 ruling asks for debug out.
- **Fix** (debug never belongs in a player ROM, so no gate):

```c
-#define DEBUG_BATTLE_MENU               TRUE
+#define DEBUG_BATTLE_MENU               FALSE // KaizoCore: Select in battle no longer opens the editor
-#define DEBUG_POKEMON_SPRITE_VISUALIZER TRUE
+#define DEBUG_POKEMON_SPRITE_VISUALIZER FALSE // KaizoCore: Select on the summary screen (see I10)
```

### B3. Uncommitted: every new game gets the Rare Candy Box (999 Rare Candies per use)

- **Uncommitted** src/player_pc.c `NewGameInitPCItems`: the `tx_Nuzlocke_RareCandy` check is removed and
  `AddPCItem(ITEM_INFINITE_RARE_CANDIES, 1)` runs on every new game, Kaizo included. The item is a reusable key item
  (src/data/items.h:16145, importance 1) whose use adds 999 Rare Candies (item_use.c:1650-1663). In a Kaizo run the
  level cap is OFF (kaizocore_tables.c, `HNS_SET(4, 1, 0)`) and `B_RARE_CANDY_CAP` is FALSE (config/caps.h:18), so
  nothing stops levelling to 100 before the first gym.
- **Tree 1** is OK: the box comes only with `tx_Nuzlocke_RareCandy`, which is 0 in a Kaizo run (the new-game path
  zeroes the settings, oak_speech_hns.c:621-624; the Kaizo preset turns Nuzlocke OFF and never sets that bit).
- **The uncommitted comment is wrong**: it says the preset is applied after `NewGameInitPCItems`. On a new game the
  challenge menu saves first (oak_speech_hns.c:646, challenge_menu.c:2371-2376) and `NewGameInitData` restores those
  settings before calling `NewGameInitPCItems` (new_game.c `savedChallenge`), so the original check worked.
- **Rule**: Standard "No Shops / only items you pick up or are given by an NPC"; Emerald has no candy source like it.
- **Fix** (uncommitted player_pc.c), plus a refusal for saves carried over from a Nuzlocke ROM:

```c
-    /* KaizoCore: every new game on this build starts with the Rare Candy Box. ... */
-    AddPCItem(ITEM_INFINITE_RARE_CANDIES, 1);
+    if (!KAIZOCORE_KAIZO_RUN && gSaveBlock3Ptr->challengeSettings.tx_Nuzlocke_RareCandy)
+        AddPCItem(ITEM_INFINITE_RARE_CANDIES, 1);
```

```c
 void ItemUseOutOfBattle_InfiniteRareCandies(u8 taskId)
 {
+#if KAIZOCORE_GEN3_BATTLE
+    if (KAIZOCORE_KAIZO_RUN) // a Nuzlocke save carried into a Kaizo run keeps the box but it does nothing
+    {
+        ItemUseOutOfBattle_CannotUse(taskId);
+        return;
+    }
+#endif
```

### B4. Field HMs run without an HM friend (any party member that can learn the move)

- **Tree 1 and uncommitted**: `ScrCmd_checkfieldmove` (scrcmd.c:3049-3078) accepts the first party member that
  *knows* the move, and if none does, the first that *can learn* it while the HM is in the bag. Surf does the same
  (field_player_avatar.c:1679-1688, `PartyHasMonWithSurf`). Cut, Rock Smash, Strength, Waterfall, Dive and Whirlpool go
  through `checkfieldmove` (data/scripts/field_move_scripts.inc:4, 66, 231, 290, 325, 352). The engine writes full HM
  compatibility (`HnsRandomizer.tmCompat`, `o.fullHMCompat`), so the main Pokemon Cuts, Surfs and pushes boulders
  without knowing the move and without an HM friend.
- **Tree 1 also**: patch 0004's start-menu HM list (start_menu.c:386-390, `BuildHMMenuMoves` ~932-947: HM in the bag
  and the badge, no Pokemon at all) and patch 0018's party-menu Fly/Flash offer.
- **Uncommitted**: removes the 0004 menu entry and 0018's offers. It does not touch the learnability fallback above and
  does not add the HM friend (10/08 items 25-26).
- **Emerald**: pret `checkpartymove` takes only a party member that knows the move. The rules' HM-friend rules exist
  because of that. Not needing a friend or a move slot is an advantage Emerald does not give.
- **Fix** (both functions; the friend marking is new work on top):

```c
-    if (gSpecialVar_Result == PARTY_SIZE)
+    // KaizoCore: in a Kaizo run a field move needs a Pokemon that knows it, as in Emerald (the HM friend).
+    if (gSpecialVar_Result == PARTY_SIZE && !(KAIZOCORE_GEN3_BATTLE && KAIZOCORE_KAIZO_RUN))
     {
         u16 itemId = GetTMHMItemIdFromMoveId(move);
```

```c
-        if (CheckBagHasItem(ITEM_HM03, 1))
+        if (CheckBagHasItem(ITEM_HM03, 1) && !(KAIZOCORE_GEN3_BATTLE && KAIZOCORE_KAIZO_RUN))
```

  Then, per the ruling, restrict the "knows the move" loop to the marked friend. The start-menu HM code
  (`BuildHMMenuMoves`, `gFlyMapFromStartMenu` in region_map.c:74) is dead once the entry is gone; delete it with 0004.

### B5. The Pokedex shows randomized learnsets, evolutions, abilities and base stats

- **Tree 1**: include/config/pokedex_plus_hgss.h:4 `POKEDEX_PLUS_HGSS TRUE`, :7 `HGSS_HIDE_UNSEEN_EVOLUTION_NAMES
  FALSE`, :8 `HGSS_HIDE_UNOWNED_EVOLUTION_METHODS FALSE`. Every owned species opens a stats page (base stats,
  abilities, the level-up / TM / tutor move lists: `PrintStatsScreen_Moves_*`) and an evolution page (targets and
  methods, unseen targets named). The only gate is `owned` (pokedex_plus_hgss.c:4186-4190). The summary screen opens
  the same entry (pokemon_summary_screen.c:1955-1958, `Task_ShowPokedexEntryFromSummary`). The engine writes these
  tables, so the dex prints the randomizer log for your own Pokemon: upcoming moves and their levels, evolution
  targets, both abilities.
- **Rule**: Blake's standing ruling that the tracker and the game never reveal randomizer information the player could
  not see. Emerald's dex shows none of it.
- **Fix**: in a Kaizo run, refuse the stats and evolution pages (each place that switches to them:
  pokedex_plus_hgss.c:4190, 6043, 7060, 7369, 7583):

```c
         case 2:
-            if (!sPokedexListItem->owned)
+            if (!sPokedexListItem->owned || (KAIZOCORE_GEN3_BATTLE && KAIZOCORE_KAIZO_RUN))
                 PlaySE(SE_FAILURE);
```

### B6. Bill's grandpa gives 16 Exp. Candy L (160,000 EXP)

**Ruling (Blake, 2026-10-10): keep vanilla.** The odds of having all four Pokemon are low, and a Kaizo player cannot
use his items anyway. Not changed.

- **Tree 1**: data/maps/Route25_BillsHouse_hns/scripts.inc:410, 428, 449, 467 set `ITEM_EXP_CANDY_L` with quantities
  1, 3, 5 and 7 (loop at 511-516) for showing a Meowth, Zigzagoon, Ponyta/Rapidash and Farfetch'd. Exp. Candy L is
  10,000 EXP each (items.h, `EXP_10000`). The engine does not touch NPC gifts (`fieldItems` covers item balls and hidden
  items only, HnsRandomizer.kt:1859), so these ship as written.
- **Emerald Nat. Dex**: no Exp. Candy anywhere; the Nat. Dex item list (assets/hns/items-natdex-121.tsv) has none.
  HGSS's grandpa gave evolution stones. An EXP source Emerald does not have.
- **Fix** (script, Kaizo only): one item per species, as HGSS did. In `Route25_BillsHouse_EventScript_Gramps_Correctmon`:

```
 Route25_BillsHouse_EventScript_Gramps_Correctmon::
+	specialvar VAR_RESULT, KaizoCore_IsKaizoRun
+	call_if_eq VAR_RESULT, TRUE, Route25_BillsHouse_EventScript_KaizoCoreGrampsStone
 	msgbox Route25_BillsHouse_Text_ShownPokemon, MSGBOX_DEFAULT
...
+Route25_BillsHouse_EventScript_KaizoCoreGrampsStone::
+	@ KaizoCore: HGSS's rewards (Everstone, then the stones), one each, never Exp. Candy.
+	setvar VAR_TEMP_1, 1
+	call_if_eq VAR_BILLS_GRANDPA, 0, ... setvar VAR_TEMP_0, ITEM_EVERSTONE
+	... ITEM_LEAF_STONE / ITEM_WATER_STONE / ITEM_FIRE_STONE / ITEM_THUNDER_STONE by VAR_BILLS_GRANDPA
+	return
```

  (Or "engine": rewrite the four `setvar VAR_TEMP_0` operands and quantities by layout address.)

### B7. Wild encounters cycle through every species (Blake-ruled exception)

- **Tree 1**: patches 0028 and 0035, wild_encounter.c:339-520 (`KaizoCore_CycleSlot`), used for land, water, Rock
  Smash and each rod in a Kaizo run (wild_encounter.c:802, 827). Each table gives every species once, in turn, before
  any repeats, and ignores the lead's Magnet Pull / Static (which Emerald has).
- **Emerald**: slot odds 20/20/10/10/10/10/5/5/4/4/1/1 (pret `ChooseWildMonIndex_Land`); a 1% species can take
  hundreds of encounters. The cycle guarantees seeing every pivot option in a handful: an advantage Emerald does not
  give.
- **Status**: Blake asked for it (2026-10-05 and 10-06) and the 10/08 code audit lists it as holding. Recorded here
  only because the brief asks for "exactly Emerald". No fix proposed unless Blake reverses it.

---

## HARDER

### H1. The PC does not heal

- **Tree 1**: config/overworld.h:31 `OW_PC_HEAL GEN_LATEST`; pokemon_storage_system.c:6589-6590 heals a placed
  Pokemon only when `OW_PC_HEAL <= GEN_7`. Box Pokemon keep `hpLost` (pokemon.c:4985-4996), so deposit and withdraw
  changes nothing.
- **Emerald**: a boxed Pokemon has no HP field; `BoxMonToMon` + `CalculateMonStats` (pret pokemon.c) bring it back at
  full HP with no status. The Emerald game-specific rule 3 (revive a Tate & Liza HM friend by deposit and withdraw)
  depends on it.
- **Fix** (config/overworld.h, which then needs config/battle.h for the switch):

```c
-#define OW_PC_HEAL                  GEN_LATEST
+#define OW_PC_HEAL                  (KAIZOCORE_GEN3_BATTLE ? GEN_3 : GEN_LATEST) // KaizoCore: Emerald heals in the PC
```

### H2. Uncommitted evolution HP fix drops Emerald's max-HP gain

- **Uncommitted** src/evolution_scene.c (hunks at ~800 and ~1232): saves HP before the species change, then puts that
  number back after `CalculateMonStats`, capped at the new max.
- **Tree 1** does not fully heal on evolution in the code path read: `CalculateMonStats` (pokemon.c:3741-3830) keeps a
  fainted Pokemon at 0 (3810) and otherwise adds the max-HP gain (3815), which is pret Emerald's formula. No
  `HealPokemon`/`HealPlayerParty` call sits on the evolution path (the callers are listed under Unsure).
- **Effect**: with the uncommitted hunk an evolved Pokemon ends with less HP than in Emerald (it loses the
  `newMaxHP - oldMaxHP` Emerald adds).
- **Fix**: drop both `keptHp` hunks. If the heal Blake saw is found elsewhere, restore Emerald's result there:
  `hp = min(oldHp + (newMaxHP - oldMaxHP), newMaxHP)`, and 0 stays 0.

### H3. Eggs hatch at level 1

- **Tree 1**: config/pokemon.h:28 `P_EGG_HATCH_LEVEL GEN_LATEST`. Kaizo's one egg is the Violet aide's (randomized
  `giveegg`, VioletCity_PokemonCenter_hns/scripts.inc:65). Emerald hatches at 5 (Lavaridge's egg).
- **Fix**: `#define P_EGG_HATCH_LEVEL (KAIZOCORE_GEN3_BATTLE ? GEN_3 : GEN_LATEST)`. Minor.

---

## INFO

### I1. The summary screen shows exact IVs (R) and EVs (L)

pokemon_summary_screen.c:1976-1993; config/summary_screen.h:10 `P_SUMMARY_SCREEN_IV_EV_VALUES TRUE` (numbers, not
grades). Emerald shows neither. Not randomizer data, but information an Emerald player cannot see. Fix, if wanted:
`else if (JOY_NEW(R_BUTTON) && !(KAIZOCORE_GEN3_BATTLE && KAIZOCORE_KAIZO_RUN))`, same for L.

### I2. Mom buys items (player-policed)

src/mom_savings.c:37-60: Super Potion, Repel, Silk Scarf, Moon Stone, Hyper Potion, Leftovers, Choice Band, Master Ball
and berries as her balance passes thresholds. Saving from battle is off until the player turns it on (InitMomSavings,
mom_savings.c:68-75), but a deposit through her menu (NewBarkTown_PlayersHouse_1F_hns/scripts.inc:95) also runs
`Mom_CheckForGiftPurchase`. Rule: "You cannot ask an NPC to buy items for you (e.g. Mom)". Emerald has no such feature.
Fix, if wanted: `if (KAIZOCORE_KAIZO_RUN) return;` at the top of `Mom_CheckForGiftPurchase`.

### I3. Healing stations inside dungeons (player-policed)

Repeatable: SS Aqua bed (SSAqua_PlayersRoom_hns/scripts.inc:4-10) and the Fighting Dojo VIP lounge nurse
(SaffronCity_FightingDojoVIP_hns/scripts.inc:4-14). The Kaizo rule bans them in dungeons; Emerald's SS Tidal bed is
the same case. Both already branch away under the Pokemon Center challenge; adding
`specialvar VAR_RESULT, KaizoCore_IsKaizoRun` / `goto_if_eq VAR_RESULT, TRUE, SSAquaRooms_EventScript_Bed_PokecenterChallenge`
would enforce it. Lance's Rocket Hideout heal is a one-time story heal (RocketHideout_B2F_hns/scripts.inc:697-711):
allowed.

### I4. Berry trees and Apricorn trees (player-policed)

HGSS berry trees (src/berry.c) and Apricorn trees (src/apricorn_tree.c) refill. Emerald Kaizo update 1: "Berry trees
are off limits." The HGSS game rules in HnS/kaizo.md do not repeat it; held to Emerald rules, the ban applies.
Apricorns become Poke Balls, which the rules allow from any source.

### I5. Banned items can be found (player-policed, as in Emerald Nat. Dex)

Lucky Egg and Sacred Ash are in the Nat. Dex non-bad pool the engine draws from (items-natdex-121.tsv ids 197, 45),
and Ruins of Alph has a Sacred Ash item ball (RuinsOfAlph_PuzzleAndRewardChambers_hns/scripts.inc:441), which the
engine rerolls. Same exposure as a UPR Emerald run; the rule bans their use.

### I6. Releasing a Pokemon returns its held item

config/overworld.h:33 `OW_PC_RELEASE_ITEM GEN_LATEST`; pokemon_storage_system.c:6704-6716. Rule: an item on a Pokemon
that died "cannot be salvaged". Player-policed (Emerald also lets you Take an item). Fix, if wanted:
`(KAIZOCORE_GEN3_BATTLE ? GEN_3 : GEN_LATEST)`.

### I7. Item balls holding a TM are yellow (patch 0036)

Tells the player that a randomized ball is a TM before picking it up. Emerald's balls all look alike. Low stakes in
Kaizo (random TMs cannot be used). Blake's patch; noted for the no-reveal ruling.

### I8. Hidden items sparkle (0002, 0046, uncommitted item_use.c)

Every hidden item on screen is marked. Emerald needs the Itemfinder, which gives similar information more slowly.
Blake-ruled 10/08 (item 29). The uncommitted rewrite matches 0046's behaviour; take one of them when rebasing.

### I9. Friendship services Emerald does not have

Goldenrod Underground haircut brothers (paid; special `HaircutBrother1`, data/specials.inc:660) and Daisy's grooming
(PalletTown_House2_hns). They speed up friendship evolutions (220) and Return. Rules are silent; a small advantage over
Emerald. Blake to rule.

### I10. Sprite visualizer on Select in the summary screen

config/debug.h:15. Shows sprites and palettes only, no stats. Turn it off with B2.

### I11. Uncommitted forces Set battle style

Uncommitted new_game.c default `OPTIONS_BATTLE_STYLE_SET` and challenge_menu.c `KaizoCore_EnforceChallengePreset`
setting Set on every Kaizo load. See conflicts: the 10/08 ruling says the ROM does not force Set.

### I12. The HnS ruleset text will be stale

app/src/main/assets/rulesets/HnS/kaizo.md "What Heart & Soul does in KaizoCore" still says the start menu has an HM
entry ("no field move needs an HM friend") and that every Pokemon Center has a Meowth Name Rater. The uncommitted work
removes both. It also does not mention the field-HM learnability path (B4).

### I13. Small movement differences

Running indoors (config/overworld.h:5 `OW_RUNNING_INDOORS GEN_LATEST`; Emerald forbids it) and Repel reading the first
party member even when fainted (config/item.h:22 `I_REPEL_INCLUDE_FAINTED GEN_LATEST`; Emerald uses the first
non-fainted). Neither skips a trainer; a dead lead must already be stored under permadeath.

---

## OK (checked, matches Emerald or the rule)

1. **Pokemon Center heal** (0003, 0019): data/scripts/pkmn_center_nurse.inc `HealPkmnFast` is the same
   `special HealPlayerParty`, only faster.
2. **Out-of-center heals outside dungeons**: Mom (NewBarkTown_PlayersHouse_1F_hns:57), Route 26 house, National Park
   (HnS/kaizo.md: outdoors, not a dungeon), Elm's lab after the police scene (0031) and Mr. Pokemon's house
   (story heals around the lab fight; the uncommitted version heals in place instead of the out-of-center script,
   same result).
3. **Level-up HP**: Emerald's formula (pokemon.c:3810-3815).
4. **Field poison**: Gen 3 faint at 0 HP (field_poison.c:65, 148; patch 0048) as pret Emerald. Conflicts with 10/08.
5. **Day Care**: refuses in a Kaizo run (Route34_DayCare_hns/scripts.inc:294-295); the rule wants EXP from trainers
   only. It is the only Day Care with a deposit script.
6. **Party-wide Exp. Share**: off in a Kaizo run (battle_util.c:9741). The held Exp. Share S (Mr. Pokemon for the Red
   Scale, Violet aide, Goldenrod basement ball) works Gen 3-style (battle_script_commands.c:4178-4352) and is
   barred from being held by the held-items rule.
7. **EXP formula**: `B_EXP_CATCH`, `B_TRAINER_EXP_MULTIPLIER`, `B_SPLIT_EXP`, `B_SCALED_EXP`,
   `B_UNEVOLVED_EXP_MULTIPLIER` all GEN_3 (config/battle.h:20-24); catch EXP also refused in Kaizo
   (battle_main.c:3181). EXP multiplier row locked at x1.0, level cap OFF (Emerald has none). No Exp. Charm source.
8. **EVs and IVs**: vitamins stop at 100 (config/item.h:10), a stat holds 255 (config/pokemon.h:55), no guaranteed
   legendary IVs (pokemon.h:54), Max Party IVs row locked OFF. No feathers, Power items, Bottle Caps or Ability
   Capsules in HnS gifts or the Nat. Dex item pool.
9. **Nature Mints**: clerk hidden by `FLAG_MINTS_DISABLED` (GoldenrodCity_FlowerShop_hns/map.json:55), set by the
   MINTS OFF row (challenge_menu.c:2016-2020).
10. **TMs break**: `GetItemImportance` (item.c:882) with REUSABLE TMS OFF; `I_REUSABLE_TMS FALSE`.
11. **Shiny odds** 1/8192: row 21 selection 0, `sShinyOddsTable[0] = 8` (pokemon.c:86-96). No Shiny Charm reachable
    in HnS maps (only Emerald's Lilycove motel references it).
12. **Wild drops OFF**: row 23 (battle_script_commands.c:15128).
13. **Pickup**: only Pokemon sent out pick up in a Kaizo run (battle_script_commands.c:10295), so HM friends get
    nothing, as the rule requires; Emerald's table is level-banded too; items randomized by the engine
    (`pickupItems`).
14. **Friendship evolutions at 220**: `FRIENDSHIP_EVO_THRESHOLD` is 220 with `P_FRIENDSHIP_EVO_THRESHOLD GEN_3`
    (pokemon.c:84). The 160 appears only under "Make Evolutions Easier" (HnsRandomizer.kt:883), which Kaizo forbids.
    The tracker must use 220 (10/08 item 21).
15. **Trade evolutions**: always removed by the engine (HnsRandomizer.kt:194-196), as Nat. Dex 1.2.1's data has none.
16. **Catching**: ball modifiers GEN_3, `B_CRITICAL_CAPTURE FALSE` (config/battle.h:238-250, 362); no Catching Charm.
17. **No remote PC or hunting tools**: DexNav disabled (config/dexnav.h:4), no VS Seeker (config/item.h:40), no
    Poke Rider fly (overworld.h:116), match-call rematches off by the pinned default (new_game.c:132).
18. **Move Relearner**: Heart Scale required (BlackthornCity_House3_hns/scripts.inc:119), level-up moves only (every
    relearner switch in config/summary_screen.h FALSE). Tutors exist, player-policed as in Emerald.
19. **Escape Rope and Dig**: as the game has them (DIFFICULTY rows 9-10 locked to 0), as Emerald.
20. **Time savers**: 0039 and 0042 removed the warps that could leave trainers or items behind. The uncommitted
    `FLAG_KAIZOCORE_QUICK_WARP` only speeds the fade of two existing warps (lab to Mr. Pokemon's house, his house to
    Cherrygrove); Route 30's trainers stand on the way to Violet. Only debug.c can set the trainer and encounter
    disable flags.
21. **Gifts, trades and statics** are randomized by the engine (0010 tables, `statics()`, `trades()`). Starting items:
    the lab trash can is Emerald's PC Potion, rerolled (`pcItem`); the bag starts empty (sNewGamePCItems is empty).
22. **Cut cuts tall grass** (0037): Emerald's Cut does the same.
23. **Safari Zone 9,999 steps** (0005): re-entry is open anyway; no new access. In-game randomizer forced off (0001).
24. **Rare Candy Box in tree 1**: only with the Nuzlocke rare-candy option (player_pc.c:380-381). See B3 for the
    uncommitted change.

---

## Conflicts with the 2026-10-08 ruling (noted, not resolved)

1. **Field poison**: the ruling says poison clears at 1 HP and Survive Poison is forced ON. That is Gen 4's rule;
   pret Emerald faints at 0. Patch 0048 (commit text "Emerald's rules by default (Blake, 2026-10-10)") makes it faint
   and pins Survive Poison OFF (challenge_menu.c CheckConditions, SaveSelections). The code follows the newer 10/10
   note and Emerald.
2. **Sturdy, New Citrus, Survive Poison stay ON** (ruling item 31) vs 0047/0048 pinning all three to Gen 3 under
   `KAIZOCORE_GEN3_BATTLE` (challenge_menu.c:1305-1317, 2026-2032). Battle-side detail is for the battle audit.
3. **Battle style**: the ruling says the ROM starts on Shift and does not force Set; the uncommitted work defaults to
   Set and forces it on every Kaizo load (I11).
4. **HM friend** (items 25-26): neither tree implements the marked friend, and both let field HMs run from any
   Pokemon that can learn them (B4).
5. **Evolution heal** (item 20): the ruling says evolving fully heals in this build. Tree 1's evolution path does
   not (H2); the uncommitted fix removes more HP than Emerald does.

---

## Unsure

- Where the "heal if you evolve" Blake saw comes from. Read and cleared: `Task_EvolutionScene` and
  `Task_TradeEvolutionScene` (evolution_scene.c), `CalculateMonStats`, `SetMonData`/`GetMonData` for HP, and every
  `HealPlayerParty` caller in src (battle_setup.c follower-NPC and early-rival paths, overworld.c whiteout,
  post_battle_event_funcs.c Hall of Fame, union_room.c, debug.c). It may need a playtest with HP noted before and after.
- Nat. Dex Emerald 1.2.1's own field poison, PC heal, egg level and summary screen were taken as pret Emerald, not read
  from its ROM. If Nat. Dex shows IVs/EVs in its summary, I1 is OK.
- Whether NPC gift items should be rerolled by the engine. UPR Gen 3 leaves them alone, so Emerald Kaizo keeps its own
  gifts; B6 proposes changing only the Exp. Candy ones.
- Whether Bill's grandpa can be satisfied in a run at all: wild species are randomized, so a Meowth or Farfetch'd may
  not exist in the wild; a gift, trade or static could still supply one.
- Gift Pokemon as pivots, and Apricorn balls as "any type of Pokeball": rule readings, not code.
- The uncommitted tree sits on 0045. Its challenge_menu.c, new_game.c and item_use.c hunks overlap 0046-0048 and need
  a rebase before either result ships.

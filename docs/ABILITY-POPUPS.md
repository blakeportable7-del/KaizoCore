# Ability pop-ups in Heart & Soul (pokehns-expansion, CRC 09405F53)

Built from the game source at `/root/hns/pokehns-expansion` (WSL, commit 6dca3c6d, the build that matches the player ROM). Every one of the 310 ABILITY_ constants in `include/constants/abilities.h` is classified. Facts only, read from the code; nothing was run on the ROM.

## How a pop-up happens

- The only caller of `CreateAbilityPopUp` is `BS_ShowAbilityPopup` (src/battle_script_commands.c:14385-14390, the `showabilitypopup` macro). `showabilitypopup` is used in exactly two places, both inside the pop-up scripts (data/battle_scripts_1.s:6021 and 6033). So "one of the four pop-up scripts is executing" catches every pop-up in this build.
- The four scripts are contiguous in the ROM: `BattleScript_AbilityPopUpTarget` 0x0829240B (falls through into) `BattleScript_AbilityPopUp` 0x08292415, `BattleScript_AbilityPopUpScripting` 0x08292432 (does `goto BattleScript_AbilityPopUp`), `BattleScript_AbilityPopUpOverwriteThenNormal` 0x08292441 (size 0x30, calls `showabilitypopup` itself). Addresses from /root/hns-syms.txt.
- No config flag gates pop-ups. `B_ABILITY_POP_UP` does not exist in include/config/battle.h, and `CreateAbilityPopUp` (src/battle_interface.c:2816) has no challengeSettings check. The only HnS challengeSettings gate on an ability is `tx_Mode_Sturdy` (src/battle_util.c:8206).
- All battle configs used below are static (`GetConfig` reads compile-time values, src/generational_changes.c:37); the relevant ones are GEN_LATEST = GEN_9 (B_UPDATED_INTIMIDATE, B_ILLUMINATE_EFFECT, B_OBLIVIOUS_TAUNT, B_REDIRECT_ABILITY_IMMUNITY, B_STURDY, B_POWDER_OVERCOAT, B_SNOW_WARNING, B_INTREPID_SWORD, B_DAUNTLESS_SHIELD, B_BATTLE_BOND, B_PROTEAN_LIBERO, B_STANCE_CHANGE_FAIL).
- `AbilityBattleEffects` returns at once in Safari battles (src/battle_util.c:3078), so no ability in that function pops up there.
- Switch-in abilities run with `shouldAbilityTrigger = battlerState.switchIn` (src/battle_switch_in.c:272), and again through `switchinabilities` after Trace, Receiver, Skill Swap, form changes and Neutralizing Gas leaving (src/battle_script_commands.c:14065-14081).

Two rules explain most of the "some" rows:

1. **Status immunities pop up only when a status move is aimed at them.** `CanSetNonVolatileStatus` runs the blocking script only with `RUN_SCRIPT` (status moves, Yawn; src/battle_script_commands.c:8113-8124). Added effects of damaging moves use `CHECK_TRIGGER` (src/battle_script_commands.c:2514-2520) and contact abilities check `CanBeParalyzed` etc. before pushing anything, so those blocks are silent (src/battle_util.c:5557-5573).
2. **Stat-drop blockers pop up only for status moves, Intimidate and other `ALLOW_PTR` script drops.** `ChangeStatBuffs` pushes the blocker script only when `flags.allowPtr` is set (src/battle_script_commands.c:7827-7910). `SetMoveEffect` stat drops from damaging moves leave it unset (src/battle_script_commands.c:2694-2716), except for Mirror Armor.

Legend for evidence: `bs1` = data/battle_scripts_1.s; every `.c` file is in src/. "-> bs1:N" means the C code pushes the script that starts at that line.

Counts: 198 show a pop-up (155 every time they act, 43 only on some triggers), 112 never show one, 0 unsure.

## 1. SHOWS A POP-UP

"yes" = every time the ability changes something in battle, a pop-up runs first (or it already announced itself on switch-in and its later effect is passive). "some" = it also acts in ways that show no pop-up; the silent cases are named.

| Ability | Shows | When the pop-up shows (the trigger, in plain words) | Script / source file:line |
|---|---|---|---|
| Drizzle | yes | On switch-in when it starts rain, or when primal weather blocks it. | battle_util.c:3357-3370; bs1:4427-4429 (WeatherAbilityActivates), bs1:3378 (BlockedByPrimalWeather) |
| Speed Boost | yes | End of turn when Speed rises (not on the turn it came in, not at +6). | battle_util.c:3741-3750; bs1:6811 -> bs1:6789-6791 (AttackerAbilityStatRaise) |
| Sturdy | some | Always when it makes an OHKO move fail (SturdyPreventsOHKO). Surviving a hit from full HP at 1 HP shows the pop-up (SturdiedMsg) only when challengeSettings.tx_Mode_Sturdy == 1; with the setting off, that effect does not exist at all. | battle_script_commands.c:1216-1221; bs1:6500-6502; battle_util.c:8206-8211; battle_script_commands.c:1390-1393, 2100; bs1:5357-5359 |
| Damp | yes | When a foe tries Explosion, Self-Destruct, Mind Blown and the like, and when it stops Aftermath (both the Aftermath and the Damp holder get a pop-up). | battle_move_resolution.c:1459-1469; bs1:6507-6509; battle_util.c:4076-4080; bs1:5616-5621 |
| Limber | some | Pop-up when a paralysis status move is aimed at it (ImmunityProtected) and when it cures its own paralysis (AbilityCuredStatus). Silent: blocking paralysis from a damaging move's added effect, Static, Effect Spore and similar (CHECK_TRIGGER path pushes no script). | battle_util.c:5435-5439, 5557-5573, 9081-9087, 9124-9146; battle_script_commands.c:2514-2520 |
| Static | yes | Contact hit, 30%, attacker can be paralyzed. | battle_util.c:4171-4192; bs1:6930-6931 (AbilityStatusEffect) |
| Volt Absorb | yes | Hit by an Electric move: heals, or "no effect" at full HP; both show the pop-up. | battle_util.c:2447-2450, 2511-2531; bs1:6517-6518 (MoveHPDrain), bs1:6536-6537 (MonMadeMoveUseless) |
| Water Absorb | yes | Hit by a Water move: heals, or "no effect" at full HP. | battle_util.c:2451-2455, 2511-2531 |
| Oblivious | some | Pop-up when Attract, Captivate or Taunt is used on it (NotAffectedAbilityPopUp), when it blocks Intimidate (Gen 8+ config), and when it cures infatuation or Taunt it somehow has. Silent: stopping Cute Charm on the attacker (only recorded) and the BS_TrySetInfatuation check. | battle_script_commands.c:8502-8507, 9710-9714, 11612-11616, 13357-13368; battle_util.c:9114-9119, 4217; battle_script_commands.c:13247 |
| Cloud Nine | yes | On switch-in (announces). Its weather suppression afterwards has no further pop-up. | battle_util.c:3476-3483, 10095; bs1:7452 |
| Insomnia | some | Pop-up when a sleep status move (or Yawn) is aimed at it (PrintAbilityMadeIneffective), when its own Rest fails (InsomniaProtects), and when it cures its own sleep. Silent: Yawn's end-of-turn check, sleep from added effects. | battle_util.c:5485-5489, 9088-9097; battle_move_resolution.c:1202-1205; bs1:2616, 2321-2323; battle_end_turn.c:870 |
| Color Change | yes | Hit by a damaging move of a type it does not already have. | battle_util.c:3853-3867; bs1:6648 |
| Immunity | some | Pop-up when a poison status move is aimed at it and when it cures its own poison. Silent: added-effect poison, Poison Point, Toxic Spikes. | battle_util.c:5416-5420, 9066-9073 |
| Flash Fire | yes | Hit by a Fire move (FlashFireBoost, both "boosted" and "already boosted"). The later damage boost is silent but only exists after the pop-up. | battle_util.c:2484-2487, 2547-2559, 7009; bs1:6543-6544 |
| Own Tempo | some | Pop-up when a confusing status move hits it (OwnTempoPrevents), when it blocks Intimidate (Gen 8+), when it cures its own confusion, and when it stops Berserk Gene confusion. Silent: confusion from added effects and rampage fatigue (CanBeConfused), and release from Sky Drop. | bs1:2679, 3232, 3576 -> bs1:6588-6589; battle_script_commands.c:13359; battle_util.c:9074-9080, 5606; bs1:8162; battle_main.c:3611 |
| Suction Cups | some | Pop-up vs Roar/Whirlwind and vs Dragon Tail/Circle Throw. Silent: Red Card (prints the anchor text with no pop-up). | bs1:2545 -> bs1:6549-6555; battle_move_resolution.c:3117-3120; bs1:7679 -> bs1:7691 |
| Intimidate | yes | On switch-in when a foe is on the field. | battle_util.c:3454-3463; bs1:6204-6206 |
| Rough Skin | yes | Contact hit; the pop-up shows even when the attacker has Magic Guard and takes no damage. | battle_util.c:4050-4069; bs1:6871 |
| Wonder Guard | yes | Blocks a damaging move that is not super effective (NoEffectivenessAbility). Status moves are not blocked. | battle_util.c:8441-8453; battle_move_resolution.c:1794-1801; bs1:7749-7750 |
| Levitate | some | Pop-up when a damaging Ground move is blocked. Silent: ungrounded for Spikes, Toxic Spikes, Sticky Web, terrain and Arena Trap. | battle_util.c:8399-8411, 6017; battle_move_resolution.c:1794-1801 |
| Effect Spore | yes | Contact hit, random, attacker affected by powder. | battle_util.c:4105-4146; bs1:6930 |
| Synchronize | yes | A foe gives it burn, poison or paralysis; the pop-up shows even when the status cannot be passed back. | battle_util.c:4670-4736; bs1:6975-6976 |
| Clear Body | some | Pop-up when it blocks a drop from a status move, Intimidate, or any script stat drop flagged ALLOW_PTR (Sticky Web, Parting Shot, Octolock...). Silent: drops that are added effects of damaging moves (Icy Wind, Crunch, Moonblast...), because SetMoveEffect calls ChangeStatBuffs without allowPtr. | battle_script_commands.c:7827-7858, 11801-11808, 2694-2716; bs1:6560-6562 |
| Lightning Rod | yes | Absorbs an Electric move (Sp. Atk +1 or "no effect"). The redirect itself prints "took the attack" without a pop-up, then the absorb pop-up follows. | battle_util.c:2464-2467; battle_move_resolution.c:835-852, 1697-1704 |
| Illuminate | some | Gen 9 config: pop-up when it blocks an accuracy drop (same ALLOW_PTR rule as Clear Body). Ignoring evasion is silent. | battle_script_commands.c:7881-7890; battle_util.c:10484 |
| Trace | yes | On switch-in when a traceable foe exists; then the copied ability's own switch-in effect runs. | battle_util.c:3100-3137; bs1:6096-6097 |
| Poison Point | yes | Contact hit, 30%, attacker can be poisoned. | battle_util.c:4149-4170 |
| Inner Focus | some | Pop-up when a move whose flinch is primary or 100% (Fake Out) would flinch it, and when it blocks Intimidate (Gen 8+). Silent: chance flinches (Bite, Stench, King's Rock), which just fail. | battle_script_commands.c:2555-2571, 3695, 13357; bs1:6580-6582 |
| Magma Armor | some | Pop-up only when it cures its own freeze or frostbite. Blocking a freeze uses BattleScript_NotAffected (no pop-up), and freezes come only from added effects (no script). | battle_util.c:5501-5505, 9107-9113 |
| Water Veil | some | Pop-up when a burn status move is aimed at it and when it cures its own burn. Silent: added-effect burns, Flame Body. | battle_util.c:5450-5454, 9098-9106 |
| Soundproof | some | Pop-up when a sound move is aimed at it (SoundproofProtected). Silent: the Heal Bell partner check (HealBellSoundproof has no pop-up), Uproar, Poke Flute. | battle_util.c:2488-2491; bs1:6596-6597; bs1:3067 -> bs1:3070; battle_script_commands.c:12813-12823, 7522 |
| Rain Dish | yes | End of turn in rain when not at full HP. | battle_util.c:3694-3704; bs1:6112-6122 |
| Sand Stream | yes | On switch-in when it starts sandstorm, or when primal weather blocks it. | battle_util.c:3371-3384 |
| Pressure | yes | On switch-in (announces). Extra PP use afterwards is silent. | battle_util.c:3309-3316; battle_move_resolution.c:957-962; bs1:6817 |
| Flame Body | yes | Contact hit, 30%, attacker can be burned. | battle_util.c:4193-4208 |
| Keen Eye | some | Pop-up when it blocks an accuracy drop from a status move or ALLOW_PTR script. Silent: ignoring evasion, and accuracy drops that are added effects (Mud-Slap). | battle_script_commands.c:7879-7894; battle_util.c:10483 |
| Hyper Cutter | some | Pop-up when it blocks an Attack drop from a status move or Intimidate. Silent: Attack drops that are added effects of damaging moves. | battle_script_commands.c:7882-7894; bs1:6216 |
| Pickup | yes | End of turn in battle when it picks up an item another battler used. The after-battle item pickup is silent. | battle_util.c:3658-3667; bs1:6131-6134; battle_script_commands.c:10295 |
| Truant | yes | On each loafing turn (TruantLoafingAround). The turn-counter toggle is invisible. | battle_move_resolution.c:251-263; bs1:7028-7030; battle_util.c:3781-3783 |
| Cute Charm | yes | Contact hit, 30%, opposite gender, no Oblivious or Aroma Veil. | battle_util.c:4209-4225; bs1:6912 |
| Forecast | yes | Castform changes form when the weather changes. | battle_util.c:4852-4873; bs1:5486-5488 |
| Sticky Hold | some | Pop-up vs Trick/Switcheroo, Knock Off, Thief/Covet, Corrosive Gas and Pickpocket. Silent: Magician simply fails, Bug Bite/Pluck and Incinerate skip the item. | battle_script_commands.c:9807-9813; battle_move_resolution.c:3046-3052, 3086-3092; bs1:366, 7641-7652; battle_util.c:4541-4545; battle_script_commands.c:2928, 2947 |
| Shed Skin | yes | End of turn, 1 in 3, cures its status. | battle_util.c:3712-3740; bs1:6169 |
| Liquid Ooze | yes | A drain move hits it, or Leech Seed drains it. | battle_move_resolution.c:2167-2176; battle_end_turn.c:448-454; bs1:2345, 4595 |
| Drought | yes | On switch-in when it starts sun, or when primal weather blocks it. | battle_util.c:3385-3399 |
| Vital Spirit | some | Same as Insomnia: sleep status move or Yawn, failed Rest, curing sleep. Yawn's end-of-turn check is silent. | battle_util.c:5485-5489, 9089-9097; battle_move_resolution.c:1203; bs1:2617; battle_end_turn.c:870 |
| White Smoke | some | Same as Clear Body. | battle_script_commands.c:7827-7858, 11807 |
| Air Lock | yes | On switch-in (announces). Weather suppression afterwards is silent. | battle_util.c:3477-3483, 10096 |
| Motor Drive | yes | Absorbs an Electric move (Speed +1 or "no effect"). | battle_util.c:2460-2463, 2534-2545 |
| Steadfast | yes | When it flinches and Speed can still rise (no pop-up at +6). | bs1:5726, 5729-5734; battle_move_resolution.c:303 |
| Anger Point | yes | Hit by a critical hit while Attack is below +6. | battle_util.c:4025-4035; bs1:6716 |
| Dry Skin | some | Pop-up when it absorbs a Water move, heals in rain, and loses HP in sun (SolarPowerActivates). Silent: the extra Fire damage it takes. | battle_util.c:2452-2455, 3690-3704, 3795-3802, 6799-6802 |
| Download | yes | On switch-in when the chosen stat can rise. | battle_util.c:3272-3308; bs1:6806 -> 6789-6791 |
| Poison Heal | some | Pop-up at end of turn when it heals while poisoned. Silent: at full HP (or under Heal Block) it takes no poison damage and shows nothing. | battle_end_turn.c:484-492; bs1:5664-5665 |
| Hydration | yes | End of turn in rain while statused, cures it. | battle_util.c:3705-3711, 3716-3739; battle_end_turn.c:372 |
| Solar Power | some | Pop-up when it loses HP at end of turn in sun. Silent: the Sp. Atk boost, which applies to its attacks before the first end-of-turn pop-up. | battle_util.c:3795-3803, 7001-7004 |
| Leaf Guard | some | In sun, pop-up when a status move is aimed at it (AbilityProtectsDoesntAffect). Silent: Yawn's end-of-turn check and its own Rest failing. | battle_util.c:5525-5529; battle_script_commands.c:7238, 14950; battle_end_turn.c:873 |
| Mold Breaker | yes | On switch-in (announces). Ignoring abilities afterwards is silent. | battle_util.c:3163-3170, 4965 |
| Aftermath | yes | Knocked out by a contact move (also when Damp stops it: both pop-ups show). | battle_util.c:4070-4089; bs1:5604-5605, 5616-5620 |
| Anticipation | yes | On switch-in, only if a foe has a super-effective or OHKO move; otherwise nothing happens. | battle_util.c:3218-3255 |
| Forewarn | yes | On switch-in when a foe is present. | battle_util.c:3263-3271 |
| Slow Start | yes | On switch-in (announces) and when it ends; the halving in between is silent. | battle_util.c:3187-3195, 3784-3790, 6997-7000; battle_main.c:4966 |
| Scrappy | some | Pop-up when it blocks Intimidate (Gen 8+). Silent: hitting Ghost types with Normal/Fighting moves. | battle_script_commands.c:13358-13368; battle_util.c:8288 |
| Storm Drain | yes | Absorbs a Water move (Sp. Atk +1 or "no effect"); the redirect text has no pop-up. | battle_util.c:2468-2471; battle_move_resolution.c:836-852 |
| Ice Body | some | Pop-up when it heals in hail/snow. Silent: hail damage immunity (also at full HP nothing shows). | battle_util.c:3678-3689; battle_end_turn.c:155-161; bs1:4470 |
| Snow Warning | yes | On switch-in when it starts snow (Gen 9 config), or when primal weather blocks it. | battle_util.c:3400-3416 |
| Frisk | yes | On switch-in when a foe holds an item (one pop-up even if it reveals two items). | battle_util.c:3256-3262; battle_script_commands.c:13665-13693; bs1:6834-6836 |
| Flower Gift | yes | Cherrim changes form when sun starts or ends. The stat boosts for its side are silent but only exist after that pop-up. | battle_util.c:4853-4873, 7047, 7149, 7306, 7337 |
| Bad Dreams | yes | End of turn when a foe is asleep (or has Comatose); one pop-up per turn. | battle_util.c:3791-3794; bs1:6459-6488 |
| Pickpocket | yes | Hit by contact while holding nothing and the attacker's item can be taken (Sticky Hold shows its own pop-up after it). | battle_move_resolution.c:3475-3513; bs1:7640-7652 |
| Unnerve | yes | On switch-in (announces). Blocking berries afterwards is silent. | battle_util.c:4759-4769, 364; bs1:6817 |
| Defiant | yes | A foe lowers any of its stats (status move, added effect, Intimidate, Sticky Web) and Attack can rise. At +6 it prints "won't go higher" with no pop-up. | battle_util.c:1273-1282, 1205-1218; bs1:5990-6006 |
| Cursed Body | yes | 30% when hit, disables the move (Aroma Veil on the attacker's side stops it silently). | battle_util.c:3959-3973; bs1:6673 |
| Healer | yes | Doubles: end of turn, 30%, cures the partner's status. | battle_util.c:3804-3813 |
| Weak Armor | yes | Hit by a physical move while Speed can rise or Defense can fall. | battle_util.c:3945-3958; bs1:6745 |
| Harvest | yes | End of turn (sun, or 50%) when it restores a used berry. | battle_util.c:3668-3677; bs1:6141-6144 |
| Telepathy | yes | Blocks an ally's damaging move (NoEffectivenessAbility). | battle_util.c:8441-8453; battle_move_resolution.c:1794-1801 |
| Moody | yes | Every end of turn. | battle_util.c:3751-3778; bs1:6047-6048 |
| Overcoat | some | Pop-up when a powder or spore move is aimed at it. Silent: sandstorm/hail damage immunity and Effect Spore immunity. | battle_util.c:2424-2439, 10754; bs1:5708-5716; battle_end_turn.c:142, 165 |
| Poison Touch | yes | Its contact hit, 30%, target can be poisoned. | battle_util.c:4346-4362 |
| Big Pecks | some | Pop-up when it blocks a Defense drop from a status move or ALLOW_PTR script. Silent: Defense drops that are added effects. | battle_script_commands.c:7883-7894 |
| Imposter | yes | On switch-in when it can transform into the facing foe. | battle_util.c:3138-3162; bs1:6851-6852 |
| Mummy | yes | Contact hit: pop-up for Mummy, then the attacker's pop-up shows its old ability and then Mummy. | battle_util.c:3974-3997; bs1:6678-6684 |
| Moxie | yes | It knocks out a foe and Attack can rise. | battle_util.c:4556-4589; bs1:6779-6782 |
| Justified | yes | Hit by a Dark move while Attack can rise. | battle_util.c:3897-3908; bs1:7823-7825 |
| Rattled | yes | Hit by a Dark, Bug or Ghost move, or Intimidated (Gen 8+), while Speed can rise. | battle_util.c:3909-3920, 1251-1259 |
| Magic Bounce | yes | Reflects a bounceable status move. | battle_move_resolution.c:4233-4248; battle_script_commands.c:1037-1043; bs1:5320-5322 |
| Sap Sipper | yes | Absorbs a Grass move (Attack +1 or "no effect"). | battle_util.c:2472-2475 |
| Iron Barbs | yes | Contact hit (same as Rough Skin). | battle_util.c:4051-4069 |
| Zen Mode | yes | Darmanitan changes form at end of turn (HP above/below half). | battle_end_turn.c:1335-1341; bs1:5458 |
| Turboblaze | yes | On switch-in (announces); ignoring abilities afterwards is silent. | battle_util.c:3179-3186, 4967 |
| Teravolt | yes | On switch-in (announces); ignoring abilities afterwards is silent. | battle_util.c:3171-3178, 4966 |
| Aroma Veil | some | Pop-up when it protects its side from Taunt, Torment, Encore, Disable, Heal Block, Attract or Psychic Noise. Silent: it stops Cursed Body and Cute Charm on the attacker's side without a pop-up. | bs1:2181, 2972, 2984, 3270, 3555, 3678 -> bs1:2305-2311; battle_script_commands.c:3057-3064; battle_util.c:3963, 4219; battle_script_commands.c:13248, 13284 |
| Flower Veil | some | Pop-up when a status move or a status-move stat drop targets a Grass ally. Silent: stat drops that are added effects. | battle_util.c:5535-5541; battle_script_commands.c:7859-7878; bs1:2273-2281 |
| Cheek Pouch | yes | After eating a berry, when not at full HP. | battle_script_commands.c:6574-6589; bs1:6125-6127 |
| Protean | yes | Changes type before using a move (once per switch-in, Gen 9 config). | battle_move_resolution.c:1442-1457; battle_script_commands.c:943-957; bs1:6653-6655 |
| Magician | yes | Steals an item after its hit. If the target has Sticky Hold it simply fails, no pop-up. | battle_util.c:4485-4555; bs1:7810-7811 |
| Bulletproof | yes | A ball or bomb move is aimed at it. | battle_util.c:2492-2495 |
| Competitive | yes | Same rule as Defiant, raising Sp. Atk. | battle_util.c:1273-1282 |
| Sweet Veil | some | Pop-up when a sleep status move or Yawn targets its side, and when Yawn's sleep is blocked at end of turn. Silent: a Rest on its side just fails. | battle_util.c:5478-5484; battle_end_turn.c:891-898; battle_script_commands.c:14955; bs1:2641-2645 |
| Stance Change | yes | Aegislash changes form before an attacking move or King's Shield. | battle_move_resolution.c:48-60, 608-612; bs1:5442-5444 |
| Symbiosis | yes | Doubles: passes its item to an ally that used up its own. | battle_util.c:10218-10240; bs1:7815-7816 |
| Gooey | yes | Contact hit while the attacker's Speed can fall (or it has Mirror Armor). | battle_util.c:4036-4049; bs1:6919-6922 |
| Dark Aura | yes | On switch-in (announces); the power change is silent. | battle_util.c:3317-3324, 6757 |
| Fairy Aura | yes | On switch-in (announces); the power change is silent. | battle_util.c:3325-3332, 6758 |
| Aura Break | yes | On switch-in (announces); the reversal is silent. | battle_util.c:3333-3340, 6760 |
| Primordial Sea | yes | On switch-in when it starts heavy rain. Fire moves it blocks print a weather message with no pop-up. | battle_util.c:3538-3546; bs1:6293-6297 |
| Desolate Land | yes | On switch-in when it starts harsh sun. Water moves it blocks print a weather message with no pop-up. | battle_util.c:3529-3537; bs1:6293-6297 |
| Delta Stream | yes | On switch-in when it starts strong winds. | battle_util.c:3547-3555 |
| Stamina | yes | Hit by a damaging move while Defense can rise. | battle_util.c:3933-3944 |
| Wimp Out | yes | Drops below half HP and can switch (after a hit, at end of turn, or on hazards at switch-in). | battle_move_resolution.c:3405-3455; battle_end_turn.c:184-201; battle_switch_in.c:248-255; bs1:6064-6066 |
| Emergency Exit | yes | Same as Wimp Out. | battle_util.c:10189-10204; battle_move_resolution.c:3405-3455 |
| Water Compaction | yes | Hit by a Water move while Defense can rise. | battle_util.c:3921-3932 |
| Shields Down | some | Pop-up when Minior changes form on switch-in or at end of turn, and when a status move is aimed at it in Meteor form. Silent: Rest fails in Meteor form. | battle_util.c:3642-3651, 5530-5534; battle_end_turn.c:1322-1344; battle_script_commands.c:14952 |
| Water Bubble | some | Pop-up when a burn status move is aimed at it and when it cures its own burn. Silent: the Fire damage halving and Water power doubling. | battle_util.c:5450-5454, 9099-9106, 6709-6712, 6791-6798 |
| Berserk | yes | A hit drops it below half HP and Sp. Atk can rise. | battle_util.c:3868-3879; bs1:4918-4920 |
| Schooling | yes | Wishiwashi changes form on switch-in and at end of turn. | battle_util.c:3642-3651; battle_end_turn.c:1322-1344; bs1:5482-5484 |
| Disguise | yes | Mimikyu's disguise breaks from a hit or a confusion self-hit. | battle_util.c:4439-4443; battle_script_commands.c:4979-4983; bs1:5468 |
| Battle Bond | yes | Gen 9 config: once per battle, after it knocks out a foe, raises Atk, Sp. Atk and Speed. | battle_util.c:4590-4643; bs1:6948 |
| Power Construct | yes | End of turn at half HP or less. | battle_end_turn.c:1329-1330; bs1:5415-5419 |
| Comatose | yes | On switch-in (announces); also when a status move is aimed at it. Acting as asleep afterwards (Hex, Dream Eater, Sleep Talk, Bad Dreams, its own Rest) is silent. | battle_util.c:3341-3348, 5515-5520; battle_move_resolution.c:1198 |
| Queenly Majesty | yes | A foe aims a priority move at its side. | battle_move_resolution.c:1403-1440; bs1:6606-6608 |
| Innards Out | yes | Knocked out by a damaging move. | battle_util.c:4090-4104 |
| Dancer | yes | Copies a dance move used by another battler. | battle_util.c:4455-4477; bs1:6970 |
| Dazzling | yes | A foe aims a priority move at its side. | battle_move_resolution.c:1391-1440 |
| Soul-Heart | yes | Any battler faints and Sp. Atk can rise. | battle_script_commands.c:14148-14168; bs1:6733-6734 |
| Tangling Hair | yes | Same as Gooey. | battle_util.c:4037-4049 |
| Receiver | yes | Doubles: copies a fainted ally's copyable ability. | battle_script_commands.c:14124-14146; bs1:6105 |
| Power of Alchemy | yes | Same as Receiver. | battle_script_commands.c:14124-14146 |
| Beast Boost | yes | It knocks out a foe and its highest stat can rise. | battle_util.c:4561-4589 |
| Electric Surge | yes | On switch-in when it sets Electric Terrain. | battle_util.c:3417-3426; bs1:6424-6425 |
| Psychic Surge | yes | On switch-in when it sets Psychic Terrain. | battle_util.c:3445-3453 |
| Misty Surge | yes | On switch-in when it sets Misty Terrain. | battle_util.c:3436-3444 |
| Grassy Surge | yes | On switch-in when it sets Grassy Terrain. | battle_util.c:3427-3435 |
| Full Metal Body | some | Same as Clear Body. | battle_script_commands.c:7827-7858, 11806 |
| Intrepid Sword | yes | On switch-in, once per battle (Gen 9 config), if Attack can rise. | battle_util.c:3491-3504; bs1:6724 |
| Dauntless Shield | yes | On switch-in, once per battle (Gen 9 config), if Defense can rise. | battle_util.c:3505-3518 |
| Libero | yes | Same as Protean. | battle_move_resolution.c:1442-1457; battle_script_commands.c:945 |
| Ball Fetch | yes | End of turn, fetches the first failed Poke Ball once per battle. | battle_util.c:3814-3829; bs1:5510-5511 |
| Cotton Down | yes | Every time it is hit by a damaging move. | battle_util.c:4234-4242; bs1:5570-5572 |
| Mirror Armor | yes | Reflects a stat drop from a foe: status moves, Intimidate, added effects (allowPtr is set for Mirror Armor) and Sticky Web. | battle_script_commands.c:7896-7910, 2702, 2758; bs1:2487-2488, 4837, 2513 |
| Gulp Missile | some | Pop-up when it is hit while Gulping or Gorging. Silent: catching prey with Surf or Dive (instant form change, no script). | battle_util.c:4405-4438; battle_script_commands.c:1653-1657; bs1:4863, 4883 |
| Steam Engine | yes | Hit by a Fire or Water move while Speed can rise. | battle_util.c:4243-4254 |
| Sand Spit | yes | Hit by a damaging move when there is no sandstorm (or primal weather blocks it). | battle_util.c:4255-4272; bs1:4427-4430 |
| Ripen | some | Pop-up when it doubles a berry: HP berries, Leppa, confusion berries, stat berries, Micle, and Jaboca/Rowap on the holder of the berry. Silent: resist-berry halving and Micle's accuracy doubling. | bs1:7118-7121, 7132-7135, 7222-7225, 7238-7241, 7518-7521, 7530-7533; battle_util.c:7698, 10590 |
| Ice Face | yes | Eiscue blocks a physical hit, and re-forms when hail/snow starts. | battle_util.c:4444-4446, 4854-4873; bs1:6603 |
| Mimicry | yes | Type change when terrain is set; the revert when terrain ends is silent. | battle_util.c:4899-4908, 1812-1820; bs1:6398-6399 |
| Screen Cleaner | yes | On switch-in when it removes screens. | battle_util.c:3349-3356 |
| Perish Body | yes | Contact hit while the attacker has no Perish count. | battle_util.c:4273-4290; bs1:4857 |
| Wandering Spirit | yes | Contact hit: two pop-ups, each showing the old ability then the new one. | battle_util.c:3998-4024; bs1:6695-6702 |
| Neutralizing Gas | yes | On switch-in (announces). Its exit message ("wore off") has no pop-up. | battle_util.c:4746-4755; battle_script_commands.c:10215-10223; bs1:7789-7808 |
| Pastel Veil | some | On switch-in only if it cures a poisoned battler on its side (one pop-up). Also when a poison status move targets its side, and when it cures its own poison. Silent: added-effect poison. | battle_util.c:3208-3217, 5409-5415, 9067-9073; bs1:7764-7773 |
| Quick Draw | yes | When it lets it move first (random, damaging move chosen). | battle_main.c:5475, 5485, 5526-5533; bs1:7501-7503 |
| Curious Medicine | yes | Doubles: on switch-in when the partner has stat changes. | battle_util.c:3196-3207 |
| Chilling Neigh | yes | It knocks out a foe and Attack can rise. | battle_util.c:4557-4589 |
| Grim Neigh | yes | It knocks out a foe and Sp. Atk can rise. | battle_util.c:4559-4589 |
| As One (Ice Rider) | yes | On switch-in: an As One pop-up and then a second pop-up labelled Unnerve. After a KO the pop-up is labelled Chilling Neigh. | battle_util.c:4770-4781, 4577-4578; bs1:6822-6832 |
| As One (Shadow Rider) | yes | On switch-in: As One then Unnerve pop-ups. After a KO the pop-up is labelled Grim Neigh. | battle_util.c:4771-4781, 4579-4580; bs1:6822-6832 |
| Lingering Aroma | yes | Same as Mummy. | battle_util.c:3974-3997 |
| Seed Sower | yes | Hit by a damaging move when Grassy Terrain can be set. | battle_util.c:4291-4299; bs1:4909-4910 |
| Thermal Exchange | some | Pop-up when hit by a Fire move (Attack +1), when a burn status move is aimed at it, and when it cures its own burn. Silent: added-effect burns. | battle_util.c:4300-4311, 5455-5459, 9100-9106 |
| Anger Shell | yes | A hit drops it below half HP. | battle_util.c:3880-3889; bs1:4928 |
| Purifying Salt | some | Pop-up when a status move is aimed at it and when its own Rest fails. Silent: halving Ghost damage. | battle_util.c:5515-5520, 7132-7139; battle_move_resolution.c:1204; bs1:2618 |
| Well-Baked Body | yes | Absorbs a Fire move (Defense +2 or "no effect"). | battle_util.c:2476-2479 |
| Wind Rider | yes | Hit by a wind move (absorb), on switch-in under its side's Tailwind, or when an ally sets Tailwind. | battle_util.c:2480-2483, 3519-3528; battle_script_commands.c:12879-12898; bs1:2100-2113 |
| Guard Dog | some | Pop-up when Intimidate is used on it (Attack rises instead). Silent: Roar/Whirlwind just fail, Dragon Tail/Circle Throw and Red Card do not switch it out. | battle_script_commands.c:13371-13374; bs1:6247-6249, 2544; battle_move_resolution.c:3114-3115, 3305 |
| Wind Power | yes | Hit by a wind move, or an ally sets Tailwind. | battle_util.c:4312-4323; bs1:2104, 2115-2117, 4948 |
| Zero to Hero | some | Pop-up when it switches back in as Hero Palafin (once). Silent: the change into Hero form on switching out. | battle_util.c:3603-3611; battle_script_commands.c:5939; bs1:6336-6337 |
| Commander | yes | Doubles: Tatsugiri enters its Dondozo partner. | battle_util.c:4791-4811; bs1:6343-6344 |
| Electromorphosis | yes | Every time it is hit by a damaging move. | battle_util.c:4316-4323 |
| Protosynthesis | yes | When sun is active for it (switch-in or sun starts) or Booster Energy activates; the stat boost is silent after that. | battle_util.c:4875-4888; battle_hold_effects.c:60-80; bs1:6299-6300, 8181-8182 |
| Quark Drive | yes | When Electric Terrain is active for it or Booster Energy activates. | battle_util.c:4909-4922; battle_hold_effects.c:60-80; bs1:6308 |
| Good as Gold | yes | A status move is aimed at it. | battle_util.c:2496-2503; bs1:7757-7758 |
| Vessel of Ruin | yes | On switch-in (announces); the stat lowering is silent. | battle_util.c:3556-3564; bs1:6316 |
| Sword of Ruin | yes | On switch-in (announces). | battle_util.c:3565-3573 |
| Tablets of Ruin | yes | On switch-in (announces). | battle_util.c:3574-3582 |
| Beads of Ruin | yes | On switch-in (announces). | battle_util.c:3583-3591 |
| Orichalcum Pulse | some | Pop-up on switch-in when it sets sun or primal weather blocks it. Silent: the Attack boost in sun that was already there (no weather change, no pop-up). | battle_util.c:3385-3399, 7108-7112 |
| Hadron Engine | some | Pop-up on switch-in when it sets Electric Terrain. Silent: the Sp. Atk boost in Electric Terrain that was already there. | battle_util.c:3418-3426, 7113-7116 |
| Opportunist | yes | Copies a foe's stat boosts. | battle_util.c:4651-4660; bs1:7424 |
| Cud Chew | yes | End of the next turn after it ate a berry, eats it again. | battle_util.c:3830-3844; bs1:5517-5518 |
| Supreme Overlord | yes | On switch-in when allies have fainted; the power boost is silent after that. | battle_util.c:3592-3602, 6749-6751 |
| Costar | yes | Doubles: on switch-in when the partner has stat changes to copy. | battle_util.c:4825-4841 |
| Toxic Debris | yes | Hit by a physical move while toxic spikes can be added. | battle_util.c:4324-4338; bs1:4955 |
| Armor Tail | yes | A foe aims a priority move at its side. | battle_move_resolution.c:1397, 1403-1440 |
| Earth Eater | yes | Absorbs a Ground move (heals or "no effect"). | battle_util.c:2456-2459 |
| Hospitality | yes | Doubles: on switch-in heals a damaged partner. | battle_util.c:4812-4824 |
| Mind's Eye | some | Pop-up when it blocks an accuracy drop (status move or ALLOW_PTR script). Silent: ignoring evasion and hitting Ghost types. | battle_script_commands.c:7880-7894; battle_util.c:8288, 10483 |
| Embody Aspect (Teal) | yes | On switch-in, once, if Speed can rise. | battle_util.c:3612-3637 |
| Embody Aspect (Hearthflame) | yes | On switch-in, once, if Attack can rise. | battle_util.c:3613-3637 |
| Embody Aspect (Wellspring) | yes | On switch-in, once, if Sp. Def can rise. | battle_util.c:3614-3637 |
| Embody Aspect (Cornerstone) | yes | On switch-in, once, if Defense can rise. | battle_util.c:3615-3637 |
| Toxic Chain | yes | Its hit badly poisons the target (chance set at hit time). | battle_util.c:4363-4377; battle_script_commands.c:3748 |
| Supersweet Syrup | yes | On switch-in, once per battle, when a foe is present. | battle_util.c:3464-3475; bs1:6256-6257 |
| Tera Shift | yes | Terapagos changes form on switch-in. | battle_util.c:4737-4745; battle_switch_in.c:44 |
| Tera Shell | yes | At full HP, when its type matchups are distorted on a hit. | battle_util.c:325-335; battle_script_commands.c:1474-1484; bs1:6665-6667 |
| Teraform Zero | yes | Stellar Terapagos on switch-in. | battle_util.c:3484-3490; bs1:7459 |
| Poison Puppeteer | yes | Pecharunt's poison also confuses the target. | battle_util.c:4389-4400 |

## 2. NEVER SHOWS ONE

| Ability | Why (where it acts) | file:line |
|---|---|---|
| Stench | Its flinch chance is applied through SetMoveEffect at move end; no pop-up script is pushed. | battle_util.c:4378-4388; battle_hold_effects.c:214 |
| Battle Armor | Blocks critical hits inside the crit-chance calculation only. | battle_util.c:8059, 8111 |
| Sand Veil | Evasion in sandstorm and sandstorm damage immunity, both silent. | battle_util.c:10532-10535; battle_end_turn.c:139 |
| Compound Eyes | Accuracy multiplier only. | battle_util.c:10515-10517 |
| Shield Dust | Blocks added effects silently. | battle_util.c:10023-10025 |
| Shadow Tag | Trapping only. A failed run or the party menu names the ability in text, but no pop-up script runs. | battle_util.c:5085; battle_main.c:4282-4287, 4520-4522 |
| Natural Cure | Cures status silently on switch-out. | battle_script_commands.c:10228-10238 |
| Serene Grace | Doubles added-effect chances silently. | battle_util.c:9702; battle_hold_effects.c:210 |
| Swift Swim | Speed multiplier only. | battle_main.c:4951 |
| Chlorophyll | Speed multiplier only. | battle_main.c:4953 |
| Huge Power | Attack multiplier only. | battle_util.c:6992-6996 |
| Magnet Pull | Trapping only (text names it, no pop-up). | battle_util.c:5091; battle_main.c:4282-4287 |
| Thick Fat | Damage modifier only (recorded for the AI). | battle_util.c:7124-7131 |
| Early Bird | Faster sleep countdown, silent. | battle_move_resolution.c:127 |
| Run Away | Fleeing prints "fled using Run Away" with no pop-up script. | battle_util.c:573-593; battle_main.c:5787-5788; bs1:4323-4326 |
| Hustle | Attack and accuracy multipliers only. | battle_util.c:7051-7054, 10521-10524 |
| Plus | Sp. Atk multiplier with a Plus/Minus partner; Magnetic Flux/Gear Up target checks have no pop-up. | battle_util.c:7029-7046; bs1:1052, 1090 |
| Minus | Same as Plus. | battle_util.c:7033-7046; bs1:1051, 1089 |
| Guts | Attack multiplier only. | battle_util.c:7059-7062, 7468 |
| Marvel Scale | Defense multiplier only. | battle_util.c:7282-7289 |
| Overgrow | Pinch power boost only. | battle_util.c:7025-7028 |
| Blaze | Pinch power boost only. | battle_util.c:7021-7024 |
| Torrent | Pinch power boost only. | battle_util.c:7017-7020 |
| Swarm | Pinch power boost only. | battle_util.c:7013-7016 |
| Rock Head | Recoil skipped silently. | battle_move_resolution.c:2979 |
| Arena Trap | Trapping only (text names it, no pop-up). | battle_util.c:5088; battle_main.c:4282-4287 |
| Pure Power | Attack multiplier only. | battle_util.c:6993-6996 |
| Shell Armor | Blocks critical hits inside the crit-chance calculation only. | battle_util.c:8059, 8111 |
| Tangled Feet | Evasion while confused only. | battle_util.c:10540-10543 |
| Rivalry | Power modifier only. | battle_util.c:6687-6692 |
| Snow Cloak | Evasion in hail/snow and hail immunity, silent. | battle_util.c:10536-10539; battle_end_turn.c:164 |
| Gluttony | Earlier berry use, silent. | battle_util.c:5623 |
| Unburden | Speed doubling only. | battle_util.c:10793; battle_main.c:4972 |
| Heatproof | Fire damage and burn damage halving, silent. | battle_util.c:6790-6798; battle_end_turn.c:526-529 |
| Simple | Doubles stat changes silently. | battle_script_commands.c:7796-7800 |
| Iron Fist | Power modifier only. | battle_util.c:6674-6677 |
| Adaptability | STAB modifier only. | battle_util.c:7430-7433; battle_terastal.c:161, 169 |
| Skill Link | Multi-hit count only. | battle_move_resolution.c:1919; battle_script_commands.c:1104 |
| Quick Feet | Speed multiplier only. | battle_main.c:4962, 4998 |
| Normalize | Type and power change only. | battle_util.c:6733-6736; battle_main.c:6393-6412 |
| Sniper | Crit damage modifier only. | battle_util.c:7569-7572 |
| Magic Guard | Indirect damage skipped silently everywhere (only recorded). Note: an attacker with Magic Guard still triggers the Rough Skin/Iron Barbs holder's pop-up. | battle_util.c:4057, 4423; battle_end_turn.c:147, 441, 482, 523; battle_switch_in.c:313 |
| No Guard | Accuracy skip only (recorded for the AI). | battle_util.c:10416-10468 |
| Stall | Turn order only. | battle_main.c:5073-5074 |
| Technician | Power modifier only. | battle_util.c:6658-6661 |
| Klutz | Item suppression only. | battle_util.c:5832, 9293 |
| Super Luck | Crit stage only. | battle_util.c:8052, 8105 |
| Unaware | Ignores stat stages in damage and accuracy math. | battle_util.c:6977, 7264, 10483, 10488 |
| Tinted Lens | Damage modifier only. | battle_util.c:7573-7576 |
| Filter | Damage modifier only. | battle_util.c:7598-7606 |
| Solid Rock | Damage modifier only. | battle_util.c:7599-7606 |
| Honey Gather | After-battle item pickup only. | battle_script_commands.c:10322 |
| Reckless | Power modifier only. | battle_util.c:6670-6673 |
| Multitype | Makes ability-changing moves fail with "But it failed!" (no pop-up). | bs1:1568 |
| Sheer Force | Power boost and added-effect removal, silent. | battle_util.c:6678-6681, 9460 |
| Contrary | Reverses stat changes silently (only swaps the message). | battle_script_commands.c:7785-7795; battle_util.c:1244-1263 |
| Defeatist | Attack/Sp. Atk halving only. | battle_util.c:7005-7008 |
| Friend Guard | Ally damage reduction only. | battle_util.c:7650-7652 |
| Heavy Metal | Weight only. | battle_util.c:6077 |
| Light Metal | Weight only. | battle_util.c:6079 |
| Multiscale | Damage modifier only. | battle_util.c:7590-7597 |
| Toxic Boost | Power modifier only. | battle_util.c:6666-6669 |
| Flare Boost | Power modifier only. | battle_util.c:6662-6665 |
| Regenerator | Heals silently on switch-out. | battle_script_commands.c:10239-10250 |
| Sand Rush | Speed multiplier and sandstorm immunity, silent. | battle_main.c:4955; battle_end_turn.c:141 |
| Wonder Skin | Accuracy reduction for status moves only. | battle_util.c:10506 |
| Analytic | Power modifier only. | battle_util.c:6693-6696 |
| Illusion | Breaking the disguise prints "illusion wore off"; BattleScript_IllusionOff has no pop-up. | battle_util.c:4226-4233, 8966; bs1:5558-5567 |
| Infiltrator | Bypasses screens, Substitute, Safeguard, Mist silently. | battle_util.c:5385, 7541; battle_script_commands.c:7809, 10418 |
| Prankster | Priority only. A Dark-type target's immunity prints "doesn't affect" with no pop-up. | battle_main.c:5043; battle_util.c:2410-2421 |
| Sand Force | Power boost and sandstorm immunity, silent. | battle_util.c:6682-6686; battle_end_turn.c:140 |
| Victory Star | Accuracy multiplier only. | battle_util.c:10518-10520, 10552-10555 |
| Fur Coat | Defense multiplier only. | battle_util.c:7290-7297 |
| Strong Jaw | Power modifier only. | battle_util.c:6701-6704 |
| Refrigerate | Type and power change only. | battle_util.c:6725-6728; battle_main.c:6163 |
| Gale Wings | Priority only. | battle_main.c:5037 |
| Mega Launcher | Power and Heal Pulse modifier only. | battle_util.c:6705-6708; battle_script_commands.c:12578 |
| Grass Pelt | Defense multiplier only. | battle_util.c:7298-7305 |
| Tough Claws | Power modifier only. | battle_util.c:6697-6700 |
| Pixilate | Type and power change only. | battle_util.c:6717-6720; battle_main.c:6160 |
| Aerilate | Type and power change only. | battle_util.c:6729-6732; battle_main.c:6166 |
| Parental Bond | Second hit only. | battle_move_resolution.c:1871 |
| Merciless | Crit stage only. | battle_util.c:8041, 8119 |
| Stakeout | Attack multiplier only. | battle_util.c:7055-7058 |
| Steelworker | Power modifier only. | battle_util.c:6713-6716 |
| Slush Rush | Speed multiplier only. | battle_main.c:4957 |
| Long Reach | Removes contact silently. | battle_util.c:5883-5885 |
| Liquid Voice | Type change only. | battle_main.c:6387 |
| Triage | Priority only. | battle_main.c:5054 |
| Galvanize | Type and power change only. | battle_util.c:6721-6724; battle_main.c:6169 |
| Surge Surfer | Speed multiplier only. | battle_main.c:4964 |
| Corrosion | Lets it poison Steel and Poison types, silent. | battle_util.c:5405 |
| Battery | Ally power boost only. | battle_util.c:6771-6774 |
| Fluffy | Damage modifier only. | battle_util.c:7607-7618 |
| RKS System | Makes ability-changing moves fail with "But it failed!" (no pop-up). | bs1:1569 |
| Shadow Shield | Damage modifier only. | battle_util.c:7591-7597 |
| Prism Armor | Damage modifier only. | battle_util.c:7600-7606 |
| Neuroforce | Damage modifier only. | battle_util.c:7565-7568 |
| Propeller Tail | Ignores redirection silently. | battle_move_resolution.c:771, 838 |
| Stalwart | Ignores redirection silently. | battle_move_resolution.c:772, 839 |
| Punk Rock | Damage modifiers only. | battle_util.c:6737-6740, 7619-7625 |
| Ice Scales | Damage modifier only. | battle_util.c:7626-7632 |
| Power Spot | Ally power boost only. | battle_util.c:6775-6777 |
| Steely Spirit | Power modifier only. | battle_util.c:6741-6744, 6778-6781 |
| Gorilla Tactics | Attack boost and move lock, silent. | battle_util.c:1621, 1731, 7076-7081 |
| Hunger Switch | Morpeko's end-of-turn form change uses BattlerFormChangeEnd3NoPopup. | battle_end_turn.c:1332-1334; bs1:5478-5480 |
| Unseen Fist | Ignores protection on contact, silent. | battle_util.c:5930; battle_move_resolution.c:2092 |
| Transistor | Attack multiplier only. | battle_util.c:7063-7071 |
| Dragon's Maw | Attack multiplier only. | battle_util.c:7072-7075 |
| Rocky Payload | Attack multiplier only. | battle_util.c:7082-7085 |
| Sharpness | Power modifier only. | battle_util.c:6745-6748 |
| Mycelium Might | Status moves go last and ignore abilities, silent. | battle_util.c:4968; battle_main.c:4688 |

## 3. UNSURE

Nothing left unsettled at the level of "does this ability ever run a pop-up script". Caveats a tracker should know:

- **Pop-ups that are not the ability triggering.** Several moves run `BattleScript_AbilityPopUpOverwriteThenNormal` and can show any ability, including ones in section 2: Skill Swap (both Pokemon, bs1:3803-3806), Role Play (bs1:3718), Doodle (bs1:205), and EFFECT_OVERWRITE_ABILITY moves such as Entrainment, Simple Beam, Worry Seed (bs1:1899). Trace, Receiver, Power of Alchemy, Mummy, Lingering Aroma and Wandering Spirit also put the copied or new ability on a pop-up. The test suite confirms this: Blaze, Overgrow, Torrent and Simple appear in ABILITY_POPUP assertions only in test/battle/move_effect/skill_swap.c, role_play.c and overwrite_ability.c.
- **The label can differ from the ability that triggered.** Form-change pop-ups set `abilityPopupOverwrite` to the pre-change ability (src/battle_util.c:3647, 4413, 4741; src/battle_end_turn.c:1326). As One shows As One then Unnerve on switch-in (bs1:6822-6832) and Chilling Neigh or Grim Neigh after a KO (src/battle_util.c:4577-4580).
- **One pop-up can stay up across several hits.** Mummy, Bad Dreams and similar set `sFIXED_ABILITY_POPUP`; Frisk and Pastel Veil deliberately show one pop-up even when they act on two Pokemon (src/battle_script_commands.c:13677-13686; bs1:7770-7773).
- **Dead pop-up scripts.** BattleScript_AnticipationActivates (bs1:5596), EarthEaterActivates (bs1:4965), ObliviousPreventsAttraction (bs1:6573), AbilityAvoidsDamage (bs1:6660), BattlerShookOffTaunt (bs1:6993) and BattlerGotOverItsInfatuation (bs1:6999) contain pop-up calls but nothing in src/ or data/ references them, so they never run.
- **Sturdy depends on the save.** The full-HP survive (and its pop-up) exists only when the player has `challengeSettings.tx_Mode_Sturdy == 1`; the OHKO block pops up either way.
- **Text that names an ability without a pop-up.** Run Away ("fled using"), Shadow Tag / Arena Trap / Magnet Pull ("prevents escape with", src/battle_message.c:217), Illusion ("wore off"), Lightning Rod / Storm Drain redirect ("took the attack"). A tracker watching only the pop-up scripts will not see these.
- Not covered: battle_ai_*.c (AI memory only) and the Battle Pike/Factory/Tent files, as instructed. Abilities that act only outside battle (Pickup, Honey Gather item pickup, field effects of Flash Fire, Static and so on) are not battle pop-ups.

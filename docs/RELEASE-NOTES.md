# KaizoCore release notes

Short and plain, one list per release. Newest first.

## rc37 (6 October 2026)

**New**

- **The File bar.** The tracker has no gear, menu or lock any more: everything is on one see-through bar, FILE, NEW,
  TRACKER, VIEW, TOOLS, SETTINGS and HOME, with an X at the right end. Tap the top third of the game to open or close
  it; the rest of the screen stays the game's. Every tracker option is under TRACKER. Four views under VIEW: Docked,
  Floating, HUD and Hidden.
- **The floating tracker locks itself.** Hold it half a second to unlock it (a ring and a buzz), then drag it or pull
  its corner. Hold again to lock it. It also locks when the bar opens and after six seconds untouched.
- **Streaming to OBS, by USB cable or Wi-Fi.** TOOLS, Stream sends the game, the tracker, your attempts, a run timer
  with badge splits and a game over card to OBS on your PC, with no screen mirroring. Both links are shown side by
  side; the wiki's Streaming to OBS page has the setup, and how to play from your PC. OBS can switch scenes by itself,
  and viewers can ask your Twitch chat for !pokemon, !moves and more once you connect Twitch (SETTINGS, Stream
  settings). Nothing goes to the internet except Twitch, and only if you connect it.
- **Nuzlocke: Bosses and Types.** The Nuzlocke ledger shows every gym leader, Elite Four member and Champion still
  ahead, with their Pokémon, levels, moves and items, and the chance each of your Pokémon survives each of their moves.
  It shows them only when the game's trainers are its own: never in a randomized game. Types shows how your whole team
  stands against every attacking type.
- **Favorites predict as you type,** like the log's search: a list of Pokémon with their pictures, and "mr mime",
  "hooh" or one typo still find the right one.

**Heart & Soul**

- **Updating never strands a run again.** The app makes its new Heart & Soul copy itself from your official 2.0.6; no
  patching again. A run made on an older copy shows a note on the tracker with Move this run: the same seed, mode and
  pool on the new copy, your in-game save carries your team (the world is rolled again, because the game changed).
- **Every Pokémon of an area comes up in Kaizo, day or night:** the wild cycle goes through every Pokémon the area has at
  any time of day, so you can pick your next Pokémon early whatever the clock says, and the tracker lists exactly those,
  in order. Before, an area could alternate between two at night while the tracker showed more. A Nuzlocke keeps the
  game's own day and night Pokémon, and its list follows the time of day.
- **TM balls are yellow** and every other item ball red, following what is really inside.
- **Cut cuts tall grass** in Johto and Kanto, as in Emerald.
- **No time saver skips or locks out a battle:** the short ways out of the Dragon's Den, the Underground and the
  lighthouse only happen once every trainer there is beaten. The Card Key's now leaves you outside the Radio Tower door.
- **Options:** the battle slide-in is back on and text speed is the game's own for a new game; trainer calls stay off
  and can be turned on (rematches are against the IronMON rules). Your OPTIONS stay after you save and load.
- **Every mode works on both pools,** checked one by one. Evo Kaizo has no evolution loops any more, so pivots are
  banned with no checkpoints, as the Nat. Dex rules say. Nuzlocke's similar-strength wild Pokémon now really are similar.
- **A Vanilla run is held to Vanilla rules:** three favorites, Gens 1 to 3 only, and Emerald's rules text.
- **Survival's heals:** every free heal outside a dungeon counts (Elm's lab machine, the National Park teacher and the
  rest), and beating Lance adds the seven Kanto heals.
- **A Nuzlocke ends at Lance:** after him, a Hardcore Nuzlocke can no longer lose its save.
- **The rules pages** say what Heart & Soul really does: the 60% levels of Kaizo and harder, every KaizoCore change in
  one list at the top, and notes where HeartGold and SoulSilver's rules differ.

**Fixed**

- **Form types, every DS game:** Sandy and Trash Cloak Wormadam show Bug/Ground and Bug/Steel, and every other form
  (Rotom, Giratina, Shaymin, Deoxys, the Therian forms, Kyurem, Darmanitan, Meloetta, Castform) shows its own types,
  stats and abilities, from your game's own data. Thanks for the report.
- **Type changes in battle:** Conversion, Conversion 2, Color Change, Protean, Soak, Roost and the rest change the
  types on the card, and the effectiveness arrows follow at once, on both cards. Moves whose type changes (Pixilate and
  the other -ate abilities, Judgment, Multi-Attack, Terrain Pulse and more, where your game has them) are scored as the
  type they will really be. An opponent's ability or item counts only once the game has shown it.
- **A crash on Android 16 in DS games** when the game turned its wireless on (HGSS, Platinum's menu, Black 2's Mystery
  Gift): the game now just finds no partners.
- **Arrow keys from a keyboard** reach the game on every kind of keyboard.
- **A Heart & Soul NEW RUN from the Play screen during a Nuzlocke** keeps the Nuzlocke's settings.

**Known issues**

- Not checked on a phone: the File bar on folding phones and tablets, streaming with real OBS and Twitch, the arrow
  keys through scrcpy, and Heart & Soul's new build in the app (it was played in an emulator on the PC).
- Some of Heart & Soul's grass has no cut version in the game and still does not cut (long grass, cave grass,
  Viridian Forest).
- Nuzlocke Bosses reads Game Boy Advance games only for now.
- At high fast forward, Survival's heal count can miss a heal outside a Pokémon Center.
- Heart & Soul's catch-rate line uses the Gen 3 formula.

## rc36.1 (5 October 2026)

**Heart & Soul: after updating, patch it again once** (Home > Pokémon Heart & Soul). Your official copy is already
in the Library, so it only takes a moment. Your saves and attempts stay.

**Kaizo rules for Heart & Soul**

- **No running from trainer battles in Kaizo:** RUN says "No! There's no running from a trainer battle!" and the
  forfeit prompt is gone. A Nuzlocke keeps the forfeit, so a run can never get stuck.
- **No EXP for catching in Kaizo,** as in the Gen 3 games IronMON was built on.
- **Wild Pokémon come in order in Kaizo:** each area cycles through its Pokémon, so five kinds mean five encounters,
  then it starts over.
- **No HM moves in any random moveset,** so no starter or wild Pokémon knows or learns one.
- **Your first rival battle plays by FireRed's first-battle rules:** your attacks never miss and nobody gets a
  critical hit.
- **Kaizo only:** used-up held items stay used up, the Day Care won't take your Pokémon, the Exp. Share is off
  so your main gets all the EXP, shiny odds and item drops stay at the default, only Pokémon that fought roll
  Pickup, TMs break after one use, and there are no mints.
- **Hidden items are never TMs,** and item pools now match the source game's list, so TMs turn up as often as in
  Emerald Kaizo and no more.

**Faster Heart & Soul**

- After the egg comes back, the aide hands you the Poké Balls right away and you are healed, no trip home.
- The trash can in Elm's lab sparkles and just gives you the item.
- Ilex Forest's Farfetch'd goes home on the first talk, trainer phone calls are off in Kaizo (Elm's story calls
  stay), the Violet egg comes with Elm's call, and the Burned Tower, Suicune and story scenes are quicker.
- Short warps after the Radio Tower keys, after Lance agrees at the Lake of Rage, and after the Dragon's Den badge
  (no quiz). The lighthouse medicine is done at the Cianwood pharmacy, Whitney gives her badge right away, Oak calls
  to open Mt. Silver, and the credits can be skipped with Start or B. No trainer is skipped and no warp lands you in
  a fight.
- The Meowth Name Raters stand where Emerald's Name Rater does.

## rc36 (5 October 2026)

**New**

- **Heart & Soul Kaizo IronMON:** Lil Dill and the Heart & Soul team's Johto on Emerald, now a Kaizo IronMON game with the full tracker, the randomizer log, rule marks and NEW RUN, like every other game here.
- **How to get it:** on Home, tap Pokémon Heart & Soul. Add your Emerald (USA) dump, then get the official 2.0.6 patch from GitHub or Hackdex with one tap and pick the file you downloaded. KaizoCore patches it, adds its own comforts on top and puts the game in your Library. KaizoCore never ships their patch or a game.
- **Two versions:** Vanilla, with the Pokémon of Gens 1 to 3 as the hack ships, and Nat. Dex, with every Pokémon through Gen 9. Every IronMON mode works on both, and so does KaizoCore's Nuzlocke.
- **The comforts:** hidden items sparkle, an HM entry in the start menu, instant healing at the Pokémon Center (it just says "Healed"), run from wild battles with B, and the PC item waits in the trash can in Elm's lab, random like in every other Kaizo game.
- **A faster start:** from New Game to your first rival battle, the talking is cut to the essentials and the walks between errands are skipped: Elm's lab, the starter, Mr. Pokémon's house, then Cherrygrove's Pokémon Center.
- **The challenge menu is set for you:** Heart & Soul's own challenge settings come set and locked for your mode, Kaizo or Nuzlocke.
- **Name Raters in every Pokémon Center,** as a Meowth in each Johto and Kanto center. Fly and Flash show in the party menu only once you have the HM and its badge.
- **How a run ends:** Kaizo IronMON is won by beating Red on Mt. Silver. A Nuzlocke ends at Lance, the Champion.
- **The tracker knows it:** the ball picker and your favorites at Elm's table, the starter's held item, Heart & Soul's own sixteen gyms, its rules page, GachaMon cards, Play as your Pokémon, and its own Pokémon pictures on the card and in the log.
- **Thanks** to Lil Dill, the Heart & Soul team and RHH. The official game stays playable in your Library too.

**Fixed**

- **Seen counts no longer go up when you restart the app** in the middle of a battle.
- **Randomizer log, every game:** a Pokémon that does not evolve, like a final stage, now shows its moves. It used to say the log listed no level-up moves for it.
- **Randomizer log:** opening a trainer or a Pokémon shows the PC tracker's info at the top: a trainer's route, team levels, average IVs, items and double battle; a Pokémon's weaknesses, its history this run, its resistances and your note.
- **Randomizer log pictures:** the log uses your game's own pictures everywhere the PC tracker's log does, Game Boy games included, and the route page's wild Pokémon stand idle like the rest.
- **A patch that ships inside KaizoCore is updated with the app,** instead of an older copy being reused.

## rc35.2 (4 October 2026)

**Fixed**

- **Floating window:** locked and see-through, swipe to scroll it; a tap on its empty space still goes to the game. The up and down arrows are gone, and the top is one slim row at any width (in a narrow window the gear is in the menu).
- **Randomizer log:** on a Game Boy Advance game it opens on your lead Pokémon's page, as the PC tracker does. Back shows the full list.

## rc35.1 (4 October 2026)

**New**

- **See-through floating window:** in Tracker Setup, the Window see-through slider fades the floating tracker's background down to 30%, so the game shows through. Words, numbers, pictures and buttons stay solid. Lock the window and a tap on its empty space goes to the game.
- **Wide view:** stretch the floating tracker wide and it lays out in columns: your Pokémon, its stats and heals, then its moves, with the opponent on the right in battle. Make it taller and it goes back to one stack.
- **A slimmer window top:** the floating tracker's top is one row now. One line of text (your attempt, the battle, the weather, or the area) scrolls when it doesn't fit, and a swap icon flips between your Pokémon and the foe. Hold it to see which one it shows.
- **Edit favorites mid-run:** in a Kaizo IronMON run, Tracker Setup has Edit favorites, with your mode's rules. Save a change and the tracker's favorites row shows it right away.

## rc35 (4 October 2026)

**New**

- **GachaMon is here:** the PC tracker's card game. In a Kaizo IronMON run on a Game Boy Advance game, each Pokémon that leads your party becomes a card, rated by its ability, moves, stats and nature the same way the PC tracker rates it. Your lead's stars show next to your heals.
- **Collect them:** keep cards in your collection, favorite the ones you like, fill the GachaDex and open card packs. Lose a run and you can win a Prize card. Open it all from Tracker Setup, GachaMon Collection.
- **Share codes work both ways with the PC tracker,** and you can bring your whole PC collection over: in GachaMon's options, Import a PC tracker collection reads its FullCollection.gccg. Cards you already have are skipped and favorites stay favorites.
- **Rule marks:** in a Kaizo IronMON run, a held item or ability your mode bans gets a red X, the same as banned moves, with each mode's own exceptions. With Huge Power or Pure Power the X goes on your physical moves. Tap an X to read the rule. It is on by default; turn it off in Tracker Setup with Mark banned items, abilities and moves.
- **19 more Pokémon walk:** Iron Boulder, Iron Crown, Squawkabilly (White too), Mabosstiff, Shroodle, Brambleghast, Toedscruel, Klawf, Rabsca, Espathra, Bombirdier, Flamigo, Brute Bonnet, Gimmighoul, Wo-Chien, Chien-Pao, Miraidon, Okidogi and Mega Malamar now walk in Play as your Pokémon and on the tracker, with overworld sprites by DarkusShadow. A shiny one walks in its plain colors for now.

**Changed**

- **Trainers defeated counts the whole area,** like the PC tracker, with its sword: Mt. Moon's floors, the S.S. Anne's cabins, Victory Road and the rest count as one place, and rivals you won't face this run are left out.
- **Gold, Silver and Crystal Survival add the 7 Kanto heals on their own** once the Johto League is beaten, like HeartGold and SoulSilver already did.
- **The support button is gone from About.**

**Fixed**

- **The route line and the battle's weather show right away** after a map change or a load, instead of a second or more later.
- **An opponent's Deoxys shows in its Normal form,** the way the battle draws it.
- **The run page's attempt number stays put:** after playing a Library game it could show another game's count next to your run's.
- **DS forms walk as themselves:** Rotom's appliances, Giratina Origin, Shaymin Sky, Deoxys's forms, Wormadam's cloaks, Kyurem, Unown's letters and the rest. The game over screen shows them in their form too.
- **DS pictures load smoother:** the tracker, the game over screen and the DS log load them in the background, so the screen no longer stalls.
- **DS save states keep the 3D scene:** a state saved in the middle of drawing loads with the right shapes on its first frame. Your older DS states still load.

**Known issues**

- A DS save state made in rc35 can't be loaded by an older KaizoCore. In-game saves are not affected.

## rc34.1 (4 October 2026)

**Changed**

- **The floating tracker is tighter:** its edge meets the tracker's boxes, and the lock, the title and the menu sit together at the left of its title bar. Resize it by grabbing just outside its sides, its bottom or its bottom corners.
- **File and NEW are red** in landscape, like NEW RUN in portrait.
- **The RUN button is gone from the tracker.** It did not work. B to run still works on Game Boy and Game Boy Advance games.
- **The Random MAC address setting is gone from DS settings.** It is always off now. Turned on, it could lock a Gen 4 game's daily events every time you continued.

**Fixed**

- **Every landscape menu button can be reached:** the File strip and the layout editor's bar stop at the edge of a docked DS tracker instead of running under it, so MENU and the last buttons can be tapped. Both bars now scroll round and round, so the first button comes back after the last.
- **Double battles in a narrow tracker:** the banner no longer spells the side's words one letter to a line. When the buttons leave too little room, the title and the side go on a line under them.
- **Long area names scroll:** the area at the top of the tracker (Rustboro City, Mt. Chimney) scrolls by when the buttons leave it too little room, instead of being cut off. With animations turned off it ends in an ellipsis.
- **Every move has a description on Nat. Dex and MaxDex:** Fairy Wind and every other move past Gen 3 now shows a short one when you tap it. Most of the newest moves' descriptions are Pokémon Showdown's.
- **An opponent's ability shows once the battle reveals it, at any speed:** at 8x and 16x the tracker could miss the moment the ability's message was on screen, a weather ability at the start of a battle most of all. It now sees every frame the game plays, on FireRed, LeafGreen, Emerald, Ruby, Sapphire, both Nat. Dex games and MaxDex.
- **Platinum saves continue:** continuing a Platinum save no longer stops on "A communication error has occurred" and a white screen.
- **Black 2 runs resume:** resuming a Black 2 run no longer closes the app.

**Known issues**

- The first time a Diamond, Pearl, Platinum, HeartGold or SoulSilver save made before this update is continued, the game may treat it as a clock change and lock its daily events for a day. It happens once per save.

## rc34 (3 October 2026)

**New**

- **MaxDex Kaizo IronMON:** Trip's MaxDex on FireRed 1.1, with every Pokémon through Gen 9 and the Legends Z-A Megas, and moves and abilities through Gen 9. The tracker reads all of it, and Play as your Pokémon works on it. It is built in: tap Patch on your FireRed 1.1 in the Library and pick MaxDex.
- **MaxDex allows a 600 BST starter,** by the Nat. Dex 1.1.3 rules it is built on.
- **IronMON HGSS for HeartGold:** PyroMike's patch, under Patched versions. It skips the intro, shortens the talking until Goldenrod, and speeds up walking and battles.
- **Rule breaks get an X on the tracker:** a Pokémon over the BST limit for your mode, and the moves your game and mode ban. Tap one to see the rule.
- **Favorites show as Pokémon icons** on the card before you get your first Pokémon, as on the PC trackers.
- **Landscape:**
  - No bar between the game and the docked tracker: drag the tracker's left edge to resize it.
  - One menu holds the tracker's extras.
  - The floating tracker moves, resizes and locks in place.
  - DS games keep the bottom screen under the docked tracker.
- **Clearer DS sound:** 16-bit with smoothing by default. The DS's own sound is one tap away in DS settings.
- **DS forms show their own picture** on Platinum, HeartGold, SoulSilver, Black 2 and White 2: Rotom's appliances, Giratina Origin, Deoxys and the rest.
- **Megas and forms with no walking sprite** walk as their base Pokémon until one is drawn.
- **Play as a shiny:** a shiny lead walks as its shiny, Always use has a Shiny switch, and a shiny Pokémon gets a shiny icon on every tracker.
- **Unown walks as its own letter,** and Deoxys in its game's form.
- **The tracker's pictures come from your own game** on FireRed, LeafGreen, Emerald, Ruby and Sapphire: a shiny Pokémon shows shiny, Unown its letter and Deoxys its game's form.
- **The randomizer log has the PC tracker's pictures:** trainer portraits from your own game, the gym badge on a leader's page, moving Pokémon and a picture on each tab. Forms read by their names (Mewtwo-X, Rayquaza-M).
- **Double battles:** the swap steps through every Pokémon on the field, and the banner says which one you see. Triples on Black 2 and White 2 too. The notebook records every opponent's moves and encounters, and the stream overlay shows the same Pokémon as the tracker.
- **Streaming keeps going while you use another app.** The game pauses meanwhile, and swiping KaizoCore away ends the stream.
- **Your favorites in OBS:** one picture each, which changes as soon as you edit your favorites.
- **The attempt number on the tracker**, Setup as a gear, and an arrow when there is more below.
- **A Licenses page** under About, with every component's license.

**Fixed**

- **Saves:**
  - Game Boy and GBA in-game saves reach the phone within seconds, so a crash or a dead battery no longer loses them.
  - Changing the phone's clock no longer stops auto-saves or Time Machine.
- **Backups:** a restore checks the file before it changes anything, a backup no longer carries the previous run's game, saving an attempt checks for room first, and a full phone shows a message instead of closing the app.
- **Runs and stats:** Game Boy wins are recorded, DS past runs and timers belong to the run, a retry counts once, and 60% levels on Standard or Ultimate raise levels by 60%, not 6%.
- **Tracker:** the Gen 1 Ice weakness, Gold, Silver and Crystal genders and shinies, Ditto's Transform and the moves it copies, Trace, Clear Body and Damp, heals that match the PC tracker, and Nat. Dex Fairy moves.
- **Nuzlocke:** a Battle Tent loss no longer kills your team or ends the run, gifts and eggs count the right area (Gen 2 eggs too, Crystal's Odd Egg included), and Genlocke offers the next game.
- **Your choices stay** if Android closes KaizoCore in the background: the settings editor, Build your own and the Nuzlocke screen.
- **DS:** importing the DSi NAND and exporting a Black 2 or White 2 run no longer run out of memory, the stylus no longer sticks, and keyboards and controllers can press X and Y.
- **Black 2 and White 2 use about 750 MB, down from about 1.3 GB:** the game was kept in memory twice. Restart on a DS game no longer closes KaizoCore.
- **Leaving and reopening a DS game no longer uses more memory each time.** Each load used to leave about 0.4 MB and an idle thread behind until KaizoCore was closed.
- **RetroAchievements:** playing offline no longer signs you out, and fast forward no longer skips achievement checks.
- **Updates:** the free space check counts what an install needs, and Android's install prompt is no longer lost.
- **Controllers and accessibility:** the d-pad moves through menus, every tracker window's text follows your font size with larger buttons, and TalkBack reads the controls.
- **Smoother play:** heavy work moved off the main thread, and 90 and 120 Hz phones no longer stall.
- **STREAM with no Wi-Fi** says to join the PC's Wi-Fi or turn on the hotspot, instead of showing a link that cannot work.
- **Restore** says what it replaces: the runs' games and saved attempts, never your library games.
- **American spelling** on every screen, in the rules pages and in the help.
- **Play as your Pokémon** comes back by itself after the app restarts, and every sprite walks and turns with you instead of dozing off while you walk.

**Under the hood**

- The emulator library is a sixth of its old size, the app is ready for phones with 16 KB memory pages, and a phone with no camera can install it.
- The DS core is now built here, from a pinned commit of its source, with melonDS's own later fix for a few 3D edge pixels that were drawn from leftover memory.

**Known issues**

- 46 Pokémon have no walking sprite yet (44 nobody has drawn, plus Mega Falinks and White Squawkabilly); with one in the lead you stay the trainer. 8 have no shiny one (Koraidon among them) and walk in plain colors when shiny.
- An opponent's Deoxys shows its game's form on the tracker; the battle screen draws it in its Normal form.
- Gold, Silver and Crystal Survival: the 7 Kanto heals are added by hand.

## rc33 (2 October 2026)

**New**

- **The tracker has KaizoCore's own look:** rounded cards, type and status pills, an HP bar and pill buttons. Your color theme still sets every color.
- **Move names in their type's color, with a type symbol** before each one, on every game. The symbols are the DS tracker's.
- **No wasted space:** on a phone in portrait the whole card fits on the screen, with the moves beside the stats. SETUP no longer takes a row of its own.
- **Weather on Game Boy Advance games:** rain, sun, a sandstorm or hail shows under the battle banner while it lasts.
- **Search the randomizer log:** it suggests Pokémon as you type, and one typo is forgiven. Every page of the log is easier to read.

**Fixed**

- **B to run** only runs from the battle menu (Fight, Bag, Pokémon, Run). B in the Bag, the party screen or the move list goes back again, on Game Boy Advance and Game Boy games. On DS games B is only B: the tracker's RUN button still runs.
- **Crackling sound** on Game Boy Advance games.
- **Black 2 and White 2 crashed at every launch** when the last auto-save was taken at the wrong moment. They open where you left off now.
- **After a switch, the tracker shows the Pokémon on the field**, not your first party slot. Heals, Calc Atk and the move matchups follow it.
- **Saves:**
  - Two quick saves to one slot could delete it.
  - A save that fails on a full phone says so.
  - Each DS game keeps its own save: a HeartGold run no longer opens on the last Platinum run's save.
  - UNDO brings a slot back with its own run.
  - Save states no longer use more memory the longer you play.
  - Time Machine no longer keeps every restore point after a restore, which could run the phone out of memory.
- **Runs:**
  - NEW RUN starts one run, however fast you tap.
  - A run made while Play is open boots on its own, instead of being written into the game you were playing.
  - Turning off "Get the next run ready" no longer deletes the run being installed.
  - A randomized Nuzlocke asks before ending a run in another game.
  - A blackout in Red, Blue or Yellow ends the run.
  - On DS games an egg no longer keeps an "entire party faints" run alive.
- **Backups and cloud sync:** a restore is all or nothing and restarts the app at once; cloud sync never leaves a half-written copy; a backup picked for restoring is linked only once the restore goes through.
- **Patches** work on Android 8 to 12, and a patch copied out of the app only part way is copied again.
- **32-bit phones:** the app no longer installs where no game could play.
- **Cheats** are no longer lost after a Nuzlocke, and hardcore mode turns them off.
- **RetroAchievements:** achievements no longer all show as unsupported.
- **Your key bindings** work from the moment the app starts.
- **The docked facecam** lets go of the camera when you turn it off.
- **A game the emulator refuses** says so, instead of a black screen.
- **Dual-screen handhelds:** the starter's info no longer opens on the second screen, where it crashed.
- **A big DS game loading** no longer freezes the app's tabs.
- **Streaming** no longer risks a crash while it reads your stat marks.

**Known issues**

- DS double battles are not tracked.
- Gold, Silver and Crystal Survival: the 7 Kanto heals are added by hand.
- Black 2 and White 2 use about 1 GB of memory.
- The DS core leaks a little memory on each load.

## rc32 (1 October 2026)

**New**

- **A new app icon:** the cracked Poké Ball.
- **Home screen:** Play any game, Kaizo IronMON, Nuzlocke and ROM Hacks, with a Continue card for where you left off.
- **Nuzlocke mode** on every game the tracker reads (Red to White 2). Pick a preset, or change any rule.
  - The tracker keeps the ledger: encounters, catches, deaths and level caps.
  - It warns when a rule is broken and never stops the game.
  - A randomized Nuzlocke starts from "Nuzlocke fair": wild Pokémon and trainers random but close in strength, levels as the game has them. IronMON modes are still there if you want them.
- **Build your own:** a custom randomized game with any starter you pick. It is a custom game, not an official IronMON mode, and the app says "(custom)" wherever it names the mode.
- **Stream to OBS over Wi-Fi:** the game's picture and sound, the tracker and the attempt counter as browser sources, with a step-by-step setup page.
- **Tracker themes:** the PC tracker's color presets, your own, and an image behind the tracker.
- **Play as your lead Pokémon** (Game Boy Advance games) or your own sprite, Gen 1 to 9. Optional, off by default.
- **Animated Pokémon on every tracker:** the Walking Pals icons now cover Gen 4 to 9 and the Nat. Dex forms, on Nat. Dex builds and on the DS tracker as well as on Gen 1 to 3. They are on by default; the two Tracker Setup switches turn them off or stop them walking. A Pokémon nobody has drawn yet keeps its still picture.
- **FireRed and LeafGreen dungeon maps** for seven dungeons, by Bill Greenwald (doctrDNA), used with permission.
  - Off by default: turn them on in Tracker Setup.
  - They show routes and item spots, the same in every game, and are not part of the PC tracker.
- **Smaller additions:**
  - Custom game-over lines (UTDZac's Death Quotes).
  - Career stats.
  - The Black 2 and White 2 tracker update from NDS tracker 6.3.11.

**Easier to use**

- **Library:**
  - It opens on your games.
  - A file the tracker cannot read says why (another language, another version, a changed game) and still plays.
- **Kaizo IronMON screen:**
  - It opens on Kaizo, and the modes are in the order the rules build.
  - Each mode has a one-line summary taken from the official rules, with a link to all of them.
- **Buttons that lead somewhere:** empty screens have a button to the next step, and start buttons name the attempt they start.
- **Windows close with an X** at the top right, as the game-over popup does. The tracker's own windows keep the PC tracker's look.
- **A and B in portrait** sit on the GBA's diagonal, B lower left of A, as on other GBA emulators. If you moved your buttons with Edit layout, your layout is kept.
- **Play as your Pokémon** asks only what it needs. With your lead Pokémon there is nothing to pick: party slot 1 decides, and you are the trainer until you have a Pokémon. The Pokémon list shows only under Always use, where it lists the names that start with what you type first, and the picture and sheet buttons only under Your own sprite. The walking sprites cover Gen 1 to 9 and the Nat. Dex forms, and Always use lists every Pokémon that has one. A few later ones have none yet: with one of those in the lead you stay the trainer, as the line under the switch says. Who you play as is one of three, drawn round, and tapping Always use or Your own sprite again goes back to your lead.
- **Patched versions from the games you already added:** PATCH on a game in My games lists what KaizoCore can make of it (Nat. Dex, the growth patch, Faster, Smart AI, Super Kaizo), FireRed 1.1 and Emerald have a NAT. DEX button that says what the Nat. Dex version adds, Kaizo IronMON offers to make it under the game, and Patched versions lists your games before the phone's files. PATCH also has an ADD A PATCH FILE button. Your own copy is never changed.
- **Tracker Setup** offers only switches that work on the game you are playing: five Gen 1 to 3 switches that did nothing on DS games, and COVERAGE CALC on Game Boy games, are gone there.
- **Gold, Silver and Crystal:** Prepare picks the growth patch the IronMON rules ask for. If a copy on the Kaizo IronMON screen lacks it, the screen says so and makes the patched copy in one tap.
- **Tracker controls:**
  - RUN asks for a second tap, so a mis-tap does not throw away an encounter.
  - Tracker buttons are easier to hit.
  - Tracker Setup and the rules follow your phone's text size. The PC tracker's option names are kept.
- **Stream kit:**
  - OBS picks the sources back up after being opened before the phone.
  - The setup page covers people who already have scenes.
  - Nothing is written over the game picture on air.

**Fixed**

- **The app no longer stops responding when the game's picture stalls.** A player's AYN Thor reported "KaizoCore
  isn't responding" (a freeze, not a crash). The app now gives up waiting on the emulator after 2 seconds instead
  of freezing, and moving it to the other screen of a dual-screen handheld no longer rebuilds it mid-game. A
  freeze report now shows where the app was stuck.
- **Save states and the in-game save:** loading a save state never changes your in-game save on Game Boy and DS. GBA already worked this way.
- **A new run keeps your in-game save, in every game.** Continue on the title screen opens it, so a save in front of the starters skips the intro on every attempt, and New Game is the clean start. The new-run window says which, and it reads the save you just made.
- **The first launch says KaizoCore is a beta** and asks for every bug and change, with the crash report switch from More right there.
- **Each Kaizo run follows its own game and mode:** favorites (three, four on Gen 4, five on Gen 5, nine on Nat. Dex) with the mode's own favorites rules under the boxes and a warning for a legendary the mode does not allow; the game-over rule, Survival's heal limit and Journey's starter read from the run's settings even when only its saved details name the mode; the rules box lists only the modes a game can run; Survival's line on Black and White and on the Johto games says what their rules say.
- **Tracker Setup shows only what works in the game and mode you are playing:** FireRed's maps only on FireRed and LeafGreen, the run-over rule and game over lines only in a Kaizo IronMON run, Play as your Pokemon only on Game Boy Advance games, and no Gen 3 switches on Game Boy or DS games.
- **Where you left off names the mode:** Standard Nuzlocke (with how the run ended, once it has) or Kaizo IronMON with the attempt.
- **The Game Over popup and A+B+Start only act in a Kaizo IronMON run.**
  - A Nuzlocke ends by its own rules.
  - A game played from your library has no run to end.
- **Nuzlocke:**
  - Rules shows your Nuzlocke rules, not the IronMON rulebook.
  - New run in a randomized Nuzlocke keeps the same rules.
  - The random ball picker is off in a Nuzlocke unless you turn it on in Tracker Setup. Kaizo IronMON keeps it on.
- **Catching tutorials:** Wally's (Emerald) and the Old Man's (FireRed and LeafGreen) no longer count as your encounter. The tracker does not read the borrowed Pokémon during them, as the PC tracker does.
- **Coming back to a game** after a tab switch, Back or closing the app opens it where you left off, not at the title screen. It is the moment you left, so nothing is rewound.
- **Stat marks and notes** can no longer be emptied by a crash while they save.
- **Saved attempts** are included in backups.
- **The backup screen says what a backup holds:** never your library games, but the current run's randomized game, so a restored run comes back whole.

**IronMON rules check** (details in [IRONMON-RULES-CHECK.md](IRONMON-RULES-CHECK.md))

- **Your favorite in a starter ball:** in a Kaizo IronMON run on a Game Boy Advance game, the tracker names the ball that holds one of your favorites, when the mode lets you take it (its official limits: one legendary at most; no BST limit in Standard and Ultimate; under 600 BST in Kaizo and the modes built on it, 600 and lower in Evo Kaizo; Survival under 580 with no legendary; Super Kaizo no legendary; Nat. Dex up to 600, and above that in Standard and Ultimate unless Strong Legendary or Mythical). It never says what the other balls hold, and IronMON Journey, where any starter is allowed, has no such line. The PC tracker does not have this line.
- **The stream shows no more than the phone.** An opponent's abilities, types and HP appear as the phone's tracker shows them, and the randomized data waits until the run is over.
- **Move info** hides the randomized numbers its move row hides.
- **DS game over follows the mode:** Standard and Ultimate end when the whole party is down, Kaizo Doubles when either of the first two faints.
- **Red, Blue and Yellow Survival** end when the highest level Pokémon faints, so the HM friend may lead a gym as that mode allows.
- **The run's record** also counts File > Restart, a kept in-game save and a backup restore.
- **Mode lines** now match the rules: when Survival's heal count starts, Journey's emergency swaps (and no ball picker in Journey), and the favorites' limits per mode.
- **Attempts count per settings file**, as the PC tracker counts per profile. Each file starts from your number as it stood; a Nuzlocke counts no attempt.
- **An attempt left before it ended** (a re-roll, a bail) goes into the run history as ended.
- **Your stats** say when a win came after state loads, retries or restarts, and a winning streak counts clean wins only.
- **A run's record** says when its official file ran without what the rules add ("50% levels", "no PART 2", "no Smart AI"), when it came from a run code, and when it was played under another game-over rule.
- **Run codes** say first when you already played that seed, and a code's passes no longer change your switches.
- **HeartGold and SoulSilver Survival** add the 7 Kanto heals when the Johto League is beaten.
- **The DS panel** no longer shows coverage counts on the main screen; they are under COVERAGE CALC, as on the DS tracker.
- **Type matchups in move info** are a switch in Tracker Setup, off by default.
- **Backups** leave out the live run's randomizer log until the run is over.
- **Streaming:** a notification brings you back if KaizoCore goes to the background while the stream is on.

**Known issues**

- DS double battles are not tracked.
- Gold, Silver and Crystal Survival: the 7 Kanto heals are added by hand.
- Black 2 and White 2 use about 1 GB of memory.
- The DS core leaks a little memory on each load.

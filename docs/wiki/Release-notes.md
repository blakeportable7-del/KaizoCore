# Release notes

Newest first. Each build's GitHub release has the same list, with the APK and its checksum.

## 1.0.0-rc38.1

**Heart & Soul**

- **Your first rival can no longer run away.** In the first rival battle outside Cherrygrove City, the rival's Pokémon
  ran once yours was low on HP, the battle ended and it counted as a win, so a trainer was skipped. A trainer's Pokémon
  now never runs from a battle, and the rival fights to the end.
- **No TM teaches an HM move, in either pool,** as in vanilla Emerald. A Nat. Dex run could put Fly or Surf on a TM
  before.
- **Trainer pictures in the log.** The randomizer log's Trainers tab shows each Heart & Soul trainer's own picture.

A run started on rc38 keeps its own game. Start a new run to get these fixes; the app makes the new Heart & Soul build
by itself.

## 1.0.0-rc38

**Heart & Soul** (from Blake's attempt 46)

- **TMs say what they teach.** A TM's bag description is the description of the move it really teaches, and the
  "Obtained" line names that move ("Obtained the TM89 GRUDGE!"). Every gym leader and everyone else who hands out a TM
  names the move it holds and reads out what it does, and the Goldenrod Game Corner's TM prizes are listed by their
  real moves.
- **No more "No?4" and "?" in the bag.** Heart & Soul has an unused item also called Sitrus Berry with no picture; runs
  no longer hand it out, or any other item the bag cannot show.
- **Gym 2 and every twins or couples battle can be fought in Kaizo.** In a Kaizo run every trainer double battle is a
  single battle, whatever your party: Amy & May and the rest send their Pokémon one at a time, walk up to you and battle
  when you talk to them even with one Pokémon, with the same prize and defeat. Kaizo Doubles keeps its doubles, a
  Nuzlocke keeps the game's own, and the Rocket Hideout tag battle with Lance is unchanged.
- **The Meowth Name Raters and the Pokémon in the scenery move,** walking in place like the Pokémon that follow you.
- **Ilex Forest no longer goes dark** after you talk to the Farfetch'd.
- **Three shortcuts are gone, so nothing is left behind:** you walk out of the Dragon's Den, you walk back out of the
  Underground after the Card Key, and the Lighthouse's second climb is back with the pharmacist's Secret Potion. Clair
  now leaves the shrine instead of standing there.
- **The Day Care refuses in a Kaizo run,** as it was meant to; in rc37 it took Pokémon.
- **Smaller scene fixes:** the Kimono Girl no longer dances off screen after Elm's call in Violet, you no longer walk
  through Lance leaving the Champion's room, and the lab aide and Elm no longer say things that are not so.
- **Trade evolutions happen by level in every Heart & Soul run.** Kadabra, Machoke, Haunter and the rest used to
  need a trade, so in a run they never evolved. Where Heart & Soul has its own way (Kadabra at level 42) that stays;
  any other trade becomes a level up as IronMON's settings do it (level 37, or 30 for one that held an item).
- **Abilities follow the official IronMON games.** Nat. Dex runs roll abilities the way Black 2 and White 2 Kaizo does:
  every ability up to Teravolt, all three slots, with the same bans (Wonder Guard, the trapping abilities, and the four
  that only work for one Pokémon). Vanilla runs roll Gen 3's abilities like Emerald Kaizo, and a hidden ability is the
  same as the first. Shedinja keeps Wonder Guard, as in every IronMON game.
- **Items and trainer held items match Emerald Nat. Dex's lists.**
- **The real catch rate.** The catch line uses Heart & Soul's own formula, with its bonus for low level wild Pokémon,
  every ball the game has (Kurt's balls included), status and the badge rule. The Catch Rates screen lists every ball
  in your bag.

**Fixed**

- **Fairy moves have their type symbol** on the tracker, and so does the ??? type.
- **Nat. Dex Pokémon on the tracker, checked one by one** (1,427 Pokémon, 847 moves, 310 abilities, 900 items):
  evolution text that was blank, 82 forms drawn as their base Pokémon, form names the log could not find (Rotom
  Heat, Arceus Fire and the rest), moves showing a power of 1, the next move's level, evolution items in the wrong
  bag tab, and the opponent's ability.
- **The route screen no longer shows wild Pokémon you have not met** in a Heart & Soul run.
- **The Egg's picture** in Heart & Soul is always the Egg.
- **Beating Lance adds Survival's seven Kanto heals** in Heart & Soul.

**Known issues**

- Not checked on a phone yet: the new Heart & Soul build in the app (it was played in an emulator on the PC).
- Two pairs of abilities share a name (As One, Embody Aspect), so the second shows the first's description.
- Some Heart & Soul grass has no cut version in the game and still does not cut.

## 1.0.0-rc37

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

## 1.0.0-rc36.1

**Heart & Soul: after updating, patch it again once** (Home > Pokémon Heart & Soul). Your official copy is already in the Library, so it only takes a moment. Your saves and attempts stay.

**Kaizo rules for Heart & Soul**

- **No running from trainer battles in Kaizo:** RUN says "No! There's no running from a trainer battle!" and the forfeit prompt is gone. A Nuzlocke keeps the forfeit, so a run can never get stuck.
- **No EXP for catching in Kaizo,** as in the Gen 3 games IronMON was built on.
- **Wild Pokémon come in order in Kaizo:** each area cycles through its Pokémon, so five kinds mean five encounters, then it starts over.
- **No HM moves in any random moveset,** so no starter or wild Pokémon knows or learns one.
- **Your first rival battle plays by FireRed's first-battle rules:** your attacks never miss and nobody gets a critical hit.
- **Kaizo only:** used-up held items stay used up, the Day Care won't take your Pokémon, the Exp. Share is off so your main gets all the EXP, shiny odds and item drops stay at the default, only Pokémon that fought roll Pickup, TMs break after one use, and there are no mints.
- **Hidden items are never TMs,** and item pools now match the source game's list, so TMs turn up as often as in Emerald Kaizo and no more.

**Faster Heart & Soul**

- After the egg comes back, the aide hands you the Poké Balls right away and you are healed, no trip home.
- The trash can in Elm's lab sparkles and just gives you the item.
- Ilex Forest's Farfetch'd goes home on the first talk, trainer phone calls are off in Kaizo (Elm's story calls stay), the Violet egg comes with Elm's call, and the Burned Tower, Suicune and story scenes are quicker.
- Short warps after the Radio Tower keys, after Lance agrees at the Lake of Rage, and after the Dragon's Den badge (no quiz). The lighthouse medicine is done at the Cianwood pharmacy, Whitney gives her badge right away, Oak calls to open Mt. Silver, and the credits can be skipped with Start or B. No trainer is skipped and no warp lands you in a fight.
- The Meowth Name Raters stand where Emerald's Name Rater does.

## 1.0.0-rc36

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

## 1.0.0-rc35.2

**Fixed**

- **Floating window:** locked and see-through, swipe to scroll it; a tap on its empty space still goes to the game. The up and down arrows are gone, and the top is one slim row at any width (in a narrow window the gear is in the menu).
- **Randomizer log:** on a Game Boy Advance game it opens on your lead Pokémon's page, as the PC tracker does. Back shows the full list.

## 1.0.0-rc35.1

**New**

- **See-through floating window:** in Tracker Setup, the Window see-through slider fades the floating tracker's background down to 30%, so the game shows through. Words, numbers, pictures and buttons stay solid. Lock the window and a tap on its empty space goes to the game.
- **Wide view:** stretch the floating tracker wide and it lays out in columns: your Pokémon, its stats and heals, then its moves, with the opponent on the right in battle. Make it taller and it goes back to one stack.
- **A slimmer window top:** the floating tracker's top is one row now. One line of text (your attempt, the battle, the weather, or the area) scrolls when it doesn't fit, and a swap icon flips between your Pokémon and the foe. Hold it to see which one it shows.
- **Edit favorites mid-run:** in a Kaizo IronMON run, Tracker Setup has Edit favorites, with your mode's rules. Save a change and the tracker's favorites row shows it right away.

## 1.0.0-rc35

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

## 1.0.0-rc34.1

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

## 1.0.0-rc34

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

## 1.0.0-rc33

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

## 1.0.0-rc32

**New**
- **A new app icon**: the cracked Poké Ball.
- **Home screen**: four modes, Play any game, Kaizo IronMON, Nuzlocke and ROM Hacks, with links to Library, More and Your stats. The **Continue** card takes you back to the game in progress and names its mode: Kaizo IronMON and the attempt, or the Nuzlocke and how it ended.
- **Nuzlocke mode** on every game the tracker reads, Red to White 2.
  - 6 presets: Standard, Hardcore, Randomizer, Monotype, Wedlocke and Genlocke. Each is a starting point, and every rule is a switch.
  - 19 rules: first encounter per area, a fainted Pokémon is dead, a whiteout ends the run, slow start, the dupes clause (and dupes as only lines you still own), the shiny clause, gifts count, static Pokémon count, cave floors as one area, water as its own area, the escape clause, a nickname reminder, level caps, rivals and team leaders as bosses, no items in battle, Set battle style, Wedlocke pairs and Genlocke. The Safari Zone can be one area or each zone its own.
  - The tracker keeps the ledger by itself: encounters, catches, gifts, deaths and level caps, in Areas, Grave and Log tabs. It warns when a rule is broken and never stops the game.
  - A randomized Nuzlocke starts from "Nuzlocke fair": random wild Pokémon and trainers close in strength, with levels as the game has them.
- **Build your own**: a custom randomized game in five steps: start from the game or any of its modes; starters (the game's own, random, or any Pokémon you pick); the world; the Pokémon; save and play. **Randomizer settings (advanced)** opens all of the randomizer's settings, about 140 in eight sections, with search, undo on each row, Save as, Copy and Paste. A custom game says "(custom)" wherever its mode is named.
- **Stream to OBS over Wi-Fi**: the game's own picture and sound (lossless, at the game's own resolution), the tracker and your attempt counter, as browser sources. A setup page tests the picture, a ready-made OBS scene comes at 1920 x 1080 (and a top-screen-only scene for DS), and the address carries a code so nobody else on the network can guess it. While the stream is on, a notification brings you back if KaizoCore goes to the background. Works for any game, in a run or not.
- **Play as your Pokémon** (Game Boy Advance games): your trainer becomes your lead Pokémon as you walk around. 980 of the 1,025 Pokémon have a sprite: all 386 from Gen 1 to 3 and 594 of the 639 from Gen 4 to 9, plus 164 forms (42 Megas, 18 Alolan, 18 Galarian, 16 Hisuian, 4 Paldean, and more such as Primal, Origin and Therian forms). It walks the way you face, stands idle, dozes off after about a minute without input, and faints at 0 HP. Your lead decides, or **Always use** picks any of them, with names that start with your letters listed first. It is drawn into the game's own picture, so the stream shows it too. After UTDZac's Sprite Is Me.
- **Your own sprite**: play as your own picture, fitted to 32 x 32, or your own sprite sheet set (idle, walk, sleep and faint sheets, with your own frame size and frame lengths). Backups keep it.
- **Animated Pokémon on every tracker**: Gen 4 to 9 now animate beside Gen 1 to 3, on Game Boy Advance games, Nat. Dex builds and DS games: the same 980 Pokémon and 164 forms. They stand idle, walk while you walk (a switch), fall asleep after 55 seconds without input, and faint at 0 HP. A Pokémon nobody has drawn yet keeps its still picture. Sprites by the Walking Pals collab's artists.
- **Tracker themes**: **EDIT COLOR THEME** has the PC tracker's 15 presets on Gen 1 to 3 and the DS tracker's 16 on DS games. Save your own presets under your own names, edit any color by its code, and export or import a theme. Auto Pokémon Themes keeps the preset you picked when it hands back.
- **Image behind the tracker**: your own photo, with Dim, see-through boxes and Fill or Fit.
- **Game over lines**: up to 100 lines of your own, 120 characters each, for the game-over screen, with or without the built-in ones. After UTDZac's Death Quotes.
- **Your stats** (Home): runs started and won (a win after state loads, retries or restarts is counted apart), time played in runs, the mode that ended the most runs, your longest winning streak of clean wins, your best run in each game and mode, and your Nuzlockes.
- **Favorites, by the rules**: 3 on Gen 1 to 3, 4 on Gen 4, 5 on Gen 5 and 9 on Nat. Dex builds, with your mode's own lines under the boxes and a warning for a legendary your mode does not allow. Ralts to Rayquaza, and the later Pokémon of every generation, can be picked now. In a Kaizo IronMON run on a Game Boy Advance game, the tracker names the starter ball that holds one of your favorites when your mode's official rules let you take it (Standard and Ultimate: any BST; Kaizo and the modes built on it: under 600; Evo Kaizo: 600 and lower; Survival: under 580 and no legendary; Super Kaizo: no legendary; Nat. Dex: up to 600, and above that in Standard and Ultimate unless Strong Legendary or Mythical). It never says what the other balls hold.
- **FireRed and LeafGreen dungeon maps**: Mt. Moon, S.S. Anne, Rock Tunnel, Power Plant, Pokémon Mansion, Seafoam Islands and Victory Road, 28 floors in all, by Bill Greenwald (doctrDNA), used with permission. They show routes and item spots, and stay off until you switch them on in Tracker Setup.
- **Type matchups in move info**: what a damaging move is strong against, what resists it and what it has no effect on. A switch in Tracker Setup, off by default.
- **Patched versions from the games you already added**: PATCH lists what KaizoCore can make of a game, FireRed 1.1 and Emerald have a NAT. DEX button that says what Nat. Dex adds, and the Kaizo IronMON screen offers to make it under the game. **ADD A PATCH FILE** puts a patch of your own in the library. Your own copy is never changed.
- **Dual-screen handhelds**: with the tracker on the second screen, the phone's FILE menu opens Tracker Setup, and moving the app to the other screen keeps the game playing.

**Easier to use**
- Library opens on My games, and a file the tracker cannot read says why and still plays.
- Kaizo IronMON opens on Kaizo, lists the modes in the order the rules build, gives each one line from its rules, and links to all of them. The rules box shows only the modes your game can run.
- Gold, Silver and Crystal: the growth patch the rules ask for is the default, and the Kaizo IronMON screen makes the patched copy in one tap.
- Bigger tracker buttons (44dp), RUN asks twice, and Tracker Setup and the rules follow your phone's text size.
- Tracker Setup has flat, full-width buttons, round pick-one choices and square on/off switches, and shows only what works in the game and mode you are playing: FireRed's maps only on FireRed and LeafGreen, the run-over rule and game over lines only in a Kaizo IronMON run, Play as your Pokémon only on Game Boy Advance games, and no Gen 3 switches on Game Boy or DS games.
- Windows close with an X at the top right. The tracker's own windows keep the PC tracker's look.
- In portrait, A and B sit on the GBA's diagonal, B lower left of A. A layout you made with Edit layout is kept.
- Play as your Pokémon asks only what it needs: as your lead, party slot 1 decides, and you are the trainer until you have a Pokémon. Tapping Always use or Your own sprite again goes back to your lead.
- Pickers list names that start with your letters first, then any name holding them.
- The first launch says KaizoCore is a beta, asks for bugs and changes, and offers the crash report switch.
- The random ball picker is off in a Nuzlocke unless you turn it on.
- Coming back to a game opens it where you left off, once.
- Rewind and cheats work in plain play. They stay off in Kaizo IronMON runs and Nuzlockes.

**IronMON rules**
- The game-over screen, Retry and New game appear only in Kaizo IronMON runs.
- The tracker and the stream show no more than the PC tracker does, and the randomizer data opens only when the run is over. One addition is the favorite-ball line above.
- Attempts count per settings file, as the PC tracker counts per profile. Your number carried over, and a Nuzlocke counts none.
- A run's record says what went back in time (state loads, retries, restores, restarts, a kept save), when its file ran without what its rules add, when it came from a run code, and when it ran under another game-over rule. Run codes say first when you already played that seed.
- Each Kaizo run follows its own game and mode: the favorites count and limits, the game-over rule, Survival heals and Journey, even from a settings file you saved yourself.
- DS game over follows the mode. Red, Blue and Yellow Survival allow the HM friend. HeartGold and SoulSilver Survival add the 7 Kanto heals when the Johto League is beaten.
- Emerald Kaizo Doubles, Chaos Kaizo and IronMON Journey get the official 60% levels, as Emerald Kaizo does.
- The Nat. Dex rulebooks say Super Kaizo's smart AI is built in, and that Nat. Dex Evo Kaizo bans pivots.
- Backups leave out the live run's randomizer log until the run is over.
- The full check: [How KaizoCore follows the IronMON rules](IronMON-rules).

**Fixed**
- The app no longer freezes ("isn't responding") when the game's picture stalls, as on a dual-screen handheld when the app moves to the other screen. A freeze report now shows where it was stuck.
- Loading a save state never changes your in-game save, on Game Boy and DS too.
- A new run keeps your in-game save in every game: Continue opens it, New Game starts fresh.
- Catching tutorials (Wally's, and the Old Man's) no longer count as your encounter.
- Stat marks and notes survive a crash while they save, and saved attempts are in backups.
- The backup screen says what a backup holds: never your library games, but the current run's randomized game, so a restored run comes back whole.
- Black 2 and White 2 find their data with NDS-Ironmon-Tracker 6.3.11's pointer.

## 1.0.0-rc31

- The app says when a new build is out, and Update installs it over the old one.
- Crash reports can be sent straight to the developer. Nothing about your games or files is in them.

## 1.0.0-rc30

- NEW RUN is ready in seconds: the next run is made while you play.
- The game saves itself every 3 minutes, and after a crash it opens where you were.
- The game-over popup says what beat you, your badges and time, and your best run.

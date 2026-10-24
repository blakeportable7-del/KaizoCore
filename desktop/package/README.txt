KaizoCore PC: Heart & Soul Kaizo IronMON in BizHawk
====================================================

This is a test version. It plays Heart & Soul Kaizo IronMON in BizHawk with a fast New Run: press the same buttons
as besteon's tracker and the next run opens in about half a second, because the next two runs are already made
while you play.

For FireRed, Emerald, Nat. Dex and every other game, keep using besteon's Ironmon-Tracker exactly as you do now.
This only takes over for Heart & Soul sessions.


What you need
-------------

- BizHawk 2.11.1 on Windows.
- Nothing else: Java comes with it, in the runtime folder ("KaizoCore PC.bat" uses it). Without that folder
  it uses the Java installed on this PC, or asks for "Java 64-bit Offline" from
  https://www.java.com/en/download/manual.jsp
- Your own Pokemon Emerald (USA) .gba and the Heart & Soul team's 2.0.6 patch, pokemonHnS_v2.0.6.ups, from their
  GitHub page: https://github.com/PokemonHnS-Development/pokehns-expansion/releases/tag/Release-v2.0.6
  A Heart & Soul 2.0.6 .gba you already patched works on its own. Nothing in this folder contains a game or their patch.


Set up once
-----------

1. Unzip the KaizoCore-PC folder somewhere that is not synced by OneDrive, for example C:\KaizoCore-PC
2. Copy your Emerald (USA) .gba and pokemonHnS_v2.0.6.ups into that folder (or your Heart & Soul 2.0.6 .gba).
   The first time KaizoCore PC starts it makes "Pokemon Heart & Soul 2.0.6.gba" there from them and checks it is
   2.0.6 exactly. Your own files are never changed. KaizoCore's own comfort patch goes on top every time it starts.
3. Optional: open kaizocore-pc.ini in Notepad and put your besteon tracker folder after tracker=, for example
   tracker=C:\PokemonIronmon\Tracker\Ironmon-Tracker
   Then New Run uses the button combo you set in that tracker, and the runs and the attempt count go where its
   New Run puts them. Without it the combo is A + B + Start and the runs go in the runs folder here.


Every Heart & Soul session
--------------------------

1. Double-click "KaizoCore PC.bat". A small KaizoCore PC window opens and says when the next runs are ready.
   Leave it open. Quit in that window stops it.
2. Open BizHawk. Open the Lua Console (Tools, then Lua Console). Choose Script, then Open Script, and pick
   kaizocore-hns.lua from the KaizoCore-PC folder. Load this instead of Ironmon-Tracker.lua.
3. Press A + B + Start together (or your own New Run combo). The next run opens and the attempt count goes up by
   one. Do it again any time for a new run. The title screen comes up as soon as the game boots.

That is all.

The very first time, with no game open in BizHawk yet: BizHawk reads no buttons until a game is open, so press
New Run in the KaizoCore PC window instead. Step by step:
  1. Start "KaizoCore PC.bat" and wait until its window says the next runs are ready.
  2. Open BizHawk (no game needed), then Tools, Lua Console, Script, Open Script, and pick kaizocore-hns.lua.
     The KaizoCore PC window then says "BizHawk is connected and no Heart & Soul game is open".
  3. Click New Run in the KaizoCore PC window. The first run opens in BizHawk.
From then on use your buttons as usual. The same button works any time, and the A + B + Start combo also works
while some other game is open in BizHawk. If you load a run that KaizoCore PC made earlier (File, then Open Recent), it is tracked too.

Load only one of the two scripts at a time: kaizocore-hns.lua for Heart & Soul, Ironmon-Tracker.lua for everything
else. With both loaded, one New Run press would make both of them load a game.


What you will see
-----------------

- In the KaizoCore PC window: whether the next runs are ready, whether BizHawk is connected and which run it plays,
  and the attempt count. New Run opens the next run in BizHawk (as your buttons do). Favorites sets your favorites,
  before any run too. Open folder opens this folder.
- The tracker panel to the right of the game, in the same place and size as besteon's tracker, laid out the same
  way: your Pokemon out of battle (HP, stats, level, item, ability, heals in your bag), the opponent when a battle
  starts (level, "New encounter" or "Last seen", the stat boxes, how often you have seen it, the moves it has used,
  the catch chance), and the strip at the bottom for notes, badges and the last attack. Capture the BizHawk window
  in OBS as you do now.
- Press Start in a battle to switch the panel between your Pokemon and the opponent, as in besteon's tracker.
- Click one of the opponent's stat boxes to mark it (blank, +, -, =, the same cycle as besteon's). Click
  "(Leave a note)" to write a note on the Pokemon it shows; Save keeps it. A right-click on the panel goes back one
  card, and only that. Marks and notes
  belong to that run: open the same run again later, even days later, and they come back.
- What belongs to one run, in its seed folder: its game and log, the marks, notes and encounters, its play time and a
  saved attempt. Everything else is one copy for all runs, in this folder: favorites.txt, "tracker options.txt" (the
  gear's settings), theme.txt, and the Twitch sign-in and twitch-chat.txt. Older copies kept beside the runs are
  picked up once, the first time this version starts. The attempt count stays where besteon keeps it.
- The gear at the top right of the panel opens the settings: Battle, Display, Theme and Play as tabs, a box to tick
  for each option, and the tracker's color themes. The arrow at the bottom right goes back. Your choices are kept for
  next time, for every run.
- The strip at the bottom of the panel turns through what besteon's does: your badges, the note on the opponent, the
  last attack's damage (the sword turns red when it would knock your Pokemon out), how many of the area's Pokemon you
  have seen ("Walking: 3/8 Seen Pokemon", while you walk an area with wild Pokemon and in wild battles), the trainers
  beaten on the map ("Trainers defeated: 2/8", while you walk a map with trainers and just after a trainer battle), a
  battle detail, and the step counter if you turn it on. It holds still while the mouse is on it, so a click opens
  what it shows.
- Theme tab, "Auto Pokemon Themes": the panel takes the colors of the Pokemon on it: your Pokemon in battle, the
  opponent when its card is up, your lead out of battle. A lead with no theme, or the option off, gives you back
  the theme you picked. A theme you pick while it is on is the one it gives back.
- Display tab, "Animated Pokemon (Walking Pals)": the panel's Pokemon pictures from the walking sprite set instead of
  the GBA pictures. "Use my own pictures": your own picture for a Pokemon, where you have one. "+ Add my own pictures"
  opens a window to choose PNG files named after their Pokemon (Gengar.png, or the National Dex number, 94.png); they
  are copied into the "pictures" folder here and used in every run.
- Play as tab, "Play as your Pokemon": your character in the game becomes a Pokemon while you walk around (the
  trainer is hidden; it changes the picture only, not the game or your save). Choose who: your lead Pokemon, one
  Pokemon always ("+ Pick who to always use", typed on the panel's keyboard, with Shiny if you like), or your own
  sprite ("+ Choose a picture" for one picture, "+ Choose a sheet set" for idle, walk, sleep and faint sheets). Your
  files are copied into the "play as" folder here. It walks, turns, sleeps after 55 seconds with no button, and faints
  at 0 HP, as on the phone.
- Before you have a Pokemon, the panel is the start screen: the attempt count and your favorites, the first three as
  pictures and the rest on a line under them. Click the favorites box to set them on the panel itself: click a slot,
  type the name with the panel's keyboard, and click the name it offers. Remove empties the slot, Done goes back.
  (The Favorites button in the KaizoCore PC window does the same, with or without a game open.) You pick them once:
  they are one list for every run, kept across New Run, and change only when you edit them or a mod uses !fav.
- Notes: click "(Leave a note)" on the opponent or on a Pokemon's info card, and type the note with the panel's
  keyboard; it shows as you type. Save keeps it, the arrow at the top right leaves it as it was. Nothing you type
  reaches the game, and no window opens over BizHawk.
- In the lab, before you take a starter, the top of the start screen is besteon's ball picker: three balls, the
  arrow over the randomly chosen one, and the die beside them to roll again. If one of your favorites is in a ball
  and this mode's rules let you take it, the arrow is over that ball instead and it is named. Nothing is ever said
  about what the other balls hold.
- When the run ends, the panel shows the game over card (or the win card): the attempt, the play time, a line from
  the announcer (click the Pokemon's picture for another), then Continue playing, Retry the battle (it asks "Are you
  sure?" and takes you back to the start of that battle), Save this attempt (the run's folder keeps its game, its log
  and a save state for good), Grade my notes (how good your stat marks were), and Inspect the log. Once you have
  beaten two trainers, View log and Prize card sit side by side instead: the prize card is a GachaMon card from one
  of the trainers you beat.
- Inspect the log draws the log viewer over the game itself, in the same place as besteon's: the tab pictures along
  the top (Pokemon, Trainers, Routes, TMs, and the extras), the page count with its arrows, and the X to close it.
  Click a Pokemon, a trainer, a route or a move and its page or its details open; the arrow at the top right goes
  back. The mouse wheel turns the pages. While a list is up, the panel beside it is the search: pick how to sort and
  what to search by, then type with the panel's own keyboard. The log never shows up mid-run, only after the run
  has ended.
- The tracker window stays on top of BizHawk and every other window, so it never hides behind them. Untick "Keep
  tracker on top" in the KaizoCore PC window if it covers something you need (on stream, check it does not sit over
  your game capture or chat in OBS). Drag its edge to make it any size; picking a size in the KaizoCore PC window
  snaps it back to a sharp one.
- On the panel: the mouse pointer turns to a hand over anything you can click; the mouse wheel turns the pages of
  the card that is open; a click on nothing clickable closes an open info card; the battle effects under the panel
  (two lines, turning every few seconds, "1/3" when there is more) open Battle Details when clicked.
- The level-up moves card names only the moves your Pokemon has learned so far; the ones still to come show their
  level only, as besteon's does. For an opponent it names only the moves it has used in this battle. The catch
  chance follows the game's own Gen 3 rules.
- Trainer levels: Kaizo raises them 60%, but a trainer Pokemon above level 50 before the raise gets less of it,
  so only Red's strongest reaches 100 and Kanto's gym leaders top out between 85 and 89 instead of all sitting at
  100. A run started on an earlier version keeps its levels; the next New Run uses the new ones.
- Whirlpool works outside battle only with the seventh badge and a party Pokemon that knows it. Red is fought
  without hail: the snow on top of Mt. Silver brings no weather into his battle.
- In BizHawk's message area: "connected to KaizoCore PC", then "attempt 123" after each New Run.
- If KaizoCore PC is closed, BizHawk keeps running and says "KaizoCore PC is not running". New Run then does
  nothing until you start "KaizoCore PC.bat" again; it reconnects by itself.
- The runs are named like besteon's: "HnS RSE NatDex v1.2 Kaizo AutoRandomized.gba" with its .log, the one before
  it "... PreviousAttempt.gba", and the count in "HnS RSE NatDex v1.2 Kaizo Attempts.txt". The "HnS" at the front
  keeps them apart from your Emerald Nat. Dex files.
- If you bound a mouse button to a game button in BizHawk (Config, Controllers), a click on the tracker would press it
  in the game too. The script holds every game button off while you click on the panel, so it does not; still, keep
  the game's buttons on keys. BizHawk's own GBA default for Select is Space.
- Each run also gets its own folder in "HnS RSE NatDex v1.2 Kaizo seeds", named by attempt and seed, with its log
  (the last 50 runs) and its game file (the last 10). Open a run from there and it is recognized as that run. This
  uses up to about half a gigabyte of disk.
- Your in-game save carries over from one run to the next, because every run opens under the same file name. Save
  right before choosing a starter and each New Run can CONTINUE from there.
- When a newer KaizoCore is out, the window says so and Open page takes you to it. Put updates=off in
  kaizocore-pc.ini to never look.


Twitch chat (optional)
----------------------

KaizoCore PC can answer your chat the way the phone app does: !pokemon, !moves, !attempts, !progress, !heals, !about
and !help, read from your own party only (never the opponent, the seed or the log). Your mods and you can also set a
favorite from chat: "!fav 3 gengar" puts Gengar in box 3 (1 to 9, the name matched the way the drop-down matches it)
and the start screen changes at once.

To connect: click "Connect Twitch" in the KaizoCore PC window. It shows a short code and a Twitch address; open that
address on any device, sign in to Twitch as yourself, and enter the code. KaizoCore PC then answers chat as you,
in your own channel, and remembers the sign-in on this PC (sealed by Windows for your user account; no password is
ever typed into KaizoCore PC). "Sign out of Twitch" forgets it. Put twitch=off in kaizocore-pc.ini for no chat at all.


Not in this test version yet
----------------------------

- The GachaMon collection: the prize card can be looked at, not kept in a collection.
- Save this attempt keeps a BizHawk save state in the run's folder, not a copy of the battery save.
- L and R to turn the log viewer's pages (use the mouse wheel or the arrows).


Credits and license
-------------------

Pokemon Heart & Soul is made by Lil Dill and the Heart & Soul team, on RHH's pokeemerald-expansion. KaizoCore does
not include their patch or any game: you bring both, and the patch comes from their own pages.
Play as your Pokemon follows Sprite Is Me, the Ironmon Tracker extension by UTDZac (MIT), and Auto Pokemon Themes
follows Fellshadow's extension (MIT). The walking sprites are from the PMD Sprite Collab, by its artists, under the
terms each artist gave, most of them the Creative Commons BY-NC 4.0 license, some Spike Chunsoft's own sprites from the
Pokemon Mystery Dungeon games; a few later ones are DarkusShadow's, used with credit.
KaizoCore PC and kaizocore-hns.lua are free software under the GNU GPL version 3 (LICENSE.txt). The randomizer
engines inside it are the Universal Pokemon Randomizer ZX and CyanSMP64's Nat. Dex fork, both GPL-3.0.

Questions: open an issue on the KaizoCore GitHub page.

# Future projects

Things Blake wants in KaizoCore after the current builds, with what is known about each. Newest first.

## Release plan (2026-10-02, Blake: "make the list of what to add for future releases")

One line per item; the entries below and the named docs hold the detail. Later the same night Blake answered the open
questions and said "i want rc34 to include rc35 updates": what was planned for rc35 now ships in rc34.

**rc34** (fix/rc34-ds-audio; it ships on Blake's go)
- Merged and green (570b0151): the rc32 audit's fixes, 247 of 264. Then the five packages started that night, each on
  its own branch from 570b0151, merged in before release:
  - sprites: Megas and forms with no walking sprite use their base species' sheet only when their own is missing; the
    missing sprites searched for (docs/research/missing-walking-sprites.md); N #10; DS form icons from the player's ROM.
  - decisions: streaming as a foreground service (P3 #80); the Battle Tent rooms the PC tracker's way (P2 #141); Calc
    Atk's low-confidence mark stays hidden (P3 #22, Blake: "no").
  - releasekey: a real release key Blake creates and holds, with a rotation lineage so every installed copy keeps
    updating (P2 #7, #8); the release gate runs the real-ROM tests (P3 #105); the update test in RELEASING.md (P3 #86).
  - playui: favorites as Pokemon icons on the no-Pokemon card (Blake's screenshot); P2 #21, the rest of P2 #19 and
    #27, P3 #79, #88, an in-app licence page (P3 #4); N #5, #9, #11, #17, #20, #28, #31.
  - followups: the other rows of docs/RC35-NOTICED.md and Gen 2 eggs for the gift rule (the rest of P2 #140).
- Added 2026-10-03 (Blake: "Max dex is in rc34"): MaxDex, merged from feat/maxdex (8a58c6f3), and Play as your
  Pokemon and Walking Pals on it (5c32d6a5); American spelling everywhere a player reads (cd057608). In progress
  the same day, each on its own fix/rc34-* branch: Play as your Pokemon coming back after a restart, and walking and
  turning for every sheet (playas-resume); play as shiny and the forms' walking sprites (shiny-pals); shiny and form
  pictures on the GBA tracker from the player's ROM, and HeartGold forms (rom-icons); the swap walking all four
  Pokemon in doubles (doubles-view); MaxDex on the Nat. Dex 1.1.3 rules, a 600 BST starter legal, and Favorites As
  Sources for OBS (maxdex-rules-favsources). GachaMon went to rc35 (below).
- Then the full suite, the device checks the packages list (opening Play on a Game Boy, GBA and DS game in both
  orientations first; P2 #81 is settled only on a device), new screenshots (P2 #1), and Blake's go.

**After rc34**
- The KaizoCore feature tour on willowcreek.group (Blake: "i want that to be on my website after we finish rc34"). Its
  draft and images are saved at C:/Users/bepor/KaizoCore-site-drafts/feature-tour-2026-09-30 (the published draft is the
  claude.ai artifact "KaizoCore Feature Tour"). The rc34 page is C:/Users/bepor/KaizoCore-site-drafts/tour-rc34, with
  INSTALL.md saying how it goes in the release build (proposed /kaizocore-tour); it waits on rc34's last screenshots.

**rc35** (Blake, 2026-10-02: "clearzo is in rc35", "no nightlies, and no gambatte to worry about"; started once rc34's
packages are merged, so its branches begin from the rc34 that ships)
- Full Clearzo (size M; docs/research/full-clearzo.md) and the per-game rule adjustments (entry below).
- MaxDex moved into rc34 (above). Blake, 2026-10-03, on comparing our port with Trip's jar on fixed seeds: "we can wait
  until it breaks on someone's run or I hear about it".
- No nightlies: every emulator core built here from a pinned source commit, with that source linked from each release.
- No Gambatte: Game Boy and Game Boy Color move to a core whose licence fits GPL-3.0 (mGBA, already the GBA core, or
  SameBoy), with the Gen 1 and 2 trackers proved on it. A Game Boy save state made before cannot load after, so the
  release notes say so; in-game saves carry over (to prove).
- Google Play stays "not yet" (P3 #9, #13).
- GachaMon, built (feat/gachamon; Blake, 2026-10-03, after it was explained: "Build it"): the PC tracker's card game,
  its ratings unchanged and checked against its Lua, on every Game Boy Advance run, Nat. Dex and MaxDex included.
- **A description for every move** (Blake, 2026-10-03: "fairy wind didn't have a description in emerald, need to add
  descriptions to all moves"). **Done for Nat. Dex and MaxDex in rc34.1:** GbaTracker.moveDescription reads
  natdex/movedesc.tsv (tools/trainer-data/convert_natdex_move_desc.py) past 354, by id on Nat. Dex and by name on
  MaxDex, and every move of both has one (NatDexMoveDescriptionTest). Its three sources, in order: the extension's
  natDexMoveDescriptions, which has only 11 real ones (the other 482 read "Not implemented yet.", which the PC
  tracker shows); the DS tracker's MoveData for the 201 moves Black and White had; Pokemon Showdown's move text (MIT,
  data/text/moves.ts pinned at commit 9fb3a5b9 in tools/trainer-data/sources) for the other 281: its shortDesc, never
  its desc (Blake, 2026-10-04, on Doodle's 837 characters: "not 800 characters"), capped at the longest Gen 3
  description (174), with KaizoCore's own line for the 10 whose shortDesc reads "No competitive use." or "No
  additional effect." (OWN in the script). Gen 1, Gen 2, Gen 3's 354 and the DS trackers already had a description
  for every move.
- Read Nat. Dex and MaxDex move descriptions from the player's own ROM (the game's summary-screen text table), so the
  tracker matches the game word for word; Showdown's lines stay as the fallback.
- **The bottom strip is slow to show the route and weather** (Blake, 2026-10-03). **Done on fix/rc35-little-things:**
  out of battle the GBA tracker read every 700 ms; a map id is adopted on its second read, and a battle's data counts as
  ready only when a read lands on the intro's party summary or the opponent's send-out (the reference's DataStart,
  checked every 10 frames), so the route line waited up to 1.4 s after a map change and the battle's lines usually
  waited for the action menu. TrackerState.settling makes the loop read again after 100 ms (TrackerPoll).
- **Seen (Trainer) goes up by one on each app restart** (found in rc35.2 QA, 2026-10-04). In attempt 791, paused in Lorelei's battle, the floating tracker's "Seen (Trainer)" line for Leafeon read 17, then 18, then 19 after three app starts with no new battle. **Done on fix/seen-and-log:** EncounterBook kept which opposing Pokemon a battle had already counted only in memory, so every new process counted the battle it resumed into again (GBA, Game Boy and DS alike, wild and trainer). The battle in progress is now written into the run's encounters.txt in the same write as the counts (StatMarks.commitBattle, a "battle-gba:" or "battle-ds:" line an older build skips). A new process holds it until the first read that shows an opponent: the same battle (the same enemy party, or on a Game Boy game a Pokemon it already counted; on DS a PID it had) counts nothing again, another one ends it and records its levels. Reads from before the saved state loads do not end it. EncounterBookRestartTest (7 cases) goes red with the fix reverted.
- **Nat. Dex 1.2.2** (Blake, 2026-10-04: CyanSixFour showed a v1.2.2 preview with an "accelerator" and contextual random battle music). When it is public: get the patch and the matching NatDexExtension, check the header slots the tracker reads, the randomizer settings, the bundled patch list and Set up a game, and whether the accelerator clashes with KaizoCore's own speed control or the tracker's timing.
- **Random MAC address stays hidden** (rc34.1, 2026-10-04). Until rc34.1 the DS settings row did nothing: the core's
  own firmware had no Wi-Fi block, so every game read the address as FF:FF:FF:FF:FF:FF whatever the row said. Core
  patch 0003 gave that firmware a real Wi-Fi block, and from then on the row would give the DS a new address every
  boot, which a Gen 4 Pokemon game takes for another console: its daily events locked for a day on every continue.
  rc34.1 removed the row and sends the core "disabled" on every boot (CoreOptions.FORCED; a value saved before is
  dropped; CoreOptionsTest). Bring it back only with an explanation of that cost on the page, if at all.
- **DS states should keep the 3D engine's polygon mode** (rc34.1 follow-up, 2026-10-04). melonDS 0.9.3's savestate
  leaves out PolygonMode, PolygonAttr, CurPolygonAttr, TexParam and TexPalette, and stores each polygon's vertex
  indices and LastStripPolygon divided once more by the struct size (upstream melonDS saves those fields today, and
  fixed the indices in PR #1864, January 2024). rc34.1's core patch 0004 stops the crash that caused (a state
  loaded mid-quad wrote past the vertex buffer: Black 2's resume crash), but such a state still draws its first frame
  with the wrong mode and vertices. **Done on fix/rc35-little-things:** core patch 0005 saves the five fields in state
  version 9.1 and writes the indices right; a 9.0 state still loads as before, and a state from a newer core is refused
  in words (MelonState.loadable). The core was rebuilt from the pinned source with patches 0001-0005 (MelonDsCoreTest).
- **DS ability messages every frame** (rc35 list, 2026-10-04; not done, sized). Feasible on Gen 4: the message id is a
  u16 at the battle's heap block (0x02000000 + versionRel + battleSubscriptMsgs, NdsTracker.pollAbilityTrigger), fixed
  for a battle, so the native trigger tap can watch it. It needs: triggertap's RAM_SIZE per arm (DS main RAM is 4 MB,
  the tap accepts 256 KB) and a 16-bit watch (it compares a u32), through the JNI, GLRetroView and RetroTriggerTap;
  NdsTracker arming the tap when a battle's versionRel is known and again when it moves, disarming out of battle, and
  draining into pendingMsgs, which its reads already resolve; TriggerTapSim in the tests to match; then a DS battle with
  an ability message at 8x on a device. Gen 5 reads a per-battler word that stays set, so the wall clock already sees
  nearly every reveal there. The reference itself looks every 8 frames on Gen 4 (BattleHandlerGen4.lua:110).

**Built or started, waiting on others**
- Play as your Pokemon on Game Boy: feat/gb-play-as, 7 commits not merged.
- RogueMon: built and switched off on feat/roguemon. Waits on Crozwords' and drumstix576's written yes and donation
  links, and mason's for the leaderboard (emails drafted 2026-10-02).
- Paradox Kaizo: waits on Lightorias's patch, randomizer source and settings (email drafted 2026-10-02). Its rules page
  and rule marks can be built now, switched off.
- IronmonConnect on Twitch: waits on WaffleSmacker and the extension going public.

**Log viewer parity check** (Blake, 2026-10-04, with PC log screenshots of Nat. Dex FireRed): compare our GBA log page by page with the PC LogOverlay and fix gaps. **Done on fix/seen-and-log** (table in docs/LOG-VIEWER-PARITY.md): the PC opens its Trainer Info and Pokemon info panels beside the log; ours now puts their lines at the top of the page. A trainer's page has its route, "Pokémon: N (Lv.a -- b)", Avg. IVs, AI Script, Usable Items, # id and Double Battle (LogInfoPanels, from the ROM's trainer entry); a Pokemon's page has Weak to, History (the run's tracked moves), Show resistances and its note with "(Leave a note)", saved into the notes Play uses (RunMarks). Still missing: tapping a move or an ability for its info (no move or ability info screen in the log), EXP yield. Images (Blake, 2026-10-05): every PC log picture is the game's own here too (the doc's Images section); the Game Boy log gained its Pokemon icons, a route page its idle icons and the player's head; DS trainer pictures stay out (no ROM source).

**Streamer features** (Blake, 2026-10-04: "put 1-6 on the future list"; no desktop version, the phone-to-OBS path is the focus)
   **All six are on one branch, feat/stream (2026-10-05), not on master yet:** feat/stream-sources, feat/stream-obs and
   feat/stream-twitch merged in that order. One More > Stream page holds the OBS card, then the Twitch card. The OBS
   scene has 15 browser sources, item ids 1 to 15 in source order (game, tracker, attempts, game over, timer, the nine
   favorites, history). Open connections: with every source shown OBS holds four /events streams (tracker, attempts,
   game over, timer) of the browser's six per host, plus the game's WebSocket; /history polls its page every 30 s and
   opens no stream, so the merge added none and nothing was moved to polling. Fixed on the merge: OBS's password is
   sealed with the Keystore (KeystoreSealedFile, shared with the Twitch sign-in) in filesDir/obs-password.bin, out of
   backups, and a plain-text one in prep/obs-link.txt is moved over once; leaving Play or closing the game goes back to
   the game scene (StreamFeed's dispose, ObsLink.gameStopped). Still open: a check with a real OBS 28+ and Blake's
   Twitch account; the app going to the background mid-battle leaves OBS where it was (the game is paused, not stopped).
1. Game-over card for stream: an OBS source that animates what ended the run, the attempt, badges, time and the GachaMon card (3-4 h).
   **Built 2026-10-05 on feat/stream-sources, merged into feat/stream:** `/gameover` (and `/gameover.json`, the `gameover` event on
   /events). Its data is the game-over popup's own: the latch's outcome, the death card RunHistoryHook filed (what ended
   the run, trainer, place, badges, time played, best other run), the popup's DeathQuotes line, and the run's GachaMon
   card as its facts (the prize card when made, else the fallen lead's capture; the card itself is Compose-drawn on the
   phone and is not served) with the Pokemon's picture from `/mon/<species>.png`. Clears on NEW RUN (PrepStore.installRun
   calls StreamHub.newRun) and on Retry; `&hold=`, `&demo=1`. In the OBS scene, centred over the game.
2. OBS reacts by itself through obs-websocket: switch scenes on battle start and game over, save the replay buffer at the moment of a loss (4-6 h).
   Built 2026-10-05 on branch feat/stream-obs, merged into feat/stream: More, Stream (StreamSettingsScreen) takes the PC's address, port and password, tests the connection (GetSceneList, GetReplayBufferStatus) and picks a game scene, a battle scene and a game over scene from OBS's own list, plus "save the replay buffer when a run ends". ObsLink (stream/ObsLink.kt) is the phone as an obs-websocket 5 client (stream/ObsWebSocket.kt, SHA-256 challenge, no new dependency) on a thread of its own: battles must hold 1.2 s before a switch, a loss goes to the game over scene at once, it switches only from one of the three scenes (Starting soon is left alone), retries 1 s up to 30 s, stops on a wrong password. Fed from StreamFeed: the tracker's inBattle and the game-over latch. Leaving Play or closing the game goes back to the game scene at once (merge fix). The password is sealed with the Keystore, not kept in obs-link.txt (merge fix). Tested against a fake OBS (ObsLinkTest, ObsWebSocketTest); still needs one check with a real OBS 28+ on a PC.
3. Run timer and splits source: time per badge and gym (3-5 h).
   **Built 2026-10-05 on feat/stream-sources, merged into feat/stream:** `/timer` (and `/timer.json`, the `timer` event). The time is
   RunClock's time played (counts while KaizoCore is in front with the run's game open; stops in the background, screen
   off, during NEW RUN; fast forward is real time) and holds at the end. Splits: RunProgress keeps the time each badge
   bit was first seen earned, filed with the run as RunRecord.splits (a new 21st column of runhistory-<game>.tsv);
   deltas are against the best earlier run with splits on the same game and settings file (win, badges, fastest to its
   last badge, earliest). In the scene, hidden. Not done: gym leader names on the rows (they say Badge 1 to 8).
4. Run history page for viewers: past attempts, how far each got, best run, most common killers, as a link or a source (4-6 h).
   Built 2026-10-05 on branch feat/stream-obs, merged into feat/stream: /history (and /history.json) on the stream server, token-protected, from the app's own RunHistory files for the game and settings file last randomized: runs and wins, the best run, the Pokemon and trainers that end runs most, the latest attempts (badges, where, what ended it). ?bg=none for a see-through source, ?rows=N. On the setup page's links and in obs-scene.json as "KaizoCore history", hidden over the tracker. Run facts only: no seed, no paths, no integrity counts.
5. Stream Connect: Twitch chat commands like the PC tracker (!pokemon, !moves, !attempts, !gachamon), the player signs in on the phone (15-30 h).
   BUILT 2026-10-05 on feat/stream-twitch, merged into feat/stream, waiting on Blake's test with his own Twitch account. More > Stream:
   Connect Twitch shows a code for twitch.tv/activate (Device Code flow, public client gge79biuenmcj3fpd4xrq1m21pfd35,
   scopes user:read:chat and user:write:chat only). Chat is read over EventSub (channel.chat.message, hand-written
   wss client in stream/twitch/WsClient.kt) and answered as the streamer through Helix, threaded under the question.
   Commands: !pokemon (!mon), !moves, !attempts, !gachamon, !progress, !heals, !about, !help (!commands), each with a
   switch; 10 s per-command cooldown and 8 answers per 30 s in all. Answers come only from the stream page's snapshot
   (hidden stats stay hidden, the opponent, seed and log are never answered) plus GachaMon's card for !gachamon, which
   waits for the summary too. Tokens: AES-GCM with an Android Keystore key, filesDir/twitch-session.bin, out of the
   backup, Cloud sync and crash reports; Sign out revokes. Drops reconnect with backoff up to a minute. Not done: the
   PC tracker's other commands (!bst, !weak, !move, !ability, !route, !trainer and the rest) and channel point redeems;
   most of them read the randomized game's data, which needs the IronMON rules check the stream page had first.
6. Overlay themes for the tracker source, including the HUD cockpit look (3 h).
   **Built 2026-10-05 on feat/stream-sources, merged into feat/stream:** `&theme=clean` (see-through, outlined text) and `&theme=hud`
   (dark glass, thin glowing cyan lines, two cut corners and brackets, scan lines, monospace numbers) on /tracker,
   /attempts.html, /gameover and /timer. Plain CSS in assets/stream/themes.css, put into the page by the server
   (StreamThemes) only when asked; system fonts only.

**Ideas** (Blake, 2026-10-04: "put it all on the future plan"; Blake ruled out importing PC tracker notes, which are per seed)
- Race a friend's seed: paste a shared seed (the log's Share Seed) and play the exact same game. Small.
- Your own run stats in the app: attempts over time, best runs, how far you usually get, what ends your runs.
- Bigger text option for the tracker, for small phones or eyesight.
- Tablet layouts that use the extra room.
- Personal milestones: first Elite Four, 100 attempts, every gym leader beaten. For fun, no tips.
- Seed of the week: opt-in, everyone plays the same seed and compares how far they got. Ask the IronMON mods first.
- Battery saver for long sessions: lower frame pacing when idle, screen-dim awareness.
- Run card to share: after a game over, one tap makes an image (attempt, how far, what ended it, seed, a clean-run line).
- Save the last 30 seconds: an on-phone replay buffer, one tap saves the death clip to the gallery, no OBS needed.
- Two-screen handhelds (AYN Thor and similar): DS on both screens like a real DS, or the tracker on the second screen.
- Auto speed: fast-forward while walking, normal speed the moment a battle starts.
- Handheld presets: button layouts and controller maps for AYN Odin and Thor, Retroid, Anbernic.
- Nuzlocke graveyard: every Pokemon lost this run, with where and to what.
- First-run walkthrough: add your game, pick a mode, start a run.
- Spanish and other languages.
- Low-end phone mode: lighter graphics and a lower DS resolution for older phones.
- Home screen widget: current attempt and game, one tap continues the run.

**KaizoCore-only modes** (Blake, 2026-10-04)
- Daily Kaizo: one seed per day for everyone (seed from the date, no server), one attempt, a spoiler-free share card. Blake: "That's unique, I like it".
- Endless Kaizo: beat a gym and the game re-randomizes into a new seed, your Pokemon carried over. Blake: "endless ironmon is cool".
- Kaizo on a trending ROM hack nobody has IronMON'd (research 2026-10-04): top pick Pokemon Heart & Soul (Lil Dill and team, Johto on pokeemerald-expansion, open source; the Hackdex page invites anyone to "improve, expand upon, or make your own version", which Blake took as the go on 2026-10-04; a courtesy note in their Discord, not a blocker), then Pokemon Crossroads (eonlynx/justgoose, beta), Unbound (Skeli, closed source, CFRU), Emerald Imperium, Renegade Platinum. Expansion hacks need our own randomizer or the hack's built-in one, plus a tracker profile per hack version.
  Plan for Heart & Soul Kaizo (about 15-25 h, after the HUD and Clearzo): target v2.0.6 (repo pokehns-expansion, tag Release-v2.0.6; 2.0 is their last major version, hotfixes only; the 1.2.1 patch in Downloads is outdated). The player patches Emerald (USA) themselves on Hackdex or from their GitHub, the only official sources; KaizoCore never bundles the patch, it recognizes the patched ROM by checksum. Steps: build v2.0.6 from source for the map file and prove the checksum; check whether their built-in randomizer (Gen 1-9, seed setting) can make Kaizo IronMON settings, else a KaizoCore reader and writer for its tables; tracker profile (our expansion structs mostly carry over); rules page adapted to Johto, noting the hack is not balanced around challenge settings; credit Lil Dill, the team and RHH; tests on real builds.

**Later**
- PC tracker parity: docs/parity's verify tables still mark about 350 rows missing, some of them PC-only. The visible
  ones: the Game Boy log viewer, View log from Extras, Time Machine points on Game Boy.
- Play as your Pokemon on DS (HeartGold and SoulSilver first), a desktop version, 3DS (research only), Emerald MaxDex,
  and the 2026-09-29 repo list.

## 2026-10-03: 3DS, tabled (Blake: "okay, table it")

Blake asked whether X/Y and Omega Ruby/Alpha Sapphire could come later and how to test 3DS. Found the same day:

- **No keys, ever.** KaizoCore never ships or downloads 3DS keys (aes_keys.txt, boot9, seeddb), and a KaizoCore
  build of Azahar turns its built-in key blob off (ENABLE_BUILTIN_KEYBLOB, src/core/hw/default_keys.h). Players
  bring decrypted games instead, which need no keys: Blake's eight USA dumps (X, Y, Omega Ruby Rev 2, Alpha
  Sapphire Rev 2, Sun, Moon, Ultra Sun, Ultra Moon) all have NoCrypto set on the game partition and no seed
  crypto (read from the NCCH flags inside the zips, nothing extracted). Sun/Moon files are 4 GB padded: trim on import.
- **Testing:** the AVD cannot run Azahar (Android build is for arm64 phones with GLES 3.2 or Vulkan 1.1). Develop
  against Azahar for Windows (azahar-windows-msvc-2126.1.2.zip, 43.9 MB, official GitHub; its RPC server is the
  same as the Android app's), then check on a real phone (Snapdragon 835 or better).
- **Phases and size** (docs/3DS-RESEARCH.md has the research): 1 Azahar on the PC with X/Y booting, 1-2 h; 2 the
  X/Y party reader over the RPC, about 8 h; 3 the tracker for X/Y and ORAS then Sun/Moon and Ultra, 24-40 h; 4
  randomizing on the phone, 16-32 h; 5 Azahar built in (keys off, decrypted only, GLES 3.2 or Vulkan, tracker
  memory access), 40-80 h; 6 real-phone testing, 10-20 h. About 100-180 h in all (50-80 h for the tracker-beside-
  Azahar route, phases 1-4).
- **Claude usage:** measured on rc34 at about 2 to 2.5 percent of a Max weekly allowance per agent-hour, so the
  tracker route is about 1.2-2 weeks of allowance and the whole of it about 2.5-4 weeks.

## 2026-10-02: Complete the sprite catalogue (from Blake: "followup for the future to complete the sprite catalogue due to the missing sprites")

Counted 2026-10-02 on fix/rc34-ds-audio. Complete today: the Gen 1 to 3 walking sprites (walkingpals/), the GBA and
Nat. Dex tracker icons (gbasprites/, 1,285) and the DS species icons. What is missing:

- **Walking Pals after Gen 3** (Play as your Pokemon's sheets and the tracker's animated icons). They come from PMD
  Sprite Collab at 211e689353e1 (2026-09-30) into walkingpals-nat/, and natdex-map.tsv leaves 111 of its 872 Nat. Dex
  ids (412-1283) with no sheet, each with the reason:
  - 44 species nobody has drawn yet: Simisear, Simipour, Tranquill, Blitzle, Zebstrika, Throh, Crustle, Tirtouga,
    Carracosta, Amoonguss, Frillish, Shelmet, Bouffalant, Trumbeak, Gumshoos, Shiinotic, Oranguru, Rolycoly, Carkol,
    Coalossal, Mr. Rime, Falinks, Cufant, Zarude, Squawkabilly, Maschiff, Mabosstiff, Shroodle, Brambleghast,
    Toedscruel, Klawf, Rabsca, Espathra, Bombirdier, Flamigo, Brute Bonnet, Iron Jugulis, Gimmighoul, Wo-Chien,
    Chien-Pao, Miraidon, Okidogi, Iron Boulder, Iron Crown.
  - 52 Megas: 19 older ones (Venusaur, Charizard Y, Blastoise, Beedrill, Pidgeot, Pinsir, Gyarados, Mewtwo X,
    Ampharos, Scizor, Heracross, Blaziken, Swampert, Aggron, Salamence, Metagross, Garchomp, Abomasnow, Audino) and 33
    of the Legends Z-A ones (ids 1238 to 1283).
  - 15 other forms: Galarian Stunfisk, Galarian Zen Darmanitan, Therian Tornadus and Thundurus, Ash-Greninja, Small
    and Large Gourgeist, Sensu Oricorio, Dusk Mane and Dawn Wings Necrozma, Ice Rider and Shadow Rider Calyrex, female
    Oinkologne, Stellar Terapagos, White Squawkabilly.
  - What the player sees: with one of these in the lead, Play as your Pokemon keeps you the trainer (the line under its
    switch says a few later ones have no sprite yet), the Always use list leaves them out, and the tracker shows the
    still icon in place of the animated one.
  - Five already borrow another sheet (natdex-map.tsv's notes): Minior's Red Core for every core colour, the female
    Pyroar for the male, Partner Pikachu and Partner Eevee as the plain ones, Battle Bond Greninja as Greninja.
  - How to complete it:
    1. Re-run `python tools/trainer-data/convert_walking_pals_nat.py --update` once a month or before a release. Sprite
       Collab's artists keep adding sheets; the run fetches only the files it needs (25 MB from an empty cache, free),
       fills every row that has one now and rewrites natdex-map.tsv and credits.tsv. Then WalkingPalsTest and
       SpriteIsMeLogicTest, and a look at the new ones in the Always use list. Size S.
    2. Blake's call: let a Mega or form with no sheet borrow its base species' sheet, as Partner Pikachu does. That
       covers 67 of the 111 at once (a Mega Venusaur would walk as Venusaur). Size S.
    3. The 44 species have to be drawn. Sprite Collab takes contributions through its own process; any other source is
       credited in credits.tsv and NOTICE, and PARITY-FINDINGS.md's rule stands: no ripped game art.
  - **2026-10-04 (rc35, feat/darkus-walking-sprites):** DarkusShadow's overworld sprites are a second source, only where
    Sprite Collab has no sheet of its own (Blake: "use the ones i gave you if they fill in sprite collabs gap"). 18 of
    the 44 walk now, White Squawkabilly with Squawkabilly, and Mega Malamar has its own sheet (walkingpals-darkus/,
    convert_walking_pals_nat.py DARKUSSHADOW). Still open, each needing Blake:
    1. **Shinies, Maschiff, Iron Jugulis and his newer versions.** All are only on his two full Paldea sheets, whose
       public copies are scaled previews (the shiny one a JPEG); the originals need a DeviantArt login. If Blake
       downloads both originals with his own account, the converter can cut them. Size S once the files exist.
    2. **More stand-ins he has drawn** (not downloaded, outside the approved list): Mega Clefable, Mega Victreebel,
       Mega Starmie, Mega Dragonite and Stellar Terapagos, which walk as their base species today. Size S with a yes.
    3. The other 25 (Gen 5, 7 and 8, Mega Falinks) have no source; waiting on Sprite Collab stays the plan.
- **DS tracker icons for forms.** gen4sprites/ holds the 649 species, plain and shiny (1,298 files), and RomSprites
  decodes one front sprite per species from the player's own ROM (Platinum, HeartGold, SoulSilver and the four Black
  and White games; Diamond and Pearl have no path yet). The DS tracker already reads the form (Gen4.kt's `form`), but
  every alternate form shows its base species, for example Unown's letters, Deoxys, Burmy and Wormadam, Shellos and
  Gastrodon, Rotom, Giratina, Shaymin, Arceus's types, Basculin, Deerling and Sawsbuck, the Therian forms, Kyurem and
  Keldeo. Plan: decode the forms from the player's ROM the way RomSprites does the species, so no new art ships, and
  let the form pick the picture. Size M.
- **MaxDex.** Its tracker icons are complete: the extension has one for every id 412 to 1280 (869), and the MaxDex
  build bundles them. Walking Pals and Play as your Pokemon on MaxDex shipped in rc34 (5c32d6a5): MaxDex ids map by
  name through natdex-map.tsv, so they share the gaps above and every fill reaches MaxDex too.

## 2026-10-04: Banned items and abilities marked on the tracker (from Blake: "should there be an X next to the item on the tracker if your pokemon is holding a banned item? ... and then banned abilities and exceptions")

**Built 2026-10-04** (feat/rule-marks): `app/.../RuleMarks.kt` holds each mode's banned held items and abilities, quoted from
the bundled rulesets; your Pokemon's item and ability get the X and a tap on it says the rule. Huge Power and Pure Power are never marked themselves: the X goes on each physical move (the category the card shows), unless Kaizo's exceptions apply, with "will evolve" read from the seed's randomizer log (Blake, 2026-10-04). One Tracker Setup switch,
"Mark banned items, abilities and moves", on by default (Blake, 2026-10-04), also covers the move X. Left for later: Mega Stones and other items not on RuleMarks' list are never marked; Chaos
Kaizo's ability rule (allowed while not fully evolved or when it evolved into it) needs the joined form, so it marks
nothing; Nat. Dex Survival's "Cut but no HM01 yet" is not read; Gen 6 and later item names are by their usual spelling,
unchecked against the Nat. Dex ROM's table. RuleMarksTest; look tests rule_marks, rule_marks_off, ds_rule_marks.

## 2026-10-01: Banned moves marked on the tracker (from Blake: "notate on the tracker the banned moves with some-kind of annotation depending on the game mode you are playing and game")

**Built 2026-10-02** (commit 796f31c7, Blake: "just like the x on bst of 600+"): `app/.../MoveRule.kt` holds the bans per mode and game from the lists below; your Pokemon's banned moves go red with the BST rule's X while the ban holds in the battle you are in, and the move's card says why. MoveRuleTest; look tests banned_moves and ds_banned_moves. What follows is the plan as it was written.

- **What:** the tracker marks any move the mode in play bans in this game, on the lead's move list and wherever a
  move is looked up, with one line saying why (for example "Banned in Kaizo IronMON on Red"). Only in a mode with
  rules: nothing shows in Play any game.
- **Sources:** the core IronMON Move Ban List (psydetrack's gist, gist.github.com/psydetrack/884443c4c4054decce2804bb513d8d45,
  linked from the core rules Blake pasted from the IronMON Discord on 2026-10-01); UTDZac's per-game gist (Red, Blue
  and Yellow add Wrap, Bind, Fire Spin and Clamp: the per-game entry below); each mode's own ruleset (Standard,
  Ultimate, Kaizo, Survival, Super Kaizo, the Nuzlocke presets). RogueMon players asked for "banned in this ascension"
  on moves (docs/private/roguemon-discord-mapping.md, feat/roguemon's handoff branch); those bans live in the ROM, so
  ask its developers. Read each list before marking anything; never mark a ban from memory.
- **The IronMON dev team's rule (2026-09-30):** the app must not break the rules, reveal hidden information or tell
  players how to play, so the mark states the rule and nothing more.
- **Fit:** the tracker already knows the game and the mode (GearScope works both out for Tracker Setup), so this is a
  ban table keyed by mode and game plus a mark on the move row. Size: S to M, most of it reading the rules.

## 2026-10-01: IronmonConnect, the run on Twitch (from Blake: "table this to add in the future, make a note for a future release")

- **What it is:** WaffleSmacker's IronmonConnect (github.com/WaffleSmacker/IronmonConnect-IronmonExtension; MIT with a
  clause asking for prominent credit to WaffleSmacker in anything adapted from it; 3 stars, last push 2026-08-27). A PC
  tracker extension (ironmonConnect.lua) writes tracker_output.json after each change: the lead's species, nickname,
  ability, level, stats and four moves, trainers defeated, milestone, stars, badges, route, seed, a faint flag, the Nat.
  Dex flag and the bag by category, and the pivots before Brock and the Safari Zone. IronmonConnect.exe, a Windows
  program shipped as a binary in the repo, signs in to Twitch and sends that to the IronmonConnect Twitch Extension, a
  video overlay viewers click to look up the run. The README's link to the Twitch extension says "coming soon".
- **Fit for KaizoCore:** the tracker already holds every field in that file, so writing it is a mapping. The part a
  phone cannot do is run the .exe: the sender (Twitch sign-in on the phone, then the extension's backend) has to be
  built, and its protocol is not published. Ask WaffleSmacker first, as for RogueMon's leaderboard: the endpoint and
  sign-in, their go, and how they want the credit shown.
- **Size:** S for the file, M to L for the Twitch side, which waits on WaffleSmacker and on the extension going public.

## 2026-10-01: Full Clearzo, MaxDex and Paradox Kaizo modes, and the per-game rules page (from Blake)

Researched 2026-10-02; the full reports are in docs/research/full-clearzo.md, maxdex.md and paradox-kaizo.md.

- **Full Clearzo IronMON** (Typo, Puffsun, ratcityretro; github.com/ratcityretro/FullClearzo): Kaizo IronMON on
  FireRed/LeafGreen plus one rule, every trainer in a dungeon beaten before you leave it. No patch, randomizer or
  settings of its own: the Kaizo FRLG settings, and the PC tracker's `!progress sevii` count (447 trainers) as the
  score. Dungeons from pret: Mt. Moon 12, S.S. Anne 17, Rock Tunnel 15, Pokemon Tower 17 (14 to the Marowak on the
  first entry), Silph Co. 32 (Giovanni hides the rest), Victory Road 12, Sevii One to Three 26, the Victory Lap after
  the Elite Four 69. Plan: first PC parity for every FRLG mode (count by dungeon, not by map; the rival from the save),
  then the mode, a dungeon table generated from pret, a "N of M left" line and checklist, and the score on the death
  card. Size: M.
- **MaxDex Kaizo IronMON** (Trip, github.com/Tripc423/Maxdex): built on Nat. Dex 1.1.3, not the 1.2.1 KaizoCore
  ships: 1,255 species to id 1280 with the 45 Legends Z-A megas, moves to 841 with a per-move physical/special byte,
  289 abilities, 3-byte learnsets. MaxDex.bps turns FireRed (U) 1.1 (84ee4776) into 28c12926. Its rules page, deleted
  2026-07-03, ended as "Same Rules as NatDex"; it ships one mode, Kaizo. Needs a third engine (Cyan's 1.1.3 source
  plus Trip's changes, identified from the jar's bytecode), its own tracker map and data, and app wiring. Size: L.
  Started 2026-10-02 on Blake's "address the tracker to be compatible with up to gen 9 pokemon".
- **Paradox Kaizo** (Lightorias with Timur, launched on stream 2026-09-28): FireRed with random dual types, palettes
  recoloured by type, and 61 item NPCs each offering three items or a mystery one. Its patch, randomizer fork and
  settings are not public (invite only while bugs are fixed), so the real mode waits on Lightorias's files. The mode
  key, rules page, rule marks and the spoiler guards its rules ask for (no Coverage Calculator, no "Include unseen")
  can be built now, switched off. Size: M to L once the files exist.
- **Per-game rule adjustments** (UTDZac's gist, updated 2026-07-05): RBY bans Pokemon over 480 BST and Wrap, Bind,
  Fire Spin, Clamp, and needs the growth patch; GSC is won at Red on Mt. Silver and bans berry trees in Kaizo and
  Survival; FRLG keeps the Sevii Islands closed until after the Elite Four; Emerald is won at Steven too and bars
  Slateport before badge 2 in Kaizo and Survival; HGSS is won at Red; Platinum allows the Underground mining once; BW
  warns off the C-Gear. Check each against docs/IRONMON-RULES-CHECK.md and the trackers before claiming support.

## RogueMon mode (added 2026-09-29; reminder set for 2026-10-06)

**Tabled 2026-10-01** (Blake: "lets table rogueMon, for now"). Not in rc33. The work stays on feat/roguemon and the
rm/* review branches, switched off; nothing about it is sent or posted until Blake picks it up again.

Blake: "add this addition to a future project, remind me in a week". Then, the same day: "ROGUEMON lets get it up,
do your research and see if it is viable to add to the modes". **Researched 2026-09-29: yes with limits, XL.** The
full findings, the bug record and the plan are in docs/research/roguemon.md; the notes below are the first pass.

- **What it is:** Crozwords' FireRed/LeafGreen randomized Pokemon roguelike (https://github.com/Crozwords/Roguemon,
  community Discord linked there, roguemon.gg). One randomized Pokemon; win by locking every type and finishing a run
  without fainting. Prizes at milestones, cursed sections, buy and cleansing phases, three ascensions (A1 to A3),
  Roguestone as the one evolution item, randomized evolutions that keep a type and the BST distribution, a forced
  route. The README says most rules are hard-coded into the game build.
- **The pieces around it** (from docs/research/repos-to-add.md, section on Roguemon, and nuzlocke-trackers.md):
  - Rules: Crozwords/Roguemon (no licence file; 16 stars; last push 2026-07-25).
  - Builds: drumstix576/roguemon-releases (no licence file; v2.2.11-beta.0 on 2026-09-22), on the FireRed Nat. Dex
    base KaizoCore already supports.
  - Tracker extension: something-smart/Roguemon-IronmonExtension (no licence file): HP and status caps, prizes,
    segments with trainer counts, curses.
  - Randomizer: something-smart/ironmon-randomizer (GPL-3.0), a ZX fork with a Roguemon build (vR.1.1).
- **Fit for KaizoCore:** a mode on the Run tab for FireRed Nat. Dex that applies the RogueMon build to the player's
  own ROM and a tracker panel for its caps, prizes, segments and curses, the way the IronMON modes work today.
- **Effort:** XL (weeks). The ruleset and automation are large; the randomizer part alone is GPL-3.0.
- **People:** Crozwords (rules, Twitch), something_smart (tracker extension, randomizer), drumstix576 (builds).

## Parked 2026-10-01: a desktop version, and play-as on DS

- **Windows and Mac** (Blake: "can kaizocore work on PC and Mac?"): Compose Multiplatform for the app plus a small
  libretro host for the cores. Several weeks. A Mac version fills a real gap: BizHawk, which the PC trackers run in,
  has no Mac build.
- **Play as your Pokemon on DS** (Blake: "why can't DS use custom sprites?"): the DS overworld is 3D, so it is a
  per-game texture swap, not a sprite swap. HeartGold and SoulSilver first.

## Queued from the 2026-09-29 plan

- Shipped in rc32 (2026-10-01): Nuzlocke mode, the Home screen with its four modes, Build your own (any starter), and
  Play as your Pokemon on Game Boy Advance games. Play as your Pokemon on Game Boy (Red, Blue, Yellow, Crystal) is on
  branch feat/gb-play-as (docs/research/gb-play-as.md).
- 3DS: docs/3DS-RESEARCH.md and the plan Blake shared with the IronMON dev team. Research only, nothing built.
- The rest of the 2026-09-29 repo list (docs/research/repos-to-add.md: Platinum GIGA patch, IronMON HGSS, Diamond
  IronMON, the Black intro patch, the patch editor, Kelsey Young's maps, Hackdex, Following Platinum): not re-checked
  against rc32.

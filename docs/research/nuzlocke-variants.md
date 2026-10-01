# Nuzlocke variants: rules and tracker split for KaizoCore

Research only, no code. Prepared 2026-09-29 for the KaizoCore Nuzlocke mode.
Scope: Gen 1 to 5 (GB, GBC, GBA, DS), one phone, offline. IronMON is out of scope; it is
mentioned only to keep it apart from "Kaizo".

Markers used below:

- [S] a cited source says it.
- [A] my own analysis for the tracker. No source says it.
- (excerpt only) the page itself was blocked to my tools and I saw only a search-engine
  excerpt of it. Treat those claims as lower confidence.

Pokemon is written without the accent on purpose (ASCII file).

## Bottom line

1. Only two rules are shared by every source I read: catch only the first wild Pokemon
   met in each area, and a fainted Pokemon is dead. Nick Franco's own site lists just those
   two (https://www.nuzlocke.com/about/), and in 2018 he still described the rules as
   something to bend for newer games
   (https://www.destructoid.com/nuzlocke-creator-talks-pokemon-lets-go-and-keeping-up-with-the-series-changes/).
   Everything else is a per-run option, and the sources disagree on most options. The
   tracker should be a rule-profile engine (presets that set toggles, plus a manual
   override that is logged), not hard-coded rules.
2. [A] In principle the tracker can read from RAM in every Gen 1 to 5 game (addresses
   differ per game and per hack): a party member reaching 0 HP, the battle style option,
   bag and party contents, the current map. It cannot read what counts as an "area",
   which wild Pokemon is a gift or a static, or the level of the next boss. Those need a
   per-game table (ideally built from the loaded ROM, so randomizers and hacks work) plus
   player rulings. One trap: a blackout heals the whole party (Bulbapedia), so faints must
   be logged as they happen, not found by scanning afterwards.
3. Hard hacks enforce some rules inside the game, or are said to (one article says
   Platinum Kaizo Gym Leaders refuse a battle when a Pokemon is over the cap; Radical Red
   forces Set and locks the bag against bosses; Unbound has a badge-based cap; section 11).
   Each rule therefore needs a state "the game enforces this", not only "check" or "remind".
4. Verdict limiters: Wonderlocke is impossible in Gen 1 to 5 (Wonder Trade is Gen 6+);
   Egglocke needs eggs injected or supplied (and does not exist in Gen 1); Wedlocke by
   gender does not exist in Gen 1 (no gender); Soul Link, Cagelocke, Tradelocke and
   Friendlocke need other people; Genlocke needs a save-to-save carry-over that stock
   Gen 1 to 5 cannot do on one phone. "Sisterlocke" has no source at all.

## 0. Method and source quality

Raw page text read (highest confidence). These come from the public MediaWiki APIs of
the sites, fetched 2026-09-29 (Bulbapedia's normal pages returned HTTP 403 to my fetch
tool; its API worked):

- Bulbapedia, "Nuzlocke Challenge": https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge
  and the Bulbapedia mechanics pages cited in section 1.
- Fanlore, "Nuzlocke": https://fanlore.org/wiki/Nuzlocke
- Wikipedia, "Nuzlocke" (https://en.wikipedia.org/wiki/Nuzlocke) and "Kaizo"
  (https://en.wikipedia.org/wiki/Kaizo).

Read through my fetch tool, which returns a model-written summary of a page rather than
its raw text, so treat wording as paraphrase and small details as slightly less certain:

- Nick Franco's own site: https://www.nuzlocke.com/about/
- Franco interview, Destructoid, December 2018 (URL in the bottom line).
- Smogon, "An Introduction to Pokemon Nuzlockes and Challenges", author Band, 2023-04-17:
  https://www.smogon.com/articles/introduction-nuzlockes
- Marriland's Wedlocke thread, Smogon Forums, posted 2013-01-04:
  https://www.smogon.com/forums/threads/marrilands-wedlocke-challenge.3476975/
- Nuzlocke University: https://nuzlockeuniversity.ca/ (rules, optional rules, variants,
  Hardcore, Soul Link, Wedlocke, Egglocke, Wonderlocke, Generationlocke, level caps by
  generation). Secondary, but maintained and consistent with the pages above.
- JoCat's published ruleset (a well-known creator): https://www.jocat.net/nuzlocke
- TheGamer pieces, PokeBase answers, tracker sites, hack pages (URLs where used).

NOT readable by my tools (Reddit and forum hosts block them; Scribd and YouTube gave no
text): the r/nuzlocke wiki (https://www.reddit.com/r/nuzlocke/wiki/index/), Nuzlocke Forums
threads (https://nuzlockeforums.com/), PokeCommunity threads
(https://www.pokecommunity.com/), pChal's rulebook on Scribd
(https://www.scribd.com/document/713352376/Official-Hardcore-Nuzlocke-Rulebook-by-PokemonChallenges),
and pChal's 2017 rules video (https://www.youtube.com/watch?v=6W4-XxdGlW8). Where I cite
those it is from search excerpts. If the r/nuzlocke wiki matters to you, open it by hand.

All Nuzlocke rules are self-imposed and vary between players. Bulbapedia says so in its
introduction, and it says a run counts as a Nuzlocke as long as the two basic rules are
in place.

## 1. Gen 1 to 5 facts that change what is possible (verified on Bulbapedia)

| Feature | Exists from | Consequence for rules |
|---|---|---|
| Gender | Gen 2. Gen 1 shows gender only on Nidoran. Gen 2 derives it from the Attack DV; Gen 3 to 5 from the personality value. (https://bulbapedia.bulbagarden.net/wiki/Gender) | Wedlocke by gender cannot exist in Gen 1. |
| Shiny | Gen 2 (from DVs); Gen 3 to 5 from ID and personality value, threshold 8 in 65536. (https://bulbapedia.bulbagarden.net/wiki/Shiny_Pok%C3%A9mon) | Shiny clause is moot in Gen 1; detectable from memory in Gen 2 to 5. |
| Held items | Gen 2. (https://bulbapedia.bulbagarden.net/wiki/Summary) | "No held items" and Exp. Share as a held item are moot in Gen 1. |
| Eggs and breeding | Gen 2. (https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Egg) | Egglocke and the breeding step of Genlocke do not exist in Gen 1. |
| "Met at" on the summary screen | Gen 3. Gen 1 has none; Gen 2 has data only in Crystal, told by the Poke Seer. Transfers overwrite it. (https://bulbapedia.bulbagarden.net/wiki/Summary) | Bulbapedia's "check the Met location" tie-break for areas is unavailable in Gen 1 and 2. |
| Battle Style option (Shift/Set) | Every game from Red/Blue through Gen 5. In Gen 4 with Set, a knocked-out Pokemon forces the next send-out with no chance to flee. Set is forced in doubles, multi and link battles. (https://bulbapedia.bulbagarden.net/wiki/Options) | Set mode can be read (and written) in every supported game. |
| Overworld poison | Gen 1 to 3: poison can faint a Pokemon outside battle and can cause a blackout. Gen 4: stops at 1 HP and cures. Gen 5: no overworld poison damage. (https://bulbapedia.bulbagarden.net/wiki/Poison_(status_condition)) | Deaths can occur outside battle in Gen 1 to 3. |
| Blackout | Happens when the whole party has fainted. A drawn battle (Explosion, Perish Song) also blacks you out. The game then heals the party at the last Pokemon Center. English Gen 2 and 3 say "white out". (https://bulbapedia.bulbagarden.net/wiki/Black_out) | Log faints live. A later party scan sees full HP. |
| Wonder Trade | Gen 6 and 7 and Brilliant Diamond/Shining Pearl only. (https://bulbapedia.bulbagarden.net/wiki/Wonder_Trade) | Wonderlocke impossible in Gen 1 to 5. |
| Global Trade System | Introduced in Gen 4, online only. The official Wi-Fi service ended 2014-05-20 for all Gen 4 and 5 games. (https://bulbapedia.bulbagarden.net/wiki/Global_Trade_System, https://bulbapedia.bulbagarden.net/wiki/Nintendo_Wi-Fi_Connection) | No official offline random-trade source. |
| Transfers | Gen 1 and 2 trade through the Time Capsule over a link cable. Gen 3 cannot receive Pokemon from older games. Gen 3 to 4 uses Pal Park through a DS or DS Lite GBA slot. Gen 4 to 5 uses Poke Transfer between two DS systems over Download Play. (https://bulbapedia.bulbagarden.net/wiki/Transfer, https://bulbapedia.bulbagarden.net/wiki/Time_Capsule, https://bulbapedia.bulbagarden.net/wiki/Pal_Park, https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9_Transfer) | Genlocke carry-over has no stock path on one phone. |
| Wild encounter sources | Gen 1: grass, caves, fishing, surfing, overworld events. Gen 2 adds Rock Smash, Headbutt trees, Sweet Scent, roamers, time of day, outbreaks. Gen 3 deep sand. Gen 4 Honey trees. Gen 5 puddles, phenomena, doubles in dark grass. (https://bulbapedia.bulbagarden.net/wiki/Wild_Pok%C3%A9mon) | "Area" needs a per-game encounter-source table. No ruleset I read classifies these sources. |

## 2. Standard / original Nuzlocke

### 2.1 Where it comes from

- Nick Franco, a UC Santa Cruz student, March 2010, playing Pokemon Ruby; the comic
  "Pokemon: Hard-Mode" went to 4chan /v/; his site and forum followed in April 2010
  (https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge,
  https://fanlore.org/wiki/Nuzlocke).
- The rules in Franco's own words on his site (https://www.nuzlocke.com/about/):
  (1) he could only capture the first Pokemon he met in each new area;
  (2) a Pokemon that fainted was dead and was released. Two rules, nothing else.
- Franco in 2018 (Destructoid, URL above) repeats the same two guidelines and says the
  purpose is to make the game interesting, so rules can flex (for Let's Go he suggested
  treating the first Pokemon you see, or the one that spawns nearest, as the catch).

### 2.2 What Bulbapedia calls "near-universal" on top of the two rules

From https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge [S]:

- Mandatory nicknames.
- "Met in" check: if you are unsure whether a place is a new area (for example a cave with
  several levels), look at the Pokemon's summary to see where it was met.
- No soft resets to undo progress; no cheat devices, except to make the game harder.
- Full wipe: a blackout or whiteout is game over even if living Pokemon sit in the PC.
- No outside trading: no trading with other saves, no Mystery Gifts.
- Boxing is fine: dead Pokemon may be boxed permanently (or transferred) instead of
  released. (The basic rule text itself says released; boxing is the accepted alternative.)
- Trade evolution: no firm consensus on trading a Pokemon away and back to evolve it.

Two of these are contested elsewhere: nicknames (Fanlore calls them common, not universal;
sources disagree on who added them: TheGamer says Franco later added it
(https://www.thegamer.com/pokemon-nuzlocke-challenge-history/), EGM says the community did
(https://egmnow.com/hard-mode-how-a-webcomic-spawned-pokemons-most-infamous-challenge/),
and Franco's own page lists only two rules) and full wipe (section 2.3, blackout).

### 2.3 Clause by clause

#### First encounter, and what counts as an "area"

Core [S]: only the first wild Pokemon met in an area can be caught. If it faints or flees,
there is no second chance (Bulbapedia). If the first encounter is a double battle or horde,
you choose which one to catch, but only one (Bulbapedia).

Options on details:

- A wild Pokemon that flees: Fanlore says it is up to the player whether that voids the
  area. Nuzlocke University lists an optional "Escape Clause" (if the first Pokemon escapes
  with Roar, Teleport and similar, you may ignore it and try again)
  (https://nuzlockeuniversity.ca/optional-rules/).
- The player running away: none of the sources I read says. The plain reading of the rule
  is that it uses up the area [A]. Bulbapedia lists "No Escape" (never flee) as a harder
  option.
- Rules start only once you can catch: Bulbapedia "Slow Start" and JoCat both say the rules
  do not apply until you hold Poke Balls
  (https://www.jocat.net/nuzlocke).

What is an area (the sources do not agree):

| Case | Options seen | Sources |
|---|---|---|
| Route, town, named place | Each named location is one area. Smogon: a location counts if the summary screen would show it as a met place (routes, cities, landmarks). | Smogon (https://www.smogon.com/articles/introduction-nuzlockes); Bulbapedia |
| Cave floors and multi-level places | Whole cave as one area, or each floor as one. Bulbapedia's tie-break: read the Met location on the summary (Gen 3 and later only). | Bulbapedia |
| Same place name split by story progress | May count as two areas (Gen 5 Pinwheel Forest: outer part before Nacrene Gym, inner part after). | Bulbapedia |
| Grass vs water vs fishing | (a) fishing and surfing are one water area, separate from grass, so one route can give two encounters (JoCat; one PokeBase answer); (b) the route is one area whatever the method; (c) the player decides ("there are no concrete rules", a PokeBase comment). | JoCat; https://pokemondb.net/pokebase/332794/pokemon-nuzlocke-fishing-encounters-encounters-encounter |
| Safari Zone | (a) whole zone is one area; (b) each sector is an area (Bulbapedia's "On Safari" clause, and the same idea for Galar Wild Area dens); (c) free for all except duplicates (JoCat). | Bulbapedia; JoCat |
| Special sources (Rock Smash, Headbutt, Sweet Scent, Honey trees, roamers, outbreaks, puddles, phenomena, dark-grass doubles) | Not classified by any ruleset I read. Each needs a decision. [A] | https://bulbapedia.bulbagarden.net/wiki/Wild_Pok%C3%A9mon |

I could not find a poll showing which reading is most common. The r/nuzlocke wiki would be
the place to look (not readable by my tools).

#### Dupes clause and species clause

- Bulbapedia files "Species/Dupes Clause" as one clause: the first-encounter rule does not
  start in an area until you meet a species whose evolutionary line you have not caught
  yet (example: already own Caterpie, Metapod or Butterfree, so a Caterpie does not count).
  A limit on how many skips per area may be set; past it you take what comes.
- Smogon: can be applied per species or per whole evolutionary line.
- Nuzlocke University: no duplicate encounters, reroll if you already have it.
- Nuzlocke Forums rule threads (excerpt only): once you catch one, no more of that family;
  a dupe as first encounter is skipped until a viable one appears.
- Real options: species vs whole family; "ever caught this run" vs "currently owned"
  (sources word it as caught or as have/own); a skip limit per area.
- "Species clause" as a separate team-composition rule is not defined by any source I
  read. The related team-level restriction is the type clause in Soul Link (section 7).
  [A]

#### Shiny clause

- Bulbapedia: a shiny may be caught even when it is not the first encounter, and need not
  be released if it faints; whether shinies may be used or only kept is up to the player.
  A compromise, the "Shiny Replacement Clause": you may use it if you release another
  Pokemon in exchange.
- Smogon: a shiny is a free encounter that does not use the area, except guaranteed
  shinies such as the Red Gyarados.
- JoCat: all Nuzlocke rules are suspended until the shiny is caught, fainted or escaped.
- Gen 1 has no shinies (section 1).

#### Gift and static Pokemon

Gifts (starter, NPC gifts, fossils, eggs, in-game trades; Bulbapedia keeps a per-game list
at https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon):

- (a) A free extra that never uses the area's encounter: Bulbapedia says some players treat
  gifts as separate encounters; Nuzlocke University's "Gift Clause".
- (b) The gift counts as the encounter of the place where you receive it: the Soul Link
  FAQ at https://soullinks.app/en/soullink-faq says gifts and statics either form their
  own pair or use a predefined location; Nuzlocke Forums threads describe the same split
  (excerpt only).
- (c) Banned: Nuzlocke University lists a gift ban (any Pokemon or egg given by an NPC);
  Fanlore lists it among common rules that some players choose. "Giftlocke" is the
  opposite (only gifts).

Statics (Snorlax, scripted legendaries):

- JoCat: static and unique encounters are exempt and can be caught whatever happened
  before, and legendaries and mythicals may not be used.
- r/nuzlocke thread "Static encounters" (excerpt only,
  https://libredd.it/r/nuzlocke/comments/oumqpn/static_encounters/): some players count
  statics like gifts as that place's encounter (Snorlax is the Route 12 catch in
  FireRed); others exclude legendaries but let other statics count.
- Bans on legendaries are common (JoCat; Screen Rant's list of clauses:
  https://screenrant.com/rules-clauses-pokemon-nuzlocke-challenge/).

No source I read classifies in-game NPC trades or Game Corner purchases separately. Treat
them as gift-like and ask. [A]

#### The starter

- The starter is a gift, not an encounter: Bulbapedia's gift Pokemon page lists the first
  partner Pokemon as a gift (https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon).
- Bulbapedia options: "Slow Start" (rules off until you can catch; in games where the
  first rival battle comes right after the starter, a faint there is often not enforced),
  "Random Starter" (by Trainer ID: last digit 1 to 3 Grass, 4 to 6 Fire, 7 to 9 Water, 0
  free choice, or ID modulo 3), "Caught Only" (release or box the starter after the first
  catch).
- Nuzlocke University adds "Starter Ban" and "Locked Pokemon" (pick one Pokemon to catch
  outside the encounter rules).

#### Nicknames

Universal by convention, not required by Franco's two rules (section 2.2). Tracker note
[A]: "nickname differs from the species default name" is checkable from the Pokemon data;
a player who types the species name exactly cannot be told apart from no nickname.

#### Faint = dead, and when "dead" is decided

- The moment a Pokemon faints it is dead: release it or box it forever. Revival items are
  forbidden (Bulbapedia: Revive and similar); Fanlore: Revives and Max Revives are
  universally prohibited.
- Not the whole party: a wipe of the whole party is a separate trigger (next section).
- Exceptions people use: no enforcement before you can catch (Slow Start; JoCat); scripted
  first rival battles (Bulbapedia).
- Overworld poison in Gen 1 to 3 can faint a Pokemon outside battle (section 1). No
  source I read says whether that counts. It is a faint, so treat it as death. [A]
- Ways sources soften it (all optional): Nuzlocke University "Limited Revives", "Sacrifice
  Revive", "Box Replacement"; Bulbapedia "Second Chance" (for example one revive per
  badge), "Checkpoints" (restart from the last badge), "Progression Sacrifice";
  Fanlore "Surplus Hax Rule" (do not count a death caused by absurd bad luck).
- Ways sources harden it: Fanlore "Arena Trap Law" (no switching at all), "No Wipes"
  (a full wipe is never allowed); Nuzlocke University "Deathless Run" (restart if ANY
  Pokemon faints, see section 11).
- Linked variants propagate death to a partner; timing options are in sections 6 and 7.

#### Whiteout or blackout = run over

- Bulbapedia: basic rule says the run fails when you have no living Pokemon; the
  near-universal "Full Wipe" makes any blackout game over even with living Pokemon boxed.
- Smogon: whether a blackout ends the challenge is up to the player; it lists this as a
  disputed point, and says the player may restart or continue with boxed Pokemon.
- Nuzlocke University optional "Box Replacement": a whiteout is not a loss, continue with
  a new team from the box.
- JoCat: the Nuzlocke ends if all party Pokemon faint during a battle. Screen Rant lists
  a white out as run over even when reserves remain.
- Soul Link: many groups say blackout ends it, others continue with boxed pairs
  (https://soullinks.app/en/soullink-faq); a Nuzlocke Forums veteran advises playing Soul
  Link without the whiteout rule (excerpt only,
  https://nuzlockeforums.com/forum/threads/soul-link-rules-clarification.21121/).
- Friendlocke keeps playing with boxed Pokemon until every friend's Pokemon is dead
  (excerpt only).
- Blackout triggers to remember: a drawn battle counts; Gen 1 to 3 overworld poison can
  cause one (section 1).

#### Pokemon Center limits and other overworld limits

Not part of the two rules, not part of pChal's Hardcore set, but listed as optional:

- Bulbapedia "No/Limited Pokemon Centers": none, or a set number of uses per Center, or a
  set number between Gyms. Also "No Buying", "Limited Balls", "No Repels" (never, or not
  before the first encounter of a route), "No Escape".
- Nuzlocke University: each Pokemon Center only once; or ban Centers entirely; also "Gym
  Lock-In", "No Return", "Completionist" (fight every optional trainer before the next
  Gym), "Found Items Only".
- Fanlore: a healing-items ban and a Center ban are alternatives that give similar
  difficulty.

#### Other standard-adjacent clauses worth a toggle

- HM Helper (catch an extra Pokemon only to use field moves, never to fight): Bulbapedia.
- Equal Parties or Fair Fight (party no larger than the Gym Leader's or rival's): Bulbapedia.
- No Exp. Share, No Candy, No Child Support (Day Care): Bulbapedia; JoCat allows the Day
  Care for training but not breeding.
- Notepad Clause (never own more than six Pokemon) and its extreme form (only ever six):
  Bulbapedia, named after a user of the old Nuzlocke Forum.
- Trade Evolution Clause: no consensus (Bulbapedia); JoCat requires your own Pokemon traded
  back at once. On one phone with no link partner this only matters if the app offers a
  self-link. [A]
- Rare Candy Clause and Master Ball Clause (hack in infinite ones): Bulbapedia says the
  first was popularised by the streamer pChal (PokemonChallenges, see section 3).
- Game crash: JoCat lets you continue from the last save.

### 2.4 The run ends when

No living Pokemon remain (Bulbapedia basic rule), and under the near-universal Full Wipe
convention, at any blackout even with living Pokemon boxed. The choices in the blackout
section above are real options, so make the wipe rule a setting. Default suggestion: the
Bulbapedia near-universal reading (blackout ends the run).

### 2.5 Verdict and tracker split

Verdict: SOLO. Playable on one phone offline in every Gen 1 to 5 game.

Tracker split [A]:

- Check by itself: faints (live), blackout event, first wild battle per map and how it
  ended (caught, fled, knocked out, player ran), species-caught log, shiny (Gen 2 and
  later), nickname set, a dead Pokemon whose HP goes back above 0 (revive detected),
  Pokemon Center uses (map plus heal), box count (Notepad).
- Check with a per-game table: map to area grouping, encounter source (grass, water,
  Headbutt and so on), which new Pokemon are gifts or statics, evolution families.
- Only ask: area edge cases, whether a gift or static counts, whether running away counts,
  house clauses (dupes scope, shiny use), forgiven deaths.
- Enforce rather than remind, because the app owns the emulator: save states, rewind, soft
  reset (except crash recovery), cheats. Bulbapedia lists "No Resets" and "No Cheating" as
  near-universal and Fanlore lists "No Save States".

## 3. Hardcore Nuzlocke (pChal)

Where it comes from:

- Formalised and popularised by the streamer pChal (PokemonChallenges), in a 2017 video
  titled "BEST Nuzlocke Variant - Hardcore Nuzlocke Rules" (year and title per
  https://nuzlockewiki.com/hardcore-nuzlocke/, video at
  https://www.youtube.com/watch?v=6W4-XxdGlW8, not opened). Nuzlocke University says the
  ruleset was formalised by pChal while similar variants existed earlier
  (https://nuzlockeuniversity.ca/nuzlocke-variants/hardcore-nuzlocke-variant/).
  Wikipedia describes pChal as known for "hardcore" Nuzlockes with level caps and no items
  in battle (https://en.wikipedia.org/wiki/PChal).
- His reasoning, as quoted on nuzlockewiki: items and grinding levels are the two broken
  mechanics that defeat almost any self-restriction.
- pChal's own summary of his rules (TheGamer interview): team level capped at the next Gym
  Leader's highest Pokemon, no items in battle, first encounter per route
  (https://www.thegamer.com/pokemon-challenges-emerald-kaizo-nuzlocke-interview/).
- Bulbapedia's generic definition: any Nuzlocke that restricts item use and over-levelling.
  Some lists call the same idea "Hardlocke" (excerpt only).
- JoCat's ruleset is an independent creator variant with the same two pillars (no items in
  battle, a level cap); it does not list Set mode.

Core rules (Nuzlocke University unless noted):

1. No bag items in battle. Held items are allowed; Poke Balls are exempt.
2. Level cap: no Pokemon above the level of the highest-level Pokemon on the next boss's
   team. Boss means Gym Leaders (also Kahunas, Trial Captains); on entering the Pokemon
   League the cap is the final Elite Four member's highest level, and once inside the
   final stretch it may be passed.
3. Any Pokemon over the cap must be boxed and cannot be used until it is under the cap.
   Timing: you must ENTER the battle with nobody over the cap; passing it during the
   battle is allowed. Fanlore calls the trick "edging" (coined by pChal): fill each
   Pokemon to just under the cap before the Gym, then gain one level over it inside the
   battle.
4. Set battle style: Nuzlocke University says it should be used; Smogon and nuzlockewiki
   list it as one of three Hardcore rules; Bulbapedia's definition of Hardcore omits it.
   Treat it as a toggle that the Hardcore preset turns on.

Where sources differ or are silent:

- Who counts as a boss. Bulbapedia's level-cap clause names the next Gym Leader, Elite Four
  member or Champion, each by its "ace" (highest level). Nuzlocke University's per-game
  tables list Gym Leaders and League members, plus post-game battles where relevant, and
  say hack versions may add team leaders and other bosses
  (https://nuzlockeuniversity.ca/2022/01/18/hardcore-nuzlocke-level-caps-by-generation/).
  Rivals and evil-team bosses are not addressed for vanilla games; hacks add them (section
  11).
- What to do with an over-cap Pokemon: box until legal (Nuzlocke University), or "left in
  storage until eligible, or released" (Bulbapedia).
- Caps are not monotonic: in Gold/Silver, Gym 7 has a lower cap (Lv 31) than Gym 6 (Lv 35)
  (Nuzlocke University level-cap page). Do not assume the cap only rises.
- Item scope: Bulbapedia lists three separate options (No Items, No Heal Items, No Held
  Items); pChal's set means no bag items but held items are fine.
- Add-ons: Rare Candy clause (unlimited candies so the cap, not grinding, is the limit),
  Equal Parties, Center limits, plus every standard clause (dupes, shiny).

Run ends when: as Standard. Breaking the cap or the item rule does not end the run by
itself; the consequence is the player's choice (box or release). Wikipedia's Kaizo article
(a section its own banner marks as unsourced) describes pChal's May 2022 win on Emerald
Kaizo under Hardcore rules as: permanently store every fainted Pokemon, catch only the
first encounter in each area, keep levels under a self-imposed cap, and restart the run
after losing a battle (https://en.wikipedia.org/wiki/Kaizo).

Verdict: SOLO.

Tracker split [A]: check faints, battle style, items used in battle (compare the bag before
and after each battle, ignoring Poke Balls; held berries are not in the bag), party levels
against the cap at the start of a boss battle and on entering the League, plus the
Standard checks. Needs a table: the next boss and that boss's highest level. Best built
from the loaded ROM's trainer data so randomizers and hacks are handled. Ask: whether
rivals and evil-team leaders count, the over-cap consequence, Rare Candy use.

## 4. Randomizer Nuzlocke

Where it comes from: Bulbapedia lists it as a variant that uses randomizer mods for more
variety, with the warnings that meeting a legendary early collapses the difficulty and that
catch rates may need raising so legendaries can be caught with weak balls
(https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge). The usual tool is the
Universal Pokemon Randomizer family, which supports every core game from Gen 1 to 7 except
Let's Go (https://upr-fvx.github.io/universal-pokemon-randomizer-fvx/about.html); the ZX
fork has a "Set Minimum Catch Rate" option with five levels, the top one guaranteeing any
wild Pokemon is caught
(https://github.com/Ajarmar/universal-pokemon-randomizer-zx/wiki/Wild-Pokemon).

Core rules: the standard rules, applied to the randomized game. A 2026 guide says to decide
in advance whether duplicates, gifts, statics and shinies are exceptions, to prefer
similar-strength trainer teams, and warns that rerolling the seed at will and changing
rules mid-run weaken the challenge
(https://pokemonroulette.blog/blog/pokemon-nuzlocke-randomizer-rules/, low authority).

Common optional clauses: legendary bans or banned species lists; minimum catch rate or
Master Ball Clause; similar-strength trainers; random vs limited starters; Soul Link
combined with randomizer (used by Jaiden Animations and Alpharad, section 7).

Run ends when: as Standard. Softlock or "unwinnable seed" policy is not covered by the
sources; ask. [A]

Verdict: SOLO. The user supplies a randomized ROM, as KaizoCore already handles for
IronMON.

Tracker split [A]: species and type tables are unaffected. Area logic is unaffected (it
keys on map and species ID). Gift and static slots must be identified by event or slot,
not by species, because their species are randomized. Level caps must be read from the
randomized ROM, not from a shipped table, because trainer teams and levels can change.
Ask: randomized statics and gifts, legendary bans.

## 5. Monotype Nuzlocke (Monolocke)

Where it comes from: listed by Bulbapedia ("Monotype/Monocolor Challenge"), Smogon
("Monolocke") and Nuzlocke University ("Monolocke")
(https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge,
https://www.smogon.com/articles/introduction-nuzlockes,
https://nuzlockeuniversity.ca/nuzlocke-variants/). No single creator is credited.

Core rules and options:

- Only Pokemon of one type (or one colour) are caught and used. Bulbapedia: instead of the
  first Pokemon in an area, catch the first one that fits, or will evolve into something
  that fits; a Pokemon that would lose the type by evolving may not evolve.
- Smogon: every team member must share at least one type (so a dual-type counts if either
  type matches).
- Token clause ("Dusty Clause"): for each area with no valid encounter, collect a token to
  spend on an extra valid catch later (Bulbapedia, named for user "Dustox" of the old
  forum).
- Related but obscure: "Uniquelocke" (no two party members share a type), in Nuzlocke
  University's list.

Run ends when: as Standard.

Verdict: SOLO.

Tracker split [A]: check party types against the chosen type (from a species-to-type table),
whether an evolution would drop the type, the "first matching encounter" (the tracker must
apply the type filter before deciding what uses the area), and tokens. Ask: whether
dual-types count, whether tokens are on.

## 6. Wedlocke

Where it comes from: created and named by the YouTuber Marriland. The primary ruleset is
the Smogon Forums thread "Marriland's Wedlocke Challenge", posted 2013-01-04 by user
Espeon65 (https://www.smogon.com/forums/threads/marrilands-wedlocke-challenge.3476975/).
Credit to Marriland: Smogon's 2023 article (https://www.smogon.com/articles/introduction-nuzlockes),
Bulbapedia's user draft (https://bulbapedia.bulbagarden.net/wiki/User:MetalMetroid997/Wedlocke_Challenge),
and Nuzlocke University (https://nuzlockeuniversity.ca/nuzlocke-variants/wedlocke-variant/).

Core rules (Marriland's first post) [S]:

1. A fainted Pokemon is released or boxed for good.
2. First eligible Pokemon per area, with gender exceptions: genderless Pokemon can never be
   caught; if you hold an odd number of one gender, you ignore encounters of that gender
   until it balances. Dupes clause recommended.
3. All Pokemon must be nicknamed.
4. Pairs: one male and one female; the pair lasts until one dies or is released. Each
   Pokemon fights only alongside its partner; you may switch only to its partner. If one
   dies, its partner must avenge it or die trying; switching to anyone else is forbidden.
   A widowed Pokemon may take a new unpaired partner (from the PC or a wild catch).
5. PC deposit ban: you may not deposit Pokemon in the PC, except by releasing them or when
   they die. HM slave exception: a pair may be parked for one or two HM carriers that do
   nothing but use field moves.

Common optional clauses and disagreements:

- Same-gender pairs ("Gay/Lesbian Wedlocke"): Marriland's own optional variant; Bulbapedia
  lists mixed and single-gender options.
- Nuzlocke University adds optional "Romeo and Juliet" (partner may only use status moves
  until it also faints) and "Double Date" (after both members of a couple have acted, you
  may switch to another couple and not back), credited to a Reddit user.
- Pairing basis is a genuine split: gender (Marriland, Smogon, Nuzlocke University,
  Fanlore) vs order of capture (Bulbapedia's Nuzlocke article: each two consecutive catches
  are a pair, widows may remarry the next catch or be perma-boxed). Smogon lists "are
  genderless Pokemon allowed" as a disputed point.
- Later posts in Marriland's thread: Exp. Share may go to any Pokemon regardless of
  partner; the gender requirement exists to limit which PC Pokemon can re-pair.

Run ends when: as Standard; Wedlocke adds no separate end condition. The catching rule can
stall you (an unbalanced party skips encounters, genderless never catchable).

Verdict: SOLO in Gen 2 to 5. In Gen 1 the gender-pair form is IMPOSSIBLE (Gen 1 has no
gender except Nidoran, section 1); only the catch-order pairing works there.

Tracker split [A]: check gender (Gen 2 from the Attack DV and species ratio; Gen 3 to 5
from the personality value), genderless flag, the pair table, that switches in battle go
only to the partner (read the active battler), and the PC deposit ban (box scan). Ask:
same-gender or catch-order pairing, widow re-pairing, HM slave exception.

## 7. Soul Link

Where it comes from:

- No single creator is documented in any source I read. The earliest threads I can point
  to are on the Nuzlocke Forums with very low thread numbers (an HGSS Soul Link, thread 702;
  a randomized Diamond/Pearl Soul Link, thread 807; dates not retrievable, excerpt only):
  https://nuzlockeforums.com/forum/threads/the-ties-that-bind-a-hgss-soul-link-nuzlocke.702/
  and https://nuzlockeforums.com/forum/threads/inescapable-a-d-p-randomized-soul-link-run.807/.
- It reached a mass audience through Jaiden Animations and Alpharad's two-player run in
  December 2021 (Nuzlocke University:
  https://nuzlockeuniversity.ca/nuzlocke-variants/soul-link-nuzlocke-rules/).
- Bulbapedia's summary is in https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge.
- Also spelled "Soullocke", "SoulLock" or "SoulLink" (tracker sites such as
  https://soullinks.app/en/soullink-rules).

Core rules [S] (Nuzlocke University, Bulbapedia, TheGamer
https://www.thegamer.com/pokemon-soul-link-nuzlocke-rules-variants-explained/,
soullinks.app https://soullinks.app/en/soullink-rules,
https://soullocke.vercel.app/rules):

1. Two players run the same game, or paired versions of one generation (X and Y, Red and
   Blue), at the same time, each under the standard rules.
2. The encounter of each area is linked between players. If one player fails to catch
   theirs, the other must also box or release theirs for that area.
3. Caught Pokemon from the same area form a link. If one faints, its partner on the other
   team is dead too and must be boxed or released.
4. Linked Pokemon stay together: if one is in the party the other must be; if one is boxed
   the other is boxed.

Common optional clauses and disagreements:

- Type clause: no primary type may appear on both teams at once; if both players get the
  same type in one encounter, neither may use it (Bulbapedia, Nuzlocke University). One
  tracker author recommends against it (https://soullocke.vercel.app/rules).
- Dupes clause, shiny clause (extra catch or replaces the encounter, varies by group),
  Hardcore rules, randomizer, team size limits (soullinks FAQ and TheGamer).
- Blackout: end the run for both, or continue with boxed pairs. A Nuzlocke Forums veteran
  says to play without whiteout (excerpt only).
- Edge cases every group must settle before starting (soullinks FAQ,
  https://soullinks.app/en/soullink-faq): a partner dying mid-battle (one workable ruling:
  finish the battle, then remove the pair), gifts, eggs and statics (they either form
  their own pair or use a set location; both players use the same conditions), a player
  who misses an encounter (both forfeit, or only the failing player).
- Solo variant (TheGamer): every two Pokemon you catch are linked, with the same faint and
  PC rules, in one save. TheGamer says solo lacks the co-op chaos. No source I read
  describes one person running two saves.

Run ends when: not stated by Nuzlocke University or TheGamer. soullocke.vercel.app treats
losing all available Pokemon as the end. Because deaths propagate, one wipe usually ends both
runs. [A: inference]

Verdict: needs a SECOND PLAYER (second phone). Single-save "solo Soul Link" (pairs by
catch order) is SOLO.

What must be shared between two phones [A, consistent with what existing trackers keep
in sync: per-route pairs, teams and deaths (https://github.com/jynnie/soullocke), a shared
link that both edit (soullinks.app, soullocke.vercel.app)]:

- Per area: the area key, each side's outcome (caught, failed, skipped as a dupe), species,
  nickname, gender and types.
- Per pair: linked or broken, alive or dead, party or box (the two must match), death
  events with time.
- Settings that must match on both phones: wipe rule, type clause, dupes scope, gift and
  static handling.
- Optional: badge progress, since groups agree to take encounters only when both are at
  the place.
- Transport: one Play Store app advertises phone-to-phone Soul Link by QR code, with a paid
  online sync (https://play.google.com/store/apps/details?id=es.mirrorapps.nuzlockevault,
  excerpt only). An append-only event log keyed by (run, area, side) merges without
  needing both phones online at once. [A]

## 8. Egglocke

Where it comes from: listed by Bulbapedia, Smogon, Nuzlocke University and Fanlore. No
creator is credited in any source I opened
(https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge,
https://nuzlockeuniversity.ca/nuzlocke-variants/egglocke-variant/).

Core rules and the two flavours:

- (a) Catch one Pokemon per area as normal, but do not use it. Replace it with a randomly
  generated egg and use what hatches. Nuzlocke University and Smogon: the egg is levelled
  with Rare Candies to the level of the Pokemon it replaces. Bulbapedia: usually by trade
  or cheat device, with the eggs chosen by a third party such as friends or an audience.
- (b) Fanlore: every Pokemon used comes from an egg, the starter is not used, and a
  fainted Pokemon is replaced with a new egg.
- Where eggs come from: third-party software, friends, viewers, forum users. A PKHeX
  plugin can insert up to 100 generated eggs into a save (Nuzlocke Forums "Egglocke Egg
  Generator" threads, excerpt only:
  https://nuzlockeforums.com/forum/threads/egglocke-egg-generator.20158/).

Run ends when: as Standard.

Verdict: Gen 1 IMPOSSIBLE (no eggs; eggs arrive in Gen 2, section 1). Gen 2 to 5: SOLO
only if the app itself writes eggs into the game (a save or memory edit, which is not a
game feature); otherwise it needs a SECOND PERSON to supply eggs, and a way to load them.
What must be shared in that case: the list of egg species, hidden from the player until
hatching.

Tracker split [A]: check faints, the catch outcome, the level of the replaced catch (for
levelling the egg), and the hatched species. Ask: where the eggs came from, whether the
starter is used.

## 9. Wonderlocke and the trade variants

Wonderlocke:

- Where from: Bulbapedia, Smogon, Nuzlocke University, Fanlore
  (https://nuzlockeuniversity.ca/nuzlocke-variants/wonderlocke-variant/). No creator
  credited.
- Rules: every Pokemon caught is immediately Wonder Traded and the Pokemon received is used
  (Bulbapedia). Typical add-ons: a level restriction, so a received Pokemon too many levels
  above the original is traded again (Bulbapedia); re-trade a duplicate until you get a
  new one (Nuzlocke University); keep nicknames (Smogon); Fanlore: a Pokemon that faints
  is traded back out.
- Ends when: as Standard.
- Verdict: IMPOSSIBLE in Gen 1 to 5. Wonder Trade exists only in Gen 6, Gen 7 and
  Brilliant Diamond/Shining Pearl; the closest Gen 4 and 5 feature, the GTS, needs an
  online service that Nintendo shut down on 2014-05-20 (section 1). A similar effect could
  only be faked by the app choosing a random replacement itself. [A]
- WonderWedlocke (Wedlocke plus Wonder Trade) is listed by Smogon; same verdict.

Other trade variants (obscure, and the sources contradict each other on which name means
which):

- Tradelocke, created by YinDragon, published 2015-01-30 by NuzlockeFamily: two or more
  players; after every two badges each picks three Pokemon to battle; the winner takes one
  of the loser's three; a traded-in Pokemon cannot leave your team unless it is traded back
  or dies (https://www.deviantart.com/nuzlockefamily/journal/Tradelocke-510579160).
  A listicle assigns a party swap after each Gym to Tradelocke and the battle-and-take
  rule to "Swaplocke", the reverse of the creator's page
  (https://www.gamepressunited.com/pokemon-blog/pokemon-nuzlockes/).
- Verdict for both: needs a SECOND PLAYER and a working trade or link between two saves. What
  must be shared: the exchanged Pokemon (species, level, moves, item) and who owns each.
  On one phone offline with no link partner these are not playable.

## 10. Genlocke, and Hatelocke

Where it comes from: a multi-game Nuzlocke, "GenerationLocke", with a PokeCommunity thread
of that name ("The GenerationLocke (genlocke) Challenge",
https://www.pokecommunity.com/threads/the-generationlocke-genlocke-challenge.340231/, not
readable) and early Nuzlocke Forums threads (for example "Pokemon GenLocke Season 1
(FireRed)", https://nuzlockeforums.com/forum/threads/pokemon-genlocke-season-1-firered.408/,
excerpt only). PokeBase quotes rules that read like that original
(https://pokemondb.net/pokebase/403191/what-is-a-genlocke-nuzlocke; I could not confirm it
is a verbatim copy); Nuzlocke University has a matching page
(https://nuzlockeuniversity.ca/nuzlocke-variants/generationlocke-variant/).

Core rules [S]:

- Play one game per region, in an order of your choosing, each as a standard Nuzlocke.
- At the end of each game the surviving Hall of Fame Pokemon are passed on: their baby
  versions start the next game, either by breeding eggs and sending them across, or
  with a save editor such as PKHeX. If neither is possible, the original text allows
  cheats to get roughly equivalent Pokemon in the next game (its example is editing a
  wild encounter into the Pokemon needed).
- Options: bonuses for long-term survivors, such as egg moves or chosen natures when
  breeding (Nuzlocke University).

Run ends when: neither source states it. If a leg is lost there is no Hall of Fame team to
pass on, so the natural reading is that the chain ends (or the leg is replayed); pick one
as a setting. [A: inference]

Hatelocke (a related one-run-across-the-series form):

- TheGamer, 2023-11-19, Ben Sledge: one run across the whole series; a species you caught
  in an earlier game may not be used in later games
  (https://www.thegamer.com/pokemon-hatelocke-nuzlocke-challenge/; creator not credited;
  also summarised by Wikipedia, https://en.wikipedia.org/wiki/Nuzlocke). One search
  excerpt words the ban as "taken to the Hall of Fame" instead of "caught"; the article
  itself says caught.

Verdicts:

- Hatelocke: SOLO. It needs only a persistent species-ban list across saves.
- Genlocke: SOLO only if the app (or the player) can put the heir Pokemon into the next
  save. Stock routes do not work on one phone: Gen 2 to Gen 3 has no transfer at all, Gen 3
  to 4 needs a DS with a GBA slot, Gen 4 to 5 needs two DS systems (section 1), and breeding
  needs Gen 2 or later. Note also that a transfer rewrites the "met at" data (Bulbapedia,
  Poke Transfer and Pal Park pages), which affects first-encounter checks in the new game.
  [A]

Tracker split [A]: record the survivors of each leg at the Champion battle; in Hatelocke keep
the banned-species list. Ask: order of games, egg or edit, what happens when a leg fails.

## 11. Kaizo Nuzlockes on hard ROM hacks, and Deathless

Boundary: "Kaizo" alone often means IronMON's Kaizo (excluded here). In this section it
means a Nuzlocke, usually with Hardcore rules, played on a hard hack.

Where it comes from:

- The Kaizo hack series is by SinisterHoodedFigure (SHF): Blue Kaizo (Gen 1, 2014),
  Crystal Kaizo (Gen 2), Emerald Kaizo (Gen 3) and Platinum Kaizo (Gen 4, v1.0 on
  2026-03-15, called the fourth entry by PokeHackDB).
- Wikipedia's Kaizo article (its Pokemon section is flagged as unsourced) says the series
  drew streamer attention in 2019 to 2020. The hacks give trainers complicated teams and
  movesets, push the player into fighting most trainers, limit resources, and stop
  healing items in battle. Radical Red (FireRed) and Run & Bun (Emerald) are associated
  with them by difficulty (https://en.wikipedia.org/wiki/Kaizo).
- The phrase "A Kaizo Nuzlocke" appears in Nuzlocke Forums titles, for example
  https://nuzlockeforums.com/forum/threads/ive-been-kaizoed-a-kaizo-nuzlocke-2-crystal-kaizo.1507/
  (excerpt only), and "Blue Kaizo Hardcore Nuzlocke"
  (https://nuzlockeforums.com/forum/threads/soul-drdin-blue-kaizo-hardcore-nuzlocke-writtenlocke-artlocke.20487/,
  excerpt only).

| Hack | Base and author | Nuzlocke-relevant built-ins | Source |
|---|---|---|---|
| Blue Kaizo | Gen 1 Blue; SHF, released 2014 | None noted; tagged "Nuzlocke friendly". Gen 1 has no gender, shiny, held items or met data. | https://pokehackdb.com/hacks/pokemon-blue-kaizo |
| Crystal Kaizo | Gen 2 Crystal; sequel to Blue Kaizo, attributed to SHF in excerpts | Described as designed around the Nuzlocke community (excerpt only). | https://www.pokecommunity.com/threads/pok%C3%A9mon-crystal-kaizo-the-official-sequel-to-blue-kaizo.334713/ |
| Emerald Kaizo | Gen 3 Emerald; SHF | No ruleset of its own: pChal applied his Hardcore rules and needed 151 attempts (TheGamer). A third-party guide (excerpt only) says only held items can be used in battle (matching Wikipedia's note that these hacks stop healing items in battle), says bosses have enforced level caps (not confirmed elsewhere), and lists 37 boss fights including rivals and evil teams. | https://www.thegamer.com/pokemon-challenges-emerald-kaizo-nuzlocke-interview/ ; https://nuzlocketracker.org/guides/emerald-kaizo |
| Platinum Kaizo | Gen 4 Platinum; SHF, v1.0 2026-03-15 | Cap list from the hack's documentation, defined as the highest-level Pokemon each boss brings (Roark 16, Gardenia 28, Fantina 38, Maylene 47, Byron 65, Candice 74, Volkner 84, Champion Cynthia 100) and including the rival (Barry) and Team Galactic commander fights. One article says Gym Leaders refuse a battle when a Pokemon is over the threshold, that an "Edge System" at gym signposts parks a Pokemon one exp point below a level-up, and that the hack's own Hall of Fame wants strict Nuzlocke rules plus video proof. PokeHackDB lists no built-in Nuzlocke features, so the sources conflict on enforcement. | https://romhackguides.com/hacks/platinum-kaizo/level-caps/ ; https://retrododo.com/pokemon-platinum-kaizo-is-here-to-both-challenge-punish-players/ ; https://pokehackdb.com/hacks/pokemon-platinum-kaizo |
| Radical Red | FireRed-based | Hardcore Mode: Set forced, bag locked against Gym Leaders and select bosses, soft level cap by next boss; 46 boss battles including rivals and evil teams (third-party guide). | https://nuzlocketracker.org/guides/radical-red-hardcore |
| Unbound | FireRed-based (not stated on the pages I opened) | Four difficulty modes. Level cap by badge count (15 to 66 vanilla, 20 to 75 harder), removed after the Champion. A Capped Exp. Share option. At the cap a Pokemon still gets the exp needed to reach it; the wiki notes rare single-point gains can push a Pokemon over, which temporarily lifts the cap for the rest of the party. | https://pokemonunbound.miraheze.org/wiki/Level_Cap |
| Run & Bun | Emerald-based hack made as a challenge Nuzlocke (pChal made a feature film of it) | No rules document found. | https://fanlore.org/wiki/Nuzlocke |
| Drayano's hacks (Renegade Platinum, Sacred Gold and Storm Silver, Blaze Black/Volt White, Blaze Black 2/Volt White 2) | Gen 4 and 5; Drayano | "Enhanced difficulty" tier per Nuzlocke University; the game names come from a search excerpt; no built-in rules found. | https://nuzlockeuniversity.ca/2025/06/21/9-of-the-best-pokemon-rom-hacks-to-nuzlocke-for-all-skill-levels-including-the-hardest-pokemon-game-ever-made/ |

Core rules: the Hardcore set from section 3, applied to the hack. Fanlore notes that a level
cap is "sometimes integrated into a romhack", which is the case for several hacks above.

Differences that matter for the tracker:

- Some hacks enforce a rule in game, or are said to (Radical Red: Set, bag lock, soft cap;
  Unbound: a badge-based cap; Emerald Kaizo: bag items blocked in battle; Platinum Kaizo:
  one article says Gym Leaders refuse over-cap battles). Unbound's cap is by badge count
  and lifts after the Champion, which is not the "next boss's ace" definition. Let the
  player mark a rule as "enforced by the game" and switch the tracker's own check off (or
  show the hack's cap). [A]
- Cap lists in hacks come from the hack author's boss sheet and include rivals and team
  bosses (Platinum Kaizo's list has Barry and Team Galactic commanders), so "who counts as
  a boss" is unavoidable there and the boss table must be per-hack data. [A]
- Runs are restart-heavy: 151 attempts for pChal on Emerald Kaizo (Wikipedia, TheGamer), so
  an attempt counter and a fast new-run flow are core features. [A]
- Memory layouts differ from the base games (expansions), so every automatic check needs
  per-hack validation. [A]

Run ends when: as Standard (Wikipedia's Kaizo article says restart after losing a battle).

Verdict: SOLO, provided the hack's memory layout is supported.

### Deathless

- Rule: a Nuzlocke (usually Hardcore) that restarts the moment ANY Pokemon faints
  (Nuzlocke University's optional rules, https://nuzlockeuniversity.ca/optional-rules/).
- Following: Wikipedia's Kaizo article records streamer Prouty's "Hardcore Nuzlocke
  Deathless" win on Emerald Kaizo in February 2023, with no Pokemon fainting; a GamesRadar
  article reportedly counts 402 attempts (search excerpt only,
  https://www.gamesradar.com/pokemon-streamer-dubbed-greatest-nuzlocker-in-the-world-after-beating-the-series-most-notorious-game-without-losing-a-single-pokemon/).
- Run ends when: at the first faint.
- Verdict: SOLO. Tracker [A]: fully automatic (a faint event ends the run). The only ruling
  is whether faints in exempt battles count (Slow Start, scripted battles).

## 12. Other names the request listed, and other named variants

- Sisterlocke: no definition found in Bulbapedia, Smogon, Nuzlocke University, Fanlore,
  Wikipedia, TheGamer, or roughly ten searches. Cannot give rules. Do not build to the name;
  if someone has a source (a video or forum thread), obtain it first.
- Dualocke, Duolocke, Duallocke: a real but ill-defined term. A Nuzlocke glossary excerpt
  (the old Nuzlocke Forum thread "Nuzlocke Terminology" was the top hit but I could not open
  it: https://www.tapatalk.com/groups/nuzlocke_forum/nuzlocke-terminology-t3504.html) says it
  is used in several ways: most often two Nuzlocke games run at once and told as one story
  (sometimes two players, sometimes with the runs battling each other), and sometimes a
  Sololocke with two Pokemon or a two-type Monotype. The Nuzlocke Forums tag
  https://nuzlockeforums.com/forum/tags/duallocke/ has threads such as a "dualocke
  egglocke" and a Breloom/Dragonite "dualocke". Verdict: undefined. If it means two saves
  played by one person it behaves like a solo Soul Link (needs two saves in the app); if it
  means two players it needs a second player.
- Cagelocke: created by the YouTuber aDrive; first match aDrive against ShadyPenguinn, 23
  episodes (https://emberjaw.wordpress.com/en/challenges/nuzlockes/cagelocke/). Rules:
  standard catch and release, mandatory nicknames, no TMs or held items unless earned, no
  swapping with the box. After each Gym the competitors fight a cage match (online or in
  Pokemon Showdown); the loser releases the fainted Pokemon; winners pick one privilege
  (TM use, held item use, or one free revival) for the winning Pokemon; a final six-vs-six
  match happens if both beat the League. The page does not state a wipe rule. Verdict: SECOND
  PLAYER, and the battle happens outside the game. Shared: team rosters before each match,
  results, prizes.
- Friendlocke: SaltyDKDan, 2021 (Fanlore says January 2021; a fan wiki says April 2021).
  Standard Nuzlocke where each Pokemon is voiced and controlled by a different human friend;
  unlike most Nuzlockes, playing continues from the box after a full wipe and the run
  resets only when every friend's Pokemon is dead
  (https://sdkdfl.fandom.com/wiki/Pok%C3%A9mon_Friendlocke, excerpt only;
  https://twitter.com/saltydkdan/status/1379860428457701381). Verdict: needs other people.
  Shared: Pokemon-to-person assignment and alive status.
- Battlelocke: multiplayer competitive Nuzlocke from a group's 4-way Platinum and HGSS run
  (https://nuzlockeforums.com/forum/threads/new-ruleset-multiplayer-competitive-nuzlocke-battlelocke.19659/,
  excerpt only). Obscure. Needs other players.
- Taglocke (friends take turns on one save, one route or section each): obscure, listed
  by Nuzlocke University (https://nuzlockeuniversity.ca/nuzlocke-variants/).
- Others Bulbapedia names, all obscure: Alphabetlocke (party must be the first six species
  alphabetically), Lorelocke (per-species mythology rules; large ruleset on the Nuzlocke
  Forums), Ballocke (one catch per ball type), Giftlocke (only gifts). Nuzlocke University
  lists about 20 more (Trashlocke, Sleeplocke, Draftlocke and so on); none has a following
  worth a preset.
- Where "Hardlocke" appears it usually means the Hardcore idea (excerpt only).

## 13. Design notes for the tracker (all [A])

1. Profiles: a rule has a state (off, remind, check, enforce). Presets: Standard, Hardcore
   (pChal), Kaizo-hack, Randomizer flag, Monotype filter, Wedlocke pairs, Soul Link (two
   phones), Soul Link (one save), Genlocke and Hatelocke lists. Every contested rule in
   sections 2 to 11 becomes one toggle with the sources' options as its values.
2. Faints must be logged live. Also Gen 1 and 2 Pokemon carry no personality value, so a
   dead Pokemon has to be identified by a fingerprint (species, DVs, original trainer ID,
   nickname) when it moves between party and box.
3. Tables the app must ship or derive: map to area, encounter source per game, gift and
   static slots, boss list with highest levels (derive from the ROM's trainer data;
   Nuzlocke University has community tables for vanilla; https://github.com/domtronn/nuzlocke.app
   is BSD-3 with routes and league data for many games but says coverage is incomplete),
   evolution families, type table, Gen 2 gender ratios.
4. Check moments: level cap at the start of a boss battle and on entering the League (not
   continuously, because passing the cap inside the battle is allowed); encounter outcome
   at battle end; bag comparison at battle end; battle style at battle start.
5. A logged manual override, for forgiven deaths, second-chance tokens, checkpoints and
   memory misreads. Several sourced clauses need it (Second Chance, Checkpoints, Surplus
   Hax, Limited Revives).
6. Enforce, do not remind: save states, rewind, soft reset (allow crash recovery, as JoCat
   does), cheats. The Rare Candy and Master Ball clauses are optional assists.
7. Two-phone sync: append-only events keyed by (run, area, side), shared by QR or file, so
   merging works offline.
8. An exportable run log has a use beyond the phone: the Platinum Kaizo Hall of Fame is
   reported to require strict Nuzlocke rules plus video proof (retrododo, section 11), so
   a tamper-evident log from an auto-checking tracker would be something that community
   asks for today.

## 14. Summary table

Legend for the "check by itself" column: F = party faints, E = first encounter on the
current map (needs a map-to-area table), L = levels against a cap (needs a next-boss table),
I = items used in battle (bag comparison), S = battle style setting, D = species already
caught (own log plus evolution families). "n/a" means the variant does not use that check.

| Variant | Tracker checks by itself (from game memory) | Tracker can only ask the player | Solo verdict (one phone, offline, Gen 1 to 5) |
|---|---|---|---|
| Standard / original, with dupes, shiny, gift, starter clauses | F, E, D; also blackout, shiny (Gen 2+), nickname set, revive of a dead Pokemon, Center uses. L, I, S n/a (S optional) | Area edge cases (caves, water vs grass, Safari, special sources); gift and static handling; whether running counts; dupes scope; forgiven deaths | SOLO |
| Hardcore (pChal) | F, E, D, L, I, S | Which bosses count (rivals, team leaders); over-cap consequence; Rare Candy use | SOLO |
| Kaizo / hard-hack Nuzlocke (Blue, Crystal, Emerald and Platinum Kaizo; Radical Red; Unbound; Run & Bun; Drayano hacks) | Same as Hardcore, read from the hack's layout; skip L, I, S where the hack enforces them; attempt counter | Same as Hardcore, plus "the game enforces this" flags; boss list per hack (rivals and team bosses included) | SOLO (hack layout must be supported) |
| Deathless (Hardcore Deathless) | F only: any faint ends the run; other checks as the base profile | Whether faints in exempt or scripted battles count | SOLO |
| Randomizer Nuzlocke | Same as the base profile; L read from the randomized ROM | Randomized statics and gifts; legendary bans; catch-rate policy; unwinnable-seed policy | SOLO (user supplies ROM) |
| Monotype (Monolocke) | F, E (after type filter), D; party types; evolution that would lose the type; tokens | Dual-type rule; token clause | SOLO |
| Wedlocke | F, E, D; gender (Gen 2+); pair table; switch only to partner; PC deposit ban; L, I, S optional | Same-gender or catch-order pairing; widow re-pairing; HM slave exception | SOLO in Gen 2 to 5. Gen 1: IMPOSSIBLE by gender (catch-order pairing only) |
| Soul Link (two players) | Own side only: F, E, D, party or box of own linked Pokemon; L, I, S if Hardcore is on | Partner's data (must be shared); wipe rule; type clause; gift, static and egg symmetry; mid-battle partner death timing | SECOND PLAYER or second phone. Shared: area outcomes, pair table, deaths, party or box status, matching settings. Single-save "solo Soul Link" is SOLO |
| Egglocke | F, E; catch level; hatched species | Egg source; starter use | Gen 1 IMPOSSIBLE (no eggs). Gen 2 to 5 SOLO only if the app injects eggs; else SECOND PERSON supplies them |
| Wonderlocke | n/a | n/a | IMPOSSIBLE in Gen 1 to 5 (Wonder Trade is Gen 6+; GTS needs a service shut down in 2014) |
| Tradelocke / Swaplocke | F, E, D per player | Trade rulings; who owns what | SECOND PLAYER plus link; shared: exchanged Pokemon and ownership |
| Genlocke | Per leg as Standard; survivors at the Champion battle | Order of games; egg or edit; leg-failure policy | SOLO only if the app or player can create heirs in the next save; no stock path on one phone |
| Hatelocke | Per game as Standard; banned-species list from catch logs | Ban basis (caught vs Hall of Fame) | SOLO |
| Cagelocke / Friendlocke / Battlelocke / Taglocke | F, E, D per player | Match results and prizes (Cagelocke); Pokemon-to-person assignment (Friendlocke) | SECOND PLAYER(S); battles or control happen outside the game |
| Dualocke / Duolocke | Depends on meaning | Which meaning | Undefined: two saves by one person is SOLO; two players needs a second player |
| Sisterlocke | Unknown | Unknown | No source found; cannot rate |

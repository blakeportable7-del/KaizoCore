# Missing walking sprites (research, 2026-10-03)

Blake (2026-10-02): "i want to find the missing sprites". The Walking Pals after Gen 3 (Play as your Pokemon's sheets
and the tracker's animated icons) come from PMD Sprite Collab, and on 2026-10-02 natdex-map.tsv left 111 of the Nat.
Dex's 872 ids with no sheet. This is what was found, what came in, and what each gap would take.

**Update 2026-10-04: DarkusShadow's overworld sprites fill 19 of the 46.** Blake: "i want iron boulder, find the missing
sprites", then, of DarkusShadow's sheets, "yes use them" and "use the ones i gave you if they fill in sprite collabs
gap". So Sprite Collab's sheet always wins, and his fills only a gap (section 9).

**Where it stood on 2026-10-03.**

- The converter ran against Sprite Collab's newest commit, d2ceb96254fb (2026-10-03). Nothing has been drawn since
  211e689353e1 (2026-09-30): every sheet and every credit came out the same, byte for byte. The tables now name the
  newer commit.
- A Mega or form with no walking sprite of its own now walks as its base species (Blake, 2026-10-02: "only if we don't
  have the correct sprites"). That covers 65 of the 111. The converter writes it into natdex-map.tsv with a note, so
  the first run after Sprite Collab draws one of them uses the real sheet, with no change to the app.
- 46 are still missing: the 44 species nobody has drawn, and Mega Falinks and White Squawkabilly, whose base species
  are two of the 44.
- Nothing else can ship today. Two copies of Sprite Collab on GitHub hold drawings of seven of the 44, but none was
  approved, their credits were written by someone other than the artist, and one of those copies mixes in ripped 3D
  renders. Every other source is another format, ripped, or has no licence.

**How this was found.**

- `python tools/trainer-data/convert_walking_pals_nat.py --update` (cache at C:/Users/bepor/walkingpals-cache, free),
  after an offline run at the old commit wrote the shipped files again byte for byte.
- Sprite Collab's tracker.json at d2ceb96254fb, read for every missing Pokemon.
- SpriteBot's source (github.com/PMDCollab/SpriteBot at 8a502bf), the bot that runs Sprite Collab's submissions.
- The GitHub API for Sprite Collab's commits since 2026-08-01, its branches and its 56 forks (13 compared file by file).
- Web searches and the pages of every other source named below. Nothing was downloaded or run, no sprite pack was
  fetched, and nothing was posted or signed up for.

---

## 1. What the player sees now

- In a Nat. Dex game, a Mega or form with no sheet of its own walks as its base species: Mega Venusaur walks as
  Venusaur, Ash-Greninja as Greninja, Ice Rider Calyrex as Calyrex. The tracker's animated icon does the same, and the
  Always use list now offers them.
- With one of the 46 in the lead, Play as your Pokemon keeps you the trainer, the Always use list leaves it out, and the
  tracker shows the still icon. The line under the switch already says a few later ones have no sprite yet.

## 2. The 46 still missing

From tracker.json at d2ceb96254fb. Every one has `sprite_complete` 0: no sprite in the collab at all.

- **Template asked for**: someone asked the collab's bot for a blank template for it (the date the bot made it). It
  only shows that someone looked.
- **Review thread**: when the collab's review thread for it opened. With nothing waiting, that usually means a
  submission was declined or withdrawn.
- **Waiting since**: a submission is in the review queue now.
- **Bounty**: Guild Points members pledged for it. Points from the collab's Discord, not money.

| Nat. Dex id | Pokemon | National | Template asked for | Review thread | Waiting since | Bounty |
|---|---|---|---|---|---|---|
| 539 | Simisear | 514 | 2025-08-26 | 2026-01-06 | | |
| 541 | Simipour | 516 | 2025-11-09 | | | 111 |
| 545 | Tranquill | 520 | 2026-09-01 | 2024-09-30 | | |
| 547 | Blitzle | 522 | 2026-01-09 | 2024-09-27 | | |
| 548 | Zebstrika | 523 | 2026-02-22 | 2026-02-20 | | 10 |
| 563 | Throh | 538 | 2025-05-31 | | | |
| 583 | Crustle | 558 | 2025-11-20 | 2025-07-05 | | |
| 589 | Tirtouga | 564 | 2024-09-03 | 2025-07-01 | | |
| 590 | Carracosta | 565 | | | | |
| 616 | Amoonguss | 591 | 2025-02-01 | | | |
| 617 | Frillish | 592 | | 2025-07-01 | | |
| 641 | Shelmet | 616 | 2024-06-01 | 2024-10-02 | | |
| 651 | Bouffalant | 626 | 2026-02-22 | 2025-07-01 | | |
| 757 | Trumbeak | 732 | | | | |
| 760 | Gumshoos | 735 | 2024-04-20 | | | |
| 781 | Shiinotic | 756 | 2025-04-18 | | | |
| 790 | Oranguru | 765 | 2024-05-16 | | | |
| 862 | Rolycoly | 837 | | 2025-08-16 | 2026-08-28 | |
| 863 | Carkol | 838 | | 2026-09-23 | 2026-09-23 | |
| 864 | Coalossal | 839 | 2026-06-26 | | | |
| 891 | Mr. Rime | 866 | 2025-10-01 | | | |
| 895 | Falinks | 870 | 2025-05-26 | | | |
| 903 | Cufant | 878 | 2025-03-15 | | | 17 |
| 918 | Zarude | 893 | 2026-01-01 | | | |
| 956 | Squawkabilly | 931 | 2025-08-14 | 2025-08-23 | | |
| 967 | Maschiff | 942 | 2024-12-11 | | | |
| 968 | Mabosstiff | 943 | 2024-12-11 | 2024-10-15 | | |
| 969 | Shroodle | 944 | | | | |
| 972 | Brambleghast | 947 | 2026-01-25 | | | |
| 974 | Toedscruel | 949 | | | | |
| 975 | Klawf | 950 | 2025-03-04 | | | 200 |
| 979 | Rabsca | 954 | 2024-02-19 | | | |
| 981 | Espathra | 956 | 2025-12-08 | 2026-03-01 | | |
| 987 | Bombirdier | 962 | 2026-04-21 | 2026-04-21 | 2026-08-25 | |
| 998 | Flamigo | 973 | 2025-04-05 | 2024-03-04 | | |
| 1011 | Brute Bonnet | 986 | | | | |
| 1018 | Iron Jugulis | 993 | 2024-03-11 | | | |
| 1024 | Gimmighoul | 999 | 2025-12-08 | | | |
| 1026 | Wo-Chien | 1001 | 2026-01-05 | | | |
| 1027 | Chien-Pao | 1002 | 2025-10-23 | | | 20 |
| 1033 | Miraidon | 1008 | 2026-05-25 | 2025-01-07 | | |
| 1039 | Okidogi | 1014 | 2025-08-06 | 2025-06-11 | | |
| 1047 | Iron Boulder | 1022 | 2026-01-05 | | | |
| 1048 | Iron Crown | 1023 | 2026-07-07 | | | |
| 1237 | White Squawkabilly | 931 | | | | |
| 1263 | Mega Falinks | 870 | | | | |

The 65 that stand in are listed in natdex-map.tsv, each with its note ("SpriteCollab's Venusaur Mega slot is empty;
Venusaur's sheet stands in until its own is drawn"). No Mega or other missing form has a sheet in this format anywhere
that was found; Megas exist elsewhere only as overworld sprites (section 5).

## 3. How Sprite Collab takes a sprite

From SpriteBot's source at 8a502bf (github.com/PMDCollab/SpriteBot) and Sprite Collab's README at d2ceb96254fb.

- **Where.** In the SkyTemple Discord, the server the tracker's ids point to. The collab's site sends new spriters to
  its #spriting-help channel (sprites.pmdcollab.org, the About page).
- **How a sprite goes in.** The artist mentions the bot with `sprite <Pokemon>` and gets the current files, or a blank
  template when there are none. They draw the animations and AnimData.xml by the collab's guide and post the zip in
  #submissions. The bot checks it first: AnimData.xml and Idle present, one or eight facings, frames matching their
  timings, at most 15 colours. A sprite that passes is posted again for review, with a thread for comments.
- **Who approves.** The server's approvers: three of them for a sprite. On approval the bot
  writes the files, adds the artist to credits.txt, commits to GitHub and pays any bounty. A decline or the artist's
  own withdrawal takes it out of the queue.
- **The licence.** Every submission is CC BY-NC 4.0, the licence of everything else KaizoCore bundles from the collab.
  The README says that by submitting to the bot an artist lets others copy, redistribute and build on the work, non
  commercially, with credit. Older lines in credits.txt carry PMDCollab_1 or PMDCollab_2 (use with credit), and the
  Mystery Dungeon games' own sprites are marked Unspecified; KaizoCore's NOTICE already says so.
- **Requests and bounties.** There is no request command. Anyone can ask for a template, ask in the Discord, or pledge
  Guild Points with `spritebounty <Pokemon> <points>`. Guild Points are a points balance on the server, earned by
  contributing; nothing found shows they can be bought or cashed. For scale, Klawf's 200 is the second largest sprite
  bounty in the whole tracker.
- **Commissions.** The collab's About page says many of its artists take commissions, and that a Pokemon the collab
  lacks can be commissioned. That is paying for art (section 6).
- **How long review takes.** Nothing says. At d2ceb96254fb 18 sprites are waiting, the oldest since 2026-07-18, and the
  five new species approved since August took between 5 weeks and 6.5 months from their thread opening.

## 4. How fast new ones arrive

Sprite Collab's commits since 2026-08-01 (GitHub API): 656, all by the bot, but 532 of them are "Update credits.", a
housekeeping commit the bot makes every ten minutes the tracker changed.

- New species in nine weeks: 5 (Yungoos, Bramblin, Oricorio's Baile style, Glastrier, Barraskewda).
- New forms: Mega Tatsugiri, Mega Darkrai, Pa'u Oricorio, a second Mega Zygarde. Also 7 new shinies and 30 redraws.
- None of the 44. At this pace 44 species is more than a year even if artists drew nothing else, and nothing says
  they will pick these.
- No work waits on another branch: the only other branch, NewCredit, is a 2021 credit format test.

So a monthly `--update` (FUTURE-PROJECTS.md's plan) is right: most runs will bring nothing new for these, and a run
costs nothing.

## 5. Other sources checked

KaizoCore's rules for anything it bundles: the same format (PMD AnimData: Idle, Walk, Sleep and Faint, eight facings),
a licence that allows bundling with credit (CC BY-NC 4.0 or freer), drawn by fans and never ripped from a game, and
nothing paid.

| Source | What it is | Format | Licence | Artists | Fits? |
|---|---|---|---|---|---|
| lxsmnsyc/SpriteCollab (github.com/lxsmnsyc/SpriteCollab) | A fork repacked into its own "compact" format by its maintainer | Its own: frames and anchors kept, Faint and other cutscene animations dropped. Could be converted back | Its own credits.txt lines say CC BY-NC 4.0, written by the fork's author, not the artists or the bot (compact/EDITS.md) | Drawn: Tranquill, Shelmet, Frillish (Pokejavi.), Crustle (JFain), Simisear (a Discord user with no name), Sensu Oricorio (baronessfaron), an older Bouffalant (Pokejavi.). Rendered: Simipour, Blitzle, Zebstrika, Throh, Tirtouga, Carracosta, Amoonguss, Bouffalant, Stunfisk from the 3DS app Pokedex 3D Pro | No. The drawings are submissions the collab never approved, taken from the artists' zips (Frillish's then moved by the maintainer after review notes); the renders are ripped models; its shinies are recoloured from official art and a game rip. Style of the drawings matches, the renders do not |
| sylvainpolletvillard/SpriteCollab (Pokemon Auto Chess's fork) | A fork for a game that uses the collab's sprites | PMD AnimData | None of its own; Pokemon Auto Chess credits nobody for these two | Not named: Tranquill ("temporary sprites"), Okidogi ("waiting for approval on PMDCollab") | No: no artist to credit. Style matches |
| The other 13 forks with work of their own (cynware, HashtagMarky, JuanManuelRB, keldaanCommunity, PokeMerge, Willowy-lee, Espik, evinjaff, SourceGD, marius851000, jamesschoch, dforel, Zeleos753) | Forks of the collab | PMD AnimData | The collab's | The collab's | Nothing for the missing ones |
| A pull request "Add complete Falinks #0870 SpriteCollab pack" on meromoonmeri/guilde-treehouse-pmd | Opened by a bot account | Unknown | None; the repository is gone (404) | Unknown | No |
| PMD Origins (RogueEssence, github.com/PMDCollab/RawAsset) | A fan game built on the collab's sprites | PMD AnimData | The collab's | The collab's | Nothing the collab lacks |
| SkyTemple (wiki.skytemple.org) | The PMD hacking community that hosts the collab | Points to the collab | | | Nothing else |
| Pokemon Mystery Universe (pmuniverse.net, its DeviantArt gallery) | A fan game | A few GIFs | None stated | Its team | No: no licence, none of the missing |
| PokeMMO's Mystery Dungeon followers mod (forums.pokemmo.com) | A mod using the collab's sprites | PMD | The collab's | The collab's | Gens 1 to 4 only |
| remokon/gen-9-sprites (github.com) | Gen 9 stills scraped from pokemondb.net and serebii.net | Still pictures | None | Not named | No: ripped, no licence |
| DarkusShadow's Gen 9 overworld sprites (deviantart.com/darkusshadow) | Fan drawn walking sprites, all of Paldea, Okidogi too | HGSS style overworld: four facings, walk only | Free for fan projects with credit | DarkusShadow | No: another format, no Idle, Sleep or Faint, and it would look unlike the collab's next to them |
| Essentials Generation 9 Resource Pack (eeveeexpo.com/resources/1101) | Pokemon Essentials resources | Overworld and battle | Credit asked, no licence | Azria, DarkusShadow, EduarPokeN, Carmanekko, StarWolff, Caruban | No: another format, no licence |
| Following Pokemon EX (eeveeexpo.com/resources/516) | Follower sprites, Gens 1 to 8 | Overworld | A credits list, no licence | Many | No: another format, mixes HGSS rips with fan art |
| Mega and Gigantamax overworld pack (eeveeexpo.com/resources/1475) | Overworlds of Megas, Legends Z-A ones too | Overworld | Credit optional by the porter; each artist's own terms vary | Kidkatt, Larryturbo, Princess-Phoenix, SageDeoxys, lasse00, DarkusShadow, BluebirdDxD | No: another format |
| SageDeoxys's Gen 8 overworlds (deviantart.com/sagedeoxys) | Overworld sheets | RPG Maker | Free only with his permission and credit | SageDeoxys | No: another format |
| lasse00's Galar overworlds (deviantart.com/lasse00) | Overworld sheets | HGSS style | None stated | lasse00 | No |
| Smogon Sprite Project (smogon.com/forums) | Black and White style battle sprites | Front and back battle pictures | Free for non profit use with credit | Its spriters | No: battle sprites, not walking ones |
| itch.io | Searched for PMD format Pokemon packs | | | | None found |

The two forks are the closest anyone has come. Tranquill, Crustle, Shelmet, Frillish, Bouffalant and Simisear were
drawn by artists who already have approved work in the collab (Pokejavi. and JFain are in its credits), and the
collab's own rule makes any submission CC BY-NC 4.0. But none was approved, the copies on GitHub were repacked (and
one reworked) by someone else, the credit lines are that person's, and none has Faint. Shipping them would put unfinished work under the
artists' names without asking them.

## 6. What each gap would take

- **Wait for Sprite Collab (free, nothing to do).** Run `--update` before each release. Each new sheet comes in with
  its credits, and a Mega or form standing in for its base gives way to its own sheet on the same run. Three of the 44
  are in review now: Bombirdier (since 2026-08-25), Rolycoly (2026-08-28) and Carkol (2026-09-23). So is Sensu
  Oricorio (2026-09-15, by baronessfaron, who drew Yungoos and Oricorio's Baile style for the collab this summer), which
  stands in as Oricorio until then. Size S
  each time: the run, WalkingPalsTest and SpriteIsMeLogicTest, and a look at the new ones in the Always use list.
- **Ask the artists of the unapproved drawings (free).** Pokejavi. (Tranquill, Shelmet, Frillish, Bouffalant), JFain
  (Crustle) and the Simisear artist could be asked in the SkyTemple Discord whether they will take their sheets back
  through review. Once approved they arrive by the next `--update` like any other. Taking the fork's copies instead is
  not recommended (section 5).
- **Contribute there.** Anyone can draw a missing Pokemon and submit it through SpriteBot; it is reviewed by the
  collab's approvers and released as CC BY-NC 4.0 with the artist credited. A KaizoCore player who draws could be
  pointed there. Pledging Guild Points needs a Discord account that has earned them.
- **Commission one (paid, Blake's call).** The collab says many of its artists take commissions. That is the only way to
  get a particular one soon, but it is paying for art, which KaizoCore's rules leave out until Blake says otherwise. A
  commissioned sheet should still go through the collab's review so it ships under the same licence and credits as the
  rest.
- **Another source.** None today. Overworld follower packs cover most of the 44 but in another format and style;
  using them would mean a second set of rules for offsets and animations, and art that does not match. Not
  recommended.
- **Mega Falinks and White Squawkabilly** follow their base species: they stand in as soon as Falinks or
  Squawkabilly is drawn, with no change.

## 7. The DS tracker's form pictures

The same catalogue entry (FUTURE-PROJECTS.md, 2026-10-02) asked for the DS tracker to stop showing every form as its
base species. Done for the games it could be proven on, from the player's own ROM like the species (RomFormSprites):

- **Platinum.** The forms are in poketool/pokegra/pl_otherpoke.narc at places the game's code picks (pret's
  pokeplatinum, BuildPokemonSpriteTemplate): Deoxys, Unown's 28, Castform, Burmy, Wormadam, Shellos, Gastrodon,
  Cherrim, Arceus's 18 types, Shaymin, Rotom, Giratina. The game draws even the first form of these twelve from there;
  their own slot holds an older drawing, and Cherrim's holds its Sunshine form, so every Cherrim used to show in the sun.
- **Black 2 and White 2.** The forms are in the species' own archive after 685 entries, where each species' personal
  data points: 66 pictures, Unown to Genesect's drives, Basculin, Darmanitan, the seasons, the Therians, Kyurem, Keldeo
  and Meloetta.
- **Not done, for want of a dump to prove them on:** HeartGold and SoulSilver (pokeheartgold's table is Platinum's plus
  Spiky-eared Pichu, which moves every palette four on; the archive's path needs a dump), Black and White (the
  randomizer's numbers put the first form at entry 652), Diamond and Pearl (no species pictures from the ROM yet).
- **Not found:** Arceus's types on Black 2 and White 2. The species archive holds none, and no small archive holds its
  palettes; it keeps its Normal picture.

## 8. Shinies and the original games' forms (2026-10-03)

Blake: "And shiny sprites for all pokemon", "If they are shiny you should be able to play as shiny", "And forms
sprites". Both converters now take Sprite Collab's shiny sheets (d2ceb96254fb), and a shiny lead or tracker icon is
drawn in them; Play as your Pokemon's Always use has a Shiny switch.

- **How they ship.** Each shiny sheet is checked against its plain sheet as the app has it (convert_walking_pals_nat.py,
  THE SHINIES): the same size, the same pixels clear and opaque, every plain color one shiny color. 2,368 are exact
  recolors and ship as color maps (840 Gen 1-3, 1,528 later), about 150 KB of tables. 1,235 are not and ship as sheets
  of their own (372 Gen 1-3, 863 later), 4.4 MB: 1,202 split one plain color into two shiny ones, 21 clear other
  pixels, 12 have other frames. 139 of the maps send two plain colors to one shiny color; they still make the shiny
  pixel for pixel.
- **Gen 1-3.** 1,207 of Ironmon-Tracker's 1,222 sheets are pixel-identical cuts of Sprite Collab's plain sheets, so
  Sprite Collab's shinies fit them. The other 15 (mostly faint sheets Sprite Collab has redrawn since) are tested
  against Ironmon-Tracker's own pixels all the same.
- **Still without a shiny.** Every Gen 1-3 Pokemon has one. After Gen 3, Sprite Collab has no shiny for 8 species
  (Karrablast, Greedent, Barraskewda, Milcery, Glastrier, Oinkologne, Naclstack, Koraidon) and 10 forms (Galarian
  Meowth, Galarian Darumaka, Mega Skarmory, Mega Tyranitar, Mega Medicham, Mega Darkrai, Pa'u Oricorio, Minior's Red
  Core, Eternamax Eternatus, Terastal Terapagos): they walk plain. 21 faint animations have no shiny (Arbok, Magnemite,
  Haunter, Drowzee, Togetic, Octillery, Spinda, Absol, Groudon, Latios, Alolan Sandshrew, Starly, Pachirisu, Stunky,
  Happiny, Croagunk, Reshiram, Lycanroc, Grookey, Fuecoco, Quaxly): a shiny one faints in plain colors.
- **Forms.** Unown walks as its letter (pret: Gen 3 GET_UNOWN_LETTER from the personality, Gen 2 GetUnownLetter from the
  DVs), from 27 new sheets, 201-b to 201-question, all with shinies. Deoxys walks in its game's form in a retail game
  (FireRed Attack, LeafGreen Defense, Emerald Speed, Ruby and Sapphire Normal; pokefirered and pokeemerald's
  sDeoxysBaseStats). Castform walks as Normal. In a Nat. Dex build the forms are ids of their own (natdex-map.tsv), and
  every form Sprite Collab has drawn is mapped; the 65 that stand in for their base species (section 1) have no slot
  of their own there.
- **Not done:** the DS tracker's icons use shininess but not the form (Unown's letter, Deoxys) yet; the DS form index
  would need its own table per species.

## 9. DarkusShadow's overworld sprites (2026-10-04)

Section 5 turned these down for their format and look; Blake chose them for the gaps. They ship as a third set,
walkingpals-darkus/, written by convert_walking_pals_nat.py (DARKUSSHADOW, its docstring has the details) from the
post images kept in tools/trainer-data/sources/darkusshadow/ (sources.tsv: post, dates, sha256, artists, terms).

- **Covered (19):** Squawkabilly (and White Squawkabilly, which walks as it), Mabosstiff, Shroodle, Brambleghast,
  Toedscruel, Klawf, Rabsca, Espathra, Bombirdier, Flamigo, Brute Bonnet, Gimmighoul (Chest Form; the Roaming Form keeps
  Sprite Collab's), Wo-Chien, Chien-Pao, Miraidon, Okidogi, Iron Boulder, Iron Crown. Also Mega Malamar, which walked as
  Malamar, has its own now.
- **Still missing (27):** the 13 Gen 5 ones, the 4 Gen 7, the 7 Gen 8 and Mega Falinks (he draws Paldea, and a few
  Hisui and Megas); and **Maschiff** (by CarmaNekko) and **Iron Jugulis** (TooManyLuigis, Mortedesu, Billla and
  Wolfang62), which are only on his full Paldea sheet.
- **No shinies.** Every post's shiny link goes to his full "SHINY Gen 9 (Paldea) Pokemon Overworld Sprites" sheet. Its
  public copy is a 632 x 1264 JPEG and the plain sheet's a 632 x 1264 resampled preview; the originals need a login, so
  neither was used. He also says the full sheet holds newer versions than the single posts. A shiny walks in plain
  colors. If Blake downloads the two originals with his own account, the shinies, Maschiff and Iron Jugulis can follow.
- **Blake's pasted sheets,** matched byte for byte to their posts: Chien-Pao, Iron Boulder, Iron Crown and Mega Malamar
  are used; Slither Wing, Roaring Moon, Gouging Fire, Ogerpon (Teal Mask), Terapagos (Terastal), Hisuian Avalugg and
  Fezandipiti are not, since Sprite Collab draws them.
- **How the format maps.** His 256 x 256 sheets are 4 x 4 frames of 32 x 32 drawn at twice their pixels (Miraidon 512,
  64 x 64): each 2 x 2 block is checked to be one color and taken as one pixel, never resampled. At that size they are
  0.80 of Sprite Collab's height (median of the 8 species both draw; 0.83 by area), the nearest whole scale. His rows
  face down, left, right and up; the eight facings take them as drawn, each diagonal as the side view. Walk: his four
  frames from the first step, 8 game frames each. Idle: his two grounded poses, 32 each. The anchor is the offset rule's,
  on the standing pose. No sleep or faint: the app shows idle.
- **Credits:** walkingpals-darkus/credits.tsv per sheet, NOTICE, About and the Licenses page (DarkusShadow-Free-Use).
- **His other drawings that would fill stand-ins** (not fetched): Mega Clefable, Mega Victreebel, Mega Starmie, Mega
  Dragonite, Stellar Terapagos.
- Section 5's row for DarkusShadow, and section 6's "Another source. None today", are as they were on 2026-10-03.

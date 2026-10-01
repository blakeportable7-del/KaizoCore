# Nuzlocke tracker research: Pokemon Gen 4 and Gen 5 (US English releases)

Prepared 2026-09-29 for KaizoCore's planned Nuzlocke mode and tracker. Research only: nothing in the app or its repo was changed.

**Games covered, each with its own numbers:** Diamond, Pearl, Platinum (section 2); HeartGold, SoulSilver (section 3); Black, White (section 4); Black 2, White 2 (section 5). Section 6 covers where a complete list of encounter areas can come from, with licences. Section 7 collects every point that could not be confirmed from two sources.

**Contents:** 0 Summary for the design | 1 Conventions and how numbers were checked | 2 Sinnoh (DP, Pt) | 3 HeartGold/SoulSilver | 4 Black/White | 5 Black 2/White 2 | 6 Encounter-area data sources | 7 Open points

---

## 0. Summary for the design

1. **What a "level cap" is.** The highest level on the next boss's team (the "ace"). Bulbapedia defines it as not using Pokemon above the level of the next Gym Leader, Elite Four member or Champion's highest-levelled Pokemon; the Smogon guide says the same under "Hardcore Nuzlocke", adds that a Pokemon over the cap before the battle is boxed until it ends, and that levelling past the cap during the battle is allowed. Sources: [Bulbapedia, Nuzlocke Challenge](https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge); [Smogon, An Introduction to Pokemon Nuzlockes and Challenges (Band, 2023-04-17)](https://www.smogon.com/articles/introduction-nuzlockes); [Nuzlocke University optional rules](https://nuzlockeuniversity.ca/optional-rules/).
2. **Paired versions share trainer data.** Diamond and Pearl use one trainer file, as do HeartGold and SoulSilver (both single-file in the pret disassemblies, with no version-conditional fields), so their tables are identical and are given once per pair with both version names. Black and White have the same numbers except which 8th Gym Leader you meet (Drayden in Black, Iris in White, both max 43) and N's ace (Zekrom in Black, Reshiram in White, both 52). Black 2 and White 2 are identical to each other; they differ from Black/White and have three difficulty tiers.
3. **Diamond/Pearl and Platinum are different games for caps.** Fantina is Gym 5 at 36 in D/P but Gym 3 at 26 in Platinum. Maylene and Crasher Wake are 30/30 in D/P and 32/37 in Platinum. Platinum's Elite Four is L53-59 and Cynthia L62 on the first run; the higher team (69-75, Cynthia 78) only appears after the Stark Mountain story flag.
4. **HeartGold/SoulSilver are two ladders.** Johto: 13, 17, 19, 25, 31, 35, then Pryce at 34 (lower than Jasmine), Clair 41. Elite Four 42, 44, 46, 47, Lance 50. Kanto gyms 50 to 60 in any order with Blue forced last; Red at Mt. Silver is 88. Blaine's Cinnabar Gym is inside the Seafoam Islands.
5. **Black/White: the Elite Four come first, then N.** Elite Four first-run max is 50 for all four (any order), then N at N's Castle is 52 and Ghetsis 54. Alder is not fought on the first clear (Bulbapedia; Serebii agrees). **Black 2/White 2: Ghetsis (52) is fought at Giant Chasm before the League**, and the Elite Four are 58, Iris 59 in Normal Mode.
6. **Black 2/White 2 difficulty.** Only Normal Mode exists on a fresh save. Challenge Mode is a Black 2 unlock and Easy Mode a White 2 unlock, each earned by beating the Champion and tied to the save file; the only way to play the story in the other mode is a key transferred from another DS ([Bulbapedia, Key System](https://bulbapedia.bulbagarden.net/wiki/Key_System); [Serebii](https://www.serebii.net/black2white2/easychallengemode.shtml)). The tracker should default to the Normal column and treat Easy/Challenge as opt-in.
7. **What an "area" is.** The community test is the "Met in" text on the Pokemon summary (Smogon: an encounter counts if it shows up in the summary; Bulbapedia: check "Met in", and areas sharing a name but split by progression may be two). In Platinum's ROM data, 154 map headers have wild tables but only 63 distinct on-screen location labels, because cave floors share a label (Mt. Coronet 13 tables, Solaceon Ruins 18, Turnback Cave 17). HGSS: 135 encounter maps map to 88 location sections. Key the tracker's default area by location label, and offer per-map splitting as a setting.
8. **Mechanics that break a naive "first encounter per route" tracker:** Gen 4: Poke Radar, daily swarms, time-of-day pools, honey trees (DPPt), Great Marsh and Trophy Garden dailies (DPPt), dual-slot GBA cartridge pools, partner-trainer segments that turn every wild battle into a double (DPPt), Headbutt trees, the Bug-Catching Contest, the Safari Zone, radio pools and weekday-only Pokemon (HGSS). Gen 5: rustling grass, dust clouds, rippling water and bridge shadows (phenomena), dark-grass doubles, seasons keyed to the month, Hidden Grottoes (B2W2 only), weekday statics (B2W2).
9. **Gifts and statics have no community consensus.** Two sources both call something a "Gift Clause" and mean opposite things (section 1.4). The tracker should make gift and static handling a per-run setting.
10. **Encounter-area data.** No single source is complete and clean. PokeAPI (BSD-3-Clause) covers all nine games in a machine-readable form; pret covers Platinum, HeartGold/SoulSilver fully and Diamond/Pearl only as binary NARC pieces, with no licence file and no Gen 5 repo; Bulbapedia is CC BY-NC-SA 2.5; reading the user's own ROM at runtime ships no third-party data. See section 6 for numbers and file paths.

**Numbers I could not confirm from two independent sources** (full list in section 7): all Black 2/White 2 Easy Mode levels (Bulbapedia only); Black 2/White 2 Challenge and Easy levels for the rival, Team Plasma and Colress battles (Bulbapedia only; Serebii lists Normal only for those); the story position of N's second battle in Black/White (Bulbapedia and Serebii disagree on before or after Lenora; his level, 13, is not in doubt).

---

## 1. Conventions and how numbers were checked

### 1.1 Scope and tags
- **[GF] game fact** (Bulbapedia, Serebii, pret data, PokeAPI). **[CC] community convention** (Nuzlocke sites, rulesets, forums, Q&A). **[U] unverified**: a search snippet or memory only; nothing tagged [U] is used for a number.
- "Highest level" always means the highest level among the Pokemon on that team on the first encounter. Where the ace changes with your starter (rival battles), the level is the same across variants unless noted.
- Story positions are taken from Bulbapedia's walkthrough part order ([DP](https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Diamond_and_Pearl), [Platinum](https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Platinum), [HGSS](https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_HeartGold_and_SoulSilver), [BW](https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Black_and_White), [B2W2](https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Black_2_and_White_2)). They are approximate for optional cap points, and each table says so where sources disagree.

### 1.2 How the level tables were built
Every level in every table below was extracted by script, not typed, then compared across sources:

| Game | Primary extraction | Independent second source(s) |
|---|---|---|
| Diamond, Pearl | pret `pokediamond` `files/poketool/trainer/trdata.json` (commit `5bc4b1a`, 2026-09-10) | Bulbapedia trainer pages; Serebii `diamondpearl/gymleaders.shtml`, `elite4.shtml`; nuzlocketracker.org `/guides/diamond` (gyms) |
| Platinum | pret `pokeplatinum` `res/trainers/data/*.json` (commit `c248fb3`, 2026-09-20) | Bulbapedia trainer pages; Serebii `platinum/gyms.shtml`, `elitefour.shtml`; nuzlocketracker.org `/guides/platinum` (gyms) |
| HeartGold, SoulSilver | pret `pokeheartgold` `files/poketool/trainer/trainers.json` (commit `9d8b759`, 2026-09-21) | Bulbapedia trainer pages; Serebii `heartgoldsoulsilver/gym.shtml`, `elitefour.shtml`, `rival.shtml`, `teamrocket.shtml`; nuzlocketracker.org `/guides/heart-gold` (gyms) |
| Black, White | Bulbapedia trainer pages (raw wikitext via the MediaWiki API) | Serebii `blackwhite/gyms.shtml`, `elitefour.shtml`, `plasma.shtml`, `cheren.shtml`, `bianca.shtml`; nuzlocketracker.org `/guides/black` (gyms) |
| Black 2, White 2 | Bulbapedia trainer pages (Easy/Normal/Challenge tooltips parsed) | Serebii `black2white2/gyms.shtml`, `elitefour.shtml`, `n.shtml`, `rival.shtml`, `teamplasma.shtml` (Normal, and Challenge for gyms/E4/Champion); PsyPokes `bw2/gymelites.php` (Normal and Challenge gyms/E4); nuzlocketracker.org `/guides/black-2-normal`, `/guides/black-2-challenge` (gym caps) |

Result: all gym, Elite Four and Champion numbers agree across every source listed for their game. Cap-point battles agree between Bulbapedia and pret (Gen 4) or Bulbapedia and Serebii (Gen 5), with the exceptions in section 7.

### 1.3 What counts as an "area"
- Smogon: the one-encounter rule applies to any place that appears in the Pokemon summary (routes, cities, landmarks and so on) ([source](https://www.smogon.com/articles/introduction-nuzlockes)).
- Bulbapedia (near-universal rules): use the summary's "Met" place to confirm whether a location is a new encounter, for example for multiple levels of a cave; areas sharing a name but split by story progression may count as two ([source](https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge)).
- Nuzlocke University's baseline rule says "route or area" and does not define it ([source](https://nuzlockeuniversity.ca/nuzlocke-rules/)).
- Consequence for the tracker: the game's own location label is the default unit. Section 6 gives the label-versus-table counts per game.

### 1.4 Ruling legend used in the rulings tables
Community rulings are conventions, not rules of the games. Where sources disagree the tables show the range instead of picking one.

| Code | Meaning | Sources |
|---|---|---|
| **G1** | A gift is free: it does not use the area's one encounter (called the "Gift Clause" by Nuzlocke University) | [Nuzlocke University optional rules](https://nuzlockeuniversity.ca/optional-rules/) (easier-rules list); the HeartGold ruleset's Gift/Starter/Egg clauses ([DeviantArt, 2023-09-08](https://www.deviantart.com/theseleneseipher/art/SP-2-Pokemon-Heart-Gold-Nuzlocke-Rules-Settings-973695186)); [Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge) says some players count gifts as separate from wild encounters in the same area |
| **G2** | A gift is the area's one Pokemon (also called the "Gifts Clause") | [PokeBase answer, 2018-01-17](https://pokemondb.net/pokebase/295307/nuzlocke-allowed-capture-pokemon-same-area-pokemon-received): the clause lets you keep a gift as the one Pokemon from that area, and the answer notes it makes the run easier |
| **G3** | Gifts are banned, or (Giftlocke) only gifts and eggs are used | Nuzlocke University optional rule "Ban all Gift Pokemon"; [Nuzlocke University Giftlocke](https://nuzlockeuniversity.ca/nuzlocke-variants/giftlocke-variant/) |
| **S1** | Statics and story or weekday Pokemon: no consensus. The baseline says "first wild Pokemon in a route or area" and does not address them; one ruleset gives one catch per playthrough for story-event Pokemon and one optional catch per run for weekday overworld Pokemon | [Nuzlocke University baseline](https://nuzlockeuniversity.ca/nuzlocke-rules/); HeartGold ruleset above |
| **S2** | Ban powerful or legendary species | [Bulbapedia optional rules](https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge); Nuzlocke University optional rules |

Note that G1 and G2 use the same name for opposite meanings, so the tracker UI should describe the behaviour, not the name.

**Single-player rulesets that were read in full.** These are one person's rules, not a consensus; they are the only community sources found that address the Gen 4 and Gen 5 mechanics below. Forum threads (Nuzlocke Forums, Tapatalk, GameFAQs, Serenes Forest) returned HTTP 403 and reddit is blocked, so nothing from them is used as a fact.

| Ruleset | Date | What it says that matters here |
|---|---|---|
| [Mr. BoJangles, Revised Nuzlocke Rule Set (Tumblr)](https://mstr-bojangles.tumblr.com/post/30980683299/revised-nuzlocke-rule-set) | 2012 (Gen 4 and Gen 5 era) | First Pokemon per route, cave and so on, plus three extra tries if it is unwanted or a duplicate. Rock Smash is its own encounter (one rock per route, not counted against the route). Fishing and surfing are encounters separate from the route; ponds and lakes are not a new area and allow one extra encounter. Shaking spots, dust clouds, shadows and bubbles are encounters separate from the route, usable once per route. Non-legendary overworld Pokemon (Rotom, Volcarona, Drifloon, Musharna) are not encounters, and respawning ones may be caught once. Roaming legendaries (Raikou, Entei, Suicune, Latias, Latios) are not encounters and may be skipped. Gift Pokemon, gift eggs and gift fossils are legal to use. Twist Mountain fossils count as an encounter and only the first may be revived. Cave floors do not count toward the route they sit on, but each cave acts as its own route. NPC trades are legal if the Pokemon the NPC asks for was caught under the capture rule. Legendaries may be caught but only used if the plot requires it (Reshiram or Zekrom) |
| [Diamond ruleset by PokestoriesFTW (DeviantArt)](https://www.deviantart.com/pokestoriesftw/art/My-Pokemon-Diamond-Nuzlocke-Rules-692798279) | 2017-07-16 | The encounter rule starts at your first Poke Balls. Gifts and Pokemon met through an overworld sprite (the Friday Drifloon is the example) can be used without touching the route's encounter. NPC trades allowed. Honey trees: one Pokemon per tree, no duplicates, and a tree is not rechecked after a duplicate. In doubles, you may capture either wild Pokemon. Repels banned. Legendaries may be caught but not used |
| [Black ruleset by stormcloud106 (DeviantArt)](https://www.deviantart.com/stormcloud106/journal/stormcloud106-s-Pokemon-Black-Nuzlocke-Rules-908573513) | 2022-03-01 | An area changes when the name label of the area changes. If the first encounter is two Pokemon (dark grass), you may choose which to catch. A Pokemon that flees or is killed gives no second attempt |
| [White 2 ruleset by bleachamara (DeviantArt)](https://www.deviantart.com/bleachamara/art/My-White-2-Nuzlocke-Challenge-Rules-362932003) | 2013-04-01 | First Pokemon per area, roamers exempt. The outside and inside of Pinwheel Forest, Virbank Complex and the Dreamyard count as different areas. Legendaries may be caught but not used unless the plot needs it. A Hidden Grotto encounter counts only if you choose to catch what is inside (Bianca's tutorial grotto included) |
| [Randomized White run (Smogon thread)](https://www.smogon.com/forums/threads/pok%C3%A9mon-white-nuzlocke.3704218/) | 2022-07-02 | Gifts, trades and statics do not count as the first Pokemon of an area. Legendaries cannot be caught except Zekrom, which is boxed immediately |
| [HeartGold ruleset by TheSeleneSeipher (DeviantArt, uses a randomizer)](https://www.deviantart.com/theseleneseipher/art/SP-2-Pokemon-Heart-Gold-Nuzlocke-Rules-Settings-973695186) | 2023-09-08 | Starter, Egg and Gift clauses (these do not use the encounter rule). Story-event Pokemon: one catch per playthrough. Weekday overworld Pokemon: one optional catch per run. Fishing, honey trees and Headbutt each give one extra catch per area. Special zones such as the Safari Zone allow one catch. The Bug-Catching Contest activates special rules and forfeits the normal park encounter. One Game Corner purchase per location. Level caps are suspended for the Kanto gym leaders |

---

## 2. Sinnoh: Diamond, Pearl, Platinum

Source key for this section. Bulbapedia base: `https://bulbapedia.bulbagarden.net/wiki/`. Serebii base: `https://www.serebii.net/`. All fetched 2026-09-29 (Bulbapedia as raw wikitext through its MediaWiki API, Serebii as raw HTML).

### 2.1 Level caps: Diamond and Pearl (one trainer file, so the numbers are identical in both)

Gym order in D/P is Roark, Gardenia, Maylene, Crasher Wake, Fantina, Byron, Candice, Volkner. The Elite Four must be fought in a fixed order (Aaron, Bertha, Flint, Lucian, then Cynthia) ([Bulbapedia, Elite Four](https://bulbapedia.bulbagarden.net/wiki/Elite_Four)).

| Story position | Battle | Location | Highest level | Team (pret pokediamond) |
|---|---|---|---|---|
| Gym 1 | Roark | Oreburgh Gym | **14** | Geodude 12, Onix 12, Cranidos 14 |
| Gym 2 | Gardenia | Eterna Gym | **22** | Cherubi 19, Turtwig 19, Roserade 22 |
| Gym 3 | Maylene | Veilstone Gym | **30** | Meditite 27, Machoke 27, Lucario 30 |
| Gym 4 | Crasher Wake | Pastoria Gym | **30** | Gyarados 27, Quagsire 27, Floatzel 30 |
| Gym 5 | Fantina | Hearthome Gym | **36** | Drifblim 32, Gengar 34, Mismagius 36 |
| Gym 6 | Byron | Canalave Gym | **39** | Bronzor 36, Steelix 36, Bastiodon 39 |
| Gym 7 | Candice | Snowpoint Gym | **42** | Snover 38, Sneasel 38, Medicham 40, Abomasnow 42 |
| Gym 8 | Volkner | Sunyshore Gym | **49** | Raichu 46, Ambipom 47, Octillery 47, Luxray 49 |
| Elite Four 1 | Aaron | Pokemon League | **57** | Dustox 53, Beautifly 53, Vespiquen 54, Heracross 54, Drapion 57 |
| Elite Four 2 | Bertha | Pokemon League | **59** | Quagsire 55, Sudowoodo 56, Golem 56, Whiscash 55, Hippowdon 59 |
| Elite Four 3 | Flint | Pokemon League | **61** | Rapidash 58, Steelix 57, Drifblim 58, Lopunny 57, Infernape 61 |
| Elite Four 4 | Lucian | Pokemon League | **63** | Mr. Mime 59, Girafarig 59, Medicham 60, Alakazam 60, Bronzong 63 |
| Champion | Cynthia | Pokemon League | **66** | Spiritomb 61, Roserade 60, Gastrodon 60, Lucario 63, Milotic 63, Garchomp 66 |

Sources: pret [`pokediamond` trdata.json](https://github.com/pret/pokediamond/blob/master/files/poketool/trainer/trdata.json); Bulbapedia trainer pages [Roark](https://bulbapedia.bulbagarden.net/wiki/Roark), [Gardenia](https://bulbapedia.bulbagarden.net/wiki/Gardenia), [Maylene](https://bulbapedia.bulbagarden.net/wiki/Maylene), [Crasher Wake](https://bulbapedia.bulbagarden.net/wiki/Crasher_Wake), [Fantina](https://bulbapedia.bulbagarden.net/wiki/Fantina), [Byron](https://bulbapedia.bulbagarden.net/wiki/Byron), [Candice](https://bulbapedia.bulbagarden.net/wiki/Candice), [Volkner](https://bulbapedia.bulbagarden.net/wiki/Volkner), [Aaron](https://bulbapedia.bulbagarden.net/wiki/Aaron), [Bertha](https://bulbapedia.bulbagarden.net/wiki/Bertha), [Flint](https://bulbapedia.bulbagarden.net/wiki/Flint), [Lucian](https://bulbapedia.bulbagarden.net/wiki/Lucian), [Cynthia](https://bulbapedia.bulbagarden.net/wiki/Cynthia) (each page's Diamond and Pearl section); Serebii [gym leaders](https://www.serebii.net/diamondpearl/gymleaders.shtml) and [Elite Four](https://www.serebii.net/diamondpearl/elite4.shtml); [nuzlocketracker.org Diamond guide](https://nuzlocketracker.org/guides/diamond). All agree. (One small difference: Serebii lists Volkner's third Pokemon at 48 where Bulbapedia and pret have 47; the maximum, 49, is the same.)

**Cap points between the gyms (optional, story order):**

| Story position | Battle | Location | Highest level | Team (one starter variant shown) |
|---|---|---|---|---|
| Before Gym 1 | Rival Barry, 1st | Route 203 | **9** | Starly 7, Turtwig 9 |
| After Gym 1 | Commander Mars, 1st | Valley Windworks | **16** | Zubat 14, Purugly 16 |
| After Gym 2 | Commander Jupiter, 1st | Team Galactic Eterna Building | **20** | Zubat 18, Skuntank 20 |
| Before Gym 3 | Rival Barry, 2nd | Hearthome City | **21** | Starly 19, Buizel 20, Ponyta 20, Grotle 21 |
| After Gym 4 | Rival Barry, 3rd | Pastoria City | **28** | Starly 26, Buizel 25, Ponyta 25, Grotle 28 |
| Before Gym 6 | Rival Barry, 4th | Canalave City (drawbridge) | **35** | Staravia 31, Buizel 32, Heracross 30, Ponyta 32, Grotle 35 |
| After Gym 6 | Commander Saturn, 1st | Lake Valor | **37** | Kadabra 35, Bronzor 35, Toxicroak 37 |
| After Gym 6 | Commander Mars, 2nd | Lake Verity | **39** | Golbat 37, Bronzor 37, Purugly 39 |
| After Gym 7 | Commander Saturn, 2nd | Team Galactic HQ, Veilstone | **40** | Kadabra 38, Bronzor 38, Toxicroak 40 |
| After Gym 7 | Boss Cyrus, 1st | Team Galactic HQ, Veilstone | **43** | Murkrow 40, Golbat 40, Sneasel 43 |
| After Gym 7 | Boss Cyrus, 2nd | Spear Pillar | **48** | Honchkrow 45, Crobat 46, Gyarados 45, Weavile 48 |
| After Gym 8, before Elite Four | Rival Barry, 5th | Pokemon League entrance | **53** | Staraptor 48, Floatzel 49, Heracross 50, Rapidash 49, Snorlax 51, Torterra 53 |

Sources: pret trdata.json as above; Bulbapedia [Barry (D/P)](https://bulbapedia.bulbagarden.net/wiki/Barry_(game)/Diamond_and_Pearl), [Mars](https://bulbapedia.bulbagarden.net/wiki/Mars), [Jupiter](https://bulbapedia.bulbagarden.net/wiki/Jupiter), [Saturn](https://bulbapedia.bulbagarden.net/wiki/Saturn), [Cyrus](https://bulbapedia.bulbagarden.net/wiki/Cyrus); story order from the [Bulbapedia DP walkthrough](https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Diamond_and_Pearl). Two sources (Bulbapedia, pret) agree on every level except Jupiter's Golbat in the Spear Pillar Multi Battle (41 in pret, 42 on Bulbapedia; the highest level is the same). That Multi Battle against Mars (Bronzor 41, Golbat 42, Purugly 45) and Jupiter (Bronzor 41, Golbat 41 or 42, Skuntank 46), with Barry as your partner, has no row of its own: its highest level, 46, falls between Cyrus's 43 at the Galactic HQ and his 48 at the pillar, and you cannot train between it and Cyrus. Barry's 4th battle happens at the Canalave drawbridge (the walkthrough); nuzlocketracker.org labels it Route 218. Post-game Fight Area battles are out of scope.

### 2.2 Level caps: Platinum

Gym order in Platinum is Roark, Gardenia, Fantina, Maylene, Crasher Wake, Byron, Candice, Volkner (Hearthome moved to third).

| Story position | Battle | Location | Highest level | Team (pret pokeplatinum) |
|---|---|---|---|---|
| Gym 1 | Roark | Oreburgh Gym | **14** | Geodude 12, Onix 12, Cranidos 14 |
| Gym 2 | Gardenia | Eterna Gym | **22** | Turtwig 20, Cherrim 20, Roserade 22 |
| Gym 3 | Fantina | Hearthome Gym | **26** | Duskull 24, Haunter 24, Mismagius 26 |
| Gym 4 | Maylene | Veilstone Gym | **32** | Meditite 28, Machoke 29, Lucario 32 |
| Gym 5 | Crasher Wake | Pastoria Gym | **37** | Gyarados 33, Quagsire 34, Floatzel 37 |
| Gym 6 | Byron | Canalave Gym | **41** | Magneton 37, Steelix 38, Bastiodon 41 |
| Gym 7 | Candice | Snowpoint Gym | **44** | Sneasel 40, Piloswine 40, Abomasnow 42, Froslass 44 |
| Gym 8 | Volkner | Sunyshore Gym | **50** | Jolteon 46, Raichu 46, Luxray 48, Electivire 50 |
| Elite Four 1 | Aaron | Pokemon League | **53** | Yanmega 49, Scizor 49, Vespiquen 50, Heracross 51, Drapion 53 |
| Elite Four 2 | Bertha | Pokemon League | **55** | Whiscash 50, Gliscor 53, Hippowdon 52, Golem 52, Rhyperior 55 |
| Elite Four 3 | Flint | Pokemon League | **57** | Houndoom 52, Flareon 55, Rapidash 53, Infernape 55, Magmortar 57 |
| Elite Four 4 | Lucian | Pokemon League | **59** | Mr. Mime 53, Espeon 55, Bronzong 54, Alakazam 56, Gallade 59 |
| Champion | Cynthia | Pokemon League | **62** | Spiritomb 58, Roserade 58, Togekiss 60, Lucario 60, Milotic 58, Garchomp 62 |

**Why the Elite Four numbers are the first-run set.** Platinum has two teams per member. The game script picks the higher team only when the flag `FLAG_ARRESTED_CHARON_STARK_MOUNTAIN` is set (pret `res/field/scripts/scripts_pokemon_league_aaron_room.s`, which starts `TRAINER_ELITE_FOUR_AARON` otherwise), so a first clear always faces the lower team. Bulbapedia labels the sets "before Stark Mountain" and "after Stark Mountain"; Serebii labels the lower set "Pre-National Dex". The trigger is the Stark Mountain story flag as pret shows it.

| Member | First run (flag unset) | After Stark Mountain (flag set) |
|---|---|---|
| Aaron | 53 | 69 |
| Bertha | 55 | 71 |
| Flint | 57 | 73 |
| Lucian | 59 | 75 |
| Cynthia | 62 | 78 |

Sources: pret [`pokeplatinum` trainer data](https://github.com/pret/pokeplatinum/tree/main/res/trainers/data) (`leader_*.json`, `elite_four_*.json`, `champion_cynthia.json`); the same Bulbapedia trainer pages as above (their Platinum sections); Serebii [gyms](https://www.serebii.net/platinum/gyms.shtml) and [Elite Four](https://www.serebii.net/platinum/elitefour.shtml); [nuzlocketracker.org Platinum guide](https://nuzlocketracker.org/guides/platinum). All agree. Note that the Serebii Volkner section also lists his later Fight Area rematch (56 to 58); only the first four Pokemon (max 50) are the gym battle.

**Cap points between the gyms (optional, story order):**

| Story position | Battle | Location | Highest level | Team (one starter variant shown) |
|---|---|---|---|---|
| Start | Rival Barry, 1st | Route 201 | **5** | Chimchar 5 |
| Before Gym 1 | Rival Barry, 2nd | Route 203 | **9** | Starly 7, Chimchar 9 |
| After Gym 1 | Commander Mars, 1st | Valley Windworks | **17** | Zubat 15, Purugly 17 |
| After Gym 2 | Commander Jupiter, 1st | Team Galactic Eterna Building | **23** | Zubat 21, Skuntank 23 |
| After Gym 3 | Rival Barry, 3rd | Route 209 | **27** | Staravia 25, Buizel 23, Roselia 23, Monferno 27 |
| Before Gym 5 | Rival Barry, 4th | Pastoria City (Gym entrance) | **36** | Staravia 34, Buizel 32, Roselia 32, Monferno 36 |
| After Gym 5 | Boss Cyrus, 1st | Celestic Town (ruins) | **36** | Sneasel 34, Golbat 34, Murkrow 36 |
| Before Gym 6 | Rival Barry, 5th | Canalave City | **38** | Staraptor 36, Floatzel 35, Heracross 37, Roserade 35, Infernape 38 |
| After Gym 6 | Commander Saturn, 1st | Valor Cavern (Lake Valor) | **40** | Golbat 38, Bronzor 38, Toxicroak 40 |
| After Gym 6 | Commander Mars, 2nd | Lake Verity | **40** | Golbat 38, Bronzor 38, Purugly 40 |
| After Gym 7 | Commander Saturn, 2nd | Team Galactic HQ, Veilstone | **44** | Golbat 42, Bronzor 42, Toxicroak 44 |
| After Gym 7 | Boss Cyrus, 2nd | Team Galactic HQ, Veilstone | **46** | Sneasel 44, Crobat 44, Honchkrow 46 |
| After Gym 7 | Boss Cyrus, 3rd | Distortion World | **48** | Houndoom 45, Honchkrow 47, Crobat 46, Gyarados 46, Weavile 48 |
| After Gym 8, before Elite Four | Rival Barry, 6th | Pokemon League entrance | **51** | Staraptor 48, Floatzel 47, Heracross 48, Roserade 47, Snorlax 49, Infernape 51 |

Sources: pret trainer data (`rival_*`, `commander_*`, `galactic_boss_*`); Bulbapedia [Barry (Platinum)](https://bulbapedia.bulbagarden.net/wiki/Barry_(game)/Platinum), Mars, Jupiter, Saturn, Cyrus (Platinum sections); story order from the [Bulbapedia Platinum walkthrough](https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Platinum). Bulbapedia and pret agree on every level. The Spear Pillar Multi Battle against Mars (Bronzor 44, Golbat 44, Purugly 46) and Jupiter (Bronzor 44, Golbat 44, Skuntank 46), with Barry as your partner, tops out at 46, between Cyrus's 46 at the Galactic HQ and 48 in the Distortion World, so it has no row of its own. The Fight Area, Survival Area and Battleground rematches are post-game and out of scope.

### 2.3 Gifts, eggs, fossils, in-game trades

Ruling codes G1 to G3 are defined in section 1.4. "D/P" means Diamond and Pearl alike. Bulbapedia event pages: [D/P](https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Pok%C3%A9mon_Diamond_and_Pearl) and [Platinum](https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Pok%C3%A9mon_Platinum); also [Gift Pokemon](https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon) and Serebii [Platinum gifts](https://www.serebii.net/platinum/gift.shtml).

| Pokemon | How and where | Version differences | Level | Requirement or timing | Ruling range |
|---|---|---|---|---|---|
| Turtwig, Chimchar or Piplup | Prof. Rowan's briefcase | D/P: Lake Verity, after the scripted wild Starly attack. Pt: Route 201, where Rowan hands both trainers a Pokemon | 5 | game start | Given before any area encounter; Nuzlocke University lists "ban starters" only as an optional rule |
| Eevee | Bebe, house next to Hearthome's Pokemon Center | D/P: level 5, needs the National Pokedex. Pt: level 20, no National Pokedex needed | 5 (D/P), 20 (Pt) | see left | G1, G2 or G3. Hearthome has no wild table in the ROM, so no clash |
| Togepi Egg | Cynthia, Eterna City | Platinum only | 1 (hatched) | after Jupiter's first defeat | G1-G3. Eterna City has a fishing table in Platinum (pret `encounters_eterna_city.json`), so G2 would clash |
| Happiny Egg | Hiker, Hearthome City | D/P only | 1 (hatched) | any time | G1-G3 |
| Porygon | Man in Veilstone City, house behind the Pokemon Center | Platinum only (D/P: Trophy Garden daily pool after the National Pokedex) | 25 | any time | G1-G3 |
| Riolu Egg | Riley, Iron Island, after the two Galactic Grunts | D, P, Pt | 1 (hatched) | story | G1-G3 |
| Cranidos / Shieldon and, after the National Pokedex, five other fossils | Fossils are dug in the Underground; revived free at the Oreburgh Mining Museum one at a time | Skull Fossil (Cranidos) is the D/P one for Diamond, Armor (Shieldon) for Pearl. In Platinum it depends on the last digit of the Trainer ID (Bulbapedia's item pages: odd gives Skull, even gives Armor; its Gift Pokemon page states the reverse) | 20 | Explorer Kit needed | One player ruleset (2012) allows gift fossils; no Sinnoh-specific ruling found |
| Spiritomb | Hallowed Tower, Route 209 | all | 25 | Odd Keystone plus 32 Underground conversations. The counter is documented as rising only from talking to other players in the Underground, so it is probably not obtainable on an emulator without wireless [U for the emulator case] | static-type; S1 |
| Rotom | Old Chateau 2F, at night | D/P: level 15, needs National Pokedex. Pt: level 20, no National Pokedex needed, respawns next day | 15 / 20 | see left | S1 |
| Drifloon | Valley Windworks, outside | D/P level 22, Pt level 15 | 22 / 15 | every Friday after Mars is first defeated | weekly static; S1. The 2017 Diamond ruleset uses it as its example of an overworld-sprite Pokemon that is free to use; the 2012 ruleset says respawning ones may be caught once |
| Manaphy Egg | Poke Mart deliveryman | all | 1 | needs a Pokemon Ranger link mission | not obtainable in a normal run |

**In-game trades.** Platinum has four, confirmed in pret (`res/npc_trades/*.json`); Diamond and Pearl's list comes from [Bulbapedia's In-game trade page](https://bulbapedia.bulbagarden.net/wiki/In-game_trade) and is not independently checked. In Gen 4 the received Pokemon takes the level of the one you give.

| Location | You give | You get | Held item |
|---|---|---|---|
| Oreburgh City | Machop | Abra (male, Synchronize) | Oran Berry |
| Eterna Condominiums | Buizel | Chatot (female) | Leppa Berry |
| Snowpoint City | Medicham | Haunter (male) | Everstone |
| Route 226 island house | Finneon | Magikarp (female, Swift Swim) | Lum Berry |

Ruling range: the general Nuzlocke ban on outside trading is about other save files and Mystery Gift; NPC trades are allowed in the two rulesets that address them ([one 2012 ruleset](https://mstr-bojangles.tumblr.com/post/30980683299/revised-nuzlocke-rule-set), [a 2017 Diamond ruleset](https://www.deviantart.com/pokestoriesftw/art/My-Pokemon-Diamond-Nuzlocke-Rules-692798279)); both are single-player rulesets, not a consensus.

**Honey trees (DPPt mechanic, not a gift).** 21 trees; slathering takes effect after 6 real hours and lasts 24; the species is fixed when you slather, so resetting does not re-roll it; four of the 21 trees can hold Munchlax at 1%. Sources: [Bulbapedia, Honey Tree](https://bulbapedia.bulbagarden.net/wiki/Honey_Tree), [Dragonfly Cave](https://www.dragonflycave.com/sinnoh/honey-trees/), pret `src/overlay005/honey_tree.c`. Ruling: the 2017 Diamond ruleset (section 1.4) allows one Pokemon per tree with no duplicates and no rechecking a tree after a duplicate; no other source addresses honey trees.

### 2.4 Statics, roamers, legendaries

Roams = R; weekly = W; respawns after the Hall of Fame = H. Rulings: S1/S2 in section 1.4; no Sinnoh-specific ruling was found in any fetched source.

| Pokemon | Where and how | Versions and levels | Requirement | Notes |
|---|---|---|---|---|
| Mesprit, Uxie, Azelf | Lakes Verity, Acuity, Valor | all; level 50 | D/P: after Team Galactic is beaten and you have faced Dialga or Palkia. Pt: after the Distortion World | Mesprit roams after you talk to it (R); Uxie and Azelf are fixed. D/P: a KO is final. Pt: respawn after the Hall of Fame |
| Cresselia | Fullmoon Island, then roams | all; level 50 | National Pokedex (D/P); National Pokedex plus Hall of Fame (Pt); Canalave sailor's request | R; one roamer |
| Articuno, Zapdos, Moltres | roam Sinnoh | Platinum only; level 60 | National Pokedex, then talk to Oak in Eterna City (Serebii adds a Pal Park visit) | R |
| Regirock, Regice, Registeel | Rock Peak Ruins, Iceberg Ruins, Iron Ruins (Platinum). D/P: Pal Park transfer only | Platinum; level 30 | Hall of Fame plus an event (fateful-encounter) Regigigas in the party, per Bulbapedia's location pages. Not obtainable natively | H |
| Regigigas | Snowpoint Temple | D/P level 70; Pt level 1 | Regirock, Regice and Registeel in the party; temple locked until the National Pokedex (D/P) or National Pokedex plus Hall of Fame (Pt) | |
| Heatran | Stark Mountain | D/P 70; Pt 50 | D/P: after Buck returns the Magma Stone (Bulbapedia); Serebii's D/P page says after the National Pokedex. Pt: after Team Galactic is stopped at Stark Mountain and you talk to Buck in the Battleground | Pt: H |
| Giratina | D/P: Turnback Cave. Pt: Distortion World (Origin Forme, story), and Turnback Cave (Altered) only if not caught | D/P 70; Pt 47 | D/P: after National Pokedex. Pt story: mandatory | Pt: H |
| Dialga / Palkia | Spear Pillar | D/P: Dialga in Diamond only, Palkia in Pearl only, level 47 (story). Pt: both versions have both, level 70 | Pt: after Hall of Fame, Cynthia's grandmother and the Orb from Mt. Coronet | H (Pt) |
| Darkrai, Shaymin, Arceus | Newmoon Island, Flower Paradise, Hall of Origin | all | need Member Card, Oak's Letter, Azure Flute, none released | event only |
| Trophy Garden daily pair | Trophy Garden (behind the Pokemon Mansion) | all | after the National Pokedex | rotates daily, see 2.5 |

Not present: **no Snorlax** (it comes only from evolving Munchlax), **no Red Gyarados** (only a TV report in D/P), **Veilstone's Game Corner sells no Pokemon**. Sources: Bulbapedia event pages above, [Roaming Pokemon](https://bulbapedia.bulbagarden.net/wiki/Roaming_Pok%C3%A9mon), [Rock Peak Ruins](https://bulbapedia.bulbagarden.net/wiki/Rock_Peak_Ruins_(Sinnoh)), Serebii [Platinum legends](https://www.serebii.net/platinum/legends.shtml), [D/P legendaries](https://www.serebii.net/diamondpearl/legendaries.shtml), [D/P unobtainables](https://www.serebii.net/diamondpearl/unobtainables.shtml).

### 2.5 Area pitfalls

Each bullet: what the tracker must model; the community position (a source, or none found); the game-fact source.

- **Great Marsh** (Pastoria City). Six areas; Safari-style (30 Safari Balls and 500 steps for 500 yen; no fighting; bait and mud are reversed compared with the Johto Safari Zone); each area shows one random "changing" Pokemon per day; the pools differ between D/P and Platinum and change with the National Pokedex; a lookout with binoculars lets you see a Pokemon without an encounter. Community: a 2015 PokeBase answer says binocular sightings are not encounters, the first battle counts and splitting the sub-areas is up to the player ([source](https://pokemondb.net/pokebase/242093/nuzlocke-and-the-great-marsh)); Bulbapedia's Nuzlocke page has no Sinnoh entry. Facts: [Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/Great_Marsh), Serebii [Platinum](https://www.serebii.net/platinum/greatmarsh.shtml) and [D/P](https://www.serebii.net/diamondpearl/safari.shtml), pret `src/overlay006/great_marsh_daily_encounters.c` and `res/field/encounters/encounters_great_marsh_*.json`.
- **Poke Radar** (after the National Pokedex). Works only in tall grass on foot; up to four patches shake; it forces a battle even under Repel; 50 steps to recharge; a chain continues only on the same species; radar slots differ per map and can hold species not in the local table (Nidoran on Route 201). Community: no ruling found in any fetched source; model it as its own method the ruleset can allow or ban. Facts: [Bulbapedia, Poke Radar](https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9_Radar), Serebii [Platinum Poke Radar](https://www.serebii.net/platinum/pokeradar.shtml).
- **Swarms.** One map and species per day, gated by the National Pokedex, announced by the rival's sister in Sandgem; they replace two land slots on that map only. Counts: 22 swarm maps in Platinum (pret `src/overlay006/swarm.c`), 28 swarm entries in D/P (Serebii; Bulbapedia's 31 route rows minus 3 duplicates). Community: none found. Facts: Serebii [Platinum swarms](https://www.serebii.net/platinum/swarms.shtml), [D/P swarms](https://www.serebii.net/diamondpearl/swarms.shtml).
- **Trophy Garden dailies.** Permanent pool (Pichu, Pikachu, Roselia, Staravia, Kricketune) plus, after the National Pokedex, two daily Pokemon from a 16-species list at 5% each; a soft reset can re-roll Backlot's Pokemon in Gen 4. Community: none found. Facts: [Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/Trophy_Garden), Serebii [Platinum](https://www.serebii.net/platinum/trophygarden.shtml).
- **Time-of-day pools.** Morning 04:00-09:59, Day 10:00-19:59, Night 20:00-03:59 on the DS clock; the tracker must record the clock hour at the encounter (Zubat is night-only on Route 203; Hoothoot night-only in Platinum). Facts: [Bulbapedia, Time](https://bulbapedia.bulbagarden.net/wiki/Time).
- **Land, surf and fishing.** Separate tables per map (Old, Good and Super Rod). Community: sources disagree or are silent: one ruleset counts fishing and surfing as separate from the route and treats lakes as one extra catch in total ([2012 ruleset](https://mstr-bojangles.tumblr.com/post/30980683299/revised-nuzlocke-rule-set)); a 2020 PokeBase answer gives no concrete rule ([source](https://pokemondb.net/pokebase/332794/pokemon-nuzlocke-fishing-encounters-encounters-encounter)); Nuzlocke University and Bulbapedia do not split by method. Facts: pret `res/field/encounters/*.json` (`surf` and rod tables per map).
- **Multi-floor caves, split routes and the Lost Tower.** In Platinum's ROM (pret `include/data/map_headers.h` joined to `res/text/location_names.json`) 154 map headers carry wild tables but they use only 63 on-screen location labels. Labels that cover several encounter maps: Mt. Coronet 13, Solaceon Ruins 18, Turnback Cave 17, Old Chateau 9, Iron Island 7, Victory Road 6, Snowpoint Temple 6, Great Marsh 6, Route 209 6, Stark Mountain 3, and two each for Oreburgh Mine, Oreburgh Gate, Wayward Cave, Ruin Maniac Cave, Lake Verity, Routes 204, 205, 210, 211 and 212 (north or south, west or east). **The five Lost Tower floors carry the label "Route 209", not "Lost Tower"**, so a label-keyed tracker merges Lost Tower into Route 209, whereas Bulbapedia and PokeAPI list it separately. Community: use the summary's "Met in" text; areas sharing a name but split by progression may be two ([Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge)); a 2012 ruleset says cave levels do not count toward the route they sit on and act as routes of their own. No Sinnoh-specific Mt. Coronet ruling found. Facts: pret files above; [Bulbapedia, List of locations by index number in Generation IV](https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_in_Generation_IV).
- **Wild doubles from partner trainers.** Five story segments make every wild battle a Double Battle with a partner (Cheryl in Eterna Forest, Mira in Wayward Cave, Riley on Iron Island B2F, Buck at Stark Mountain, Marley on the east side of Victory Road post-game); roamers cannot appear. Community: choose one of the two wild Pokemon ([Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge); the 2017 Diamond ruleset agrees). Facts: pret trainer data shows all five partner trainers; Bulbapedia location pages.
- **Underground.** No wild Pokemon; fossils, spheres and the Spiritomb counter only.
- **Dual-slot (GBA cartridge) pools.** After the National Pokedex, with a GBA cartridge inserted, slots 8 and 9 change (about 8%); ignore unless the emulator core exposes slot 2. Facts: [Bulbapedia, Dual-slot mode](https://bulbapedia.bulbagarden.net/wiki/Dual-slot_mode), Serebii [Platinum](https://www.serebii.net/platinum/gbainsertion.shtml), pret `src/overlay006/dual_slot_encounters.c`.
- **First-encounter edge cases.** Fainted or fled means no second chance (basic rule). Whether a roamer that shows up first uses the route's encounter is unresolved (one ruleset excludes roamers). Repel does not stop a radar patch. Soft resets re-roll Trophy Garden, not honey trees.

### 2.6 Version differences

Wild species at default conditions, from Bulbapedia's encounter tables and re-derived from PokeAPI's `encounters.csv` (no swarm, radar, dual-slot or National Pokedex-only rows; they agree). The first four routes and Oreburgh are the same in Diamond and Pearl; the first D/P split is Eterna Forest.

| Area | Diamond | Pearl | Platinum |
|---|---|---|---|
| Route 201 | Starly, Bidoof | same | Starly, Bidoof, Kricketot |
| Routes 202 and 203 | 202: Starly, Bidoof, Shinx, Kricketot. 203: adds Abra and Zubat | same | same species as Diamond |
| Oreburgh Gate | Geodude, Zubat, Psyduck | same | adds Golbat |
| Oreburgh Mine | Geodude, Zubat, Onix | same | same species |
| Route 204 | Starly, Bidoof, Shinx, Budew, Kricketot, Zubat | same | adds Wurmple |
| Ravaged Path | Geodude, Zubat, Psyduck | same | no Geodude |
| Route 205 | Bidoof, Buizel, Pachirisu, Shellos | same | adds Budew, Kricketot, Wurmple, Silcoon, Cascoon, Beautifly, Dustox and Hoothoot (Bulbapedia puts these on the northern half) |
| Eterna Forest | Wurmple, Silcoon, Beautifly, Budew, Buneary, Murkrow (N) | Wurmple, Cascoon, Dustox, Budew, Buneary, Misdreavus (N) | Wurmple, Silcoon, Cascoon, Beautifly, Dustox, Budew, Buneary, Bidoof, Kricketot, Hoothoot, Gastly |
| Valley Windworks | Bidoof, Buizel, Pachirisu, Shellos | same | Buizel, Pachirisu, Shellos, Shinx (no Bidoof) |
| Route 206 | Geodude, Ponyta, Kricketot, Kricketune, Bronzor, Stunky, Zubat | same, no Stunky | Geodude, Ponyta, Kricketune, Machop, Gligar, Zubat |
| Route 207 | Geodude, Machop, Kricketot, Zubat | same | adds Ponyta |
| Route 208 | Machop, Meditite, Psyduck, Bidoof, Bibarel, Zubat | same | Ralts, Roselia, Budew, Bidoof, Bibarel, Zubat |

Beyond the first ten areas: Routes 209 and 210 south (D: Mime Jr., P: Bonsly, Pt: Duskull, Ralts, Roselia and others), Route 214 (D: Stunky, P: Sudowoodo, Pt: Houndour, Rhyhorn), Lost Tower at night (D: Murkrow, P: Misdreavus, Pt: Duskull), and the Great Marsh (section 2.5). Version exclusives (Serebii [D/P](https://www.serebii.net/diamondpearl/exclusives.shtml), [Platinum](https://www.serebii.net/platinum/exclusives.shtml)): Diamond only Seel, Dewgong, Scyther, Scizor, Murkrow, Larvitar line, Poochyena line, Aron line, Kecleon, Cranidos line, Honchkrow, Stunky line, Dialga; Pearl only Slowpoke line, Pinsir, Stantler, Misdreavus, Houndour line, Spheal line, Bagon line, Shieldon line, Mismagius, Glameow line, Palkia; Platinum-only wild Tangela, Tropius, Tangrowth and the Kanto birds; Platinum drops Murkrow, Misdreavus, Trapinch line, Clamperl line, Stunky, Glameow lines from the wild. Regirock, Regice, Registeel, Tangela, Tropius and the birds are Pal Park items in D/P.

### 2.7 Open points for Sinnoh
- Platinum fossil parity is contradicted inside Bulbapedia (2.3); do not hard-code which of Skull or Armor a save gets.
- Serebii's Platinum honey-tree page (via summary) says 18 trees; Bulbapedia, Dragonfly Cave and pret all say 21.
- Heatran and Giratina unlock conditions in D/P differ between Bulbapedia (story event, Hall of Fame) and Serebii (National Pokedex).
- D/P-only facts rest on Bulbapedia and Serebii; no D/P disassembly data covers gifts or trades. pret `pokediamond` has the encounter tables only as binary NARC pieces.
- Community rulings for Poke Radar, swarms, Trophy Garden, time-of-day pools and Mt. Coronet floors were not found in any fetchable source; forum threads (Nuzlocke Forums, Tapatalk, GameFAQs, Serenes Forest) returned HTTP 403.

---

## 3. HeartGold and SoulSilver

Source key for this section. Bulbapedia base: `https://bulbapedia.bulbagarden.net/wiki/`. Serebii base: `https://www.serebii.net/heartgoldsoulsilver/`. pret: [`pret/pokeheartgold`](https://github.com/pret/pokeheartgold) (commit `9d8b759`, 2026-09-21). All fetched 2026-09-29.

### 3.1 Level caps (HeartGold and SoulSilver share one trainer file, so the numbers are identical)

The game is two ladders: Johto (8 gyms, Elite Four, Lance), then Kanto (8 gyms, then Red). The Elite Four have a higher "after 16 Badges" team in Bulbapedia's [Elite Four](https://bulbapedia.bulbagarden.net/wiki/Elite_Four) table (Will 62, Koga 64, Bruno 64, Karen 64, Lance 75); that is the rematch and not a first-run cap. Order of the Elite Four: Will, Koga, Bruno, Karen, then Lance.

**Johto ladder and first Elite Four run**

| Story position | Battle | Location | Highest level | Team (pret pokeheartgold) |
|---|---|---|---|---|
| Johto Gym 1 | Falkner | Violet Gym | **13** | Pidgey 9, Pidgeotto 13 |
| Johto Gym 2 | Bugsy | Azalea Gym | **17** | Scyther 17, Kakuna 15, Metapod 15 |
| Johto Gym 3 | Whitney | Goldenrod Gym | **19** | Clefairy 17, Miltank 19 |
| Johto Gym 4 | Morty | Ecruteak Gym | **25** | Gastly 21, Haunter 21, Gengar 25, Haunter 23 |
| Johto Gym 5 | Chuck | Cianwood Gym | **31** | Primeape 29, Poliwrath 31 |
| Johto Gym 6 | Jasmine | Olivine Gym | **35** | Magnemite 30, Magnemite 30, Steelix 35 |
| Johto Gym 7 | Pryce | Mahogany Gym | **34** | Seel 30, Dewgong 32, Piloswine 34 |
| Johto Gym 8 | Clair | Blackthorn Gym | **41** | Gyarados 38, Dragonair 38, Dragonair 38, Kingdra 41 |
| Elite Four 1 | Will | Indigo Plateau | **42** | Xatu 40, Jynx 41, Exeggutor 41, Slowbro 41, Xatu 42 |
| Elite Four 2 | Koga | Indigo Plateau | **44** | Ariados 40, Venomoth 41, Forretress 43, Muk 42, Crobat 44 |
| Elite Four 3 | Bruno | Indigo Plateau | **46** | Hitmontop 42, Hitmonlee 42, Hitmonchan 42, Onix 43, Machamp 46 |
| Elite Four 4 | Karen | Indigo Plateau | **47** | Umbreon 42, Vileplume 42, Gengar 45, Murkrow 44, Houndoom 47 |
| Champion | Lance | Indigo Plateau | **50** | Gyarados 46, Dragonite 49, Dragonite 49, Aerodactyl 48, Charizard 48, Dragonite 50 |

Note that Pryce (34) is lower than Jasmine (35): a running "next boss" cap does not rise for Gym 7.

**Kanto ladder**

Kanto gyms can be done in any order. Blue is always last: in Gen 4 he stays on Cinnabar Island and does not return to Viridian Gym until you speak to him after the other seven Kanto badges ([Bulbapedia, Viridian Gym](https://bulbapedia.bulbagarden.net/wiki/Viridian_Gym)). The three sources order the other seven differently (Bulbapedia's walkthrough: Vermilion, Saffron, Cerulean, Celadon, Fuchsia, Pewter, Cinnabar, Viridian; Serebii: Vermilion, Saffron, Celadon, Cerulean, Fuchsia, Pewter, Cinnabar, Viridian; nuzlocketracker.org: Surge, Sabrina, Erika, Janine, Misty, Brock, Blaine, Blue), so the tracker should let the user pick the order and use the picked gym's ace as the cap. For reference, sorted by ace level the order is Janine 50, Surge 53, Misty 54, Brock 54, Sabrina 55, Erika 56, Blaine 59, Blue 60 (my arrangement, not a source).

| Kanto Gym (any order; Blue last) | Leader | Location | Highest level | Team (pret pokeheartgold) |
|---|---|---|---|---|
| Vermilion | Lt. Surge | Vermilion Gym | **53** | Raichu 51, Electrode 47, Magneton 47, Electrode 47, Electabuzz 53 |
| Saffron | Sabrina | Saffron Gym | **55** | Espeon 53, Mr. Mime 53, Alakazam 55 |
| Cerulean | Misty | Cerulean Gym | **54** | Golduck 49, Quagsire 49, Lapras 52, Starmie 54 |
| Celadon | Erika | Celadon Gym | **56** | Jumpluff 51, Tangela 52, Victreebel 56, Bellossom 56 |
| Fuchsia | Janine | Fuchsia Gym | **50** | Crobat 47, Weezing 44, Ariados 47, Ariados 47, Venomoth 50 |
| Pewter | Brock | Pewter Gym | **54** | Graveler 51, Rhyhorn 51, Omastar 53, Onix 54, Kabutops 52 |
| Cinnabar | Blaine | Cinnabar Gym (inside the Seafoam Islands) | **59** | Magcargo 54, Magmar 54, Rapidash 59 |
| Viridian | Blue | Viridian Gym | **60** | Exeggutor 55, Arcanine 58, Rhydon 58, Gyarados 52, Machamp 56, Pidgeot 60 |
| After all 16 Badges | Red | Mt. Silver (summit) | **88** | Pikachu 88, Lapras 80, Snorlax 82, Venusaur 84, Charizard 84, Blastoise 84 |

Sources: pret [`trainers.json`](https://github.com/pret/pokeheartgold/blob/master/files/poketool/trainer/trainers.json); Bulbapedia trainer pages [Falkner](https://bulbapedia.bulbagarden.net/wiki/Falkner), [Bugsy](https://bulbapedia.bulbagarden.net/wiki/Bugsy), [Whitney](https://bulbapedia.bulbagarden.net/wiki/Whitney), [Morty](https://bulbapedia.bulbagarden.net/wiki/Morty), [Chuck](https://bulbapedia.bulbagarden.net/wiki/Chuck), [Jasmine](https://bulbapedia.bulbagarden.net/wiki/Jasmine), [Pryce](https://bulbapedia.bulbagarden.net/wiki/Pryce), [Clair](https://bulbapedia.bulbagarden.net/wiki/Clair), [Will](https://bulbapedia.bulbagarden.net/wiki/Will), [Koga](https://bulbapedia.bulbagarden.net/wiki/Koga), [Bruno](https://bulbapedia.bulbagarden.net/wiki/Bruno), [Karen](https://bulbapedia.bulbagarden.net/wiki/Karen), [Lance](https://bulbapedia.bulbagarden.net/wiki/Lance), [Lt. Surge](https://bulbapedia.bulbagarden.net/wiki/Lt._Surge), [Sabrina](https://bulbapedia.bulbagarden.net/wiki/Sabrina), [Misty](https://bulbapedia.bulbagarden.net/wiki/Misty), [Erika](https://bulbapedia.bulbagarden.net/wiki/Erika), [Janine](https://bulbapedia.bulbagarden.net/wiki/Janine), [Brock](https://bulbapedia.bulbagarden.net/wiki/Brock), [Blaine](https://bulbapedia.bulbagarden.net/wiki/Blaine), [Blue](https://bulbapedia.bulbagarden.net/wiki/Blue_(game)), [Red](https://bulbapedia.bulbagarden.net/wiki/Red_(game)) (HeartGold and SoulSilver sections); Serebii [gyms](https://www.serebii.net/heartgoldsoulsilver/gym.shtml) and [Elite Four](https://www.serebii.net/heartgoldsoulsilver/elitefour.shtml); [nuzlocketracker.org HeartGold guide](https://nuzlocketracker.org/guides/heart-gold). All agree on every number above. Blaine's Cinnabar Gym is in a cave of the Seafoam Islands in Gen 2 and HGSS ([Bulbapedia, Blaine](https://bulbapedia.bulbagarden.net/wiki/Blaine)).

**Cap points (optional, story order)**

| Story position | Battle | Location | Highest level | Team (pret pokeheartgold) |
|---|---|---|---|---|
| Start | Rival Silver, 1st | Cherrygrove City | **5** | Cyndaquil 5 |
| Around Gym 2 | Executive Proton, 1st | Slowpoke Well | **12** | Zubat 8, Koffing 12 |
| Around Gym 2 | Rival Silver, 2nd | Azalea Town | **18** | Gastly 14, Zubat 16, Bayleef 18 |
| Before Gym 4 | Rival Silver, 3rd | Burned Tower | **22** | Gastly 20, Magnemite 18, Zubat 20, Bayleef 22 |
| Before Gym 7 | Executive Petrel, 1st | Team Rocket HQ (Mahogany) | **24** | Zubat 22, Raticate 24, Koffing 22 |
| Before Gym 7 | Executive Ariana, HQ battle (Double Battle with a Grunt, Lance is your partner) | Team Rocket HQ (Mahogany) | **27** | Arbok 25, Gloom 25, Murkrow 27 |
| After Gym 7 | Executive Proton, 2nd | Goldenrod Radio Tower | **33** | Golbat 28, Weezing 33 |
| After Gym 7 | Executive Petrel, 2nd | Goldenrod Radio Tower | **32** | Koffing 30, Koffing 30, Koffing 30, Weezing 32, Koffing 30, Koffing 30 |
| After Gym 7 | Executive Ariana, 2nd | Goldenrod Radio Tower | **32** | Arbok 32, Vileplume 32, Murkrow 32 |
| After Gym 7 | Executive Archer | Goldenrod Radio Tower | **38** | Houndour 35, Koffing 35, Houndoom 38 |
| After Gym 7 | Rival Silver, 4th | Goldenrod Tunnel | **34 (or 32)** | Golbat 30, Magnemite 28, Haunter 30, Sneasel 32, Feraligatr 32 |
| After Gym 8, before Elite Four | Rival Silver, 5th | Victory Road (Kanto), 3F west, before the exit | **40** | Sneasel 36, Golbat 38, Magneton 37, Haunter 37, Kadabra 37, Meganium 40 |
| After Lance, on first entering Mt. Moon | Rival Silver, 6th | Mt. Moon | **50** | Sneasel 46, Golbat 47, Magneton 46, Gengar 48, Alakazam 48, Meganium 50 |

Sources: pret `trainers.json` (all rows); Bulbapedia [Silver](https://bulbapedia.bulbagarden.net/wiki/Silver_(game)), [Archer](https://bulbapedia.bulbagarden.net/wiki/Archer), [Ariana](https://bulbapedia.bulbagarden.net/wiki/Ariana), [Petrel](https://bulbapedia.bulbagarden.net/wiki/Petrel), [Proton](https://bulbapedia.bulbagarden.net/wiki/Proton); Serebii [rival](https://www.serebii.net/heartgoldsoulsilver/rival.shtml) and [Team Rocket](https://www.serebii.net/heartgoldsoulsilver/teamrocket.shtml); story order from the [Bulbapedia HGSS walkthrough](https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_HeartGold_and_SoulSilver). Two or three sources agree on every level, with the one exception in note (2). Notes: (1) Silver's 4th battle is 34 when his ace is Meganium or Quilava and 32 when it is Feraligatr (that is, you chose Cyndaquil); (2) Ariana's Team Rocket HQ battle (27) is a Double Battle beside a Grunt with Lance as your partner, and pret, Serebii and Bulbapedia (her page and walkthrough Part 11) all give Arbok 25, Gloom 25, Murkrow 27; for her Goldenrod Radio Tower battle pret and Bulbapedia give 32 for all three, while Serebii's Team Rocket page lists Arbok 28, Vileplume 28, Murkrow 33 (no effect on the cap, since Archer's 38 follows); (3) Giovanni's L46 battle at Tohjo Falls needs the Celebi time-travel event (GS Ball event), so it is not part of a normal run; (4) Lance's L40 Dragonite at Team Rocket HQ is a partner battle. The post-game Dragon's Den and Indigo Plateau Silver rematches (L60) are out of scope.

### 3.2 Gifts, eggs, fossils, prizes, in-game trades

Ruling codes G1 to G3 are in section 1.4. Sources: Bulbapedia [HGSS in-game events](https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Pok%C3%A9mon_HeartGold_and_SoulSilver), [Gift Pokemon](https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon); Serebii [gifts](https://www.serebii.net/heartgoldsoulsilver/gift.shtml), [Game Corner](https://www.serebii.net/heartgoldsoulsilver/gamecorner.shtml), [Rock Smash](https://www.serebii.net/heartgoldsoulsilver/rocksmash.shtml). The one HGSS-specific community ruleset found ([DeviantArt, 2023-09-08](https://www.deviantart.com/theseleneseipher/art/SP-2-Pokemon-Heart-Gold-Nuzlocke-Rules-Settings-973695186)) exempts starters, eggs and gifts, gives Story-event and weekday Pokemon one optional catch per run, allows one purchase per Game Corner, and lets fishing and Headbutt give one extra catch per area; it is one player's rules, not a consensus.

| Pokemon | How and where | Versions | Level | Requirement or timing | Ruling range |
|---|---|---|---|---|---|
| Chikorita, Cyndaquil or Totodile | Elm's Lab, New Bark Town | both | 5 | start | before any area |
| Togepi (Egg, Extrasensory) | Mr. Pokemon's Mystery Egg; Elm's aide hands over the Egg in the Violet City Poke Mart | both | 1 (hatched) | after Falkner | G1-G3 |
| Mareep, Wooper or Slugma (Egg) | Primo, Violet City, via a secret code (Serebii: the code depends on your Trainer ID) | both | 1 (Serebii: hatched at 5) | timing not stated | G1-G3 |
| Spearow "Kenya" | Gatehouse north of Goldenrod (Route 35 side); you deliver it to a friend on Route 31, who takes it back | both | 20 | Serebii calls it "technically not a gift" | no ruling found |
| Eevee | Bill's house, Goldenrod | both | 5 | after meeting Bill in Ecruteak | G1-G3 |
| Shuckle "Shuckie" (OT Kirk) | Cianwood house; can be returned to him | both | 20 | after Chuck. A wild Shuckle also exists via Rock Smash in Cianwood | G1-G3 |
| Tentacool | Cianwood Pokemon Center man | both | 15 | only if your party is one Pokemon and your PC is empty; repeatable (an anti-softlock gift) | none needed |
| Tyrogue | Kiyo the Karate King, Mt. Mortar B1F | both | 10 | after beating him | G1-G3 |
| Dratini | Dragon's Den shrine quiz | both | 15 (ExtremeSpeed with the right answers, else Leer) | after Clair's TM59, then return to the shrine | G1-G3 |
| Bulbasaur, Charmander or Squirtle | Professor Oak, Pallet Town | both | 5 | after Red (Serebii: after all 16 Badges and Red) | G1-G3 |
| Treecko, Torchic or Mudkip | Steven Stone, Silph Co. lobby, Saffron City (stone colour picks the Pokemon) | both | 5 | after Red | G1-G3 |
| Fossil Pokemon | Fossils come from Rock Smash: outside the Ruins of Alph (Helix in HeartGold or Dome in SoulSilver, plus Old Amber in both) and Cliff Cave (Claw in HeartGold, Root in SoulSilver). Revived at the Pewter Museum of Science | HG: Omanyte, Anorith, Aerodactyl. SS: Kabuto, Lileep, Aerodactyl. Cranidos and Shieldon are trade-only | 20 | the Ruins of Alph rocks are available in the main story; the Cliff Cave rocks are said to need the Hall of Fame (single source) [U] | no HGSS ruling found |
| Game Corner prizes (Serebii levels) | Goldenrod: Abra 200 coins (L20), Ekans in HeartGold or Sandshrew in SoulSilver 700 (L15), Dratini 2,100 (L15). Celadon: Mr. Mime 3,333 (L5), Eevee 6,666 (L15), Porygon 9,999 (L5) | as listed | see left | Bulbapedia lists different prize levels (15 for Abra, Mr. Mime and Porygon), so the level is unreliable; coins come from Voltorb Flip outside Japan | the HeartGold ruleset allows one purchase per Game Corner |
| Not in HGSS or event-only | Odd Egg (Crystal only); Hitmonlee or Hitmonchan Dojo prize (HGSS gives Tyrogue instead); Celebi, Arceus and the Sinjoh Ruins Dialga, Palkia, Giratina (event Arceus); spiky-eared Pichu (needs an event Pichu); Manaphy Egg (Ranger link); Pokewalker species | | | | not obtainable on an emulator |

**In-game trades (10, identical in both versions per Bulbapedia's [In-game trade](https://bulbapedia.bulbagarden.net/wiki/In-game_trade) and [Serebii](https://www.serebii.net/heartgoldsoulsilver/trade.shtml)).** pret defines 13 NPC-trade entries: these 10, plus the Shuckle and Kenya gifts above, plus one (a Rapidash) that neither fan list shows. Ruling: the general no-outside-trading rule is about other save files; no HGSS-specific NPC-trade ruling found.

| Location | You give | You get | Condition |
|---|---|---|---|
| Violet City | Bellsprout | Onix (Sturdy, Persim Berry) | none |
| Goldenrod Dept. Store | Drowzee | Machop (Guts, Macho Brace) | none |
| Olivine City | Krabby | Voltorb (Static, Cheri Berry) | none |
| Blackthorn City | female Dragonair | Dodrio (Run Away, Smoke Ball) | none |
| Kanto Power Plant | Dugtrio | Magneton (Magnet Pull, Metal Coat) | none |
| Pewter City | Haunter | Xatu (Synchronize, Wacan Berry) | none |
| Saffron City | Pikachu | Pikachu (Static, Yellow Shard) | after beating Lt. Surge a second time |
| Silph Co. lobby | Forretress | Beldum (Clear Body, Dawn Stone) | after Steven's Hoenn starter and talking to him in the Pewter Museum |
| Diglett's Cave entrance | Bonsly | Rhyhorn (Lightning Rod, knows Thunder Fang, Passho Berry) | after Brock; Saturday 17:00 to 20:00 |
| Olivine Gym | any Pokemon | Steelix (Rock Head, Soothe Bell) | after Jasmine's rematch; 13:00 to 14:00 |

### 3.3 Statics, roamers, legendaries

Ruling: S1/S2 in section 1.4. Statics respawn after the next Hall of Fame entry if you defeated or fled them (Bulbapedia location pages). Only four roamers exist: Raikou, Entei, Latias, Latios (pret `include/constants/roamer.h`).

| Pokemon | Where and how | Versions and levels | Requirement or timing | Roams, one-time, exclusive? |
|---|---|---|---|---|
| Sudowoodo | Route 36 | both, 20 | SquirtBottle (Goldenrod flower shop girl, after Whitney) | one per Hall of Fame cycle |
| Red Gyarados | Lake of Rage | both, 30 | story | one per cycle |
| Lapras | Union Cave B2F | both, 20 | Fridays only | repeats weekly |
| Raikou, Entei | roam Johto | both, 40 | after the Burned Tower legendary-beast event | roam; flee on turn 1 |
| Suicune | scripted chase, battle at Route 25 | both, 40 | Misty's badge needed | static; if not caught it respawns at Burned Tower B1F after the League |
| Ho-Oh | Bell Tower roof | HeartGold 45 (Rainbow Wing from the Radio Tower director plus the Kimono Girls), SoulSilver 70 (Rainbow Wing from a Pewter City man) | as left | one per game, the level differs |
| Lugia | Whirl Islands B3F | SoulSilver 45, HeartGold 70 | as Ho-Oh | one per game |
| Articuno | Seafoam Islands B4F | both, 50 | Bulbapedia: first visit; Serebii: after 16 Badges | static |
| Zapdos | outside the Kanto Power Plant | both, 50 | after the Machine Part is returned | static |
| Moltres | Mt. Silver Cave | both, 50 | Mt. Silver access | static |
| Mewtwo | Cerulean Cave B1F | both, 70 | Kanto post-game | static |
| Snorlax | in front of Diglett's Cave (Route 11) | both, 50, holds Leftovers | wake it with the Poke Flute radio station | static |
| Latias | HeartGold: roams Kanto after Steven speaks to you in Vermilion City (35). SoulSilver: Pewter City via the Enigma Stone (40, event) | as left | as left | roams in HeartGold only |
| Latios | SoulSilver: roams Kanto (35). HeartGold: Pewter City via the Enigma Stone (40, event) | as left | as left | roams in SoulSilver only |
| Kyogre / Groudon | Embedded Tower | Kyogre in HeartGold, Groudon in SoulSilver, 50 | Blue Orb or Red Orb (Serebii adds National Pokedex plus Kanto starter) | version-exclusive, post-game |
| Rayquaza | Embedded Tower | both, 50 | Jade Orb from Oak, only if you showed him a Groudon and a Kyogre caught in HGSS | post-game |
| Electrode x3 | Team Rocket HQ | both, 23 | after Ariana's HQ battle | one-time |

Not in HGSS: the Regis, Regigigas, Mew, Jirachi, Deoxys, and Steven's Metagross (he trades a Beldum instead). Sources: Bulbapedia HGSS event list above, [Roaming Pokemon](https://bulbapedia.bulbagarden.net/wiki/Roaming_Pok%C3%A9mon), Serebii [legends](https://www.serebii.net/heartgoldsoulsilver/legends.shtml), pret `roamer.h`. Community position on roamers: the 2012 ruleset (section 1.4) says roaming legendaries (Raikou, Entei, Suicune, Latias, Latios) are not encounters and may be skipped, and that non-legendary overworld Pokemon are not encounters while respawning ones may be caught once; the 2023 HeartGold ruleset gives story-event Pokemon one catch per playthrough. Other positions (a roamer that shows up first counts as the route's encounter) appeared only in search summaries [U].

### 3.4 Area pitfalls

Each bullet: what the tracker must model; the community position; the game-fact source. "Section" below means a location label from the ROM (pret `map_headers.h` `.mapsec`).

- **Bug-Catching Contest** (National Park). A separate source inside the park's own section: Tuesday, Thursday and Saturday only; 20 Sport Balls, 20 minutes, one party Pokemon, one kept catch, no saving, no entry fee. Before the National Pokedex one table runs every contest day; afterwards Thursday and Saturday add Hoenn and Sinnoh bugs. "Met in" cannot tell contest from park grass. Community: only the HeartGold ruleset addresses it (using the contest catch forfeits the park's regular encounter); no consensus. Facts: [Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/Bug-Catching_Contest), pret `files/data/mushi/mushi_encount.csv`.
- **Johto Safari Zone.** Six rearrangeable areas, no time or step limit, 500 for 30 Safari Balls; pret's `files/arc/safari_enc.json` defines 12 area types (plains, meadow, savannah, peak, rocky beach, wetland, forest, swamp, marshland, wasteland, mountain, desert) that blocks upgrade over time; the Safari Zone Gate has its own section. Community: the HeartGold ruleset gives the whole zone one catch and does not use the first-encounter rule; a Nuzlocke Forums thread on multi-zone areas exists but returned HTTP 403 [U]. Facts: [Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/Johto_Safari_Zone), pret safari files.
- **Headbutt trees.** Model Headbutt as its own method. Whether a given tree holds a Pokemon depends on your Trainer ID and Secret ID and cannot be seen from outside; pret `files/arc/headbutt.json` has 60 maps with trees (852 trees plus 20 secret trees). Kanto trees can hold Hoenn and Sinnoh species after the National Pokedex. Community: one ruleset says fishing and Headbutt each give one extra catch per area; no other ruling found. Facts: [Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/Headbutt_tree), Serebii [Headbutt](https://www.serebii.net/heartgoldsoulsilver/headbutt.shtml), pret.
- **Swarms and radio.** One swarm per day, replacing land slots 0 and 1 (or surf and fishing slots); 22 swarm entries on 20 maps, consistent across Bulbapedia, Serebii and pret. Radio: Hoenn Sound (Wednesday) and Sinnoh Sound (Thursday) replace slots 2 to 5 after the National Pokedex, with the radio playing. Community: none found. Facts: [Bulbapedia, Mass outbreak](https://bulbapedia.bulbagarden.net/wiki/Mass_outbreak), Serebii [swarms](https://www.serebii.net/heartgoldsoulsilver/swarms.shtml).
- **Time-of-day pools.** Morning 04:00-09:59, Day 10:00-19:59, Night 20:00-03:59 from the DS clock; caves use the same three pools. Facts: [Bulbapedia, Time](https://bulbapedia.bulbagarden.net/wiki/Time), pret `gs_enc_data.json` (`morn`, `day`, `nite` per slot).
- **Land, surf, fishing, Rock Smash.** Each map has its own tables; rods are Old (Lv 10), Good (Lv 20), Super (Lv 40). pret has 10 Rock Smash maps and up to two species each. Community: the HeartGold ruleset gives fishing (with honey trees and Headbutt) one extra catch per area; Nuzlocke University's baseline does not split by method. Facts: pret `gs_enc_data.json`.
- **Multi-floor caves.** In the ROM, encounter maps that share one location label (pret `src/data/map_headers.h` `.mapsec`): Bell Tower 9, Mt. Silver Cave 7, Seafoam Islands 5, Ruins of Alph 5, Union Cave 3, Mt. Mortar 4, Ice Path 4, Whirl Islands 4, Mt. Moon 4, Cerulean Cave 3, Victory Road 3, and two each for Dark Cave, Slowpoke Well, Rock Tunnel, Burned Tower and Sprout Tower. The park's Bug-Catching Contest map shares the National Park label, and Route 2 has two encounter maps under one label. Mt. Silver (outside) and Mt. Silver Cave are different labels. Community: floors are not separate areas per the HeartGold ruleset; the "Met in" check per Bulbapedia; a 2012 ruleset says cave levels act as routes of their own. Facts: pret map headers (135 encounter maps, 88 labels).
- **Johto then Kanto.** Kanto areas are separate sections from Johto; no route number is shared in HGSS (Routes 1 to 22 and 24 to 28 are Kanto, 29 to 48 Johto). Routes 26 and 27, Tohjo Falls, Victory Road and Route 22 are Kanto sections you visit during the Johto story. Revisited Johto routes keep their section. The HeartGold ruleset's "Kanto Tour" clause is about level caps, not encounters. Facts: pret map headers.
- **Phone rematches and Pokegear.** Rematch calls start after 7 Badges and beating Team Rocket at the Radio Tower; swarm news comes from the radio in HGSS, not from calls; rematches are not encounters.
- **Weekday-gated Pokemon.** Lapras (Friday), the Bug-Catching Contest (Tue, Thu, Sat), Hoenn Sound (Wed), Sinnoh Sound (Thu), Brock's trade (Saturday evening). The HeartGold ruleset gives weekday overworld Pokemon one optional catch per run.
- **Kanto has no Safari Zone.** Pal Park stands on its site; Cinnabar Island is destroyed; the Johto Safari Zone is the only one.
- **First-encounter edge cases.** Roamers flee on turn 1 (Latias and Latios excepted); statics respawn after the Hall of Fame; the contest keeps one catch; Nuzlocke University has an optional retry when a first Pokemon is forced to leave by Roar or Teleport.

### 3.5 Version differences (species level, any time of day)

Verified against pret `gs_enc_data.json` (HG and SS stored separately) and re-derived from PokeAPI `encounters.csv`; Headbutt rows come from Bulbapedia and pret's headbutt file. Levels, rates and slots also differ slightly.

**Johto**

| Area | HeartGold | SoulSilver |
|---|---|---|
| Route 29 | Headbutt: Spinarak | Headbutt: Ledyba |
| Routes 30, 31 | Caterpie, Metapod, Spinarak | Weedle, Kakuna, Ledyba |
| Route 32 | none | Ekans |
| Union Cave | Sandshrew | none |
| Route 33 | none | Ekans |
| Ilex Forest | Caterpie, Metapod (Headbutt: Butterfree) | Weedle, Kakuna (Headbutt: Beedrill) |
| National Park | Caterpie, Metapod | Weedle, Kakuna |
| Route 36 | Growlithe | Vulpix |
| Route 37 | Growlithe, Spinarak | Vulpix, Ledyba |
| Routes 38, 39 | Rattata | Meowth |
| Route 41 (surf) | Mantine | none |
| Route 42 | Mankey | none |
| Ice Path | none | Delibird |
| Route 45 | Gligar, Phanpy | Teddiursa, Skarmory |
| Mt. Silver | Donphan (Phanpy in the cave) | Ursaring (Teddiursa in the cave) |

**Kanto**

| Area | HeartGold | SoulSilver |
|---|---|---|
| Route 2, Viridian Forest | Caterpie, Metapod, Butterfree, Spinarak, Ariados | Weedle, Kakuna, Beedrill, Ledyba, Ledian |
| Routes 3, 4 | none | Ekans, Arbok |
| Routes 5, 6 | none | Meowth |
| Route 7 | Growlithe | Vulpix, Meowth, Persian |
| Route 8 | Growlithe | Vulpix, Meowth |
| Route 9 | Mankey, Primeape | none |
| Route 26 | Sandslash, Dodrio | Arbok |
| Route 27 | Sandslash | Arbok, Dodrio |
| Route 28 | Donphan | Ursaring |
| Mt. Moon | Sandshrew, Sandslash | none |
| Victory Road | Donphan | Ursaring |
| Cerulean Cave 1F | Primeape | Persian |

Exclusives outside area pools: Game Corner Ekans (HG) versus Sandshrew (SS); fossils (HG Helix and Claw, SS Dome and Root); Ho-Oh L45 (HG) versus Lugia L45 (SS); Kyogre (HG) versus Groudon (SS); Kanto roamer Latias (HG) versus Latios (SS); Route 3 and Route 9 swarms are version-split (Baltoy or Gulpin, Sableye or Mawile).

### 3.6 Open points for HGSS
- Route 26 night grass differs between Bulbapedia (Doduo 5% in both versions) and pret (Doduo 0% in HG, 10% in SS); pret is game data but slot rates were not decoded independently.
- Articuno's gate: Bulbapedia (first visit) versus Serebii (after 16 Badges).
- Game Corner prize levels differ between Bulbapedia and Serebii (see 3.2).
- The Latias/Latios roamer trigger: Bulbapedia says Steven in Vermilion; Serebii adds the National Pokedex.
- The Cliff Cave fossil gate (Hall of Fame) and the Special Egg hatch level (1 versus 5) are single-source.
- Community rulings for fossils, NPC trades, roamers as a first encounter, and area counting are [U] beyond the one HeartGold ruleset and the Nuzlocke University pages; every forum source returned HTTP 403.
- I did not check whether "Met in" equals pret's `.mapsec` on the wild-catch path (only on the egg path); the tracker should test this on a real save.

---

## 4. Black and White

Source key for this section. Bulbapedia base: `https://bulbapedia.bulbagarden.net/wiki/`. Serebii base: `https://www.serebii.net/blackwhite/`. All fetched 2026-09-29. There is no pret disassembly for Gen 5.

### 4.1 Level caps

Black and White carry the same numbers. Two things differ by version and neither changes a level: the 8th Gym Leader (Drayden in Black, Iris in White, both 43) and N's ace (Zekrom in Black, Reshiram in White, both 52). In Striaton the leader depends on your starter (Serebii: Snivy meets Chili, Tepig meets Cress, Oshawott meets Cilan) but all three top out at 14. The Elite Four can be fought in any order ([Bulbapedia, Elite Four](https://bulbapedia.bulbagarden.net/wiki/Elite_Four)).

| Story position | Battle | Location | Highest level | Team (one variant shown) |
|---|---|---|---|---|
| Gym 1 | Cilan / Chili / Cress (set by your starter) | Striaton Gym | **14** | Lillipup 12, Pansage 14 |
| Gym 2 | Lenora | Nacrene Gym | **20** | Herdier 18, Watchog 20 |
| Gym 3 | Burgh | Castelia Gym | **23** | Whirlipede 21, Dwebble 21, Leavanny 23 |
| Gym 4 | Elesa | Nimbasa Gym | **27** | Emolga 25, Emolga 25, Zebstrika 27 |
| Gym 5 | Clay | Driftveil Gym | **31** | Krokorok 29, Palpitoad 29, Excadrill 31 |
| Gym 6 | Skyla | Mistralton Gym | **35** | Swoobat 33, Unfezant 33, Swanna 35 |
| Gym 7 | Brycen | Icirrus Gym | **39** | Vanillish 37, Cryogonal 37, Beartic 39 |
| Gym 8 | Drayden (Black) / Iris (White) | Opelucid Gym | **43** (Black) / **43** (White) | Fraxure 41, Druddigon 41, Haxorus 43 (Black); Fraxure 41, Druddigon 41, Haxorus 43 (White) |
| Elite Four (any order) | Shauntal | Pokemon League | **50** | Cofagrigus 48, Jellicent 48, Golurk 48, Chandelure 50 |
| Elite Four (any order) | Marshal | Pokemon League | **50** | Throh 48, Sawk 48, Conkeldurr 48, Mienshao 50 |
| Elite Four (any order) | Grimsley | Pokemon League | **50** | Scrafty 48, Krookodile 48, Liepard 48, Bisharp 50 |
| Elite Four (any order) | Caitlin | Pokemon League | **50** | Reuniclus 48, Musharna 48, Sigilyph 48, Gothitelle 50 |

**The end of the game is not "Elite Four then Champion".** The Champion, Alder, cannot be fought on the first clear: N has already beaten him, and after the Elite Four you fight N in N's Castle, then Ghetsis ([Bulbapedia, Alder](https://bulbapedia.bulbagarden.net/wiki/Alder); Serebii's [Team Plasma page](https://www.serebii.net/blackwhite/plasma.shtml) says the same). Alder's team (75 to 77, Volcarona 77) and the Elite Four's post-National-Pokedex rematch (71 to 73) are post-game.

| Story position | Battle | Location | Highest level | Team |
|---|---|---|---|---|
| After the Elite Four | N (Black) | N's Castle | **52** | Zekrom 52, Carracosta 50, Vanilluxe 50, Archeops 50, Zoroark 50, Klinklang 50 |
| After the Elite Four | N (White) | N's Castle | **52** | Reshiram 52, Carracosta 50, Vanilluxe 50, Archeops 50, Zoroark 50, Klinklang 50 |
| After N | Ghetsis | N's Castle | **54** | Cofagrigus 52, Bouffalant 52, Seismitoad 52, Bisharp 52, Eelektross 52, Hydreigon 54 |

Sources: Bulbapedia trainer pages [Cilan](https://bulbapedia.bulbagarden.net/wiki/Cilan), [Chili](https://bulbapedia.bulbagarden.net/wiki/Chili), [Cress](https://bulbapedia.bulbagarden.net/wiki/Cress), [Lenora](https://bulbapedia.bulbagarden.net/wiki/Lenora), [Burgh](https://bulbapedia.bulbagarden.net/wiki/Burgh), [Elesa](https://bulbapedia.bulbagarden.net/wiki/Elesa), [Clay](https://bulbapedia.bulbagarden.net/wiki/Clay), [Skyla](https://bulbapedia.bulbagarden.net/wiki/Skyla), [Brycen](https://bulbapedia.bulbagarden.net/wiki/Brycen), [Drayden](https://bulbapedia.bulbagarden.net/wiki/Drayden), [Iris](https://bulbapedia.bulbagarden.net/wiki/Iris), [Shauntal](https://bulbapedia.bulbagarden.net/wiki/Shauntal), [Marshal](https://bulbapedia.bulbagarden.net/wiki/Marshal), [Grimsley](https://bulbapedia.bulbagarden.net/wiki/Grimsley), [Caitlin](https://bulbapedia.bulbagarden.net/wiki/Caitlin), [N](https://bulbapedia.bulbagarden.net/wiki/N), [Ghetsis](https://bulbapedia.bulbagarden.net/wiki/Ghetsis) (Black and White sections); Serebii [gyms](https://www.serebii.net/blackwhite/gyms.shtml), [Elite Four](https://www.serebii.net/blackwhite/elitefour.shtml), [Team Plasma](https://www.serebii.net/blackwhite/plasma.shtml); [nuzlocketracker.org Black guide](https://nuzlocketracker.org/guides/black) (gyms only). All agree.

**Cap points (optional, story order).** Bulbapedia and Serebii ([Cheren](https://www.serebii.net/blackwhite/cheren.shtml), [Bianca](https://www.serebii.net/blackwhite/bianca.shtml), [Team Plasma](https://www.serebii.net/blackwhite/plasma.shtml)) agree on every level; story order is from the [Bulbapedia BW walkthrough](https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Black_and_White) and pages [Cheren](https://bulbapedia.bulbagarden.net/wiki/Cheren) and [Bianca](https://bulbapedia.bulbagarden.net/wiki/Bianca). Cheren also appears as a partner (Wellspring Cave 14, Route 5 26) but those are not opponent fights. Sage battles do not exist in Black and White.

| Story position | Battle | Location | Highest level | Team (one starter variant shown) |
|---|---|---|---|---|
| Start | Rival Cheren, 1st | Nuvema Town | **5** | Tepig 5 |
| Start | Bianca, 1st | Nuvema Town | **5** | Oshawott 5 |
| Before Gym 1 | Bianca, 2nd | Unova Route 2 | **7** | Lillipup 6, Oshawott 7 |
| Before Gym 1 | Rival Cheren, 2nd | Striaton City | **8** | Tepig 8, Purrloin 8 |
| Before Gym 1 | N, 1st | Accumula Town | **7** | Purrloin 7 |
| After Gym 1 | Rival Cheren, 3rd | Unova Route 3 | **14** | Tepig 14, Purrloin 12 |
| Gym 2 door (before or after Lenora, see note) | N, 2nd | Nacrene City | **13** | Pidove 13, Timburr 13, Tympole 13 |
| After Gym 3 | Bianca, 3rd | Castelia City | **20** | Herdier 18, Pansear 18, Munna 18, Dewott 20 |
| After Gym 3 | Rival Cheren, 4th | Unova Route 4 | **22** | Pidove 20, Pansage 20, Liepard 20, Pignite 22 |
| Before Gym 4 | N, 3rd | Nimbasa City | **22** | Sandile 22, Scraggy 22, Darumaka 22, Sigilyph 22 |
| After Gym 4 | Rival Cheren, 5th | Unova Route 5 | **26** | Liepard 24, Pansage 24, Tranquill 24, Pignite 26 |
| After Gym 5 | Bianca, 4th | Driftveil City | **28** | Herdier 26, Pansear 26, Musharna 26, Dewott 28 |
| Before Gym 6 | N, 4th | Chargestone Cave | **28** | Boldore 28, Ferroseed 28, Joltik 28, Klink 28 |
| After Gym 6 | Rival Cheren, 6th | Twist Mountain | **35** | Unfezant 33, Simisage 33, Liepard 33, Pignite 35 |
| After Gym 7 | Bianca, 5th | Unova Route 8 | **40** | Stoutland 38, Simisear 38, Musharna 38, Samurott 40 |
| After Gym 8, before Victory Road | Rival Cheren, 7th | Unova Route 10 | **45** | Unfezant 43, Simisage 43, Liepard 43, Emboar 45 |

The timing of N's second battle at Nacrene is disputed: Bulbapedia's walkthrough has N challenge you at the Gym door before Lenora, Serebii places it after you beat her. His team is 13 either way. Post-game rematches (Cheren 65 to 67, Bianca 63 to 65) are out of scope.

### 4.2 Gifts, eggs, fossils, trades

Codes G1 to G3 are in section 1.4. Sources: Bulbapedia [Gen V in-game events](https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Pok%C3%A9mon_Black_and_White), [Gift Pokemon](https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon), [Fossil](https://bulbapedia.bulbagarden.net/wiki/Fossil); Serebii [gifts](https://www.serebii.net/blackwhite/gift.shtml), [in-game events](https://www.serebii.net/blackwhite/ingameevent.shtml), [interactable Pokemon](https://www.serebii.net/blackwhite/interact.shtml). Community rulings for Black and White come only from the single-player rulesets in section 1.4: a 2022 randomized White run says gifts, trades and statics do not count as the first Pokemon of an area; the 2012 ruleset says gifts and gift eggs are legal, and that Twist Mountain fossils count as an encounter with only the first revived.

| Pokemon | How and where | Versions | Level | Requirement or timing | Ruling range |
|---|---|---|---|---|---|
| Snivy, Tepig or Oshawott | Professor Juniper, Nuvema Town; Cheren and Bianca take the other two | both | 5 | start | before any area |
| Pansage, Pansear or Panpour | A girl at the Dreamyard ruins. You get the monkey weak against your starter (Snivy gives Panpour, Tepig gives Pansage, Oshawott gives Pansear) | both | 10 | first visit; meant for the first gym | G1-G3. The Dreamyard also has wild grass (Patrat, Purrloin, Munna), so the ruling matters |
| Zorua | Game Freak building, Castelia City | both | 10 | needs an event Celebi from Gen IV via the Relocator, plus a spare Poke Ball; not obtainable in normal play | Castelia has no wild table |
| Zoroark | Lostlorn Forest | both | 25 | needs an event shiny Raikou, Entei or Suicune from Gen IV; event only | n/a |
| Larvesta (Egg) | A man in the rest house at the far end of Route 18 (Bulbapedia: a treasure hunter; Serebii: a man in a red suit and hat) | both | 1 (hatched) | the only Egg gift in Black and White; timing not stated by either source | G1-G3 |
| Tirtouga (Cover) or Archen (Plume) | Choose one fossil in Relic Castle; revive at the Nacrene Museum | both | 25 | Relic Castle's first floors; Nacrene City has no wild table | G1-G3 |
| Omanyte, Kabuto, Aerodactyl, Lileep, Anorith, Cranidos, Shieldon | A worker in Twist Mountain gives one random fossil per day | both | 25 | post-game (Bulbapedia: after Ghetsis; Serebii: after the National Pokedex); soft-resetting rerolls the fossil | G1-G3 |
| Magikarp | Salesman on Marvelous Bridge, 500 Poke Dollars | both | 5 | post-credits | a purchase |
| Musharna (Telepathy) | Dreamyard basement, Fridays | both | 50 | after the credits | weekly static |
| Darmanitan x5 (Zen Mode) | Statues at the Desert Resort entrance to Relic Castle | both | 35 | each needs a RageCandyBar | static |
| Foongus, Amoonguss "item balls" | Route 6 (3 Foongus, L20), Route 10 (2 Foongus L30, 1 Amoonguss L40) | both | as listed | none | static |
| Volcarona | Deepest Relic Castle | both | 70 | after the credits, after meeting Ryoku | static |
| Victini | Liberty Garden | both | 15 | needs the event Liberty Pass | not obtainable |

**In-game trades (five).** All [GF] from [Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/In-game_trade) and [Serebii](https://www.serebii.net/blackwhite/trade.shtml); the received Pokemon have set levels and one perfect IV.

| City | Black: you give, you get | White: you give, you get | Level |
|---|---|---|---|
| Nacrene City | Cottonee for Petilil | Petilil for Cottonee | 15 |
| Driftveil City | Minccino for Red-Striped Basculin | Minccino for Blue-Striped Basculin | 25 |
| Route 7 (first house) | Boldore for Emolga | same | 30 |
| Route 15 (first house) | Ditto for Rotom | same | 60 |
| Undella Town (summer only) | Cinccino for Munchlax | same | 60 |

**Not available:** the Dream World shut down on 2014-01-14 and the Dream Radar on 2023-03-27 (Bulbapedia, [Dream World](https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Dream_World), [Dream Radar](https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Dream_Radar)); Keldeo, Meloetta and Genesect are event-only; Poke Transfer (Route 15) needs the National Pokedex and counts as outside trading under the near-universal rule ([Bulbapedia, Nuzlocke Challenge](https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge)).

**Fishing.** Black and White have one rod, the Super Rod, and it is given only after the credits (Looker, Nuvema Town, after Ghetsis) ([Bulbapedia, Super Rod](https://bulbapedia.bulbagarden.net/wiki/Super_Rod)). Before the credits water encounters are Surf only, and every "fishing" row in the location tables is post-game.

### 4.3 Statics, roamers, legendaries

All [GF] from Bulbapedia's Gen V event list and Serebii's [legendary page](https://www.serebii.net/blackwhite/legendary.shtml). No source found rules on whether a static uses up an area's encounter.

| Pokemon | Where and how | Version | Level | Requirement | Roams, one-time, exclusive? |
|---|---|---|---|---|---|
| Reshiram (Black) / Zekrom (White) | You must capture it in N's Castle; N uses the other one | Black gets Reshiram, White gets Zekrom | 50 | story, before N's battle; if boxes are full it reappears at Dragonspiral Tower | one, version-exclusive |
| Cobalion | Mistralton Cave, Guidance Chamber (Surf from Route 6, Flash) | both | 42 | first visit; respawns after the Hall of Fame | one-time |
| Terrakion | Victory Road, Trial Chamber (Strength) | both | 42 | only after you meet Cobalion | one-time |
| Virizion | Pinwheel Forest, Rumination Field | both | 42 | only after Cobalion; needs the Bicycle to pass the old man | one-time |
| Tornadus (Black) / Thundurus (White) | roams Unova after a storm story on Route 7 | Tornadus in Black, Thundurus in White | 40 | after the 8th Badge and talking to the Route 10 gate clerk | roams; flees at once |
| Landorus | Abundant Shrine | both | 70 | needs Tornadus and Thundurus both in the party (trade or event) | post-game |
| Kyurem | Giant Chasm | both | 75 | post-game | one-time |
| Volcarona, Musharna, Darmanitan x5 | see 4.2 | both | 70, 50, 35 | see 4.2 | static |
| Swarms | after the National Pokedex, daily at midnight, all levels 15 to 55 | some by version | | post-game | route-bound |

There is no Snorlax in Black and White. Sources: [Bulbapedia, Roaming Pokemon](https://bulbapedia.bulbagarden.net/wiki/Roaming_Pok%C3%A9mon), [Mass outbreak](https://bulbapedia.bulbagarden.net/wiki/Mass_outbreak). The White Smogon ruleset above bars every legendary except the one story-forced Zekrom, which is boxed immediately; Nuzlocke University's Giftlocke page says story-forced legendary catches may not be used in a Giftlocke.

### 4.4 Area pitfalls

Each bullet: what the tracker must model; the community position; the game-fact source. The only community ruling on the phenomena found is the 2012 ruleset in section 1.4: shaking spots, dust clouds, shadows and bubbles are encounters separate from the route, usable once per route. Forum threads on them returned HTTP 403 [U]. The 2022 Black ruleset defines an area as changing whenever the on-screen name label changes, which supports keying areas by location label.

- **Rustling ("shaking") grass.** A phenomenon in normal grass only (never dark grass), never before the first Badge; about 10% per 20 steps; Repel does not stop it; it is not saved and vanishes when you leave the area. Audino is in every patch. It can produce evolved forms and the opposite version's species (Throh or Sawk, the monkeys, Whimsicott, Lilligant). Facts: [Bulbapedia, Phenomenon](https://bulbapedia.bulbagarden.net/wiki/Phenomenon), [Tall grass](https://bulbapedia.bulbagarden.net/wiki/Tall_grass).
- **Dust clouds.** Cave phenomenon: 40% Pokemon (Drilbur, Excadrill), otherwise an item. Facts: Bulbapedia Phenomenon page and the [Twist Mountain](https://bulbapedia.bulbagarden.net/wiki/Twist_Mountain) page.
- **Rippling water.** On Surf tiles (and fishing tiles post-game); in rippling water the opposite Basculin form appears; Route 4's rippling water gives Huntail in Black and Gorebyss in White. Facts: Bulbapedia Phenomenon.
- **Bridge shadows.** Driftveil Drawbridge (Ducklett) and Marvelous Bridge (Swanna, post-game); 20% Pokemon, 80% a wing item. Facts: Bulbapedia Phenomenon.
- **Dark-grass doubles.** Higher-level grass with a chance of a double battle in which you cannot throw a Ball until one is knocked out. Community: choose one of the two ([Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/Nuzlocke_Challenge); one 2022 Black ruleset agrees).
- **Seasons.** The console clock's month sets the season (Spring: Jan, May, Sep; Summer: Feb, Jun, Oct; Autumn: Mar, Jul, Nov; Winter: Apr, Aug, Dec) and it changes only at a load or building transition. Eight areas have season-dependent pools: Route 6, Route 7, Route 8, Twist Mountain, Icirrus City, Moor of Icirrus, Dragonspiral Tower and Undella Bay. This list matches both Serebii's [seasons page](https://www.serebii.net/blackwhite/seasons.shtml) and PokeAPI's `season-*` condition rows. Facts: [Bulbapedia, Season](https://bulbapedia.bulbagarden.net/wiki/Season_(game_mechanic)).
- **Emulated date.** Seasons, swarms, weekday events and White Forest residents all depend on the clock, so the tracker should store the emulated date at each encounter and not trust today's date (2026-09-29 is spring by the table above). melonDS keeps an adjustable date/time offset and saves it between runs (its [RTC post, 2023-10-31](https://melonds.kuribo64.net/comments.php?id=192)); builds up to 0.9.5 needed the host clock changed ([forum thread, 2024-07-19](https://melonds.kuribo64.net/board/thread.php?pid=6835)). Which core KaizoCore uses was not checked.
- **Multi-floor and split areas.** Pinwheel Forest has an outer and an inner half, which Bulbapedia cites as a case of one name split by progression; Route 1's west half needs Surf and has Lv 32 to 35 dark grass; Relic Castle, Chargestone Cave, Twist Mountain, Mistralton Cave, Wellspring Cave, Victory Road, Dragonspiral Tower and Giant Chasm each have several floors or sections; Nacrene, Castelia, Nimbasa, Accumula, Mistralton, Opelucid and Lacunosa cities have no wild table. Facts: Bulbapedia location pages.
- **Black City and White Forest.** Black City has no wild Pokemon; White Forest has wild Pokemon, all Lv 5, not blocked by Repel, and its pool depends on which residents live there (0 to 10). Residents depend on the clock. Facts: [Bulbapedia, White Forest](https://bulbapedia.bulbagarden.net/wiki/White_Forest), [Black City](https://bulbapedia.bulbagarden.net/wiki/Black_City).
- **Not in Black and White** (they are Black 2 and White 2 features): Hidden Grottoes, Habitat List, Nature Preserve, Join Avenue, Pokemon World Tournament, Seaside Cave. Lostlorn Forest, the Musical and Entralink do exist; Entralink and Entree Forest need dead online services.
- **First-encounter edge cases.** A fled first encounter uses the area; Tornadus and Thundurus flee at once; phenomena can fire before a normal encounter; a soft reset re-rolls swarms and Twist Mountain's fossil.

### 4.5 Version differences (wild Pokemon)

Black versus White, from Bulbapedia's tables and re-derived from PokeAPI (they agree; PokeAPI does not show Sawk or Throh as different because both appear in each version, one in normal grass and one in rustling grass).

| Area | Black | White |
|---|---|---|
| Routes 1 to 3, Dreamyard, Wellspring Cave | same (Basculin form aside; Route 3 swarm Volbeat in Black, Illumise in White) | same |
| Pinwheel Forest | outer: Sawk 10% (Throh 5% rustling); inner: Cottonee, Whimsicott (rustling) | outer: Throh 10% (Sawk 5% rustling); inner: Petilil, Lilligant (rustling) |
| Route 4 | rippling fishing: Huntail | rippling fishing: Gorebyss |
| Route 5 | Gothita | Solosis |
| Route 9 | Gothorita, Gothitelle (rustling) | Duosion, Reuniclus (rustling) |
| Route 10 | Sawk, Vullaby | Throh, Rufflet |
| Route 11, Village Bridge | Vullaby, Mandibuzz | Rufflet, Braviary |
| Route 12 | Kakuna, Beedrill | Metapod, Butterfree |
| Route 16 | Gothita | Solosis |
| Abundant Shrine | Murkrow, Honchkrow, Cottonee, Whimsicott | Misdreavus, Mismagius, Petilil, Lilligant |
| Victory Road | Vullaby | Rufflet |
| Nacrene City trade | gives Petilil | gives Cottonee |

Version exclusives (Serebii [exclusives](https://www.serebii.net/blackwhite/exclusives.shtml)): Black only Weedle line, Murkrow line, Houndour line, Shroomish line, Plusle, Volbeat, Cottonee line, Gothita line, Vullaby line, Tornadus, Reshiram; White only Caterpie line, Paras line, Misdreavus line, Poochyena line, Minun, Illumise, Petilil line, Solosis line, Rufflet line, Thundurus, Zekrom. Black City (Black) has no wild Pokemon; White Forest (White) has 33 distinct species (Bulbapedia's table; Serebii counts 32).

### 4.6 Open points for Black/White
- N's second battle position (before or after Lenora) is disputed between Bulbapedia and Serebii.
- The Twist Mountain fossil gate (after Ghetsis versus after the National Pokedex) differs between sources; both are post-game.
- Serebii says the Driftveil Minccino trade Pokemon is on Route 7, but Bulbapedia's Route 7 table has none.
- Community rulings for phenomena, seasons, Black City and White Forest were not found in any fetchable source [U].

---

## 5. Black 2 and White 2

Source key for this section. Bulbapedia base: `https://bulbapedia.bulbagarden.net/wiki/`. Serebii base: `https://www.serebii.net/black2white2/`. All fetched 2026-09-29. There is no pret disassembly for Gen 5.

### 5.1 Level caps

**Three difficulty tiers, one default.** Only Normal Mode exists on a fresh save. Easy Mode is exclusive to White 2 and Challenge Mode to Black 2; each is unlocked by beating the Champion and awards a key tied to the save file, and the only way to play the story in a mode other than Normal is to receive the key from another player through the Unova Link ([Bulbapedia, Key System](https://bulbapedia.bulbagarden.net/wiki/Key_System); [Serebii](https://www.serebii.net/black2white2/easychallengemode.shtml)). In Challenge Mode trainers' levels are 1 higher at the start rising to 5 higher by the end, Gym Leaders and the Elite Four each use one extra Pokemon with different moves and items and perfect-30 IVs, the raised levels count for prize money and experience, but trainer stats are still calculated at the Normal-Mode level. In Easy Mode levels are lower and the AI weaker, again with stats at Normal-Mode level. Neither source lists any change to wild Pokemon. The tracker should default to the Normal column and treat the other two as opt-in.

Gym order is Aspertia, Virbank, Castelia, Nimbasa, Driftveil, Mistralton, Opelucid, Humilau (Marlon is the 8th gym). The Elite Four can be fought in any order. Easy Mode values below are from Bulbapedia only (Section 7).

| Story position | Battle | Location | Easy | Normal | Challenge | Normal-Mode team |
|---|---|---|---|---|---|---|
| Gym 1 | Cheren | Aspertia Gym | 12 | **13** | 14 | Patrat 11, Lillipup 13 |
| Gym 2 | Roxie | Virbank Gym | 17 | **18** | 19 | Koffing 16, Whirlipede 18 |
| Gym 3 | Burgh | Castelia Gym | 22 | **24** | 26 | Swadloon 22, Dwebble 22, Leavanny 24 |
| Gym 4 | Elesa | Nimbasa Gym | 28 | **30** | 32 | Emolga 28, Flaaffy 28, Zebstrika 30 |
| Gym 5 | Clay | Driftveil Gym | 30 | **33** | 36 | Krokorok 31, Sandslash 31, Excadrill 33 |
| Gym 6 | Skyla | Mistralton Gym | 36 | **39** | 42 | Swoobat 37, Skarmory 37, Swanna 39 |
| Gym 7 | Drayden | Opelucid Gym | 44 | **48** | 52 | Druddigon 46, Flygon 46, Haxorus 48 |
| Gym 8 | Marlon | Humilau Gym | 47 | **51** | 55 | Carracosta 49, Wailord 49, Jellicent 51 |
| Elite Four (any order) | Shauntal | Pokemon League | 54 | **58** | 62 | Cofagrigus 56, Drifblim 56, Golurk 56, Chandelure 58 |
| Elite Four (any order) | Marshal | Pokemon League | 54 | **58** | 62 | Throh 56, Sawk 56, Mienshao 56, Conkeldurr 58 |
| Elite Four (any order) | Grimsley | Pokemon League | 54 | **58** | 62 | Liepard 56, Scrafty 56, Krookodile 56, Bisharp 58 |
| Elite Four (any order) | Caitlin | Pokemon League | 54 | **58** | 62 | Musharna 56, Sigilyph 56, Reuniclus 56, Gothitelle 58 |
| Champion | Iris | Pokemon League | 55 | **59** | 63 | Hydreigon 57, Druddigon 57, Archeops 57, Aggron 57, Lapras 57, Haxorus 59 |

Sources: Bulbapedia trainer pages [Cheren](https://bulbapedia.bulbagarden.net/wiki/Cheren), [Roxie](https://bulbapedia.bulbagarden.net/wiki/Roxie), [Burgh](https://bulbapedia.bulbagarden.net/wiki/Burgh), [Elesa](https://bulbapedia.bulbagarden.net/wiki/Elesa), [Clay](https://bulbapedia.bulbagarden.net/wiki/Clay), [Skyla](https://bulbapedia.bulbagarden.net/wiki/Skyla), [Drayden](https://bulbapedia.bulbagarden.net/wiki/Drayden), [Marlon](https://bulbapedia.bulbagarden.net/wiki/Marlon), [Shauntal](https://bulbapedia.bulbagarden.net/wiki/Shauntal), [Marshal](https://bulbapedia.bulbagarden.net/wiki/Marshal), [Grimsley](https://bulbapedia.bulbagarden.net/wiki/Grimsley), [Caitlin](https://bulbapedia.bulbagarden.net/wiki/Caitlin), [Iris](https://bulbapedia.bulbagarden.net/wiki/Iris) (their Black 2 and White 2 sections, Easy/Normal and Challenge tables); Serebii [gyms](https://www.serebii.net/black2white2/gyms.shtml) and [Elite Four](https://www.serebii.net/black2white2/elitefour.shtml) (Normal and Challenge); PsyPokes [Gym and Elite Four guide](https://www.psypokes.com/bw2/gymelites.php) (Normal and Challenge); nuzlocketracker.org [Black 2 Normal](https://nuzlocketracker.org/guides/black-2-normal) and [Black 2 Challenge](https://nuzlocketracker.org/guides/black-2-challenge) (gym caps). Normal and Challenge agree across all sources for every row. Easy Mode is single-source, but its offsets (Normal minus 1 to 4) mirror Challenge's plus 1 to 5.

**Cap points (optional, story order).** Ghetsis is fought at the Giant Chasm before the League, not after it. N's own fights in Black 2 and White 2 are post-game (his castle team is L70 and his season battles L75 to 77) and are out of scope.

| Story position | Battle | Location | Easy | Normal | Challenge | Normal-Mode team |
|---|---|---|---|---|---|---|
| Start | Rival Hugh, 1st | Aspertia City | 4 | **5** | 6 | Tepig 5 |
| Before Gym 1 | Rival Hugh, 2nd | Floccesy Ranch | 7 | **8** | 9 | Tepig 8 |
| After Gym 3 | Colress, 1st | Unova Route 4 | 21 | **23** | 25 | Magnemite 21, Klink 23 |
| Before Gym 5 | Sage Rood | Driftveil City | 24 | **27** | 30 | Herdier 27, Swoobat 27 |
| After Gym 6 | Rival Hugh, 4th | Undella Town | 37 | **41** | 45 | Unfezant 39, Simipour 39, Emboar 41 |
| After Gym 7 | Sage Zinzolin, 2nd | Opelucid City | 44 | **48** | 52 | Cryogonal 46, Cryogonal 46, Weavile 48 |
| After Gym 8, before Elite Four | Colress, 3rd | Plasma Frigate | 48 | **52** | 56 | Magneton 50, Metang 50, Beheeyem 50, Magnezone 50, Klinklang 52 |
| After Gym 8, before Elite Four | Ghetsis | Giant Chasm | 48 | **52** | 56 | Cofagrigus 50, Seismitoad 50, Eelektross 50, Drapion 50, Toxicroak 50, Hydreigon 52 |
| After Gym 8, before Elite Four | Rival Hugh, 5th | Victory Road (Black 2 and White 2) | 53 | **57** | 61 | Unfezant 55, Simipour 55, Bouffalant 55, Emboar 57 |

Sources: Bulbapedia [Hugh](https://bulbapedia.bulbagarden.net/wiki/Hugh), [Colress](https://bulbapedia.bulbagarden.net/wiki/Colress), [Zinzolin](https://bulbapedia.bulbagarden.net/wiki/Zinzolin), [Rood](https://bulbapedia.bulbagarden.net/wiki/Rood), [Ghetsis](https://bulbapedia.bulbagarden.net/wiki/Ghetsis); Serebii [Hugh](https://www.serebii.net/black2white2/rival.shtml) and [Team Plasma and Colress](https://www.serebii.net/black2white2/teamplasma.shtml), which list Normal Mode and agree on every Normal level; the Easy and Challenge values here are Bulbapedia only. Story order is from the [Bulbapedia B2W2 walkthrough](https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Black_2_and_White_2) (parts 1 to 15). Hugh's other appearances are partner battles (Castelia Sewers, Plasma Frigate, Lacunosa Town) or a fixed-level PWT match, and are not opponent caps. The Sage Zinzolin's two partner battles (Lacunosa Town 44, Plasma Frigate 50 in Normal Mode; Serebii's Team Plasma page) and the Shadow Triad fights (48 after Zinzolin in Opelucid City, 51 at the Giant Chasm entrance) each stay at or below the next cap listed here.

### 5.2 Gifts, eggs, fossils, trades

Codes G1 to G3 are in section 1.4. Sources: Bulbapedia [B2W2 in-game events](https://bulbapedia.bulbagarden.net/wiki/List_of_in-game_event_Pok%C3%A9mon_in_Pok%C3%A9mon_Black_2_and_White_2), [Gift Pokemon](https://bulbapedia.bulbagarden.net/wiki/Gift_Pok%C3%A9mon), [Fossil](https://bulbapedia.bulbagarden.net/wiki/Fossil); Serebii [gifts](https://www.serebii.net/black2white2/gift.shtml), [in-game trades](https://www.serebii.net/black2white2/ingametrade.shtml), [in-game events](https://www.serebii.net/black2white2/ingameevent.shtml), [interactable Pokemon](https://www.serebii.net/black2white2/interact.shtml). Community rulings come from the White 2 and 2012 rulesets in section 1.4.

| Pokemon | How and where | Versions | Level | Requirement or timing | Ruling range |
|---|---|---|---|---|---|
| Snivy, Tepig or Oshawott | Bianca gives a first partner Pokemon in Aspertia City | both | 5 | start | before any area |
| N's Zorua | Sage Rood, Driftveil City, as a reward for helping the original Team Plasma; OT N, Hasty, male, cannot be shiny | both | 25 | story | G1-G3 |
| Deerling | Weather Institute researcher on Route 6; Hidden Ability Serene Grace; its form follows the current season | both | 30 | story | G1-G3 |
| Happiny (Egg) | Pokemon Breeder in the gate between Route 3 and Nacrene City | both | 1 (hatched) | timing not stated; the Route 3 wild levels are 56 to 59, so it is late by geography [inference] | G1-G3 |
| Eevee | Research facility in Castelia City (Bulbapedia: Amanita; Serebii: Fennel's assistants); Hidden Ability Anticipation, male | both | 10 | post-game (Bulbapedia: after Iris; Serebii: after the Elite Four). A wild Eevee also exists in Castelia Park | G1-G3 |
| Tirtouga (Cover) or Archen (Plume) | Lenora at the Nacrene Museum; pick one; revived at the museum | both | 25 | only after Iris and the Hall of Fame (Bulbapedia's Fossil page) | G1-G3 |
| Omanyte, Kabuto, Aerodactyl, Lileep, Anorith, Cranidos, Shieldon | A worker in Twist Mountain gives one random fossil per day; the Join Avenue Antique Shop (after Iris and rank 15) and version-split Funfest missions also offer fossils | both | 25 | after Ghetsis or Iris (Bulbapedia) or the National Pokedex (Serebii) | the 2012 ruleset counts Twist Mountain fossils as an encounter and revives only the first |
| Shiny Gible (Black 2) or shiny Dratini (White 2) | Benga, Floccesy Town, after beating him in the Black Tower or White Treehollow | Gible: Black 2 only. Dratini: White 2 only | 1 | post-game | G1-G3 |
| Magikarp | Salesman on Marvelous Bridge, 500 Poke Dollars | both | 5 | the bridge is inaccessible until the Hall of Fame | a purchase |
| N's Pokemon (14 wild encounters) | Wild encounters after a Memory Link flashback | both | 7 to 55 | needs Memory Link with a Black/White save that has beaten N and Ghetsis, so a fresh emulator save will not have them | no ruling found |

**In-game trades (all confirmed by Bulbapedia and Serebii).**

| Where | You give | You get | Level | Versions |
|---|---|---|---|---|
| Route 4 | Cottonee | Petilil (female, Own Tempo) | 20 | Black 2 only |
| Route 4 | Petilil | Cottonee (male, Prankster) | 20 | White 2 only |
| Route 7 | Emolga | Gigalith | 35 | both |
| Humilau City | Mantine | Tangrowth | 45 | both |
| Route 15 | Ditto | Rotom | 60 | both |
| Accumula Town | Excadrill, then later Hippowdon | Ambipom, then Alakazam | 40 | both |
| Nimbasa City | any Pokemon | one Pokemon per day from a fixed 12-step list that depends on your gender, with Hidden Abilities | 50 | both |

The Nimbasa trade needs the Dropped Item subquest and then Xtransceiver calls; Bulbapedia says 15 calls, Serebii says 30, so the count is unresolved. Rulings: the 2012 ruleset allows NPC trades when the Pokemon the NPC asks for was caught under the capture rule; no B2W2-specific ruling found.

**Not obtainable in-game:** Victini (Liberty Garden only shows a scene if you already own one); Keldeo, Meloetta and Genesect (event only); Tornadus, Thundurus and Landorus (Dream Radar or trade); the fused Kyurem cannot be caught. Kyurem itself, Black Kyurem (Black 2) and White Kyurem (White 2) are handled by the Giant Chasm story and post-game.

### 5.3 Statics, roamers, legendaries

**Roamers: none** (Bulbapedia, Roaming Pokemon). Statics respawn after the Hall of Fame if defeated or fled, sometimes at a higher level. Community: the 2012 ruleset says non-legendary overworld Pokemon are not encounters and respawning ones may be caught once; the White 2 and 2012 rulesets let you catch legendaries but only use them if the plot requires it.

| Pokemon | Where and how | Versions and levels | Requirement or timing | Roams, one-time, exclusive? |
|---|---|---|---|---|
| Volcarona | Relic Castle lowest floor, reached via Relic Passage | both; 35, then 65 after the Hall of Fame | after the Quake Badge | respawns |
| Crustle | Seaside Cave, blocking the way to the Plasma Frigate | both; 42 | wake it with Colress's Colress Machine | one-time |
| Braviary (White 2) or Mandibuzz (Black 2) | Route 4, behind a house | version-exclusive; 25 | Mondays in White 2 (Braviary), Thursdays in Black 2 (Mandibuzz); Hidden Abilities | weekly |
| Jellicent | Undella Bay | both; 40 | male on Mondays in Black 2, female on Thursdays in White 2; Hidden Ability | weekly |
| Cobalion, Virizion, Terrakion | Route 13, Route 11, Route 22 | both; 45 (65 on respawn) | story; Terrakion after the Wave Badge and a Colress scene | one-time |
| Kyurem (fused) | Giant Chasm depths | story; cannot be caught | | |
| Reshiram (White 2) or Zekrom (Black 2) | Dragonspiral Tower | version-exclusive; 70 | post-game: beat Iris, then N in the ruins of his castle; take the stone to the tower | one-time |
| Kyurem (plain) | Giant Chasm depths | both; 70 | post-game, after catching N's dragon | |
| Latios (Black 2) or Latias (White 2) | Dreamyard | version-exclusive; 68 | post-game; it flees and must be chased | not roaming |
| Uxie, Mesprit, Azelf | in front of the Nacrene Museum; Celestial Tower roof; Route 23 | both; 65 | post-game, after the Cave of Being scene | respawn after the Hall of Fame |
| Regirock, Regice, Registeel | Underground Ruins | both; 65 | Regirock is open in both; the Iron Key (Black 2, after Regirock) and Iceberg Key (White 2) unlock the others and must be transferred to the other version | version-tied |
| Regigigas | Twist Mountain basement | both; 68 | Regirock, Regice and Registeel in the party | |
| Cresselia | Marvelous Bridge | both; 68 | Lunar Wing from the Strange House; bridge is post-game | |
| Heatran | Reversal Mountain | both; 68 | Magma Stone from a cliff on Route 18 | |
| Haxorus (shiny) | Nature Preserve | both; 60 | after seeing every Unova Pokemon (Permit from Juniper) | |
| Foongus and Amoonguss "item balls" | Routes 6, 7, 11, 22, 23 | both | none | interactable |

Not in Black 2 and White 2: Dialga, Palkia, Giratina, the Kanto and Johto legends, the Hoenn weather trio, and the Forces of nature (per Bulbapedia's game page and Serebii's unobtainable list). Sources: Bulbapedia B2W2 event list above, [Roaming Pokemon](https://bulbapedia.bulbagarden.net/wiki/Roaming_Pok%C3%A9mon), Serebii [legendary](https://www.serebii.net/black2white2/legendary.shtml), [unobtainable](https://www.serebii.net/black2white2/unobtainable.shtml).

### 5.4 Area pitfalls

Each bullet: what the tracker must model; the community position; the game-fact source.

- **Hidden Grottoes.** Twenty grottoes in 15 areas; four need Surf. An empty grotto regenerates at about 5% per 256 steps (raisable with the Grotto Pass Power) and only after you take the item or the Pokemon. Grotto Pokemon have Hidden Abilities, and a Pokemon caught there shows "Hidden Grotto" as its met place instead of the route. Community: the White 2 ruleset counts a grotto encounter as a regular encounter only if you choose to catch what is inside (Bianca's tutorial grotto included). Facts: [Bulbapedia, Hidden Grotto](https://bulbapedia.bulbagarden.net/wiki/Hidden_Grotto), Serebii [Hidden Grotto](https://www.serebii.net/black2white2/hiddengrotto.shtml).
- **Phenomena** (rustling grass, dust clouds, rippling water, flying-Pokemon shadows). Same mechanics as Black/White (section 4.4): rustling grass never in dark grass and never before the first Badge; dust clouds in caves; shadows only on Driftveil Drawbridge and Marvelous Bridge. Community: the 2012 ruleset treats each as an encounter separate from the route, usable once per route. Facts: [Bulbapedia, Phenomenon](https://bulbapedia.bulbagarden.net/wiki/Phenomenon).
- **Dark grass.** Higher-level grass with double battles. Community: choose one of the two (Bulbapedia's Nuzlocke page; the 2022 Black ruleset).
- **Water and fishing.** The Super Rod is given by Cedric Juniper in Nuvema Town during the post-game ([Bulbapedia, Super Rod](https://bulbapedia.bulbagarden.net/wiki/Super_Rod)), so before the credits water encounters are Surf, puddle and rippling-water only. The 2012 ruleset treats fishing and surfing as separate encounters from the route.
- **Seasons.** The month sets the season (Spring: Jan, May, Sep; Summer: Feb, Jun, Oct; Autumn: Mar, Jul, Nov; Winter: Apr, Aug, Dec), changing only at a load or transition. PokeAPI's `season-*` condition rows mark eight areas with season-dependent pools: Route 20, Route 7, Route 8, Icirrus City, Moor of Icirrus, Twist Mountain, Undella Bay and Dragonspiral Tower. Deerling and Sawsbuck also change form with the season, and the Castelia Sewers need Surf in spring and summer but not in autumn and winter. Facts: [Bulbapedia, Season](https://bulbapedia.bulbagarden.net/wiki/Season_(game_mechanic)), Serebii [seasons](https://www.serebii.net/black2white2/seasons.shtml). The emulated-clock advice in section 4.4 applies here too.
- **Weekday statics.** Route 4 (Mandibuzz on Thursday in Black 2, Braviary on Monday in White 2) and Undella Bay (Jellicent) depend on the emulator clock.
- **Multi-floor and split areas.** The game's met place is a location index shared by floors: Relic Castle, Victory Road, Giant Chasm, Pinwheel Forest (outer and inner alike), Relic Passage, Twist Mountain, Chargestone Cave, Mistralton Cave, Celestial Tower, Reversal Mountain, Wellspring Cave and Seaside Cave each carry one index for all floors. Separate indices exist for Route 4 versus Desert Resort, Route 20 versus Floccesy Ranch, Virbank City versus Virbank Complex, Castelia City versus Castelia Sewers, Clay Tunnel versus Underground Ruins, and Hidden Grotto (00143) ([Bulbapedia, location index list](https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_in_Generation_V)). Community: the White 2 ruleset counts the outside and inside of Pinwheel Forest, Virbank Complex and the Dreamyard as different areas, which the game's own met place cannot express for Pinwheel Forest; the 2012 ruleset says cave levels do not count toward the route they sit on and act as routes of their own; Bulbapedia's Nuzlocke page suggests the "Met" text as the tiebreak and notes that same-name areas split by progression may count as two. So per-map splitting must be a tracker setting, since it differs from the game's label.
- **Swarms.** After the National Pokedex, changing daily (Serebii); Bulbapedia's route tables list them, for example Route 20 has Sudowoodo (Black 2) or Mr. Mime (White 2) at 40 to 55.
- **N's Pokemon, PWT, Join Avenue, Pokestar Studios, Habitat List.** N's Pokemon need Memory Link (see 5.2). The other four gave no wild encounters in the pages read.
- **First-encounter edge cases.** A fled or fainted first encounter gives no second chance (basic rule; Nuzlocke University lists an optional retry for forced escapes such as Roar or Teleport). The Eon duo flee first and must be chased. Terrakion and Crustle need the Colress Machine. Regigigas, Heatran and Cresselia each need a specific item or party.

### 5.5 Version differences (wild Pokemon)

Black 2 versus White 2, from Bulbapedia's B2W2 tables (with explicit B2 and W2 flags). Basculin's form differs everywhere it appears: Red-Striped is normal in Black 2 and Blue-Striped in White 2, and rippling water flips it.

| Area | Black 2 | White 2 |
|---|---|---|
| Aspertia City, Routes 19 and 20 (land), Floccesy Ranch | same (Basculin form aside) | same |
| Route 20 | swarm Sudowoodo | swarm Mr. Mime |
| Virbank Complex | Magby | Elekid |
| Castelia Park | Buneary, Cottonee; rustling Lopunny, Whimsicott | Skitty, Petilil; rustling Delcatty, Lilligant |
| Route 4 | Trubbish; rippling Huntail; trade gives Petilil; Thursdays Mandibuzz | Minccino; rippling Gorebyss; trade gives Cottonee; Mondays Braviary |
| Routes 5 and 16 | Gothita | Solosis |
| Lostlorn Forest | Heracross, Cottonee (rustling Whimsicott) | Pinsir, Petilil (rustling Lilligant) |
| Route 6 | Karrablast 5%, Shelmet 25%; swarm Plusle | Karrablast 25%, Shelmet 5%; swarm Minun |
| Reversal Mountain | Spoink, Grumpig | Numel, Camerupt |
| Strange House | Gothita, Gothorita | Solosis, Duosion |
| Route 9 | Gothorita (rustling Gothitelle) | Duosion (rustling Reuniclus) |
| Route 11 | Karrablast 5%, Shelmet 25% | Karrablast 25%, Shelmet 5% |
| Route 12 | Heracross | Pinsir |
| Route 8, Icirrus City, Moor of Icirrus (puddles) | Karrablast rare, Shelmet common | Karrablast common, Shelmet rare |

Later areas follow the same pattern (Routes 22 and 23, Victory Road, Abundant Shrine). Version exclusives (Bulbapedia's [Version-exclusive Pokemon](https://bulbapedia.bulbagarden.net/wiki/Version-exclusive_Pok%C3%A9mon) and Serebii [exclusives](https://www.serebii.net/black2white2/exclusives.shtml)): Black 2 only the Weedle line, Magmar and Magby, Spinarak line, Sudowoodo, Bonsly, Plusle, Spoink line, Latios, Buneary line, Stunky line, Gible line, Magmortar, Gothita line, Vullaby line, Zekrom, Black Kyurem; White 2 only the Caterpie line, Mr. Mime, Electabuzz, Elekid, Ledyba line, Skitty line, Minun, Numel line, Latias, Glameow line, Mime Jr., Electivire, Solosis line, Rufflet line, Reshiram, White Kyurem. Petilil (Black 2) and Cottonee (White 2) come by the Route 4 trade; Registeel (White 2) and Regice (Black 2) need the Unova Link key. Black City (Black 2) and White Treehollow (White 2) hold a battle facility rather than a wild table; shops and Benga's team differ by version (Serebii [Black City and White Forest](https://www.serebii.net/black2white2/blackcitywhiteforest.shtml)).

### 5.6 Open points for Black 2/White 2
- Easy Mode levels for every battle, and Challenge/Easy levels for the rival, Plasma and Colress battles, are single-source (Bulbapedia).
- The Nimbasa trade call count (15 versus 30) and the Twist Mountain fossil gate (after Iris or after the National Pokedex) disagree between Bulbapedia and Serebii.
- The Nacrene Gate Happiny egg has no stated timing; Accumula Town's trade timing is also inferred from wild levels.
- Community rulings for phenomena, grottoes (beyond the 2013 White 2 ruleset), statics and NPC trades are otherwise single-source or [U]; forum threads returned HTTP 403.

---

## 6. Where the complete list of encounter areas can come from

Nothing below is legal advice; licence statements are the sources' own stated terms as read on 2026-09-29.

### 6.1 The four candidates, with numbers

"Wild-only" below means location areas with at least one row whose method is not gift, gift-egg, static, npc-trade, roaming, Pokemon Ranger, Feebas-tile fishing, Poke Flute or SquirtBottle. Counts were computed by script from the sources' own files.

| Game | US ROM game code | Wild-encounter file inside the ROM (UPR ZX offsets) | PokeAPI: location areas with rows (wild-only) / locations (wild-only) | pret encounter data | Bulbapedia location pages |
|---|---|---|---|---|---|
| Diamond | ADAE | `fielddata/encountdata/d_enc_data.narc` | 161 (145) / 74 (65) | binary NARC pieces only: 185 `narc_XXXX.bin` files in `files/fielddata/encountdata/d_enc_data/` | [Category:Sinnoh locations](https://bulbapedia.bulbagarden.net/wiki/Category:Sinnoh_locations) (197 pages, not all with wild tables) |
| Pearl | APAE | `fielddata/encountdata/p_enc_data.narc` | 161 (145) / 74 (65) | same, `p_enc_data/` (185 files) | same |
| Platinum | CPUE | `fielddata/encountdata/pl_enc_data.narc` | 163 (145) / 76 (65) | JSON: 185 files in `res/field/encounters/` (183 in `encounters.order`); 154 map headers with wild tables, 63 distinct location labels | same |
| HeartGold | IPKE | `a/0/3/7` | 172 (151) / 99 (91) | JSON: `files/fielddata/encountdata/gs_enc_data.json`, 142 map entries with HG and SS species side by side; 135 map headers with an encounter bank, 88 distinct location sections (46 Johto, 42 Kanto); plus `files/arc/headbutt.json` and `files/arc/safari_enc.json` | [Category:Johto locations](https://bulbapedia.bulbagarden.net/wiki/Category:Johto_locations) (144) and [Category:Kanto locations](https://bulbapedia.bulbagarden.net/wiki/Category:Kanto_locations) (156) |
| SoulSilver | IPGE | `a/1/3/6` | 172 (151) / 99 (91) | same file as HeartGold | same |
| Black | IRBO | `a/1/2/6` | 88 (80) / 52 (46) | none: pret has no Black/White repository | [Category:Unova locations](https://bulbapedia.bulbagarden.net/wiki/Category:Unova_locations) (158) |
| White | IRAO | `a/1/2/6` | 88 (80) / 52 (46) | none | same |
| Black 2 | IREO | `a/1/2/7` | 137 (128) / 68 (64) | none | same |
| White 2 | IRDO | `a/1/2/7` | 137 (128) / 68 (64) | none | same |

File paths and game codes are from the offsets file of the Universal Pokemon Randomizer ZX ([`gen4_offsets.ini`](https://github.com/Ajarmar/universal-pokemon-randomizer-zx/blob/master/src/com/dabomstew/pkrandom/config/gen4_offsets.ini), [`gen5_offsets.ini`](https://github.com/Ajarmar/universal-pokemon-randomizer-zx/blob/master/src/com/dabomstew/pkrandom/config/gen5_offsets.ini), entries "Diamond (U)" and so on). PokeAPI counts are from commit `89289e3` (2026-09-29) of [PokeAPI/pokeapi](https://github.com/PokeAPI/pokeapi) (`data/v2/csv/encounters.csv`, `location_areas.csv`, `encounter_slots.csv`, `encounter_methods.csv`). pret commits: `pokeplatinum` `c248fb3`, `pokeheartgold` `9d8b759`, `pokediamond` `5bc4b1a`. Bulbapedia counts include non-wild pages (cities, buildings).

### 6.2 PokeAPI

- **Where:** hosted API `https://pokeapi.co/api/v2/location-area/{id}`, `location/`, `encounter-method/`; source data as CSV in the repo at [`data/v2/csv/`](https://github.com/PokeAPI/pokeapi/tree/master/data/v2/csv); a static JSON dump in [PokeAPI/api-data](https://github.com/PokeAPI/api-data). Docs: [pokeapi.co/docs/v2](https://pokeapi.co/docs/v2) (the fair-use policy asks you to cache locally).
- **Licence:** [`LICENSE.md`](https://github.com/PokeAPI/pokeapi/blob/master/LICENSE.md) is BSD-3-Clause, copyright Paul Hallett and PokeAPI contributors 2013 to 2023, with a note that Pokemon and Pokemon character names are trademarks of Nintendo; `api-data` is also BSD-3-Clause. BSD-3-Clause allows shipping the data if the notice is kept and the project name is not used to endorse the app.
- **What is in it for these games:** all nine versions, with paired versions carrying different species where they really differ (comparing slot rows, the pairs differ in 236 rows for Diamond and Pearl, 790 for HeartGold and SoulSilver, 600 for Black and White, and 992 for Black 2 and White 2, counted across both sides). Methods present: walk, surf, old, good and super rod, rock smash, Headbutt, honey trees, gifts, gift eggs, statics, NPC trades, roaming, Gen 5 phenomena (`grass-spots`, `cave-spots`, `bridge-spots`, `surf-spots`, `super-rod-spots`, plus `dark-grass`), `hidden-grotto` (Black 2/White 2, 70 rows). Conditions present in the rows: swarm, time of day, Poke Radar, dual-slot cartridge, radio, season, Great Marsh daily slot, honey-tree group, Headbutt-tree group, Bug-Catching Contest, Johto Safari blocks, weekday, story-progress and item gates. `location_game_indices.csv` maps PokeAPI locations to each game's own location index (Gen 4: 328 rows; Gen 5: 206 rows), which is the number the game stores as a Pokemon's met place.
- **Gaps and quirks found:** Black and White have no `npc-trade` rows although the games have five in-game trades (Black 2 and White 2 have six trade rows); Sinnoh sea routes are named "Sea Route 220/223/226/230" where the game label is "Route 2xx"; the Platinum and HGSS lists contain non-wild entries (Distortion World, Hall of Origin, Newmoon Island, Flower Paradise, "Roaming Sinnoh", "Roaming Johto") and malformed entries with no English name (`sinnoh-pokemart`, `johto-pokemart`, "Unknown; all Poliwag", "Unknown; all Rattata", "Unknown; all bugs"); one B2W2 row lists a male Jellicent for both versions where White 2's is female; the Lost Tower is a separate PokeAPI location although the ROM labels it "Route 209".
- **Check against the ROM (Platinum):** after treating "Sea Route 2xx" as "Route 2xx", all 63 ROM location labels that have wild tables are present in PokeAPI's 76 Platinum locations; the other 13 PokeAPI entries are gift or static sites (Hearthome, Oreburgh, Veilstone, Snowpoint, Spear Pillar, Distortion World, Newmoon Island, Flower Paradise, Hall of Origin), a roaming entry, Floaroma Meadow (a honey-tree spot), the Lost Tower (labelled Route 209 in the ROM) and one malformed entry.

### 6.3 pret disassemblies

- **Repositories:** [pret/pokeplatinum](https://github.com/pret/pokeplatinum), [pret/pokeheartgold](https://github.com/pret/pokeheartgold), [pret/pokediamond](https://github.com/pret/pokediamond). The organisation has no Black/White or Black 2/White 2 repository ([pret org repository list](https://github.com/orgs/pret/repositories)).
- **Platinum:** wild tables in `res/field/encounters/*.json` (per map: land table with 12 slots, morning, day and night species, swarm species, radar species, surf and rod tables, and dual-slot species per GBA cartridge; the Great Marsh areas, honey trees and Trophy Garden dailies are separate tables). Map headers in `include/data/map_headers.h` carry `wildEncountersArchiveID` (`0xFFFF` means none) and `mapLabelTextID`; the label text is `res/text/location_names.json`. NPC trades `res/npc_trades/*.json`. Trainers `res/trainers/data/*.json` (928 files).
- **HeartGold and SoulSilver:** `files/fielddata/encountdata/gs_enc_data.json` per map (`land`, `surf`, `rock_smash`, `fishing` rods, per-version species as `HEARTGOLD` and `SOULSILVER`, morning, day and night, swarms, `hoenn` and `sinnoh` radio species); map headers in `src/data/map_headers.h` (`wildEncounterBank`, `mapsec`); Headbutt trees `files/arc/headbutt.json` (60 maps, 852 trees, 20 secret trees); Safari Zone `files/arc/safari_enc.json` (12 area types); trainers `files/poketool/trainer/trainers.json` (738 trainers).
- **Diamond and Pearl:** wild data exists only as binary NARC pieces; trainers are readable in `files/poketool/trainer/trdata.json` (850 trainers).
- **Licence:** none of the three repositories has a LICENSE file (GitHub reports no licence) and their READMEs state none. The data tables are transcriptions of Game Freak and Nintendo data and the repositories are built against a user-supplied ROM, so treat them as all rights reserved: use them to learn the formats, not as a shipped dataset.

### 6.4 Reading the user's own ROM at runtime

- **Idea:** KaizoCore already has the ROM, so it can read the area list from it and ship no third-party table. This is the only option that covers Black, White, Black 2 and White 2 completely and needs no licence for the data, because nothing is redistributed.
- **Gen 4:** map header to wild-encounter table index plus a location-label text index (Platinum `MapHeader.wildEncountersArchiveID` and `mapLabelTextID`, into the `pl_enc_data.narc` and the location-names text bank; HGSS `wildEncounterBank` and `mapsec`). Group tables by label for the default area. Each table has per-slot species, levels and time-of-day variants, plus swarm, radar, dual-slot, radio and honey-tree extras stored separately.
- **Gen 5:** the encounter NARC holds up to four 232-byte season blocks per entry (UPR's `Gen5RomHandler`), and wild-set index to map name goes through the map table file `a/0/1/2` and text bank `a/0/0/2` (`loadWildMapNames`).
- **The game's own idea of an area:** the "met place" index stored on each caught Pokemon, with names in Bulbapedia's [Generation IV](https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_in_Generation_IV) and [Generation V](https://bulbapedia.bulbagarden.net/wiki/List_of_locations_by_index_number_in_Generation_V) index lists and PokeAPI's `location_game_indices.csv`. Black 2/White 2 record Hidden Grotto catches as index 143 ("Hidden Grotto"), not the route. Whether KaizoCore can read that field from RAM was not researched [U].
- **Not in the tables:** which Pokemon are gifts, statics, roamers, weekday or season events. Those need a small hand-authored per-game file (sections 2 to 5 are the source material) or PokeAPI's rows for them.
- **Licence:** ROM contents are Nintendo and Game Freak's; reading them at runtime from the user's copy ships nothing. The parsing code should be written from the documented layouts: the Universal Pokemon Randomizer ZX ([Ajarmar/universal-pokemon-randomizer-zx](https://github.com/Ajarmar/universal-pokemon-randomizer-zx), GPL-3.0) and PKHeX ([kwsch/PKHeX](https://github.com/kwsch/PKHeX), GPL-3.0 per its LICENSE file; GitHub's licence detector shows "no assertion") contain parsers and encounter tables, but copying their code or tables would bring the GPL into the app.

### 6.5 Bulbapedia

- **Where:** one page per location, for example [Sinnoh Route 201](https://bulbapedia.bulbagarden.net/wiki/Sinnoh_Route_201), with a table per generation. In the wikitext the rows are `{{Catch/entry4|dex|name|D|P|Pt|method|levels|rates...}}` (Diamond, Pearl and Platinum flags per row), `{{Catch/entryhs|...}}` (HGSS), `{{Catch/entry5|...}}` (Black and White) and `{{Catch/entry5-2|...}}` (Black 2 and White 2). Location lists by category: the four Category pages in 6.1. Machine access: the MediaWiki API `https://bulbapedia.bulbagarden.net/w/api.php`.
- **Etiquette:** [robots.txt](https://bulbapedia.bulbagarden.net/robots.txt) sets a crawl delay of 5 seconds; the WebFetch tool used for this research is blocked (HTTP 403) but a normal client with a stated User-Agent and the API works.
- **Licence:** [Bulbapedia:Copyrights](https://bulbapedia.bulbagarden.net/wiki/Bulbapedia:Copyrights): Creative Commons Attribution-NonCommercial-ShareAlike 2.5 for original content (articles taken from Wikipedia are GFDL). Attribution, non-commercial use only, and share-alike, so shipping a scraped table would tie the shipped dataset to CC BY-NC-SA 2.5.
- **Coverage limits:** hand-written pages; no single page lists encounter areas per game; a page can mix versions in one table; cave floors may be merged or split differently from the ROM (the Lost Tower is separate on Bulbapedia but carries the "Route 209" label in Platinum's ROM).

### 6.6 Other sources seen

| Source | Content | Licence or terms |
|---|---|---|
| [Serebii](https://www.serebii.net/) game pages (gifts, legends, swarms, exclusives per game) | Good second source for facts | Footer: all content copyright Serebii.net 1999 to 2026; no reuse licence; robots.txt disallows only `/hidden/ranch/` and `/crossword/` |
| [nuzlocketracker.org](https://nuzlocketracker.org/guides/platinum) guides | Level caps and location lists per game; the prose is templated and partly wrong for some games | No licence seen on the pages fetched; treat as copyrighted |
| [veekun/pokedex](https://github.com/veekun/pokedex) | Older open Pokedex database | Repository is MIT (GitHub licence field); the underlying game data is still Nintendo's; not examined further |
| [PokemonDB](https://pokemondb.net/) | Location and trainer tables | Terms not checked |

### 6.7 Recommendation (my judgement, not a source)

1. Default to **reading the ROM at runtime** for the area list and label names, grouped by label, with per-map splitting as a setting (section 1.3 and the label counts in 6.1). It is the only route that is complete for Gen 5 and licence-free.
2. Ship a small **hand-authored per-game overrides file** for what the tables do not say: gifts, statics, roamers, weekday, season and time gates, partner-double segments, and the label merges that surprise players (Lost Tower under "Route 209"). Use sections 2 to 5 as the source material.
3. For development and offline tests, seed from **PokeAPI's CSV** (BSD-3-Clause) and diff it against the ROM-derived list; keep the notice file the licence requires if any of it ships.
4. Do not vendor Bulbapedia text (share-alike and non-commercial), Serebii, nuzlocketracker.org or the pret data files.

---

## 7. Open points and unconfirmed numbers

**Numbers not confirmed from two independent sources**
1. Black 2/White 2 Easy Mode levels for every battle: Bulbapedia only (Serebii and PsyPokes list Normal and Challenge; no source found for Easy).
2. Black 2/White 2 Challenge and Easy levels for Hugh, Colress, Zinzolin, Rood and Ghetsis: Bulbapedia only (Serebii lists Normal for those).
3. Black/White N's second battle position (before or after Lenora): the two sources disagree; the level, 13, is confirmed by both.
4. Every Diamond/Pearl-only fact about gifts and trades (Bulbapedia and Serebii only; no D/P disassembly data for them).

**Where sources conflict (not resolved)**
- Platinum fossil parity (Bulbapedia item pages versus its Gift Pokemon page).
- Heatran and Giratina unlock conditions in D/P (Bulbapedia versus Serebii); Articuno's gate in HGSS; Latias/Latios roamer trigger in HGSS.
- Twist Mountain fossil gate in Black/White and Black 2/White 2 (after Ghetsis or Iris versus after the National Pokedex); Nimbasa trade call count in Black 2/White 2 (15 versus 30).
- Game Corner prize levels in HGSS (Bulbapedia versus Serebii); Route 26 night grass in HGSS (Bulbapedia versus pret).
- Serebii's Platinum honey-tree count (18) versus Bulbapedia, Dragonfly Cave and pret (21).
- Serebii's Team Rocket page gives Ariana's Goldenrod Radio Tower team in HeartGold/SoulSilver as Arbok 28, Vileplume 28, Murkrow 33; pret and Bulbapedia give 32 for all three. No effect on the cap (Archer, 38, follows).

**Research limits**
- All forum sources (Nuzlocke Forums, Tapatalk, GameFAQs, Serenes Forest) returned HTTP 403 to the fetch tools and reddit is blocked, so community positions are limited to the sources named in section 1.4 plus Nuzlocke University, Bulbapedia and Smogon.
- Bulbapedia and Serebii pages show no edit dates; everything was fetched on 2026-09-29.
- The emulator core KaizoCore uses, and whether it exposes the met-place field, GBA slot 2 and the RTC, were not checked.
- Legal readings in section 6 are of stated licence terms only.

# Permission and courtesy messages (2026-09-06)

One message per rights holder, each written as a first contact that stands on
its own: what KaizoCore is, what of theirs it uses, that it will be
distributed, that source is GPL-3.0 and public, and that donations are
optional. Nothing here refers to an earlier conversation.

Rules for all of them:

- Send as Blake Porterfield, a person, not as Willow Creek Group. Every
  message is signed with blake@willowcreek.group so a reply can come by
  email if they prefer it to GitHub.
- Ask on GitHub where the project has a repository, so the answer is public,
  timestamped and linkable; that is what NOTICE records. If someone answers
  on Discord instead, screenshot it and save the image beside this file.
- Say the money part plainly. A yes that did not know about the donate
  button is not a yes.
- One ask per message. A one-line answer must be enough.
- Record each reply in NOTICE verbatim, with the date and the URL, and tick
  the matching line in "Binding release conditions".

The order matters. Cyan and RetroAchievements gate the public build; the
rest are courtesy and can go the same day.

---

## 1. CyanSMP64 (Nat. Dex Extension). REQUIRED.

**Why:** NatDexExtension has no licence file, so all rights are reserved.
The app ports its Pokémon and move data, bundles its sprites, and bundles
its two .bps patches and its Nat. Dex settings files inside the APK.

**Where:** a new issue on github.com/CyanSMP64/NatDexExtension. Title:
"Permission to use Nat. Dex Extension in KaizoCore, a free Android IronMON app".

**Message:**

> Hi Cyan,
>
> I am building KaizoCore, a free Android app that puts the whole IronMON
> setup on a phone: an emulator for Game Boy, Game Boy Color, GBA and DS, the
> IronMON tracker built in, the official rulesets ready to pick, a randomizer
> that runs on the device, and a stream server so each screen goes into OBS.
> Source will be public under GPL-3.0. It is going to a small closed beta
> first and then a public sideloaded APK, not the Play Store. The page is
> willowcreek.group/kaizocore.
>
> I would like your permission to use Nat. Dex Extension in it. Your
> repository has no licence file, so I am treating it as all rights reserved
> rather than assuming. Specifically:
>
> 1. The extension's Pokémon and move data, ported to Kotlin, so the tracker
>    works on Nat. Dex 1.2.x.
> 2. Its sprites, bundled in the app, because the Nat. Dex ROMs publish no
>    front-pic table the app could decode from.
> 3. Your natdex-emerald.bps and natdex-firered.bps, and your Nat. Dex Kaizo
>    settings files, bundled so a fresh install can patch and randomize
>    without hunting for files. If you would rather they were not inside the
>    APK, the app will download them from your release page on first use,
>    with credit, and nothing else changes.
>
> What I am not doing: no ROM is bundled, hosted or linked. Players patch
> their own dump, exactly as on PC. Nothing is sold and nothing is gated.
> There will be an optional donate button (Ko-fi or GitHub Sponsors) for
> test phones and hosting; if that changes your answer, say so and I will
> take the button off or leave Nat. Dex out.
>
> You would be credited by name, with a link to this repository, in the
> app's About screen and the README, and the app will state plainly that it
> is not an official Nat. Dex or IronMON release. I am also using your
> randomizer fork, which is GPL-3.0, and will keep that licence and its
> notices intact.
>
> A one-line yes, no, or "yes but" is plenty. Thank you for building it.
>
> Blake Porterfield
> blake@willowcreek.group

## 2. RetroAchievements. REQUIRED before the achievements feature ships publicly.

**Why:** the app talks to retroachievements.org through rcheevos. The
library is MIT and needs nothing, but RetroAchievements registers new
clients before release, keeps the list of approved emulators, and can refuse
an unregistered client or discard its unlocks. Their integration guide
expects an open-source client whose hardcore mode locks cheats, rewind, slow
motion and state loads, which KaizoCore's does.

**Where:** the RetroAchievements Discord, the developers and integrations
channel (ask a moderator which one is current), or the forum at
retroachievements.org. Have the GitHub link ready; they will ask for it.

**Message:**

> Hi. I am building KaizoCore, a free, open-source (GPL-3.0) Android app for
> IronMON players: an emulator for Game Boy, Game Boy Color, GBA and DS with
> the IronMON tracker built in. I have integrated rc_client from rcheevos:
> login with the session token kept on the phone, memory reads every frame
> through the core's memory map, softcore by default, and a hardcore mode
> that locks cheats, rewind, slow motion and save-state loads. Before anyone
> but me uses the achievements part, I would like to register the client
> with you and go through whatever review you require. Source: <GitHub
> link>. What do you need from me?
>
> Blake Porterfield
> blake@willowcreek.group

## 3. billgreenwald (ironmon_emu). REQUIRED, or drop the reference.

**Why:** the repository has no licence. Only its public architecture notes
were read as reference while designing the tracker's memory reads. Either
get that in writing or stop citing it.

**Where:** an issue on github.com/billgreenwald/ironmon_emu.

**Message:**

> Hi. I am building KaizoCore, a free, open-source (GPL-3.0) Android IronMON
> app: emulator, tracker and randomizer in one, going to a small beta at
> willowcreek.group/kaizocore. While designing the tracker's memory reads I
> read the architecture notes you published in ironmon_emu, and I want to
> credit that and have your okay in writing. I used only what is in the
> public repository, no unpublished source. Two questions: are you fine with
> that reference and a credit line in the app's NOTICE file, and is there
> any condition you want attached? There will be an optional donate button;
> nothing is sold. A one-line answer is plenty.
>
> Blake Porterfield
> blake@willowcreek.group

## 4. Besteon (Ironmon-Tracker, Gen 3). COURTESY, with one real question.

**Why:** the code is MIT and needs no permission. Two things deserve a
message anyway: the app's tracker panel is a deliberate clone of theirs, and
the APK bundles their type icons, status icons, badge images and Gen 3
sprite pack, which are Pokémon art kept in their repository. Their answer
does not make the art legal, but you should not learn on Discord that the
tracker's author objects.

**Where:** GitHub Discussions on github.com/besteon/Ironmon-Tracker, or the
IronMON Discord's tracker channel. Discussions is better; it is public.

**Message:**

> Hi. I am building KaizoCore, a free, open-source (GPL-3.0) Android app
> that runs the whole IronMON setup on a phone: emulator, tracker, randomizer
> and a stream server for OBS. The tracker panel in it is a clone of yours on
> purpose: same layout, same rules, ported to Kotlin against your source so
> players see what they already know, with every port citing the file it
> came from. You are credited in the app and the README.
>
> Two questions before it goes to beta: do you object to the clone, or to it
> being called the IronMON tracker inside the app, and do you mind the app
> bundling the type, status and badge icons and the sprite pack from your
> repository? I know the art is Nintendo's, not yours; I am asking about
> your files and your name. There will be an optional donate button;
> nothing is sold and nothing is gated. Beta page:
> willowcreek.group/kaizocore.
>
> Blake Porterfield
> blake@willowcreek.group

## 5. Brian0255 (NDS-Ironmon-Tracker, Gen 4 and 5). COURTESY, same question.

**Why:** GPL-3.0, no permission needed for the code. The app ports its
memory addresses, its Gen 4 and Gen 5 battle handlers and its ability-reveal
logic, and bundles its Gen 4 and 5 sprite icon set, its badge images and its
DPPt Kaizo settings file.

**Where:** an issue or discussion on github.com/Brian0255/NDS-Ironmon-Tracker.

**Message:**

> Hi. I am building KaizoCore, a free, open-source (GPL-3.0) Android IronMON
> app with a built-in DS emulator. The DS tracker in it is a Kotlin port of
> yours: the memory addresses, the Gen 4 and Gen 5 battle handlers and the
> ability-reveal logic, all read from your source and cited file by file.
> The APK also bundles your icon set for Gen 4 and 5, your badge images and
> your DPPt Kaizo settings file. You are credited in the app and the README.
>
> Do you object to any of that, and in particular to the bundled images? I
> know the art itself is Nintendo's; I am asking about your files and your
> name. It is going to a small beta at willowcreek.group/kaizocore, with an
> optional donate button and nothing sold.
>
> Blake Porterfield
> blake@willowcreek.group

## 6. UTDZac (official IronMON settings). COURTESY.

**Why:** the app bundles the settings strings from the official IronMON
settings page as presets, named after the rulesets. They are published for
players to use, but they are his work and the app names them.

**Where:** GitHub, on the settings gist or on his tracker repository, or the
IronMON Discord.

**Message:**

> Hi. I am building KaizoCore, a free, open-source Android IronMON app going
> to beta at willowcreek.group/kaizocore. It bundles the settings strings
> from your official settings page as ready-made presets (Standard, Kaizo,
> Ultimate, Survival and the rest, for every game the page lists, including
> the Gen 1 two-pass), so a phone can randomize without a PC. They are
> unmodified and the page is credited as the source. If you would rather
> they were fetched from the page than bundled, or credited differently, say
> the word.
>
> Blake Porterfield
> blake@willowcreek.group

## 7. Swordfish90 (LibretroDroid). OPTIONAL.

GPL-3.0 covers everything; no message is owed. The good-citizen move is a
pull request upstream with the patches marked "KaizoCore patch" (the sensor
interface, slow motion, the serialize guards, the RetroAchievements bridge).
That is a contribution, not a permission.

## 8. Nintendo, Game Freak, The Pokémon Company. NO MESSAGE.

There is nothing to ask for that would be granted. The answer to the
bundled sprites, icons and badges is to stop shipping them: decode sprites
from the player's own ROM at runtime (the decoder exists), redraw the type,
status and badge icons, and keep the standing rule that no ROM is shipped,
hosted or linked. That is section 0 of BETA-PLAN.md and it gates the public
build regardless of what anyone above says.

---

## After the replies

- Paste each reply verbatim into NOTICE under the party's entry with the
  date and URL, and tick the matching line in "Binding release conditions".
- If Cyan says no to bundling: switch the patches and presets to first-use
  download from his release page, keep the credit, and re-run the tests
  that check the bundled asset list.
- If RetroAchievements wants changes: they come before the beta's
  achievements button is enabled, not after.

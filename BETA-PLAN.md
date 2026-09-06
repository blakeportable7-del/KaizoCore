# KaizoCore: beta, feedback, support and promotion plan

Written 2026-09-06 at Blake's request. The pitch, in his words: the complex
way to play Kaizo IronMON and Pokémon ROM hacks is the problem, and this app
fixes it, so more people can take part. Everything below serves that one
sentence.

## 0. The decision that comes before any of it

KaizoCore was built as a personal, sideloaded build. Three things are true
of a personal build and stop being true the moment a stranger installs it:

1. **The app is GPL-3, whether or not it says so.** It links LibretroDroid
   (GPL-3) and vendors Universal Pokémon Randomizer ZX and the Nat. Dex fork
   (both GPL-3), and the DS tracker it clones is GPL-3 as well. Distributing
   the APK obliges you to offer the app's source under a compatible
   licence. There is no version of a public beta that keeps the source
   private. Recommendation: put the repository on GitHub under GPL-3 with a
   LICENSE file, keep the package id, and treat the open source as part of
   the pitch. Every emulator the community trusts (RetroArch, mGBA, melonDS)
   is open, and IronMON's own trackers are open. The Gen 3 trackers are MIT
   (Besteon), which only asks for attribution, already on the INFO tab.
2. **The bundled art is Nintendo's.** `assets/gbasprites` and
   `assets/gen4sprites` (8 MB of Pokémon sprites) and the type and badge
   icons are copyrighted game art. Fine on your phone; not fine in a file
   you hand out. The tracker already carries a `SpriteDecoder`, so the
   honest fix is to read sprites out of the player's own ROM at runtime and
   ship none; badges and types are small enough to redraw. This is a real
   engineering item, not a footnote, and it gates the public build.
3. **The Nat. Dex patch files** (`assets/patches`, 51 MB) are bundled under a
   permission CyanSMP64 gave for this app. Before a public build, confirm in
   writing that the permission covers redistribution inside the APK; if not,
   the app downloads them from CyanSMP64's release page on first use, with
   credit, which the INFO tab already gives.

Unchanged and non-negotiable: the app never bundles, hosts, links or fetches
a ROM, and the website and the feedback form say so and accept none.

## 1. The value proposition, for the page and the outreach

What a new player does today to play Kaizo IronMON on a phone: find a clean
dump, patch it on a PC, run the randomizer twice for Gen 1 or once with the
right settings string, copy the ROM over, install an emulator, install and
configure a tracker that only works on a PC, and stream two windows into
OBS. KaizoCore does all of it on the phone: the library checks the dump,
the patcher refuses a patch for the wrong game, the presets are the official
strings, the randomizer runs on the device, the tracker is a clone of the
PC one and reads the game live, and the stream server puts each screen into
OBS as a browser source. That is the story. It is told once on the page,
once in the video, and once in every message to a streamer.

## 2. Where it lives

- **A page of its own, not on willowcreek.group.** The agency site sells
  websites to plumbers; this is a community tool. A separate static page
  (Netlify, which you already run) with a short domain. Sections: what it
  is in three lines, the video, six screenshots, requirements (Android 8+,
  your own dumps, a phone with at least 3 GB RAM for DS), install steps for
  a sideloaded APK, the current build with its SHA-256 and date, known
  issues, the beta rules, feedback, support, credits and licence.
- **Builds on GitHub Releases.** Free, versioned, checksummed, and the
  source sits beside them, which the licence requires anyway. The page
  links the latest release; the app's INFO tab links the release page and
  shows its own version, which it already reads from the package.
- **Not the Play Store for the beta.** Play's review of an emulator that
  patches and randomizes Pokémon ROMs is slow and uncertain, and every
  update would wait on it. Sideload first; Play later if it earns it.

## 3. How testers report back

Three channels, cheapest first:

1. **In the app.** The INFO tab already has a crash card with SEND REPORT.
   Extend it to a SEND FEEDBACK button that composes the device model,
   Android version, app version, the game family in play (never the ROM),
   the last 200 lines of log and a free-text box, and hands it to the share
   sheet. No account, no server, works offline. One evening's work.
2. **A bug form on the page.** Netlify Forms, which the agency site already
   uses for the audit, 100 submissions a month free. Fields: device, Android
   version, game, what happened, what you expected, steps. No file uploads.
3. **A Discord server.** Every IronMON community lives there. A #kaizocore
   channel with a pinned "how to report" post and the release feed is where
   the real testing conversation happens. Ask a streamer to host the
   channel on their existing server rather than start an empty one.

Every report lands as a GitHub Issue with a template, so the backlog is
public and testers can see their bug get fixed. That visibility is the
recruiting tool.

## 4. Donations

- **Ko-fi** for one-off and monthly support: no platform fee on donations,
  PayPal and card, a page in ten minutes. **GitHub Sponsors** beside it
  once the repo is public: zero fees, and it sits on the source people are
  already looking at. Skip Patreon for now; it wants content tiers you do
  not have time to produce.
- Money goes to Willow Creek Group, LLC, not to you personally, and is
  income for tax purposes. Say what it funds: a test phone or two for the
  device matrix, the domain and hosting, and your time.
- **Never sell the app and never gate a feature behind a donation.** An
  emulator plus randomizer for Nintendo games that charges money is a
  different legal conversation. Free app, open source, optional support.
  A SUPPORT link on the INFO tab and on the page is the whole integration.

## 5. Screenshots and the video

- **Screenshots**: the emulator harness already drives the app by
  accessibility tree, so a scripted set is repeatable on every build:
  library with the shelves, the Play screen with the tracker docked, the
  save-state grid, the layout editor, the cheat list, the emulator settings
  page, the RetroAchievements dialog, cloud sync, and the OBS browser source
  in a desktop browser. 1080x2340 raw, then framed. Real gameplay in the
  frame means Pokémon on screen, which is the same as every emulator's
  store listing and every streamer's thumbnail; the sprites the APK ships
  are the problem, not the footage.
- **Video**: 45 seconds, phone-vertical for Shorts and Reels, a 16:9 cut
  with a device frame for the page. The click-through: open the library,
  add a dump, patch it, pick Kaizo, randomize, play with the tracker, die,
  NEW RUN, then the OBS view. Recorded with scrcpy on your phone rather
  than the emulator so the frame rate is real, cut with ffmpeg, title cards
  from the site's own type. No music with a licence question; a plain
  voice-over or captions.

## 6. Winning the streamers

The people in the list you gave me earlier in this session are the whole
beta. Ten of them, each with a community, is more useful than a thousand
downloads.

- **The ask is specific**: "install this, do one run on your phone, tell me
  what got in your way." Not "check out my app."
- **What they get**: a tool that lets their chat play along on a phone,
  which grows their community, and a credit on the page. Offer to add a
  "streamer preset" that bakes in their own house rules.
- **What you send**: a two-line message, the video, the page link, the
  Discord channel. Nothing longer.
- **What you do not do**: DM fifty people at once, post the APK in someone
  else's Discord uninvited, or claim compatibility with a game you have not
  booted. The owed hardware checks in OVERHAUL-PLAN (DSi, Gold, Yellow, the
  RetroAchievements login) come before the first message goes out.

## 7. Order of work

| Phase | What | Who |
|---|---|---|
| 0 | Licence decision, LICENSE file, repo public; sprite pack replaced by ROM-side decoding; Nat. Dex redistribution confirmed with CyanSMP64 | Blake decides; I build |
| 1 | Page, GitHub release with checksum, in-app SEND FEEDBACK and SUPPORT links, Netlify form, issue template | I build; Blake picks the domain |
| 2 | Screenshots and the video | I script and cut; Blake records the phone footage |
| 3 | Ko-fi and GitHub Sponsors accounts, Discord channel | Blake (accounts are his to create) |
| 4 | Ten streamers, one run each, two weeks of fixes | Blake sends; I fix |
| 5 | Public beta post | both |

Phase 0 is the gate. Nothing in phases 1 to 5 is worth doing on a build
that cannot legally leave your phone.

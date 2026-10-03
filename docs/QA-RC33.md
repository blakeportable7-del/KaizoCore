# rc33 QA (2026-10-01 to 2026-10-02, shipped)

## Shipped (2026-10-02)

GitHub release v1.0.0-rc33, marked Latest (the public repo's tag on its rc33 snapshot 2676d2fb; the private repo's
tag on master dc90023b, built from 4a248501), site deploy 6abf48fbe6c05b16d00d97f7 (rollback: 6abea82b3e06920bcb2cbaf2,
rc32's page). APK 125,425,196 bytes, SHA-256 3ee91227dd2175cb5b3902c918cce4329c4d8a3f57cb23c7c9a2f18f99dea86b.
Blake: "I'm good, push it and release".

The release check on that exact APK, on the emulator (AVD ironmon, Android 14):

| Check | Result |
|---|---|
| Installs over the rc33 debug build, data kept; versionName 1.0.0-rc33+4a248501, versionCode 42 | pass |
| Game Boy Advance: LeafGreen opens in Play; the staged wild battle shows the new tracker with the RAIN pill | pass |
| Game Boy: Crystal from the library opens in Play, the tracker on its new card | pass |
| DS: Platinum from the library opens in Play | pass |
| No crash, ANR or VerifyError in logcat at any step (PlayScreen sits at the verifier's limit) | pass |
| More > Check now reads the live latest.json: "You have the newest build." | pass |

The site, before the deploy: every gate of build_release.py passed, and the built folder against the live deploy's
files differed in exactly the release's own files (the page, the new APK in and the old one out, latest.json,
netlify.toml's 301 for the old APK) and sitemap.xml's dates. After it: the audit tool answers with a business,
10 functions deployed, the page links the rc33 APK twice, latest.json names rc33 (versionCode 42), the APK
downloaded from the site and from GitHub both hash to 3ee91227, and the old rc32 link 301s to the page. The GitHub
wiki was pushed with the rc33 notes and names rc33 as the current download.

## Not yet seen

- B to run in a real Game Boy Advance battle on a phone (the Game Boy one was proved on the emulator with Red).
- The audio fix heard on a phone (measured: -85 dB against the old -25 dB under 12 kHz on LeafGreen's output).
- The new tracker look on a phone (seen on the emulator only, portrait and landscape).

## Left for rc34

- The rc32 audit's open P1s (docs/RC33-P1.md), among them 58 and 59: while the stream is on, a device on the same
  network can flood the stream server and crash it.
- On DS games, out of a battle, SETUP still has a row to itself (the Gen 1 to 3 panel shares it with the route).

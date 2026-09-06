# IronMON One — the hours-out plan

**Written 2026-08-30, after billgreenwald granted permission (local-only build).**
Supersedes the schedule framing of the master map; the architecture and guardrails in
that map still stand. This document exists because the goal changed from "replace the
PC stack with one APK" to "Blake is playing tonight, with no PC in the loop."

## The reframe that makes hours possible

Months live in exactly one place: building an emulator. There is no mGBA Android port,
ironmon_emu's app source is not published, and its public `mgba-android-memapi` module
turns out to be a **TCP debug server** (localhost:7777, debug builds only, protocol:
`{uint32 addr, uint8 len}` → raw bytes) — a good design reference for our future
tracker bridge, not an embeddable core.

But Blake's phone already has a working emulator with a built-in tracker that handles
NatDex 1.2.x: **ironmon_emu itself.** What the phone stack is missing is everything
*around* it — and all of that is plain JVM code we have already built and tested.

**IronMON One v0.2 is the prep station; ironmon_emu is the play surface.**

```
IronMON One:  verify ROM -> patch -> pick Kaizo rnqs -> RANDOMIZE -> save .gba
ironmon_emu:  open that .gba -> play with tracker
New Run:      die -> switch app -> tap Randomize again -> reload. Seconds.
```

The PC is gone. That is the product promise, delivered by two apps instead of one.
The single-APK version stays on the master map as the long game.

## What already works (do not rebuild)

- CRC gate, header parse, BPS/IPS patcher — on the phone, in the current APK
- Recipe runner with stage caching — tested, 28 Kotlin tests
- rnqs matcher — validated against all 72 real files
- **ZX 4.6.1 engine compiled and running on JVM 21** — loads Blake's real
  `RSE Kaizo.rnqs`, round-trips settings strings
- Blake's Emerald is already NatDex-1.2.1-patched (`ebfdce4b`)

## The build, in order

### 1. Vendor the NatDex randomizer fork — sole engine, no relocation  (~1–2h)

Blake plays **Kaizo NatDex Emerald**. That needs `CyanSMP64`'s fork, not mainline ZX.
The package collision between the two forks only exists if both ship in one APK, so
**v0.2 ships the NatDex fork as the only engine** and the relocation problem is
deferred entirely. (Vanilla profiles keep using engine-zx on the JVM test side; the
app engine is the fork.)

- Clone `CyanSMP64/universal-pokemon-randomizer-zx`, pin to the commit matching the
  1.2.1 jar (verify, don't assume: its `Version.VERSION` must read Blake's
  `RSE NatDex v1.2 Kaizo.rnqs` — run the same `Settings.read` test that exposed the
  version-direction error on mainline).
- Same vendoring shape as `engine-zx`: flat `src` tree, resources on the classpath,
  nothing excluded, our tests in `test/`.
- GPL-3.0: keep `LICENSE.txt` and notices (Cyan's condition 4). Local-only use means
  no distribution obligations trigger, but the notices stay anyway.

### 2. Randomize screen  (~2–3h)

New tab: **Run**. Flow:

1. Pick the prepared ROM (Blake's existing `..._NatDex.gba`) — identified by CRC,
   refused if it is not a known NatDex build. `Profile.validate` already encodes the
   pairing rules; this screen is the first real consumer.
2. Pick settings: scan a SAF folder for `.rnqs`, run the validated matcher, present
   "RSE NatDex Kaizo" as the default with the other presets listed. Show the engine
   name in the header (brief §6).
3. Seed: random by default, visible, tappable to copy.
4. **Randomize** → engine runs on-device → save `Emerald_Kaizo_Run.gba` via SAF,
   rotating the previous file to `_PreviousAttempt.gba` (the recipe runner already
   hands the previous bytes back for exactly this).
5. Big obvious **New Run** button that re-runs with a fresh seed.

Engine-facing plumbing: ZX's API is file-based (`FileInputStream`, ini resources via
classpath). SAF gives streams, not paths, so copy the source ROM into `cacheDir`, run
the engine on real files there, and stream the output back out through SAF. `largeHeap`
is already in the manifest.

### 3. Prove it on the phone  (~1h, the only part that cannot be done here)

The one genuinely unproven step is **this fork on ART**. UPR-Android proves mainline
ZX randomizes fine on Android; the fork adds newer code that could, in principle,
touch a desktop-only API. The first on-device action is therefore the smallest
possible probe: load the rnqs, open the ROM, randomize, diff the output CRC against
two runs of the same seed (must match) and a different seed (must differ).

**Exit test for the whole plan:** phone in hand, no PC running — randomize, open the
output in ironmon_emu, see the tracker populate, reset, New Run, play again.

## What this plan deliberately does not do

- **No emulator work.** B-to-Run, BGM-locked turbo, and the single APK all live inside
  an emulator we now have permission to build on but no source for. They stay on the
  master map (D2 Branch A: libmgba + NDK + our JNI, with the memapi TCP protocol as
  the tracker-bridge reference). Months, honestly.
- **No vanilla engine in the APK** until the relocation work is paid for.
- **No editor screens yet.** Presets + rnqs files cover tonight; the reflection-driven
  editor inherited from UPR-Android is next after the loop works.

## Open items for Blake

1. Paste billgreenwald's actual wording into `NOTICE` (it still holds a placeholder —
   a grant we can't quote is a grant we can't rely on, local-only or not).
2. The APK upload to your phone failed over Remote Control earlier; the current build
   is at `app/build/outputs/apk/debug/app-debug.apk` on this machine.

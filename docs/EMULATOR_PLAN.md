# The emulator, repriced — from "months" to a phased few weeks

**Written 2026-08-30, superseding the schedule language in the master map and
HOURS_OUT_PLAN. Architecture (D2/D3, `core-api` interfaces) is unchanged.**

## The correction, owned

"Months" was priced against building a bespoke mGBA host: NDK toolchain, custom JNI,
our own render surface, audio out, input plumbing. That was the wrong build to price.
**LibretroDroid** (Swordfish90, GPL-3.0 — the library Lemuroid ships on) already *is*
the host: it loads a libretro core into an Android view and owns video, audio, input,
and save states. mGBA has a first-party libretro core with prebuilt Android binaries.
The earlier "libretro can't expose memory" objection confused two APIs: the limited
`retro_get_memory_data` versus `SET_MEMORY_MAPS`, through which the mGBA core publishes
its full memory map (`_setupMaps()`, 11 descriptors) to any frontend willing to catch
it. Catching it is a small patch to LibretroDroid's C++, not a subsystem.

What "months" never meant: romhack tooling. Patching, randomizing, settings editing —
all shipped in v0.3.0 already.

GPL-3.0 across the stack is already our license. Local-only build; notices kept.

## What we already hold, mapped to this build

| Asset | Status |
|---|---|
| `EmulatorCore` / `Tracker` interfaces | in `core-api` since commit 1 — LibretroDroid gets wrapped, not exposed |
| Gen III decoder (decrypt, 24 orderings, IVs, shiny) | **written and tested** (`tools/PokemonDecoder.java` → Kotlin port is mechanical) |
| Tracker architecture spec | `docs/TRACKER_SPEC.md` (poller cadence, reader decomposition, gotcha list) |
| NatDex detection mechanism | known: magic u32 at `0x08000170` == 1258, pointer table ~`0x08000150` |
| NatDex 1200+ species/move tables + sprites | **granted by Cyan**, extractable from the NatDexExtension Lua |
| Randomize + New Run in-app | shipped (v0.2.0) — in-app emulator removes even the export step |
| Touch pad (RadialGamePad), BT controller via KeyEvents | surveyed, GPL-compatible |
| Audio stretch (SonicAudioProcessor) | surveyed, Apache-2.0, standalone |

## Phases — each ends with something Blake can run

**E1 — It plays. (~1–2 build sessions)**
`emu-mgba/` module wrapping LibretroDroid + the mGBA core behind `EmulatorCore`.
Play tab: load the current run from PrepStore (no export step anymore), touch overlay
via RadialGamePad, Bluetooth controller passthrough, battery saves + save states in
app-private storage, fast-forward with **audio muted during turbo** (the sanctioned
BgmLock fallback; never pitch-up). Exit test: current Kaizo run boots, plays, saves,
survives process death; controller works; 2x doesn't chipmunk (because it's silent).

**E2 — The app can see the game. (~1 session)**
Patch LibretroDroid: capture `SET_MEMORY_MAPS` descriptors, expose
`readMemory(addr, len)` over JNI. Wire to `EmulatorCore.readMemory`. Exit test: read
the ROM header and `gBattleTypeFlags` live; values match RomTool's static read.

**E3 — Tracker MVP. (~2–4 sessions; the long pole)**
Port the decoder to Kotlin (mechanical), vanilla Emerald address table from the MIT
Lua tracker, 250ms poller per TRACKER_SPEC, My-Mon panel first. Then NatDex: magic
check, pointer-table addresses, and the 1200+ species/move tables scripted out of the
NatDexExtension Lua into Kotlin/JSON (a parsing script, not hand entry). Exit test:
**differential** — same save in our app and stock ironmon_emu, values match. Blank
tracker = blocked release, per spec.

**E4 — The loop closes in one app. (~1 session)**
New Run button on the play screen + A+B+Start combo via `injectInput`; rotates
PreviousAttempt, re-randomizes (already in-app), reloads the core. **B-to-Run**
emulator-side per the map's §6 proposal: on B in the wild-battle action menu (bit 3
of `battleTypeFlags` — bit 3, not bit 0), inject cursor-to-Run + confirm. Flee
formula untouched; off-toggle for strict rulesets. Trainer battles and overworld
hold-B unaffected.

**E5 — Polish. (~1–2 sessions)**
Pitch-locked turbo option via SonicAudioProcessor (music fast but never squeaky),
enemy + area tracker panels, view modes (integer/fit/stretch), remap screen for touch
AND physical buttons (controllers report A/B swapped versus GBA layout), tracker
side/bottom choice, hide-tracker fullscreen.

Sessions ≈ one sitting of building here plus one on-device test round from Blake.
Calendar: **~1–3 weeks at this pace**, dominated by E3 and by device-test turnaround,
not by code volume. That is the honest replacement for "months".

## Risks that survive the reprice

1. **LibretroDroid API fit** — speed control, audio mute, and programmatic input
   injection need to exist or be patchable. Verification in flight; E1 confirms.
2. **Core sourcing** — prebuilt mGBA core per ABI (buildbot or Lemuroid's artifacts)
   vs building it ourselves with the NDK. Either works; one is an afternoon slower.
3. **E3 data volume** — 1200+ species tables must be *scripted* out of the Lua, with
   spot-checks against the tracker on stream/reference values. Hand entry is banned.
4. **Differential oracle** — ironmon_emu stays installed as the correctness reference
   for the tracker throughout.

## What stays true from the old plan

The prep station (v0.3.0) is complete and none of it gets redone: the play screen
consumes PrepStore's current run, `Profile`/`Recipe` guardrails carry over, and the
editor is already engine-exact. The two-app loop keeps working the whole time; each
phase only shortens it.

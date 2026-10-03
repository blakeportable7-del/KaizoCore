# KaizoCore

Formerly IronMON One. Package id is still `com.ironmonone.app`; renaming it would
make a new app to Android and orphan every save.

One Android app replacing the PC stack: BizHawk + GBA IronMON Tracker + Universal
Pokémon Randomizer ZX + NatDex extension + web patcher.

Specification: `IRONMON_ONE_BRIEF.md` (what to build, non-negotiable rules).
Architecture: `IRONMON_ONE_MAP.md` (how, with verified reference-repo findings).

**No ROM files in this repository, ever.** The user supplies legally obtained dumps.


## Beta

Download the APK from https://willowcreek.group/kaizocore (or the releases page here); the SHA-256 is on both. Install a newer APK over the old one; uninstalling wipes your games, saves, notes and attempt count. You need your own game dumps: nothing here contains or fetches a ROM. Home, ROM Hacks lists the hacks made for your game and opens each one's own page for its patch.

Your first run: Library, My games, Add files, pick your dump (a zip is fine). Home, Kaizo IronMON, pick the game and a mode, Start attempt 1. Play. Library, Patched versions is only for Nat. Dex on Emerald or FireRed v1.1 and the patched rulesets. Bugs: More, Backup and info, Report a bug, Email Blake.

## State as of 2026-08-30

**Milestones 1 and 2 are done and tested, and the toolchain is installed.**
JDK 21.0.12 LTS, Android SDK (platform-tools, android-34, build-tools 34.0.0,
licences accepted) at `C:\Users\bepor\Android\Sdk`, Gradle 8.7 with a wrapper in the
repo. `core-api` compiles and its tests pass. The NDK was deliberately **not** installed
then; it is now required: NDK 26.1.10909125, for libretrodroid's native code and for the
strip step that keeps its debug info out of the APK (app/build.gradle.kts ndkVersion).

```
core-api/        pure Kotlin, zero Android deps   — 10 tests green
core-patch/      crc32, BPS, IPS                  — 10 tests green
core-recipe/     recipe runner + stage cache      — 8 tests green
tools/           Java reference impls + decoder   — 24 tests green
emu-mgba/        libmgba + NDK + our own JNI (no official port)  — not started
tracker-gba/     native tracker behind Tracker    — spec'd, see docs/
engine-zx/       ZX 4.6.1 source subtree, headless               — not started
engine-natdex/   CyanSMP64 fork @1.2.1, package-relocated        — not started
app/             Compose shell: ROM library + About — BUILDS, untested on device
nds/             stubs only, must stay empty in v1               — not started
```

Build a debug APK (lands in `app/build/outputs/apk/debug/`). A release is built only by
`tools/release.sh`, and docs/RELEASING.md is the whole release checklist. The versionCode
moves only in the commit you release: a build made after the bump carries the release's
code, and a phone on it is never offered the release (release.sh refuses otherwise).

```bash
./gradlew :app:assembleDebug
```

Run the tests:

```bash
./gradlew test
```

```bash
cd tools && javac -d out *.java && java -cp out RomToolTest && java -cp out SettingsMatcher C:\PokemonIronmon && java -cp out PokemonDecoderTest
```

Set `JAVA_HOME` to the **JDK 21** install, not the system JDK 25: Gradle 8.7 does not
run on 25.

What is proven, not assumed:

- CRC-32 matches the standard check vector, so the gate itself is right.
- BPS applies all four opcodes correctly, and refuses a wrong-CRC source, a
  wrong-size source, and an output that misses its declared checksum.
- IPS handles plain records, RLE runs and the optional truncation field.
- The settings matcher parses **all 72 real `.rnqs` files** on this machine (52
  distinct names) and resolves each v1 profile to exactly one file. Super Kaizo never
  satisfies a request for Kaizo, and no vanilla file can reach a NatDex profile.
- Blake's Emerald is already correct Nat. Dex 1.2.1 (`ebfdce4b`). There is **no clean
  Emerald dump and no FireRed** here, so BPS apply is verified against synthetic
  fixtures rather than the real patch.
- The Gen III decoder unpacks species, moves, item and level across four different
  substructure orderings. Its permutation table is **generated from the canonical
  ordering rule, not transcribed**, then checked against the published GAEM / GAME /
  MEAG rows, so a silent typo in 24 rows of digits is impossible.
- Shiny, nature, gender and the IV bit-packing are each asserted against **independent**
  hand-computed vectors, not against the test's own encoder. A round-trip alone would
  only prove the encoder and decoder share an assumption.
- `GbaHeader` parses the **real retail ROM** and its GBATEK checksum validates:
  `POKEMON EMER / BPEE / maker 01 / v1.0`. Nat. Dex leaves the header untouched, which
  is why CRC-32 remains the only way to identify a patched ROM.

- The Kotlin patcher reads the **real 26MB Nat. Dex 1.2.1 patch** and independently
  reproduces both checksums the app hardcodes. Its failures are written for a person:
  a wrong dump says which copy it needs, a wrong size adds "that usually means the file
  is a different game, or already patched".
- **New Run re-runs only randomization.** Proved by counting calls, not by comparing
  output: the second run reports the patch stage as a cache hit while the randomizer
  runs exactly once more. Adding a QoL toggle re-runs the new stage and still hits cache
  for everything upstream of it.
- **Randomizing a randomized ROM is unrepresentable.** Randomization always consumes the
  prepared ROM, so the same seed twice is byte-identical rather than drifting.

**Verified on Android (emulator, API 34, 2026-08-30) — the whole loop, driven by adb:**
SAF pick → CRC gate identified the NatDex ROM green → Prepare stored it → rnqs import
labeled "RSE Nat. Dex Kaizo" by the matcher → **Randomize ran on ART** ("New run ready
(seed 7ff78351c808b73f)", no crash) → output pulled via run-as: exactly 32MB, valid
GBA header, fresh CRC. Also closed with Blake's clean dumps: the **real Emerald BPS
apply** (clean `1f1c08fb` → `ebfdce4b`, byte-identical to the known-good build), and
the FireRed gate correctly **refusing a v1.0 dump** (`dd88761c`; the patch needs v1.1
`84ee4776`).

Still unproven, and honestly so: the party-structure offsets and the tracker's address
tables need a differential test against a live save once the emulator exists (see
`docs/TRACKER_SPEC.md`), and real-phone behaviour (performance, screen sizes) versus
the x86_64 emulator.

## Next three actions

1. Install Android Studio. It bundles its own JDK; **do not point the build at the
   system JDK 25** without checking that the Android Gradle Plugin supports it.
2. Fork `billgreenwald/ironmon_emu` (permission secured 2026-08-30) and stand its
   mGBA core + `mgba-android-memapi` up behind `EmulatorCore`.
3. `core-patch`: BPS applies the NatDex patch and its embedded CRC-32s become the
   verification gate.

## Credits

Nat. Dex support uses the Pokémon and move data and sprites from the **Nat. Dex
Extension** by **CyanSixFour / CyanSMP64**, used with the author's permission:
https://github.com/CyanSMP64/NatDexExtension

Faster FireRed, the FireRed quality-of-life patch bundled for Library, Set up a game, is by DrMaple:
https://github.com/DrMaple/Faster-FireRed

Tracker constants and behaviour derive from the **IronMON Tracker** (MIT) by besteon
and contributors: https://github.com/besteon/Ironmon-Tracker

**This is not an official IronMON or Nat. Dex release.** It is an unaffiliated,
unendorsed community project.

## Licensing

GPL-3.0. Forced by the ZX randomizer and UPR-Android, both GPL-3.0, whose source is
compiled into the APK. mGBA is MPL 2.0 and compatible via section 3.3.

**No Pokémon ROM is shipped, hosted, or linked, ever.** Users supply their own legally
obtained dump and patch it in-app with the author's published BPS. This is both a
project rule and a condition of the Nat. Dex permission.

See [NOTICE](NOTICE) for the full component list, the permission grants verbatim, and
the binding release checklist.

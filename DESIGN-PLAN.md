# KaizoCore — UI/UX Overhaul Plan

Audited 2026-08-31 against live screenshots of every screen (portrait + landscape,
all six tabs, tracker cards via the render harness, a full live run to game over).
Critique is written to market-product standard on purpose, even though this ships
to exactly one person.

---

## 0. Ground rules for the implementer

These are not suggestions. Each one exists because it was violated once already
and cost a round of rework.

1. **The tracker panel is a clone, not a design surface.** Its interior changes
   only with a citation into `~/ironmon-ref/Ironmon-Tracker/ironmon_tracker/`
   (file:line in the code comment). The invented-then-deleted list already
   includes: PP as `cur/max`, dot/ring category icons, tinted stat rows, a
   joined ability line, an enemy HP readout, a Heals colour ramp. Do not add
   an entry to this list.
2. **No change lands without a rendered screenshot.** The harness exists:
   `TrackerLookTest` renders the panel at 221dp (landscape pane) and 393dp
   (portrait) and writes PNGs to the app's `filesDir`; `adb exec-out run-as
   com.ironmonone.app cat files/<name>.png` pulls them. For shell screens,
   screenshot the emulator (`screencap` + pull). Verify by looking, and by
   measuring where a number is claimed (the contrast script from this audit is
   in the session scratchpad; reproduce it — it is ~15 lines of PIL).
3. **135 unit tests stay green**, including the glyph-vs-Constants.lua diff and
   the sprite-pack consistency check. `gradlew test` before every
   `assembleRelease`.
4. **NOTICE is binding.** Nothing in this plan requires new third-party assets;
   keep it that way.
5. **Release builds sign with the debug key.** Installing the androidTest pair
   for the harness, then reinstalling release, preserves app data. Uninstall
   `com.ironmonone.app.test` when done.

---

## 1. Audit findings

### 1.1 The app has three visual languages, and only two are on purpose

- **GBA shell** (deliberate): paper `#F8F8F8` cards on black, hard 0–2px
  corners, pixel type (Press Start 2P) for buttons/tabs, yellow accent on dark.
  Seen on PREP / RUN / ROMS / INFO cards and the portrait pad.
- **PC tracker clone** (deliberate, untouchable): `#222` boxes, `#AAA` hairlines,
  Roboto Condensed at reference metrics.
- **Default Material** (accidental): stock radio buttons on RUN, stock list rows
  and body type on KEYS, stock dialogs, stock text-button "RESET TO DEFAULTS".
  This third language is noise. Every instance of it should die.

The single highest-leverage design move is: **one shell, one instrument.**
Everything that is not the tracker is GBA shell; the tracker is the instrument
mounted in it; nothing is stock Material.

### 1.2 Information architecture fights the actual flow

Six flat tabs — PREP · RUN · PLAY · ROMS · KEYS · INFO — for a flow that is
strictly linear: *get a ROM in → prepare a base → pick settings → randomize →
play*. Specific failures, all observed:

- **PREP and ROMS are the same job split in two.** Both exist to get a ROM into
  the app. Worse, they disagree: after preparing FireRed, PREP shows
  "Pokémon FireRed (U) v1.0 — 16 MB" while ROMS says **"No ROMs yet."** Two
  stores, two truths, zero explanation. A first-time user cannot tell which tab
  they are supposed to feed.
- **RUN's outcome lands below the fold.** "New run ready (seed …). Play tab…"
  renders at the very bottom of a scrolling column, off-screen at the moment it
  matters. The user taps NEW RUN and sees nothing happen.
- **NEW RUN itself is clipped** by the nav bar until you scroll (screenshotted).
  The primary action of the whole app is half-hidden.
- **Randomizing takes 25–45 seconds with no progress UI at all.** The screen
  sits frozen. This is the app's longest operation and its only feedback is a
  status string you can't see (see previous point).
- The attempt counter — the emotional core of IronMON — appears nowhere except
  inside the tracker during play.

### 1.3 Measured defects

| Defect | Measured | Standard |
|---|---|---|
| INFO links: pure `#FFFF00` on `#F8F8F8` paper | **1.01 : 1** | 4.5 : 1 |
| ROMS empty-state hint: `#404040` on black | **2.03 : 1** | 4.5 : 1 |
| Portrait with party loaded: game 710 + tracker 736 + pad 860 + chrome | **~260 px over** a 2340 px screen | fits |
| Landscape chip strip (1X · S1 · SAVE · LOAD · NEW · SOUND · CAM · PAD · MENU) | ~40 dp tall targets, 9 chips, no grouping | 48 dp min |
| RUN settings list | two entries both named "FRLG Kaizo", distinguished only by filename subtext | unique names |
| Tracker width drag handle | a 2-px `‖` glyph, no affordance, tiny grab area | discoverable |

The yellow-on-paper number deserves emphasis: **1.01:1** means the link is
luminance-invisible and survives on hue alone. Same failure class as the ghost
button the Willow Creek audit caught at 1.08:1.

### 1.4 Two different game pads

Portrait: opaque paper-white framed buttons below the game, plus a `HIDE PAD`
pixel button. Landscape: translucent dark squares overlaid on the game. Same
verbs, two unrelated designs. The portrait pad is also the direct cause of the
portrait overflow — it demands its own 860 px band instead of overlaying.

### 1.5 States

- **Empty states**: copy is genuinely good ("Wrong revisions are rejected rather
  than quietly half-working") but rendered at 2:1 contrast it reads as disabled
  UI, not guidance.
- **Loading**: nonexistent (the 40 s randomize, ROM prepare, patching).
- **Error**: the "TRACKER CANNOT READ THIS ROM" card is the best screen in the
  app — plain statement, reason, diagnostics for a screenshot. It is the model
  the loading/empty states should be held to.

### 1.6 Feedback, motion, feel

- No haptics anywhere — a GBA pad with dead thumbs.
- No press states beyond Compose defaults on pixel buttons.
- Tab switches hard-cut; no transition anywhere in the app.
- No splash; status bar is default black regardless of screen.

### 1.7 Accessibility

- Shell text ignores nothing (uses `sp`) but the pixel font below ~10sp is
  illegible at 1.0× and worse at large font scales; no `contentDescription` on
  icon-only controls (chip strip, ball picker, HIDE); TalkBack never attempted.
- The tracker deliberately ignores font scale (reference metrics) — that is
  correct and stays; the shell is not exempt.

---

## 2. Strategy — "One shell, one instrument"

**Design stance:** the app is a piece of *hardware*: a GBA-era shell with a
modern instrument (the PC tracker) bolted into it. Paper, ink, hard corners,
pixel labels. The tracker keeps its own black-box livery because it is a
faithful clone of a real tool — the contrast between shell and instrument is a
feature, not a bug. Everything Material-by-accident gets rebuilt in shell
language.

### 2.1 Tokens (define once, in one file)

```
Color:
  ink        #1C1C1C   text on paper
  paper      #F8F8F8   card ground
  frame      #333333   card frames, button frames
  night      #000000   app ground
  accent     #FFEB00   ONLY on dark grounds; never on paper
  linkOnPaper -> ink + underline (links on paper are ink, underlined)
  hint       #9E9E9E   minimum for de-emphasized text on black (4.6:1)
  danger     #E53935 on paper / #FF6B60 on black

Type:
  pixel  (Press Start 2P)  — labels, buttons, tab bar; NEVER below 10sp; never prose
  body   (Roboto)          — prose, list rows, dialogs
  tracker (Roboto Condensed) — tracker only, reference metrics, off-limits

Shape:   0dp radius, 2dp frame stroke (matches existing cards)
Spacing: 4dp grid; 16dp card padding; 48dp min touch target
Motion:  120ms crossfade standard; 0ms when reduced-motion
```

### 2.2 Component kit (build these, then forbid ad-hoc UI)

`PaperCard` · `PixelButton` (primary = ink-on-paper frame, secondary = paper-on-
ink, destructive = danger frame) · `ShellListRow` (replaces Material rows on
KEYS/RUN) · `ShellRadio` (pixel-style selector, replaces stock radios) ·
`StatusBanner` (anchored feedback, replaces bottom-of-page status text) ·
`ProgressPanel` (framed card: stage label + percent or indeterminate marquee in
pixel style) · `ShellDialog` (framed paper panel, replaces stock dialogs).

---

## 3. IA restructure

**Recommended: four tabs.**

```
RUN                      LIBRARY                 PLAY        MORE
─ current-run card       ─ ADD A ROM (one door)  ─ game      ─ controls (KEYS)
  seed · attempt · game  ─ prepared bases          + tracker ─ about (INFO)
─ settings picker        ─ raw ROMs list
─ NEW RUN (sticky)       (one screen, one truth)
─ progress / status
```

- **LIBRARY merges PREP + ROMS.** One "ADD A ROM" door. The list shows raw ROMs
  and prepared bases as states of the same object ("FireRed (U) v1.1 —
  prepared ✓ / tap to prepare"), which kills the two-truths problem outright.
- **RUN becomes a dashboard.** Current-run card on top (seed, engine, attempt
  count — finally surfaced), settings below with unique display names, NEW RUN
  pinned in a sticky footer so it is never clipped, `ProgressPanel` replacing
  the frozen 40 seconds, `StatusBanner` for outcomes.
- **MORE absorbs KEYS + INFO** — neither is a daily destination.

**Cheaper fallback** (if the merge is too invasive): keep six tabs, but (a) put
a numbered step chip on PREP/RUN ("STEP 1 · PREPARE"), (b) make ROMS list
prepared bases too, (c) sticky NEW RUN + StatusBanner + ProgressPanel anyway.
Items (c) are mandatory in either variant.

---

## 4. Work orders

### P0 — correctness and usability (do first)

| # | Work | Done means |
|---|---|---|
| 1 | **ProgressPanel on randomize/prepare.** Stage text at minimum ("Patching… / Randomizing… / Verifying…"); the engine already logs stages. | Screenshot of mid-randomize showing the panel |
| 2 | **Contrast repairs, then sweep.** Links on paper → ink+underline; hints → `#9E9E9E`+; then run the contrast script over a screenshot of *every* screen against 4.5:1, tracker excluded (it matches the reference's own palette). A colour fixed in one file is not fixed — sweep, don't spot-fix. | Script output table, all ≥4.5:1 |
| 3 | **One pad, both orientations.** Adopt the landscape translucent overlay pad in portrait, overlaid on the lower half of the game surface. This kills the 260px overflow *and* the two-pad split in one change. Keep the ships-visible rule: pad renders without JS/state, opacity only ever added. | Portrait screenshot: game + full tracker + pad, no scroll |
| 4 | **Sticky NEW RUN + StatusBanner on RUN.** | Screenshot: button visible at page open; banner visible after tap |
| 5 | **Chip strip: 48dp targets, grouped** (system: 1X·SOUND·CAM·PAD·MENU right; session: SAVE·LOAD·S1·NEW left), `S1` labelled "SLOT 1". | Screenshot + tap-target measure |

### P1 — coherence

| # | Work |
|---|---|
| 6 | De-Materialize: `ShellRadio` on RUN, `ShellListRow` on KEYS, `ShellDialog` everywhere, pixel RESET button. Grep for `androidx.compose.material3` usages outside the component kit and drive to zero on user-visible surfaces. |
| 7 | Settings picker: display names from the `.rnqs` (or a curated map) — never two identical labels; selected entry shows a one-line summary (game + tier). |
| 8 | Tracker drag handle: 24dp grab strip with a visible ridge; double-tap toggles collapse. |
| 9 | Empty states at full contrast with a consistent one-glyph illustration (pixel pokéball outline), modeled on the unreadable-ROM card's tone. |
| 10 | Attempt counter on the RUN dashboard card (already in P0-4's card layout — this item is wiring the real number). |

### P2 — feel

| # | Work |
|---|---|
| 11 | Haptics: light tick on pad press (`CONFIRM`), heavy on game over (`REJECT`). Respect system haptics setting. |
| 12 | Motion: 120ms tab crossfade; PLAY resume slide-up; all gated on reduced-motion. Never gate *content* on an animation completing (paid-for rule: a stalled animation clock must strand nothing). |
| 13 | Chrome: status-bar colour per screen (black over shell, black over play), proper splash (pixel logo on black), launcher icon consistency check. |
| 14 | Press states on `PixelButton`: 1px inset shift + frame darken (GBA button feel), no ripple. |
| 15 | Accessibility pass: contentDescriptions on all icon-only controls; TalkBack walk of LIBRARY→RUN→PLAY; verify shell at fontScale 1.3 (tracker exempt by design). |

### Explicitly out of scope

- Any change inside the tracker panel not backed by a reference citation.
- New features (log viewer, GachaMon, stats screens) — this is polish, not scope.
- Engine, decoder, or battle-state code.
- Anything that adds a bundled asset (NOTICE).

---

## 5. Verification protocol (per milestone)

1. `gradlew test` green (135+).
2. Render harness: tracker cards at 221dp and 393dp — pixel-diff against the
   previous accepted PNGs; any diff must be explained by an intended change.
3. Emulator sweep: screenshot all tabs portrait + PLAY both orientations at
   1080×2340; eyeball each; run the contrast script on each.
4. Release build, install over existing (debug-key continuity), open every tab
   once on device. The "it built" state is not the "it works" state — this app
   has already shipped one APK where the fix demonstrably wasn't in it.
5. Anything gated on geometry or visibility: read geometry fresh, never latch
   (paid-for rule from the hero mock).

## 6. Sequencing

- **M1** = P0 items 1–5. One session. Highest user-visible payoff; portrait
  becomes actually playable with the tracker up.
- **M2** = P1 items 6–10. The de-Materialize pass is mechanical once the kit
  exists; build the kit first (tokens + components), then migrate screen by
  screen: RUN, KEYS, dialogs, LIBRARY.
- **M3** = P2 items 11–15, plus the milestone-3-only luxury of revisiting
  anything M1/M2 shipped rough.

Each milestone ends with the full §5 protocol and an APK in
`C:\Users\bepor\Downloads\IronMON-One.apk`.

---

*Also on the books, unrelated to design, still open from the last session:*
*live verification of the pokéball row and `Last seen Lv.N` against a real*
*trainer battle; a DS-tracker render after the shared-composable changes;*
*Nat. Dex end-to-end on device. M1's device pass should fold these in.*

---

## 7. Resources vetted for this work (2026-08-31)

Recorded so nobody relitigates. Honesty note on method: rejections grounded
in PROJECT facts (vendored engine source, console scope, toolchain pins —
all verified in this repo) hold regardless of repo contents. Where a verdict
depended on the repo itself, the entry below says whether it was READ or
inferred; anything marked unverified must be read before it is relied on.

**Use:**
- `chrisbanes/skills` — VERIFIED BY READING, not just the README:
  `compose-component-design/SKILL.md` is ~400 words of real heuristics (slots
  over boolean flags, caller modifier at root, component owns invariant
  structure / caller owns placement) with no embedded commands or fetches.
  Take these four for M2: `compose-component-design`,
  `compose-state-and-effects`, `compose-performance`,
  `compose-ui-testing-patterns`. Only the first was read in full — read each
  of the other three before copying it into `~/.claude/skills/`; that per-file
  read is Opus's job at install time. Do NOT bulk-install the repo.

**Skim as reference only, no dependency:**
- `rafaelvcaetano/melonDS-android` — bookmark only. The "best layout editor
  pattern" claim is from the recommendation list, NOT verified by reading the
  repo; nothing in M1-M4 depends on it. Verify before citing it as a pattern.

**Rejected, with reasons:**
- `yschimke/compose-ai-tools` — does exactly what our render harness does,
  plus layout trees, and would be the better tool on a fresh project. But it
  requires Kotlin 2.0.21+/AGP 8.13+/Gradle 8.13+; this project is Kotlin
  1.9.24 / AGP 8.3.2 / Gradle 8.7 with a vendored NDK build. A triple
  toolchain migration to replace a working harness is a bad trade during a
  polish milestone. Revisit only if the toolchain upgrades for its own reasons.
- All randomizer/.jar reverse-engineering repos and Apktool/decompile skills —
  moot: the randomizer (ZX 4.6.1 + NatDex fork) is VENDORED AS SOURCE in this
  repo under GPL-3.0. There is nothing to decompile.
- GB/GBC emulator repos (gbcc, GameDroid, gameroy, docboy) — wrong consoles;
  the app is GBA + NDS, and this plan explicitly adds no new features.
- `SeedlessDS` — VERIFIED, and worse than first recorded: it ships a
  prebuilt proprietary `libdrastic_arm64.so` (DraStic was never open-sourced)
  and the frontend itself has no licence ("not chosen yet"). Exactly the
  provenance this project's NOTICE discipline exists to keep out.
- Material Design 3 skills — actively counter to the plan. M2's entire job is
  removing accidental Material, not deepening it.
- Emulator-automation skills (`android-emulator-skill`, `agent-device`) — the
  adb workflow in §5 already does launch/tap/screenshot/logcat directly, is
  proven across two live sessions, and adds no dependency.
- `dpconde/claude-android-skill` (Clean Architecture) — an architecture
  refactor is explicitly not in scope for a polish pass; the module split
  (app/tracker-gba/tracker-nds/engine/editor) already works.
- `android/skills` (official collection) — same rule as chrisbanes: read the
  individual SKILL.md, copy only what M2 needs, never bulk-install.
- `nds4droid` / `ANDSemu` / `NooDS` — the DS core decision (melonDS via
  LibretroDroid) is made and shipped; alternative cores are not a UI question.
- `randomize.sh` / `TCGRandomizer` / `kotlinds/pokemon-map-randomizer` — same
  verdict as the other randomizer repos: the engine is vendored as source;
  there is no logic left to reverse-engineer, and none of these are ZX-
  settings-compatible references for the M4 editor. The M4 dependency map
  comes from the vendored `newgui/` panel source, nothing external.

---

## 8. M4 — The settings editor ("make my own rnqs")

Added 2026-08-31 after auditing the existing editor live. This is the one place
the app grows capability rather than polish, and it gets its own milestone
AFTER M1 (P0) so the shell kit exists to build it from.

### 8.1 What exists, and what is wrong with it

`EditorScreen.kt` (302 lines) + `SettingsReflector` surface 141 options in 8
accordion sections, reflected straight off the engine's `Settings` class.
Screenshotted live. It works — load, edit, Save As, copy/paste settings
string, correct engine detection — and it is not usable for real authoring:

1. **No dependency logic.** "Ban bad abilities" is editable while
   "Abilities mod" is set to Unchanged — a no-op the UI happily accepts. The
   desktop UPR gates every dependent control on its master radio; the
   reflector's flat alphabetical field order even puts dependents ABOVE their
   master ("Abilities follow evolutions" renders before "Abilities mod").
2. **No per-game filtering.** "Abilities follow mega evolutions" is offered
   while editing FireRed — a Gen 3 game. The engine exposes per-ROM support
   checks; the UI ignores them, so options that do nothing look like choices.
3. **Enum sprawl.** Every Choice renders as a stacked radio list; a 2-value
   enum eats ~230px. ZX has enums with up to 22 constants — as radios that is
   a full screen of one option.
4. **The action row scrolls off-screen.** SAVE AS · COPY · PASTE · [clipped].
   The fourth button is invisible unless you know to swipe the row.
5. **No descriptions.** 141 bare labels ("Assign evo stats randomly") with no
   explanation — while the vendored ZX source ships **237 official tooltip
   strings** in `newgui/Bundle.properties`. They exist; the UI just never
   reads them.
6. **No diff visibility.** "0 change(s)" in the header is the only dirty
   state. Nothing marks WHICH rows differ from the loaded preset, and there is
   no per-row revert.
7. **No search** across 141 options.
8. **Stock Material everywhere** — switches, radios, cards, dialogs. The
   whole screen is the "third language" §1.1 condemns.

### 8.2 Design

**Stance: an authoring tool, not a form dump.** Nobody builds an IronMON
settings file from nothing — the gist presets are the base and the user's own
file is a *deviation* from one. The editor's job is to make deviations easy to
create, see, and trust.

- **Always start from a preset.** Entry point is "EDIT A COPY" on the RUN
  settings picker; the title is "FRLG Kaizo → my-tms.rnqs". Bundled presets
  stay immutable (already true; keep it).
- **Master-first sections with gating.** Within a section: the mod enum first,
  its dependents indented beneath, disabled (40% ink, not hidden) unless the
  enum value makes them meaningful. The dependency map is CURATED FROM THE
  DESKTOP UPR PANEL SOURCE in `.vendor/upr-zx-461/src/.../newgui/` — cite the
  component per mapping, do not guess relationships.
- **Per-game visibility.** Hide options the loaded engine+game cannot use
  (megas on Gen 3, etc.), sourced from the engine's own support checks. When
  in doubt, show-and-gate rather than hide, with a "not used by FireRed" note.
- **Enums**: ≤3 values → segmented shell control on one row; >3 → row showing
  the current value, opening a `ShellDialog` picker.
- **Ints**: stepper row with range and unit; clamp to what the engine accepts.
- **Diff badges + revert.** Changed rows get an accent dot; section headers
  show "(3 changed)"; trailing revert glyph per changed row; sticky footer:
  `SAVE AS` + "12 changes vs FRLG Kaizo". COPY/PASTE/RESET move into an
  overflow — killing the clipped action row.
- **Descriptions from the horse's mouth.** Map Settings field → GUI component
  key → `Bundle.properties` tooltip (curated table, ~141 rows, mechanical).
  Tap a row's label to expand the official one-liner. Where no tooltip exists,
  show nothing — do not write lore.
- **Search** field above the sections, filtering all 141 by label/description.
- **Round-trip guard.** After Save As, re-read the file with the same engine
  class and diff against the in-memory settings; refuse silently-lossy saves.
  (Same class of check as the served-stylesheet read-back rule.)
- Built entirely from the M2 shell kit: `PaperCard`, `ShellRadio`/segmented,
  `ShellDialog`, `PixelButton`, `StatusBanner`.

### 8.3 Phasing

- **E1 (core):** shell-kit rebuild, master-first ordering + dependency gating,
  enum pickers, sticky footer, diff badges + revert. This alone makes authoring
  real.
- **E2:** search, tooltip descriptions, int steppers with ranges.
- **E3:** per-game visibility filtering.

Each phase: screenshot the section it touched, plus one full authoring pass on
device — edit FRLG Kaizo, save, randomize with the saved file, boot the run.
The output `.rnqs` being accepted by the engine and producing a bootable ROM is
the acceptance test; a pretty editor that emits a broken file is worse than the
current ugly one that emits a good file.

### 8.4 On the voliol/UPR releases link

Engine swap is **out of scope and dangerous**: the standing constraint is
never mix randomizer engines — vanilla ROMs go through the vendored ZX 4.6.1,
Nat. Dex ROMs through the NatDex fork, and every preset in circulation
(including the official gist strings) targets ZX-format settings. A newer UPR
lineage means a new settings format and new preset incompatibilities for zero
IronMON benefit. The desktop UPR's *UI* is the reference for §8.2's dependency
structure — and it is already vendored as source, so read the panel code
rather than the release binaries.

---

## 9. M1 — DONE 2026-08-31 (Opus)

All five P0 items landed. Evidence, not assertion:

| # | Work | Verified by |
|---|---|---|
| 1 | ProgressPanel on randomize | Screenshot mid-run: "RANDOMIZING ON THIS PHONE / 0s" + bar + honest copy, NEW RUN disabled |
| 2 | Contrast sweep, 14 sites | Measured: INFO links 1.01 -> **12.43:1**; ROMs hint 2.03 -> **7.84:1**; PLAY empty state fixed. All >= 4.5:1 |
| 3 | Portrait game+pad pinned | Screenshot: game top, full pad incl. HIDE PAD bottom, tracker between |
| 4 | Sticky NEW RUN + StatusBanner | Screenshot: button pinned above nav; "New run ready (seed ...)" visible above it |
| 5 | Chip strip | **Measured 48.0dp** (was ~29dp); grouped run-actions \| app-actions with a rule; "S1" -> "SLOT 1" |

135 unit tests green. Tracker cards pixel-diffed against the accepted renders
at both widths: **identical** - no regression from the shell work.

### What was built
- `ShellTheme.kt` - tokens named BY GROUND (`inkOnPaper`, `hintOnNight`, ...).
  This is the actual fix for item 2: the two measured failures were the same
  bug, a scheme with one `onSurfaceVariant` for two grounds. A token you
  cannot name the ground for is a token you cannot place safely.
- `ShellComponents.kt` - `ProgressPanel`, `StatusBanner`, `RunPhase`.

### Decisions worth keeping
- **Progress is indeterminate on purpose.** The engine exposes no callback;
  `randomize()` is one blocking call. The three phases reported are the three
  steps the CALLER actually runs, and the second count is measured. No fake
  percentage.
- **Item 3 was re-planned during the work.** The plan said "overlay the
  portrait pad on the game". Measuring first killed that: in portrait the game
  is a 710px strip, so an overlay would cover the text box - the one part you
  read. The pin-and-weight pattern (same as the Run footer) fixes it with
  nothing covered. Plan amended by measurement, not preference.
- Fixed in passing: the RunScreen confirm dialog stayed in the scroll area
  while its trigger moved to the footer - the same off-screen bug one level
  up. And the first `IndeterminateBar` painted full width always (background
  on the node whose size `layout{}` expanded), i.e. a bar permanently at 100%.

### Not verified, carried into M2
- Portrait pinning is proven with an EMPTY tracker and guaranteed structurally
  (a weighted child cannot displace a later sibling in a non-scrolling
  column), but has NOT been seen with a real party card. Needs a run with a
  starter.
- Still open from before M1: pokeball row and `Last seen Lv.N` against a live
  trainer battle; a DS-tracker render after the shared-composable changes.

---

## 10. M2 — DONE 2026-08-31 (Opus)

P1 items 6-10. 141 unit tests green (six new). Tracker cards pixel-diffed
against the accepted renders: **identical** - the shell work has not touched
the clone.

| # | Work | Verified by |
|---|---|---|
| 6 | De-Materialize | `grep` for every Material widget outside the Editor returns **NONE**. Screenshots of RUN and KEYS |
| 7 | Unique settings labels | 3 unit tests, one of which counts titles-vs-unique-titles; **proven to fail** with the disambiguation removed |
| 8 | Drag handle | 10dp+glyph -> 24dp grab strip with a 3-bar grip |
| 9 | Empty states | `EmptyState` on paper, ROMs and PLAY |
| 10 | Attempt counter | Screenshot: "ATTEMPT 3" heads the RUN card |

### The component kit
`ShellRadio`, `ShellListRow`, `ShellDivider`, `ShellBusy`, `ShellDialog`,
`EmptyState`, joining M1's `ProgressPanel` / `StatusBanner`. Screens are now
assembled from these; a screen cannot quietly grow its own dialect.

### Two audit findings that were wrong, corrected by reading the code
- **KEYS was never Material.** The audit called it "stock list rows"; it was
  actually drawing the PC TRACKER's palette (#222 rows, white/yellow pixel
  text on black) on a settings screen - the only shell screen that did, which
  is exactly why it read as a third language. Rebuilt as a paper card like its
  neighbours. Same conclusion, different cause.
- **Most `Button(` hits were false positives** - substring matches on
  `Gen3Button` / `PcCard` / `PadButton`. The real Material surface was ~20
  sites, not the ~60 a naive grep suggests. Worth knowing before M4 greps the
  Editor.

### Deliberate scope calls
- **EditorScreen was left alone.** It holds 10 of the app's Material sites,
  and M4 rebuilds it wholesale; de-Materializing it now would be thrown away.
- `RnqsInfo.displayLabels` was **extracted from the composable** so the
  collision rule could be tested. The emulator's current preset folder has no
  colliding pair, so the on-device path is unexercised - the unit test is the
  evidence, and it is a real one (it fails when the fix is removed).

### Carried forward
- Portrait pinning still unproven with a real party card (from M1).
- Pokeball row and `Last seen Lv.N` unproven against a live trainer battle.
- DS tracker still never rendered since the shared-composable changes - and
  M2 changed shared components again. This is now the oldest unverified item
  and should lead M3.

---

## 11. Assets — status 2026-09-01

### 11.1 The extracted-graphics zip did NOT contain graphics

`Untitled folder-20260901T031930Z-1-001.zip` is 9KB of **17 HTML files and
zero images**. Every entry is a Google Drive *shortcut* stub:

    <!DOCTYPE NETSCAPE-Bookmark-file-1> ... <A HREF="https://drive.google.com/open?id=...">

Folders referenced: Battle UI, HP Bars, Text Boxes, Type Icons, Badges, Bag,
Item / Overworld / Pokemon / Trainer Sprites, Tilesets, Town Map, Battle
Backgrounds, Opening, Credits, Other, #LibertyTwins.

Downloading a Drive folder that contains shortcuts exports the shortcuts, not
their targets. One link was checked and returns a **sign-in wall**, so the
files cannot be fetched. NOTHING can be done with this zip.

**To re-send:** open each Drive folder and use *Download* on the folder
contents (or select the files and download), which produces a zip of real
PNGs. A single zip of the actual images is enough; the folder names above are
a good structure to keep.

### 11.2 Where the game assets WOULD go (once they arrive)

Only the shell is in scope - the tracker is a clone and its chrome comes from
the reference, not from ROM art.

| Asset | Shell use |
|---|---|
| Text Boxes | `Gen3Box` currently HAND-DRAWS a 2dp frame + bevel. Real FRLG text-box graphics as 9-patches would make every card in the app authentic instead of approximated. Highest-value item by far. |
| Battle UI / HP Bars | Not the tracker (clone). Possibly the PLAY chrome. |
| Type Icons | Already bundled from besteon and already correct at 30x12; a swap is optional and must keep those metrics. |
| Badges | Already bundled; same note. |
| Bag / Item Sprites | Only if an item view is ever built. Out of scope. |
| Overworld / Trainer / Tilesets / Town Map / Backgrounds / Opening | No shell use. A splash (P2 item 13) is the only candidate. |

**Licence standing is unchanged and must be recorded when they land:** these
are Nintendo's artwork regardless of being extracted from bought copies.
Exactly the same standing as the sprites already bundled - fine for a LOCAL
personal build, and `NOTICE`'s existing line applies verbatim ("a public
release must remove them or obtain rights"). Add them to the BUNDLED IN THE
APK inventory when added, and do not let them into a distributable build.

### 11.3 Control overlays — vetted source

Verified by reading the repositories, not by reputation:

- **`libretro/common-overlays`** - **CC-BY-4.0**, confirmed from the repo's
  own licence text, and it has the `gamepads/` directory with the touch
  overlays. This is the right source.
- `libretro/retroarch-assets` - also **CC-BY-4.0**; UI iconography rather than
  pad overlays. Useful for glyphs if needed.

CC-BY-4.0 is attribution-only and compatible with this GPL-3.0 app. It is a
genuinely *safe* drop-in, unlike the ROM art - so overlays can go into a
distributable build provided the attribution is added to the About screen and
`NOTICE`, the way Cyan's grant is recorded.

**Do NOT bulk-vendor either repo.** Take the specific PNGs used, record the
file paths and the creator attribution, and keep the shell's existing
geometry: the pad's buttons are 56dp/48dp targets and the chip strip is 48dp,
which M1 measured. An overlay set with different proportions must be fitted to
those targets, not the other way round.

---

## 12. DS tracker verified 2026-09-01 — and it had a regression

The oldest unverified item in the project is closed. `TrackerLookTest` now
renders `NdsTrackerPanel` too (`ds_party_card`), so the DS panel is under the
same harness as the GBA one and can never go two milestones unlooked-at again.

**The structural worry was unfounded:** M1's reference-pixel canvas and M2's
component kit did NOT break the DS layout. Nothing clipped, chips sized right,
stat column and moves table intact.

**But rendering it found a real regression I had introduced in M2.** The
enemy-moves work padded move tables to four rows and identified the padding
with `id == 0`. The DS panel builds its rows from `NdsMoveInfo`, which carries
no move id, so EVERY Gen 4 move looked like an empty slot and its PP column
printed "---". The sentinel was wrong, not the data: an absent id means "not
known here", not "no move".

Fixed with an explicit `PcMove.blank` flag set only on the padding row.
Verified both ways in one render: DS moves now show 35 / 40 / 30 / 30, and the
GBA enemy card still shows "---" on its two empty slots. Three unit tests pin
it, including the exact DS case (a real move with `id == 0` is not blank).

Two smaller drifts fixed at the same time, both found only by looking:
- The DS panel still had the `HIDE` / `TRACKER HIDDEN` state that M2 removed
  from the GBA panel as not-in-reference.
- Its header read "Moves: 4/16" - the reference has no colon
  (`Utils.getMovesLearnedHeader`), and the GBA panel already matched.

**Lesson worth keeping:** a shared component changed under two panels while
only one was ever looked at. The harness now covers both, and any new shared
composable should render in both before it is called done.

### Still carried
- Portrait pinning with a REAL party card (structural guarantee only).
- Pokeball row and `Last seen Lv.N` against a LIVE trainer battle.
Both need a played run to a starter; neither is provable from the harness.

---

## 13. Backgrounds — system built, art still missing (2026-09-01)

### Asset status: still not delivered
The re-sent zip is BYTE-IDENTICAL to the first (9296 bytes, same timestamp,
same 17 Drive-shortcut stubs). Two loose PNGs did arrive in Downloads:

- `Background.png` - 256x160 sky/water scene. Real, usable, now bundled as
  `assets/backgrounds/battle-water.png`.
- `DPPT Bag by DlogRevlis_05.png` - 256x256 DPPt bag UI sheet, magenta-keyed.
  **NOT bundled.** Its filename credits a named third-party spriter, which is
  a different provenance from "extracted from my bought copies": it is one
  person's fan work and would need THEIR permission, not just the personal-
  build caveat that covers ROM rips. Flagged, not used.

### What was built anyway
`ScreenBackground(asset, scrim) { content }` plus `PcAssets.background()`, and
every tab except PLAY now names a scene. Missing art falls back to plain
black, so a tab can be assigned a scene before the art exists.

**Why the scrim lives in the component and not the screens:** full-bleed art
is the fastest way to wreck a contrast sweep. The component owns the
guarantee. Verified on the RUN tab with the real scene behind it:

| Check | Result |
|---|---|
| Card ground under the scene | `(248,248,248)` - still opaque paper |
| Settings subtext on that card | **5.02:1** - unchanged, still passes |
| Page area | `(9,46,88)` - the scene is genuinely rendering |

**This only works because of M2.** De-Materializing moved essentially every
string in the shell onto an opaque card; exactly ONE place still paints text
directly on the page. Backgrounds would have been a contrast disaster before
that and are close to free after it. Worth remembering as the argument for
doing the boring pass first.

### To finish
1. Real art per tab. In Drive, open each folder and download the FILES (not
   the folder, which exports shortcuts). Battle Backgrounds / Tilesets / Town
   Map are the candidates.
2. Assign scenes per tab in `Tab`'s enum - one scene for all five is a
   placeholder, not the design.
3. Re-run the contrast probe above per tab; a bright scene may need a higher
   `scrim` than the current 0.62.
4. Record every bundled scene in NOTICE's inventory under the existing
   personal-build-only line.
5. The RUN sticky footer paints opaque black over the scene. Once real art
   lands, decide whether it should be translucent - it currently reads as a
   control bar, which is defensible, but it was not a design decision.

---

## 14. M3 — mostly done 2026-09-01 (Opus)

| # | Work | State | Evidence |
|---|---|---|---|
| 11 | Haptics | done | Wired on `Gen3Button` and every `PadButton`. **Cannot be verified on an emulator** - it has no haptic hardware. Code path only. |
| 12 | Motion | partial | 120ms tab crossfade, **0ms** when `ANIMATOR_DURATION_SCALE` is 0. PLAY resume slide-up deliberately skipped. |
| 13 | Chrome | partial | Status bar already `#000000` in `themes.xml`. **Splash and launcher icon not done - they need art.** |
| 14 | Press states | done | Pixel-diffed a held vs unheld frame: face darkens, 1px inset shift, region `(247,581)-(561,718)` |
| 15 | Accessibility | mostly | 10 pad controls + the drag handle named, **read back from the live accessibility tree**; shell verified at fontScale 1.3 |

### Decisions
- **Haptic on PRESS only, never release.** A d-pad held through a walk cycle
  must not buzz continuously, and a buzz at both ends of a tap reads as a
  stutter rather than a button.
- **No ripple on `Gen3Button`.** A Material ripple is the wrong idiom on a
  moulded plastic button; `indication = null` plus the inset shift is the
  GBA-hardware feel the shell is imitating.
- **Crossfade animates alpha of already-composed content**, so a device whose
  animation clock never runs still shows the screen. This project has been
  bitten before by content that only appeared once an animation completed;
  that failure mode is structurally impossible here.
- PLAY slide-up skipped: motion over a running emulator surface is risk
  without benefit.

### Honest gaps
- **Haptics are unfelt.** They are wired and compile; nobody has confirmed the
  phone actually buzzes. First thing to check on real hardware.
- Splash + launcher icon still need art (blocked with §13).
- No full TalkBack walk - labels were verified by dumping the accessibility
  tree, which proves the names exist, not that the traversal order is sane.

---

## 15. M4 E1 — DONE 2026-09-01 (Opus)

The editor is rebuilt. Verified on device by opening it, expanding a section,
and toggling a row.

| Work | Verified by |
|---|---|
| Shell language throughout | Screenshot: paper cards, no Material widget left |
| Sticky footer | SAVE AS + change count always visible; COPY/PASTE/BACK all reachable (the old row scrolled the 4th button out of sight) |
| Master-first ordering | Screenshot: "Base statistics mod" heads its section, then EXP curve mod, Standardize EXP curves, Base stats follow evolutions - the desktop GUI's exact order |
| Enum controls | Segmented inline when short; full-width picker + `ShellDialog` otherwise. No more 22-value radio stacks |
| Diff badges | Dot on the changed row, `1` on the section header, "1 changed" in the footer |
| Per-row revert | `<` key appears only on changed rows |
| Round-trip guard | Save writes a temp file, re-reads it, and diffs every option before importing; refuses a lossy save |

### Where the content comes from — NOT guessed

- **Which options exist**: reflected off the vendored `Settings` class, i.e.
  the engine's own API. No hand-typed list.
- **What order they appear in**: `tools/extract_upr_order.py` parses
  `NewRandomizerGUI.form` (the desktop GUI's real layout, in visual order) and
  joins it to `createSettingsFromState()` (which control writes which Settings
  field). 138 fields, GENERATED into `editor/src/main/resources/upr-order.tsv`,
  regenerate if the vendored randomizer is ever updated.
- **Enum values**: the enum constants themselves.
- Two unit tests pin the ordering, including that four known masters precede
  their dependents.

### The one thing I chose rather than copied
Segmented-vs-dialog for enums: inline only when there are <=3 values AND their
labels total <=26 characters. The desktop has room for radio stacks and needs
no such rule, so there is nothing to copy - this is a phone layout decision.
It was tuned by looking: the first attempt keyed on value COUNT alone and the
three long EXP-curve labels crushed the row, erasing its label and squeezing
the third option to a sliver.

### E2 / E3 still to do, with their sources identified
- **Descriptions**: 237 official tooltips already sit in
  `.vendor/upr-zx-461/src/.../newgui/Bundle.properties`. Needs a
  SettingsField -> GUI component -> tooltip key mapping; the component half is
  already produced by the extractor above.
- **Dependency gating**: `enableOrDisableSubControls()`,
  `NewRandomizerGUI.java:3049`, ~600 lines of Swing logic keyed to component
  names. The component->field mapping the extractor builds is what makes a
  faithful port possible. This is the biggest remaining piece.
- **Search** across 141 options, and **int steppers with real engine ranges**
  (currently clamped 0..255, which is a placeholder, not the engine's limits).
- **Per-game filtering** (E3).

---

## 16. M4 E2 + E3 — DONE 2026-09-01 (Opus)

Everything below is GENERATED from the vendored desktop randomizer by
`tools/extract_upr.py` and `tools/extract_upr_support.py`. Nothing is a
hand-kept list.

| Resource | Contents | Source |
|---|---|---|
| `upr-order.tsv` | 138 fields in the desktop's visual order | `NewRandomizerGUI.form` + `createSettingsFromState()` |
| `upr-help.tsv` | **122** descriptions, Swing HTML stripped to plain text | `Bundle.properties` tooltips |
| `upr-gating.tsv` | **41** "disabled while master = X" rules | `enableOrDisableSubControls()` single-condition blocks |
| `upr-unsupported.tsv` | **21** per-generation hides (8 for Gen 3) | `setVisible(romHandler.hasX())` + literal-return handlers |

### Verified on device
- **Gating**: setting "Base statistics mod" to Unchanged greys out "Base stats
  follow evolutions" with the reason *"No effect while Base statistics mod is
  Unchanged"* - the exact defect from the M4 audit.
- **Per-game**: with a FireRed preset the section counts fell 28/23/27/11 ->
  **25/21/25/10**. That is 8 options hidden, matching the 8 extracted Gen 3
  rules exactly - mega evolutions and alt formes, which Gen 3 does not have.
- **Help**: a `?` marker appears only on the 122 rows that have upstream text.
- **Search**: matches label OR description, so "trapping" finds an option
  whose label does not contain the word.

### Two deliberate safety directions
- Gating coverage is **partial by design**. Only single-condition blocks are
  extracted; compound conditions are skipped rather than guessed. A missing
  rule leaves a control usable; a wrong rule greys out something that works
  and looks like a broken editor.
- Per-game hiding takes only capabilities whose handler returns a **literal**.
  `hasMoveTutors()` depends on the specific ROM, so it is skipped and those
  controls stay visible.

### The bug this nearly shipped with
The table is keyed `GEN3`/`GEN4`; the app's own enum is `GBA3`/`NDS4`. Passing
the app's name matched nothing, so the entire per-game filter was **silently
off** - no error, no warning, the screen simply looked finished. It was caught
only by checking that the section counts actually changed. `unsupportedIn` now
normalises both spellings and a test pins it.

That is the third silent string/sentinel mismatch in this project (after the
`id == 0` blank-move sentinel and the national-dex sprite numbering). The
pattern is always the same: two vocabularies meet, nothing throws, and the
feature quietly does nothing. **Check that a feature changed something
observable, not just that it ran.**

### Still open in the editor
- Int steppers clamp 0..255, a placeholder. The engine's real per-field ranges
  are not extracted yet.
- The `?` help panel and search are untested at fontScale 1.3.

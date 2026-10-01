# Add-on designs (2026-09-28)

Two tracker add-ons Blake asked to integrate. Research only; nothing below is built yet.

## Auto Pokemon Themes (Fellshadow, MIT) and the DS tracker's built-in "Auto Pokemon themes"

**What it does (GBA extension, v1.2):** a fixed table, one theme code per species (386 lines, 218 distinct
palettes shared by evolution families; the table was exported from the NDS tracker). Every 30 frames it takes
party slot 1 (an egg there: the next non-egg slot, wrapping); a species with a theme loads it; one without loads
the user's theme back; an empty party keeps what is showing. Theme code order: Default text, Lower box text,
Positive, Negative, Intermediate, Header, Upper border, Upper background, Lower border, Lower background, Main
background, then flag 1 (0 = move names in lower text with a 2x7 type colour bar) and flag 2 (text shadows).
It never saves the auto theme (except a documented flaw: editing colours while one shows saves it).

**NDS built-in (`AUTO_POKEMON_THEMES`, default off, first row of Appearance; PokemonThemeManager.lua):** same
data idea, a `theme` on every PokemonData entry (649 species plus forms; 14 colours, 6 flags). Differences: in
battle it follows the active battler (a switch changes the theme), outside battle the first non-fainted non-egg
party member; forms have their own theme where it differs (13); a species with no theme keeps the current one;
positive/negative are forced to C8DDFF/FDCDCD (white top text) or 0343B0/B40002 (black); plain move names with
8x8 type icons and physical/special icons on; Settings.ini always keeps the user's colours; importing, loading or
resetting a theme turns the option off.

**Design:**
- `tools/trainer-data/convert_autothemes.py` (lupa.lua54; stub globals as convert_nds_log_tables.py plus
  arithmetic metamethods): Gen 3 `gen3/autothemes.tsv` (id, code; assert 386 rows) from the extension's own
  loader; NDS `nds/autothemes.tsv` (gen, species, form, theme; base rows 493/649 plus the 21 form rows that
  differ); `nds/move-type-icons.tsv` from IconDrawer.ICONS[TYPE.."_FILLED"] (17 icons, 8x8 + 4 colours).
- `Pc` (PcTracker.kt): nullable override colours (HeaderX, HeaderGroundX, LowerTextX, LowerBorderX,
  LowerGroundX, LowerPositiveX, LowerNegativeX) whose getters fall back to today's colours, plus
  `moveMark` NAME/BAR/ICON. Map: 0 Text, 1 LowerText+Dim, 2 Positive, 3 Negative, 4 Gold, 5 Header, 6 Border,
  7 Ground, 8 LowerBorder, 9 LowerGround, 10 Page+HeaderGround. Draw the moves section, category icons, effect
  glyphs and carousel rows in the lower colours; fix the stale BLANK_MOVE.
- New `AutoTheme.kt`: onGba(state, on) and onDs(state, gen, on) as above; apply/release; suspend/resume for the
  colour editor.
- `ThemeStore`: keep the user's colours apart so an auto theme never reaches prep/theme.txt.
- Options `autoPokemonThemes` (GBA/GB, label "Auto Pokemon Themes") and `autoPokemonThemesDs` ("Auto Pokemon
  themes"), both off by default, under EDIT COLOR THEME in the gear.
- PlayScreen: call after each tracker state update; release on leaving the play screen.
- Tracker: Gen 3 `Mon.isEgg` (IV word bit 30), Gen 4 `Mon.form` (block B +0x18 >> 3), DS active battler.
- Tests: data equals the references; behaviour cases (egg skip, no-theme release, DS forms, alternate colours,
  editor apply turns DS option off); a wiring test that fails if PlayScreen stops calling AutoTheme; breakage proofs.
- NOTICE: Fellshadow (MIT); palettes and icons from the NDS tracker (GPL-3.0).

## Sprite Is Me (UTDZac, MIT): "Play as your Pokemon"

**Built** (rc32, branch feat/sprite-is-me). What the extension does (v1.4, BizHawk only): draws a 32x32 Walking Pals
sheet of the lead (or "Always use Pokemon", a custom sprite, or "Default if no Pokemon") at a FIXED spot, (104, 56) on
the 240x160 screen, which is exactly the standing player's box. It walks while a D-pad direction is held, idles
otherwise, sleeps after 55 s or when the lead is asleep, faints at 0 HP, and shows 8 facing rows. An optional
invisible-trainer IPS (Ropan, FireRed v1.1 only) blanks the player's overworld pictures. **KaizoCore does not ship that
IPS**: the trainer is hidden natively, on any of the five games, with nothing patched into the ROM.

**What KaizoCore does.** One global switch, off by default, in every mode (Play any game, Kaizo IronMON, Nuzlocke, ROM
Hacks): nothing is gated on a run or a verified ROM. The sprite is drawn into the game's own picture, so it is scaled,
letterboxed and streamed (the Stream kit's tap sees it) exactly as the game is. It changes the picture only: not the game,
not the save. Three parts:

- **Native, per frame** (`libretrodroid/src/main/cpp/sprite_core.h`, freestanding, plus `sprite_overlay.cpp/.h` for JNI):
  one call at the very top of `LibretroDroid::handleVideoRefresh` (off = one relaxed atomic load). When
  `gMain.callback2` is CB2_Overworld or CB2_OverworldBasic and the player's sprite is in use and visible, it works out the
  32x32 box from the game's own UpdateOamCoords fields (so it follows steps, ledge jumps, surfing, bikes, the camera
  offset and the screen edge), hides the trainer by setting attr0 bits 8-9 to 0b10 on the `gMain.oamBuffer` entries in the
  player's tile range (halves and the water reflection included, nothing else touched), and draws the replacement into a
  scratch copy of the picture (RGB565, XRGB8888 and 0RGB1555), faded the way the game fades the trainer's palette. The
  replacement is drawn at the PREVIOUS refresh's box: mGBA's video callback runs before the game's VBlank handler, so the
  OAM built at refresh k is what picture k+1 shows. Any reading out of range draws and writes nothing.
- **Kotlin, per change** (`SpriteIsMe*.kt`): which sprite and which frame (lead = first non-egg Pokemon, "Always use",
  "If no Pokemon", your own), facing and stepping from the game's own state, sleep after 55 s idle or when the lead is
  asleep, faint at 0 HP. The pixels go to the native side only when the frame changes. Switched off, the engine makes no
  call per frame and the native side reads and writes nothing.
- **Your own sprite**: a picture (fitted into 32x32, feet on the ground, nearest-neighbour for small pixel art, flipped for
  left, a two-step bob) or Sprite Is Me / Walking Pals sheets (a zip or several PNGs; idle and walk have 8 rows, sleep and
  faint one), stored under `prep/spriteisme/` so Backup carries them.

**Addresses.** `tracker-gba/.../Overworld.kt` holds one table per game (FireRed 1.0 and LeafGreen share one, FireRed 1.1
moves only the two callbacks). Each value was read from the pret symbol files and is checked against the game's own ROM
by `OverworldAddressTest` (the compiled code of ten small functions is the same in all six retail dumps apart from its
literal pool, so a dump gives every address and struct offset from the game itself). Nat. Dex has no table: its layout is
not published and moves between versions, so `OverworldScan` reads the same functions out of the running ROM, refusing
if any is missing, doubled, or disagrees; it returns exactly the tables on the six retail dumps. Hacks of the five games use
the base game's table and are refused by the native side's own callback check where they moved something.

**Tests.** `OverworldAddressTest` (six dumps), `OverworldScanTest`, `OverworldMathTest` (hand-computed), and in `:app`
`SpriteCoreWasmTest` and `SpriteCoreParityTest` (the real sprite_core.h built for WebAssembly and run under Node against
fake Gen 3 memory, and held to the Kotlin mirror), `SpriteIsMeEngineTest`, `SpriteIsMeLogicTest`, `SpriteArtTest`,
`SpriteIsMeSettingsTest`, `SpriteIsMeWiringTest`, `BackupCoverageTest`. The wasm tests skip, printing why, without the NDK
and node.

**Not verified without a device**: the look of it on a phone, the frame timing against a running mGBA, Nat. Dex on a
device, and the PlayScreen bytecode limit (one added line).

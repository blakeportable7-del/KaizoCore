# Gen 3 UI/UX — design plan

**Goal, in Blake's words: "the entire app menus and settings look like you are in Gen 3
Pokémon," with "thoughtful human UI and ease of use." Those two requirements rank in
that order reversed: usability first, then the skin.** A Gen 3 costume on a confusing
app is a theme; a clear app wearing Gen 3 perfectly is the product.

**Device target: Samsung Galaxy Z Fold 8.** Two form factors in one phone, and the
unfolded inner screen is the best tracker canvas any phone offers.

## 1. Principles (the "thoughtful human UI" contract)

1. **Pixel-perfect, not pixel-small.** GBA assets are authored for 240x160. Scale by
   integer factors with nearest-neighbour so edges stay crisp; never bilinear-blur a
   sprite. Text set in a GBA font must never render below ~16dp equivalent.
2. **Touch targets stay modern.** 48dp minimum regardless of how small the sprite art
   inside them is. The Gen 3 look is the *paint*, not the hitbox.
3. **The game's own grammar is the navigation.** Gen 3 players already know: a white
   rounded text box with a dark 3px frame means "read this"; a red/blue selector
   triangle means "you are here"; START MENU-style vertical lists mean "choose one."
   Reuse that grammar instead of inventing chrome.
4. **One accent decision per screen.** Emerald's UI works because each screen has one
   frame colour family. Don't mix Ruby red, Sapphire blue and Emerald green per screen.
5. **Motion is GBA motion.** Snappy 2-4 frame transitions, the vertical text-box
   type-on, the selector bounce. No material ripples, no fades over 150ms.
6. **Readability escape hatch.** A settings toggle: "Modern text" swaps the pixel font
   for the system font everywhere except decorative headers. Costs nothing to build
   now; saves the theme from ever fighting a tired-eyes evening.

## 2. Design tokens (from the Emerald palette)

```
gen3-frame-dark    #303030   text box outer frame
gen3-frame-light   #A8A8A0   frame bevel
gen3-paper         #F8F8F8   text box fill
gen3-text          #404040   body text (Emerald's dark grey, not black)
gen3-text-shadow   #B8B8B0   the 1px offset shadow behind all game text
gen3-emerald       #40A058   primary accent (menu frames, our brand green stays)
gen3-hp-green      #58D080   HP bar high
gen3-hp-amber      #F8B050   HP bar mid
gen3-hp-red        #F05838   HP bar low
gen3-exp-blue      #40C8F8   EXP bar
gen3-sky           #A9D8F8   backgrounds / battle sky
```

HP-bar semantics are free UX: **progress = HP bar** (green→amber→red is a progress
vocabulary every player reads instantly), **busy = EXP bar filling**, and the
randomizer's "working…" state is a Pokéball wobble.

## 3. Component map (Compose)

| App element | Gen 3 form | Build note |
|---|---|---|
| Cards / dialogs | Emerald text box: 3px dark frame, light bevel, paper fill | One `Gen3Box` composable: draw the frame as a 9-slice from the UI sheet, content padding 12dp |
| Buttons | Text box variant with the selector triangle ▶ on focus/press | `Gen3Button`; pressed state nudges content 1px down-right like the game |
| Tab bar | START MENU vertical list on fold-closed; horizontal on cover screen | Selector triangle marks the active tab |
| Switches | Poké Ball closed/open (off/on) sprite pair | 48dp target, ball art 32dp |
| Radio (mode enums) | The game's OPTIONS screen: left/right arrows cycling values | Matches how Gen 3 actually presents choices; better than 22-row radio stacks |
| Progress | HP bar (determinate), EXP bar (indeterminate) | `Gen3HpBar(fraction)` recolours by thresholds 0.5/0.2 |
| Status/error card | Battle text box with type-on text | Errors use the "It hurt itself in its confusion!" cadence: short, blunt line first |
| Editor sections | Pokédex list frame; section headers as Pokédex category tabs | The 8 ZX sections map neatly |
| Seed display | Trainer ID card styling ("ID No. 7ff78351") | Tap to copy |
| App icon | Premier Ball on grass tile | Fixes the default-robot splash icon too |

## 4. Screens

- **Prepare** = Professor Birch intro framing: one text box asking for the ROM, the
  Nat.Dex/Standard choice as the game's YES/NO selection box.
- **Run** = Trainer Card + party screen: ROM as the trainer card, settings presets as
  party slots, RANDOMIZE as the big battle-menu FIGHT-style button. New Run inherits
  the "would you like to save the game?" double-confirm framing.
- **Editor** = the game's OPTIONS screen scaled up: left/right value cycling, sections
  as Pokédex tabs. 143 options with search (search bar drawn as the game's name-entry
  box, but full QWERTY system keyboard — never the grid-typewriter, that's rule 2).
- **Play** = clean: game on top, controls skinned as GBA hardware (pad cross, pill
  A/B), menu bar as a mini text box. Tracker panel (E3) docks per §5.
- **About** = ends with "CYANSIXFOUR" credit as the title screen renders it — matching
  the grant condition with the game's own typography.

## 5. Z Fold 8 layouts

- **Cover screen (tall, narrow):** phone layout above; tracker (when it exists) is a
  bottom sheet in Gen 3 box style, swiped up over the game.
- **Inner screen (unfolded, ~square):** the payoff — **game left at integer scale,
  tracker right as a permanent Gen 3 panel**, exactly the BizHawk-plus-tracker desktop
  arrangement IronMON players already know. `WindowSizeClass` drives the switch; fold
  state via Jetpack WindowManager. Test both on the emulator with a resizable AVD.
- Flex mode (half-folded on a table): game on the upper half, controls on the lower.

## 6. Asset pipeline (Blake's repositories)

Sources, as supplied: The Spriters Resource Emerald + Ruby/Sapphire UI sheets (text
boxes, HP/EXP bars, option menus, Pokédex frame), PokeCommunity rom-hacking sprite
packs and the Gen 3 sprite pack for 32x32 menu icons and item/ball icons, Bulbapedia
route maps and the Gen 3 Ultimate Tileset Collection for backgrounds, and a GBA-style
pixel font ("Power Green" / "PKMN RBYGSC" class) from a font site.

Pipeline: download sheets to `design/raw/` (git-ignored) → crop the needed elements to
`app/src/main/res/drawable-nodpi/` as individually named PNGs (`gen3_box_tl.png`, 9
slices per frame; `gen3_ball_on/off`; HP bar caps and fill) → frames render via a
`Gen3Box` drawn from slices so they scale to any size → tileable grass/route PNG as the
app background at 3x integer scale, dimmed 20% behind content for contrast.

Font: verify the specific .ttf's licence file before bundling (most GBA-style fonts on
font sites are free for personal use, which local-only satisfies; keep the licence text
beside the font in `assets/fonts/`). Declare via `FontFamily`; sizes only at integer
multiples of the font's design size.

**Licensing, stated once and plainly:** UI sheets and sprites ripped from the games are
Nintendo/Game Freak copyright. In a local-only personal build they're your own
wallpaper; they also permanently close the door on ever distributing this APK. That
door is already closed by the bundled Nat. Dex data conditions and by taste, but this
adds a lock. Cyan's granted sprites remain the only cleared game art.

The home-screen theming in Blake's reference image (Pokéball app icons, route
wallpapers, KWGT widgets) is **launcher theming, outside this app** — the asset list
above serves it too, but nothing in this plan depends on it.

## 7. Build order (each step ships usable)

1. **Tokens + font + `Gen3Box`/`Gen3Button`** — restyle the status cards and buttons
   app-wide. Biggest visible win, one session.
2. **HP-bar progress + Poké Ball switches + selector triangles** — Run and Prepare
   feel like the game. One session.
3. **Editor as OPTIONS screen** (left/right cycling + search). One session.
4. **Play screen GBA controls + app icon + splash fix.** One session.
5. **Fold-aware layouts** (cover/inner/flex). One session, after the tracker exists to
   fill the second pane.
6. Background tiles, type-on text animation, sound-free selector feedback (haptics),
   "Modern text" toggle. Polish pass.

Steps 1–4 are pure Compose skinning over screens that already work, testable on the
emulator screenshot loop the same way everything else was verified today.

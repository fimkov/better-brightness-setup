# Better Brightness Setup — Screen Redesign Spec (v2)

**Date:** 2026-06-24
**Status:** Approved (pending written-spec review)
**Supersedes the screen/UX parts of:** `2026-06-24-better-brightness-setup-design.md` (v1 shipped but the
screen looked stretched/cluttered/unclear). Core architecture (Architectury, MC 26.2, gamma persistence
mixin, first-launch marker, cross-loader) is unchanged.

## Why

v1 works but the screen is ugly and unclear: textures are stretched, tiles overlap the slider/Done button,
there is no instruction telling the user what to do, no animation, and the brightness scale is inconsistent
with vanilla (vanilla now shows 0–200, the setup screen showed 0.00–2.00). This redesign fixes all six.

## Scope (the six fixes)

### 1. Open AFTER vanilla first-launch onboarding
Currently the screen opens on the first `TitleScreen` (Architectury `ClientGuiEvent.INIT_POST`). On a true
first launch MC first shows `AccessibilityOnboardingScreen` (narrator / accessibility), which on completion
calls `Options.onboardingAccessibilityFinished()` (sets `options.onboardAccessibility = false`) then
`setScreen(new TitleScreen())`.

**Change:** gate `BrightnessSetup.shouldOpen()` additionally on
`!Minecraft.getInstance().options.onboardAccessibility` — i.e. only open once vanilla onboarding is done.
So the order is always: vanilla onboarding → title screen → our screen. (Marker + `shownThisSession`
guards stay.)

### 2. Square textures (no stretching)
v1 blits a square source (8×8 creeper region / 16×16 block) into the tile's non-square inner rect → stretch.
**Change:** in `CalibrationPanel.render`, draw the texture as a centered **square** of side
`s = min(innerW, innerH)` (source is square → 1:1, no distortion), centered in the tile content area.

### 3. Row layout, no overlap
**Change:** lay the 4 tiles out in a **horizontal row** (not a 2×2 grid), centered, in the upper-middle of
the screen. The slider and Done button sit below with a clear gap. Layout is computed from `this.height` so
nothing overlaps on any GUI scale:
- Tiles: 4 squares of side `tile` (≈ 96, clamped so the row fits `this.width` with gaps), top at
  `tilesTop` (≈ 84), gap `tileGap` (≈ 16). Row is centered horizontally.
- Each tile's target label is drawn centered just below its tile (the row reserves vertical room for it).
- Slider: width 200, height 20, centered, at `this.height - 56`.
- Done: width 200, height 20, centered, at `this.height - 30`.
- The tiles row must end (tile bottom + label) at least ~20px above the slider; if a very short screen would
  collide, shrink `tile`. Tiles never overlap the widgets.

### 4. Instructions + clear per-tile labels
**Change:** the screen shows, top to bottom:
- Title: `betterbrightness.title` = "Brightness Setup".
- Instruction line under the title: `betterbrightness.instruction` =
  "Drag the slider until each tile matches its label below".
- Under each tile, its target: lang keys (English)
  - `betterbrightness.panel.hidden` = "Should be hidden"
  - `betterbrightness.panel.faint`  = "Barely visible"
  - `betterbrightness.panel.clear`  = "Clearly visible"
  - `betterbrightness.panel.bright` = "Bright"
  (Tile→label mapping by descending threshold: creeper=hidden(1.6), deepslate=faint(1.1),
  coal_ore=clear(0.6), diamond_ore=bright(0.2).)

### 5. Tasteful animation
**Change:**
- **Fade-in:** when the screen opens, content (title, instruction, tiles, labels) fades in over ~250ms
  (alpha 0→1, ease-out). Timed with `System.currentTimeMillis()` captured in `init()` and read each frame.
- **Eased tile brightness:** each tile keeps a `displayedVis` that eases toward its target
  `Brightness.panelVisibility(currentGamma, threshold)` instead of snapping — a per-frame lerp using
  `partialTick`/elapsed so dragging the slider produces a smooth fade, not an instant jump.
- **Soft tile frame:** a subtle 1px lighter inner border on each tile (static; no glow/blur).
Animation is "tasteful" — no per-tile staggered reveals, no pulsing. Keep it MC-consistent.

### 6. Consistent 0–200% scale
- **Setup slider label:** "Brightness: NNN%" where `NNN = round(currentGamma * 100)` (0–200%). (Replaces
  the old `"Brightness: %.2f"` gamma format.)
- **Vanilla gamma label:** extend the gamma option's caption function so the in-game Video Settings slider
  reads: `0% → "Moody"` (`options.gamma.min`), `100% → "Bright"` (`options.gamma.max`),
  `200% → "Brightest"` (new key `betterbrightness.options.gamma.brightest`), and any other value → "NNN%".
  Implemented as a second `@ModifyArg` on the **same** gamma `OptionInstance.<init>` call already targeted
  by `OptionsGammaMixin` (the caption-function argument, **index 2**; the ValueSet is index 3 and is already
  modified). The replacement caption function must reproduce vanilla's label format (caption + value); if
  `Options.genericValueLabel(...)` is not accessible from the mixin, format with the vanilla
  `"options.generic_value"` translatable (`"%s: %s"`). Verify the arg index/type against the decompiled
  `Options.java` gamma block (lines ~869–884) before coding.

## Components changed

- `common/.../BrightnessSetup.java` — add the `onboardAccessibility` gate to `shouldOpen()`.
- `common/.../client/BrightnessSetupScreen.java` — row layout, instruction text, fade-in timing, slider
  label as "%", pass eased gamma/visibility + fade alpha to panels.
- `common/.../client/CalibrationPanel.java` — square centered texture, eased `displayedVis`, label below
  tile, optional soft frame, fade-alpha applied.
- `common/.../mixin/OptionsGammaMixin.java` — add the caption-function `@ModifyArg` (index 2) for the
  vanilla "Moody/Bright/Brightest/NNN%" label. (ValueSet @ModifyArg + `GammaRange` unchanged.)
- `common/.../GammaRange.java` — unchanged (range [0,2], stores exposed value).
- `common/src/main/resources/assets/betterbrightness/lang/en_us.json` — add `instruction`,
  `options.gamma.brightest`; keep/confirm `title` + the 4 panel keys (English).
- Pure logic: if an easing lerp or the percent formatting is extracted into `Brightness` (or a small
  helper), it gets a unit test. Keep it a one-liner — e.g. `Brightness.lerp(double from, double to,
  double t)` and `Brightness.toPercent(double gamma)` → `(int) Math.round(gamma*100)`.

## Non-goals (unchanged from v1 / deferred)
Live 3D entity render (title-screen `level` is null), sounds, configurable thresholds/colors, a re-open
button, older-version ports. Forge still deferred (upstream).

## Error handling
- Per-tile texture blit stays wrapped in try/catch with a placeholder fill (v1 behavior).
- The new caption-function mixin must not throw at construction; if the vanilla label format can't be
  reproduced, fall back to a plain `caption + ": " + NNN%` Component rather than crashing option init.
- The onboarding gate reads a public field; no failure path.

## Testing
- **Pure logic (JUnit, no MC):** `toPercent` (0.0→0, 1.0→100, 2.0→200) and `lerp` (endpoints + midpoint) if
  extracted. Existing `Brightness`/`Marker` tests stay green (5/5).
- **Runtime (`runClient`, controller-verified):** (a) on a fresh run the screen opens only after the title
  screen (onboarding gate holds — verify via log + that it doesn't open during onboarding); (b) textures are
  square, not stretched; (c) tiles don't overlap slider/Done; (d) instruction + per-tile labels render;
  (e) fade-in + eased brightness visible; (f) setup label shows "%"; (g) Video Settings gamma slider reads
  Moody/Bright/Brightest/NNN%. Confirmed by screenshot on the real display.

## Risk
- The caption-function `@ModifyArg` (index 2) must target the same gamma ctor as the ValueSet one — verify
  the index/type and that `genericValueLabel`/label format is reproducible. Runtime-verify the gamma slider
  label (mixins apply only at runtime under loom-no-remap).
- Animation timing must use real time (`System.currentTimeMillis()`), fine in mod runtime code.

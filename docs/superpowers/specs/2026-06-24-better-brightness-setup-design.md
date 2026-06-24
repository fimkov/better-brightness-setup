# Better Brightness Setup — Design Spec

**Date:** 2026-06-24
**Status:** Approved (pending written-spec review)

## Summary

A Minecraft mod that, on the **first client launch only**, shows a polished,
Minecraft-styled brightness calibration screen. The screen presents 4 panels of
**live-rendered** game content (blocks + an entity) that darken/brighten in real
time as the user drags a brightness slider, with captions telling the user what
each panel should look like ("shouldn't be visible" … "should be bright"). On
*Done*, the chosen brightness is written to the game's gamma setting.

Target: **Minecraft 26.2** (year-versioned, Java 25), shipped simultaneously for
**NeoForge, Fabric, Forge, and Quilt**. Ports to older MC versions are a
separate, later effort.

## Goals / Non-Goals

**Goals**
- One codebase, four loaders, from day one.
- Beautiful, vanilla-consistent first-launch calibration screen.
- 4 live calibration panels that react to the slider in real time.
- Slider maps min→max as **vanilla-min gamma (0.0) → 2× vanilla-max gamma (2.0)**.
- Show exactly once (first launch), persisted across restarts.

**Non-Goals (deferred, explicitly out of scope now)**
- A button/keybind to re-open the screen later. (User wants a second variant
  later — separate spec.)
- Ports to older Minecraft versions. (Separate spec.)
- Custom lightmap / shader-level brightness. We reuse vanilla `options.gamma`.
- A live full-world render via the world renderer (over-engineered; see below).

## Architecture — Architectury multiloader

Gradle multi-project using the Architectury template:

```
better_bridness_setup/
  common/      # 95% of the code: Screen, panel renderer, brightness logic, first-launch marker
  fabric/      # thin: client entrypoint -> BrightnessSetup.onClientReady(); also runs on Quilt
  neoforge/    # thin: client mod event -> onClientReady()
  forge/       # thin: client setup event -> onClientReady()
```

- **Quilt** is *not* a separate Gradle module. Quilt loads Fabric mods through
  its Fabric-compat layer, so the Fabric jar runs on Quilt as-is. We add a
  `quilt.mod.json` to the Fabric jar so it appears as a native Quilt mod. Low
  cost, no extra build.
- `common` uses vanilla Minecraft classes directly for all GUI/render code
  (identical across loaders). Architectury API is used only where a loader
  abstraction is genuinely needed (config dir path, client-init hook). Each
  loader module is a few lines: register a client-init callback that calls
  `BrightnessSetup.onClientReady()`.

**Mod identity (defaults, changeable):**
- mod id: `betterbrightness`
- package: `io.github.fimkov.betterbrightness`
- display name: `Better Brightness Setup`

## Components

### `BrightnessSetup` (common)
Entry logic. `onClientReady()`:
1. Read the first-launch marker (see Persistence).
2. If already done → return.
3. Set a one-shot "open pending" flag. The screen is opened the first time the
   **title screen** appears (not mid-loading), via the loader's screen/tick hook.

### `BrightnessSetupScreen extends Screen` (common)
- Vanilla-styled menu background, centered title, a **2×2 grid of 4
  `CalibrationPanel`s**, a brightness slider, and a *Done* button.
- Esc and *Done* both finish (write gamma + create marker + close).
- On every frame, passes the current slider brightness to each panel so they
  re-tint live.

### `CalibrationPanel` (common)
Renders **real game content** into a bounded rect:
- Block textures (e.g. a small arrangement of stone/ore blocks) and/or a living
  entity (a creeper) using vanilla GUI helpers
  (`InventoryScreen.renderEntityInInventory`-style entity-in-GUI rendering and
  `GuiGraphics` block/item drawing).
- We compute a **darkening multiplier** ourselves from the current brightness
  value and apply it as a tint/overlay, so each panel demonstrates a different
  visibility threshold. Panels do **not** route through the world lightmap — the
  tint is our own curve, which is what makes "live render reacting to the slider"
  simple and version-stable.
- 4 panels target 4 reference levels with captions:
  1. "Эту фигуру не должно быть видно" (hidden until high brightness)
  2. "Эта должна быть едва видна"
  3. "Эта — хорошо видна"
  4. "Эта — ярко видна"

> **ponytail note:** "live render" = real blocks/entity drawn into panels with our
> own brightness curve applied each frame. We deliberately do **not** spin up a
> fake `Level` + world renderer — that's heavy and fragile across version ports
> for no calibration benefit.

### `Brightness` (common, pure logic — testable without Minecraft)
- `sliderToGamma(t: 0..1) -> 0.0..2.0` (linear: vanilla-min → 2× vanilla-max).
- `panelVisibility(gamma, panelThreshold) -> 0..1` — the curve the panels use to
  decide how visible their content is, so we can unit-test the calibration
  thresholds without rendering.

### Persistence (common, loader config dir via Architectury)
- Marker file: `config/betterbrightness/.done` (empty marker).
- Absent → first launch. Created on *Done*.
- ponytail: a marker file, not a JSON config — we persist exactly one bit.

## Data flow

```
loader client-init -> BrightnessSetup.onClientReady()
  -> marker exists? yes -> stop
  -> no -> set openPending
title screen shown (first time) + openPending
  -> Minecraft.setScreen(new BrightnessSetupScreen())
slider drag -> brightness value updates -> panels re-tint each frame
Done/Esc
  -> options.gamma = sliderToGamma(value)  (set directly, allows > 1.0)
  -> options.save()
  -> create config/betterbrightness/.done
  -> close screen
```

## Brightness write detail

`options.gamma` is a clamped option in the UI (0..1), but the underlying value
can be set directly to push past 1.0 (the well-known "fullbright gamma" trick;
the lighting engine honors the raw value). We set the option value to
`sliderToGamma(t)` in `[0.0, 2.0]` and call `options.save()`. Exact accessor for
26.2 to be confirmed against mappings (see Risks).

## Error handling
- If reading/writing the marker file fails (IO), log a warning and treat as
  "show the screen" — worst case the screen appears again next launch; we never
  crash startup.
- If entity/block rendering for a panel throws, catch per-panel and draw a plain
  placeholder rect so one bad panel can't crash the screen.
- Setting gamma is wrapped so a mapping/API mismatch logs and no-ops rather than
  crashing the *Done* action.

## Versioning / Risk
- **MC 26.2 is year-versioned and new.** Render/GUI/option class & method names
  are verified against the **Architectury 26.2 project template + official
  mappings**, not assumed from 1.21.x. This verification is the first
  implementation step.
- Loader toolchain: Java 25 toolchain, Loom 1.15 / Gradle 9.4 for Fabric per
  upstream notes; NeoForge 26.2.x, Forge for 26.2.

## Testing
- **Pure logic:** `Brightness` (slider→gamma mapping, panel-visibility
  thresholds) gets one small assert-based test runnable without Minecraft
  (the only non-trivial logic worth a test — ponytail).
- **GUI:** verified by running the dev client (`runClient`) on at least Fabric
  and NeoForge: confirm the screen shows on first launch, panels react to the
  slider, *Done* writes gamma and the marker, and it does **not** reappear on the
  second launch.

## Open items
- Confirm exact 26.2 API names for: `Screen` lifecycle, `GuiGraphics`,
  entity-in-GUI render helper, and the `gamma` option accessor (done during impl
  step 1).
- Final caption wording / which blocks + entity per panel (cosmetic; defaults
  above).

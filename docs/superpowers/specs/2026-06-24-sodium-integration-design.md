# Sodium Integration — Design Spec

**Date:** 2026-06-24
**Status:** Approved (user said "делай")
**Companion research:** `SODIUM-NOTES.md` (authoritative Sodium 0.9.x class/API detail — read it for the exact hooks).

## Summary

When **Sodium 0.9.0 (MC 26.2)** is installed, integrate Better Brightness into Sodium's own
video-settings GUI instead of the vanilla one: (1) extend Sodium's Brightness slider to **0–200%**
(robust, via Sodium's public config API), and (2) make that slider **wider** and draw **4 compact
calibration icons + captions inline below it**, reacting to the slider — via mixins into Sodium
internals (the user chose this over the robust API-button alternative, accepting the fragility).

Covers **Fabric + Quilt + NeoForge** with one codebase (Sodium's first-party NeoForge build, not
Embeddium). Forge is out (no Sodium for Forge; we don't ship Forge anyway).

## Goals / Non-Goals

**Goals**
- Soft dependency: the mod works unchanged WITHOUT Sodium (vanilla "Setup Brightness" button path
  stays). Sodium integration activates only when Sodium is present.
- Sodium Brightness slider range 0–200% (gamma 0.0–2.0).
- The Brightness option's row: wider slider + 4 reference icons (creeper / deepslate / coal_ore /
  diamond_ore) with captions, inline below the track, reacting to the slider's gamma. Reuse the
  existing `CalibrationPanel` rendering logic (compact).

**Non-Goals**
- Embeddium (Sodium's own NeoForge build is the target).
- Touching other Sodium sliders/options (mixins scoped to the brightness option only).
- Shipping Sodium (compile-only dependency).

## Architecture

Sodium's GUI lives in its shared `common` module, so our Sodium-compat code lives in our `common`
module too; per-loader only the config-API entrypoint registration differs.

### Dependency (compile-only — we don't ship Sodium)
Full Sodium jar (bundles API + internals; the internal classes are needed for the mixin targets):
- Fabric: `modCompileOnly "maven.modrinth:sodium:mc26.2-0.9.0-fabric"`
- NeoForge: `compileOnly "maven.modrinth:sodium:mc26.2-0.9.0-neoforge"`
- Repo: `maven { url "https://api.modrinth.com/maven" }`

### Part 1 — Range 0–200% (Sodium config API; robust)
- A `ConfigEntryPoint` (`net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint`) implementation,
  `registerConfigLate(ConfigBuilder)`: on `registerOwnModOptions()`/the appropriate builder, call
  `registerOptionOverlay(Identifier.parse("sodium:general.gamma"), <IntegerOptionBuilder>)` with
  `setRange(0, 200, 1)`, `setBinding(v -> gamma().set(v*0.01), () -> (int)(gamma().get()/0.01))`,
  and a `setValueFormatter` rendering "NNN%" (0–200%).
- Registered via the `sodium:config_api_user` entrypoint — Fabric in `fabric.mod.json`; NeoForge via
  `@ConfigEntryPointForge("betterbrightness")` (or the `neoforge.mods.toml` modproperties key).

### Part 2 — Wider slider + 4 inline icons (Sodium-internal mixins; fragile, user-chosen)
Separate mixin config `betterbrightness.sodium.mixins.json` with `"required": false` so it is a
no-op when Sodium is absent (targets only Sodium classes). Scope ALL changes to the brightness
option only (identified by option ID `sodium:general.gamma`); leave every other Sodium slider/row
untouched.
- Mixin `OptionListWidget` (row-layout loop): give the brightness row extra height (so there's room
  for the icons under the slider). Per-option height — only the brightness row grows.
- Mixin the slider control's render (`SliderControl.SliderControlElement#extractRenderState`, and the
  width source) for the brightness option: widen the track and draw the 4 calibration icons +
  captions in the extra space below it, tinted/eased from the slider's current gamma — reuse
  `CalibrationPanel` (compact). Identify "this is the brightness control" via its `Option` ID.
- Exact members to target (from SODIUM-NOTES): `Layout.entryHeight(Font)`,
  `Layout.SLIDER_WIDTH`/`SLIDER_HEIGHT`, `OptionListWidget` row loop,
  `SliderControl.SliderControlElement#extractRenderState`, `IntegerOption#createControl()`.

### Soft-dependency behavior
- Sodium dep is `compileOnly` → not shipped; present at runtime only if the user installs Sodium.
- The Sodium mixin config is `required:false` + Sodium-class-targeted → silently skipped without
  Sodium. The config entrypoint is a Sodium entrypoint → only invoked if Sodium loads it.
- With Sodium installed, Sodium replaces the vanilla video-settings screen, so our vanilla
  `VideoSettingsScreenMixin` (the "Setup Brightness" button) is simply not seen there — no conflict.
  Without Sodium, the vanilla button path is the entry. Both coexist.
- Our existing gamma-persistence mixin (`OptionsGammaMixin`, [0,2] ValueSet) stays — but note Sodium
  writes gamma directly bypassing the ValueSet, so Sodium's 0–200% is delivered by Part 1's overlay,
  not by that mixin.

## Components / Files (high level; plan details them)
- `common/.../sodium/BetterBrightnessSodiumConfig.java` — the `ConfigEntryPoint` (range overlay).
- `common/.../mixin/sodium/` — the Sodium GUI mixins (row height, slider render/width). New mixin
  config `betterbrightness.sodium.mixins.json`.
- A compact icon renderer reusing `CalibrationPanel` (or a small shared helper).
- Build: Modrinth maven repo + Sodium compileOnly deps (fabric/neoforge). Entrypoint declarations.

## Error handling
- All Sodium code guarded by soft-dep (no Sodium → dormant, no crash).
- Per-icon render wrapped in try/catch (as the existing `CalibrationPanel` does) so a bad icon can't
  break Sodium's screen.
- The range overlay is pure API; if the option ID changes in a future Sodium, the overlay is a no-op
  (logged), not a crash.

## Testing
- No pure-logic units (it's GUI/mixin + API wiring). Build green both loaders with Sodium compileOnly.
- Runtime, controller-verified, TWO scenarios:
  1. **With Sodium installed:** Sodium Video Settings → Brightness slider spans 0–200%; wider; 4
     reference icons + captions render inline below it and react as the slider moves.
  2. **Without Sodium:** game boots, vanilla "Setup Brightness" button still works, no mixin/crash.
- Mixins apply only at runtime → must launch with Sodium present to verify Part 2.

## Risks
- **Part 2 mixins: HIGH fragility** — non-API Sodium internals, version-locked to 0.9.x; Sodium
  refactors its GUI between versions. Pin the Sodium build; expect to fix on Sodium updates. (User
  accepted this over the robust API-button alternative.)
- Part 1 (API overlay): low risk; only assumes the `sodium:general.gamma` ID + gamma binding shape.
- The brand-new Sodium config API is itself evolving (0.8/0.9 line) — pin the version.

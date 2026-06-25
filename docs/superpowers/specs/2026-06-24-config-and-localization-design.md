# Config + Localization — Design Spec

**Date:** 2026-06-24
**Branch:** `feat/setup-button` (continues; merged to master after this batch + final review)
**Status:** approved (design), pending spec review

## Goal

Add a user config (max brightness, default 100%, raisable to 500%) reachable from the mod
list, and localize every user-facing string into all Minecraft locales (machine-translated)
plus hand-written joke locales.

## Global Constraints (apply to every task)

- **MC 26.2**, Java 25, Architectury multiloader (`common` + `fabric` + `neoforge`; Quilt via
  Fabric-compat). MC 26.2 ships **deobfuscated** → mixins target real names, `remap=false`, no refmap.
- **New dependencies (26.2 builds verified on Modrinth):**
  - Cloth Config `26.2.155` (`fabric` + `neoforge`) — **required** dependency (config backend + screen).
  - Mod Menu `20.0.0-beta.4` (`fabric` + `quilt`) — **optional** dependency (mod-list button on Fabric/Quilt only).
  - Architectury API `21.0.2` — already used.
- **Default max brightness = 100%** (gamma 1.0 = vanilla). Config `maxBrightnessPercent` raises it; range **100–500**.
- Sodium stays `compileOnly` / soft (`required:false`) — absent Sodium must still boot.
- Existing behavior preserved: vanilla gamma slider is replaced by a "Setup Brightness" button;
  first-launch flow opens the setup screen after onboarding; gamma persistence via `OptionsGammaMixin`.
- All user-facing strings are translatable keys under `assets/betterbrightness/lang/`.

## Components

### 1. Config model — `BetterBrightnessConfig` (common)

Cloth Config `AutoConfig` config class.

```java
@Config(name = "betterbrightness")
public class BetterBrightnessConfig implements ConfigData {
    @ConfigEntry.BoundedDiscrete(min = 100, max = 500)
    @Comment("Maximum brightness the brightness sliders allow, in percent. 100 = vanilla.")
    public int maxBrightnessPercent = 100;

    @Override public void validatePostLoad() { /* clamp 100..500 defensively */ }
}
```

- Registered once in **common client init**: `AutoConfig.register(BetterBrightnessConfig.class, GsonConfigSerializer::new)`.
- Single accessor used everywhere: `BetterBrightnessConfig.maxPercent()` → reads the live holder, returns
  `100` if the holder is not yet available (so early callers like `OptionsGammaMixin` never NPE).
- Storage: AutoConfig writes `config/betterbrightness.json` automatically.

### 2. Wire the configurable max

- **`Brightness.sliderToGamma(double t)`** → `clamp01(t) * (maxPercent()/100.0)`. Slider fraction → gamma `[0, max/100]`.
  - **TDD note:** the test must inject the max (the method takes max as a param, or a package-visible overload),
    so the pure-math test stays free of Minecraft/Cloth. Public API: `sliderToGamma(t)` reads config;
    `sliderToGamma(t, maxPercent)` is the tested pure overload.
- **`OptionsGammaMixin` → `GammaRange`**: range fixed at the **ceiling** `[0.0, 5.0]` (= 500%), CONSTANT.
  Rationale: the vanilla slider is replaced by a button, so `GammaRange` exists only to let `gamma.set()`
  accept values > 1.0; pinning it at the ceiling means changing `maxBrightnessPercent` never requires a
  restart (no value within the config-allowed range is ever clamped). The visible slider max is enforced
  separately and live (below).
- **Setup screen slider** (`BrightnessSetupScreen`): the `%` label and `t→gamma` mapping use `maxPercent()`
  read live on open. Slider fraction 0..1 maps to 0..max%.
- **Sodium config API** (`BetterBrightnessSodiumConfig`): the overlay `setRange(0, maxPercent(), 1)` reads
  `maxPercent()` live when the config screen is built.

### 3. Config screen via mod list

AutoConfig generates the Cloth config screen from `BetterBrightnessConfig`.

- **Fabric / Quilt:** Mod Menu API entrypoint (OPTIONAL dep) returning
  `parent -> AutoConfig.getConfigScreen(BetterBrightnessConfig.class, parent).get()`.
  Declared in `fabric.mod.json` `entrypoints.modmenu` + `depends`/`suggests` as optional. Without Mod Menu
  installed, the mod still loads and the screen still exists (reachable programmatically); only the mod-list
  button is absent — expected.
- **NeoForge:** register the config screen via Cloth's NeoForge integration / `IConfigScreenFactory` so the
  native mod-list "Config" button opens the AutoConfig screen.

### 4. Localization

**4a. Keys.** Convert remaining hardcoded literals to translatable keys in `en_us.json`:
- `betterbrightness.button.setup` ("Setup Brightness" — the Video Settings button)
- `betterbrightness.button.done` ("Done")
- `betterbrightness.slider.brightness` ("Brightness: %s%%" — slider label, `%s` = percent)
- AutoConfig keys: `text.autoconfig.betterbrightness.title`,
  `text.autoconfig.betterbrightness.option.maxBrightnessPercent` (+ `.@Tooltip`)
- Existing keys retained: `betterbrightness.title`, `.instruction`, `.panel.{hidden,faint,clear,bright}`,
  `.short.{hidden,barely,clear,bright}`.

**4b. Auto-translation script** — `tools/translate.py`:
- Input: the canonical `en_us.json`. Output: `<locale>.json` for every MC locale.
- Uses `deep-translator` (`GoogleTranslator`). A `MC_LOCALE → GOOGLE_LANG` mapping table covers MC's ~115
  locales; locales with no Google equivalent are skipped (logged). Joke/constructed locales are EXCLUDED
  from machine translation (see 4c).
- Preserves format placeholders (`%s`, `%%`, `%1$s`) verbatim — values containing them are translated with
  the placeholder protected (mask → translate → unmask) so Google can't mangle them.
- Idempotent and re-runnable; prints a per-locale OK/skip summary. **Caveat:** sends the short UI strings to
  Google Translate (external service) — acceptable per the user's request for machine translation.

**4c. Joke locales (hand-written / deterministic):**
- `en_pt` — Pirate Speak (hand-written).
- `lol_us` — LOLCAT (hand-written).
- `en_ud` — Upside-down: generated by a deterministic character-flip of `en_us` values inside `translate.py`
  (no network), matching MC's own en_ud convention.

## Edge cases / decisions

- Config holder not loaded when `OptionsGammaMixin` runs → accessor returns default 100 (and `GammaRange` is
  pinned at the ceiling anyway, so no clamp surprise).
- `maxBrightnessPercent` changed at runtime: setup screen + Sodium pick it up live on next open; no restart.
- A previously-stored gamma above the new (lowered) config max stays valid (GammaRange ceiling unchanged);
  the slider just can't reach it until config is raised again.
- Translation script run failure / offline: the build does not depend on running it; committed lang files are
  the artifact. Re-runnable any time.

## Testing

- `Brightness.sliderToGamma(t, maxPercent)` pure overload — endpoints + clamping for max ∈ {100, 200, 500}.
- `BetterBrightnessConfig` default = 100; `validatePostLoad` clamps out-of-range.
- `translate.py` placeholder-preservation: a unit check that `%s`/`%%` survive a mock translate round-trip;
  en_ud flip is reversible on ASCII.
- Runtime (controller): config screen opens from mod list (NeoForge native; Fabric via Mod Menu); changing
  max to 500 widens the setup + Sodium sliders without restart; a sampled translated locale renders.

## Out of scope

- Per-string human review of machine translations (machine output is accepted as-is per request).
- Additional config options beyond `maxBrightnessPercent` (YAGNI).

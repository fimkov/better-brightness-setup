# Sodium Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When Sodium 0.9.0 (MC 26.2) is installed, extend its Brightness slider to 0–200% (via Sodium's config API) and render 4 compact calibration icons + captions inline below a widened brightness slider (via Sodium-internal mixins), while the mod stays fully functional without Sodium.

**Architecture:** A soft-dependency Sodium-compat layer in `common`. Part 1 (range) uses Sodium's public config API (robust). Part 2 (wider slider + inline icons) uses mixins into Sodium's GUI internals scoped to the brightness option only (fragile, version-locked to 0.9.x — user-chosen over the API-button alternative). Sodium is a compile-only dependency; a `required:false` mixin config makes the Sodium mixins no-ops when Sodium is absent.

**Tech Stack:** Architectury, MC 26.2, Sodium 0.9.0+mc26.2 (config API + internals), Sponge Mixin, Java 25. Reuses our existing `CalibrationPanel`.

## Global Constraints

- Soft dependency: mod MUST work unchanged without Sodium (vanilla "Setup Brightness" button path stays; no crash). Sodium dep is `compileOnly`/`modCompileOnly` (NOT shipped).
- Sodium target: `maven.modrinth:sodium:mc26.2-0.9.0-fabric` / `...-neoforge` (repo `https://api.modrinth.com/maven`). Sodium's own first-party NeoForge build — NOT Embeddium. One `common` codebase covers Fabric+Quilt+NeoForge.
- Brightness option ID: `sodium:general.gamma`. Sodium writes gamma directly as `gamma().set(value*0.01)`, range hardcoded `setRange(0,100,1)`, bypassing the vanilla ValueSet. So 0–200% in Sodium is delivered by Part 1's API overlay (NOT our existing OptionsGammaMixin).
- Part 2 mixins MUST be scoped to the brightness option ONLY (by ID `sodium:general.gamma`) — do not change `Layout.SLIDER_WIDTH`/`entryHeight` globally for other options.
- Part 2 internals are non-API + version-locked to Sodium 0.9.x; pin the Sodium version. Authoritative class/hook detail: `docs/superpowers/specs/SODIUM-NOTES.md`. Sodium source for exact signatures: clone `https://github.com/CaffeineMC/sodium` at tag `mc26.2-0.9.0` (commit `bf93ed8`).
- Mixins apply only at RUNTIME (loom-no-remap) and only with Sodium present — the controller runtime-verifies both with-Sodium and without-Sodium.
- Reuse the existing `io.github.fimkov.betterbrightness.client.CalibrationPanel` for the icons (compact). Per-icon render already try/caught there.

---

## File Structure

```
common/src/main/java/io/github/fimkov/betterbrightness/sodium/
    BetterBrightnessSodiumConfig.java     # ConfigEntryPoint: range 0–200% overlay (Part 1)
common/src/main/java/io/github/fimkov/betterbrightness/mixin/sodium/
    OptionListWidgetMixin.java            # taller row for brightness only (Part 2)
    SliderControlElementMixin.java        # wider track + 4 inline icons for brightness (Part 2)
common/src/main/resources/betterbrightness.sodium.mixins.json   # required:false, Sodium-targeted
fabric/src/main/resources/fabric.mod.json        # + sodium:config_api_user entrypoint
neoforge/...                                       # + @ConfigEntryPointForge (or mods.toml key)
build.gradle / fabric/build.gradle / neoforge/build.gradle  # Modrinth repo + Sodium compileOnly
```

---

### Task S1: Sodium dependency + soft-dep scaffolding

**Files:** root `build.gradle`, `fabric/build.gradle`, `neoforge/build.gradle`; new `common/src/main/resources/betterbrightness.sodium.mixins.json`; `fabric.mod.json`/`neoforge.mods.toml` (mixin config registration).

**Interfaces:**
- Produces: a build that compiles against Sodium 0.9.0 (API + internals) as a compile-only dep, and a `required:false` Sodium mixin config wired into both loaders (empty `client` list for now). No behavior yet.

- [ ] **Step 1: Add the Modrinth maven repo** to the repositories block (root or per-module as the Architectury template expects): `maven { url "https://api.modrinth.com/maven" }`.
- [ ] **Step 2: Add Sodium as a compile-only dependency.**
  - `fabric/build.gradle`: `modCompileOnly "maven.modrinth:sodium:mc26.2-0.9.0-fabric"`
  - `neoforge/build.gradle`: `compileOnly "maven.modrinth:sodium:mc26.2-0.9.0-neoforge"`
  - `common/build.gradle`: depend on one of them so common can reference Sodium classes — under loom-no-remap common uses the Fabric jar: `compileOnly "maven.modrinth:sodium:mc26.2-0.9.0-fabric"`. (If common can't resolve the modrinth `sodium` artifact directly, mirror how the project already adds `architectury`/`fabric-loader` to common and use that configuration.)
- [ ] **Step 3: Create `betterbrightness.sodium.mixins.json`** (sibling of the existing `betterbrightness.mixins.json`):
  ```json
  {
      "required": false,
      "minVersion": "0.8.7",
      "package": "io.github.fimkov.betterbrightness.mixin.sodium",
      "compatibilityLevel": "JAVA_25",
      "client": [],
      "injectors": { "defaultRequire": 1 }
  }
  ```
  (`required:false` → if a target Sodium class is absent, the whole config is skipped without failing the launch.)
- [ ] **Step 4: Register the Sodium mixin config** for both loaders: add `"betterbrightness.sodium.mixins.json"` to `fabric.mod.json`'s `"mixins"` array, and a second `[[mixins]] config="betterbrightness.sodium.mixins.json"` in `neoforge.mods.toml`.
- [ ] **Step 5: Build.** Run: `./gradlew build` → BUILD SUCCESSFUL both loaders (Sodium resolves as compileOnly; empty Sodium mixin config loads). Do NOT run runClient.
- [ ] **Step 6: Commit.** `git add -A && git commit -m "build: add Sodium 0.9.0 compile-only dep + soft (required:false) mixin config"`

If the Modrinth `sodium` artifact won't resolve for a loader after real effort, try the CaffeineMC API artifact (`net.caffeinemc:sodium-fabric-api:0.9.0+mc26.2` from `https://maven.caffeinemc.net/releases`) for Part 1, and report that the internals (needed for Part 2 mixins) require the full Modrinth jar — BLOCK Part 2 if the full jar is unavailable.

---

### Task S2: Brightness range 0–200% via Sodium config API

**Files:** `common/.../sodium/BetterBrightnessSodiumConfig.java`; `fabric.mod.json` (entrypoint); `neoforge` entrypoint wiring.

**Interfaces:**
- Consumes: Sodium API `net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint`, `ConfigBuilder`, `ModOptionsBuilder.registerOptionOverlay`, `IntegerOptionBuilder` (`setRange`/`setBinding`/`setValueFormatter`). Verify exact names/signatures against the Sodium API source (`common/src/api/.../config/`) / `api/config/USAGE.md`.
- Produces: an active overlay making `sodium:general.gamma` span 0–200% when Sodium is present. No code consumers in later tasks.

- [ ] **Step 1: Read the Sodium config API** (`common/src/api/java/net/caffeinemc/mods/sodium/api/config/` + `USAGE.md` in the cloned Sodium). Confirm: `ConfigEntryPoint` method names (`registerConfigLate(ConfigBuilder)`), how to reach a `ModOptionsBuilder`, the `registerOptionOverlay(Identifier, OptionBuilder)` signature, and `IntegerOptionBuilder.setRange(int,int,int)/setBinding(...)/setValueFormatter(...)` exact signatures + the `ControlValueFormatter` interface.
- [ ] **Step 2: Implement `BetterBrightnessSodiumConfig`** implementing `ConfigEntryPoint`. In the late-register callback, overlay the gamma option (adjust to the verified API):
  ```java
  package io.github.fimkov.betterbrightness.sodium;
  // imports per the verified Sodium API
  public final class BetterBrightnessSodiumConfig implements ConfigEntryPoint {
      @Override public void registerConfigLate(ConfigBuilder builder) {
          var gammaId = Identifier.parse("sodium:general.gamma");
          var opts = net.minecraft.client.Minecraft.getInstance().options;
          builder.registerModOptions(...) /* or the API's overlay entry path */
              .registerOptionOverlay(gammaId,
                  builder.createIntegerOption(gammaId)
                      .setRange(0, 200, 1)
                      .setValueFormatter(v -> net.minecraft.network.chat.Component.literal(v + "%"))
                      .setBinding(v -> opts.gamma().set(v * 0.01D),
                                  () -> (int) (opts.gamma().get() / 0.01D)));
      }
  }
  ```
  (The exact builder-entry path — `registerOwnModOptions` vs a Sodium-options overlay registrar — comes from Step 1's reading; the overlay+range+binding shape is the point.)
- [ ] **Step 3: Register the entrypoint.**
  - Fabric `fabric.mod.json`: add entrypoint key `"sodium:config_api_user"` → `"io.github.fimkov.betterbrightness.sodium.BetterBrightnessSodiumConfig"`.
  - NeoForge: annotate the class `@net.caffeinemc.mods.sodium.api.config.ConfigEntryPointForge("betterbrightness")` (or add the `[modproperties.betterbrightness] "sodium:config_api_user"=...` key in `neoforge.mods.toml`) per SODIUM-NOTES §3.
- [ ] **Step 4: Build.** `./gradlew build` → SUCCESSFUL both loaders. (Runtime with Sodium = controller.)
- [ ] **Step 5: Commit.** `git commit -m "feat(sodium): extend Brightness slider range to 0-200% via Sodium config API"`

---

### Task S3: Wider brightness slider + 4 inline calibration icons (Sodium-internal mixins)

**Files:** `common/.../mixin/sodium/OptionListWidgetMixin.java`, `common/.../mixin/sodium/SliderControlElementMixin.java`; add both to `betterbrightness.sodium.mixins.json` `client` list.

**Interfaces:**
- Consumes: Sodium internals (verify EXACT signatures from the cloned 0.9.0 source — names below are from SODIUM-NOTES, treat as targets to confirm): `OptionListWidget` row-layout loop + `Layout.entryHeight(Font)`; `SliderControl.SliderControlElement#extractRenderState(...)`, `Layout.SLIDER_WIDTH`; the `Option`/`Control` accessor to read the option ID (`sodium:general.gamma`). Our `CalibrationPanel` (existing).
- Produces: the brightness row, when Sodium renders it, is taller with a wider track and 4 calibration icons + captions below, reacting to `Minecraft.options.gamma()`. Terminal task.

- [ ] **Step 1: Clone + read Sodium 0.9.0 internals.** `git clone https://github.com/CaffeineMC/sodium` (temp dir), checkout tag `mc26.2-0.9.0`. Read `client/gui/widgets/OptionListWidget.java` (the row loop: how `entryHeight` + per-row `Dim2i` are computed/advanced), `client/gui/options/control/SliderControl.java` (the inner `SliderControlElement` + `extractRenderState` signature in the 26.2 render-state model, and how it reads `Layout.SLIDER_WIDTH`/`getSliderX`), `client/gui/Layout.java` (constants), and how to get the bound `Option` + its `Identifier` from a control element. Record the exact signatures you'll target (append to SODIUM-NOTES under "verified 0.9.0 internals").
- [ ] **Step 2: Taller brightness row — `OptionListWidgetMixin`.** Mixin the row-build loop so the brightness option's row (`Dim2i` height + the `listHeight` advance) gets extra height (enough for icons under the slider, e.g. `+ font.lineHeight*4`), ONLY when the option being laid out is `sodium:general.gamma`; all other rows keep `Layout.entryHeight`. Use the option ID to scope. (Mechanism — `@ModifyVariable`/`@Redirect`/`@Inject` on the per-row `Dim2i` construction + the height advance — chosen from Step 1's reading; both must agree so layout stays consistent.)
- [ ] **Step 3: Wider track + 4 icons — `SliderControlElementMixin`.** Mixin the brightness slider element's `extractRenderState` (scoped by option ID): widen the track for this option, and after the normal slider draw, render 4 compact `CalibrationPanel` icons + captions in the extra vertical space below the track, sized to fit the row width, each tinted/eased from the current gamma (`Brightness.panelVisibility(Minecraft.options.gamma().get(), threshold)`), reusing the 4 thresholds/textures/captions from `BrightnessSetupScreen`'s panels (creeper 1.35 / deepslate 1.1 / coal_ore 0.6 / diamond_ore 0.2). Wrap icon rendering in try/catch (as `CalibrationPanel` does). For non-brightness sliders the mixin is a no-op (early return).
- [ ] **Step 4: Register the two mixins** in `betterbrightness.sodium.mixins.json` `client`: `["OptionListWidgetMixin", "SliderControlElementMixin"]`.
- [ ] **Step 5: Build.** `./gradlew build` → SUCCESSFUL both loaders (compiles against Sodium internals via the compileOnly full jar). No mixin/refmap errors at build. (Mixin APPLICATION + visuals = controller runtime-verify with Sodium installed.)
- [ ] **Step 6: Commit.** `git commit -m "feat(sodium): wider brightness slider + 4 inline calibration icons (internal mixins)"`

If a target signature can't be confirmed from the Sodium source, or a mixin can't be scoped to the brightness option only, report BLOCKED with the exact Sodium class/method and what you found — do not ship a mixin that alters all sliders.

---

## Self-Review

- **Spec coverage:** Sodium compileOnly dep + soft mixin config → S1 ✓; range 0–200% via API → S2 ✓; wider slider + 4 inline icons via internal mixins scoped to brightness → S3 ✓; soft-dep (works w/o Sodium) → S1 `required:false` + compileOnly + S2 entrypoint-only-if-Sodium ✓; reuse CalibrationPanel → S3 ✓; Fabric+Quilt+NeoForge one codebase → S1/S2 loader wiring ✓; runtime verify both scenarios → noted for controller in S2/S3.
- **Placeholder scan:** S2's API overlay code is concrete modulo the verified builder-entry path (flagged to confirm from USAGE.md — a real API unknown, not a vague TODO). S3 is approach + exact target classes + a mandatory "read the 0.9.0 source and record signatures" gate (Sodium internals genuinely require source inspection, like the MC API-NOTES pattern) — not hand-wavy: it names the classes, the scoping rule (by option ID), and the render reuse.
- **Type consistency:** `sodium:general.gamma` ID, the gamma binding shape (`set(v*0.01)`/`get()/0.01`), and the 4 panel thresholds are used identically across S2/S3 and match the existing `BrightnessSetupScreen`. `CalibrationPanel` reused with its existing signature.
- **Known risk:** S3 internal mixins are HIGH-fragility/version-locked (per spec, user-accepted); they apply only at runtime → controller must verify with Sodium installed AND verify the no-Sodium path still boots with the vanilla button.

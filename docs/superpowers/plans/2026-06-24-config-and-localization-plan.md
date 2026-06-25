# Config + Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use `- [ ]`.

**Goal:** Configurable max brightness (default 100%, raisable to 500%) reachable from the mod list, and full localization of all user-facing strings into every MC locale + hand-written joke locales.

**Architecture:** Cloth Config `AutoConfig` config in `common`; loader-specific mod-list hooks (NeoForge native, Fabric/Quilt via optional Mod Menu); a Python translation script generating per-locale lang JSON.

**Tech stack:** MC 26.2, Architectury multiloader, loom-no-remap (plain mod deps, no refmap). Cloth Config `26.2.155` (required), Mod Menu `20.0.0-beta.4` (optional), Architectury `21.0.2`. Python + `deep-translator`.

## Global Constraints

- MC 26.2 deobfuscated → mixins `remap=false`, no refmap. Modrinth maven already configured (used for Sodium).
- Default `maxBrightnessPercent = 100` (gamma 1.0 = vanilla). Range **100–500**.
- Cloth Config = **required** dep; Mod Menu = **optional** (compileOnly + `suggests`, not `depends`).
- Sodium stays soft/`required:false`; the game must boot without Sodium AND without Mod Menu.
- `GammaRange` pinned to ceiling `[0.0, 5.0]` (constant) so config changes need no restart; the *visible slider max* reads `maxPercent()` live.
- All user-facing strings are translatable keys in `assets/betterbrightness/lang/`.
- Existing layout/paths: deps in `common/build.gradle` (`compileOnly`), `fabric/build.gradle` (`modImplementation`/`modCompileOnly`), `neoforge/build.gradle`. Modrinth coords: `maven.modrinth:cloth-config:26.2.155+fabric|+neoforge`, `maven.modrinth:modmenu:20.0.0-beta.4`. Common init: see `BetterBrightness.java` / client bootstrap.

---

### Task C1: Dependencies + config model + registration

**Files:**
- Modify: `common/build.gradle` (compileOnly cloth-config fabric), `fabric/build.gradle` (modImplementation cloth-config-fabric; modCompileOnly modmenu), `neoforge/build.gradle` (modImplementation/compileOnly cloth-config-neoforge)
- Create: `common/.../BetterBrightnessConfig.java` (Cloth `@Config` + `ConfigData`, field `maxBrightnessPercent` default 100, `@BoundedDiscrete(100,500)`, `validatePostLoad` clamp; static `maxPercent()` accessor returning live value or 100 fallback)
- Modify: common client bootstrap to `AutoConfig.register(BetterBrightnessConfig.class, GsonConfigSerializer::new)` once
- Test: `common/src/test/.../BetterBrightnessConfigTest.java` (default == 100; clamp below 100 → 100, above 500 → 500)

**Interfaces produced:** `BetterBrightnessConfig.maxPercent()` → `int` (live config max, fallback 100). Consumed by C2/C3.

- [ ] Add cloth-config deps to all three build files; refresh/build to confirm resolution.
- [ ] Write the config class + `maxPercent()` accessor (null-safe before AutoConfig.register runs).
- [ ] Register AutoConfig in common client init.
- [ ] Test: default + clamp; `:common:test` green.
- [ ] `./gradlew :fabric:build :neoforge:build` green (config registers at runtime, JSON written).
- [ ] Commit.

### Task C2: Wire configurable max into Brightness + sliders + GammaRange

**Files:**
- Modify: `common/.../Brightness.java` — add tested pure overload `sliderToGamma(double t, int maxPercent)` = `clamp01(t) * (maxPercent/100.0)`; make `sliderToGamma(double t)` delegate with `BetterBrightnessConfig.maxPercent()`
- Modify: `common/.../GammaRange.java` + `mixin/OptionsGammaMixin.java` — range fixed `[0.0, 5.0]` (ceiling), no longer 2.0
- Modify: `common/.../client/BrightnessSetupScreen.java` — slider label/mapping use `maxPercent()` read live on open (the `%` shown and the gamma it writes)
- Modify: `common/.../sodium/BetterBrightnessSodiumConfig.java` — `setRange(0, maxPercent(), 1)` live
- Test: extend `BrightnessTest` — `sliderToGamma(t, maxPercent)` endpoints/clamp for max ∈ {100, 200, 500}

**Interfaces consumed:** `BetterBrightnessConfig.maxPercent()`.

- [ ] TDD: failing test for `sliderToGamma(t, maxPercent)`.
- [ ] Implement overload + delegate; `GammaRange`/`OptionsGammaMixin` ceiling `[0,5]`.
- [ ] Wire setup screen + Sodium range to live `maxPercent()`.
- [ ] `:common:test` green; `:fabric:build :neoforge:build` green.
- [ ] Runtime sanity deferred to controller (default 100 → 0–100%; config 500 → 0–500%).
- [ ] Commit.

### Task C3: Config screen reachable from the mod list

**Files:**
- Create: `fabric/.../BetterBrightnessModMenu.java` (`implements ModMenuApi`, `getModConfigScreenFactory()` → `parent -> AutoConfig.getConfigScreen(BetterBrightnessConfig.class, parent).get()`)
- Modify: `fabric/src/main/resources/fabric.mod.json` — add `entrypoints.modmenu`; add Mod Menu as **optional** (`suggests`, NOT `depends`)
- NeoForge: register the Cloth/AutoConfig screen as the mod-list config screen (via Cloth's NeoForge integration or `IConfigScreenFactory` in the NeoForge entry); verify the native "Config" button appears
- Modify: `neoforge/build.gradle` only if a runtime cloth-config-neoforge dep is needed for the screen

**Interfaces consumed:** `BetterBrightnessConfig`, `AutoConfig`.

- [ ] Fabric Mod Menu entrypoint + optional dep declaration.
- [ ] NeoForge config-screen registration.
- [ ] `:fabric:build :neoforge:build` green; boots WITHOUT Mod Menu (no crash, no hard dep).
- [ ] Commit.

### Task C4: Localize remaining literals → keys

**Files:**
- Modify: `common/.../mixin/VideoSettingsScreenMixin.java` (button label → `betterbrightness.button.setup`), `client/BrightnessSetupScreen.java` ("Done" → `betterbrightness.button.done`; slider label → `betterbrightness.slider.brightness` with `%s`), and any other literal user-facing strings
- Modify: `common/src/main/resources/assets/betterbrightness/lang/en_us.json` — add `button.setup`, `button.done`, `slider.brightness`, AutoConfig keys (`text.autoconfig.betterbrightness.title`, `.option.maxBrightnessPercent`, `.option.maxBrightnessPercent.@Tooltip`)

**Interfaces produced:** the final canonical `en_us.json` (input to C5).

- [ ] Replace literals with `Component.translatable`.
- [ ] Complete `en_us.json` (all keys, correct `%s`/`%%` in slider label).
- [ ] `:fabric:build` green; English renders (controller-verifiable).
- [ ] Commit.

### Task C5: Translation script + generated locales + joke locales

**Files:**
- Create: `tools/translate.py` (reads `en_us.json`; `deep-translator` GoogleTranslator; `MC_LOCALE→GOOGLE_LANG` map for MC's ~115 locales; masks `%s`/`%%`/`%1$s` before translate, unmasks after; writes `<locale>.json`; `en_ud` via deterministic char-flip; skips joke/constructed locales; prints OK/skip summary; re-runnable)
- Create: `tools/README` note (how to run: `pip install deep-translator`, `python tools/translate.py`)
- Create (hand-written): `assets/betterbrightness/lang/en_pt.json` (Pirate Speak), `lol_us.json` (LOLCAT)
- Generated: `assets/betterbrightness/lang/<locale>.json` for all mapped locales (committed)
- Test: a small self-check in `translate.py` (`__main__` guard or `--selftest`) asserting placeholder masking round-trips and en_ud flip is reversible on ASCII

**Interfaces consumed:** final `en_us.json` from C4.

- [ ] Write `translate.py` with placeholder protection + en_ud flip + self-check.
- [ ] (Controller runs it — network) generate locale files; verify a sample (`ru_ru`, `de_de`) is sane and placeholders intact.
- [ ] Hand-write `en_pt`, `lol_us`.
- [ ] `:fabric:build` green (resources pack); commit generated + hand-written locales + script.

---

## Self-Review notes
- Spec coverage: C1 config+deps, C2 wiring+ceiling, C3 mod-list screen, C4 keys, C5 translate+joke — all spec sections covered.
- Type consistency: `maxPercent()` int everywhere; `sliderToGamma(t,maxPercent)` matches test.
- Cloth/ModMenu 26.2-beta APIs may differ slightly from older versions — implementers verify the actual API surface of `26.2.155` / `20.0.0-beta.4` before coding (controller oversees between tasks).

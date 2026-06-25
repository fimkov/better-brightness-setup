# Sodium 0.9.x (MC 26.2) integration notes — Better Brightness Setup

Read-only research. Nothing in our repo was changed. Source read from the
`mc26.2-0.9.0` tag of `https://github.com/CaffeineMC/sodium`
(commit `bf93ed8`, "Port to Minecraft 26.2 (#3735)", 2026-06-16). All class/file
paths below are relative to the Sodium repo root unless stated otherwise.

## TL;DR verdict

- **Sodium 0.9.x ships a NEW, fully public config API.** The historical "no
  config API" stance is over (as of the 0.8/0.9 line). Mods register via an
  entrypoint and a builder DSL. We should use the API, not mixins, for almost
  everything we need.
- **Brightness binds to vanilla gamma but bypasses the vanilla `OptionInstance`
  ValueSet.** It writes `gamma().set(value * 0.01D)` directly and hardcodes
  `setRange(0, 100, 1)`. So our `[0,2]` ValueSet-widening mixin does NOT affect
  Sodium's slider — extending to 200% is Sodium-specific work (one API overlay
  call), but it's trivial and supported.
- **Widening the slider + the 4 inline reference icons under it are TWO different
  difficulty tiers.** Widening the slider's *value range* is a clean one-line API
  overlay. Making the slider *physically wider* and drawing *4 icons below it in
  the same row* is NOT expressible in the API and requires mixins, because option
  rows are a fixed uniform height and the slider width is a global constant.

---

## 1. Options GUI architecture (0.9.x)

The entire GUI lives in the shared `common/` module
(`common/src/main/java/net/caffeinemc/mods/sodium/client/...`), so one
integration covers Fabric + NeoForge (+ Quilt via Fabric).

Two cooperating layers:

### (a) Structure / data model — `client/config/structure/`
- `Config`, `ModOptions`, `Page` / `OptionPage` / `ExternalPage`,
  `OptionGroup`, and the option classes: `Option` (base),
  `StatefulOption<T>`, `IntegerOption`, `BooleanOption`, `EnumOption`,
  `ExternalButtonOption`, plus `OptionOverlay` / `OptionOverride`.
- Built by the public builder DSL in `client/config/builder/*Impl.java`
  (e.g. `IntegerOptionBuilderImpl`, `ModOptionsBuilderImpl`, `ConfigBuilderImpl`).
- `client/config/ConfigManager.java` is the central registry; it discovers
  entrypoints and assembles the `Config`.

### (b) Control / rendering — `client/gui/options/control/`
- `Control` (interface): `getOption()`, `createElement(screen, list, Dim2i,
  theme)`, `getMaxWidth()`.
- `ControlElement` (abstract widget, extends `AbstractWidget`): renders the row
  background + label, handles focus/hover.
- Concrete controls: `SliderControl` (+ inner `SliderControlElement`),
  `CyclingControl`, `TickBoxControl`, `ExternalButtonControl`,
  `StatefulControlElement`.
- The screen: `client/gui/VideoSettingsScreen.java`.
- Page/option list widgets: `client/gui/widgets/OptionListWidget.java`,
  `PageListWidget.java`.
- Layout constants: `client/gui/Layout.java`.

### How a row is built and sized — THE KEY CONSTRAINT
In `OptionListWidget` (lines ~65, ~111, ~164):
```java
this.entryHeight = Layout.entryHeight(this.font);   // = font.lineHeight * 2
...
var element = control.createElement(screen, this,
    new Dim2i(x, y + listHeight, width, this.entryHeight).insetLeft(...), theme);
listHeight += this.entryHeight;
```
- **Every option row is the SAME fixed height** (`Layout.entryHeight(font) =
  font.lineHeight * 2`, see `Layout.java:53`). The control does not get to
  declare its own height; the list hands it a fixed `Dim2i` and advances by the
  same constant. There is no per-control height hook.
- The slider width is the global constant `Layout.SLIDER_WIDTH = 90`
  (`Layout.java:31`), `SLIDER_HEIGHT = 10`. `SliderControlElement` reads these
  directly (`getSliderX()`/`extractRenderState`). There is no per-option width.

**Implication:** A control *cannot* be taller than one standard row, and cannot
be wider than `SLIDER_WIDTH`, through any public mechanism. Fitting 4 icons
*below* the slider in the same option's area requires either (i) a mixin that
makes that one row taller + a custom render, or (ii) abandoning "inline in the
row" and putting the icons elsewhere (own page / tooltip / external screen).

## 2. The Brightness option specifically

Defined in `client/gui/SodiumConfigBuilder.java` (lines ~172–181):
```java
builder.createIntegerOption(Identifier.parse("sodium:general.gamma"))
    .setStorageHandler(this.vanillaStorage)
    .setName(Component.translatable("options.gamma"))
    .setTooltip(Component.translatable("sodium.options.brightness.tooltip"))
    .setValueFormatter(ControlValueFormatterImpls.brightness())
    .setRange(0, 100, 1)
    .setDefaultValue(50)
    .setBinding(value -> this.vanillaOpts.gamma().set(value * 0.01D),
                () -> (int) (this.vanillaOpts.gamma().get() / 0.01D));
```

- **Option ID:** `sodium:general.gamma` (note: ID says "gamma", display name is
  the vanilla "Brightness"/`options.gamma` key, tooltip key is
  `sodium.options.brightness.tooltip`). This ID is our overlay/replace target.
- **Storage:** binds to vanilla `Minecraft.options.gamma()` — read AND write.
  The slider stores an int 0–100; binding multiplies by `0.01` on write and
  divides by `0.01` on read.
- **Range / ValueSet:** hardcoded `setRange(0, 100, 1)`. It calls
  `gamma().set(double)` **directly**, bypassing the vanilla `OptionInstance`'s
  `ValueSet`. Therefore Sodium **does NOT respect our `[0,2]` widening mixin** on
  vanilla's gamma `OptionInstance`. To get 0–200% in Sodium we must change
  Sodium's option (range + binding multiplier), independently of our vanilla
  mixin.
- **Formatter:** `ControlValueFormatterImpls.brightness()` returns
  `options.gamma.min` at 0, `options.gamma.default` at 50, `options.gamma.max`
  at 100, else a percent. Purely cosmetic labels; does not constrain the range.
- The brightness value formatter and `ControlValueFormatterImpls` are **not**
  part of the public API package (only the `ControlValueFormatter` interface is,
  at `api/.../config/option/ControlValueFormatter.java`), but we can supply our
  own lambda formatter via the API.

## 3. Extension API — IT EXISTS (major change)

Public API package: `common/src/api/java/net/caffeinemc/mods/sodium/api/config/`
(this is a separate `api` source set, published as a standalone artifact).
Authoritative docs: `.../api/config/USAGE.md` (read it; it is the spec).

### Entrypoint
- Interface `api/config/ConfigEntryPoint` with
  `registerConfigEarly(ConfigBuilder)` (optional) and
  `registerConfigLate(ConfigBuilder)` (the normal one).
- **Fabric:** declare entrypoint key `sodium:config_api_user` in
  `fabric.mod.json` → class implementing `ConfigEntryPoint`.
  (`ConfigManager.CONFIG_ENTRY_POINT_KEY = "sodium:config_api_user"`;
  discovery in `fabric/.../config/ConfigLoaderFabric.java`.)
- **NeoForge:** either the same metadata key in `neoforge.mods.toml`
  (`[modproperties.<modid>] "sodium:config_api_user" = "..."`), OR annotate the
  class with `@ConfigEntryPointForge("<modid>")`
  (`api/config/ConfigEntryPointForge.java`; discovery in
  `neoforge/.../config/ConfigLoaderForge.java` + an `EntrypointMixin`).

### Builders (`api/config/structure/`)
`ConfigBuilder` (root, passed to entrypoint) → `registerOwnModOptions()` /
`registerModOptions(...)` → `ModOptionsBuilder`. Plus factories:
`createOptionPage`, `createOptionGroup`, `createBooleanOption`,
`createIntegerOption`, `createEnumOption`, `createExternalButtonOption`,
`createExternalPage`, `createColorTheme`.

### Modifying ANOTHER mod's / Sodium's existing option — `ModOptionsBuilder`
- `registerOptionReplacement(Identifier target, OptionBuilder replacement)` —
  fully replace an option (keep or change its ID).
- `registerOptionOverlay(Identifier target, OptionBuilder overlay)` — partially
  change an existing option; only the properties you set are overridden.
- `registerFlagHook(...)` — run code after an option with given flags is applied.

These are exactly the hooks to retarget `sodium:general.gamma`. For our
"extend to 200%" requirement:
```java
modOptions.registerOptionOverlay(
    Identifier.parse("sodium:general.gamma"),
    builder.createIntegerOption(Identifier.parse("sodium:general.gamma"))
        .setRange(0, 200, 1)
        .setValueFormatter(v -> /* our 0–200% label */)
        .setBinding(v -> vanillaOpts.gamma().set(v * 0.01D),
                    () -> (int)(vanillaOpts.gamma().get() / 0.01D))
);
```
(`IntegerOptionBuilder.setRange(min,max,step)` / `setValidator` /
`setValueFormatter` are all public — `api/.../structure/IntegerOptionBuilder.java`.)

### What the API CANNOT do
The API only exposes the **built-in control types** (integer slider, enum,
boolean, external button → opens a `Screen`). There is **no** public hook to:
- supply a custom `Control`/`ControlElement` renderer,
- make a single option row taller than `Layout.entryHeight`,
- make a slider wider than `Layout.SLIDER_WIDTH`,
- draw arbitrary content (our 4 icons) inside an option's row area.

So range-widening = API. Physical widening + inline icons = mixin.

## 4. Custom control feasibility (the 4 inline icons + wider slider)

The user's literal spec ("wider slider + 4 reference icons BELOW it, INSIDE the
brightness option's container, in Sodium's row") is **not achievable through the
public API** and is only achievable with mixins that are inherently
version-fragile. Concretely:

**To widen the slider track physically:** `Layout.SLIDER_WIDTH` is a global
constant used by all sliders and by width/label-truncation math in
`SliderControlElement` and `ControlElement.truncateLabelToFit`. Changing it
globally is easy (mixin/AW on `Layout`) but widens *every* slider, and the row
width is itself bounded by `Layout.OPTION_WIDTH = 210`. A brightness-only wider
slider means a custom `SliderControlElement` (mixin/override) — fragile.

**To get a taller row + icons under the slider:** the row height is fixed in
`OptionListWidget` (`this.entryHeight` applied to every `Dim2i`). Options to
draw 4 icons under the brightness slider, in rough order of cleanliness:

1. **Cleanest given the constraints (RECOMMENDED): don't fight the row.** Use the
   API. Either:
   - keep Sodium's brightness slider, overlay it to 0–200% (API, robust), AND
   - add a small **calibration page** or an **external-button option** (API
     `createExternalButtonOption` / `createExternalPage`) that opens our own
     `Screen` showing the 4 calibration tiles (reuse our existing
     `CalibrationPanel` / `BrightnessSetupScreen`). Icons live on our own
     surface, which we fully control, instead of inside Sodium's fixed row.
   This is fully supported, survives Sodium GUI refactors, and still gives the
   user the slider + the 4 reference tiles reacting to gamma.

2. **If "inline in the row" is a hard requirement: mixin.** Two coordinated
   mixins, all version-locked to Sodium internals:
   - `OptionListWidget` (mixin on the row-layout loop, ~lines 111/164) to give
     just the brightness row a larger `entryHeight` and advance `listHeight`
     accordingly — non-trivial because height is a single field used for all
     rows; you'd need per-option height logic.
   - A custom `ControlElement` for the brightness option (replace via
     `IntegerOption.createControl()` — which is package-private and hardcodes
     `new SliderControl(this)`, so this needs a mixin on `IntegerOption` or on
     `SliderControl.SliderControlElement.extractRenderState`) to draw the 4
     icons in the extra vertical space below the track.
   Relevant exact members: `Layout.entryHeight(Font)` (`Layout.java:53`),
   `Layout.SLIDER_WIDTH`/`SLIDER_HEIGHT` (`Layout.java:31-32`),
   `OptionListWidget#init/build` (the `createElement` + `listHeight +=
   entryHeight` loop), `SliderControl.SliderControlElement#extractRenderState`,
   `IntegerOption#createControl()`.
   **Fragility:** HIGH and version-locked. These are non-API internals in the
   shared `common` module; Sodium reorganizes the GUI between minor versions
   (the row uses the new `GuiGraphicsExtractor`/`extractRenderState` model in
   26.2). Every Sodium update can break these mixins. Sodium also discourages
   mixing into its internals (USAGE.md §Overview).

**Recommendation:** ship option (1) — API overlay for the 0–200% range plus an
API external page/button that opens our own calibration screen with the 4 tiles.
If the team insists on truly-inline icons, accept the maintenance cost of (2)
and pin a Sodium version.

## 4b. Verified 0.9.0 internals (S3 — exact targets, from `mc26.2-0.9.0` jar bytecode)

All `remap = false` (Sodium internals are not Mojang-mapped). Verified by `javap -c -p` on
`sodium-mc26.2-0.9.0-fabric.jar`.

**Option identity (scoping):**
- `net.caffeinemc.mods.sodium.client.config.structure.Option#id` — `final net.minecraft.resources.Identifier`,
  **package-private, no getter**. Read via an `@Accessor("id")` mixin interface (`OptionIdAccessor`).
- Brightness option id = `Identifier.parse("sodium:general.gamma")`
  (`SodiumConfigBuilder` ~line 173). Display name is `options.gamma`; the id path is literally `general.gamma`.
- `Control#getOption()` → `Option` (public, on the `Control` interface).
- `SliderControl$SliderControlElement#getOption()` → covariant `IntegerOption` (public).

**Row layout — `client/gui/widgets/OptionListWidget`:**
- Field `private int entryHeight` (`#97`), set once per rebuild to `Layout.entryHeight(this.font)`.
- Row build loop, in BOTH `private int renderAllPages(Screen,int,int,int)` and
  `private int renderFilteredOptions(Screen,int,int,int)`:
  - exactly ONE `Control.createElement(Screen, AbstractOptionList, Dim2i, ColorTheme)ControlElement`
    INVOKEINTERFACE per method (renderAllPages off. 382; renderFilteredOptions off. 256);
  - immediately after `controls.add(element)` the `listHeight += this.entryHeight` reads `entryHeight`
    (GETFIELD `#97`; renderAllPages off. 411; renderFilteredOptions off. 285).
- S3 targets (`OptionListWidgetMixin`, `@Mixin(OptionListWidget.class)`):
  1. `@Redirect` the `Control.createElement(...)` INVOKE (method `{renderAllPages, renderFilteredOptions}`)
     → for the gamma option, hand a taller `Dim2i` (`+ font.lineHeight*4`) and stash the delta.
  2. `@Redirect` the `entryHeight` GETFIELD, `@Slice(from = INVOKE createElement)` so only the
     post-`createElement` read (the `listHeight +=`) is affected → returns `entryHeight + delta`.
  Result: only the gamma row is taller; the `Dim2i` height and the `listHeight` advance stay in sync;
  every other row + all headers are byte-identical (delta 0).

**Slider element — `client/gui/options/control/SliderControl$SliderControlElement` (package-private):**
- Target by string: `@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl$SliderControlElement")`.
- `public void extractRenderState(GuiGraphicsExtractor, int, int, float)` — 26.2 render-state draw path.
  S3 `@Inject(method = "extractRenderState", at = @At("TAIL"))`.
- `@Shadow`-able members (concretely DECLARED on `SliderControlElement`): `getOption()IntegerOption`,
  `getSliderX()I`, `getSliderY()I`, `getThumbPositionForValue(I)D`.
- `getX()I`/`getLimitX()I`/`getLimitY()I`/`getCenterY()I` are **interface-DEFAULT methods on
  `net.caffeinemc.mods.sodium.client.gui.Dimensioned`** (`AbstractWidget implements Dimensioned`),
  only INHERITED by `SliderControlElement` — they are **NOT `@Shadow`-able** (Sponge Mixin throws
  `InvalidMixinException` "method … was not located in the target class", failing the whole mixin →
  Sodium's brightness control fails to load). Call them via `((Dimensioned)(Object) this).getX()` etc.
  (Same gotcha class as the vanilla `OptionsSubScreen.list` field.) `Dimensioned` is `public`.
- `Layout.SLIDER_WIDTH = 90` / `SLIDER_HEIGHT = 10` are `public static final int` → **inlined as
  `bipush` constants** in `extractRenderState` (no GETSTATIC). So the track cannot be widened by a field
  redirect; S3 draws its own wider track + thumb for the gamma row only and leaves SLIDER_WIDTH global.
- `Colors.FOREGROUND` (`public static final int`) used for the track/thumb tint.
- `GuiGraphicsExtractor#fill(int,int,int,int,int)` and `#blit(RenderPipeline, Identifier, ...)` are the
  draw primitives (same ones `AbstractWidget.drawRect`/`CalibrationPanel` use).

## 5. NeoForge vs Embeddium

**Target Sodium's own NeoForge build, NOT Embeddium.** Sodium 0.9.0 ships a
first-party NeoForge artifact built from the same `common/` GUI code:
- `neoforge/` is a real subproject (`settings.gradle.kts` includes it).
- `neoforge/src/mod/java/.../config/ConfigLoaderForge.java` and
  `EntrypointMixin.java` wire up the SAME config API on NeoForge.
- Modrinth publishes `sodium-neoforge-0.9.0+mc26.2.jar`.

So a single integration against the Sodium config API covers
**Fabric + Quilt + NeoForge** with one codebase. Embeddium is not needed for
26.2 and would be a separate, divergent fork — avoid it.

## 6. Dev dependency coordinates (compileOnly / modCompileOnly)

Two valid sources. The **CaffeineMC API-only artifact** is the right
compile-time dependency (matches USAGE.md; smaller; just the `api` package).
The **Modrinth full mod** is what you'd use as a runtime/dev mod.

### CaffeineMC maven (API only — preferred for compile)
Repo:
```
maven { name = "CaffeineMC"; url = uri("https://maven.caffeinemc.net/releases") }
```
Confirmed-existing artifacts (verified on the maven, incl. NeoForge jar+pom+sources):
- Fabric:   `net.caffeinemc:sodium-fabric-api:0.9.0+mc26.2`
- NeoForge: `net.caffeinemc:sodium-neoforge-api:0.9.0+mc26.2`

Per USAGE.md the dependency configuration is: `modImplementation` on older
Fabric (1.21.11), `implementation` on Fabric 1.21.12+ and NeoForge. For
**compile-only dev** use `compileOnly` (NeoForge) / `modCompileOnly` (Fabric
Loom) since we don't ship Sodium:
```groovy
// Fabric (Loom)
modCompileOnly "net.caffeinemc:sodium-fabric-api:0.9.0+mc26.2"
// NeoForge
compileOnly     "net.caffeinemc:sodium-neoforge-api:0.9.0+mc26.2"
```

### Modrinth maven (full mod — for runtime test, or if you prefer a single coord)
Repo:
```
maven { url = uri("https://api.modrinth.com/maven") }
```
Exact version slugs (verified via Modrinth API, game_versions=["26.2"]):
- Fabric:   `maven.modrinth:sodium:mc26.2-0.9.0-fabric`   (file `sodium-fabric-0.9.0+mc26.2.jar`)
- NeoForge: `maven.modrinth:sodium:mc26.2-0.9.0-neoforge` (file `sodium-neoforge-0.9.0+mc26.2.jar`)

Note: the Modrinth full-mod jar bundles the API package, so
`modCompileOnly`/`compileOnly` against it also works if you'd rather not add the
CaffeineMC repo. For a clean compile-only dependency against just the public
API, prefer the CaffeineMC `*-api` artifacts above.

## Risks summary

- **Range widening (API overlay): low risk.** Public, documented, survives
  refactors. Only Sodium-version assumption: the option ID
  `sodium:general.gamma` and the gamma binding shape stay stable.
- **Inline-icons-in-row (mixin path): high risk.** Touches non-API internals
  (`OptionListWidget`, `SliderControlElement`, `IntegerOption#createControl`,
  `Layout` constants), all in shared `common`; version-locked; against Sodium's
  stated intent. Prefer the API external-page/button alternative.
- **API maturity:** the API is new (0.8/0.9 line) and USAGE.md says it may not
  cover all cases and invites bug reports — treat it as evolving across minor
  Sodium versions; pin the Sodium version you build against.

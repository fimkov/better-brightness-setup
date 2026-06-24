# Brightness Setup Screen Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the first-launch brightness screen look good and read clearly: square (un-stretched) textures in a horizontal row with target labels, an instruction line, tasteful fade-in + eased tile brightness, a 0–200% scale consistent with vanilla, and open it only after vanilla's onboarding.

**Architecture:** Edits to the existing v1 mod (Architectury, MC 26.2, Fabric + NeoForge). The screen (`BrightnessSetupScreen`) and tile (`CalibrationPanel`) are reworked; the trigger gains an onboarding gate; the existing gamma mixin gains a second `@ModifyArg` for the vanilla slider label. Pure helpers go in `Brightness` (unit-tested).

**Tech Stack:** Java 25, MC 26.2 render-state GUI (`GuiGraphicsExtractor`, `extractRenderState`), Sponge Mixin (`@ModifyArg`), JUnit 5.

## Global Constraints

- Target MC 26.2, Java 25, mod id `betterbrightness`, package `io.github.fimkov.betterbrightness`. Fabric + NeoForge (Forge deferred).
- 26.2 GUI is render-state: draw via `GuiGraphicsExtractor` inside `extractRenderState(GuiGraphicsExtractor, int, int, float)`; `super.extractRenderState(...)` renders added widgets; background is engine-drawn. Texture blit: `g.blit(RenderPipelines.GUI_TEXTURED, Identifier, x, y, float u, float v, int w, int h, int srcW, int srcH, int texW, int texH, int argbColor)` — alpha byte of `argbColor` tints. `g.centeredText(Font, Component, int x, int y, int argbColor)` — alpha byte tints text.
- Scale: setup slider label = `"Brightness: NNN%"`, `NNN = round(gamma*100)`, gamma∈[0,2]. Vanilla gamma label: `0%→"Moody"` (`options.gamma.min`), `100%→"Bright"` (`options.gamma.max`), `200%→"Brightest"` (`betterbrightness.options.gamma.brightest`), else `"NNN%"`.
- Tile→label by descending threshold: creeper `1.6`=hidden, deepslate `1.1`=faint, coal_ore `0.6`=clear, diamond_ore `0.2`=bright.
- No tile may overlap the slider/Done at any GUI scale. Textures must be drawn 1:1 square (no stretch).
- Animation timing uses `System.currentTimeMillis()` (mod runtime — allowed). Tasteful: fade-in + eased brightness + soft frame only; no staggered reveals, no pulsing.
- Mixins apply only at RUNTIME under loom-no-remap — the build won't catch a mis-targeted/mis-packaged mixin; the gamma-label change must be runtime-verified.
- Existing behavior preserved: gamma persistence ([0,2] `GammaRange` + ValueSet `@ModifyArg`), first-launch marker, Esc-marks-done, per-tile try/catch placeholder.

---

## File Structure

- `common/.../Brightness.java` — add `toPercent(double)` and `lerp(double,double,double)` (pure).
- `common/.../BrightnessSetup.java` — add onboarding gate to `shouldOpen()`.
- `common/.../client/CalibrationPanel.java` — square texture, eased `displayedVis`, label below, soft frame, fade alpha. Render signature changes.
- `common/.../client/BrightnessSetupScreen.java` — row layout (overlap-proof), instruction line, fade-in, slider label `%`.
- `common/.../mixin/OptionsGammaMixin.java` — add caption-function `@ModifyArg` (index 2).
- `common/src/main/resources/assets/betterbrightness/lang/en_us.json` — add `instruction`, `options.gamma.brightest`; confirm `title` + 4 panel keys.

---

### Task 1: Pure helpers `toPercent` + `lerp` (TDD)

**Files:**
- Modify: `common/src/main/java/io/github/fimkov/betterbrightness/Brightness.java`
- Test: `common/src/test/java/io/github/fimkov/betterbrightness/BrightnessTest.java`

**Interfaces:**
- Produces: `static int toPercent(double gamma)` → `Math.round(gamma*100)` as int. `static double lerp(double from, double to, double t)` → `from + (to-from)*clamp01(t)`. Consumed by `CalibrationPanel` (Task 4: lerp) and `BrightnessSetupScreen` (Task 5: toPercent).

- [ ] **Step 1: Add failing tests** to `BrightnessTest.java` (append, keep existing tests):

```java
@Test void toPercentMapsGammaRange() {
    assertEquals(0,   Brightness.toPercent(0.0));
    assertEquals(100, Brightness.toPercent(1.0));
    assertEquals(200, Brightness.toPercent(2.0));
    assertEquals(130, Brightness.toPercent(1.3));
}
@Test void lerpEndpointsAndMidpoint() {
    assertEquals(0.0, Brightness.lerp(0.0, 1.0, 0.0), 1e-9);
    assertEquals(1.0, Brightness.lerp(0.0, 1.0, 1.0), 1e-9);
    assertEquals(0.5, Brightness.lerp(0.0, 1.0, 0.5), 1e-9);
    assertEquals(1.0, Brightness.lerp(0.0, 1.0, 5.0), 1e-9); // t clamped to 1
}
```

- [ ] **Step 2: Run to verify fail.** Run: `./gradlew :common:test --tests '*BrightnessTest'` → FAIL (`toPercent`/`lerp` not found).

- [ ] **Step 3: Implement** in `Brightness.java` (add these methods; `clamp01` already exists as private — reuse it):

```java
/** gamma in [0,2] -> integer percent in [0,200]. */
public static int toPercent(double gamma) {
    return (int) Math.round(gamma * 100.0);
}

/** Linear interpolate from->to by t, with t clamped to [0,1]. */
public static double lerp(double from, double to, double t) {
    return from + (to - from) * clamp01(t);
}
```

- [ ] **Step 4: Run to verify pass.** Run: `./gradlew :common:test --tests '*BrightnessTest'` → PASS (all, incl. existing).

- [ ] **Step 5: Commit.**
```bash
git add common/src/main/java/io/github/fimkov/betterbrightness/Brightness.java common/src/test/java/io/github/fimkov/betterbrightness/BrightnessTest.java
git commit -m "feat: Brightness.toPercent + lerp helpers"
```

---

### Task 2: Open only after vanilla onboarding

**Files:**
- Modify: `common/src/main/java/io/github/fimkov/betterbrightness/BrightnessSetup.java`

**Interfaces:**
- Consumes: `Marker.isDone`, `Platform.getConfigFolder()` (existing). `Minecraft.getInstance().options.onboardAccessibility` (public boolean field, VERIFIED in `Options.java:944`; `true` = onboarding not yet finished).
- Produces: unchanged public surface; `shouldOpen()` gains the onboarding condition.

- [ ] **Step 1: Update `shouldOpen()`.** It currently returns `!shownThisSession && !Marker.isDone(Platform.getConfigFolder())`. Add the onboarding gate so we never open during/before the vanilla narrator/accessibility onboarding:

```java
public static boolean shouldOpen() {
    if (shownThisSession) return false;
    if (net.minecraft.client.Minecraft.getInstance().options.onboardAccessibility) return false; // wait for vanilla onboarding
    return !Marker.isDone(dev.architectury.platform.Platform.getConfigFolder());
}
```
(Keep imports tidy — `Minecraft` / `Platform` may already be imported.)

- [ ] **Step 2: Build.** Run: `./gradlew build` → BUILD SUCCESSFUL (both loaders).

- [ ] **Step 3: Commit.**
```bash
git add common/src/main/java/io/github/fimkov/betterbrightness/BrightnessSetup.java
git commit -m "feat: open calibration screen only after vanilla onboarding"
```

(Runtime verification — that on a fresh profile the screen waits for onboarding — is the controller's `runClient` check; no unit test.)

---

### Task 3: Vanilla gamma slider label (Moody / Bright / Brightest / NNN%)

**Files:**
- Modify: `common/src/main/java/io/github/fimkov/betterbrightness/mixin/OptionsGammaMixin.java`
- Modify: `common/src/main/resources/assets/betterbrightness/lang/en_us.json`

**Interfaces:**
- Consumes: the same gamma `OptionInstance.<init>` call already targeted for the ValueSet (`@ModifyArg` index 3). The caption function is **arg index 2**, type `OptionInstance.CaptionBasedToString<Double>` — VERIFY the exact interface name + functional method signature and the index against decompiled `OptionInstance.java` / `Options.java:869-884` before coding.
- Produces: the in-game Video Settings gamma slider label text. No code consumers.

- [ ] **Step 1: Confirm the caption-function arg.** From the decompiled `Options.java` gamma block (~869): args are `"options.gamma"`(0), `noTooltip()`(1), the caption lambda (2), `UnitDouble.INSTANCE`(3, already modified), `0.5`(4), `NO_ACTION`(5). Confirm the index-2 parameter type is `OptionInstance.CaptionBasedToString<Double>` with method `Component toString(Component caption, Double value)` (read `OptionInstance.java`). Also confirm the vanilla generic value format key `"options.generic_value"` ("%s: %s") exists (it's how vanilla composes "Brightness: X").

- [ ] **Step 2: Add the caption `@ModifyArg` to `OptionsGammaMixin`.** Reuse the SAME slice you already use for the ValueSet arg (between CONSTANT `"options.gamma"` and CONSTANT `"options.guiScale"`), targeting `OptionInstance.<init>`, but `index = 2`. Provide a handler that returns a caption function building the label. Adjust the interface/type names to Step 1's findings:

```java
@org.spongepowered.asm.mixin.injection.ModifyArg(
    method = "<init>",
    slice = @org.spongepowered.asm.mixin.injection.Slice(
        from = @org.spongepowered.asm.mixin.injection.At(value = "CONSTANT", args = "stringValue=options.gamma"),
        to   = @org.spongepowered.asm.mixin.injection.At(value = "CONSTANT", args = "stringValue=options.guiScale")),
    at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE",
        target = "Lnet/minecraft/client/OptionInstance;<init>(Ljava/lang/String;Lnet/minecraft/client/OptionInstance$TooltipSupplier;Lnet/minecraft/client/OptionInstance$CaptionBasedToString;Lnet/minecraft/client/OptionInstance$ValueSet;Ljava/lang/Object;Lnet/minecraft/client/OptionInstance$ValueUpdateListener;)V"),
    index = 2)
private static OptionInstance.CaptionBasedToString<Double> betterbrightness$gammaLabel(
        OptionInstance.CaptionBasedToString<Double> original) {
    return (caption, value) -> {
        int pct = (int) Math.round(value * 100.0);
        net.minecraft.network.chat.Component label;
        if (pct == 0) {
            label = net.minecraft.network.chat.Component.translatable("options.gamma.min");
        } else if (pct == 100) {
            label = net.minecraft.network.chat.Component.translatable("options.gamma.max");
        } else if (pct == 200) {
            label = net.minecraft.network.chat.Component.translatable("betterbrightness.options.gamma.brightest");
        } else {
            label = net.minecraft.network.chat.Component.literal(pct + "%");
        }
        return net.minecraft.network.chat.Component.translatable("options.generic_value", caption, label);
    };
}
```
Notes for the implementer: the exact `<init>` descriptor in `target` must match the real 6-arg constructor — verify the parameter types (TooltipSupplier, CaptionBasedToString, ValueSet, Object for T, ValueUpdateListener) from the decompiled source and copy the descriptor exactly (under loom-no-remap these are real, un-obfuscated names; no refmap). If `@ModifyArg` can't disambiguate by index alone, the slice already narrows it to the gamma ctor. If the `"options.generic_value"` key differs, use the actual vanilla key that formats "caption: value" (find it in vanilla lang/en_us.json or `Options.genericValueLabel`).

- [ ] **Step 3: Add lang key.** In `en_us.json` add:
```json
"betterbrightness.options.gamma.brightest": "Brightest"
```
(Keep existing keys.)

- [ ] **Step 4: Build.** Run: `./gradlew build` → BUILD SUCCESSFUL (both loaders), no mixin/refmap errors.

- [ ] **Step 5: Commit.**
```bash
git add common/src/main/java/io/github/fimkov/betterbrightness/mixin/OptionsGammaMixin.java common/src/main/resources/assets/betterbrightness/lang/en_us.json
git commit -m "feat: vanilla gamma label Moody/Bright/Brightest/NNN%"
```

(Runtime verification — Video Settings shows Moody@0/Bright@100/Brightest@200/NNN% — is the controller's `runClient` check; build can't apply the mixin.)

---

### Task 4: `CalibrationPanel` — square texture, eased brightness, label, soft frame, fade

**Files:**
- Modify: `common/src/main/java/io/github/fimkov/betterbrightness/client/CalibrationPanel.java`

**Interfaces:**
- Consumes: `Brightness.panelVisibility(gamma, threshold)` (existing), `Brightness.lerp` (Task 1).
- Produces: new render signature `void render(GuiGraphicsExtractor g, Font font, int x, int y, int tile, double gamma, float fadeAlpha)` — draws a square tile of side `tile` at top-left `(x,y)`, the texture as a centered SQUARE eased toward `panelVisibility(gamma, threshold)`, the whole thing (texture + label) modulated by `fadeAlpha`∈[0,1]; the target label is drawn centered below the tile. Consumed by `BrightnessSetupScreen` (Task 5). The constructor (threshold/caption/texture/UV fields) is unchanged.

- [ ] **Step 1: Add per-panel easing state + rewrite `render`.** Replace the existing `render(...)` with the signature + body below. Keep the constructor and fields. Add two instance fields for easing.

```java
// add fields near the others:
private double displayedVis = 0.0;
private long lastMillis = 0L;
private static final double EASE_TAU_MS = 90.0; // smaller = snappier

public void render(GuiGraphicsExtractor g, Font font, int x, int y, int tile, double gamma, float fadeAlpha) {
    // Dark backing + soft 1px inner frame.
    g.fill(x, y, x + tile, y + tile, withAlpha(0xFF101010, fadeAlpha));
    g.fill(x, y, x + tile, y + 1, withAlpha(0xFF3A3A42, fadeAlpha));               // top
    g.fill(x, y + tile - 1, x + tile, y + tile, withAlpha(0xFF3A3A42, fadeAlpha)); // bottom
    g.fill(x, y, x + 1, y + tile, withAlpha(0xFF3A3A42, fadeAlpha));               // left
    g.fill(x + tile - 1, y, x + tile, y + tile, withAlpha(0xFF3A3A42, fadeAlpha)); // right

    // Ease displayedVis toward the live target.
    long now = System.currentTimeMillis();
    double target = Brightness.panelVisibility(gamma, threshold);
    if (lastMillis == 0L) {
        displayedVis = target;
    } else {
        double dt = now - lastMillis;
        displayedVis = Brightness.lerp(displayedVis, target, dt / EASE_TAU_MS);
    }
    lastMillis = now;

    // Square texture centered in the tile content area (margin 8). Square src -> square dest: no stretch.
    int margin = 8;
    int s = tile - margin * 2;
    int ix = x + margin;
    int iy = y + margin;
    int texAlpha = (int) Math.round(displayedVis * fadeAlpha * 255.0);
    int argb = (texAlpha << 24) | 0xFFFFFF;
    try {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, ix, iy, u, v, s, s, srcW, srcH, texW, texH, argb);
    } catch (Throwable t) {
        g.fill(ix, iy, ix + s, iy + s, withAlpha(0xFF402020, fadeAlpha));
    }

    // Target label centered below the tile, faded with the screen.
    int textColor = (Math.round(fadeAlpha * 255.0f) << 24) | 0xFFFFFF;
    g.centeredText(font, caption, x + tile / 2, y + tile + 6, textColor);
}

/** Multiply an ARGB color's alpha by f in [0,1]. */
private static int withAlpha(int argb, float f) {
    int a = (argb >>> 24) & 0xFF;
    int na = Math.round(a * Math.max(0f, Math.min(1f, f)));
    return (na << 24) | (argb & 0xFFFFFF);
}
```

- [ ] **Step 2: Build.** Run: `./gradlew build` → BUILD SUCCESSFUL (both loaders). (No unit test — rendering is verified by the screen runtime check in Task 5.)

- [ ] **Step 3: Commit.**
```bash
git add common/src/main/java/io/github/fimkov/betterbrightness/client/CalibrationPanel.java
git commit -m "feat: square un-stretched tiles with eased brightness, soft frame, fade"
```

---

### Task 5: `BrightnessSetupScreen` — row layout, instruction, fade-in, % label

**Files:**
- Modify: `common/src/main/java/io/github/fimkov/betterbrightness/client/BrightnessSetupScreen.java`
- Modify: `common/src/main/resources/assets/betterbrightness/lang/en_us.json` (add `instruction`; keep panel keys)

**Interfaces:**
- Consumes: `Brightness.toPercent` (Task 1), `CalibrationPanel.render(g, font, x, y, tile, gamma, fadeAlpha)` (Task 4).
- Produces: the final screen. Terminal task.

- [ ] **Step 1: Add `instruction` lang key.** In `en_us.json`:
```json
"betterbrightness.instruction": "Drag the slider until each tile matches its label below"
```
Confirm the 4 panel keys exist with the English values: hidden="Should be hidden", faint="Barely visible", clear="Clearly visible", bright="Bright". (Adjust the panel-construction `Component.translatable(...)` keys if needed — keep them.)

- [ ] **Step 2: Add fade-in timing + the `%` slider label.** In `BrightnessSetupScreen`, add an open-time field and compute fade alpha each frame. Change the slider label format to percent.

```java
// field:
private long openMillis = 0L;
private static final double FADE_MS = 250.0;

private float fadeAlpha() {
    if (openMillis == 0L) return 1f;
    double t = (System.currentTimeMillis() - openMillis) / FADE_MS;
    return (float) Math.max(0.0, Math.min(1.0, t));
}

private static String sliderLabel(double sliderValue) {
    return "Brightness: " + Brightness.toPercent(Brightness.sliderToGamma(sliderValue)) + "%";
}
```
In `init()`, set `openMillis = System.currentTimeMillis();` at the top, and use `sliderLabel(...)` for both the slider's initial message and its `updateMessage()`:
```java
// initial message:
Component.literal(sliderLabel(slider))
// inside updateMessage():
setMessage(Component.literal(sliderLabel(this.value)));
```

- [ ] **Step 3: Replace the layout + render.** Replace `renderPanels(...)` and the panel draw in `extractRenderState(...)` with an overlap-proof horizontal row + the instruction line + fade. Use the new panel signature.

```java
@Override
public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    float fade = fadeAlpha();
    int titleColor = (Math.round(fade * 255f) << 24) | 0xFFFFFF;
    int subColor   = (Math.round(fade * 255f) << 24) | 0xB9B9C0;
    graphics.centeredText(this.font, this.title, this.width / 2, 18, titleColor);
    graphics.centeredText(this.font, Component.translatable("betterbrightness.instruction"),
            this.width / 2, 34, subColor);
    renderRow(graphics, fade);
    super.extractRenderState(graphics, mouseX, mouseY, partialTick); // slider + Done on top
}

/** 4 square tiles in a centered row; tile size clamped so the row never overlaps the slider/Done. */
private void renderRow(GuiGraphicsExtractor graphics, float fade) {
    final int n = panels.length;     // 4
    final int gap = 16;
    final int topY = 52;
    final int labelRoom = 24;        // space under tiles for the caption
    final int sliderY = this.height - 56;
    // Largest tile that fits width AND leaves >=12px above the slider after the label.
    int byWidth = (this.width - 40 - gap * (n - 1)) / n;
    int byHeight = sliderY - 12 - labelRoom - topY;
    int tile = Math.max(32, Math.min(96, Math.min(byWidth, byHeight)));
    int rowW = tile * n + gap * (n - 1);
    int ox = (this.width - rowW) / 2;
    double gamma = currentGamma();
    for (int i = 0; i < n; i++) {
        int x = ox + i * (tile + gap);
        panels[i].render(graphics, this.font, x, topY, tile, gamma, fade);
    }
}
```
Delete the old `renderPanels(...)` method and the old `graphics.centeredText(this.title, ...)` line in `extractRenderState`.

- [ ] **Step 4: Build.** Run: `./gradlew build` → BUILD SUCCESSFUL (both loaders).

- [ ] **Step 5: Commit.**
```bash
git add common/src/main/java/io/github/fimkov/betterbrightness/client/BrightnessSetupScreen.java common/src/main/resources/assets/betterbrightness/lang/en_us.json
git commit -m "feat: row layout + instruction + fade-in + 0-200% slider label"
```

(Runtime verification of the whole screen — square tiles, no overlap, instruction + labels, fade-in/eased brightness, % label — is the controller's `runClient` + screenshot check.)

---

## Self-Review

- **Spec coverage:** #1 onboarding gate → Task 2 ✓; #2 square textures → Task 4 ✓; #3 row layout no-overlap → Task 5 (clamped `tile`) ✓; #4 instruction + per-tile labels → Task 5 + Task 4 (label) + en_us.json ✓; #5 animation → Task 4 (eased) + Task 5 (fade-in) ✓; #6 0–200% scale → Task 1 (toPercent) + Task 5 (slider label) + Task 3 (vanilla label) ✓.
- **Placeholder scan:** concrete code in every code step; the only "verify" gates are genuine 26.2-API unknowns (caption-fn type/index, generic-value key) with explicit instructions to confirm against decompiled source — not vague placeholders.
- **Type consistency:** `toPercent(double)`/`lerp(double,double,double)` (Task 1) used identically in Tasks 4/5; `CalibrationPanel.render(g, font, x, y, tile, gamma, fadeAlpha)` defined in Task 4 and called with that exact signature in Task 5; `withAlpha`/`fadeAlpha` private helpers used only within their own files.
- **Known risk:** the caption-function `@ModifyArg` (Task 3) shares the gamma ctor with the ValueSet `@ModifyArg`; the `<init>` descriptor + index must be exact, and only runtime confirms mixin application. Flagged in the task.

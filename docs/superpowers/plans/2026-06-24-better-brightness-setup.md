# Better Brightness Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a Minecraft mod that, on first client launch only, opens a vanilla-styled brightness calibration screen with 4 live-rendered panels reacting to a slider, then writes the chosen brightness to the game's gamma.

**Architecture:** One Architectury multiloader project. ~95% of logic lives in `common` using vanilla Minecraft classes; `fabric`, `neoforge`, `forge` modules are thin client entrypoints. Quilt runs the Fabric jar via its Fabric-compat layer (plus a `quilt.mod.json`). Cross-loader hooks (open-on-title-screen, config dir) come from the Architectury API, not per-loader code.

**Tech Stack:** Java 25, Gradle 9.4, Architectury (loom 1.15 for Fabric), Minecraft 26.2, NeoForge 26.2.x, Forge (26.2 if available — see Global Constraints), JUnit 5 for pure-logic tests.

## Global Constraints

- Target Minecraft version: **26.2** (year-versioned). Java toolchain: **25**.
- mod id: `betterbrightness`. Root package: `io.github.fimkov.betterbrightness`. Display name: `Better Brightness Setup`.
- Loaders: NeoForge, Fabric, Forge, Quilt. **Quilt is not a Gradle module** — it consumes the Fabric jar; we only add `quilt.mod.json`.
- Brightness mapping is fixed: slider `0.0 → gamma 0.0` (vanilla min "Moody"), slider `1.0 → gamma 2.0` (2× vanilla max "Bright"). Linear.
- Show the screen **exactly once** (first launch), persisted via a marker file. No re-open button, no keybind (deferred).
- Reuse vanilla `options.gamma`. No custom lightmap, no world-renderer scene.
- **API-NOTES rule:** Task 1 produces `docs/superpowers/plans/API-NOTES.md` with the verified 26.2 class/method names. If any name in a later task's code differs from the dev-environment source, use the name recorded in API-NOTES (the dev source is ground truth, this plan's names are the best-known-stable defaults).
- Every task that renders GUI is verified by `runClient`; pure logic is verified by JUnit. Never claim a GUI task done without a runClient observation.

---

## File Structure

Created by the Architectury template (Task 1), then filled in:

```
common/src/main/java/io/github/fimkov/betterbrightness/
    BrightnessSetup.java        # initClient(): register title-screen hook, open logic
    Brightness.java             # PURE: sliderToGamma(), panelVisibility()  (no MC imports)
    Marker.java                 # PURE-ish: isDone(Path), markDone(Path)
    GammaWriter.java            # setGammaRaw(double): set options.gamma past the 0..1 clamp
    client/BrightnessSetupScreen.java
    client/CalibrationPanel.java
common/src/test/java/io/github/fimkov/betterbrightness/
    BrightnessTest.java
    MarkerTest.java
fabric/src/main/java/io/github/fimkov/betterbrightness/fabric/BetterBrightnessFabricClient.java
fabric/src/main/resources/fabric.mod.json
fabric/src/main/resources/quilt.mod.json
neoforge/src/main/java/io/github/fimkov/betterbrightness/neoforge/BetterBrightnessNeoForge.java
forge/src/main/java/io/github/fimkov/betterbrightness/forge/BetterBrightnessForge.java
docs/superpowers/plans/API-NOTES.md
README.md
```

---

### Task 1: Bootstrap Architectury project + verify 26.2 APIs

**Files:**
- Create: whole Gradle project via the Architectury template generator
- Create: `docs/superpowers/plans/API-NOTES.md`
- Modify: `gradle.properties` (ids, versions, java toolchain)

**Interfaces:**
- Produces: a building 4-loader skeleton (or 3 + documented Forge gap), a working dev client, and `API-NOTES.md` recording the exact 26.2 names every later task depends on.

- [ ] **Step 1: Generate the template.** Use the Architectury template generator (https://generate.architectury.dev — select MC 26.2, Fabric + NeoForge + Forge, "split sources" off, mod id `betterbrightness`, package `io.github.fimkov.betterbrightness`). If the generator does not yet offer Forge for 26.2, generate Fabric + NeoForge, record the Forge gap in `API-NOTES.md`, and proceed (Forge module added later when upstream ships). Drop the generated files into the repo root (keep the existing `docs/` and `.gitignore`).

- [ ] **Step 2: Set identity/toolchain in `gradle.properties`.** Confirm: `minecraft_version=26.2`, mod id/group/package as above, and the Java toolchain is 25 in each `build.gradle` (`java.toolchain.languageVersion = JavaLanguageVersion.of(25)`).

- [ ] **Step 3: Build the empty mod.**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; jars produced under `fabric/build/libs`, `neoforge/build/libs`, and `forge/build/libs` (or the documented subset).

- [ ] **Step 4: Smoke-run the dev client.**

Run: `./gradlew :fabric:runClient`
Expected: Minecraft 26.2 launches to the title screen with the (empty) mod loaded. Close it.

- [ ] **Step 5: Record verified APIs.** With the dev sources downloaded (loom/architectury decompiles them), open them and write `docs/superpowers/plans/API-NOTES.md` recording the EXACT fully-qualified names + signatures for:
  - `Screen` base: package, the `init()` override, and the render method signature (expected `render(GuiGraphics, int mouseX, int mouseY, float partialTick)`).
  - `GuiGraphics`: methods for `blit`/fill/`drawCenteredString` and how to get a `Font`.
  - `Minecraft.getInstance()`, `Minecraft.options`, and the gamma accessor (expected `options.gamma()` returning `OptionInstance<Double>`; note its `get()`/`set()` and whether `set()` clamps to [0,1]).
  - `Button.builder(...)` and the slider base class (expected `AbstractSliderButton`, with `applyValue()` / `updateMessage()`).
  - Entity-in-GUI render helper (expected `InventoryScreen.renderEntityInInventoryFollowsMouse(...)` or the 26.2 equivalent) and how to obtain a `Creeper`/`EntityType.CREEPER` instance for rendering.
  - `TitleScreen` FQN.
  - Architectury: `dev.architectury.event.events.client.ClientGuiEvent.INIT_POST` register signature, and `dev.architectury.platform.Platform.getConfigFolder()` return type.
  - **Whether `OptionInstance.set()` clamps gamma above 1.0** (drives Task 5's GammaWriter).

- [ ] **Step 6: Commit.**

```bash
git add -A
git commit -m "chore: bootstrap Architectury 26.2 multiloader skeleton + API notes"
```

---

### Task 2: Brightness pure logic (TDD)

**Files:**
- Create: `common/src/main/java/io/github/fimkov/betterbrightness/Brightness.java`
- Test: `common/src/test/java/io/github/fimkov/betterbrightness/BrightnessTest.java`

**Interfaces:**
- Produces: `static double sliderToGamma(double t)` — maps t∈[0,1] linearly to [0.0, 2.0], clamping t outside [0,1]. `static double panelVisibility(double gamma, double threshold)` — returns 0..1 (0 = invisible, 1 = fully visible): `clamp01((gamma - threshold) / 0.5)`. Consumed by `BrightnessSetupScreen` (Task 5) and `CalibrationPanel` (Task 6).

- [ ] **Step 1: Write the failing test.**

```java
package io.github.fimkov.betterbrightness;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BrightnessTest {
    @Test void mapsSliderEndpointsAndMidpoint() {
        assertEquals(0.0, Brightness.sliderToGamma(0.0), 1e-9);
        assertEquals(2.0, Brightness.sliderToGamma(1.0), 1e-9);
        assertEquals(1.0, Brightness.sliderToGamma(0.5), 1e-9);
    }
    @Test void clampsSliderOutOfRange() {
        assertEquals(0.0, Brightness.sliderToGamma(-3.0), 1e-9);
        assertEquals(2.0, Brightness.sliderToGamma(7.0), 1e-9);
    }
    @Test void panelVisibilityRamps() {
        assertEquals(0.0, Brightness.panelVisibility(0.10, 0.5), 1e-9); // below threshold -> hidden
        assertEquals(1.0, Brightness.panelVisibility(1.50, 0.5), 1e-9); // well above -> fully visible
        assertEquals(0.5, Brightness.panelVisibility(0.75, 0.5), 1e-9); // mid-ramp
    }
}
```

- [ ] **Step 2: Run test to verify it fails.**

Run: `./gradlew :common:test --tests '*BrightnessTest'`
Expected: FAIL — `Brightness` does not exist / cannot find symbol.

- [ ] **Step 3: Write minimal implementation.**

```java
package io.github.fimkov.betterbrightness;

public final class Brightness {
    private Brightness() {}

    /** slider t in [0,1] -> gamma in [0.0, 2.0] (vanilla min .. 2x vanilla max), clamped. */
    public static double sliderToGamma(double t) {
        return clamp01(t) * 2.0;
    }

    /** 0 = content invisible, 1 = fully visible, ramping over a 0.5-gamma window above threshold. */
    public static double panelVisibility(double gamma, double threshold) {
        return clamp01((gamma - threshold) / 0.5);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
```

- [ ] **Step 4: Run test to verify it passes.**

Run: `./gradlew :common:test --tests '*BrightnessTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit.**

```bash
git add common/src/main/java/io/github/fimkov/betterbrightness/Brightness.java common/src/test/java/io/github/fimkov/betterbrightness/BrightnessTest.java
git commit -m "feat: brightness slider->gamma mapping and panel visibility curve"
```

---

### Task 3: First-launch marker (TDD)

**Files:**
- Create: `common/src/main/java/io/github/fimkov/betterbrightness/Marker.java`
- Test: `common/src/test/java/io/github/fimkov/betterbrightness/MarkerTest.java`

**Interfaces:**
- Produces: `static boolean isDone(Path configDir)` — true iff `configDir/betterbrightness/.done` exists. `static void markDone(Path configDir)` — creates that file (and parent dir), swallowing/logging IO errors. Consumed by `BrightnessSetup` (Task 4) and `BrightnessSetupScreen` (Task 5).

- [ ] **Step 1: Write the failing test.**

```java
package io.github.fimkov.betterbrightness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MarkerTest {
    @Test void absentThenPresent(@TempDir Path cfg) {
        assertFalse(Marker.isDone(cfg));
        Marker.markDone(cfg);
        assertTrue(Marker.isDone(cfg));
    }
    @Test void markDoneIsIdempotent(@TempDir Path cfg) {
        Marker.markDone(cfg);
        Marker.markDone(cfg); // must not throw
        assertTrue(Marker.isDone(cfg));
    }
}
```

- [ ] **Step 2: Run test to verify it fails.**

Run: `./gradlew :common:test --tests '*MarkerTest'`
Expected: FAIL — `Marker` does not exist.

- [ ] **Step 3: Write minimal implementation.**

```java
package io.github.fimkov.betterbrightness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Marker {
    private Marker() {}

    private static Path markerPath(Path configDir) {
        return configDir.resolve("betterbrightness").resolve(".done");
    }

    public static boolean isDone(Path configDir) {
        return Files.exists(markerPath(configDir));
    }

    public static void markDone(Path configDir) {
        Path p = markerPath(configDir);
        try {
            Files.createDirectories(p.getParent());
            if (!Files.exists(p)) Files.createFile(p);
        } catch (IOException e) {
            System.err.println("[betterbrightness] could not write marker: " + e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes.**

Run: `./gradlew :common:test --tests '*MarkerTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit.**

```bash
git add common/src/main/java/io/github/fimkov/betterbrightness/Marker.java common/src/test/java/io/github/fimkov/betterbrightness/MarkerTest.java
git commit -m "feat: first-launch marker file read/create"
```

---

### Task 4: Trigger + persistence wiring (loaders → open-on-title-screen, log-only)

Proves the cross-loader trigger and the once-only persistence before any screen exists. The screen call is a log line here, replaced in Task 5.

**Files:**
- Create: `common/.../BrightnessSetup.java`
- Create: `fabric/.../fabric/BetterBrightnessFabricClient.java`
- Create: `neoforge/.../neoforge/BetterBrightnessNeoForge.java`
- Create: `forge/.../forge/BetterBrightnessForge.java` (skip if Forge 26.2 absent per Task 1)
- Modify: `fabric/src/main/resources/fabric.mod.json` (client entrypoint), neoforge/forge mod metadata as the template requires

**Interfaces:**
- Consumes: `Marker.isDone/markDone` (Task 3), `Platform.getConfigFolder()` (Task 1 notes), `ClientGuiEvent.INIT_POST` (Task 1 notes).
- Produces: `static void initClient()` — idempotent; registers the title-screen hook. `static boolean shouldOpen()` and `static void onScreenOpened()` session state used by Task 5.

- [ ] **Step 1: Implement `BrightnessSetup` (log-only open).** Adjust the `INIT_POST` lambda signature and `TitleScreen` import to match API-NOTES.

```java
package io.github.fimkov.betterbrightness;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.platform.Platform;
import net.minecraft.client.gui.screens.TitleScreen;

public final class BrightnessSetup {
    private static boolean registered = false;
    private static boolean shownThisSession = false;
    private BrightnessSetup() {}

    public static void initClient() {
        if (registered) return;
        registered = true;
        ClientGuiEvent.INIT_POST.register((screen, access) -> {
            if (screen instanceof TitleScreen && shouldOpen()) {
                onScreenOpened();
                // Task 5 replaces this line with Minecraft.getInstance().setScreen(new BrightnessSetupScreen());
                System.out.println("[betterbrightness] first launch detected — would open calibration screen");
            }
        });
    }

    public static boolean shouldOpen() {
        return !shownThisSession && !Marker.isDone(Platform.getConfigFolder());
    }

    public static void onScreenOpened() {
        shownThisSession = true;
    }
}
```

- [ ] **Step 2: Fabric client entrypoint.**

```java
package io.github.fimkov.betterbrightness.fabric;

import io.github.fimkov.betterbrightness.BrightnessSetup;
import net.fabricmc.api.ClientModInitializer;

public final class BetterBrightnessFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BrightnessSetup.initClient();
    }
}
```

Register it under `"client"` in `fabric.mod.json` entrypoints (keep the template's existing `main` entry).

- [ ] **Step 3: NeoForge client entrypoint.** Use the template's mod-bus pattern; call `initClient()` on the client-setup event. Adjust annotations/event class to API-NOTES.

```java
package io.github.fimkov.betterbrightness.neoforge;

import io.github.fimkov.betterbrightness.BrightnessSetup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent; // placeholder import; use FMLClientSetupEvent per API-NOTES
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.bus.api.IEventBus;

@Mod(value = "betterbrightness", dist = Dist.CLIENT)
public final class BetterBrightnessNeoForge {
    public BetterBrightnessNeoForge(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(BrightnessSetup::initClient));
    }
}
```

- [ ] **Step 4: Forge client entrypoint** (skip if Forge 26.2 absent). Mirror NeoForge using Forge's `FMLClientSetupEvent` and `@Mod`. Record the skip in API-NOTES if not built.

- [ ] **Step 5: Verify trigger on Fabric.**

Run: `rm -rf run/config/betterbrightness && ./gradlew :fabric:runClient`
Expected: at the title screen, console prints `[betterbrightness] first launch detected — would open calibration screen`. Close.

- [ ] **Step 6: Verify persistence.** Manually create the marker, relaunch, expect NO log line.

Run: `mkdir -p run/config/betterbrightness && touch run/config/betterbrightness/.done && ./gradlew :fabric:runClient`
Expected: no first-launch log line at the title screen. Close.

- [ ] **Step 7: Verify on NeoForge.**

Run: `rm -rf run/config/betterbrightness && ./gradlew :neoforge:runClient`
Expected: same first-launch log line. Close.

- [ ] **Step 8: Commit.**

```bash
git add -A
git commit -m "feat: cross-loader first-launch trigger via title-screen hook (log-only)"
```

---

### Task 5: BrightnessSetupScreen skeleton (title, background, slider, Done → writes gamma + marker)

**Files:**
- Create: `common/.../client/BrightnessSetupScreen.java`
- Create: `common/.../GammaWriter.java`
- Modify: `common/.../BrightnessSetup.java` (replace the log line with `setScreen`)

**Interfaces:**
- Consumes: `Brightness.sliderToGamma` (Task 2), `Marker.markDone` + `Platform.getConfigFolder()` (Tasks 1/3), `BrightnessSetup.onScreenOpened` (Task 4).
- Produces: `BrightnessSetupScreen` (a `Screen`) and `GammaWriter.setGammaRaw(double)`. `CalibrationPanel` (Task 6) reads the screen's `currentGamma()` for live tint.

- [ ] **Step 1: Implement `GammaWriter`.** Primary path: `options.gamma().set(value)` + `options.save()`. If Task 1 found `set()` clamps to ≤1.0, reflect the backing value field (find the `OptionInstance` field currently holding a `Double`) and set it directly, then `save()`. Wrapped so failure logs and no-ops (never crashes Done). Adjust accessor names per API-NOTES.

```java
package io.github.fimkov.betterbrightness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import java.lang.reflect.Field;

public final class GammaWriter {
    private GammaWriter() {}

    public static void setGammaRaw(double gamma) {
        try {
            Minecraft mc = Minecraft.getInstance();
            OptionInstance<Double> opt = mc.options.gamma();
            opt.set(gamma);                 // may clamp to <=1.0
            if (Math.abs(opt.get() - gamma) > 1e-6) {
                forceValue(opt, gamma);     // bypass clamp for >1.0 (fullbright trick)
            }
            mc.options.save();
        } catch (Throwable t) {
            System.err.println("[betterbrightness] could not set gamma: " + t);
        }
    }

    private static void forceValue(OptionInstance<Double> opt, double gamma) throws Exception {
        for (Field f : OptionInstance.class.getDeclaredFields()) {
            f.setAccessible(true);
            Object v = f.get(opt);
            if (v instanceof Double) { f.set(opt, gamma); return; }
        }
    }
}
```

> Note (persistence ceiling): `save()` writes the raw value, but vanilla may re-clamp on the *next* load. If runClient shows the >1.0 value lost after restart and that matters, the documented follow-up is a small Mixin widening the gamma `OptionInstance` range to [0,2]. Do NOT build it speculatively — only if Step 7 shows it's needed.

- [ ] **Step 2: Implement the screen skeleton.** Adjust `Screen`/`GuiGraphics`/`AbstractSliderButton`/`Button` names + render signature per API-NOTES.

```java
package io.github.fimkov.betterbrightness.client;

import io.github.fimkov.betterbrightness.Brightness;
import io.github.fimkov.betterbrightness.GammaWriter;
import io.github.fimkov.betterbrightness.Marker;
import dev.architectury.platform.Platform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BrightnessSetupScreen extends Screen {
    private double slider = 0.5; // start mid (gamma 1.0)

    public BrightnessSetupScreen() {
        super(Component.literal("Brightness Setup"));
    }

    public double currentGamma() { return Brightness.sliderToGamma(slider); }

    @Override protected void init() {
        int cx = this.width / 2;
        addRenderableWidget(new AbstractSliderButton(cx - 100, this.height - 56, 200, 20,
                Component.literal("Brightness"), slider) {
            @Override protected void updateMessage() {
                setMessage(Component.literal(String.format("Brightness: %.2f", Brightness.sliderToGamma(this.value))));
            }
            @Override protected void applyValue() { slider = this.value; }
        });
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onDone())
                .bounds(cx - 100, this.height - 30, 200, 20).build());
    }

    private void onDone() {
        GammaWriter.setGammaRaw(Brightness.sliderToGamma(slider));
        Marker.markDone(Platform.getConfigFolder());
        this.onClose();
    }

    @Override public void onClose() { super.onClose(); }
    @Override public boolean shouldCloseOnEsc() { return true; } // Esc == Done's persistence still applies via removed()? -> handle in render note

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g, mouseX, mouseY, pt);
        g.drawCenteredString(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
        super.render(g, mouseX, mouseY, pt); // widgets
    }
}
```

> Esc handling: to guarantee the marker is written even when the user presses Esc (not the Done button), also call `Marker.markDone(Platform.getConfigFolder())` from an overridden `removed()` (called when the screen is dismissed). Add that one-liner; it is idempotent (Task 3). This keeps "show once" true regardless of how the screen is closed.

- [ ] **Step 3: Wire the screen into the trigger.** In `BrightnessSetup`, replace the `System.out.println` line with:

```java
net.minecraft.client.Minecraft.getInstance().setScreen(new io.github.fimkov.betterbrightness.client.BrightnessSetupScreen());
```

- [ ] **Step 4: Build.**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify the screen shows and slider works (Fabric).**

Run: `rm -rf run/config/betterbrightness && ./gradlew :fabric:runClient`
Expected: the calibration screen opens over the title screen; the slider drags and its label shows `Brightness: 0.00`..`2.00`. Do NOT press Done yet — close the window to confirm `removed()` still wrote the marker.

- [ ] **Step 6: Verify once-only + gamma write.**

Run: `./gradlew :fabric:runClient` (no rm this time)
Expected: screen does NOT reappear (marker honored). Open Video Settings → Brightness reflects the value you set. Inspect `run/options.txt` `gamma:` line.

- [ ] **Step 7: Verify >1.0 persistence.** Set the slider near max (gamma ~2.0), press Done, fully quit, relaunch, check `options.txt`/Video Settings. If the value was re-clamped to ~1.0 and that's unacceptable, implement the Mixin follow-up from Step 1's note (separate commit); otherwise record in API-NOTES that vanilla persists it and move on.

- [ ] **Step 8: Commit.**

```bash
git add -A
git commit -m "feat: brightness setup screen with slider + gamma/marker write on done"
```

---

### Task 6: CalibrationPanel live render (4 panels, captions, live tint)

**Files:**
- Create: `common/.../client/CalibrationPanel.java`
- Modify: `common/.../client/BrightnessSetupScreen.java` (lay out a 2×2 grid, render panels each frame)

**Interfaces:**
- Consumes: `Brightness.panelVisibility` (Task 2), `currentGamma()` (Task 5), `GuiGraphics` + entity-in-GUI helper (API-NOTES).
- Produces: `CalibrationPanel.render(GuiGraphics, int x, int y, int w, int h, double gamma)` drawing real game content dimmed by `panelVisibility(gamma, threshold)`, with a caption.

- [ ] **Step 1: Implement `CalibrationPanel`.** Each panel draws a dark backing fill, then real content (a block texture grid and/or a creeper via the entity-in-GUI helper from API-NOTES) at alpha = `panelVisibility(gamma, threshold)`, then a caption below. Wrap the content render in try/catch and draw a plain rect on failure (one bad panel must not crash the screen). Adjust the entity-render call to API-NOTES.

```java
package io.github.fimkov.betterbrightness.client;

import io.github.fimkov.betterbrightness.Brightness;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class CalibrationPanel {
    private final double threshold;     // gamma at which content starts becoming visible
    private final Component caption;

    public CalibrationPanel(double threshold, String caption) {
        this.threshold = threshold;
        this.caption = Component.literal(caption);
    }

    public void render(BrightnessSetupScreen screen, GuiGraphics g, int x, int y, int w, int h, double gamma) {
        g.fill(x, y, x + w, y + h, 0xFF101010);              // dark backing
        double vis = Brightness.panelVisibility(gamma, threshold);
        int alpha = (int) Math.round(vis * 255.0) << 24;
        try {
            // Content tinted by visibility. Replace with the API-NOTES entity/block helper:
            // dim a creeper/block render by `alpha`. Until the exact helper is wired,
            // a tinted marker rect proves the live-tint path:
            g.fill(x + 8, y + 8, x + w - 8, y + h - 8, alpha | 0x00C0C0C0);
        } catch (Throwable t) {
            g.fill(x + 8, y + 8, x + w - 8, y + h - 8, 0xFF402020); // placeholder on failure
        }
        g.drawCenteredString(screen.fontAccess(), caption, x + w / 2, y + h + 4, 0xFFFFFF);
    }
}
```

- [ ] **Step 2: Expose the font + lay out the grid in the screen.** Add `public net.minecraft.client.gui.Font fontAccess() { return this.font; }` to `BrightnessSetupScreen`, build the 4 panels in `init()`, and render them in `render()` before `super.render`:

```java
private final CalibrationPanel[] panels = {
    new CalibrationPanel(1.6, "Эту фигуру не должно быть видно"),
    new CalibrationPanel(1.1, "Эта должна быть едва видна"),
    new CalibrationPanel(0.6, "Эта — хорошо видна"),
    new CalibrationPanel(0.2, "Эта — ярко видна"),
};

private void renderPanels(GuiGraphics g) {
    int pw = 140, ph = 90, gap = 24;
    int gridW = pw * 2 + gap, gridH = ph * 2 + gap + 16;
    int ox = (this.width - gridW) / 2;
    int oy = 44;
    for (int i = 0; i < 4; i++) {
        int col = i % 2, row = i / 2;
        int x = ox + col * (pw + gap);
        int y = oy + row * (ph + gap + 16);
        panels[i].render(this, g, x, y, pw, ph, currentGamma());
    }
}
```

Call `renderPanels(g);` in `render()` right after the title, before `super.render(...)`.

- [ ] **Step 3: Build.**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify live tint (Fabric).**

Run: `rm -rf run/config/betterbrightness && ./gradlew :fabric:runClient`
Expected: 4 captioned panels in a 2×2 grid; dragging the slider visibly changes each panel's content brightness in real time (low slider → top-left panel near-invisible; high slider → all visible). Close.

- [ ] **Step 5: Wire real block/entity content.** Replace the tinted-rect placeholder in `CalibrationPanel` with the API-NOTES entity-in-GUI render (a creeper) and/or a block-item render, applying `vis` as the dim factor. Rebuild and re-run Step 4's command; confirm real game content (not a rect) now fades in/out with the slider.

- [ ] **Step 6: Verify on NeoForge.**

Run: `rm -rf run/config/betterbrightness && ./gradlew :neoforge:runClient`
Expected: same screen + behavior. Close.

- [ ] **Step 7: Commit.**

```bash
git add -A
git commit -m "feat: 4 live calibration panels reacting to the brightness slider"
```

---

### Task 7: Quilt metadata + final cross-loader verification + README

**Files:**
- Create: `fabric/src/main/resources/quilt.mod.json`
- Create: `README.md`

**Interfaces:**
- Produces: a Quilt-recognizable Fabric jar and user/build docs. No code consumed by later tasks (final task).

- [ ] **Step 1: Add `quilt.mod.json`** to the Fabric module so the Fabric jar registers as a native Quilt mod (mirrors `fabric.mod.json`: schema_version 1, mod id `betterbrightness`, the `ClientModInitializer` under Quilt's client entrypoint, depends on `quilt_loader`/`minecraft`). Quilt also reads `fabric.mod.json` via compat, so this is presentation, not function.

- [ ] **Step 2: Build everything.**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; jars in `fabric/build/libs`, `neoforge/build/libs`, and `forge/build/libs` (or documented subset).

- [ ] **Step 3: Final loader matrix.** Run each available loader's client once on a fresh config (`rm -rf run/config/betterbrightness`) and confirm: screen shows first launch, panels react, Done writes gamma+marker, screen gone on relaunch.

Run: `./gradlew :fabric:runClient` then `:neoforge:runClient` then (if built) `:forge:runClient`
Expected: identical behavior across loaders.

- [ ] **Step 4: Quilt check.** If a Quilt dev/runtime is available, drop the Fabric jar into a Quilt instance and confirm it loads + the screen shows. Otherwise document in README that the Fabric jar is Quilt-compatible via Quilt's Fabric-compat layer (verified path) and Forge status.

- [ ] **Step 5: Write `README.md`** — what the mod does, supported MC (26.2) + loaders (incl. the Forge availability note from Task 1), build commands (`./gradlew build`), and where each jar lands.

- [ ] **Step 6: Commit.**

```bash
git add -A
git commit -m "feat: quilt metadata, README, cross-loader verification"
```

---

## Self-Review Notes

- **Spec coverage:** Architectury 4-loader (Task 1, 7) ✓; live-render panels reacting to slider (Task 6) ✓; slider 0→2.0 gamma (Task 2, 5) ✓; first-launch-only marker (Task 3, 4, 5) ✓; reuse vanilla gamma incl. >1.0 trick (Task 5) ✓; Quilt via Fabric-compat + quilt.mod.json (Task 7) ✓; pure-logic tests + runClient verification (Tasks 2/3 + 4/5/6/7) ✓; error handling per-panel + gamma + marker IO (Tasks 3/5/6) ✓; 26.2 API verification gate (Task 1, applied throughout) ✓.
- **Known risks carried as explicit gates, not placeholders:** exact 26.2 API names (API-NOTES rule), Forge-26.2 availability (Task 1 fallback), gamma >1.0 persistence across restart (Task 5 Step 7 conditional Mixin). These are real unknowns about a brand-new MC version, surfaced for the implementer rather than guessed.
- **Type consistency:** `sliderToGamma`/`panelVisibility` (Task 2) used identically in Tasks 5/6; `Marker.isDone/markDone` (Task 3) used in Tasks 4/5; `currentGamma()`/`fontAccess()` defined in Task 5/6 and consumed by `CalibrationPanel`.

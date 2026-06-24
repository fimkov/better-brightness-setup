# Kotlin Port Implementation Plan

> **STATUS: DEFERRED (2026-06-24).** Blocked on `kotlinforforge-neoforge` having no Minecraft 26.2 build (newest 6.2.0 caps at `[1.21.9,26.2)`). Fabric side is ready (fabric-language-kotlin 1.13.12 / Kotlin 2.4.0). Revisit when kotlinforforge supports 26.2. See [KOTLIN-NOTES.md](KOTLIN-NOTES.md).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the "Better Brightness Setup" mod from Java to Kotlin (behavior-identical), keeping the Mixin and its ValueSet in Java, so the codebase is Kotlin for future maintenance.

**Architecture:** Add Kotlin to the Architectury build (Kotlin Gradle plugin + per-loader Kotlin runtime adapters: fabric-language-kotlin for Fabric/Quilt, kotlinforforge for NeoForge). Convert every `.java` to idiomatic Kotlin EXCEPT `mixin/OptionsGammaMixin.java` and `GammaRange.java` (a self-contained Java unit — mixins are finicky in Kotlin). Existing JUnit tests (converted to Kotlin) are the behavior safety net; the controller runtime-verifies the screen + mixin still work.

**Tech Stack:** Kotlin (JVM target 25), Architectury, MC 26.2, fabric-language-kotlin, kotlinforforge-neoforge, JUnit 5, Sponge Mixin (Java).

## Global Constraints

- Behavior must be IDENTICAL to the current Java mod — this is a port, not a redesign. The existing Java files are the behavior reference. No feature/threshold/layout changes.
- KEEP IN JAVA (do not convert): `common/.../mixin/OptionsGammaMixin.java` and `common/.../GammaRange.java`. They reference only each other (both Java) and are invoked by the runtime/each other — no Kotlin interop. Kotlin goes in `src/main/kotlin`, Java stays in `src/main/java`; both compile in the same module.
- Convert to Kotlin (idiomatic, behavior-preserving): `Brightness`, `Marker`, `GammaWriter`, `BetterBrightness`, `BrightnessSetup`, `client/CalibrationPanel`, `client/BrightnessSetupScreen`, the two tests, and the two loader entrypoints.
- Java↔Kotlin interop during conversion: members of Kotlin objects that are still called from Java (the loader entrypoints, until they're converted in K3) MUST be reachable from Java — use `const val` for `MOD_ID` and `@JvmStatic` for `BetterBrightness.init()`. Keep these annotations (harmless) even after K3.
- mod id `betterbrightness`, package `io.github.fimkov.betterbrightness`, MC 26.2, Java/JVM target 25, Fabric + NeoForge (Forge still deferred), Quilt via Fabric-compat.
- Kotlin runtime MUST be on the runtime classpath of each loader jar (that's what the adapters provide) — a Kotlin class that compiles but can't find the Kotlin stdlib at runtime crashes. Verify the adapters bundle/provide it.
- The build won't apply mixins (loom-no-remap, runtime-only) and won't launch the GUI — the controller runtime-verifies (screen opens/renders, gamma mixin applies, persistence).

---

## File Structure

After the port:
```
common/src/main/kotlin/io/github/fimkov/betterbrightness/
    Brightness.kt  Marker.kt  GammaWriter.kt  BetterBrightness.kt  BrightnessSetup.kt
    client/CalibrationPanel.kt  client/BrightnessSetupScreen.kt
common/src/main/java/io/github/fimkov/betterbrightness/
    GammaRange.java               # KEPT Java
    mixin/OptionsGammaMixin.java   # KEPT Java
common/src/test/kotlin/io/github/fimkov/betterbrightness/
    BrightnessTest.kt  MarkerTest.kt
fabric/src/main/kotlin/io/github/fimkov/betterbrightness/fabric/BetterBrightnessFabric.kt
neoforge/src/main/kotlin/io/github/fimkov/betterbrightness/neoforge/BetterBrightnessNeoForge.kt
```
The corresponding `.java` files are DELETED as each is ported (git mv / delete). `src/main/java` keeps only GammaRange + the mixin.

---

### Task K1: Kotlin build infrastructure + adapters (RESEARCH-GATED)

**Files:**
- Modify: `build.gradle` (root), `common/build.gradle`, `fabric/build.gradle`, `neoforge/build.gradle`, `gradle.properties`
- Modify: `fabric/src/main/resources/fabric.mod.json`, `neoforge/src/main/resources/META-INF/neoforge.mods.toml`
- Create: `docs/superpowers/plans/KOTLIN-NOTES.md`, a throwaway `common/src/main/kotlin/io/github/fimkov/betterbrightness/KotlinPing.kt`

**Interfaces:**
- Produces: a build where Kotlin compiles to JVM-25 bytecode in all three modules, the Kotlin runtime is provided per-loader, and the existing (still-Java) mod still builds. KOTLIN-NOTES.md records the verified plugin/adapter versions every later task relies on.

- [ ] **Step 1: Research the real versions (do not guess).** Determine, from maven metadata / docs, versions compatible with this project (Kotlin → JVM 25, Architectury loom-no-remap, MC 26.2, NeoForge 26.2.0.7-beta, Fabric loader 0.19.3):
  - Kotlin Gradle plugin (`org.jetbrains.kotlin.jvm`) — a version supporting `jvmTarget = "25"`.
  - `net.fabricmc:fabric-language-kotlin` — latest (it tracks the Kotlin version, is largely MC-agnostic; check maven.fabricmc.net).
  - `thedarkcolour:kotlinforforge-neoforge` — a build matching NeoForge 26.2.x (check https://thedarkcolour.github.io/KotlinForForge/ maven). **If no kotlinforforge build supports NeoForge 26.2-beta, STOP and report BLOCKED** with the evidence (then we decide: bundle the Kotlin stdlib via shadow without kotlinforforge, or Kotlin on Fabric only). Do not fake it.
- [ ] **Step 2: Apply the Kotlin plugin** in each module's `build.gradle` (`plugins { id 'org.jetbrains.kotlin.jvm' version '<pinned>' }` or via the root) and set `kotlin { jvmToolchain(25) }` / `compileKotlin { kotlinOptions.jvmTarget = '25' }` to match the Java 25 toolchain. Ensure `src/main/kotlin` + `src/test/kotlin` are recognized (the Kotlin plugin adds them).
- [ ] **Step 3: Add the per-loader Kotlin runtime adapters.**
  - Fabric (`fabric/build.gradle`): add `modImplementation "net.fabricmc:fabric-language-kotlin:<pinned>"` (it provides the Kotlin runtime + the `kotlin` language adapter). In `fabric.mod.json` add `"fabric-language-kotlin": "*"` to `depends`.
  - NeoForge (`neoforge/build.gradle`): add `implementation "thedarkcolour:kotlinforforge-neoforge:<pinned>"`. In `neoforge.mods.toml` set `modLoader = "kotlinforforge"` (replacing `javafml`) and add a `[[dependencies.betterbrightness]]` on `kotlinforforge` (per kotlinforforge docs). Confirm the exact `modLoader` id + loaderVersion the chosen kotlinforforge build expects.
  - common (`common/build.gradle`): Kotlin plugin only (no adapter; common is consumed as classes). Keep the existing `compileOnly fabric-loader` + `api architectury` deps.
- [ ] **Step 4: Prove Kotlin compiles + the still-Java mod builds.** Add `KotlinPing.kt`:
  ```kotlin
  package io.github.fimkov.betterbrightness
  internal object KotlinPing { fun ping(): String = "kotlin ok" }
  ```
  Run: `./gradlew build`
  Expected: BUILD SUCCESSFUL for `:common`, `:fabric`, `:neoforge`; Kotlin compiles; the existing Java classes still build; jars produced. (Do NOT run runClient.) Confirm the Kotlin runtime is bundled/declared for each loader (fabric-language-kotlin in depends; kotlinforforge dep present).
- [ ] **Step 5: Record `KOTLIN-NOTES.md`** — the pinned versions (Kotlin plugin, fabric-language-kotlin, kotlinforforge-neoforge), the exact `modLoader`/dependency entries used, the jvmTarget config, and whether the Kotlin runtime is confirmed on each loader's classpath. Note any compromise (e.g., kotlinforforge version pinned to a specific NeoForge build).
- [ ] **Step 6: Delete `KotlinPing.kt`** (it was only a compile probe) and commit.
  ```bash
  git rm common/src/main/kotlin/io/github/fimkov/betterbrightness/KotlinPing.kt
  git add -A
  git commit -m "build: add Kotlin (jvm 25) + fabric-language-kotlin + kotlinforforge adapters"
  ```
  (Deleting the probe is fine — Step 4 already proved Kotlin compiles; later tasks add real Kotlin.)

If the toolchain won't resolve or a loader's Kotlin runtime can't be wired after real effort, report BLOCKED with the exact error.

---

### Task K2: Convert common classes + tests to Kotlin

**Files:**
- Create (Kotlin): `common/src/main/kotlin/.../Brightness.kt`, `Marker.kt`, `GammaWriter.kt`, `BetterBrightness.kt`, `BrightnessSetup.kt`, `client/CalibrationPanel.kt`, `client/BrightnessSetupScreen.kt`
- Create (Kotlin tests): `common/src/test/kotlin/.../BrightnessTest.kt`, `MarkerTest.kt`
- Delete: the corresponding 7 `.java` files + 2 `.java` tests
- Untouched: `GammaRange.java`, `mixin/OptionsGammaMixin.java`

**Interfaces:**
- Consumes: K1's Kotlin build. The Java `GammaRange`/mixin are untouched and do not reference these Kotlin classes.
- Produces: Kotlin equivalents with IDENTICAL behavior. `BetterBrightness` exposes `const val MOD_ID` + `@JvmStatic fun init()` (callable from the still-Java loader entrypoints until K3). `BrightnessSetup.initClient()`, `Brightness.{sliderToGamma,panelVisibility,toPercent,lerp}`, `Marker.{isDone,markDone}`, `GammaWriter.setGammaRaw`, `CalibrationPanel.render(...)`, `BrightnessSetupScreen(parent)` preserved.

- [ ] **Step 1: Port the pure-logic classes** (verbatim behavior). `Brightness.kt`:
```kotlin
package io.github.fimkov.betterbrightness

object Brightness {
    fun sliderToGamma(t: Double): Double = clamp01(t) * 2.0
    fun panelVisibility(gamma: Double, threshold: Double): Double = clamp01((gamma - threshold) / 0.5)
    fun toPercent(gamma: Double): Int = Math.round(gamma * 100.0).toInt()
    fun lerp(from: Double, to: Double, t: Double): Double = from + (to - from) * clamp01(t)
    private fun clamp01(v: Double): Double = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v
}
```
`Marker.kt`:
```kotlin
package io.github.fimkov.betterbrightness

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object Marker {
    private fun markerPath(configDir: Path): Path = configDir.resolve("betterbrightness").resolve(".done")
    fun isDone(configDir: Path): Boolean = Files.exists(markerPath(configDir))
    fun markDone(configDir: Path) {
        val p = markerPath(configDir)
        try {
            Files.createDirectories(p.parent)
            if (!Files.exists(p)) Files.createFile(p)
        } catch (e: IOException) {
            System.err.println("[betterbrightness] could not write marker: $e")
        }
    }
}
```

- [ ] **Step 2: Port `BetterBrightness.kt`, `GammaWriter.kt`, `BrightnessSetup.kt`.**
```kotlin
package io.github.fimkov.betterbrightness

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object BetterBrightness {
    const val MOD_ID = "betterbrightness"
    @JvmField val LOGGER: Logger = LoggerFactory.getLogger("BetterBrightness")
    @JvmStatic fun init() {
        LOGGER.info("[{}] common init", MOD_ID)
        BrightnessSetup.initClient()
    }
}
```
```kotlin
package io.github.fimkov.betterbrightness

import net.minecraft.client.Minecraft

object GammaWriter {
    fun setGammaRaw(gamma: Double) {
        try {
            val mc = Minecraft.getInstance()
            mc.options.gamma().set(gamma)
            mc.options.save()
        } catch (t: Throwable) {
            BetterBrightness.LOGGER.warn("[betterbrightness] could not set gamma", t)
        }
    }
}
```
```kotlin
package io.github.fimkov.betterbrightness

import dev.architectury.event.events.client.ClientGuiEvent
import dev.architectury.platform.Platform
import io.github.fimkov.betterbrightness.client.BrightnessSetupScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen

object BrightnessSetup {
    private var registered = false
    private var shownThisSession = false

    @JvmStatic
    fun initClient() {
        if (registered) return
        registered = true
        ClientGuiEvent.INIT_POST.register { screen, _ ->
            if (screen is TitleScreen && shouldOpen()) {
                onScreenOpened()
                Minecraft.getInstance().execute {
                    Minecraft.getInstance().gui.setScreen(BrightnessSetupScreen(screen))
                }
            }
        }
    }

    fun shouldOpen(): Boolean {
        if (shownThisSession) return false
        if (Minecraft.getInstance().options.onboardAccessibility) return false
        return !Marker.isDone(Platform.getConfigFolder())
    }

    fun onScreenOpened() { shownThisSession = true }
}
```
(SAM conversion: `ClientGuiEvent.INIT_POST.register { screen, access -> }` works because `ScreenInitPost` is a Java single-method interface; `Minecraft.execute { }` takes a `Runnable`. Verify these compile; if Kotlin needs an explicit SAM wrapper, add it.)

- [ ] **Step 3: Port `client/CalibrationPanel.kt`** — convert the existing `CalibrationPanel.java` to an idiomatic Kotlin `class` preserving behavior EXACTLY: same constructor params (`threshold: Double, caption: Component, texture: Identifier, texW: Int, texH: Int, u: Float, v: Float, srcW: Int, srcH: Int`), same `render(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, tile: Int, gamma: Double, fadeAlpha: Float)` body (dark backing, soft frame via `withAlpha`, eased `displayedVis` toward `Brightness.panelVisibility(...)` using `System.currentTimeMillis()` with the `lastMillis == 0L` first-frame snap, square texture `s = tile - 16` centered at `+8,+8`, `try/catch` placeholder, caption below). Keep `displayedVis`/`lastMillis`/`EASE_TAU_MS` and the private `withAlpha(argb: Int, f: Float): Int` (Kotlin `Int`, use `ushr`/`and`/`shl` for the bit ops). The existing Java file is the behavior reference — port it line-for-line.

- [ ] **Step 4: Port `client/BrightnessSetupScreen.kt`** — convert `BrightnessSetupScreen.java` to a Kotlin `class BrightnessSetupScreen(private val parent: Screen) : Screen(Component.translatable("betterbrightness.title"))`, preserving behavior EXACTLY: the `panels` array (creeper threshold **1.35**, deepslate 1.1, coal 0.6, diamond 0.2 — keep the current tuned values), `slider = 0.5`, `currentGamma()`, `openMillis`/`fadeAlpha()`/`sliderLabel()`, `init()` (set `openMillis` first; add the `AbstractSliderButton` as a Kotlin object expression `object : AbstractSliderButton(cx-100, height-56, 200, 20, Component.literal(sliderLabel(slider)), slider) { override fun updateMessage()... ; override fun applyValue()... }`, and the Done `Button.builder(Component.literal("Done")) { onDone() }.bounds(...).build()`), `onDone()`, `removed()`, `extractRenderState(...)` + `renderRow(...)` (the overlap-proof clamp). The existing Java file is the behavior reference. Note Kotlin specifics: anonymous subclass → object expression; `Button.OnPress` and the slider's protected overrides; `Math.max/min`/`Math.round` or Kotlin `coerceIn`.

- [ ] **Step 5: Port the tests to Kotlin.** `BrightnessTest.kt` + `MarkerTest.kt` — same assertions as the Java tests (JUnit 5 works with Kotlin). E.g.:
```kotlin
package io.github.fimkov.betterbrightness
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path

class BrightnessTest {
    @Test fun mapsSliderEndpointsAndMidpoint() {
        assertEquals(0.0, Brightness.sliderToGamma(0.0), 1e-9)
        assertEquals(2.0, Brightness.sliderToGamma(1.0), 1e-9)
        assertEquals(1.0, Brightness.sliderToGamma(0.5), 1e-9)
    }
    @Test fun clampsSliderOutOfRange() {
        assertEquals(0.0, Brightness.sliderToGamma(-3.0), 1e-9)
        assertEquals(2.0, Brightness.sliderToGamma(7.0), 1e-9)
    }
    @Test fun panelVisibilityRamps() {
        assertEquals(0.0, Brightness.panelVisibility(0.10, 0.5), 1e-9)
        assertEquals(1.0, Brightness.panelVisibility(1.50, 0.5), 1e-9)
        assertEquals(0.5, Brightness.panelVisibility(0.75, 0.5), 1e-9)
    }
    @Test fun toPercentMapsGammaRange() {
        assertEquals(0, Brightness.toPercent(0.0)); assertEquals(100, Brightness.toPercent(1.0))
        assertEquals(200, Brightness.toPercent(2.0)); assertEquals(130, Brightness.toPercent(1.3))
    }
    @Test fun lerpEndpointsAndMidpoint() {
        assertEquals(0.0, Brightness.lerp(0.0,1.0,0.0),1e-9); assertEquals(1.0, Brightness.lerp(0.0,1.0,1.0),1e-9)
        assertEquals(0.5, Brightness.lerp(0.0,1.0,0.5),1e-9); assertEquals(1.0, Brightness.lerp(0.0,1.0,5.0),1e-9)
    }
}
```
`MarkerTest.kt` — same two cases (`@TempDir cfg: Path`, absent→present, idempotent). Ensure the JUnit test task picks up `src/test/kotlin` (the Kotlin plugin + existing `useJUnitPlatform()` handle it; verify).

- [ ] **Step 6: Delete the ported `.java` files.** Remove the 7 main `.java` + 2 test `.java` files (NOT GammaRange.java, NOT the mixin).

- [ ] **Step 7: Build + test.**
Run: `./gradlew build` then `./gradlew :common:test --rerun-tasks`
Expected: BUILD SUCCESSFUL both loaders; `:common:test` 7/7 pass (5 Brightness + 2 Marker). The still-Java loader entrypoints compile against `BetterBrightness.MOD_ID`/`init()` (the `const val` + `@JvmStatic`).

- [ ] **Step 8: Commit.**
```bash
git add -A
git commit -m "refactor: port common classes + tests to Kotlin (mixin + GammaRange stay Java)"
```

If a Kotlin/Java interop or SAM-conversion issue blocks compilation after real effort, report BLOCKED with the exact error.

---

### Task K3: Convert loader entrypoints to Kotlin

**Files:**
- Create: `fabric/src/main/kotlin/.../fabric/BetterBrightnessFabric.kt`, `neoforge/src/main/kotlin/.../neoforge/BetterBrightnessNeoForge.kt`
- Delete: the two `.java` entrypoints
- Modify: `fabric/src/main/resources/fabric.mod.json` (entrypoint reference unchanged value), `neoforge/src/main/resources/META-INF/neoforge.mods.toml` if needed

**Interfaces:**
- Consumes: `BetterBrightness.init()` (Kotlin, `@JvmStatic`), the K1 Kotlin adapters.
- Produces: Kotlin loader entrypoints. Terminal code task.

- [ ] **Step 1: Port the Fabric entrypoint.** `BetterBrightnessFabric.kt`:
```kotlin
package io.github.fimkov.betterbrightness.fabric

import io.github.fimkov.betterbrightness.BetterBrightness
import net.fabricmc.api.ClientModInitializer

class BetterBrightnessFabric : ClientModInitializer {
    override fun onInitializeClient() {
        BetterBrightness.init()
    }
}
```
`fabric.mod.json`: the `client` entrypoint value stays `io.github.fimkov.betterbrightness.fabric.BetterBrightnessFabric` (fabric-language-kotlin handles a Kotlin class; if the kotlin adapter is required for a class entrypoint, add `"adapter": "kotlin"` to that entrypoint entry — verify which the chosen FLK version needs). Delete `BetterBrightnessFabric.java`.

- [ ] **Step 2: Port the NeoForge entrypoint.** Port `BetterBrightnessNeoForge.java` to Kotlin preserving its construction (the `@Mod(value = BetterBrightness.MOD_ID, dist = [Dist.CLIENT])`, constructor taking the mod event bus, `addListener` for `FMLClientSetupEvent` → `enqueueWork { BetterBrightness.init() }`). Kotlin annotation array syntax is `dist = [Dist.CLIENT]`. The exact event-bus wiring must match how kotlinforforge expects the `@Mod` entry (object vs class; some kotlinforforge setups use a Kotlin `object` for `@Mod` — follow KOTLIN-NOTES from K1). Delete `BetterBrightnessNeoForge.java`.

- [ ] **Step 3: Build.**
Run: `./gradlew build`
Expected: BUILD SUCCESSFUL both loaders; no Java sources remain except `GammaRange.java` + `mixin/OptionsGammaMixin.java` (verify: `find common fabric neoforge -name '*.java' -not -path '*/build/*'` lists exactly those two).

- [ ] **Step 4: Commit.**
```bash
git add -A
git commit -m "refactor: port loader entrypoints to Kotlin; only mixin + GammaRange remain Java"
```

(Runtime verification — clean launch, screen renders, gamma mixin applies + persists, both loaders boot with the Kotlin runtime present — is the controller's `runClient` check, since the build can neither apply mixins nor confirm the Kotlin runtime loads.)

---

## Self-Review

- **Spec coverage:** Kotlin build infra + adapters → K1 ✓; convert common + tests → K2 ✓; convert entrypoints → K3 ✓; mixin + GammaRange stay Java → enforced in K2/K3 file lists + Global Constraints ✓; behavior-identical → "Java file is the behavior reference" in K2 Steps 3-4 + unchanged tests/thresholds ✓; Kotlin runtime per loader → K1 Step 3 + final runtime verify ✓.
- **Placeholder scan:** complete Kotlin given for the small classes + tests; the two GUI files are ports of existing Java (the source is in-repo) with the Kotlin-specific idioms called out — not vague placeholders. The only "verify/research" gates are genuine unknowns (Kotlin adapter availability for 26.2/NeoForge-beta, exact FLK entrypoint-adapter requirement, kotlinforforge `@Mod` convention) with explicit BLOCK instructions.
- **Type consistency:** `BetterBrightness.MOD_ID` (`const val`) + `init()` (`@JvmStatic`) defined in K2, consumed by the Java entrypoints (until K3) and Kotlin entrypoints (K3); `Brightness`/`Marker`/`GammaWriter`/`BrightnessSetup` object members + `CalibrationPanel.render(...)`/`BrightnessSetupScreen(parent)` signatures preserved from the Java originals and used consistently.
- **Known risk:** kotlinforforge may not have a NeoForge 26.2-beta build (K1 BLOCK gate); mixins/Kotlin-runtime apply only at runtime (final controller verify). Both flagged.

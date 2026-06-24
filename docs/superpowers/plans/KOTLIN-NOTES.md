# KOTLIN-NOTES.md — Kotlin build infrastructure (Task K1)

**Status: BLOCKED at the research gate (Step 1).** No NeoForge Kotlin runtime
adapter exists for Minecraft 26.2 yet, so the build was NOT modified. This file
records the verified version research so a later attempt (or a controller
decision on the fallback path) can resume without re-querying maven.

Researched: 2026-06-24. Project: Better Brightness Setup, Architectury,
MC 26.2, Fabric + NeoForge, Architectury Loom **no-remap** 1.17.487
(MC 26.2 ships deobfuscated). JVM toolchain: Java 25.

---

## 1. Kotlin Gradle plugin (`org.jetbrains.kotlin.jvm`) — OK

- Latest Kotlin that the project's adapters track is **2.4.0** (via
  fabric-language-kotlin `1.13.12+kotlin.2.4.0`). Kotlin 2.2+ supports
  `jvmTarget`/`-jvm-target` up to **24** at time of writing; **JVM target 25**
  requires a Kotlin version whose compiler enumerates `JvmTarget.JVM_25`.
  This MUST be verified against the chosen Kotlin release's `JvmTarget` enum
  before pinning — if `jvmTarget = "25"` is rejected, fall back to the highest
  supported (e.g. `"24"`) while keeping the **Java** toolchain at 25
  (Kotlin-on-24 bytecode runs fine on a Java 25 runtime). This detail is moot
  until the NeoForge runtime block below is resolved.

## 2. Fabric — fabric-language-kotlin — AVAILABLE (low risk)

- Maven: `https://maven.fabricmc.net/net/fabricmc/fabric-language-kotlin/maven-metadata.xml`
- Latest: **`1.13.12+kotlin.2.4.0`** (lastUpdated 2026-06-03). MC-agnostic;
  bundles the Kotlin stdlib + the `kotlin`/`kotlin-adapter` Fabric language
  adapter and ships it as a Fabric mod.
- Wiring (when unblocked):
  - `fabric/build.gradle`: `modImplementation "net.fabricmc:fabric-language-kotlin:1.13.12+kotlin.2.4.0"`
  - `fabric/src/main/resources/fabric.mod.json`: add `"fabric-language-kotlin": "*"` to `depends`.
- Conclusion: **Fabric side is unblocked.** Kotlin-on-Fabric-only is viable.

## 3. NeoForge — kotlinforforge-neoforge — **BLOCKED for MC 26.2**

- Maven: `https://thedarkcolour.github.io/KotlinForForge/thedarkcolour/kotlinforforge-neoforge/maven-metadata.xml`
- Latest published: **`6.2.0`** (release; lastUpdated 2026-03-10). Also 6.1.0,
  6.1.0a, 6.0.0, plus the 5.x line.
- The aggregate artifact `kotlinforforge-neoforge` is a fat jar (jarjars
  kotlin-stdlib 2.3.10, kotlin-reflect, kotlinx-coroutines 1.10.2,
  kotlinx-serialization 1.10.0) and carries **no** `neoforge.mods.toml` of its
  own. The actual NeoForge mod metadata (the `kotlinforforge` language
  provider) lives in the sub-artifact **`kffmod-neoforge`**.
- The Minecraft compatibility range is declared in
  `kffmod-neoforge-<ver>/META-INF/neoforge.mods.toml`:

  | kotlinforforge | bundled Kotlin | `minecraft` versionRange       | Covers MC 26.2? |
  |----------------|----------------|--------------------------------|-----------------|
  | **6.2.0**      | 2.3.10         | `[1.21.9,26.2)` (**excl. 26.2**) | **NO**          |
  | 6.1.0 / 6.1.0a | 2.3.0          | `[1.21.9,26.2)` (**excl. 26.2**) | **NO**          |
  | 6.0.0          | 2.2.20         | `[1.21.9,1.22)`                | NO              |
  | 5.11.0         | 2.3.0          | `[1.20.6,1.22)`                | NO              |

  All `modId="minecraft"` upper bounds are **exclusive at 26.2** (or earlier),
  i.e. the newest build (6.2.0) explicitly supports MC up to *but not
  including* 26.2. Project target is MC **26.2** (NeoForge **26.2.0.7-beta**).
  The KotlinForForge README's current NeoForge example is for MC **1.21.10**.
  `loaderVersion="[5,)"` is the FML/NeoForge *loader* major (fine); the
  blocking constraint is the **Minecraft** dependency range.

- The `modLoader`/dependency wiring that *would* be used once a compatible
  build exists (from `kffmod-neoforge` `neoforge.mods.toml`):
  - `neoforge/build.gradle`: `implementation "thedarkcolour:kotlinforforge-neoforge:<ver>"`
    (add the repo `maven { url "https://thedarkcolour.github.io/KotlinForForge/" }`).
  - `neoforge/src/main/resources/META-INF/neoforge.mods.toml`:
    - `modLoader = "kotlinforforge"` (replacing `javafml`)
    - `loaderVersion = "[5,)"`
    - add a dependency block:
      ```toml
      [[dependencies.betterbrightness]]
      modId = "kotlinforforge"
      type = "required"
      versionRange = "[<ver>,)"
      ordering = "NONE"
      side = "BOTH"
      ```

## 4. Decision (controller's call — per brief HARD GATE)

Do NOT fake the NeoForge Kotlin runtime. Options, in the brief's words:
1. **Wait** for a kotlinforforge build whose `kffmod-neoforge` mods.toml
   includes MC 26.2 (range opens to `[…,26.3)` or similar).
2. **Shadow-bundle** the Kotlin stdlib into the NeoForge jar *without*
   kotlinforforge, keep `modLoader = "javafml"`, and write the NeoForge
   entrypoint in Java (or a Kotlin class invoked from a Java entrypoint) so no
   `kotlinforforge` language provider is required.
3. **Kotlin on Fabric only** — port `:common` + `:fabric` to Kotlin (FLK 1.13.12
   is ready), leave `:neoforge` Java for now.

Build was left untouched (still green Java). No probe, no commit.

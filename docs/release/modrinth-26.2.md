# Modrinth release — Better Brightness Setup v1.0.0 (MC 26.2)

## Files to upload
- `releases/fabric/26.2/betterbrightness-1.0.0+mc26.2-fabric.jar`  → loaders **Fabric**, **Quilt**
- `releases/neoforge/26.2/betterbrightness-1.0.0+mc26.2-neoforge.jar` → loader **NeoForge**

(Two separate Modrinth versions, OR one version with both files — both files can sit on one version if you tag all three loaders.)

## Version metadata
| Field | Value |
|---|---|
| Version number | `1.0.0+mc26.2` |
| Version name | `1.0.0 for MC 26.2` |
| Release channel | Release |
| Game versions | `26.2` |
| Loaders | Fabric, Quilt, NeoForge |
| Environment | Client |

## Dependencies (set on the Modrinth version)
| Mod | Type |
|---|---|
| Cloth Config API | **required** |
| Mod Menu | optional (Fabric/Quilt mod-list button) |
| Sodium | optional (inline calibration icons in Sodium's video settings) |
| Fabric API | required (Fabric/Quilt only) |
| Architectury API | required |

## Project setup (if the project doesn't exist yet)
- **Name:** Better Brightness Setup
- **Summary:** A beautiful first-launch screen to calibrate your in-game brightness, with a faithful preview of how blocks will actually look.
- **Categories:** Utility, Decoration *(client-side)*
- **Client/Server:** Client required, Server unsupported
- **License:** MIT

---

## Description (paste into the project body)

**Better Brightness Setup** greets you on first launch with a clean, vanilla-styled screen to set your brightness *properly* — so dark caves are atmospheric, not pitch-black, and not washed-out either.

**Why it's better than dragging the vanilla gamma slider blind:**
- **Faithful preview.** Four reference tiles (creeper, deepslate, coal ore, diamond ore) are tinted by Minecraft's *actual* lightmap gamma curve at their light level — they brighten exactly as those blocks would in-world as you move the slider. Calibrate until the "hidden" tile stays dark and the "bright" tile reads clearly.
- **A real button, not a mystery slider.** In Video Settings, the gamma slider is replaced by a **Setup Brightness** button that opens the calibration screen.
- **Sodium-native.** With Sodium installed, the brightness row in its Video Settings gets the four calibration icons inlined right under the slider (in Sodium's own style).
- **Configurable headroom.** Default max is vanilla 100%. A config option (reachable from the mod list) raises it up to **500%** for very dark monitors.
- **Localized** into every Minecraft language (plus Pirate Speak, LOLCAT and Upside-down for fun).

Client-side only. Works on **Fabric, Quilt, and NeoForge**.

Requires **Cloth Config API**. Optional: **Mod Menu** (mod-list config button) and **Sodium** (inline icons).

---

## Changelog — v1.0.0
- First-launch brightness calibration screen with four live, gamma-accurate reference tiles.
- Faithful in-game lightmap gamma preview (mirrors `lightmap.fsh`).
- Vanilla Video Settings: gamma slider replaced by a **Setup Brightness** button.
- Sodium integration: inline calibration icons under the brightness slider (soft dependency).
- Cloth Config option `maxBrightnessPercent` (default 100, up to 500), reachable from the mod list (NeoForge native; Fabric/Quilt via Mod Menu).
- Brightness persistence above vanilla's 1.0 gamma cap.
- Full localization: machine-translated for all MC locales, hand-corrected major languages, plus Pirate/LOLCAT/Upside-down.

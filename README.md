# Better Brightness Setup

A Minecraft 26.2 client mod that shows a brightness calibration screen on first launch so players can choose a comfortable gamma before stepping into the world.

## What it does

On the very first game launch, the mod intercepts the title screen and opens a calibration screen with four live texture panels: a torch-lit cave (dark reference), a daylight forest, a dark room with a single light source, and a Creeper face. All four panels update in real time as the player moves a slider.

The slider maps to gamma in the range 0.0 (pitch black) to 2.0 (double brightness). When the player clicks Done, the chosen gamma is written to Minecraft's `options.txt` and the game continues normally. The screen appears only once; a small marker file (`config/betterbrightness/.done`) prevents it from showing again on subsequent launches.

## Supported platforms

| Loader    | Status                                                                                                    |
|-----------|-----------------------------------------------------------------------------------------------------------|
| Fabric    | Built and supported. Jar: `fabric/build/libs/`.                                                          |
| NeoForge  | Built and supported. Jar: `neoforge/build/libs/`.                                                        |
| Quilt     | Supported via Quilt's Fabric-compatibility layer. Install the Fabric jar in a Quilt instance — Quilt reads `fabric.mod.json` and runs the Fabric `ClientModInitializer` automatically. No separate Quilt jar needed. |
| Forge     | Not available. `architectury-forge` is not yet published for Minecraft 26.2.                             |

## Building

Requires Java 25 and a network connection for the first build (Gradle downloads dependencies).

```
./gradlew build
```

Built jars land in:
- `fabric/build/libs/betterbrightness-*.jar`
- `neoforge/build/libs/betterbrightness-*.jar`

## Gamma above 1.0 persists across restarts

The slider allows values up to gamma 2.0, and the chosen value survives a restart. A client-only mixin
(`OptionsGammaMixin`, bundled into both loader jars) widens the vanilla gamma option's value space from
`[0, 1]` to `[0, 2]` by replacing its `ValueSet` with `UnitDouble.INSTANCE.xmap(v -> v * 2, v -> v / 2)`.
Validation now runs against the *halved* value, so `set(2.0)` and `load(2.0)` are accepted instead of
being rejected and reset. The mixin targets **only** gamma (the other 16 options that share
`UnitDouble.INSTANCE` are untouched), via a sliced `@ModifyArg` on gamma's `OptionInstance` constructor.

Note: because the disk codec now stores `gamma / 2`, the in-game gamma slider also spans 0..2.

## Dependencies

- Minecraft 26.2
- Architectury API 21.0.2
- Fabric: fabric-loader ≥ 0.19.3, fabric-api 0.153.0+26.2
- NeoForge: 26.2.0.7-beta
- Java 25

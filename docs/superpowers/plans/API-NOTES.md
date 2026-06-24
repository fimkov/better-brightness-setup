# API-NOTES — Minecraft 26.2 verified APIs

Every signature below was read from the **decompiled / `javap`-inspected Minecraft 26.2 client
jar** (already-deobfuscated, real names) and the **Architectury API 21.0.2** jar that this project
resolves. Anything not personally verified is marked **UNVERIFIED**. Later tasks must use these
exact names — several differ substantially from older Minecraft (1.20/1.21) and from the brief's
expectations.

Toolchain context: MC `26.2`, Architectury Loom `1.17.487` (**no-remap** variant — see "Build notes"),
Architectury API `21.0.2`, Fabric loader `0.19.3`, Fabric API `0.153.0+26.2`, NeoForge `26.2.0.7-beta`,
Java 25 toolchain, Gradle 9.6.

---

## ⚠️ Major 26.2 API changes vs. older Minecraft (read this first)

These renames/relocations affect almost every later GUI task. They are NOT what the brief assumed.

| Old (≤1.21.x) name                     | 26.2 name (VERIFIED)                                             |
|----------------------------------------|-----------------------------------------------------------------|
| `GuiGraphics`                          | **`net.minecraft.client.gui.GuiGraphicsExtractor`**             |
| `ResourceLocation`                     | **`net.minecraft.resources.Identifier`** (used by `blit`)       |
| `Renderable.render(GuiGraphics,…)`     | **`Renderable.extractRenderState(GuiGraphicsExtractor, int, int, float)`** |
| `Screen.render(GuiGraphics,…)`         | **`Screen.extractRenderState(GuiGraphicsExtractor, int, int, float)`** |
| `GuiGraphics.drawString(...)`          | **`GuiGraphicsExtractor.text(Font, …)`**                       |
| `GuiGraphics.drawCenteredString(...)`  | **`GuiGraphicsExtractor.centeredText(Font, …)`**               |
| `EntityType.CREEPER`                   | **`net.minecraft.world.entity.EntityTypes.CREEPER`** (plural holder) |
| `InventoryScreen.renderEntityInInventoryFollowsMouse(...)` | **`InventoryScreen.extractEntityInInventoryFollowsMouse(...)`** |

26.2 uses a **render-state extraction** architecture: screens/widgets implement
`extractRenderState(...)` rather than an immediate-mode `render(...)`. Drawing calls
(`text`, `centeredText`, `fill`, `blit`) are issued on the `GuiGraphicsExtractor` passed in.

---

## Screen base — `net.minecraft.client.gui.screens.Screen`

```java
public abstract class Screen extends AbstractContainerEventHandler implements Renderable
```

- **Init override (VERIFIED):**
  ```java
  protected void init()                 // override this to add widgets
  public final void init(int width, int height)   // engine entrypoint; do not override
  ```
- **Render / extract override (VERIFIED — replaces the brief's expected render()):**
  ```java
  // from Renderable:
  void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);
  // Screen overrides it:
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
  ```
  There is **no** `render(GuiGraphics, int, int, float)` in 26.2.
- **Font access (VERIFIED):** `protected final Font font;` and `public Font getFont()`.
- **Add a widget (VERIFIED):**
  ```java
  protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget)
  ```

## TitleScreen — `net.minecraft.client.gui.screens.TitleScreen`  (VERIFIED)
```java
public class TitleScreen extends Screen
```

---

## GuiGraphics replacement — `net.minecraft.client.gui.GuiGraphicsExtractor`

Package `net.minecraft.client.gui`. Drawing methods (all VERIFIED):

```java
// fill
public void fill(int x0, int y0, int x1, int y1, int col)
public void fill(RenderPipeline pipeline, int x0, int y0, int x1, int y1, int col)

// text (was drawString)
public void text(Font font, @Nullable String str, int x, int y, int color)
public void text(Font font, @Nullable String str, int x, int y, int color, boolean dropShadow)
public void text(Font font, Component str, int x, int y, int color)
public void text(Font font, FormattedCharSequence str, int x, int y, int color)

// centeredText (was drawCenteredString)
public void centeredText(Font font, String str, int x, int y, int color)
public void centeredText(Font font, Component text, int x, int y, int color)

// blit (note: uses Identifier, RenderPipeline)
public void blit(RenderPipeline renderPipeline, Identifier texture,
                 int x, int y, float u, float v, int width, int height,
                 int textureWidth, int textureHeight, int color)
```

**Obtaining a `Font`:** `Minecraft.getInstance().font` (public field, VERIFIED) or, inside a
`Screen`, `this.font` / `this.getFont()`. `Font` is `net.minecraft.client.gui.Font` with
`public int width(String|FormattedText|FormattedCharSequence)`.

---

## Minecraft — `net.minecraft.client.Minecraft`  (all VERIFIED)
```java
public static Minecraft getInstance()
public final Options options;                  // public field
public final Font font;                        // public field
public EntityRenderDispatcher getEntityRenderDispatcher()
```

---

## Options + gamma  ★ CRITICAL FOR TASK 5 ★

`net.minecraft.client.Options` (VERIFIED):
```java
public OptionInstance<Double> gamma()          // accessor (line 1426)
private final OptionInstance<Double> gamma = new OptionInstance<>(
    "options.gamma", OptionInstance.noTooltip(), <captionFn>,
    OptionInstance.UnitDouble.INSTANCE,        // ← the ValueSet
    0.5,                                        // ← default value
    OptionInstance.NO_ACTION);
```

`net.minecraft.client.OptionInstance<T>` (VERIFIED):
```java
public T get()                                  // returns current value
public void set(T value)                        // see clamp behavior below
public OptionInstance.ValueSet<T> values()
```

### Does `OptionInstance.set()` clamp gamma above 1.0?  → **NO. It REJECTS (does not clamp).**

`OptionInstance.set` (verified body):
```java
public void set(final T value) {
    T newValue = this.values.validateValue(value).orElseGet(() -> {
        LOGGER.error("Illegal option value {} for {}", value, this.caption.getString());
        return this.initialValue;          // <-- falls back to default, NOT a clamp
    });
    ...
}
```

gamma's ValueSet is `OptionInstance.UnitDouble.INSTANCE`, whose validation is (verified):
```java
public Optional<Double> validateValue(final Double value) {
    return value >= 0.0 && value <= 1.0 ? Optional.of(value) : Optional.empty();
}
```

**Conclusion for Task 5 (GammaWriter):**
- Calling `options.gamma().set(x)` with `x` in **[0.0, 1.0]** stores `x` as-is.
- Calling it with `x > 1.0` (or `x < 0.0`) does **NOT clamp to the bound** — `validateValue`
  returns `Optional.empty()`, `set()` logs `"Illegal option value …"` and **resets gamma to its
  initial value `0.5`**. The out-of-range value is discarded entirely.
- Therefore **you cannot push gamma above 1.0 through `OptionInstance.set()`** in 26.2. If Task 5
  needs an "over-bright" effect beyond 1.0 it must bypass validation — write the backing `value`
  field directly via an access widener / mixin / reflection — because the public API hard-rejects
  it. If staying within [0,1] is acceptable, `set()` is safe and stores exact values.
- The gamma slider label treats `value*100`: `0`→`options.gamma.min`, `50`→`options.gamma.default`,
  `100`→`options.gamma.max`. So the in-game slider spans 0.0–1.0 with default 0.5.

---

## Button — `net.minecraft.client.gui.components.Button`  (VERIFIED)
```java
public static Button.Builder builder(Component message, Button.OnPress onPress)
```
`Button.Builder` fluent methods (VERIFIED): `pos(int,int)`, `width(int)`, `size(int,int)`,
`bounds(int,int,int,int)`, `build()` → `Button`.

## Slider base — `net.minecraft.client.gui.components.AbstractSliderButton`  (VERIFIED)
```java
public abstract class AbstractSliderButton extends AbstractWidget.WithInactiveMessage   // nested class of AbstractWidget; import simple name AbstractSliderButton
public AbstractSliderButton(int x, int y, int width, int height, Component message, double initialValue)
protected double value;                     // 0.0..1.0 slider position
protected abstract void updateMessage();    // override: refresh the displayed label
protected abstract void applyValue();        // override: push `this.value` into your model
```

---

## Entity-in-GUI render helper  (renamed in 26.2)

`net.minecraft.client.gui.screens.inventory.InventoryScreen` (VERIFIED):
```java
public static void extractEntityInInventoryFollowsMouse(
    GuiGraphicsExtractor graphics,
    int x1, int y1, int x2, int y2,        // bounding box
    int scale,
    float yOffset,
    float mouseX, float mouseY,
    LivingEntity entity)
```
(Old name `renderEntityInInventoryFollowsMouse` does not exist in 26.2.)

### Getting a Creeper to render
- EntityType constant: **`net.minecraft.world.entity.EntityTypes.CREEPER`**
  (`public static final EntityType<Creeper> CREEPER`) — note the **plural `EntityTypes`** holder
  class, not `EntityType`. (VERIFIED via javap.)
- Entity class: `net.minecraft.world.entity.monster.Creeper`, ctor (VERIFIED):
  ```java
  public Creeper(EntityType<? extends Creeper> type, Level level)
  ```
  So a render instance is `new Creeper(EntityTypes.CREEPER, <clientLevel>)`. The client level is
  `Minecraft.getInstance().level` (VERIFIED: `public net.minecraft.client.multiplayer.ClientLevel level;`).
  Note: at the title screen `level` is `null`, so Task 6 must supply a level for the Creeper
  (a throwaway/dummy level) or rely on the entity-in-inventory helper — confirm in Task 6.

---

## Architectury API 21.0.2  (VERIFIED via javap on architectury-21.0.2)

```java
// dev.architectury.event.events.client.ClientGuiEvent
public static final Event<ClientGuiEvent.ScreenInitPost> INIT_POST;
// register a listener:  ClientGuiEvent.INIT_POST.register(listener)
// the functional interface:
public interface ClientGuiEvent.ScreenInitPost {
    void init(net.minecraft.client.gui.screens.Screen screen,
              dev.architectury.hooks.client.screen.ScreenAccess access);
}
// (also available: INIT_PRE = Event<ScreenInitPre>, RENDER_PRE/POST, SET_SCREEN, RENDER_HUD)

// dev.architectury.platform.Platform
public static java.nio.file.Path getConfigFolder()   // returns java.nio.file.Path
public static java.nio.file.Path getGameFolder()
public static java.nio.file.Path getModsFolder()
```

---

## Build notes (toolchain reality for 26.2)

- **Minecraft 26.2 ships ALREADY DEOBFUSCATED.** The client jar contains real `net.minecraft.*`
  names (10,372 classes, zero obfuscated single-letter classes), and the Mojang version manifest
  for 26.2 has **no `client_mappings`/`server_mappings`** downloads. Parchment and Yarn publish
  **nothing** for 26.2. NeoForm `26.2-1` performs **no remapping step**.
- Consequently `loom.officialMojangMappings()` / any mappings layer **fails** ("Failed to find
  official mojang mappings for 26.2"). Architectury Loom itself directs you to the fix:
  > *"The Minecraft version '26.2' is unobfuscated (no mappings). Forge / NeoForge support for
  > unobfuscated Minecraft is through the `dev.architectury.loom-no-remap` plugin instead."*
- This project therefore applies **`dev.architectury.loom-no-remap` 1.17.487** (not the standard
  `dev.architectury.loom`). Under no-remap there is **no `mappings` dependency, no `remapJar` task,
  and no `namedElements`/`transformProductionX` configurations**; the shadowed `jar` is the
  distributable, and `:common` is consumed as a plain project dependency. `mod*` configurations
  are re-created manually as aliases of the standard ones.

## Forge status

- **Forge is NOT included.** `architectury-forge` is not published for the 26.2 line
  (`architectury-forge-21.0.2.jar` → HTTP 404), and Forge lags newest Minecraft. The `forge/`
  module is deferred to a later task per the brief's fallback clause. Fabric + NeoForge build green.

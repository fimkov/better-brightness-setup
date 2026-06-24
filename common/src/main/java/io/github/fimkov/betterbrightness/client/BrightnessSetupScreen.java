package io.github.fimkov.betterbrightness.client;

import dev.architectury.platform.Platform;
import io.github.fimkov.betterbrightness.Brightness;
import io.github.fimkov.betterbrightness.GammaWriter;
import io.github.fimkov.betterbrightness.Marker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Vanilla-styled first-launch brightness calibration screen.
 *
 * <p>26.2 GUI uses a render-state extraction architecture: the engine draws the menu/panorama
 * background for us via {@code Screen.extractBackground(...)} (mirrored from {@code ConfirmScreen},
 * which does not override {@code extractRenderState} at all), so this screen only issues its own
 * draws (the title) and then defers to {@code super.extractRenderState(...)} to render the added
 * widgets. There is no immediate-mode {@code render(GuiGraphics, ...)} in 26.2.
 *
 * <p>A brightness slider (mapped 0..1 -> gamma 0..2 via {@link Brightness#sliderToGamma(double)})
 * and a Done button live near the bottom. Done writes the chosen gamma + the persistent marker;
 * closing via Esc also writes the marker through {@link #removed()} so "show once" holds regardless
 * of how the screen is dismissed.
 */
public class BrightnessSetupScreen extends Screen {

    private final Screen parent;
    private double slider = 0.5; // start mid (gamma 1.0)

    // Fade-in timing.
    private long openMillis = 0L;
    private static final double FADE_MS = 250.0;

    /**
     * The 4 live calibration tiles. Each fades a real game texture in/out as the slider
     * moves; the captions read top-left -> bottom-right by descending threshold. The creeper tile
     * (highest threshold) is the classic "shouldn't be visible in the dark" calibration target.
     * Rendered as textures, not a live 3D entity, because this screen opens over the title screen
     * where {@code Minecraft.getInstance().level} is null and entity construction would crash.
     */
    private final CalibrationPanel[] panels = {
            // Creeper face region (8,8 8x8) from the 64x32 entity skin atlas — the "should stay hidden"
            // target. Threshold 1.35 so the creeper reaches invisible (vis 0) exactly where deepslate is
            // ~50% visible ((1.35-1.1)/0.5 = 0.5): scrolling down from max, the creeper vanishes at the
            // same gamma the "barely visible" tile is half-faded — the intended calibration sweet spot.
            new CalibrationPanel(1.35, Component.translatable("betterbrightness.panel.hidden"),
                    Identifier.withDefaultNamespace("textures/entity/creeper/creeper.png"),
                    64, 32, 8.0f, 8.0f, 8, 8),
            new CalibrationPanel(1.1, Component.translatable("betterbrightness.panel.faint"),
                    Identifier.withDefaultNamespace("textures/block/deepslate.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new CalibrationPanel(0.6, Component.translatable("betterbrightness.panel.clear"),
                    Identifier.withDefaultNamespace("textures/block/coal_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new CalibrationPanel(0.2, Component.translatable("betterbrightness.panel.bright"),
                    Identifier.withDefaultNamespace("textures/block/diamond_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
    };

    public BrightnessSetupScreen(Screen parent) {
        super(Component.translatable("betterbrightness.title"));
        this.parent = parent;
    }

    /** Live gamma for the current slider position. Read by the calibration panels. */
    public double currentGamma() {
        return Brightness.sliderToGamma(slider);
    }

    /** Screen-open fade alpha: 0 -> 1 over FADE_MS milliseconds, eased linearly. */
    private float fadeAlpha() {
        if (openMillis == 0L) return 1f;
        double t = (System.currentTimeMillis() - openMillis) / FADE_MS;
        return (float) Math.max(0.0, Math.min(1.0, t));
    }

    /** Slider label: "Brightness: NNN%" using the 0..200 scale. */
    private static String sliderLabel(double sliderValue) {
        return "Brightness: " + Brightness.toPercent(Brightness.sliderToGamma(sliderValue)) + "%";
    }

    @Override
    protected void init() {
        openMillis = System.currentTimeMillis();

        int cx = this.width / 2;

        addRenderableWidget(new AbstractSliderButton(
                cx - 100, this.height - 56, 200, 20,
                Component.literal(sliderLabel(slider)), slider) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(sliderLabel(this.value)));
            }

            @Override
            protected void applyValue() {
                BrightnessSetupScreen.this.slider = this.value;
            }
        });

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onDone())
                .bounds(cx - 100, this.height - 30, 200, 20)
                .build());
    }

    private void onDone() {
        GammaWriter.setGammaRaw(currentGamma());
        Marker.markDone(Platform.getConfigFolder());
        Minecraft.getInstance().gui.setScreen(parent);
    }

    /** Esc / any dismissal still marks done. Idempotent. */
    @Override
    public void removed() {
        Marker.markDone(Platform.getConfigFolder());
        super.removed();
    }

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
}

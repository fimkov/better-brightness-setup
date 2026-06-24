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

    /**
     * The 4 live calibration tiles (Task 6). Each fades a real game texture in/out as the slider
     * moves; the captions read top-left -> bottom-right by descending threshold. The creeper tile
     * (highest threshold) is the classic "shouldn't be visible in the dark" calibration target.
     * Rendered as textures, not a live 3D entity, because this screen opens over the title screen
     * where {@code Minecraft.getInstance().level} is null and entity construction would crash.
     */
    private final CalibrationPanel[] panels = {
            // Creeper face region (8,8 8x8) from the 64x32 entity skin atlas — stays hidden until very bright.
            new CalibrationPanel(1.6, Component.literal("Эту фигуру не должно быть видно"),
                    Identifier.withDefaultNamespace("textures/entity/creeper/creeper.png"),
                    64, 32, 8.0f, 8.0f, 8, 8),
            new CalibrationPanel(1.1, Component.literal("Эта должна быть едва видна"),
                    Identifier.withDefaultNamespace("textures/block/deepslate.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new CalibrationPanel(0.6, Component.literal("Эта — хорошо видна"),
                    Identifier.withDefaultNamespace("textures/block/coal_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new CalibrationPanel(0.2, Component.literal("Эта — ярко видна"),
                    Identifier.withDefaultNamespace("textures/block/diamond_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
    };

    public BrightnessSetupScreen(Screen parent) {
        super(Component.translatable("betterbrightness.title"));
        this.parent = parent;
    }

    /** Live gamma for the current slider position. Read by the calibration panels (Task 6). */
    public double currentGamma() {
        return Brightness.sliderToGamma(slider);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        addRenderableWidget(new AbstractSliderButton(
                cx - 100, this.height - 56, 200, 20,
                Component.literal(String.format("Brightness: %.2f", Brightness.sliderToGamma(slider))), slider) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(
                        String.format("Brightness: %.2f", Brightness.sliderToGamma(this.value))));
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

    /** Esc / any dismissal still marks done. Idempotent (Task 3). */
    @Override
    public void removed() {
        Marker.markDone(Platform.getConfigFolder());
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Background is drawn by the engine (Screen.extractBackground) outside this method.
        graphics.centeredText(this.font, this.title, this.width / 2, 24, 0xFFFFFF);

        // Live calibration panels, drawn before the widgets so the slider/Done sit on top.
        renderPanels(graphics);

        // Render the added widgets (slider, Done button).
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** Lays the 4 panels out in a centered 2x2 grid and re-tints them from the live gamma. */
    private void renderPanels(GuiGraphicsExtractor graphics) {
        final int pw = 120, ph = 80;
        final int gapX = 24, gapY = 24 + 16; // extra vertical room for each panel's caption
        final int gridW = pw * 2 + gapX;
        final int ox = (this.width - gridW) / 2;
        final int oy = 44;
        double gamma = currentGamma();
        for (int i = 0; i < panels.length; i++) {
            int col = i % 2, row = i / 2;
            int x = ox + col * (pw + gapX);
            int y = oy + row * (ph + gapY);
            panels[i].render(graphics, this.font, x, y, pw, ph, gamma);
        }
    }
}

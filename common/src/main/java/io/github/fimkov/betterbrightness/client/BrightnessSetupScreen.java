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

    public BrightnessSetupScreen(Screen parent) {
        super(Component.literal("Brightness Setup"));
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
                Component.literal(""), slider) {
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
        // Task 6 adds the live calibration panels here (drawn before widgets so the slider/Done
        // sit on top). Leaving this spot clear for them.

        graphics.centeredText(this.font, this.title, this.width / 2, 24, 0xFFFFFF);

        // Render the added widgets (slider, Done button).
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}

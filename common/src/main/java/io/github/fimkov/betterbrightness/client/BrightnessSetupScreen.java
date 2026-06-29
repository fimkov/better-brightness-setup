package io.github.fimkov.betterbrightness.client;

import dev.architectury.platform.Platform;
import io.github.fimkov.betterbrightness.BetterBrightnessConfig;
import io.github.fimkov.betterbrightness.Brightness;
import io.github.fimkov.betterbrightness.GammaWriter;
import io.github.fimkov.betterbrightness.Marker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class BrightnessSetupScreen extends Screen {
    private final Screen parent;

    private double slider = 0.5;

    private long openMillis = 0L;
    private static final double FADE_MS = 250.0;

    private final CalibrationPanel[] panels = {
            new CalibrationPanel(1, Component.translatable("betterbrightness.panel.hidden"),
                    Identifier.withDefaultNamespace("textures/entity/creeper/creeper.png"),
                    64, 32, 8.0f, 8.0f, 8, 8),
            new CalibrationPanel(4, Component.translatable("betterbrightness.panel.faint"),
                    Identifier.withDefaultNamespace("textures/block/deepslate.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new CalibrationPanel(7, Component.translatable("betterbrightness.panel.clear"),
                    Identifier.withDefaultNamespace("textures/block/coal_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new CalibrationPanel(10, Component.translatable("betterbrightness.panel.bright"),
                    Identifier.withDefaultNamespace("textures/block/diamond_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
    };

    public BrightnessSetupScreen(Screen parent) {
        super(Component.translatable("betterbrightness.title"));
        this.parent = parent;

        try {
            double gamma = Minecraft.getInstance().options.gamma().get();
            this.slider = Brightness.gammaToSlider(gamma, BetterBrightnessConfig.maxPercent());
        } catch (Throwable ignored) {
        }
    }

    public double currentGamma() {
        return Brightness.sliderToGamma(slider);
    }

    private float fadeAlpha() {
        if (openMillis == 0L) return 1f;
        double t = (System.currentTimeMillis() - openMillis) / FADE_MS;
        return (float) Math.max(0.0, Math.min(1.0, t));
    }

    private static Component sliderLabel(double sliderValue) {
        return Component.translatable("betterbrightness.slider.brightness",
                Brightness.toPercent(Brightness.sliderToGamma(sliderValue)));
    }

    @Override
    protected void init() {
        openMillis = System.currentTimeMillis();

        int cx = this.width / 2;

        addRenderableWidget(new AbstractSliderButton(
                cx - 100, this.height - 56, 200, 20,
                sliderLabel(slider), slider) {
            @Override
            protected void updateMessage() {
                setMessage(sliderLabel(this.value));
            }

            @Override
            protected void applyValue() {
                BrightnessSetupScreen.this.slider = this.value;
            }
        });

        addRenderableWidget(Button.builder(Component.translatable("betterbrightness.done_button"), b -> onDone())
                .bounds(cx - 100, this.height - 30, 200, 20)
                .build());
    }

    private void onDone() {
        GammaWriter.setGammaRaw(currentGamma());
        Marker.markDone(Platform.getConfigFolder());
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        Marker.markDone(Platform.getConfigFolder());
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
        float fade = fadeAlpha();
        int titleColor = (Math.round(fade * 255f) << 24) | 0xFFFFFF;
        int subColor   = (Math.round(fade * 255f) << 24) | 0xB9B9C0;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 18, titleColor);
        graphics.drawCenteredString(this.font, Component.translatable("betterbrightness.instruction"),
                this.width / 2, 34, subColor);
        renderRow(graphics, fade);
    }

    private void renderRow(GuiGraphics graphics, float fade) {
        final int n = panels.length;
        final int gap = 16;
        final int topY = 52;
        final int labelRoom = 24;
        final int sliderY = this.height - 56;

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

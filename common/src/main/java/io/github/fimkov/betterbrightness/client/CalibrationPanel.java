package io.github.fimkov.betterbrightness.client;

import io.github.fimkov.betterbrightness.Brightness;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class CalibrationPanel {
    private final int lightLevel;
    private final Component caption;
    private final Identifier texture;
    private final int texW;
    private final int texH;

    private final float u;
    private final float v;
    private final int srcW;
    private final int srcH;

    private double displayed = 0.0;
    private long lastMillis = 0L;
    private static final double EASE_TAU_MS = 90.0;

    public CalibrationPanel(int lightLevel, Component caption, Identifier texture,
                            int texW, int texH, float u, float v, int srcW, int srcH) {
        this.lightLevel = lightLevel;
        this.caption = caption;
        this.texture = texture;
        this.texW = texW;
        this.texH = texH;
        this.u = u;
        this.v = v;
        this.srcW = srcW;
        this.srcH = srcH;
    }

    public void render(GuiGraphicsExtractor g, Font font, int x, int y, int tile, double gamma, float fadeAlpha) {
        g.fill(x, y, x + tile, y + tile, withAlpha(0xFF101010, fadeAlpha));
        g.fill(x, y, x + tile, y + 1, withAlpha(0xFF3A3A42, fadeAlpha));
        g.fill(x, y + tile - 1, x + tile, y + tile, withAlpha(0xFF3A3A42, fadeAlpha));
        g.fill(x, y, x + 1, y + tile, withAlpha(0xFF3A3A42, fadeAlpha));
        g.fill(x + tile - 1, y, x + tile, y + tile, withAlpha(0xFF3A3A42, fadeAlpha));

        long now = System.currentTimeMillis();
        double target = Brightness.displayedBrightness(gamma, lightLevel / 15.0);
        if (lastMillis == 0L) {
            displayed = target;
        } else {
            double dt = now - lastMillis;
            displayed = Brightness.lerp(displayed, target, dt / EASE_TAU_MS);
        }
        lastMillis = now;

        int margin = 8;
        int s = tile - margin * 2;
        int ix = x + margin;
        int iy = y + margin;

        int gray = (int) Math.round(displayed * 255.0);
        int screenAlpha = (int) Math.round(Math.max(0f, Math.min(1f, fadeAlpha)) * 255.0f);
        int argb = (screenAlpha << 24) | (gray << 16) | (gray << 8) | gray;
        try {
            g.blit(RenderPipelines.GUI_TEXTURED, texture, ix, iy, u, v, s, s, srcW, srcH, texW, texH, argb);
        } catch (Throwable t) {
            g.fill(ix, iy, ix + s, iy + s, withAlpha(0xFF402020, fadeAlpha));
        }

        int textColor = (Math.round(fadeAlpha * 255.0f) << 24) | 0xFFFFFF;
        g.centeredText(font, caption, x + tile / 2, y + tile + 6, textColor);
    }

    private static int withAlpha(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        int na = Math.round(a * Math.max(0f, Math.min(1f, f)));
        return (na << 24) | (argb & 0xFFFFFF);
    }
}

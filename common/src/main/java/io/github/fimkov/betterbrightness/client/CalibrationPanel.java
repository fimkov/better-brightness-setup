package io.github.fimkov.betterbrightness.client;

import io.github.fimkov.betterbrightness.Brightness;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * One live calibration tile: a real game texture tinted by the FAITHFUL in-game lightmap brightness for a
 * block sitting at this tile's {@code lightLevel} (0..15) — so it brightens/darkens exactly as that block
 * would as the brightness slider moves — with a caption underneath.
 *
 * <p>26.2 is render-state based: drawing happens by issuing calls on the {@link GuiGraphicsExtractor}
 * handed to a screen's {@code extractRenderState}. We render a real game <em>texture</em> rather than
 * a live 3D entity because this screen opens over the title screen where
 * {@code Minecraft.getInstance().level} is {@code null}; constructing a {@code Creeper} (or any entity)
 * needs a non-null {@code Level} and would crash.
 *
 * <p>The tint is carried in the {@code color} (ARGB) argument of
 * {@code blit(RenderPipeline, Identifier, x, y, u, v, w, h, texW, texH, color)}: the RGB is a grayscale
 * multiplier equal to {@link Brightness#displayedBrightness} (the real MC lightmap value for this light
 * level at the live gamma), so the texture is dimmed exactly as the game dims a poorly-lit block; the alpha
 * byte carries only the screen's fade-in. This is a true preview of how the block will look at that gamma,
 * not a stylized opacity fade.
 */
public final class CalibrationPanel {

    private final int lightLevel;
    private final Component caption;
    private final Identifier texture;
    private final int texW;
    private final int texH;
    // Sub-region of the texture to sample (entity skins are atlases; blocks are the whole 16x16).
    private final float u;
    private final float v;
    private final int srcW;
    private final int srcH;
    // Per-panel easing state (eases the displayed brightness so the tile transitions smoothly).
    private double displayed = 0.0;
    private long lastMillis = 0L;
    private static final double EASE_TAU_MS = 90.0; // smaller = snappier

    /**
     * @param lightLevel block-light level (0..15) this tile simulates; lower = darker, brightens later
     * @param caption    label drawn centered below the panel
     * @param texture    game texture identifier to blit
     * @param texW       full texture width  (for UV normalization)
     * @param texH       full texture height (for UV normalization)
     * @param u          left edge of the sampled region within the texture
     * @param v          top edge of the sampled region within the texture
     * @param srcW       width of the sampled region
     * @param srcH       height of the sampled region
     */
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
        // Dark backing + soft 1px inner frame.
        g.fill(x, y, x + tile, y + tile, withAlpha(0xFF101010, fadeAlpha));
        g.fill(x, y, x + tile, y + 1, withAlpha(0xFF3A3A42, fadeAlpha));               // top
        g.fill(x, y + tile - 1, x + tile, y + tile, withAlpha(0xFF3A3A42, fadeAlpha)); // bottom
        g.fill(x, y, x + 1, y + tile, withAlpha(0xFF3A3A42, fadeAlpha));               // left
        g.fill(x + tile - 1, y, x + tile, y + tile, withAlpha(0xFF3A3A42, fadeAlpha)); // right

        // Ease the displayed brightness toward the live target: the real MC lightmap value for this tile's
        // light level at the current gamma.
        long now = System.currentTimeMillis();
        double target = Brightness.displayedBrightness(gamma, lightLevel / 15.0);
        if (lastMillis == 0L) {
            displayed = target;
        } else {
            double dt = now - lastMillis;
            displayed = Brightness.lerp(displayed, target, dt / EASE_TAU_MS);
        }
        lastMillis = now;

        // Square texture centered in the tile content area (margin 8). Square src -> square dest: no stretch.
        int margin = 8;
        int s = tile - margin * 2;
        int ix = x + margin;
        int iy = y + margin;
        // Grayscale RGB multiplier = displayed brightness (dims the texture exactly as the in-game lightmap
        // would); alpha carries only the screen fade-in, so the tile stays opaque and just darkens/brightens.
        int gray = (int) Math.round(displayed * 255.0);
        int screenAlpha = (int) Math.round(Math.max(0f, Math.min(1f, fadeAlpha)) * 255.0f);
        int argb = (screenAlpha << 24) | (gray << 16) | (gray << 8) | gray;
        try {
            g.blit(RenderPipelines.GUI_TEXTURED, texture, ix, iy, u, v, s, s, srcW, srcH, texW, texH, argb);
        } catch (Throwable t) {
            g.fill(ix, iy, ix + s, iy + s, withAlpha(0xFF402020, fadeAlpha));
        }

        // Target label centered below the tile, faded with the screen.
        int textColor = (Math.round(fadeAlpha * 255.0f) << 24) | 0xFFFFFF;
        g.centeredText(font, caption, x + tile / 2, y + tile + 6, textColor);
    }

    /** Multiply an ARGB color's alpha by f in [0,1]. */
    private static int withAlpha(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        int na = Math.round(a * Math.max(0f, Math.min(1f, f)));
        return (na << 24) | (argb & 0xFFFFFF);
    }
}

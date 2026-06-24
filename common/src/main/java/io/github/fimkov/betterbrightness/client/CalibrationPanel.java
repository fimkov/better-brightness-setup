package io.github.fimkov.betterbrightness.client;

import io.github.fimkov.betterbrightness.Brightness;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * One live calibration tile: a real game texture that fades from invisible to opaque as the
 * brightness slider rises past this panel's {@code threshold}, with a caption underneath.
 *
 * <p>26.2 is render-state based: drawing happens by issuing calls on the {@link GuiGraphicsExtractor}
 * handed to a screen's {@code extractRenderState}. We render a real game <em>texture</em> rather than
 * a live 3D entity because this screen opens over the title screen where
 * {@code Minecraft.getInstance().level} is {@code null}; constructing a {@code Creeper} (or any entity)
 * needs a non-null {@code Level} and would crash. A texture blit tinted by {@link Brightness#panelVisibility}
 * is still genuine live-reactive rendering of game content — it re-tints every frame from the live gamma.
 *
 * <p>The tint is carried in the {@code color} (ARGB) argument of
 * {@code blit(RenderPipeline, Identifier, x, y, u, v, w, h, texW, texH, color)}: the alpha byte is the
 * live visibility, so {@code (alpha << 24) | 0xFFFFFF} fades the texture in/out while leaving RGB white
 * (i.e. the texture's own colours show through unmodulated).
 */
public final class CalibrationPanel {

    private final double threshold;
    private final Component caption;
    private final Identifier texture;
    private final int texW;
    private final int texH;
    // Sub-region of the texture to sample (entity skins are atlases; blocks are the whole 16x16).
    private final float u;
    private final float v;
    private final int srcW;
    private final int srcH;

    /**
     * @param threshold gamma at which the content starts becoming visible
     * @param caption   label drawn centered below the panel
     * @param texture   game texture identifier to blit
     * @param texW      full texture width  (for UV normalization)
     * @param texH      full texture height (for UV normalization)
     * @param u         left edge of the sampled region within the texture
     * @param v         top edge of the sampled region within the texture
     * @param srcW      width of the sampled region
     * @param srcH      height of the sampled region
     */
    public CalibrationPanel(double threshold, Component caption, Identifier texture,
                            int texW, int texH, float u, float v, int srcW, int srcH) {
        this.threshold = threshold;
        this.caption = caption;
        this.texture = texture;
        this.texW = texW;
        this.texH = texH;
        this.u = u;
        this.v = v;
        this.srcW = srcW;
        this.srcH = srcH;
    }

    public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, double gamma) {
        // Dark backing so the fade reads as "emerging from the dark".
        g.fill(x, y, x + w, y + h, 0xFF101010);

        double vis = Brightness.panelVisibility(gamma, threshold);
        int alpha = (int) Math.round(vis * 255.0);
        int argb = (alpha << 24) | 0xFFFFFF;

        // Inner area: leave a small margin inside the backing.
        int ix = x + 6;
        int iy = y + 6;
        int iw = w - 12;
        int ih = h - 12;

        try {
            g.blit(RenderPipelines.GUI_TEXTURED, texture,
                    ix, iy, u, v, iw, ih, srcW, srcH, texW, texH, argb);
        } catch (Throwable t) {
            // One bad texture must not break the whole screen.
            g.fill(x + 6, y + 6, x + w - 6, y + h - 6, 0xFF402020);
        }

        g.centeredText(font, caption, x + w / 2, y + h + 4, 0xFFFFFF);
    }
}

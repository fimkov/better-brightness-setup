package io.github.fimkov.betterbrightness.mixin.sodium;

import io.github.fimkov.betterbrightness.Brightness;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.gui.Dimensioned;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * For the Sodium brightness slider ONLY ({@code sodium:general.gamma}): widen the visible track and,
 * in the extra vertical space {@link OptionListWidgetMixin} added to this one row, draw the SAME 4
 * compact calibration icons + captions as {@code BrightnessSetupScreen}, each fading from the live
 * gamma. For every other slider this mixin is a strict no-op (early return).
 *
 * <p><b>Target (verified from Sodium 0.9.0 bytecode).</b> The inner element
 * {@code net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl$SliderControlElement} is
 * <em>package-private</em>, so it cannot be named in Java from our package — we target it by string via
 * {@code @Mixin(targets = ...)}. Members <em>concretely declared</em> on the element
 * ({@code getOption}, {@code getSliderX}, {@code getSliderY}, {@code getThumbPositionForValue}) are reached
 * with {@code @Shadow}; the positioning getters ({@code getX}/{@code getLimitX}/{@code getLimitY}) are
 * <em>interface-default</em> methods on {@link Dimensioned} that the element only inherits — Sponge Mixin
 * cannot {@code @Shadow} a method that isn't declared in the target class (it throws
 * {@code InvalidMixinException} and the whole mixin fails to apply), so we call those via
 * {@code ((Dimensioned)(Object) this)}. We {@code @Inject} at
 * {@code TAIL} of
 * {@code extractRenderState(GuiGraphicsExtractor, int, int, float)} (the 26.2 render-state draw path), so the
 * icons render after Sodium's own row background, label and (when hovered/focused) track + thumb. The bound
 * option is read through the shadowed public {@code getOption()} ({@code IntegerOption}); its id is read via
 * {@link OptionIdAccessor}.
 *
 * <p><b>Widening the track.</b> {@code Layout.SLIDER_WIDTH} (= 90) is a {@code public static final int}, so
 * the compiler inlines it as a constant everywhere {@code SliderControlElement} uses it — there is no field
 * access to redirect, and changing it globally would widen every slider anyway. So we draw our own wider
 * track (and re-draw the thumb at the proportional position) for the gamma row only, extending left of
 * where Sodium's slider ends. Sodium's own (narrow) track is only drawn while hovered/focused; ours is
 * always visible, giving the brightness option a consistently wider track.
 *
 * <p><b>Icons.</b> The same 4 calibration textures / thresholds as {@code BrightnessSetupScreen}
 * (creeper 1.35, deepslate 1.1, coal_ore 0.6, diamond_ore 0.2), laid out as an evenly-spaced row of 4
 * columns BELOW the track, one small square icon centred per column with a SHORT label under it. We blit
 * the texture directly (alpha = {@code Brightness.panelVisibility(gamma, threshold)} so each icon fades in
 * as the brightness crosses its threshold) instead of reusing {@code CalibrationPanel.render}, because that
 * draws the long calibration caption ("Should be hidden" etc.) centred on the tile — far too wide for a
 * ~77px inline column (the captions collapsed onto each other into garbage). The inline labels are short
 * single words ({@code betterbrightness.short.hidden|barely|clear|bright}). Gamma is read live from
 * {@code Minecraft.getInstance().options.gamma().get()}; all icon rendering is wrapped in try/catch so a
 * texture/render hiccup can never break Sodium's GUI.
 *
 * <p>Fragile + version-locked to Sodium {@code 0.9.0} / MC 26.2 internals. The config is
 * {@code required:false}; without Sodium installed this never applies.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl$SliderControlElement", remap = false)
public abstract class SliderControlElementMixin {

    /** The one option this mixin acts on. */
    private static final Identifier BRIGHTNESS_OPTION_ID = Identifier.parse("sodium:general.gamma");

    // --- Shadowed Sodium internals (methods concretely DECLARED on SliderControlElement) ---
    @Shadow
    public abstract IntegerOption getOption();

    @Shadow
    public abstract int getSliderX();

    @Shadow
    public abstract int getSliderY();

    @Shadow
    public abstract double getThumbPositionForValue(int value);

    // NOTE: getX()/getLimitX()/getLimitY() are interface-DEFAULT methods on Dimensioned that the element
    // only inherits — they are NOT @Shadow-able (Mixin can't locate a method not declared in the target).
    // They are read via ((Dimensioned)(Object) this) below.

    /**
     * One inline calibration icon: the SAME texture/threshold as {@code BrightnessSetupScreen}'s panels,
     * minus the long caption. {@code threshold} drives the live alpha; {@code labelKey} is the short
     * single-word column label. {@code u}/{@code v}/{@code srcW}/{@code srcH}/{@code texW}/{@code texH}
     * select the texture sub-region (creeper face is an 8x8 region of the 64x32 skin; blocks are full 16x16).
     */
    private record Icon(double threshold, String labelKey, Identifier texture,
                        int texW, int texH, float u, float v, int srcW, int srcH) {
    }

    /** The 4 columns, left -> right by descending threshold: hidden, barely, clear, bright. */
    private static final Icon[] ICONS = {
            new Icon(1.35, "betterbrightness.short.hidden",
                    Identifier.withDefaultNamespace("textures/entity/creeper/creeper.png"),
                    64, 32, 8.0f, 8.0f, 8, 8),
            new Icon(1.1, "betterbrightness.short.barely",
                    Identifier.withDefaultNamespace("textures/block/deepslate.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new Icon(0.6, "betterbrightness.short.clear",
                    Identifier.withDefaultNamespace("textures/block/coal_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new Icon(0.2, "betterbrightness.short.bright",
                    Identifier.withDefaultNamespace("textures/block/diamond_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
    };

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void betterbrightness$drawCalibrationIcons(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Scope strictly to the brightness option; every other slider is untouched.
        final IntegerOption option;
        final Identifier id;
        try {
            option = this.getOption();
            id = ((OptionIdAccessor) (Object) option).betterbrightness$getId();
        } catch (Throwable t) {
            return;
        }
        if (!BRIGHTNESS_OPTION_ID.equals(id)) {
            return;
        }

        try {
            Font font = Minecraft.getInstance().font;
            double gamma = Minecraft.getInstance().options.gamma().get();

            // getX/getLimitX/getLimitY are Dimensioned interface-default methods (not @Shadow-able);
            // call them through the interface on this element.
            Dimensioned dim = (Dimensioned) (Object) this;

            int sliderX = this.getSliderX();
            int sliderRight = sliderX + Layout.SLIDER_WIDTH;
            int sliderY = this.getSliderY();
            int rowLeft = dim.getX() + Layout.OPTION_TEXT_SIDE_PADDING;
            int rowRight = dim.getLimitX() - Layout.OPTION_TEXT_SIDE_PADDING;

            // --- Wider track (gamma row only) ---
            // Extend the track a full SLIDER_WIDTH further left, clamped to the row, up to where
            // Sodium's slider ends on the right.
            int wideLeft = Math.max(rowLeft, sliderX - Layout.SLIDER_WIDTH);
            int wideRight = sliderRight;
            int wideWidth = Math.max(1, wideRight - wideLeft);
            int trackY = sliderY + Layout.SLIDER_HEIGHT / 2;
            graphics.fill(wideLeft, trackY, wideRight, trackY + 1, Colors.FOREGROUND);

            double t = this.getThumbPositionForValue(option.getValidatedValue());
            int thumbX = (int) (wideLeft + t * wideWidth);
            graphics.fill(thumbX - 2, sliderY, thumbX + 2, sliderY + Layout.SLIDER_HEIGHT, Colors.FOREGROUND);

            // --- 4 calibration icons + short labels, evenly spaced across the full row width ---
            int n = ICONS.length;
            int iconsTop = sliderY + Layout.SLIDER_HEIGHT + 2;
            int iconsBottom = dim.getLimitY() - 2;
            int labelRoom = font.lineHeight + 1;
            float colWidth = (rowRight - rowLeft) / (float) n;
            // Small square icon that fits a column and the vertical room left after the label line.
            int iconByHeight = (iconsBottom - iconsTop) - labelRoom;
            int iconSize = Math.max(8, Math.min(18, Math.min((int) colWidth - 4, iconByHeight)));
            for (int i = 0; i < n; i++) {
                Icon icon = ICONS[i];
                int colCenterX = rowLeft + (int) ((i + 0.5f) * colWidth);

                // Icon texture, centred in the column, faded in by live gamma vs this icon's threshold.
                double vis = Brightness.panelVisibility(gamma, icon.threshold());
                int alpha = (int) Math.round(vis * 255.0);
                int argb = (alpha << 24) | 0xFFFFFF;
                int ix = colCenterX - iconSize / 2;
                try {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), ix, iconsTop,
                            icon.u(), icon.v(), iconSize, iconSize, icon.srcW(), icon.srcH(),
                            icon.texW(), icon.texH(), argb);
                } catch (Throwable blitError) {
                    graphics.fill(ix, iconsTop, ix + iconSize, iconsTop + iconSize, 0xFF402020);
                }

                // Short label centred on THIS column (truncated to the column width if needed).
                String label = Component.translatable(icon.labelKey()).getString();
                int maxLabelW = (int) colWidth - 2;
                while (label.length() > 1 && font.width(label) > maxLabelW) {
                    label = label.substring(0, label.length() - 1);
                }
                graphics.centeredText(font, Component.literal(label),
                        colCenterX, iconsTop + iconSize + 1, Colors.FOREGROUND);
            }
        } catch (Throwable ignored) {
            // Never let icon rendering break Sodium's options GUI.
        }
    }
}

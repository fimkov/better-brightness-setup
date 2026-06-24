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
 * For the Sodium brightness slider ONLY ({@code sodium:general.gamma}): in the extra vertical space
 * {@link OptionListWidgetMixin} added to this one row, draw the SAME 4 compact calibration icons +
 * captions as {@code BrightnessSetupScreen} BELOW Sodium's own (unmodified) slider, each fading from
 * the live gamma. For every other slider this mixin is a strict no-op (early return).
 *
 * <p><b>Target (verified from Sodium 0.9.0 bytecode).</b> The inner element
 * {@code net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl$SliderControlElement} is
 * <em>package-private</em>, so it cannot be named in Java from our package — we target it by string via
 * {@code @Mixin(targets = ...)}. The one member <em>concretely declared</em> on the element we need
 * ({@code getOption}) is reached with {@code @Shadow}; the positioning getters
 * ({@code getX}/{@code getLimitX}/{@code getLimitY}) are
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
 * <p><b>No track widening.</b> {@code Layout.SLIDER_WIDTH} (= 90) is a {@code public static final int} that
 * the compiler inlines everywhere {@code SliderControlElement} uses it, so Sodium's own track width cannot
 * be changed without affecting every slider. An earlier attempt to overdraw a wider track for the gamma row
 * produced a visible <em>second</em> track overlapping Sodium's label and value, so we leave Sodium's slider
 * exactly as-is and only add the icon row beneath it.
 *
 * <p><b>Icons.</b> The same 4 calibration textures / block-light levels as {@code BrightnessSetupScreen}
 * (creeper @ light 1, deepslate @ 4, coal_ore @ 7, diamond_ore @ 10), laid out as an evenly-spaced row of 4
 * columns in the blank space {@link OptionListWidgetMixin} reserves BELOW this row (Sodium centres its own
 * slider + value label at the top of the now normal-height row, so they stay pinned to the top and the icons
 * sit directly under them). Each column is one small square icon with a 1px WHITE outline (so the four
 * reference tiles are always clearly framed) and a SHORT label under it. We blit the texture directly,
 * tinting its RGB by {@link Brightness#displayedBrightness} (the faithful MC lightmap brightness for the
 * tile's light level at the live gamma — it dims/lifts exactly as the real block would), instead of reusing
 * {@code CalibrationPanel.render}, because that draws the long calibration caption ("Should be hidden" etc.)
 * centred on the tile — far too wide for the inline column (the captions collapsed onto each other into
 * garbage). The inline labels are short single words ({@code betterbrightness.short.hidden|barely|clear|bright}).
 *
 * <p><b>Live value.</b> Gamma is read from the slider's <em>pending</em> value
 * ({@code getOption().getValidatedValue()} / 100.0) — the exact 0–200 value Sodium's own
 * {@code extractRenderState} formats into the displayed "%" every frame — so the icons fade live as the
 * slider is dragged, NOT only after Apply. (Falls back to {@code options.gamma().get()} if that read ever
 * fails.) All icon rendering is wrapped in try/catch so a texture/render hiccup can never break Sodium's GUI.
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

    // NOTE: getX()/getLimitX()/getLimitY() are interface-DEFAULT methods on Dimensioned that the element
    // only inherits — they are NOT @Shadow-able (Mixin can't locate a method not declared in the target).
    // They are read via ((Dimensioned)(Object) this) below.

    /**
     * One inline calibration icon: the SAME texture/light-level as {@code BrightnessSetupScreen}'s panels,
     * minus the long caption. {@code lightLevel} (0..15) is the simulated block-light the tile sits at and
     * drives its faithful brightness via {@link Brightness#displayedBrightness}; {@code labelKey} is the
     * short single-word column label. {@code u}/{@code v}/{@code srcW}/{@code srcH}/{@code texW}/{@code texH}
     * select the texture sub-region (creeper face is an 8x8 region of the 64x32 skin; blocks are full 16x16).
     */
    private record Icon(int lightLevel, String labelKey, Identifier texture,
                        int texW, int texH, float u, float v, int srcW, int srcH) {
    }

    /** The 4 columns, left -> right by ascending light level: hidden(1), barely(4), clear(7), bright(10). */
    private static final Icon[] ICONS = {
            new Icon(1, "betterbrightness.short.hidden",
                    Identifier.withDefaultNamespace("textures/entity/creeper/creeper.png"),
                    64, 32, 8.0f, 8.0f, 8, 8),
            new Icon(4, "betterbrightness.short.barely",
                    Identifier.withDefaultNamespace("textures/block/deepslate.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new Icon(7, "betterbrightness.short.clear",
                    Identifier.withDefaultNamespace("textures/block/coal_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new Icon(10, "betterbrightness.short.bright",
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

            // LIVE pending value the slider is currently showing (0-200), NOT the committed gamma:
            // Sodium's own extractRenderState formats this exact value into the "%" label every frame, so
            // reading it here makes the icons fade as the slider is dragged, before Apply.
            double gamma;
            try {
                gamma = ((Number) option.getValidatedValue()).doubleValue() / 100.0;
            } catch (Throwable valueError) {
                gamma = Minecraft.getInstance().options.gamma().get();
            }

            // getX/getLimitX/getLimitY are Dimensioned interface-default methods (not @Shadow-able);
            // call them through the interface on this element.
            Dimensioned dim = (Dimensioned) (Object) this;
            int rowLeft = dim.getX() + Layout.OPTION_TEXT_SIDE_PADDING;
            int rowRight = dim.getLimitX() - Layout.OPTION_TEXT_SIDE_PADDING;

            // --- 4 calibration icons + short labels, evenly spaced across the full row width, drawn in the
            //     blank space OptionListWidgetMixin reserved BELOW this (normal-height) row. Sodium renders
            //     its slider + value label at the top of the row; the icons sit directly under them. ---
            final int n = ICONS.length;
            final int iconSize = 16;
            int iconsTop = dim.getLimitY() + 3;
            float colWidth = (rowRight - rowLeft) / (float) n;

            for (int i = 0; i < n; i++) {
                Icon icon = ICONS[i];
                int colCenterX = rowLeft + (int) ((i + 0.5f) * colWidth);
                int ix = colCenterX - iconSize / 2;

                // White 1px outline framing each reference tile (always visible; the texture inside fades).
                final int white = 0xFFFFFFFF;
                graphics.fill(ix - 1, iconsTop - 1, ix + iconSize + 1, iconsTop, white);                      // top
                graphics.fill(ix - 1, iconsTop + iconSize, ix + iconSize + 1, iconsTop + iconSize + 1, white); // bottom
                graphics.fill(ix - 1, iconsTop - 1, ix, iconsTop + iconSize + 1, white);                       // left
                graphics.fill(ix + iconSize, iconsTop - 1, ix + iconSize + 1, iconsTop + iconSize + 1, white);  // right

                // Icon texture, centred in the column, tinted by the FAITHFUL in-game lightmap brightness for
                // this icon's light level at the live gamma (grayscale RGB multiply, opaque) — it dims/lifts
                // exactly as the real block would, instead of just fading its opacity.
                double bright = Brightness.displayedBrightness(gamma, icon.lightLevel() / 15.0);
                int gray = (int) Math.round(bright * 255.0);
                int argb = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
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
                        colCenterX, iconsTop + iconSize + 3, Colors.FOREGROUND);
            }
        } catch (Throwable ignored) {
            // Never let icon rendering break Sodium's options GUI.
        }
    }
}

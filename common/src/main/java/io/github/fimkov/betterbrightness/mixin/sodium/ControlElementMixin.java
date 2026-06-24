package io.github.fimkov.betterbrightness.mixin.sodium;

import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.gui.Dimensioned;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Grows ONLY the Sodium brightness row's painted container (row background + focus border) downward so it
 * visually wraps the 4 calibration icons {@link SliderControlElementMixin} draws below the slider.
 *
 * <p><b>Why here and not by moving the slider.</b> {@code ControlElement.extractRenderState} paints the row
 * background with {@code drawRect(getX, getY, getLimitX, getLimitY)} and the focus border with
 * {@code drawBorder(...getLimitY)} — both take the bottom edge from {@code getLimitY()}. The slider track,
 * the value label ({@code getCenterY()}) and all hover/click hit-testing ({@code isMouseOver}) also derive
 * from the element's box, so if we made the box itself taller Sodium would re-centre the slider + label in
 * the middle of an oversized row (the "widget too big, floating in the centre" look) and we'd have to chase
 * the hit-test geometry too. Instead the element keeps its <em>normal</em> height — slider, label and
 * click routing all stay correctly pinned to the top of the row — and we make two render-time changes for
 * the gamma row alone: (1) enlarge the two {@code getLimitY()} reads that feed the background + border
 * draws, so the painted box extends down over the blank band {@link OptionListWidgetMixin} reserved below
 * the row and the icons sit visually <em>inside</em> the brightness container; and (2) enlarge the
 * {@code isMouseOver} check that sets the {@code hovered} flag, so hovering anywhere over that taller
 * container (the icon band included, not just the slider) lights the row's hover background like every other
 * option. Both changes live only in {@code extractRenderState}, so the actual clickable region is untouched.
 *
 * <p>This {@code @Redirect} fires for every option row (the base render path), but returns {@code getLimitY()}
 * unchanged for all rows except {@code sodium:general.gamma}. The {@code +lineHeight*4} extension MUST match
 * {@link OptionListWidgetMixin#betterbrightness$extraHeight()} (the reserved advance) and
 * {@code SliderControlElementMixin}'s icon layout, so the container, the icons and the next row all line up.
 *
 * <p>Fragile + version-locked to Sodium {@code 0.9.0} / MC 26.2 internals. The whole
 * {@code betterbrightness.sodium} mixin config is {@code required:false}; without Sodium it never applies.
 */
@Mixin(value = ControlElement.class, remap = false)
public abstract class ControlElementMixin {

    /** The one row whose painted container we grow. */
    private static final Identifier BRIGHTNESS_OPTION_ID = Identifier.parse("sodium:general.gamma");

    @Shadow
    public abstract Option getOption();

    /** Height of the icon band added below the gamma row; MUST match the other two Sodium mixins. */
    private static int betterbrightness$band() {
        return Minecraft.getInstance().font.lineHeight * 4;
    }

    /** True only for the brightness row. */
    private boolean betterbrightness$isGamma(ControlElement self) {
        try {
            Option option = self.getOption();
            Identifier id = ((OptionIdAccessor) (Object) option).betterbrightness$getId();
            return BRIGHTNESS_OPTION_ID.equals(id);
        } catch (Throwable ignored) {
            // Any shift in Sodium's option model -> treat as a normal row (no extension).
            return false;
        }
    }

    /**
     * Redirect the {@code getLimitY()} reads in {@code extractRenderState} (background {@code drawRect} +
     * focus {@code drawBorder}). For the brightness row, return a bottom edge one icon-band lower so the
     * painted container wraps the icons; for every other row, return the real {@code getLimitY()}.
     */
    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/gui/options/control/ControlElement;getLimitY()I"
            ))
    private int betterbrightness$extendGammaContainer(ControlElement self) {
        int limitY = ((Dimensioned) (Object) self).getLimitY();
        return betterbrightness$isGamma(self) ? limitY + betterbrightness$band() : limitY;
    }

    /**
     * Redirect the {@code isMouseOver(mouseX, mouseY)} call in {@code extractRenderState} that sets the
     * {@code hovered} flag driving the background highlight. For the brightness row, hit-test against the
     * SAME extended container (so hovering the icon band — not just the slider — highlights the row, like
     * every other option); for every other row, defer to the real {@code isMouseOver}. This affects only the
     * render-time hover flag, never click routing, so the slider stays the only interactive control.
     */
    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/gui/options/control/ControlElement;isMouseOver(DD)Z"
            ))
    private boolean betterbrightness$hoverOverGammaContainer(ControlElement self, double mouseX, double mouseY) {
        if (!betterbrightness$isGamma(self)) {
            return self.isMouseOver(mouseX, mouseY);
        }
        Dimensioned dim = (Dimensioned) (Object) self;
        return mouseX >= dim.getX() && mouseX < dim.getLimitX()
                && mouseY >= dim.getY() && mouseY < dim.getLimitY() + betterbrightness$band();
    }
}

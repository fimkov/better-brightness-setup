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

@Mixin(value = ControlElement.class, remap = false)
public abstract class ControlElementMixin {
    private static final Identifier BRIGHTNESS_OPTION_ID = Identifier.parse("sodium:general.gamma");

    @Shadow
    public abstract Option getOption();

    private static int betterbrightness$band() {
        return Minecraft.getInstance().font.lineHeight * 4;
    }

    private boolean betterbrightness$isGamma(ControlElement self) {
        try {
            Option option = self.getOption();
            Identifier id = ((OptionIdAccessor) (Object) option).betterbrightness$getId();
            return BRIGHTNESS_OPTION_ID.equals(id);
        } catch (Throwable ignored) {
            return false;
        }
    }

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

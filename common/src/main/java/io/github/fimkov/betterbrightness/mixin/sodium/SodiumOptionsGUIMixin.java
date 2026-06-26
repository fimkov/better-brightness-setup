package io.github.fimkov.betterbrightness.mixin.sodium;

import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import me.jellysquid.mods.sodium.client.gui.options.control.Control;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlElement;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SodiumOptionsGUI.class, remap = false)
public abstract class SodiumOptionsGUIMixin {
    private int betterbrightness$shift;

    private static int betterbrightness$band() {
        return Minecraft.getInstance().font.lineHeight + 16 + 6;
    }

    @Inject(method = "rebuildGUIOptions", at = @At("HEAD"), remap = false)
    private void betterbrightness$resetShift(CallbackInfo ci) {
        this.betterbrightness$shift = 0;
    }

    @ModifyArg(
            method = "rebuildGUIOptions",
            at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/util/Dim2i;<init>(IIII)V"),
            index = 1,
            remap = false)
    private int betterbrightness$shiftRows(int y) {
        return y + this.betterbrightness$shift;
    }

    @Redirect(
            method = "rebuildGUIOptions",
            at = @At(
                    value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/gui/options/control/Control;"
                            + "createElement(Lme/jellysquid/mods/sodium/client/util/Dim2i;)"
                            + "Lme/jellysquid/mods/sodium/client/gui/options/control/ControlElement;"),
            remap = false)
    private ControlElement<?> betterbrightness$reserveRoomAfterBrightness(Control<?> control, Dim2i dim) {
        ControlElement<?> element = control.createElement(dim);
        try {
            if (Component.translatable("options.gamma").equals(control.getOption().getName())) {
                this.betterbrightness$shift += betterbrightness$band();
            }
        } catch (Throwable ignored) {
        }
        return element;
    }
}

package io.github.fimkov.betterbrightness.mixin.sodium;

import io.github.fimkov.betterbrightness.BetterBrightnessConfig;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptionPages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = SodiumGameOptionPages.class, remap = false)
public abstract class SodiumGameOptionPagesMixin {
    @ModifyArg(
            method = "lambda$general$6",
            at = @At(
                    value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/gui/options/control/SliderControl;<init>("
                            + "Lme/jellysquid/mods/sodium/client/gui/options/Option;"
                            + "III"
                            + "Lme/jellysquid/mods/sodium/client/gui/options/control/ControlValueFormatter;)V"),
            index = 2)
    private static int betterbrightness$widenBrightnessMax(int max) {
        return Math.max(max, BetterBrightnessConfig.maxPercent());
    }
}

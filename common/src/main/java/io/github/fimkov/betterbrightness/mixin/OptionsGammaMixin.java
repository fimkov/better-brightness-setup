package io.github.fimkov.betterbrightness.mixin;

import io.github.fimkov.betterbrightness.GammaRange;
import net.minecraft.client.Options;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(Options.class)
public abstract class OptionsGammaMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/OptionInstance;<init>("
                            + "Ljava/lang/String;"
                            + "Lnet/minecraft/client/OptionInstance$TooltipSupplier;"
                            + "Lnet/minecraft/client/OptionInstance$CaptionBasedToString;"
                            + "Lnet/minecraft/client/OptionInstance$ValueSet;"
                            + "Ljava/lang/Object;"
                            + "Ljava/util/function/Consumer;)V"
            ),
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "stringValue=options.gamma"),
                    to = @At(value = "CONSTANT", args = "stringValue=options.guiScale")
            ),
            index = 3
    )
    private OptionInstance.ValueSet<Double> betterbrightness$widenGammaRange(OptionInstance.ValueSet<Double> original) {
        return GammaRange.INSTANCE;
    }

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/OptionInstance;<init>("
                            + "Ljava/lang/String;"
                            + "Lnet/minecraft/client/OptionInstance$TooltipSupplier;"
                            + "Lnet/minecraft/client/OptionInstance$CaptionBasedToString;"
                            + "Lnet/minecraft/client/OptionInstance$ValueSet;"
                            + "Ljava/lang/Object;"
                            + "Ljava/util/function/Consumer;)V"
            ),
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "stringValue=options.gamma"),
                    to = @At(value = "CONSTANT", args = "stringValue=options.guiScale")
            ),
            index = 2
    )
    private OptionInstance.CaptionBasedToString<Double> betterbrightness$gammaLabel(
            OptionInstance.CaptionBasedToString<Double> original) {
        return (caption, value) -> {
            int pct = (int)(value * 100.0);
            Component label;
            if (pct == 0) {
                label = Component.translatable("options.gamma.min");
            } else if (pct == 50) {
                label = Component.translatable("options.gamma.default");
            } else if (pct == 100) {
                label = Component.translatable("options.gamma.max");
            } else if (pct == 200) {
                label = Component.translatable("betterbrightness.options.gamma.brightest");
            } else {
                label = Component.literal(pct + "%");
            }
            return Options.genericValueLabel(caption, label);
        };
    }
}

package io.github.fimkov.betterbrightness.mixin;

import io.github.fimkov.betterbrightness.GammaRange;
import net.minecraft.client.Options;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Widens the vanilla {@code gamma} (brightness) option's value space from {@code [0, 1]} to the
 * {@code [0, 5.0]} ceiling (the 500% config max / 100) so a chosen gamma above
 * {@code 1.0} (up to 500%) survives a restart.
 *
 * <p><b>Why a mixin is required.</b> Gamma's {@code ValueSet} is {@code OptionInstance.UnitDouble.INSTANCE},
 * whose {@code validateValue} <em>rejects</em> (does not clamp) anything outside {@code [0, 1]} and falls
 * back to the initial value. {@code Options.load()} reads {@code options.txt} and calls
 * {@code gamma.set(parsed)}; for a stored gamma {@code > 1.0} this logs "Illegal option value" and resets
 * to {@code 0.5}. So {@code > 1.0} written for the session does not persist across launches.
 *
 * <p><b>Fix.</b> Replace gamma's {@code ValueSet} with {@link GammaRange#INSTANCE} — a custom
 * {@code [0, 5.0]} {@code SliderableValueSet<Double>} whose codec is {@code Codec.doubleRange(0.0, 5.0)},
 * so the value written to {@code options.txt} is the <em>exposed</em> gamma (disk gamma == real gamma).
 * Now {@code set(2.0)} and {@code load(2.0)} are valid and {@code > 1.0} persists, and there is no value
 * doubling — an existing {@code gamma:0.5} still reads back as {@code 0.5}. (The vanilla gamma slider
 * itself is replaced by a "Setup Brightness" button via {@code VideoSettingsScreenMixin}; this widened
 * value space exists so the value the setup screen / Sodium write — up to the configured max — persists.
 * The ceiling is the constant 5.0, not the live config max, so changing the config needs no restart.)
 * (An {@code xmap(v -> v * 2, v -> v / 2)} was rejected: its xmapped codec persists the
 * underlying {@code [0, 1]} value, i.e. {@code gamma / 2}, which would silently double every existing
 * user's brightness on install.)
 *
 * <p><b>Targeting ONLY gamma.</b> {@code UnitDouble.INSTANCE} is shared by 17 options, so we must not
 * touch {@code UnitDouble} itself. Instead we {@code @ModifyArg} the {@code ValueSet} argument (index 3)
 * of the {@code OptionInstance.<init>} call that constructs gamma, sliced to start at the
 * {@code "options.gamma"} string constant and end before {@code "options.guiScale"} — the only
 * {@code OptionInstance} constructed in that window is gamma, so the injection is unambiguous and
 * does not affect the other 16 {@code UnitDouble} options. Client-only.
 */
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
                            + "Lnet/minecraft/client/OptionInstance$ValueUpdateListener;)V"
            ),
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "stringValue=options.gamma"),
                    to = @At(value = "CONSTANT", args = "stringValue=options.guiScale")
            ),
            index = 3
    )
    private OptionInstance.ValueSet<Double> betterbrightness$widenGammaRange(OptionInstance.ValueSet<Double> original) {
        // [0, 5.0] value space that stores the exposed value on disk (disk gamma == real gamma).
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
                            + "Lnet/minecraft/client/OptionInstance$ValueUpdateListener;)V"
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

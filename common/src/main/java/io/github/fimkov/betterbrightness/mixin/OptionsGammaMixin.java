package io.github.fimkov.betterbrightness.mixin;

import net.minecraft.client.Options;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Widens the vanilla {@code gamma} (brightness) option's value space from {@code [0, 1]} to
 * {@code [0, 2]} so a chosen gamma above {@code 1.0} survives a restart.
 *
 * <p><b>Why a mixin is required.</b> Gamma's {@code ValueSet} is {@code OptionInstance.UnitDouble.INSTANCE},
 * whose {@code validateValue} <em>rejects</em> (does not clamp) anything outside {@code [0, 1]} and falls
 * back to the initial value. {@code Options.load()} reads {@code options.txt} and calls
 * {@code gamma.set(parsed)}; for a stored gamma {@code > 1.0} this logs "Illegal option value" and resets
 * to {@code 0.5}. So {@code > 1.0} written for the session does not persist across launches.
 *
 * <p><b>Fix.</b> Replace gamma's {@code ValueSet} with
 * {@code UnitDouble.INSTANCE.xmap(v -> v * 2.0, v -> v / 2.0)} — a {@code [0, 2]} value space that
 * validates the <em>halved</em> value against {@code UnitDouble}'s {@code [0, 1]}. Now {@code set(2.0)}
 * and {@code load(2.0)} are valid (the disk codec stores {@code value / 2}, i.e. {@code [0, 1]}, and
 * decodes it back to {@code [0, 2]}), so {@code > 1.0} persists and the in-game gamma slider spans
 * {@code 0..2}.
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
        // [0, 2] value space: validate/store the halved value against UnitDouble's [0, 1].
        return OptionInstance.UnitDouble.INSTANCE.xmap(v -> v * 2.0, v -> v / 2.0);
    }
}

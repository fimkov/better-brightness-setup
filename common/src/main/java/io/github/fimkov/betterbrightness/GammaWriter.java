package io.github.fimkov.betterbrightness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/**
 * Writes the game's gamma (brightness) option, including values above the vanilla 1.0 ceiling.
 *
 * <p>This relies on {@code OptionsGammaMixin} having widened gamma's {@code ValueSet} to {@code [0, 2]}
 * (via {@code UnitDouble.INSTANCE.xmap(v -> v * 2, v -> v / 2)}). With that in place, {@code set(gamma)}
 * validates the halved value against {@code [0, 1]}, so the full {@code [0, 2]} range is accepted and
 * {@code save()} persists it (the disk codec stores {@code gamma / 2}, which {@code load()} decodes back
 * to {@code gamma}). No reflection or validation bypass is needed.
 */
public final class GammaWriter {

    private GammaWriter() {}

    public static void setGammaRaw(double gamma) {
        try {
            Minecraft mc = Minecraft.getInstance();
            OptionInstance<Double> opt = mc.options.gamma();
            opt.set(gamma);
            mc.options.save();
        } catch (Throwable t) {
            BetterBrightness.LOGGER.warn("[{}] could not set gamma", BetterBrightness.MOD_ID, t);
        }
    }
}

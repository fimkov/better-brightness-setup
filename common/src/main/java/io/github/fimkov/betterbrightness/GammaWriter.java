package io.github.fimkov.betterbrightness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/**
 * Writes the game's gamma (brightness) option, including values above the vanilla 1.0 ceiling.
 *
 * <p>This relies on {@code OptionsGammaMixin} having widened gamma's {@code ValueSet} to {@link
 * io.github.fimkov.betterbrightness.mixin.GammaRange}'s {@code [0, 2]} range. With that in place,
 * {@code set(gamma)} accepts the full {@code [0, 2]} range and {@code save()} persists the exposed value
 * directly (disk gamma == real gamma; {@code load()} reads it straight back). No reflection or validation
 * bypass is needed.
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

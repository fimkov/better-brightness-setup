package io.github.fimkov.betterbrightness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

import java.lang.reflect.Field;

/**
 * Writes the game's gamma (brightness) option, including values above the vanilla 1.0 ceiling.
 *
 * <p>Verified against decompiled 26.2 sources: {@code OptionInstance.set(x)} runs the value through
 * {@code UnitDouble.validateValue}, which <em>rejects</em> (does not clamp) anything outside
 * {@code [0.0, 1.0]} and falls back to the option's initial value (0.5). So for {@code gamma > 1.0}
 * we must bypass validation and write the backing field directly.
 *
 * <p>{@link OptionInstance} has two {@code T}-typed (Double) fields — {@code private final T
 * initialValue} (= 0.5) and {@code private T value} (what {@code get()} returns). We target
 * {@code value} by name; targeting "the first Double field" could hit {@code initialValue} instead.
 *
 * <p><b>Known ceiling (documented, not fixed here):</b> {@code options.save()} writes the raw value to
 * {@code options.txt}, but on the next launch {@code options.load()} calls {@code gamma.set(parsed)},
 * which re-rejects {@code > 1.0} and resets to 0.5. So a {@code > 1.0} gamma does not persist across
 * restarts. The proper fix is a Mixin widening gamma's {@code ValueSet} to {@code [0, 2]}.
 */
public final class GammaWriter {

    private GammaWriter() {}

    public static void setGammaRaw(double gamma) {
        try {
            Minecraft mc = Minecraft.getInstance();
            OptionInstance<Double> opt = mc.options.gamma();
            if (gamma >= 0.0 && gamma <= 1.0) {
                opt.set(gamma); // in range: the public API stores it as-is
            } else {
                // > 1.0 (or < 0.0): bypass validation by writing the backing field directly.
                Field f = OptionInstance.class.getDeclaredField("value");
                f.setAccessible(true);
                f.set(opt, gamma);
            }
            mc.options.save();
        } catch (Throwable t) {
            BetterBrightness.LOGGER.warn("[{}] could not set gamma", BetterBrightness.MOD_ID, t);
        }
    }
}

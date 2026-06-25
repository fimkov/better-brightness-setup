package io.github.fimkov.betterbrightness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

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

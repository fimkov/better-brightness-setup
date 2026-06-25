package io.github.fimkov.betterbrightness;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BetterBrightness {
    public static final String MOD_ID = "betterbrightness";
    public static final Logger LOGGER = LoggerFactory.getLogger("BetterBrightness");

    private static boolean configRegistered = false;

    private BetterBrightness() {
    }

    public static void init() {
        LOGGER.info("[{}] common init", MOD_ID);
        registerConfig();
        BrightnessSetup.initClient();
    }

    private static void registerConfig() {
        if (configRegistered) return;
        configRegistered = true;
        AutoConfig.register(BetterBrightnessConfig.class, GsonConfigSerializer::new);
    }
}

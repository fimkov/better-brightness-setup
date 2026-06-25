package io.github.fimkov.betterbrightness;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point for Better Brightness Setup.
 *
 * <p>On the client, {@link #init()} registers the Cloth Config model (see
 * {@link BetterBrightnessConfig}) and the first-launch hook by delegating to
 * {@link BrightnessSetup#initClient()}, which schedules the brightness calibration screen
 * to open on the first title-screen render when no marker file is present.
 */
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

    /**
     * Registers the {@link BetterBrightnessConfig} AutoConfig model once. Idempotent — safe to
     * call from multiple loaders' client init.
     */
    private static void registerConfig() {
        if (configRegistered) return;
        configRegistered = true;
        AutoConfig.register(BetterBrightnessConfig.class, GsonConfigSerializer::new);
    }
}

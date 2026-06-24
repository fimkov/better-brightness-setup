package io.github.fimkov.betterbrightness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point for Better Brightness Setup.
 *
 * <p>This Task-1 skeleton only logs initialization. Feature code (first-launch detection,
 * the brightness-calibration screen, gamma writing) is added in later tasks.
 */
public final class BetterBrightness {

    public static final String MOD_ID = "betterbrightness";
    public static final Logger LOGGER = LoggerFactory.getLogger("BetterBrightness");

    private BetterBrightness() {
    }

    public static void init() {
        LOGGER.info("[{}] common init", MOD_ID);
        BrightnessSetup.initClient();
    }
}

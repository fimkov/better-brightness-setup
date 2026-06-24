package io.github.fimkov.betterbrightness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point for Better Brightness Setup.
 *
 * <p>On the client, {@link #init()} registers the first-launch hook by delegating to
 * {@link BrightnessSetup#initClient()}, which schedules the brightness calibration screen
 * to open on the first title-screen render when no marker file is present.
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

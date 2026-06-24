package io.github.fimkov.betterbrightness;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.platform.Platform;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Handles the first-launch trigger: registers a title-screen hook via Architectury's
 * {@code ClientGuiEvent.INIT_POST} and fires once per session (and once ever, persisted
 * via {@link Marker}).
 *
 * <p>Task 5 replaces {@link #onScreenOpened()} body's log line with a real screen open:
 * {@code Minecraft.getInstance().setScreen(new BrightnessSetupScreen())}
 */
public final class BrightnessSetup {

    private static boolean registered = false;
    private static boolean shownThisSession = false;

    private BrightnessSetup() {}

    /**
     * Registers the title-screen hook. Idempotent — safe to call from multiple loaders.
     */
    public static void initClient() {
        if (registered) return;
        registered = true;

        ClientGuiEvent.INIT_POST.register((screen, access) -> {
            if (screen instanceof TitleScreen && shouldOpen()) {
                onScreenOpened();
                // Task 5 replaces this line with:
                // Minecraft.getInstance().setScreen(new BrightnessSetupScreen());
                BetterBrightness.LOGGER.info(
                        "[{}] first launch detected — would open calibration screen",
                        BetterBrightness.MOD_ID);
            }
        });
    }

    /**
     * Returns true if the calibration screen should be shown: not yet shown this session
     * and the persistent marker is absent.
     */
    public static boolean shouldOpen() {
        return !shownThisSession && !Marker.isDone(Platform.getConfigFolder());
    }

    /**
     * Marks the screen as shown for this session. Task 5 also calls {@link Marker#markDone}
     * after the user completes (or dismisses) the calibration screen.
     */
    public static void onScreenOpened() {
        shownThisSession = true;
    }
}

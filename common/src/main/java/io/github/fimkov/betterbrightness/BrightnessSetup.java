package io.github.fimkov.betterbrightness;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.platform.Platform;
import io.github.fimkov.betterbrightness.client.BrightnessSetupScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Handles the first-launch trigger: registers a title-screen hook via Architectury's
 * {@code ClientGuiEvent.INIT_POST} and fires once per session (and once ever, persisted
 * via {@link Marker}).
 *
 * <p>On first launch it opens {@link BrightnessSetupScreen} (deferred a tick to avoid
 * setScreen-during-init re-entrancy); the screen writes the gamma and the persistent marker.
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
                onScreenOpened(); // sets shownThisSession, guarding against any reopen loop
                BetterBrightness.LOGGER.info(
                        "[{}] first launch detected — opening calibration screen",
                        BetterBrightness.MOD_ID);
                // Defer to the next tick to avoid setScreen-during-INIT_POST re-entrancy.
                final Screen parent = screen; // the TitleScreen
                Minecraft.getInstance().execute(() ->
                        Minecraft.getInstance().gui.setScreen(new BrightnessSetupScreen(parent)));
            }
        });
    }

    /**
     * Returns true if the calibration screen should be shown: not yet shown this session
     * and the persistent marker is absent.
     */
    public static boolean shouldOpen() {
        if (shownThisSession) return false;
        if (Minecraft.getInstance().options.onboardAccessibility) return false; // wait for vanilla onboarding
        return !Marker.isDone(Platform.getConfigFolder());
    }

    /**
     * Marks the screen as shown for this session. Task 5 also calls {@link Marker#markDone}
     * after the user completes (or dismisses) the calibration screen.
     */
    public static void onScreenOpened() {
        shownThisSession = true;
    }
}

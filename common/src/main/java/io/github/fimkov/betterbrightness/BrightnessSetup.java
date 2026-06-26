package io.github.fimkov.betterbrightness;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.platform.Platform;
import io.github.fimkov.betterbrightness.client.BrightnessSetupScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

public final class BrightnessSetup {
    private static boolean registered = false;
    private static boolean shownThisSession = false;

    private BrightnessSetup() {}

    public static void initClient() {
        if (registered) return;
        registered = true;

        ClientGuiEvent.INIT_POST.register((screen, access) -> {
            if (screen instanceof TitleScreen && shouldOpen()) {
                onScreenOpened();
                BetterBrightness.LOGGER.info(
                        "[{}] first launch detected — opening calibration screen",
                        BetterBrightness.MOD_ID);

                final Screen parent = screen;
                Minecraft.getInstance().execute(() ->
                        Minecraft.getInstance().setScreen(new BrightnessSetupScreen(parent)));
            }
        });
    }

    public static boolean shouldOpen() {
        if (shownThisSession) return false;
        if (Minecraft.getInstance().options.onboardAccessibility) return false;
        return !Marker.isDone(Platform.getConfigFolder());
    }

    public static void onScreenOpened() {
        shownThisSession = true;
    }
}

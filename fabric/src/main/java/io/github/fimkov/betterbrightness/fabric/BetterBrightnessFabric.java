package io.github.fimkov.betterbrightness.fabric;

import io.github.fimkov.betterbrightness.BetterBrightness;
import net.fabricmc.api.ClientModInitializer;

public final class BetterBrightnessFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BetterBrightness.init();
    }
}

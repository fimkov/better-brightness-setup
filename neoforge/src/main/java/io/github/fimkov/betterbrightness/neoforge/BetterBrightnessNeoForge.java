package io.github.fimkov.betterbrightness.neoforge;

import io.github.fimkov.betterbrightness.BetterBrightness;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = BetterBrightness.MOD_ID, dist = Dist.CLIENT)
public final class BetterBrightnessNeoForge {

    public BetterBrightnessNeoForge() {
        BetterBrightness.init();
    }
}

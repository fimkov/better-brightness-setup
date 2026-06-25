package io.github.fimkov.betterbrightness.neoforge;

import io.github.fimkov.betterbrightness.BetterBrightness;
import io.github.fimkov.betterbrightness.BetterBrightnessConfig;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = BetterBrightness.MOD_ID, dist = Dist.CLIENT)
public final class BetterBrightnessNeoForge {
    public BetterBrightnessNeoForge(ModContainer container) {
        BetterBrightness.init();

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (mc, parent) -> AutoConfigClient.getConfigScreen(BetterBrightnessConfig.class, parent).get());
    }
}

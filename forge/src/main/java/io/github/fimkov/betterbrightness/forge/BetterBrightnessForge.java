package io.github.fimkov.betterbrightness.forge;

import io.github.fimkov.betterbrightness.BetterBrightness;
import io.github.fimkov.betterbrightness.BetterBrightnessConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

@Mod(BetterBrightness.MOD_ID)
public final class BetterBrightnessForge {
    public BetterBrightnessForge() {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        BetterBrightness.init();

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> AutoConfig.getConfigScreen(BetterBrightnessConfig.class, parent).get()));
    }
}

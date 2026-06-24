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

        // Wire the native NeoForge mod-list "Config" button to the Cloth Config / AutoConfig screen.
        // Cloth Config NeoForge does NOT auto-register an IConfigScreenFactory for our config, so we
        // register it ourselves. The screen builder lives on AutoConfigClient in Cloth 26.2.155
        // (AutoConfig.getConfigScreen does not exist). This @Mod is Dist.CLIENT, so referencing the
        // client GUI types here is safe.
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (mc, parent) -> AutoConfigClient.getConfigScreen(BetterBrightnessConfig.class, parent).get());
    }
}

package io.github.fimkov.betterbrightness.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.fimkov.betterbrightness.BetterBrightnessConfig;
import me.shedaniel.autoconfig.AutoConfigClient;

public final class BetterBrightnessModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfigClient.getConfigScreen(BetterBrightnessConfig.class, parent).get();
    }
}

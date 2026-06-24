package io.github.fimkov.betterbrightness.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.fimkov.betterbrightness.BetterBrightnessConfig;
import me.shedaniel.autoconfig.AutoConfigClient;

/**
 * Mod Menu integration (Fabric/Quilt only). Wires the "Config" button on the mod list to the
 * Cloth Config / AutoConfig settings screen for {@link BetterBrightnessConfig}.
 *
 * <p>Mod Menu is an OPTIONAL dependency: it is a {@code modCompileOnly} dependency and is declared
 * in {@code fabric.mod.json} under {@code "suggests"}, never {@code "depends"}. This class is only
 * loaded by Mod Menu via the {@code "modmenu"} entrypoint, so the mod boots normally when Mod Menu
 * is absent.
 *
 * <p>The screen is built via {@link AutoConfigClient#getConfigScreen} — note that in Cloth Config
 * 26.2.155 the screen builder lives on {@code AutoConfigClient}, not on {@code AutoConfig}.
 */
public final class BetterBrightnessModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfigClient.getConfigScreen(BetterBrightnessConfig.class, parent).get();
    }
}

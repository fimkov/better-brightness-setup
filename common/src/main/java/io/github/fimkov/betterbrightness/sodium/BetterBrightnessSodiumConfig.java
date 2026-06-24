package io.github.fimkov.betterbrightness.sodium;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPointForge;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Sodium config-API integration: overlays the built-in Brightness (gamma) slider
 * to span 0–200% instead of the default 0–100%.
 *
 * <p>Registered as a Fabric entrypoint via {@code "sodium:config_api_user"} in
 * {@code fabric.mod.json}, and on NeoForge via the {@code @ConfigEntryPointForge}
 * annotation below.  This class is only instantiated when Sodium is present;
 * no code path in the rest of the mod touches these Sodium API types, so the
 * dependency is safely soft.</p>
 */
@ConfigEntryPointForge("betterbrightness")
public final class BetterBrightnessSodiumConfig implements ConfigEntryPoint {

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        var gammaId = Identifier.parse("sodium:general.gamma");

        builder.registerOwnModOptions()
                .registerOptionOverlay(
                        gammaId,
                        builder.createIntegerOption(gammaId)
                                .setRange(0, 200, 1)
                                .setValueFormatter(value -> Component.literal(value + "%"))
                                .setBinding(
                                        value -> Minecraft.getInstance().options.gamma().set(value * 0.01D),
                                        () -> (int) Math.round(Minecraft.getInstance().options.gamma().get() / 0.01D)
                                )
                                .setDefaultValue(100)
                );
    }
}

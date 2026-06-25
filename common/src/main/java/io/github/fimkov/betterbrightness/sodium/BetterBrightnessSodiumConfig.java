package io.github.fimkov.betterbrightness.sodium;

import io.github.fimkov.betterbrightness.BetterBrightnessConfig;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPointForge;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@ConfigEntryPointForge("betterbrightness")
public final class BetterBrightnessSodiumConfig implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        var gammaId = Identifier.parse("sodium:general.gamma");

        builder.registerOwnModOptions()
                .registerOptionOverlay(
                        gammaId,
                        builder.createIntegerOption(gammaId)
                                .setRange(0, BetterBrightnessConfig.maxPercent(), 1)
                                .setValueFormatter(value -> Component.literal(value + "%"))
                                .setBinding(
                                        value -> Minecraft.getInstance().options.gamma().set(value * 0.01D),
                                        () -> (int) Math.round(Minecraft.getInstance().options.gamma().get() / 0.01D)
                                )
                                .setDefaultValue(100)
                );
    }
}

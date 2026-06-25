package io.github.fimkov.betterbrightness;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = BetterBrightness.MOD_ID)
public final class BetterBrightnessConfig implements ConfigData {
    @ConfigEntry.BoundedDiscrete(min = 100, max = 500)
    @ConfigEntry.Gui.Tooltip
    public int maxBrightnessPercent = 100;

    @Override
    public void validatePostLoad() {
        maxBrightnessPercent = Math.max(100, Math.min(500, maxBrightnessPercent));
    }

    public static int maxPercent() {
        try {
            ConfigHolder<BetterBrightnessConfig> holder =
                    AutoConfig.getConfigHolder(BetterBrightnessConfig.class);
            if (holder == null) return 100;
            BetterBrightnessConfig config = holder.getConfig();
            return config == null ? 100 : config.maxBrightnessPercent;
        } catch (Throwable t) {
            return 100;
        }
    }
}

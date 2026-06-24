package io.github.fimkov.betterbrightness;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Cloth Config (AutoConfig) model for Better Brightness Setup.
 *
 * <p>Serialized to {@code config/betterbrightness.json} via {@code GsonConfigSerializer}.
 * Registered once at client init in {@link BetterBrightness#init()}.
 *
 * <p>{@link #maxBrightnessPercent} is the user-chosen ceiling for the widened brightness
 * slider, expressed as a percentage of vanilla full brightness. 100% is plain vanilla
 * (gamma 1.0); 500% is the widest allowed boost. A later task wires this into the gamma
 * mixin via {@link #maxPercent()}, which is null-safe for very early callers.
 */
@Config(name = BetterBrightness.MOD_ID)
public final class BetterBrightnessConfig implements ConfigData {

    /** Lower bound of {@link #maxBrightnessPercent} and the default (plain vanilla). */
    public static final int MIN_PERCENT = 100;

    /** Upper bound of {@link #maxBrightnessPercent} (widest allowed boost). */
    public static final int MAX_PERCENT = 500;

    /**
     * Maximum brightness as a percentage of vanilla full brightness, in {@code [100, 500]}.
     * Default 100 (vanilla). Rendered in-game as a discrete slider.
     */
    @ConfigEntry.BoundedDiscrete(min = MIN_PERCENT, max = MAX_PERCENT)
    @ConfigEntry.Gui.Tooltip
    public int maxBrightnessPercent = MIN_PERCENT;

    @Override
    public void validatePostLoad() {
        maxBrightnessPercent = Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, maxBrightnessPercent));
    }

    /**
     * Returns the live {@link #maxBrightnessPercent} from the registered AutoConfig holder,
     * or {@link #MIN_PERCENT} (100) if the holder is not yet registered/available.
     *
     * <p>Null-safe and exception-safe: this is called very early by the gamma mixin, before
     * (or independently of) config registration, so it never throws.
     */
    public static int maxPercent() {
        try {
            ConfigHolder<BetterBrightnessConfig> holder =
                    AutoConfig.getConfigHolder(BetterBrightnessConfig.class);
            if (holder == null) return MIN_PERCENT;
            BetterBrightnessConfig config = holder.getConfig();
            return config == null ? MIN_PERCENT : config.maxBrightnessPercent;
        } catch (Throwable t) {
            return MIN_PERCENT;
        }
    }
}

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
 * <p>{@link #maxBrightnessPercent} is the user-chosen ceiling for the widened brightness slider, as a
 * percentage of vanilla full brightness. 100% is plain vanilla (gamma 1.0); 500% is the widest allowed
 * boost. It is wired into the gamma mixin and the sliders via {@link #maxPercent()}, which is null-safe for
 * very early callers.
 *
 * <p><b>No {@code static} fields on this class.</b> Cloth's AutoConfig (26.2.155) reflects over the config
 * class's fields to build the settings GUI <em>and writes them back on Save</em>; a
 * {@code public static final} constant here gets (mis)treated as an option — it renders an unlocalized
 * extra row (e.g. {@code text.autoconfig.betterbrightness.option.MAX_PERCENT}) and throws
 * {@code IllegalAccessException} ("cannot set a final field") on Save, which broke the Save button when a
 * {@code MIN_PERCENT}/{@code MAX_PERCENT} pair lived here. The 100/500 bounds are therefore inlined as
 * literals rather than named constants on this class; the {@code 5.0} gamma ceiling derived from the 500%
 * max lives in {@link GammaRange}.
 */
@Config(name = BetterBrightness.MOD_ID)
public final class BetterBrightnessConfig implements ConfigData {

    /**
     * Maximum brightness as a percentage of vanilla full brightness, in {@code [100, 500]}.
     * Default 100 (vanilla). Rendered in-game as a discrete slider.
     */
    @ConfigEntry.BoundedDiscrete(min = 100, max = 500)
    @ConfigEntry.Gui.Tooltip
    public int maxBrightnessPercent = 100;

    @Override
    public void validatePostLoad() {
        maxBrightnessPercent = Math.max(100, Math.min(500, maxBrightnessPercent));
    }

    /**
     * Returns the live {@link #maxBrightnessPercent} from the registered AutoConfig holder, or {@code 100}
     * (vanilla) if the holder is not yet registered/available.
     *
     * <p>Null-safe and exception-safe: this is called very early by the gamma mixin, before
     * (or independently of) config registration, so it never throws.
     */
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

package io.github.fimkov.betterbrightness;

import com.mojang.serialization.Codec;
import net.minecraft.client.OptionInstance;

import java.util.Optional;

/**
 * A {@code [0, MAX_PERCENT/100.0]} value space for the vanilla gamma option, installed by
 * {@code OptionsGammaMixin} in place of {@code OptionInstance.UnitDouble.INSTANCE}.
 *
 * <p>This lives in the normal {@code io.github.fimkov.betterbrightness} package, NOT the
 * {@code .mixin} package: Mixin treats every class in a mixin-config's declared package as a mixin and
 * forbids direct references to it (referencing such a class throws {@code IllegalClassLoadError} at
 * runtime). The {@code @ModifyArg} handler references this class directly, so it must sit outside the
 * mixin package.
 *
 * <p>Unlike {@code UnitDouble.INSTANCE.xmap(v -> v * 2, v -> v / 2)} (whose xmapped codec persists the
 * underlying {@code [0, 1]} value, i.e. {@code gamma / 2}), this ValueSet stores the <em>exposed</em>
 * value directly: {@link #codec()} is {@code Codec.doubleRange(0.0, CEILING)}, so the number written to
 * {@code options.txt} equals the real gamma. That makes it fully backward-compatible — an existing
 * {@code gamma:0.5} still reads back as exposed {@code 0.5} (no silent doubling), and removing the mod
 * leaves the disk value meaning the same gamma it always did — while still allowing {@code > 1.0} to
 * validate and persist.
 *
 * <p>The ceiling is the CONSTANT {@code BetterBrightnessConfig.MAX_PERCENT / 100.0} (= 5.0), not the
 * live {@code maxPercent()} config value. This intentional choice means changing the config never needs a
 * restart: {@code gamma().set(v)} always accepts up to 5.0, regardless of the user's current max setting.
 * The sliders (setup screen and Sodium) read the live {@code maxPercent()} separately and only offer
 * values up to the configured ceiling.
 *
 * <p>Mirrors {@code OptionInstance.UnitDouble} (the only difference is the {@code [0, CEILING]} bound
 * and the {@code /CEILING}, {@code *CEILING} slider mapping so the slider knob still travels {@code 0..1}).
 * The {@code createButton}, {@code next}, {@code previous} and {@code applyValueImmediately} methods are
 * left to {@code SliderableValueSet}'s defaults, exactly as {@code UnitDouble} does.
 */
public enum GammaRange implements OptionInstance.SliderableValueSet<Double> {
    INSTANCE;

    /**
     * Fixed ceiling: {@code MAX_PERCENT / 100.0} = 5.0.
     * This is a constant — it does NOT read the live config — so {@code gamma().set(v)} accepts any value
     * up to 5.0 without a restart, even if the user has configured a lower max.
     */
    private static final double CEILING = BetterBrightnessConfig.MAX_PERCENT / 100.0;

    /** Accept (do not clamp) anything in {@code [0, CEILING]}; reject outside, like {@code UnitDouble}. */
    @Override
    public Optional<Double> validateValue(Double value) {
        return value >= 0.0 && value <= CEILING ? Optional.of(value) : Optional.empty();
    }

    /** Slider knob is {@code 0..1}; the exposed value is {@code 0..CEILING}. */
    @Override
    public double toSliderValue(Double value) {
        return value / CEILING;
    }

    @Override
    public Double fromSliderValue(double slider) {
        return slider * CEILING;
    }

    /** Persist the EXPOSED value, so disk gamma == real gamma (no scaling). */
    @Override
    public Codec<Double> codec() {
        return Codec.doubleRange(0.0, CEILING);
    }
}

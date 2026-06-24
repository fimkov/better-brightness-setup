package io.github.fimkov.betterbrightness.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.client.OptionInstance;

import java.util.Optional;

/**
 * A {@code [0, 2]} value space for the vanilla gamma option, installed by {@link OptionsGammaMixin} in
 * place of {@code OptionInstance.UnitDouble.INSTANCE}.
 *
 * <p>Unlike {@code UnitDouble.INSTANCE.xmap(v -> v * 2, v -> v / 2)} (whose xmapped codec persists the
 * underlying {@code [0, 1]} value, i.e. {@code gamma / 2}), this ValueSet stores the <em>exposed</em>
 * value directly: {@link #codec()} is {@code Codec.doubleRange(0.0, 2.0)}, so the number written to
 * {@code options.txt} equals the real gamma. That makes it fully backward-compatible — an existing
 * {@code gamma:0.5} still reads back as exposed {@code 0.5} (no silent doubling), and removing the mod
 * leaves the disk value meaning the same gamma it always did — while still allowing {@code > 1.0} to
 * validate and persist.
 *
 * <p>Mirrors {@code OptionInstance.UnitDouble} (the only difference is the {@code [0, 2]} bound and the
 * {@code /2}, {@code *2} slider mapping so the slider knob still travels {@code 0..1}). The
 * {@code createButton}, {@code next}, {@code previous} and {@code applyValueImmediately} methods are left
 * to {@code SliderableValueSet}'s defaults, exactly as {@code UnitDouble} does.
 */
public enum GammaRange implements OptionInstance.SliderableValueSet<Double> {
    INSTANCE;

    /** Accept (do not clamp) anything in {@code [0, 2]}; reject outside, like {@code UnitDouble}. */
    @Override
    public Optional<Double> validateValue(Double value) {
        return value >= 0.0 && value <= 2.0 ? Optional.of(value) : Optional.empty();
    }

    /** Slider knob is {@code 0..1}; the exposed value is {@code 0..2}. */
    @Override
    public double toSliderValue(Double value) {
        return value / 2.0;
    }

    @Override
    public Double fromSliderValue(double slider) {
        return slider * 2.0;
    }

    /** Persist the EXPOSED value, so disk gamma == real gamma (no doubling). */
    @Override
    public Codec<Double> codec() {
        return Codec.doubleRange(0.0, 2.0);
    }
}

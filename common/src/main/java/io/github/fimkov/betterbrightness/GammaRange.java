package io.github.fimkov.betterbrightness;

import com.mojang.serialization.Codec;
import net.minecraft.client.OptionInstance;

import java.util.Optional;

public enum GammaRange implements OptionInstance.SliderableValueSet<Double> {
    INSTANCE;

    private static final double CEILING = 5.0;

    @Override
    public Optional<Double> validateValue(Double value) {
        return value >= 0.0 && value <= CEILING ? Optional.of(value) : Optional.empty();
    }

    @Override
    public double toSliderValue(Double value) {
        return value / CEILING;
    }

    @Override
    public Double fromSliderValue(double slider) {
        return slider * CEILING;
    }

    @Override
    public Codec<Double> codec() {
        return Codec.doubleRange(0.0, CEILING);
    }
}

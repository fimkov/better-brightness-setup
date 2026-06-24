package io.github.fimkov.betterbrightness;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure-POJO tests for the Cloth Config model — no Minecraft/Cloth runtime needed. */
class BetterBrightnessConfigTest {

    @Test void defaultsToVanilla() {
        assertEquals(100, new BetterBrightnessConfig().maxBrightnessPercent);
    }

    @Test void validatePostLoadClampsBelowMin() {
        BetterBrightnessConfig config = new BetterBrightnessConfig();
        config.maxBrightnessPercent = 50;
        config.validatePostLoad();
        assertEquals(100, config.maxBrightnessPercent);
    }

    @Test void validatePostLoadClampsAboveMax() {
        BetterBrightnessConfig config = new BetterBrightnessConfig();
        config.maxBrightnessPercent = 999;
        config.validatePostLoad();
        assertEquals(500, config.maxBrightnessPercent);
    }

    @Test void validatePostLoadLeavesInRangeUntouched() {
        BetterBrightnessConfig config = new BetterBrightnessConfig();
        config.maxBrightnessPercent = 250;
        config.validatePostLoad();
        assertEquals(250, config.maxBrightnessPercent);
    }
}

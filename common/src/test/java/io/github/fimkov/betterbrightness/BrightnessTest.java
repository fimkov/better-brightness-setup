package io.github.fimkov.betterbrightness;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrightnessTest {
    @Test void mapsSliderEndpointsAndMidpoint() {
        assertEquals(0.0, Brightness.sliderToGamma(0.0), 1e-9);
        assertEquals(2.0, Brightness.sliderToGamma(1.0), 1e-9);
        assertEquals(1.0, Brightness.sliderToGamma(0.5), 1e-9);
    }
    @Test void clampsSliderOutOfRange() {
        assertEquals(0.0, Brightness.sliderToGamma(-3.0), 1e-9);
        assertEquals(2.0, Brightness.sliderToGamma(7.0), 1e-9);
    }
    @Test void displayedBrightnessFollowsMcGammaCurve() {
        // A pure-black light level (0) stays black, a full light level (1) stays full — for any gamma.
        assertEquals(0.0, Brightness.displayedBrightness(0.0, 0.0), 1e-9);
        assertEquals(0.0, Brightness.displayedBrightness(2.0, 0.0), 1e-9);
        assertEquals(1.0, Brightness.displayedBrightness(0.0, 1.0), 1e-9);
        assertEquals(1.0, Brightness.displayedBrightness(2.0, 1.0), 1e-9);
        // gamma 0 = raw light ramp b = level/(4-3*level); level 0.5 -> 0.2.
        assertEquals(0.2, Brightness.displayedBrightness(0.0, 0.5), 1e-9);
        // gamma 1 = full mix to notGamma(b) = 1-(1-b)^4; level 0.5 -> 0.2 + (1-0.8^4 - 0.2) = 0.5904.
        assertEquals(0.5904, Brightness.displayedBrightness(1.0, 0.5), 1e-9);
        // Raising gamma always brightens a dim spot.
        assertTrue(Brightness.displayedBrightness(1.0, 0.3) > Brightness.displayedBrightness(0.0, 0.3));
        // Extrapolating past gamma 1.0 (our widened range) clamps to 1.0.
        assertEquals(1.0, Brightness.displayedBrightness(2.0, 0.8), 1e-9);
    }
    @Test void toPercentMapsGammaRange() {
        assertEquals(0,   Brightness.toPercent(0.0));
        assertEquals(100, Brightness.toPercent(1.0));
        assertEquals(200, Brightness.toPercent(2.0));
        assertEquals(130, Brightness.toPercent(1.3));
    }
    @Test void lerpEndpointsAndMidpoint() {
        assertEquals(0.0, Brightness.lerp(0.0, 1.0, 0.0), 1e-9);
        assertEquals(1.0, Brightness.lerp(0.0, 1.0, 1.0), 1e-9);
        assertEquals(0.5, Brightness.lerp(0.0, 1.0, 0.5), 1e-9);
        assertEquals(1.0, Brightness.lerp(0.0, 1.0, 5.0), 1e-9); // t clamped to 1
    }
}

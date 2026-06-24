package io.github.fimkov.betterbrightness;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    @Test void panelVisibilityRamps() {
        assertEquals(0.0, Brightness.panelVisibility(0.10, 0.5), 1e-9); // below threshold -> hidden
        assertEquals(1.0, Brightness.panelVisibility(1.50, 0.5), 1e-9); // well above -> fully visible
        assertEquals(0.5, Brightness.panelVisibility(0.75, 0.5), 1e-9); // mid-ramp
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

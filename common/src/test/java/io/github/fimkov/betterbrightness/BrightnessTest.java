package io.github.fimkov.betterbrightness;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrightnessTest {
    @Test void mapsSliderEndpointsAndMidpoint() {
        assertEquals(0.0, Brightness.sliderToGamma(0.0), 1e-9);
        assertEquals(1.0, Brightness.sliderToGamma(1.0), 1e-9);
        assertEquals(0.5, Brightness.sliderToGamma(0.5), 1e-9);
    }
    @Test void clampsSliderOutOfRange() {
        assertEquals(0.0, Brightness.sliderToGamma(-3.0), 1e-9);
        assertEquals(1.0, Brightness.sliderToGamma(7.0), 1e-9);
    }
    @Test void displayedBrightnessFollowsMcGammaCurve() {
        assertEquals(0.0, Brightness.displayedBrightness(0.0, 0.0), 1e-9);
        assertEquals(0.0, Brightness.displayedBrightness(2.0, 0.0), 1e-9);
        assertEquals(1.0, Brightness.displayedBrightness(0.0, 1.0), 1e-9);
        assertEquals(1.0, Brightness.displayedBrightness(2.0, 1.0), 1e-9);

        assertEquals(0.2, Brightness.displayedBrightness(0.0, 0.5), 1e-9);

        assertEquals(0.5904, Brightness.displayedBrightness(1.0, 0.5), 1e-9);

        assertTrue(Brightness.displayedBrightness(1.0, 0.3) > Brightness.displayedBrightness(0.0, 0.3));

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
        assertEquals(1.0, Brightness.lerp(0.0, 1.0, 5.0), 1e-9);
    }

    @Test void sliderToGammaWithMaxPercentZeroT() {
        assertEquals(0.0, Brightness.sliderToGamma(0.0, 100), 1e-9);
        assertEquals(0.0, Brightness.sliderToGamma(0.0, 200), 1e-9);
        assertEquals(0.0, Brightness.sliderToGamma(0.0, 500), 1e-9);
    }
    @Test void sliderToGammaWithMaxPercentFullT() {
        assertEquals(1.0, Brightness.sliderToGamma(1.0, 100), 1e-9);
        assertEquals(2.0, Brightness.sliderToGamma(1.0, 200), 1e-9);
        assertEquals(5.0, Brightness.sliderToGamma(1.0, 500), 1e-9);
    }
    @Test void sliderToGammaWithMaxPercentMidT() {
        assertEquals(1.0, Brightness.sliderToGamma(0.5, 200), 1e-9);
    }
    @Test void sliderToGammaWithMaxPercentClampsT() {
        assertEquals(1.0, Brightness.sliderToGamma(2.0, 100), 1e-9);
    }

    @Test void gammaToSliderInvertsSliderToGamma() {
        assertEquals(1.0, Brightness.gammaToSlider(1.0, 100), 1e-9);
        assertEquals(1.0, Brightness.gammaToSlider(2.0, 200), 1e-9);
        assertEquals(1.0, Brightness.gammaToSlider(5.0, 500), 1e-9);

        assertEquals(0.2, Brightness.gammaToSlider(1.0, 500), 1e-9);
        assertEquals(0.5, Brightness.gammaToSlider(1.0, 200), 1e-9);
        assertEquals(0.0, Brightness.gammaToSlider(0.0, 100), 1e-9);

        assertEquals(1.0, Brightness.gammaToSlider(9.0, 100), 1e-9);

        assertEquals(0.3, Brightness.gammaToSlider(Brightness.sliderToGamma(0.3, 500), 500), 1e-9);
    }
}

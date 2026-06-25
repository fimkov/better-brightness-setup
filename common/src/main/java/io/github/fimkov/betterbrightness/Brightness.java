package io.github.fimkov.betterbrightness;

public final class Brightness {
    private Brightness() {}

    /**
     * slider t in [0,1] -> gamma scaled by {@code maxPercent/100.0}, clamped.
     * At maxPercent=100 (vanilla default) this returns t (gamma 0..1); at 200 it doubles to 0..2,
     * at 500 it quintuples to 0..5. t is clamped to [0,1] before scaling.
     */
    public static double sliderToGamma(double t, int maxPercent) {
        return clamp01(t) * (maxPercent / 100.0);
    }

    /** slider t in [0,1] -> gamma using the live configured max brightness, clamped. */
    public static double sliderToGamma(double t) {
        return sliderToGamma(t, BetterBrightnessConfig.maxPercent());
    }

    /**
     * Inverse of {@link #sliderToGamma(double, int)}: a gamma value -> the slider fraction [0,1] that
     * produces it for the given max. Used to seed the setup screen from the CURRENT gamma so reopening it
     * reflects the saved brightness instead of always snapping to mid-slider. Clamped to [0,1].
     */
    public static double gammaToSlider(double gamma, int maxPercent) {
        if (maxPercent <= 0) return 0.0;
        return clamp01(gamma * 100.0 / maxPercent);
    }

    /**
     * Faithful MC 26.2 lightmap brightness (grayscale, 0..1) for a block sitting at block-light
     * {@code level} (0..1) in a dark spot (sky light 0, no night-vision/darkness effect), under the
     * brightness/gamma {@code gamma} (0..5). Mirrors {@code lightmap.fsh} exactly:
     * {@code mix(b, notGamma(b), BrightnessFactor)}, where {@code BrightnessFactor} is the gamma slider
     * (verified: {@code LightmapRenderState.brightness = max(options.gamma - darkness, 0)}),
     * {@code b = level/(4-3*level)} is {@code get_brightness}, and the grayscale {@code notGamma(b)} is
     * {@code 1-(1-b)^4}. Tinting a calibration tile's RGB by this value makes it brighten as the real block
     * would when the slider moves — a true preview, not a stylized fade.
     */
    public static double displayedBrightness(double gamma, double level) {
        double b = lightRamp(clamp01(level));
        double oneMinus = 1.0 - b;
        double brightened = 1.0 - oneMinus * oneMinus * oneMinus * oneMinus; // notGamma(b), grayscale
        return clamp01(b + gamma * (brightened - b));                        // mix(b, notGamma(b), gamma)
    }

    /** MC {@code get_brightness}: a single light level (0..1) -> linear brightness. */
    private static double lightRamp(double level) {
        return level / (4.0 - 3.0 * level);
    }

    /** gamma in [0,2] -> integer percent in [0,200]. */
    public static int toPercent(double gamma) {
        return (int) Math.round(gamma * 100.0);
    }

    /** Linear interpolate from->to by t, with t clamped to [0,1]. */
    public static double lerp(double from, double to, double t) {
        return from + (to - from) * clamp01(t);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}

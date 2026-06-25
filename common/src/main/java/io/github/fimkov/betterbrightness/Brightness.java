package io.github.fimkov.betterbrightness;

public final class Brightness {
    private Brightness() {}

    public static double sliderToGamma(double t, int maxPercent) {
        return clamp01(t) * (maxPercent / 100.0);
    }

    public static double sliderToGamma(double t) {
        return sliderToGamma(t, BetterBrightnessConfig.maxPercent());
    }

    public static double gammaToSlider(double gamma, int maxPercent) {
        if (maxPercent <= 0) return 0.0;
        return clamp01(gamma * 100.0 / maxPercent);
    }

    public static double displayedBrightness(double gamma, double level) {
        double b = lightRamp(clamp01(level));
        double oneMinus = 1.0 - b;
        double brightened = 1.0 - oneMinus * oneMinus * oneMinus * oneMinus;
        return clamp01(b + gamma * (brightened - b));
    }

    private static double lightRamp(double level) {
        return level / (4.0 - 3.0 * level);
    }

    public static int toPercent(double gamma) {
        return (int) Math.round(gamma * 100.0);
    }

    public static double lerp(double from, double to, double t) {
        return from + (to - from) * clamp01(t);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}

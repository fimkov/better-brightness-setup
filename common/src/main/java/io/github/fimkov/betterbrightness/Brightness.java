package io.github.fimkov.betterbrightness;

public final class Brightness {
    private Brightness() {}

    /** slider t in [0,1] -> gamma in [0.0, 2.0] (vanilla min .. 2x vanilla max), clamped. */
    public static double sliderToGamma(double t) {
        return clamp01(t) * 2.0;
    }

    /** 0 = content invisible, 1 = fully visible, ramping over a 0.5-gamma window above threshold. */
    public static double panelVisibility(double gamma, double threshold) {
        return clamp01((gamma - threshold) / 0.5);
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

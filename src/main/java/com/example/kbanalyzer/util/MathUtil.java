package com.example.kbanalyzer.util;

public final class MathUtil {

    private MathUtil() {}

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        return (double) Math.round(value * factor) / factor;
    }

    public static String fmt(double d) {
        return String.format("%.6f", d);
    }

    public static String fmt4(double d) {
        return String.format("%.4f", d);
    }

    public static String fmt2(double d) {
        return String.format("%.2f", d);
    }
}

package com.yash.upsc400v2.engine;

/** Observed pace figures. */
public final class Pace {
    private Pace() {}

    public static double perHour(double amount, long ms) {
        return ms <= 0 ? 0 : amount / ((double) ms / 3600000.0);
    }

    /** Observed minutes per planned minute for lectures; plan assumes duration / 1.5 x 1.25. */
    public static double lectureFactor(double observedMin, double durationMin) {
        double planned = durationMin / 1.5 * 1.25;
        return planned <= 0 ? 0 : observedMin / planned;
    }
}

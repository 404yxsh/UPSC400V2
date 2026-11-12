package com.yash.upsc400v2.engine;

/** Averages, moving average and projected finish. Days are epoch days. */
public final class Stats {
    private Stats() {}

    /** Average per day over every day from the first log day to today, zero days included. */
    public static double avgPerDay(long totalMs, int firstDay, int today) {
        if (firstDay > today) return 0;
        return (double) totalMs / (double) (today - firstDay + 1);
    }

    public static double[] movingAvg(double[] v, int window) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            int from = Math.max(0, i - window + 1);
            double sum = 0;
            for (int k = from; k <= i; k++) sum += v[k];
            out[i] = sum / (i - from + 1);
        }
        return out;
    }

    /** Epoch day on which the remaining work is done at the current average, or -1 if there is no average. */
    public static int projectedFinish(long remainingMs, double avgMsPerDay, int today) {
        if (remainingMs <= 0) return today;
        if (avgMsPerDay <= 0) return -1;
        return today + (int) Math.ceil((double) remainingMs / avgMsPerDay);
    }

    /** Epoch day of the Monday of the week containing d (epoch day 0 was a Thursday). */
    public static int weekStart(int d) {
        int dow = Math.floorMod(d + 3, 7); // 0 = Monday
        return d - dow;
    }
}

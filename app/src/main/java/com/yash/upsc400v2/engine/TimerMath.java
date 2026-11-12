package com.yash.upsc400v2.engine;

/** Session timer arithmetic. All values in milliseconds. */
public final class TimerMath {
    private TimerMath() {}

    public static long elapsed(long loggedMs, boolean running, long startedAt, long now) {
        long e = loggedMs;
        if (running && now > startedAt) e += now - startedAt;
        return e;
    }

    public static long remaining(long plannedMin, long elapsedMs) { return plannedMin * 60000L - elapsedMs; }

    public static boolean overtime(long plannedMin, long elapsedMs) { return elapsedMs > plannedMin * 60000L; }

    /** Time at which the planned length is reached when running now, or -1 if already reached. */
    public static long buzzAt(long plannedMin, long loggedMs, long now) {
        long rem = remaining(plannedMin, loggedMs);
        return rem <= 0 ? -1 : now + rem;
    }

    public static String format(long ms) {
        long s = Math.abs(ms) / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        String t = h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
        return ms < 0 ? "-" + t : t;
    }
}

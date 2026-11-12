package com.yash.upsc400v2.engine;

/** Day rollover: late-night study counts for the previous date until the rollover time. */
public final class Rollover {
    public static final int DEFAULT_MIN = 4 * 60;
    private static final long DAY = 86400000L;

    private Rollover() {}

    /** Epoch day of the local date for a UTC timestamp, after subtracting the rollover. */
    public static int dayKey(long utcMillis, long zoneOffsetMillis, int rolloverMin) {
        long local = utcMillis + zoneOffsetMillis - rolloverMin * 60000L;
        return (int) Math.floorDiv(local, DAY);
    }
}

package com.yash.upsc400v2.engine;

import java.util.Map;

/** V (reward) and streak rules. */
public final class Rewards {
    public static final long HOUR_MS = 3600000L;
    public static final long STREAK_MS = 4 * HOUR_MS;
    public static final int[] BADGES = {10, 50, 100, 250};
    public static final int TOKEN_GAP_DAYS = 7;

    private Rewards() {}

    public static int vTotal(long totalMs) { return (int) (totalMs / HOUR_MS); }

    public static long vCarryMs(long totalMs) { return totalMs % HOUR_MS; }

    public static int badgesEarned(int v) {
        int n = 0;
        for (int b : BADGES) if (v >= b) n++;
        return n;
    }

    /** Next badge threshold above v, or -1. */
    public static int nextBadge(int v) {
        for (int b : BADGES) if (v < b) return b;
        return -1;
    }

    /** result: {current streak, best streak, rest token available (1/0)}. */
    public static int[] streak(Map<Integer, Long> dayMs, int today) {
        if (dayMs.isEmpty()) return new int[] {0, 0, 1};
        int first = Integer.MAX_VALUE;
        for (Integer k : dayMs.keySet()) if (k < first) first = k;
        int cur = 0, best = 0;
        int lastToken = Integer.MIN_VALUE / 2;
        for (int d = first; d <= today; d++) {
            Long ms = dayMs.get(d);
            boolean met = ms != null && ms >= STREAK_MS;
            if (met) {
                cur++;
                if (cur > best) best = cur;
            } else if (d == today) {
                // today is still open: neither extend nor break
            } else if (cur > 0 && d - lastToken >= TOKEN_GAP_DAYS) {
                lastToken = d; // rest token keeps the streak alive for one missed day
            } else {
                cur = 0;
            }
        }
        int token = (today - lastToken >= TOKEN_GAP_DAYS) ? 1 : 0;
        return new int[] {cur, best, token};
    }
}

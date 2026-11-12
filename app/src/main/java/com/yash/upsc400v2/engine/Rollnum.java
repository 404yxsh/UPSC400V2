package com.yash.upsc400v2.engine;

import java.util.List;

/** Queue ordering helper: sparse, increasing sequence numbers. */
public final class Rollnum {
    private Rollnum() {}

    public static void renumber(List<Session> list) {
        long n = 1000;
        for (Session s : list) { s.seq = n; n += 1000; }
    }
}

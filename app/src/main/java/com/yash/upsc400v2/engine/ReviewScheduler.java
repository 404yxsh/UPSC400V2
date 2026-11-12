package com.yash.upsc400v2.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Spaced reviews from real completion dates (epoch days). Overdue reviews lapse. */
public final class ReviewScheduler {
    public static final int[] INTERVALS = {1, 7, 30};
    public static final int WINDOW = 2;       // a review stays due for this many extra days
    public static final int CAP = 4;
    public static final int MIN_PER_TOPIC = 8;

    public static final class Done {
        public String itemId, title, tier;
        public int doneDay;
        public boolean d1, d7, d30;
        public Done(String itemId, String title, String tier, int doneDay, boolean d1, boolean d7, boolean d30) {
            this.itemId = itemId; this.title = title; this.tier = tier; this.doneDay = doneDay;
            this.d1 = d1; this.d7 = d7; this.d30 = d30;
        }
    }

    public static final class Due {
        public String itemId, title, tier;
        public int interval, dueDay;
    }

    private ReviewScheduler() {}

    static int rank(String tier) {
        if ("core".equals(tier)) return 0;
        if ("standard".equals(tier)) return 1;
        return 2;
    }

    public static List<Due> compute(List<Done> done, int today, int cap) {
        List<Due> out = new ArrayList<Due>();
        for (Done d : done) {
            boolean[] f = {d.d1, d.d7, d.d30};
            for (int k = 0; k < INTERVALS.length; k++) {
                int due = d.doneDay + INTERVALS[k];
                if (!f[k] && today >= due && today <= due + WINDOW) {
                    Due x = new Due();
                    x.itemId = d.itemId; x.title = d.title; x.tier = d.tier;
                    x.interval = INTERVALS[k]; x.dueDay = due;
                    out.add(x);
                }
            }
        }
        Collections.sort(out, new Comparator<Due>() {
            public int compare(Due a, Due b) {
                int r = rank(a.tier) - rank(b.tier);
                if (r != 0) return r;
                if (a.dueDay != b.dueDay) return a.dueDay < b.dueDay ? -1 : 1;
                return a.itemId.compareTo(b.itemId);
            }
        });
        if (out.size() > cap) return new ArrayList<Due>(out.subList(0, cap));
        return out;
    }

    /** Minutes left for question practice after the review topics. */
    public static int pyqMinutes(int topics, int sessionMin) {
        return Math.max(10, sessionMin - topics * MIN_PER_TOPIC);
    }
}

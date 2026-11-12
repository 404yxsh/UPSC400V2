package com.yash.upsc400v2.engine;

import java.util.List;

/** Skip rules. The list holds only open sessions (todo) in queue order. */
public final class SkipSwap {
    public static final int NONE = -1;
    public static final int DROP = -2;

    private SkipSwap() {}

    public static boolean skippable(Session s) {
        return !"consolidation".equals(s.kind) && !"carry".equals(s.kind);
    }

    /** Index of the session to swap with, DROP for revision sessions, or NONE. */
    public static int partner(List<Session> open, int idx) {
        Session s = open.get(idx);
        if (!skippable(s)) return NONE;
        if (s.stream == 'R') return DROP;
        for (int k = idx + 1; k < open.size(); k++) if (compatible(s, open.get(k))) return k;
        for (int k = idx - 1; k >= 0; k--) if (compatible(s, open.get(k))) return k;
        return NONE;
    }

    private static boolean compatible(Session a, Session b) {
        if (b.stream != a.stream) return false;
        if (!"todo".equals(b.status)) return false;
        if ("consolidation".equals(b.kind) || "carry".equals(b.kind)) return false;
        if (a.stream == 'G' && "plan".equals(a.kind)) {
            return "plan".equals(b.kind) && a.block.equals(b.block);
        }
        if (a.stream == 'G') return !"plan".equals(b.kind);
        return true; // Mathematics: next session of the same stream
    }

    /** Swaps the two positions and renumbers. Returns true if the order changed. */
    public static boolean apply(List<Session> open, int idx) {
        int p = partner(open, idx);
        if (p < 0) return false;
        Session s = open.get(idx);
        s.skipCount++;
        open.set(idx, open.get(p));
        open.set(p, s);
        Rollnum.renumber(open);
        return true;
    }
}

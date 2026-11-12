package com.yash.upsc400v2.engine;

import java.util.ArrayList;
import java.util.List;

/** Unfinished pieces become a short session at the front of the same stream. */
public final class CarryOver {
    private CarryOver() {}

    /** Returns the carry session, or null when every piece was ticked or the session has no pieces. */
    public static Session make(Session from) {
        if ("revision".equals(from.kind)) return null;
        List<Piece> rest = new ArrayList<Piece>();
        int total = 0;
        for (Piece p : from.pieces) {
            if (!p.ticked) { rest.add(p.copyUnticked()); total += p.minutes; }
        }
        if (rest.isEmpty()) return null;
        Session c = new Session();
        c.stream = from.stream;
        c.kind = "carry";
        c.planIndex = from.planIndex;
        c.subject = from.subject;
        c.block = from.block;
        c.plannedMin = Math.max(15, total);
        c.pieces = rest;
        c.summary = "Unfinished: " + QueueBuilder.summarize(rest);
        return c;
    }

    /** Position of the first open session of the stream, or the end of the list. */
    public static int insertIndex(List<Session> open, char stream) {
        for (int k = 0; k < open.size(); k++) {
            Session s = open.get(k);
            if (s.stream == stream && "todo".equals(s.status)) return k;
        }
        return open.size();
    }

    public static void insert(List<Session> open, Session carry) {
        open.add(insertIndex(open, carry.stream), carry);
        Rollnum.renumber(open);
    }
}

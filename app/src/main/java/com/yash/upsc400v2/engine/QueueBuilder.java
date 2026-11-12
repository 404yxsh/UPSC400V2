package com.yash.upsc400v2.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Builds the ordered session queue from the plan. */
public final class QueueBuilder {
    public static final int REVISION_MIN = 45;
    public static final int CONSOLIDATION_MIN = 135;
    public static final int PRACTICE_MIN = 150;

    private QueueBuilder() {}

    public static List<Session> build(Plan plan, int composedCycles) {
        List<Session> g = new ArrayList<Session>();
        List<Session> m = new ArrayList<Session>();
        List<Plan.Slot> gsSlots = new ArrayList<Plan.Slot>();
        int planIndex = 0;
        for (Plan.Slot sl : plan.slots) {
            if ("Mathematics".equals(sl.slot)) {
                m.add(fromSlot(sl, 'M', planIndex++));
            } else {
                gsSlots.add(sl);
            }
        }
        LinkedHashSet<String> blocks = new LinkedHashSet<String>();
        for (int k = 0; k < gsSlots.size(); k++) {
            Plan.Slot sl = gsSlots.get(k);
            blocks.add(sl.subject);
            g.add(fromSlot(sl, 'G', k));
            boolean last = k + 1 >= gsSlots.size() || !gsSlots.get(k + 1).subject.equals(sl.subject);
            if (last) g.add(consolidation(sl.subject));
        }
        List<String> blockList = new ArrayList<String>(blocks);

        List<Session> out = new ArrayList<Session>();
        int gi = 0, mi = 0;
        while (gi < g.size()) {
            if (mi < m.size()) out.add(m.get(mi++));
            out.add(g.get(gi++));
            if (gi < g.size()) out.add(g.get(gi++));
            out.add(revision());
        }
        int practiceIdx = 0;
        while (mi < m.size()) {
            out.add(m.get(mi++));
            if (mi < m.size()) out.add(m.get(mi++)); else out.add(composedMathRevision());
            out.add(gsPractice(blockList.isEmpty() ? "GS" : blockList.get(practiceIdx++ % blockList.size())));
        }
        for (int c = 0; c < composedCycles; c++) {
            out.add(composedMathPractice());
            out.add(composedMathRevision());
            out.add(gsPractice(blockList.isEmpty() ? "GS" : blockList.get(practiceIdx++ % blockList.size())));
        }
        Rollnum.renumber(out);
        return out;
    }

    /** Extra composed cycles appended later when the queue runs short. */
    public static List<Session> composedCycle(String practiceBlock) {
        List<Session> l = new ArrayList<Session>();
        l.add(composedMathPractice());
        l.add(composedMathRevision());
        l.add(gsPractice(practiceBlock));
        return l;
    }

    static Session fromSlot(Plan.Slot sl, char stream, int planIndex) {
        Session s = new Session();
        s.stream = stream;
        s.kind = "plan";
        s.planIndex = planIndex;
        s.subject = sl.subject;
        s.block = sl.subject;
        s.plannedMin = sl.plannedMin;
        for (Piece p : sl.pieces) s.pieces.add(p.copyUnticked());
        s.summary = summarize(s.pieces);
        return s;
    }

    static Session consolidation(String block) {
        Session s = new Session();
        s.stream = 'G';
        s.kind = "consolidation";
        s.subject = block;
        s.block = block;
        s.plannedMin = CONSOLIDATION_MIN;
        s.pieces.add(Piece.simple("consolidate", "Notebook review: " + block, 45,
                "Read through your physical notes for this block and mark what you cannot recall."));
        s.pieces.add(Piece.simple("consolidate", "Rewrite weak topics", 45,
                "Rewrite the topics you could not recall, from memory first, then check the source."));
        s.pieces.add(Piece.simple("consolidate", "PYQ and answer practice", 45,
                "Use the question sets in your own books for this block; where there are none, write Mains-style answer outlines."));
        s.summary = "Block consolidation: notebook review, rewriting weak topics, PYQ practice";
        return s;
    }

    static Session revision() {
        Session s = new Session();
        s.stream = 'R';
        s.kind = "revision";
        s.subject = "Revision and PYQ";
        s.block = "Revision";
        s.plannedMin = REVISION_MIN;
        s.summary = "Topics due at 1, 7 and 30 days, then question practice";
        return s;
    }

    static Session gsPractice(String block) {
        Session s = new Session();
        s.stream = 'G';
        s.kind = "practice";
        s.subject = block;
        s.block = block;
        s.plannedMin = PRACTICE_MIN;
        s.pieces.add(Piece.simple("practice", "Question practice: " + block, 90,
                "Use the question sets in your own books for this block; where there are none, write Mains-style answer outlines for what you covered."));
        s.pieces.add(Piece.simple("review", "Revise your notes: " + block, 60,
                "Revise the physical notebook for this block and fix gaps."));
        s.summary = "GS question practice and revision: " + block;
        return s;
    }

    static Session composedMathPractice() {
        Session s = new Session();
        s.stream = 'M';
        s.kind = "composed";
        s.subject = "Mathematics: practice";
        s.block = "Mathematics practice";
        s.plannedMin = PRACTICE_MIN;
        s.pieces.add(Piece.simple("problems", "Timed problem practice on finished units", PRACTICE_MIN,
                "Solve timed problems from the units finished longest ago first."));
        s.summary = "Timed problem practice on finished units";
        return s;
    }

    static Session composedMathRevision() {
        Session s = new Session();
        s.stream = 'M';
        s.kind = "composed";
        s.subject = "Mathematics: revision";
        s.block = "Mathematics revision";
        s.plannedMin = PRACTICE_MIN;
        s.pieces.add(Piece.simple("review", "Revise finished units", 90,
                "Re-derive key proofs and results of finished units from memory."));
        s.pieces.add(Piece.simple("problems", "Weak-topic problems", 60,
                "Problems on the topics where you made mistakes."));
        s.summary = "Revision of finished Mathematics units and weak-topic problems";
        return s;
    }

    public static String summarize(List<Piece> ps) {
        StringBuilder sb = new StringBuilder();
        String last = null;
        for (Piece p : ps) {
            String t = p.title == null ? "" : p.title;
            if (t.equals(last)) continue;
            last = t;
            if (sb.length() > 0) sb.append(" \u00b7 ");
            sb.append(t);
            if (p.pages != null && !"lec".equals(p.kind)) {
                sb.append(" (").append(p.pages.replace("pp. ", "").replace("p. ", "")).append(")");
            }
            if (sb.length() > 140) break;
        }
        String r = sb.toString();
        return r.length() > 140 ? r.substring(0, 137) + "..." : r;
    }
}

package com.yash.upsc400v2.engine;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.Test;

public class EngineTest {

    private static Plan realPlan;

    private static Plan plan() throws Exception {
        if (realPlan == null) {
            File f = new File("src/main/assets/plan.json");
            String t = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            realPlan = Plan.parse(t);
        }
        return realPlan;
    }

    private static Session s(char stream, String kind, String block, String status) {
        Session x = new Session();
        x.stream = stream; x.kind = kind; x.block = block; x.subject = block; x.status = status;
        return x;
    }

    // ---- JSON ----
    @Test public void jsonRoundTrip() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("a", "x\"y\\z\n\u00e9");
        m.put("n", Long.valueOf(5));
        m.put("d", Double.valueOf(2.5));
        m.put("l", Arrays.asList((Object) Long.valueOf(1), "two", null, Boolean.TRUE));
        Map<String, Object> back = MiniJson.map(MiniJson.parse(MiniJson.write(m)));
        assertEquals("x\"y\\z\n\u00e9", back.get("a"));
        assertEquals(5L, ((Number) back.get("n")).longValue());
        assertEquals(2.5, ((Number) back.get("d")).doubleValue(), 1e-9);
        assertEquals(4, MiniJson.list(back.get("l")).size());
    }

    @Test public void jsonUnicodeEscape() {
        assertEquals("A\u00e9", MiniJson.parse("\"A\\u00e9\""));
    }

    // ---- real plan ----
    @Test public void planCounts() throws Exception {
        Plan p = plan();
        assertEquals(1072, p.items.size());
        int m = 0, g = 0;
        for (Plan.Slot sl : p.slots) { if ("Mathematics".equals(sl.slot)) m++; else g++; }
        assertEquals(400, m);
        assertEquals(636, g);
    }

    @Test public void queueCyclesFirstPass() throws Exception {
        List<Session> q = QueueBuilder.build(plan(), 0);
        int gs = 0, cons = 0, mm = 0, rev = 0, plan = 0;
        for (Session s : q) {
            if (s.stream == 'G' && ("plan".equals(s.kind) || "consolidation".equals(s.kind))) { gs++; if ("consolidation".equals(s.kind)) cons++; }
            if (s.stream == 'M') mm++;
            if (s.stream == 'R') rev++;
        }
        assertEquals(652, gs);
        assertEquals(16, cons);
        assertEquals(400, mm);
        assertEquals(326, rev);
        assertEquals(37, countKind(q, "practice"));
        // first cycle pattern M, G, G, R
        assertEquals('M', q.get(0).stream);
        assertEquals('G', q.get(1).stream);
        assertEquals('G', q.get(2).stream);
        assertEquals('R', q.get(3).stream);
        assertEquals(135, q.get(1).plannedMin);
        assertEquals(120, q.get(2).plannedMin);
        assertEquals(180, q.get(0).plannedMin);
    }

    @Test public void queuePostGsCycle() throws Exception {
        List<Session> q = QueueBuilder.build(plan(), 0);
        int start = 326 * 4;
        // after the GS first pass: M, M, GS practice
        assertEquals('M', q.get(start).stream);
        assertEquals('M', q.get(start + 1).stream);
        assertEquals("practice", q.get(start + 2).kind);
        assertEquals(150, q.get(start + 2).plannedMin);
        // 74 remaining Mathematics plan sessions -> 37 cycles of three
        assertEquals(start + 37 * 3, q.size());
    }

    @Test public void totalPlannedHours() throws Exception {
        List<Session> q = QueueBuilder.build(plan(), 0);
        long min = 0;
        for (Session s : q) min += s.plannedMin;
        // 2896 h of planned first-pass content (see step-1 document), to within a few minutes
        assertEquals(2896.0, min / 60.0, 0.5);
    }

    @Test public void blocksAreContiguousWithConsolidationLast() throws Exception {
        List<Session> q = QueueBuilder.build(plan(), 0);
        String lastBlock = null;
        List<Session> g = new ArrayList<Session>();
        for (Session s : q) if (s.stream == 'G') g.add(s);
        int consolidationSeen = 0;
        for (int i = 0; i < g.size(); i++) {
            if ("consolidation".equals(g.get(i).kind)) {
                consolidationSeen++;
                if (i + 1 < g.size()) assertFalse(g.get(i + 1).block.equals(g.get(i).block));
            }
        }
        assertEquals(16, consolidationSeen);
    }

    @Test public void everyItemAppearsInTheQueue() throws Exception {
        Plan p = plan();
        Set<String> seen = new HashSet<String>();
        for (Session s : QueueBuilder.build(p, 0)) for (Piece pc : s.pieces) if (pc.item != null) seen.add(pc.item);
        assertEquals(1072, seen.size());
    }

    private static int countKind(List<Session> q, String kind) {
        int n = 0;
        for (Session s : q) if (kind.equals(s.kind)) n++;
        return n;
    }

    // ---- skip ----
    @Test public void skipSwapsWithNextSameType() {
        List<Session> open = new ArrayList<Session>();
        open.add(s('M', "plan", "A", "todo"));
        open.add(s('G', "plan", "P", "todo"));
        open.add(s('G', "plan", "P", "todo"));
        open.add(s('R', "revision", "R", "todo"));
        open.add(s('M', "plan", "B", "todo"));
        Session first = open.get(0), last = open.get(4);
        assertTrue(SkipSwap.apply(open, 0));
        assertTrue(open.get(0) == last);
        assertTrue(open.get(4) == first);
        assertEquals(1, first.skipCount);
        assertEquals(1000L, open.get(0).seq);
    }

    @Test public void skipGsStaysWithinBlockAndUsesPreviousAtBlockEnd() {
        List<Session> open = new ArrayList<Session>();
        Session a = s('G', "plan", "P", "todo");
        Session b = s('G', "plan", "P", "todo");
        Session c = s('G', "consolidation", "P", "todo");
        Session d = s('G', "plan", "Q", "todo");
        open.add(a); open.add(b); open.add(c); open.add(d);
        assertEquals(1, SkipSwap.partner(open, 0));
        assertEquals(0, SkipSwap.partner(open, 1)); // last of block: previous in block
        assertEquals(SkipSwap.NONE, SkipSwap.partner(open, 2)); // consolidation cannot be skipped
        assertEquals(SkipSwap.NONE, SkipSwap.partner(open, 3)); // only one in its block
    }

    @Test public void skipRevisionDrops() {
        List<Session> open = new ArrayList<Session>();
        open.add(s('R', "revision", "R", "todo"));
        assertEquals(SkipSwap.DROP, SkipSwap.partner(open, 0));
    }

    // ---- carry over ----
    @Test public void carryOverKeepsOnlyUnticked() {
        Session from = s('G', "plan", "P", "doing");
        Piece a = Piece.simple("gs", "A", 60, "x"); a.ticked = true;
        Piece b = Piece.simple("gs", "B", 40, "x");
        Piece c = Piece.simple("lec", "C", 30, "x");
        from.pieces.add(a); from.pieces.add(b); from.pieces.add(c);
        Session carry = CarryOver.make(from);
        assertEquals(2, carry.pieces.size());
        assertEquals(70, carry.plannedMin);
        assertEquals("carry", carry.kind);
        assertEquals('G', carry.stream);
    }

    @Test public void carryOverNullWhenAllTicked() {
        Session from = s('M', "plan", "A", "doing");
        Piece a = Piece.simple("theory", "A", 60, "x"); a.ticked = true;
        from.pieces.add(a);
        assertNull(CarryOver.make(from));
    }

    @Test public void carryGoesToFrontOfItsStream() {
        List<Session> open = new ArrayList<Session>();
        open.add(s('M', "plan", "A", "todo"));
        open.add(s('G', "plan", "P", "todo"));
        open.add(s('G', "plan", "P", "todo"));
        Session carry = s('G', "carry", "P", "todo");
        CarryOver.insert(open, carry);
        assertTrue(open.get(1) == carry);
        assertEquals(4, open.size());
    }

    // ---- reviews ----
    @Test public void reviewsDueAtOneSevenThirtyAndLapse() {
        List<ReviewScheduler.Done> d = new ArrayList<ReviewScheduler.Done>();
        d.add(new ReviewScheduler.Done("a", "A", "core", 100, false, false, false));
        assertEquals(0, ReviewScheduler.compute(d, 100, 4).size());
        assertEquals(1, ReviewScheduler.compute(d, 101, 4).get(0).interval);
        assertEquals(1, ReviewScheduler.compute(d, 103, 4).size());   // still inside the window
        assertEquals(0, ReviewScheduler.compute(d, 104, 4).size());   // lapsed
        assertEquals(7, ReviewScheduler.compute(d, 107, 4).get(0).interval);
        assertEquals(30, ReviewScheduler.compute(d, 130, 4).get(0).interval);
        d.get(0).d1 = true;
        assertEquals(0, ReviewScheduler.compute(d, 101, 4).size());
    }

    @Test public void reviewsOrderCoreFirstAndCap() {
        List<ReviewScheduler.Done> d = new ArrayList<ReviewScheduler.Done>();
        d.add(new ReviewScheduler.Done("x1", "X1", "extended", 100, false, false, false));
        d.add(new ReviewScheduler.Done("s1", "S1", "standard", 100, false, false, false));
        for (int i = 0; i < 5; i++) d.add(new ReviewScheduler.Done("c" + i, "C", "core", 100, false, false, false));
        List<ReviewScheduler.Due> due = ReviewScheduler.compute(d, 101, 4);
        assertEquals(4, due.size());
        for (ReviewScheduler.Due x : due) assertEquals("core", x.tier);
    }

    // ---- V and streak ----
    @Test public void vCarriesPartialHours() {
        long ms = 3 * 3600000L + 40 * 60000L;
        assertEquals(3, Rewards.vTotal(ms));
        assertEquals(40 * 60000L, Rewards.vCarryMs(ms));
        assertEquals(1, Rewards.vTotal(ms + 20 * 60000L) - 3);
    }

    @Test public void badges() {
        assertEquals(0, Rewards.badgesEarned(9));
        assertEquals(1, Rewards.badgesEarned(10));
        assertEquals(3, Rewards.badgesEarned(100));
        assertEquals(4, Rewards.badgesEarned(250));
        assertEquals(50, Rewards.nextBadge(10));
        assertEquals(-1, Rewards.nextBadge(250));
    }

    private static Map<Integer, Long> days(int first, double... hours) {
        Map<Integer, Long> m = new HashMap<Integer, Long>();
        for (int i = 0; i < hours.length; i++) m.put(first + i, (long) (hours[i] * 3600000L));
        return m;
    }

    @Test public void streakBasic() {
        int[] r = Rewards.streak(days(10, 4, 5, 4), 12);
        assertEquals(3, r[0]);
        assertEquals(3, r[1]);
    }

    @Test public void streakTodayPendingDoesNotBreak() {
        int[] r = Rewards.streak(days(10, 4, 4, 0), 12);
        assertEquals(2, r[0]);
    }

    @Test public void streakRestTokenSavesOneMissedDayThenBreaks() {
        // 4h,4h,miss(token),4h -> streak 3; then miss again within 7 days breaks
        int[] r = Rewards.streak(days(10, 4, 4, 0, 4), 13);
        assertEquals(3, r[0]);
        int[] r2 = Rewards.streak(days(10, 4, 4, 0, 4, 0, 4), 15);
        assertEquals(1, r2[0]);
        assertEquals(3, r2[1]);
    }

    @Test public void streakBelowThresholdIsNotADay() {
        int[] r = Rewards.streak(days(10, 3.9, 4), 11);
        assertEquals(1, r[0]);
    }

    // ---- stats ----
    @Test public void averageCountsZeroDays() {
        // 10 hours over 5 days since first log = 2 h/day
        assertEquals(2.0 * 3600000, Stats.avgPerDay(10 * 3600000L, 100, 104), 1.0);
    }

    @Test public void movingAverage() {
        double[] a = Stats.movingAvg(new double[] {1, 2, 3, 4}, 2);
        assertEquals(1.0, a[0], 1e-9);
        assertEquals(1.5, a[1], 1e-9);
        assertEquals(3.5, a[3], 1e-9);
    }

    @Test public void projectedFinish() {
        long hour = 3600000L;
        assertEquals(110, Stats.projectedFinish(100 * hour, 10.0 * hour, 100));
        assertEquals(-1, Stats.projectedFinish(100 * hour, 0, 100));
        assertEquals(100, Stats.projectedFinish(0, 0, 100));
        assertEquals(101, Stats.projectedFinish(1, 5, 100));
    }

    @Test public void weekStartIsMonday() {
        // epoch day 4 = Monday 5 January 1970
        assertEquals(4, Stats.weekStart(4));
        assertEquals(4, Stats.weekStart(10));
        assertEquals(11, Stats.weekStart(11));
    }

    // ---- rollover ----
    @Test public void rolloverCountsLateNightForPreviousDate() {
        long zone = 5 * 3600000L + 30 * 60000L; // UTC+5:30
        // 2026-12-06 02:00 local = 2026-12-05 20:30 UTC
        long ts = java.time.LocalDateTime.of(2026, 12, 5, 20, 30).toEpochSecond(java.time.ZoneOffset.UTC) * 1000L;
        int day = Rollover.dayKey(ts, zone, 240);
        assertEquals(java.time.LocalDate.of(2026, 12, 5).toEpochDay(), (long) day);
        // 2026-12-06 04:30 local counts for the 6th
        long ts2 = java.time.LocalDateTime.of(2026, 12, 5, 23, 0).toEpochSecond(java.time.ZoneOffset.UTC) * 1000L;
        assertEquals(java.time.LocalDate.of(2026, 12, 6).toEpochDay(), (long) Rollover.dayKey(ts2, zone, 240));
    }

    // ---- pages ----
    @Test public void pageOffset() {
        assertEquals(17, PageMap.printedToPdf(10, 7));
        assertEquals(10, PageMap.pdfToPrinted(17, 7));
    }

    @Test public void parseRanges() {
        assertEquals(14, PageMap.parseRange("pp. 14-25")[0]);
        assertEquals(25, PageMap.parseRange("pp. 14-25")[1]);
        assertEquals(26, PageMap.parseRange("p. 26")[1]);
        assertNull(PageMap.parseRange(null));
        assertNull(PageMap.parseRange("none"));
        assertEquals(12, PageMap.pageCount("pp. 14-25"));
    }

    @Test public void pieceRangeProportional() {
        int[] full = PageMap.pieceRange(100, 199, 0.0, 1.0);
        assertEquals(100, full[0]);
        assertEquals(199, full[1]);
        int[] half = PageMap.pieceRange(100, 199, 0.0, 0.5);
        assertEquals(100, half[0]);
        assertEquals(149, half[1]);
        int[] second = PageMap.pieceRange(100, 199, 0.5, 1.0);
        assertEquals(150, second[0]);
        assertEquals(199, second[1]);
    }

    @Test public void calibration() {
        int[][] r = PageMap.calibrate(new int[] {5, 30, 61}, 90);
        assertEquals(29, r[0][1]);
        assertEquals(30, r[1][0]);
        assertEquals(60, r[1][1]);
        assertEquals(90, r[2][1]);
    }

    // ---- timer ----
    @Test public void timerArithmetic() {
        assertEquals(5000L, TimerMath.elapsed(2000, true, 10000, 13000));
        assertEquals(2000L, TimerMath.elapsed(2000, false, 10000, 13000));
        assertFalse(TimerMath.overtime(1, 59000));
        assertTrue(TimerMath.overtime(1, 61000));
        assertEquals(-1L, TimerMath.buzzAt(1, 61000, 5));
        assertEquals(59005L, TimerMath.buzzAt(1, 1000, 5));
        assertEquals("01:05", TimerMath.format(65000));
        assertEquals("1:01:05", TimerMath.format(3665000));
        assertEquals("-00:05", TimerMath.format(-5000));
    }

    @Test public void paceFigures() {
        assertEquals(10.0, Pace.perHour(20, 2 * 3600000L), 1e-9);
        assertEquals(0.0, Pace.perHour(5, 0), 1e-9);
        assertEquals(1.0, Pace.lectureFactor(100, 120), 1e-9);
    }

    // ---- backup ----
    @Test public void backupRoundTrip() {
        Map<String, List<Map<String, Object>>> t = new LinkedHashMap<String, List<Map<String, Object>>>();
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("id", Long.valueOf(7)); r.put("label", "a \"b\""); r.put("ms", Long.valueOf(123456789012L)); r.put("x", null);
        rows.add(r);
        t.put("log_entries", rows);
        String json = BackupCodec.encode(t, 5L);
        Map<String, List<Map<String, Object>>> back = BackupCodec.decode(json);
        assertEquals(1, back.get("log_entries").size());
        assertEquals("a \"b\"", back.get("log_entries").get(0).get("label"));
        assertEquals(123456789012L, ((Number) back.get("log_entries").get(0).get("ms")).longValue());
    }

    @Test public void backupRejectsWrongFormat() {
        try {
            BackupCodec.decode("{\"format\":\"other\",\"version\":2,\"tables\":{}}");
            fail("expected failure");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    private static void fail(String m) { throw new AssertionError(m); }
}

package com.yash.upsc400v2.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The approved plan (plan.json, verbose format): items and the Mathematics / GS slots in day order. */
public final class Plan {
    public static final class Item {
        public String id, block, res, rname, title, tier, mode, kind, unit;
        public int pages = -1, p0 = -1, p1 = -1;
        public boolean approx;
        public int totalMin;
    }

    public static final class Slot {
        public String slot, subject;
        public int day, plannedMin;
        public List<Piece> pieces = new ArrayList<Piece>();
    }

    public final List<Item> items = new ArrayList<Item>();
    public final List<Slot> slots = new ArrayList<Slot>();
    public String startDate = "", endDate = "";

    public static Plan parse(String json) {
        Map<String, Object> root = MiniJson.map(MiniJson.parse(json));
        Plan plan = new Plan();
        Map<String, Object> meta = MiniJson.map(root.get("meta"));
        plan.startDate = MiniJson.str(meta, "start_date");
        plan.endDate = MiniJson.str(meta, "end_date");
        for (Object o : MiniJson.list(root.get("items"))) {
            Map<String, Object> m = MiniJson.map(o);
            Item it = new Item();
            it.id = MiniJson.str(m, "id");
            it.block = MiniJson.str(m, "block");
            it.res = MiniJson.str(m, "res");
            it.rname = MiniJson.str(m, "rname");
            it.title = MiniJson.str(m, "title");
            it.tier = MiniJson.str(m, "tier");
            it.mode = MiniJson.str(m, "mode");
            it.kind = MiniJson.str(m, "kind");
            it.unit = MiniJson.str(m, "unit");
            it.pages = MiniJson.integer(m, "pages", -1);
            it.p0 = MiniJson.integer(m, "p0", -1);
            it.p1 = MiniJson.integer(m, "p1", -1);
            it.approx = MiniJson.bool(m, "approx");
            plan.items.add(it);
        }
        java.util.HashMap<String, Item> byId = new java.util.HashMap<String, Item>();
        for (Item it : plan.items) byId.put(it.id, it);
        for (Object o : MiniJson.list(root.get("days"))) {
            Map<String, Object> d = MiniJson.map(o);
            int day = MiniJson.integer(d, "day", 0);
            for (Object so : MiniJson.list(d.get("slots"))) {
                Map<String, Object> sm = MiniJson.map(so);
                String name = MiniJson.str(sm, "slot");
                if (!"Mathematics".equals(name) && !"GS slot 1".equals(name) && !"GS slot 2".equals(name)) continue;
                Slot sl = new Slot();
                sl.slot = name;
                sl.subject = MiniJson.str(sm, "subject");
                sl.day = day;
                sl.plannedMin = MiniJson.integer(sm, "planned_min", 0);
                for (Object po : MiniJson.list(sm.get("pieces"))) {
                    Map<String, Object> pm = MiniJson.map(po);
                    Piece p = new Piece();
                    p.item = MiniJson.str(pm, "item");
                    p.kind = nn(MiniJson.str(pm, "kind"));
                    p.title = nn(MiniJson.str(pm, "title"));
                    p.resource = nn(MiniJson.str(pm, "resource"));
                    p.tier = nn(MiniJson.str(pm, "tier"));
                    p.pages = emptyToNull(MiniJson.str(pm, "pages"));
                    p.mode = MiniJson.str(pm, "mode");
                    p.objective = MiniJson.str(pm, "objective");
                    p.syllabus = MiniJson.str(pm, "syllabus");
                    p.pyq = MiniJson.str(pm, "pyq_focus");
                    p.note = emptyToNull(MiniJson.str(pm, "note"));
                    p.prerequisites = MiniJson.str(pm, "prerequisites");
                    p.unit = MiniJson.str(pm, "unit");
                    p.minutes = (int) Math.round(MiniJson.dbl(pm, "minutes", 0));
                    Object fr = pm.get("frac");
                    if (fr instanceof List && ((List<?>) fr).size() >= 2) {
                        p.f0 = ((Number) ((List<?>) fr).get(0)).doubleValue();
                        p.f1 = ((Number) ((List<?>) fr).get(1)).doubleValue();
                    } else { p.f0 = 0; p.f1 = 1; }
                    p.approx = MiniJson.bool(pm, "approx");
                    sl.pieces.add(p);
                    Item it = p.item == null ? null : byId.get(p.item);
                    if (it != null) it.totalMin += p.minutes;
                }
                plan.slots.add(sl);
            }
        }
        return plan;
    }

    private static String nn(String s) { return s == null ? "" : s; }
    private static String emptyToNull(String s) { return s == null || s.length() == 0 ? null : s; }
}

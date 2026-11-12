package com.yash.upsc400v2.engine;

/** One content piece of a session (a chapter range, a lecture portion, a practice block). */
public final class Piece {
    public long id;
    public String item;       // plan item id, or null for composed pieces
    public String kind = "";
    public String title = "";
    public String resource = "";
    public String tier = "";
    public String pages;      // printed page range text, may be null
    public String mode, objective, syllabus, pyq, note, prerequisites, unit;
    public int minutes;
    public double f0, f1;
    public boolean approx;
    public boolean ticked;

    public Piece copyUnticked() {
        Piece p = new Piece();
        p.item = item; p.kind = kind; p.title = title; p.resource = resource; p.tier = tier;
        p.pages = pages; p.mode = mode; p.objective = objective; p.syllabus = syllabus;
        p.pyq = pyq; p.note = note; p.prerequisites = prerequisites; p.unit = unit;
        p.minutes = minutes; p.f0 = f0; p.f1 = f1; p.approx = approx; p.ticked = false;
        return p;
    }

    public static Piece simple(String kind, String title, int minutes, String objective) {
        Piece p = new Piece();
        p.kind = kind; p.title = title; p.minutes = minutes; p.objective = objective;
        return p;
    }
}

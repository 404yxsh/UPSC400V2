package com.yash.upsc400v2.engine;

import java.util.ArrayList;
import java.util.List;

/** A queue session. stream: 'M' Mathematics, 'G' GS, 'R' revision. */
public final class Session {
    public long id;
    public long seq;
    public char stream = 'M';
    public String kind = "plan";   // plan, consolidation, carry, composed, practice, revision
    public int planIndex = -1;
    public String subject = "";
    public String block = "";
    public int plannedMin;
    public String status = "todo"; // todo, doing, done, skipped
    public int skipCount;
    public String summary = "";
    public List<Piece> pieces = new ArrayList<Piece>();
}

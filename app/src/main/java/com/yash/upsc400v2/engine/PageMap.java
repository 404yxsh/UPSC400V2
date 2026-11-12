package com.yash.upsc400v2.engine;

import java.util.ArrayList;
import java.util.List;

/** Printed page <-> PDF page conversion and page-range helpers. PDF pages are 1-based. */
public final class PageMap {
    private PageMap() {}

    public static int printedToPdf(int printed, int offset) { return printed + offset; }

    public static int pdfToPrinted(int pdfPage, int offset) { return pdfPage - offset; }

    /** First and last page numbers found in text such as "pp. 14-25" or "p. 26"; null if none. */
    public static int[] parseRange(String s) {
        if (s == null) return null;
        List<Integer> nums = new ArrayList<Integer>();
        int i = 0;
        while (i < s.length()) {
            if (Character.isDigit(s.charAt(i))) {
                int j = i;
                while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
                if (j - i <= 5) nums.add(Integer.valueOf(s.substring(i, j)));
                i = j;
            } else i++;
        }
        if (nums.isEmpty()) return null;
        return new int[] {nums.get(0), nums.get(nums.size() - 1)};
    }

    public static int pageCount(String s) {
        int[] r = parseRange(s);
        return r == null ? 0 : Math.max(0, r[1] - r[0] + 1);
    }

    /** Printed page range covered by fractions f0..f1 of an item spanning p0..p1. */
    public static int[] pieceRange(int p0, int p1, double f0, double f1) {
        int size = p1 - p0 + 1;
        int a = p0 + (int) Math.round(f0 * size);
        int b = p0 + (int) Math.round(f1 * size) - 1;
        if (f1 >= 0.9995) b = p1;
        if (f0 <= 0.0005) a = p0;
        if (b < a) b = a;
        if (a > p1) a = p1;
        if (b > p1) b = p1;
        return new int[] {a, b};
    }

    public static String format(int[] r) {
        if (r[0] == r[1]) return "p. " + r[0];
        return "pp. " + r[0] + "-" + r[1];
    }

    /** One-time calibration: start pages of consecutive items; end = next start - 1; last end given. */
    public static int[][] calibrate(int[] starts, int lastPage) {
        int[][] out = new int[starts.length][2];
        for (int k = 0; k < starts.length; k++) {
            out[k][0] = starts[k];
            out[k][1] = (k + 1 < starts.length) ? Math.max(starts[k], starts[k + 1] - 1) : Math.max(starts[k], lastPage);
        }
        return out;
    }
}

package org.junit;
/** Tiny stand-in for JUnit 4 Assert (the real JUnit jar is not available in this sandbox). */
public class Assert {
    public static void assertTrue(boolean c) { if (!c) throw new AssertionError("expected true"); }
    public static void assertTrue(String m, boolean c) { if (!c) throw new AssertionError(m); }
    public static void assertFalse(boolean c) { if (c) throw new AssertionError("expected false"); }
    public static void assertFalse(String m, boolean c) { if (c) throw new AssertionError(m); }
    public static void assertNull(Object o) { if (o != null) throw new AssertionError("expected null but was " + o); }
    public static void assertNotNull(Object o) { if (o == null) throw new AssertionError("expected not null"); }
    public static void assertEquals(long e, long a) { if (e != a) throw new AssertionError("expected " + e + " but was " + a); }
    public static void assertEquals(String m, long e, long a) { if (e != a) throw new AssertionError(m + ": expected " + e + " but was " + a); }
    public static void assertEquals(Object e, Object a) {
        if (e == null ? a != null : !e.equals(a)) throw new AssertionError("expected " + e + " but was " + a);
    }
    public static void assertEquals(String m, Object e, Object a) {
        if (e == null ? a != null : !e.equals(a)) throw new AssertionError(m + ": expected " + e + " but was " + a);
    }
    public static void assertEquals(double e, double a, double d) {
        if (Math.abs(e - a) > d) throw new AssertionError("expected " + e + " but was " + a);
    }
}

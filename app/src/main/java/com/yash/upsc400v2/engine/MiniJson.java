package com.yash.upsc400v2.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON reader/writer with no Android dependency. Numbers are Long or Double. */
public final class MiniJson {
    private final String s;
    private int i;

    private MiniJson(String s) { this.s = s; }

    public static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != text.length()) throw new IllegalArgumentException("Trailing data at " + p.i);
        return v;
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++; else break;
        }
    }

    private Object value() {
        if (i >= s.length()) throw new IllegalArgumentException("Unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{': return obj();
            case '[': return arr();
            case '"': return str();
            case 't': lit("true"); return Boolean.TRUE;
            case 'f': lit("false"); return Boolean.FALSE;
            case 'n': lit("null"); return null;
            default: return num();
        }
    }

    private void lit(String w) {
        if (!s.startsWith(w, i)) throw new IllegalArgumentException("Bad literal at " + i);
        i += w.length();
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        i++;
        ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = str();
            ws();
            if (s.charAt(i) != ':') throw new IllegalArgumentException("Expected : at " + i);
            i++;
            ws();
            m.put(k, value());
            ws();
            char c = s.charAt(i++);
            if (c == ',') continue;
            if (c == '}') return m;
            throw new IllegalArgumentException("Bad object at " + i);
        }
    }

    private List<Object> arr() {
        List<Object> l = new ArrayList<Object>();
        i++;
        ws();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ',') continue;
            if (c == ']') return l;
            throw new IllegalArgumentException("Bad array at " + i);
        }
    }

    private String str() {
        if (s.charAt(i) != '"') throw new IllegalArgumentException("Expected string at " + i);
        i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw new IllegalArgumentException("Bad escape at " + i);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object num() {
        int st = i;
        boolean fl = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+') i++;
            else if (c == '.' || c == 'e' || c == 'E') { fl = true; i++; }
            else break;
        }
        String t = s.substring(st, i);
        if (t.length() == 0) throw new IllegalArgumentException("Bad value at " + st);
        if (fl) return Double.valueOf(t);
        return Long.valueOf(t);
    }

    // ---- helpers ----
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) { return (List<Object>) o; }

    public static String str(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o == null ? null : o.toString();
    }

    public static double dbl(Map<String, Object> m, String k, double def) {
        Object o = m.get(k);
        return o instanceof Number ? ((Number) o).doubleValue() : def;
    }

    public static int integer(Map<String, Object> m, String k, int def) {
        Object o = m.get(k);
        return o instanceof Number ? ((Number) o).intValue() : def;
    }

    public static boolean bool(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o instanceof Boolean && ((Boolean) o).booleanValue();
    }

    // ---- writer ----
    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        w(sb, v);
        return sb.toString();
    }

    private static void w(StringBuilder sb, Object v) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String) { q(sb, (String) v); return; }
        if (v instanceof Boolean) { sb.append(v.toString()); return; }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null"); else sb.append(Double.toString(d));
            return;
        }
        if (v instanceof Number) { sb.append(((Number) v).longValue()); return; }
        if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                q(sb, String.valueOf(e.getKey()));
                sb.append(':');
                w(sb, e.getValue());
            }
            sb.append('}');
            return;
        }
        if (v instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object o : (List<?>) v) {
                if (!first) sb.append(',');
                first = false;
                w(sb, o);
            }
            sb.append(']');
            return;
        }
        q(sb, v.toString());
    }

    private static void q(StringBuilder sb, String t) {
        sb.append('"');
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        sb.append('"');
    }
}

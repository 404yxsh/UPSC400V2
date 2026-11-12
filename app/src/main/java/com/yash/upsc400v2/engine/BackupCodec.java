package com.yash.upsc400v2.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Backup file format, version 2: {"format":"upsc400-v2","version":2,"tables":{name:[{col:val}]}}. */
public final class BackupCodec {
    public static final int VERSION = 2;
    public static final String FORMAT = "upsc400-v2";

    private BackupCodec() {}

    public static String encode(Map<String, List<Map<String, Object>>> tables, long createdAt) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("format", FORMAT);
        root.put("version", Integer.valueOf(VERSION));
        root.put("created", Long.valueOf(createdAt));
        root.put("tables", tables);
        return MiniJson.write(root);
    }

    public static Map<String, List<Map<String, Object>>> decode(String json) {
        Map<String, Object> root = MiniJson.map(MiniJson.parse(json));
        if (!FORMAT.equals(MiniJson.str(root, "format"))) throw new IllegalArgumentException("Not a UPSC 400 v2 backup");
        if (MiniJson.integer(root, "version", 0) != VERSION) throw new IllegalArgumentException("Unsupported backup version");
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<String, List<Map<String, Object>>>();
        Map<String, Object> t = MiniJson.map(root.get("tables"));
        for (Map.Entry<String, Object> e : t.entrySet()) {
            List<Map<String, Object>> rows = new java.util.ArrayList<Map<String, Object>>();
            for (Object r : MiniJson.list(e.getValue())) rows.add(MiniJson.map(r));
            out.put(e.getKey(), rows);
        }
        return out;
    }
}

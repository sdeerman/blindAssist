package com.example.test_android_dev;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Utility class for JSON operations.
 */
public class JsonUtils {
    /**
     * Convert a JSONObject to a Map<String, Object>.
     * This is used for AutoGLM SDK command parsing.
     */
    public static Map<String, Object> toMap(JSONObject json) {
        if (json == null) {
            return new HashMap<>();
        }

        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = json.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            try {
                Object value = json.get(key);
                if (value instanceof JSONObject) {
                    map.put(key, toMap((JSONObject) value));
                } else if (value instanceof org.json.JSONArray) {
                    // Handle arrays if needed
                    map.put(key, value.toString());
                } else {
                    map.put(key, value);
                }
            } catch (Exception e) {
                // Skip problematic keys
            }
        }

        return map;
    }
}
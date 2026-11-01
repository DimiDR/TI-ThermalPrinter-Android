package com.dantsu.thermalprinter.helpClasses;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks a TastyIgniter location id from {@code GET /api/locations} JSON:API.
 */
public final class LocationIdResolver {

    private LocationIdResolver() {}

    public static int resolve(String locationsJson, int preferred) {
        List<Integer> ids = parseIds(locationsJson);
        if (ids.isEmpty()) {
            return preferred > 0 ? preferred : 1;
        }
        if (preferred > 0 && ids.contains(preferred)) {
            return preferred;
        }
        return ids.get(0);
    }

    static List<Integer> parseIds(String json) {
        List<Integer> ids = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return ids;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray data = root.optJSONArray("data");
            if (data == null) {
                return ids;
            }
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                int id = item.optInt("id", 0);
                if (id <= 0) {
                    JSONObject attributes = item.optJSONObject("attributes");
                    if (attributes != null) {
                        id = attributes.optInt("location_id", 0);
                    }
                }
                if (id > 0) {
                    ids.add(id);
                }
            }
        } catch (Exception ignored) {
        }
        return ids;
    }
}

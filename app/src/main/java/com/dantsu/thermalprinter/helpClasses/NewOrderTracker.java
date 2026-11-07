package com.dantsu.thermalprinter.helpClasses;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single source of truth for "which orders have already been announced" (new-order sound).
 * <p>
 * The first catalog seen after start/reset only seeds the set with the unprinted orders that
 * already exist, so the sound is not played for a backlog. Afterwards every unprinted order
 * that was not announced before is reported once.
 */
public final class NewOrderTracker {
    private static final Set<String> ANNOUNCED = Collections.synchronizedSet(new HashSet<>());
    private static boolean seeded;

    private NewOrderTracker() {
    }

    /**
     * @return ids of orders that are new since the last call (empty on the seeding call).
     */
    public static synchronized List<String> detectNew(JSONArray orders, Set<String> printedOrders) {
        List<String> fresh = new ArrayList<>();
        if (orders == null) {
            return fresh;
        }
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            String orderId = order == null ? "" : order.optString("id", "");
            if (orderId.isEmpty() || printedOrders.contains(orderId) || ANNOUNCED.contains(orderId)) {
                continue;
            }
            ANNOUNCED.add(orderId);
            if (seeded) {
                fresh.add(orderId);
            }
        }
        seeded = true;
        return fresh;
    }

    /** Orders whose sound has already been played (or that were part of the initial backlog). */
    public static Set<String> announcedOrders() {
        return ANNOUNCED;
    }

    /** Forget everything, e.g. after switching shop or clearing the print history. */
    public static synchronized void reset() {
        ANNOUNCED.clear();
        seeded = false;
    }
}

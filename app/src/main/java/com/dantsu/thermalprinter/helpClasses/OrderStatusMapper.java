package com.dantsu.thermalprinter.helpClasses;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves TastyIgniter order statuses by name first, then by optional API map.
 * Numeric defaults are only used when the payload has no status name.
 */
public final class OrderStatusMapper {
    public static final String RECEIVED = "received";
    public static final String PENDING = "pending";
    public static final String CONFIRMED = "confirmed";
    public static final String PREPARATION = "preparation";
    public static final String DELIVERY = "delivery";
    public static final String COMPLETED = "completed";

    private static volatile Map<String, Integer> apiNameToId = Collections.emptyMap();

    private OrderStatusMapper() {}

    public static void loadFromStatusesJson(String json) {
        Map<String, Integer> map = new HashMap<>();
        if (json == null || json.isEmpty()) {
            apiNameToId = Collections.emptyMap();
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray data = root.optJSONArray("data");
            if (data == null) {
                apiNameToId = Collections.emptyMap();
                return;
            }
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                int id = parseId(item.optString("id", String.valueOf(item.optInt("status_id", -1))));
                JSONObject attributes = item.optJSONObject("attributes");
                String name = "";
                if (attributes != null) {
                    name = firstNonEmpty(attributes.optString("status_name", ""), attributes.optString("name", ""));
                }
                if (name.isEmpty()) {
                    name = firstNonEmpty(item.optString("status_name", ""), item.optString("name", ""));
                }
                if (id > 0 && !name.isEmpty()) {
                    map.put(normalize(name), id);
                }
            }
        } catch (Exception ignored) {
            map.clear();
        }
        apiNameToId = map;
    }

    public static JSONObject orderData(JSONObject order) {
        if (order == null) {
            return new JSONObject();
        }
        JSONObject attributes = order.optJSONObject("attributes");
        return attributes != null ? attributes : order;
    }

    public static String statusName(JSONObject order) {
        JSONObject data = orderData(order);
        JSONObject status = data.optJSONObject("status");
        if (status != null) {
            String nested = firstNonEmpty(
                    status.optString("status_name", ""),
                    status.optString("name", ""),
                    status.optString("status_status_name", "")
            );
            if (!nested.isEmpty()) {
                return nested;
            }
        }
        return firstNonEmpty(data.optString("status_name", ""), data.optString("status", ""));
    }

    public static int statusId(JSONObject order) {
        JSONObject data = orderData(order);
        JSONObject status = data.optJSONObject("status");
        if (status != null && status.has("status_id")) {
            return status.optInt("status_id", -1);
        }
        return data.optInt("status_id", -1);
    }

    public static boolean isCompleted(JSONObject order) {
        return matches(statusName(order), COMPLETED) || (!hasName(order) && statusId(order) == 5);
    }

    public static boolean isReceived(JSONObject order) {
        String name = statusName(order);
        return matches(name, RECEIVED) || matches(name, PENDING) || (!hasName(order) && statusId(order) == 1);
    }

    public static JSONArray filterPrintable(JSONArray dataArray, Set<String> printedOrders) throws JSONException {
        JSONArray printableOrders = new JSONArray();
        if (dataArray == null) {
            return printableOrders;
        }
        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject order = dataArray.getJSONObject(i);
            String orderId = order.optString("id", "");
            if (orderId.isEmpty()) {
                continue;
            }
            if ((printedOrders == null || !printedOrders.contains(orderId)) && isReceived(order)) {
                printableOrders.put(order);
            }
        }
        return printableOrders;
    }

    public static int idForName(String statusName) {
        String key = normalize(statusName);
        Integer fromApi = apiNameToId.get(key);
        if (fromApi != null) {
            return fromApi;
        }
        switch (key) {
            case RECEIVED:
            case PENDING:
                return 1;
            case CONFIRMED:
                return 2;
            case PREPARATION:
                return 3;
            case DELIVERY:
                return 4;
            case COMPLETED:
                return 5;
            default:
                return -1;
        }
    }

    public static String displayName(JSONObject order) {
        String name = statusName(order);
        if (!name.isEmpty()) {
            return name;
        }
        switch (statusId(order)) {
            case 1:
                return "Pending";
            case 2:
                return "Confirmed";
            case 3:
                return "Preparation";
            case 4:
                return "Delivery";
            case 5:
                return "Completed";
            default:
                return "Unknown";
        }
    }

    public static int color(JSONObject order) {
        String name = normalize(statusName(order));
        if (name.isEmpty()) {
            switch (statusId(order)) {
                case 1:
                    name = PENDING;
                    break;
                case 2:
                    name = CONFIRMED;
                    break;
                case 3:
                    name = PREPARATION;
                    break;
                case 4:
                    name = DELIVERY;
                    break;
                case 5:
                    name = COMPLETED;
                    break;
                default:
                    return 0xFFFF0000;
            }
        }
        switch (name) {
            case RECEIVED:
            case PENDING:
                return 0xFFFFA500;
            case CONFIRMED:
                return 0xFFFFD700;
            case PREPARATION:
                return 0xFFFF6B35;
            case DELIVERY:
                return 0xFF4CAF50;
            case COMPLETED:
                return 0xFF9E9E9E;
            default:
                return 0xFFFF0000;
        }
    }

    private static boolean hasName(JSONObject order) {
        return !statusName(order).isEmpty();
    }

    private static boolean matches(String actual, String expected) {
        return normalize(actual).equals(expected);
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String n = value.trim().toLowerCase(Locale.ROOT);
        switch (n) {
            case "eingegangen":
                return RECEIVED;
            case "ausstehend":
                return PENDING;
            case "bestätigt":
            case "bestaetigt":
                return CONFIRMED;
            case "zubereitung":
                return PREPARATION;
            case "lieferung":
                return DELIVERY;
            case "abgeschlossen":
                return COMPLETED;
            default:
                return n;
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static int parseId(String id) {
        try {
            return Integer.parseInt(id);
        } catch (Exception e) {
            return -1;
        }
    }
}

package com.dantsu.thermalprinter.helpClasses;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OrderStatusMapperTest {

    @Before
    public void resetApiMap() {
        OrderStatusMapper.loadFromStatusesJson(null);
    }

    @Test
    public void receivedByEnglishAndGermanName() throws Exception {
        assertTrue(OrderStatusMapper.isReceived(orderWithStatus("Received", 99)));
        assertTrue(OrderStatusMapper.isReceived(orderWithStatus("Eingegangen", 99)));
        assertTrue(OrderStatusMapper.isReceived(orderWithStatus("Pending", 99)));
        assertFalse(OrderStatusMapper.isReceived(orderWithStatus("Completed", 1)));
    }

    @Test
    public void receivedFallsBackToIdWhenNameMissing() throws Exception {
        assertTrue(OrderStatusMapper.isReceived(orderWithIdOnly(1)));
        assertFalse(OrderStatusMapper.isReceived(orderWithIdOnly(5)));
    }

    @Test
    public void completedByNameIgnoresNumericId() throws Exception {
        assertTrue(OrderStatusMapper.isCompleted(orderWithStatus("Abgeschlossen", 1)));
        assertTrue(OrderStatusMapper.isCompleted(orderWithStatus("Completed", 2)));
        assertFalse(OrderStatusMapper.isCompleted(orderWithStatus("Preparation", 5)));
    }

    @Test
    public void idForNameUsesApiMapThenDefaults() {
        OrderStatusMapper.loadFromStatusesJson(
                "{\"data\":[{\"id\":\"12\",\"status_name\":\"Preparation\"}]}");
        assertEquals(12, OrderStatusMapper.idForName("preparation"));
        assertEquals(12, OrderStatusMapper.idForName("Zubereitung"));
        assertEquals(5, OrderStatusMapper.idForName("completed"));
        assertEquals(-1, OrderStatusMapper.idForName("unknown-status"));
    }

    @Test
    public void displayNamePrefersPayloadName() throws Exception {
        assertEquals("Zubereitung", OrderStatusMapper.displayName(orderWithStatus("Zubereitung", 1)));
        assertEquals("Completed", OrderStatusMapper.displayName(orderWithIdOnly(5)));
    }

    @Test
    public void filterPrintableKeepsUnprintedReceived() throws Exception {
        JSONArray data = new JSONArray()
                .put(orderWithStatusId("10", "Received", 1))
                .put(orderWithStatusId("11", "Completed", 5))
                .put(orderWithStatusId("12", "Eingegangen", 9));
        Set<String> printed = new HashSet<>();
        printed.add("10");
        JSONArray printable = OrderStatusMapper.filterPrintable(data, printed);
        assertEquals(1, printable.length());
        assertEquals("12", printable.getJSONObject(0).getString("id"));
    }

    @Test
    public void filterPrintableDeduplicatesPrintedIds() throws Exception {
        JSONArray data = new JSONArray()
                .put(orderWithStatusId("1", "Pending", 1))
                .put(orderWithStatusId("2", "Pending", 1));
        Set<String> printed = new HashSet<>();
        printed.add("1");
        printed.add("2");
        assertEquals(0, OrderStatusMapper.filterPrintable(data, printed).length());
    }

    private JSONObject orderWithStatusId(String id, String name, int statusId) throws Exception {
        JSONObject status = new JSONObject()
                .put("status_id", statusId)
                .put("status_name", name);
        JSONObject attributes = new JSONObject().put("status", status);
        return new JSONObject().put("id", id).put("attributes", attributes);
    }

    private JSONObject orderWithStatus(String name, int id) throws Exception {
        return orderWithStatusId("1", name, id);
    }

    private JSONObject orderWithIdOnly(int id) throws Exception {
        JSONObject status = new JSONObject().put("status_id", id);
        JSONObject attributes = new JSONObject().put("status", status);
        return new JSONObject().put("id", "1").put("attributes", attributes);
    }
}

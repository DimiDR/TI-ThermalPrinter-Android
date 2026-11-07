package com.dantsu.thermalprinter.helpClasses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NewOrderTrackerTest {

    @Before
    public void setUp() {
        NewOrderTracker.reset();
    }

    private static JSONArray orders(String... ids) throws Exception {
        JSONArray array = new JSONArray();
        for (String id : ids) {
            array.put(new JSONObject().put("id", id));
        }
        return array;
    }

    @Test
    public void firstCatalogOnlySeedsBacklog() throws Exception {
        assertTrue(NewOrderTracker.detectNew(orders("1", "2"), Collections.emptySet()).isEmpty());
        assertTrue(NewOrderTracker.announcedOrders().contains("1"));
    }

    @Test
    public void reportsEachNewOrderOnce() throws Exception {
        NewOrderTracker.detectNew(orders("1"), Collections.emptySet());
        List<String> fresh = NewOrderTracker.detectNew(orders("2", "1"), Collections.emptySet());
        assertEquals(Collections.singletonList("2"), fresh);
        assertTrue(NewOrderTracker.detectNew(orders("2", "1"), Collections.emptySet()).isEmpty());
    }

    @Test
    public void ignoresPrintedOrders() throws Exception {
        NewOrderTracker.detectNew(orders(), Collections.emptySet());
        Set<String> printed = new HashSet<>(Collections.singletonList("3"));
        assertTrue(NewOrderTracker.detectNew(orders("3"), printed).isEmpty());
    }

    @Test
    public void resetSeedsAgain() throws Exception {
        NewOrderTracker.detectNew(orders("1"), Collections.emptySet());
        NewOrderTracker.reset();
        assertTrue(NewOrderTracker.detectNew(orders("1", "2"), Collections.emptySet()).isEmpty());
    }
}

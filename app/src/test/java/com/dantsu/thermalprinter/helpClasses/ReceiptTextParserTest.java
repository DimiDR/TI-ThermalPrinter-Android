package com.dantsu.thermalprinter.helpClasses;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReceiptTextParserTest {

    @Test
    public void buildsReceiptFromTastyIgniterJsonApiShape() throws Exception {
        JSONObject order = new JSONObject();
        order.put("id", "3");
        order.put("type", "orders");

        JSONObject attributes = new JSONObject();
        attributes.put("first_name", "d");
        attributes.put("last_name", "d");
        attributes.put("telephone", "+4915923654985");
        attributes.put("payment", "cod");
        attributes.put("order_type", "collection");
        attributes.put("order_total", 35.98);
        attributes.put("order_time_is_asap", "true");
        attributes.put("order_date_time", "2026-08-13T16:02:00.000000Z");
        attributes.put("comment", JSONObject.NULL);
        attributes.put("formatted_address", JSONObject.NULL);

        JSONArray menus = new JSONArray();
        JSONObject menuItem = new JSONObject();
        menuItem.put("menu_id", "10");
        menuItem.put("name", "Special Shrimp Deluxe");
        menuItem.put("qty", 2);
        menuItem.put("subtotal", 35.98);
        menuItem.put("comment", JSONObject.NULL);
        menuItem.put("menu_options", new JSONArray());
        menus.put(menuItem);
        attributes.put("order_menus", menus);

        JSONArray totals = new JSONArray();
        JSONObject total = new JSONObject();
        total.put("title", "Order total");
        total.put("value", 35.98);
        totals.put(total);
        attributes.put("order_totals", totals);

        order.put("attributes", attributes);

        ReceiptTextParser.ReceiptData receipt = ReceiptTextParser.buildReceiptData(
                order, emptyCatalog(), emptyCatalog(), "TastyIgniter");

        assertEquals("Bestellung Nr.3", receipt.header.orderNumber);
        assertEquals("Abholung", receipt.header.orderType);
        assertEquals("Barzahlung", receipt.header.payment);
        assertEquals("35.98", receipt.header.orderTotal);
        assertTrue(receipt.header.orderTime.startsWith("Sofort: am "));

        assertEquals(1, receipt.items.size());
        assertEquals("Special Shrimp Deluxe", receipt.items.get(0).name);
        assertEquals("2", receipt.items.get(0).quantity);
        assertEquals("35.98", receipt.items.get(0).subtotal);

        assertEquals(1, receipt.totals.size());
        assertEquals("35.98", receipt.totals.get(0).value);

        assertEquals("d d", receipt.customerInfo.name);
        assertEquals("+4915923654985", receipt.customerInfo.telephone);
        assertEquals("nicht angegeben", receipt.customerInfo.address);
        assertFalse(receipt.customerInfo.hasGoogleMaps);
        assertEquals("TastyIgniter", receipt.header.shopName);
    }

    @Test
    public void fallsBackToLegacyOrderIdAndCustomerName() throws Exception {
        JSONObject order = new JSONObject();
        JSONObject attributes = new JSONObject();
        attributes.put("order_id", 12);
        attributes.put("customer_name", "Legacy Guest");
        attributes.put("telephone", "123");
        attributes.put("payment", "stripe");
        attributes.put("order_type", "delivery");
        attributes.put("order_total", 10);
        attributes.put("order_time_is_asap", "false");
        attributes.put("order_date_time", "2026-08-13T16:02:00.000000Z");
        attributes.put("formatted_address", "Wickeder Strasse 46, Dortmund");
        attributes.put("order_menus", new JSONArray());
        attributes.put("order_totals", new JSONArray());
        order.put("attributes", attributes);

        ReceiptTextParser.ReceiptData receipt = ReceiptTextParser.buildReceiptData(
                order, emptyCatalog(), emptyCatalog(), "Shop");

        assertEquals("Bestellung Nr.12", receipt.header.orderNumber);
        assertEquals("Legacy Guest", receipt.customerInfo.name);
        assertEquals("Wickeder Strasse 46, Dortmund", receipt.customerInfo.address);
        assertTrue(receipt.customerInfo.hasGoogleMaps);
    }

    private static JSONObject emptyCatalog() throws Exception {
        JSONObject catalog = new JSONObject();
        catalog.put("data", new JSONArray());
        return catalog;
    }
}

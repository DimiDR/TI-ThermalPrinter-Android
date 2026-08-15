package com.dantsu.thermalprinter.helpClasses;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ReceiptTextParser {
    
    // Receipt data models for Android UI
    public static class ReceiptHeader {
        public String shopName;
        public String orderNumber;
        public String orderType;
        public String payment;
        public String orderTotal;
        public String orderTime;
        public boolean isASAP;
    }
    
    public static class OrderItem {
        public String name;
        public String quantity;
        public String subtotal;
        public String comment;
        public String categoryName;
        public String categoryId;
        public List<OrderOption> options;
        
        public OrderItem() {
            this.options = new ArrayList<>();
        }
    }
    
    public static class OrderOption {
        public String name;
        public String price;
    }
    
    public static class OrderTotal {
        public String title;
        public String value;
    }
    
    public static class CustomerInfo {
        public String name;
        public String telephone;
        public String address;
        public String comment;
        public String googleMapsUrl;
        public boolean hasGoogleMaps;
    }
    
    public static class ReceiptData {
        public ReceiptHeader header;
        public List<OrderItem> items;
        public List<OrderTotal> totals;
        public CustomerInfo customerInfo;
        
        public ReceiptData() {
            this.header = new ReceiptHeader();
            this.items = new ArrayList<>();
            this.totals = new ArrayList<>();
            this.customerInfo = new CustomerInfo();
        }
    }
    
    // Main method to build receipt data from JSON
    public static ReceiptData buildReceiptData(JSONObject order, JSONObject menusData,
                                             JSONObject categoriesData, String shopName) {
        ReceiptData receiptData = new ReceiptData();

        // Build each section independently so one bad field does not wipe the whole receipt
        try {
            buildReceiptHeader(receiptData, order, shopName);
        } catch (JSONException e) {
            Log.e("ReceiptTextParser", "Error building receipt header", e);
        }

        try {
            buildOrderItems(receiptData, order, menusData, categoriesData);
        } catch (JSONException e) {
            Log.e("ReceiptTextParser", "Error building order items", e);
        }

        try {
            buildOrderTotals(receiptData, order);
        } catch (JSONException e) {
            Log.e("ReceiptTextParser", "Error building order totals", e);
        }

        try {
            buildCustomerInfo(receiptData, order);
        } catch (JSONException e) {
            Log.e("ReceiptTextParser", "Error building customer info", e);
        }

        return receiptData;
    }

    private static JSONObject getOrderAttributes(JSONObject order) {
        JSONObject attributes = order.optJSONObject("attributes");
        return attributes != null ? attributes : order;
    }

    private static String resolveOrderId(JSONObject order, JSONObject orderAttributes) {
        String id = order.optString("id", "");
        if (!id.isEmpty() && !"null".equals(id)) {
            return id;
        }
        if (orderAttributes.has("order_id") && !orderAttributes.isNull("order_id")) {
            return String.valueOf(orderAttributes.opt("order_id"));
        }
        return "";
    }

    private static String resolveCustomerName(JSONObject orderAttributes) {
        String customerName = normalizeNullable(orderAttributes.optString("customer_name", ""));
        if (!customerName.isEmpty()) {
            return customerName;
        }
        String firstName = normalizeNullable(orderAttributes.optString("first_name", ""));
        String lastName = normalizeNullable(orderAttributes.optString("last_name", ""));
        return (firstName + " " + lastName).trim();
    }

    private static String resolveQuantity(JSONObject orderMenuObject) {
        if (orderMenuObject.has("qty") && !orderMenuObject.isNull("qty")) {
            return String.valueOf(orderMenuObject.opt("qty"));
        }
        String quantity = normalizeNullable(orderMenuObject.optString("quantity", ""));
        return quantity.isEmpty() ? "1" : quantity;
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return "";
        }
        return value;
    }

    private static String translatePayment(String payment) {
        if ("cod".equals(payment)) {
            return "Barzahlung";
        } else if ("stripe".equals(payment)) {
            return "Bezahlt mit Kreditkarte";
        } else if ("paypalexpress".equals(payment)) {
            return "Bezahlt mit PayPal";
        }
        return payment;
    }

    private static void buildReceiptHeader(ReceiptData receiptData, JSONObject order, String shopName) throws JSONException {
        ReceiptHeader header = receiptData.header;
        header.shopName = shopName;

        JSONObject orderAttributes = getOrderAttributes(order);
        header.orderNumber = "Bestellung Nr." + resolveOrderId(order, orderAttributes);

        header.payment = translatePayment(orderAttributes.optString("payment", ""));

        String orderType = orderAttributes.optString("order_type", "collection");
        if ("delivery".equals(orderType)) {
            header.orderType = "Lieferung";
        } else {
            header.orderType = "Abholung";
        }

        header.orderTotal = String.format(Locale.US, "%.2f", orderAttributes.optDouble("order_total", 0.0));

        String orderTimeIsASAP = orderAttributes.optString("order_time_is_asap", "false");
        String orderDateTime = formatDate(orderAttributes.optString("order_date_time", ""));
        header.isASAP = "true".equals(orderTimeIsASAP);

        if (header.isASAP) {
            header.orderTime = "Sofort: am " + orderDateTime;
        } else {
            header.orderTime = "Gewünschte Zeit: am " + orderDateTime;
        }
    }

    private static void buildOrderItems(ReceiptData receiptData, JSONObject order,
                                      JSONObject menusData, JSONObject categoriesData) throws JSONException {
        JSONObject orderAttributes = getOrderAttributes(order);
        JSONArray orderMenusArray = orderAttributes.optJSONArray("order_menus");
        if (orderMenusArray == null) {
            return;
        }

        JSONArray menusArray = menusData != null ? menusData.optJSONArray("data") : null;
        JSONArray categoriesArray = categoriesData != null ? categoriesData.optJSONArray("data") : null;

        // Add category information to order items when catalog data is available
        if (menusArray != null && categoriesArray != null) {
            for (int i = 0; i < orderMenusArray.length(); i++) {
                JSONObject orderMenuObject = orderMenusArray.getJSONObject(i);
                String menuId = orderMenuObject.optString("menu_id", "");
                JSONObject menu = filterById(menusArray, menuId);

                if (menu != null) {
                    try {
                        String categoryId = menu.getJSONObject("relationships")
                                .getJSONObject("categories")
                                .getJSONArray("data")
                                .getJSONObject(0)
                                .getString("id");
                        JSONObject category = filterById(categoriesArray, categoryId);

                        if (category != null) {
                            String categoryName = category.getJSONObject("attributes").getString("name");
                            orderMenuObject.put("category_id", categoryId);
                            orderMenuObject.put("category_name", categoryName);
                        }
                    } catch (JSONException e) {
                        Log.w("ReceiptTextParser", "Could not resolve category for menu " + menuId);
                    }
                }
            }
        }

        JSONArray sortedOrderMenus = sortJSONArray(orderMenusArray);

        for (int i = 0; i < sortedOrderMenus.length(); i++) {
            JSONObject orderMenuObject = sortedOrderMenus.getJSONObject(i);

            OrderItem item = new OrderItem();
            item.name = orderMenuObject.optString("name", "");
            item.quantity = resolveQuantity(orderMenuObject);
            item.subtotal = formatMoneyValue(orderMenuObject.opt("subtotal"));
            item.comment = normalizeNullable(orderMenuObject.optString("comment", ""));
            item.categoryId = orderMenuObject.optString("category_id", "");
            item.categoryName = orderMenuObject.optString("category_name", "");

            JSONArray menuOptionsArray = orderMenuObject.optJSONArray("menu_options");
            if (menuOptionsArray != null) {
                for (int j = 0; j < menuOptionsArray.length(); j++) {
                    JSONObject menuOptionObject = menuOptionsArray.getJSONObject(j);
                    OrderOption option = new OrderOption();
                    option.name = menuOptionObject.optString("order_option_name",
                            menuOptionObject.optString("name", ""));
                    option.price = formatMoneyValue(menuOptionObject.opt("order_option_price"));
                    item.options.add(option);
                }
            }

            receiptData.items.add(item);
        }
    }

    private static void buildOrderTotals(ReceiptData receiptData, JSONObject order) throws JSONException {
        JSONObject orderAttributes = getOrderAttributes(order);
        JSONArray orderTotalsArray = orderAttributes.optJSONArray("order_totals");
        if (orderTotalsArray == null) {
            return;
        }

        for (int i = 0; i < orderTotalsArray.length(); i++) {
            JSONObject orderTotalObject = orderTotalsArray.getJSONObject(i);
            OrderTotal total = new OrderTotal();
            total.title = orderTotalObject.optString("title", "");
            total.value = formatMoneyValue(orderTotalObject.opt("value"));
            receiptData.totals.add(total);
        }
    }

    private static void buildCustomerInfo(ReceiptData receiptData, JSONObject order) throws JSONException {
        CustomerInfo customerInfo = receiptData.customerInfo;
        JSONObject orderAttributes = getOrderAttributes(order);

        customerInfo.name = resolveCustomerName(orderAttributes);
        if (customerInfo.name.isEmpty()) {
            customerInfo.name = "nicht angegeben";
        }

        customerInfo.telephone = normalizeNullable(orderAttributes.optString("telephone", ""));
        customerInfo.comment = normalizeNullable(orderAttributes.optString("comment", ""));

        String formattedAddress = normalizeNullable(orderAttributes.optString("formatted_address", ""))
                .replaceAll("\\s+", " ")
                .replaceAll(",\\s*,", ",");

        String orderType = orderAttributes.optString("order_type", "collection");
        if (!"delivery".equals(orderType) || formattedAddress.isEmpty()) {
            customerInfo.address = "nicht angegeben";
            customerInfo.hasGoogleMaps = false;
        } else {
            customerInfo.address = formattedAddress;
            customerInfo.googleMapsUrl = "https://www.google.com/maps?q=" +
                    formattedAddress.replaceAll("[,\\s]+", "+");
            customerInfo.hasGoogleMaps = true;
        }

        if (customerInfo.telephone.isEmpty()) {
            customerInfo.telephone = "nicht angegeben";
        }

        if (customerInfo.comment.isEmpty()) {
            customerInfo.comment = "nicht angegeben";
        }
    }
    
    // Helper methods from DocketStringModeler
    private static JSONObject filterById(JSONArray jsonArray, String targetId) {
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String id = jsonObject.getString("id");
                if (id.equals(targetId)) {
                    return jsonObject;
                }
            } catch (JSONException e) {
                Log.e("ReceiptTextParser", "Error filtering by ID", e);
            }
        }
        return null;
    }
    
    private static JSONArray sortJSONArray(JSONArray jsonArray) {
        List<JSONObject> jsonList = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                jsonList.add(jsonArray.getJSONObject(i));
            } catch (JSONException e) {
                Log.e("ReceiptTextParser", "Error sorting JSON array", e);
            }
        }
        
        Collections.sort(jsonList, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject jsonObjectA, JSONObject jsonObjectB) {
                try {
                    String valA = jsonObjectA.optString("category_id", "");
                    String valB = jsonObjectB.optString("category_id", "");
                    return valA.compareTo(valB);
                } catch (Exception e) {
                    return 0;
                }
            }
        });
        
        return new JSONArray(jsonList);
    }
    
    private static String formatMoneyValue(Object rawValue) {
        if (rawValue == null || rawValue == JSONObject.NULL) {
            return "0.00";
        }
        if (rawValue instanceof Number) {
            return String.format(Locale.US, "%.2f", ((Number) rawValue).doubleValue());
        }

        String value = String.valueOf(rawValue).trim();
        if (value.isEmpty() || "null".equals(value)) {
            return "0.00";
        }

        try {
            return String.format(Locale.US, "%.2f", Double.parseDouble(value));
        } catch (NumberFormatException e) {
            // Legacy format: integer cents as string without decimal point
            if (value.length() > 2 && !value.contains(".")) {
                return value.substring(0, value.length() - 2) + "." + value.substring(value.length() - 2);
            }
            return value;
        }
    }

    private static String formatDate(String inputDateString) {
        if (inputDateString == null || inputDateString.isEmpty() || "null".equals(inputDateString)) {
            return "";
        }
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US);
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = inputFormat.parse(inputDateString);
            SimpleDateFormat outputFormat = new SimpleDateFormat("EEE, MMM d, yyyy HH:mm", Locale.GERMANY);
            outputFormat.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
            return outputFormat.format(date);
        } catch (ParseException e) {
            Log.e("ReceiptTextParser", "Error formatting date", e);
            return inputDateString;
        }
    }
    
    // QR Code generation
    public static Bitmap generateQRCode(String content, int width, int height) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height);
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            Log.e("ReceiptTextParser", "Error generating QR code", e);
            return null;
        }
    }
}

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
        
        try {
            // Build header
            buildReceiptHeader(receiptData, order, shopName);
            
            // Build order items
            buildOrderItems(receiptData, order, menusData, categoriesData);
            
            // Build order totals
            buildOrderTotals(receiptData, order);
            
            // Build customer info
            buildCustomerInfo(receiptData, order);
            
        } catch (JSONException e) {
            Log.e("ReceiptTextParser", "Error building receipt data", e);
        }
        
        return receiptData;
    }
    
    private static void buildReceiptHeader(ReceiptData receiptData, JSONObject order, String shopName) throws JSONException {
        ReceiptHeader header = receiptData.header;
        header.shopName = shopName;
        
        JSONObject orderAttributes = order.getJSONObject("attributes");
        String orderId = String.valueOf(orderAttributes.getInt("order_id"));
        header.orderNumber = "Bestellung Nr." + orderId;
        
        // Payment translation
        String payment = orderAttributes.getString("payment");
        if (payment.equals("cod")) {
            header.payment = "Barzahlung";
        } else if (payment.equals("stripe")) {
            header.payment = "Bezahlt mit Kreditkarte";
        } else if (payment.equals("paypalexpress")) {
            header.payment = "Bezahlt mit PayPal";
        } else {
            header.payment = payment;
        }
        
        // Order type translation
        String orderType = orderAttributes.getString("order_type");
        if (orderType.equals("delivery")) {
            header.orderType = "Lieferung";
        } else {
            header.orderType = "Abholung";
        }
        
        // Order total
        header.orderTotal = String.valueOf(orderAttributes.getDouble("order_total"));
        
        // Order time
        String orderTimeIsASAP = orderAttributes.getString("order_time_is_asap");
        String orderDateTime = formatDate(orderAttributes.getString("order_date_time"));
        header.isASAP = orderTimeIsASAP.equals("true");
        
        if (header.isASAP) {
            header.orderTime = "Sofort: am " + orderDateTime;
        } else {
            header.orderTime = "Gewünschte Zeit: am " + orderDateTime;
        }
    }
    
    private static void buildOrderItems(ReceiptData receiptData, JSONObject order, 
                                      JSONObject menusData, JSONObject categoriesData) throws JSONException {
        JSONObject orderAttributes = order.getJSONObject("attributes");
        JSONArray orderMenusArray = orderAttributes.getJSONArray("order_menus");
        JSONArray menusArray = menusData.getJSONArray("data");
        JSONArray categoriesArray = categoriesData.getJSONArray("data");
        
        // Add category information to order items
        for (int i = 0; i < orderMenusArray.length(); i++) {
            JSONObject orderMenuObject = orderMenusArray.getJSONObject(i);
            String menuId = orderMenuObject.getString("menu_id");
            JSONObject menu = filterById(menusArray, menuId);
            
            if (menu != null) {
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
            }
        }
        
        // Sort by category
        JSONArray sortedOrderMenus = sortJSONArray(orderMenusArray);
        
        // Build order items
        for (int i = 0; i < sortedOrderMenus.length(); i++) {
            JSONObject orderMenuObject = sortedOrderMenus.getJSONObject(i);
            
            OrderItem item = new OrderItem();
            item.name = orderMenuObject.getString("name");
            item.quantity = orderMenuObject.getString("quantity");
            item.subtotal = formatStringValue(orderMenuObject.getString("subtotal"));
            item.comment = orderMenuObject.getString("comment");
            item.categoryId = orderMenuObject.getString("category_id");
            item.categoryName = orderMenuObject.getString("category_name");
            
            // Add menu options
            JSONArray menuOptionsArray = orderMenuObject.getJSONArray("menu_options");
            for (int j = 0; j < menuOptionsArray.length(); j++) {
                JSONObject menuOptionObject = menuOptionsArray.getJSONObject(j);
                OrderOption option = new OrderOption();
                option.name = menuOptionObject.getString("order_option_name");
                option.price = formatStringValue(menuOptionObject.getString("order_option_price"));
                item.options.add(option);
            }
            
            receiptData.items.add(item);
        }
    }
    
    private static void buildOrderTotals(ReceiptData receiptData, JSONObject order) throws JSONException {
        JSONObject orderAttributes = order.getJSONObject("attributes");
        JSONArray orderTotalsArray = orderAttributes.getJSONArray("order_totals");
        
        for (int i = 0; i < orderTotalsArray.length(); i++) {
            JSONObject orderTotalObject = orderTotalsArray.getJSONObject(i);
            OrderTotal total = new OrderTotal();
            total.title = orderTotalObject.getString("title");
            total.value = formatStringValue(orderTotalObject.getString("value"));
            receiptData.totals.add(total);
        }
    }
    
    private static void buildCustomerInfo(ReceiptData receiptData, JSONObject order) throws JSONException {
        CustomerInfo customerInfo = receiptData.customerInfo;
        JSONObject orderAttributes = order.getJSONObject("attributes");
        
        customerInfo.name = orderAttributes.getString("customer_name");
        customerInfo.telephone = orderAttributes.getString("telephone");
        customerInfo.comment = orderAttributes.getString("comment");
        
        String formattedAddress = orderAttributes.getString("formatted_address")
                .replaceAll("\\s+", " ")
                .replaceAll(",\\s*,", ",");
        
        if ("null".equals(formattedAddress) || formattedAddress.isEmpty()) {
            customerInfo.address = "nicht angegeben";
            customerInfo.hasGoogleMaps = false;
        } else {
            customerInfo.address = formattedAddress;
            customerInfo.googleMapsUrl = "https://www.google.com/maps?q=" +
                    orderAttributes.getString("formatted_address").replaceAll("[,\\s]+", "+");
            customerInfo.hasGoogleMaps = true;
        }
        
        if ("null".equals(customerInfo.telephone) || customerInfo.telephone.isEmpty()) {
            customerInfo.telephone = "nicht angegeben";
        }
        
        if ("null".equals(customerInfo.comment) || customerInfo.comment.isEmpty()) {
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
                    String valA = jsonObjectA.getString("category_id");
                    String valB = jsonObjectB.getString("category_id");
                    return valA.compareTo(valB);
                } catch (JSONException e) {
                    return 0;
                }
            }
        });
        
        return new JSONArray(jsonList);
    }
    
    private static String formatStringValue(String value) {
        if (value.length() > 2) {
            return value.substring(0, value.length() - 2);
        }
        return value;
    }
    
    private static String formatDate(String inputDateString) {
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

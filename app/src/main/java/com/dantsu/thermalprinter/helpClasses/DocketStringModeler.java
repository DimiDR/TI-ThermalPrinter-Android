package com.dantsu.thermalprinter.helpClasses;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.util.DisplayMetrics;

import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.textparser.PrinterTextParserImg;
import com.dantsu.thermalprinter.R;
import com.dantsu.thermalprinter.async.AsyncEscPosPrinter;

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
import java.util.Set;
import java.util.TimeZone;

public class DocketStringModeler {
    private static Set<String> printedOrders;
    String printOutput = "";
    String printHeader = "";
    String printOrder = "";
    String printAllCosts = "";
    String printCustomer = "";
    String printPayment = "";
    String shop_name = "";
    boolean isGoogleMaps = true;
    JSONObject orders;
    MediaPlayer mediaPlayer;
    JSONObject categories;
    String customerReceipt = "Placeholder";
    JSONObject  jsonObjectMenus;
    JSONObject jsonObjectCategories;


    public String startPrintingReceipt(JSONObject dataObject, JSONObject  jsonObjectMenus, JSONObject jsonObjectCategories, MediaPlayer mediaPlayer, String shop_name){
        this.orders = dataObject;
        this.mediaPlayer = mediaPlayer;
        this.jsonObjectMenus = jsonObjectMenus;
        this.jsonObjectCategories = jsonObjectCategories;
        this.shop_name = shop_name;

        // add category id and category name to orders for printing
        addInformation();
        printDocketCustomerReceipt();
        return customerReceipt;
    }

    public String startPrintingKitchen(JSONObject dataObject, JSONObject  jsonObjectMenus, JSONObject jsonObjectCategories, MediaPlayer mediaPlayer, String shop_name){
        this.orders = dataObject;
        this.mediaPlayer = mediaPlayer;
        this.jsonObjectMenus = jsonObjectMenus;
        this.jsonObjectCategories = jsonObjectCategories;
        this.shop_name = shop_name;

        // add category id and category name to orders for printing
        addInformation();
        printDocketKitchenReceipt();
        return customerReceipt;
    }

public JSONObject addInformation(){
    try {
        JSONObject orderAttributes = orders.getJSONObject("attributes");
        JSONArray order_menus_array = orderAttributes.getJSONArray("order_menus");
        JSONArray menus_array = jsonObjectMenus.getJSONArray("data");
        JSONArray categories_array = jsonObjectCategories.getJSONArray("data");

        for (int i = 0; i < order_menus_array.length(); i++) {
            JSONObject order_menus_object = order_menus_array.getJSONObject(i);
            String menu_id = order_menus_object.getString("menu_id");
            JSONObject menu = filterById(menus_array, menu_id);
            // TODO: handle error if no category assigned
            String category_id = menu.getJSONObject("relationships").
                    getJSONObject("categories").getJSONArray("data").
                    getJSONObject(0).getString("id");
            // TODO: handle error if category is not part of the location
            JSONObject category = filterById(categories_array, category_id);
            String category_name = category.getJSONObject("attributes").getString("name");
            order_menus_object.put("category_id", category_id);
            order_menus_object.put("category_name", category_name);
        }
    } catch (JSONException e) {
        throw new RuntimeException(e);
    }
    return orders;
}

    public JSONObject filterById(JSONArray jsonArray, String targetId) {
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String id = null;
                id = jsonObject.getString("id");
                if (id.equals(targetId)) {
                    return jsonObject;
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        return null; // Return null or a default JSONObject if no match is found
    }

    public JSONArray sortJSONArray(JSONArray jsonArray) {
        List<JSONObject> jsonList = new ArrayList<JSONObject>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                jsonList.add(jsonArray.getJSONObject(i));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        Collections.sort(jsonList, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject jsonObjectA, JSONObject jsonObjectB) {
                String valA = new String();
                String valB = new String();

                try {
                    valA = (String) jsonObjectA.get("category_id");
                    valB = (String) jsonObjectB.get("category_id");
                }
                catch (JSONException e) {
                    // do something
                }

                return valA.compareTo(valB);
                // if you want to change the sort order, simply use the following:
                // return -valA.compareTo(valB);
            }
        });

        JSONArray sortedJsonArray = new JSONArray(jsonList);

        return sortedJsonArray;
    }

    private void printDocketCustomerReceipt() {
        try {
                isGoogleMaps = true;
                JSONObject orderAttributes = orders.getJSONObject("attributes");
                String payment = orderAttributes.optString("payment", "");
                String order_type = orderAttributes.optString("order_type", "collection");
                String order_time_is_asap = orderAttributes.optString("order_time_is_asap", "false");
                String order_date_time = FormatDate(orderAttributes.optString("order_date_time", ""));
                String orderId = resolveOrderId(orders, orderAttributes);
                String orderTotal = String.format(Locale.US, "%.2f", orderAttributes.optDouble("order_total", 0.0));
                //payment translation
                if (payment.equals("cod")) {
                    payment = "Barzahlung";
                } else if (payment.equals("stripe")) {
                    payment = "Bezahlt mit Kreditkarte";
                } else if (payment.equals("paypalexpress")) {
                    payment = "Bezahlt mit PayPal";
                }
                //delivery translation
                if (order_type.equals("delivery")) {
                    order_type = "Lieferung";
                } else {
                    order_type = "Abholung";
                }
                // check if delivery is later, print bigger if delivery later
                String order_type_time = "";
                if (order_time_is_asap.equals("false")) {
                    order_type_time = "[L]<b>Gewünschte Zeit: " + " am " + order_date_time + "</b>\n";
                } else {
                    order_type_time = "[L] Sofort: " + " am " + order_date_time + "\n";
                }
                printHeader = "[C]<u><font size='big'>" + shop_name + "</font></u>\n" +
                        "[L]<font size='big'>Bestellung Nr." + orderId + "</font>\n" +
//                        "[L]\n" +
                        "[L]<b>" + order_type + " - " + payment + " - <u type='double'>" +
                        orderTotal + "</u>€ </b>\n" +
                        order_type_time +
//                        "[C]\n" +
                        "[L]====================================\n";

                // menu entries, need to sort by category so on the print, a category is visible only once
                JSONArray order_menus_array = sortJSONArray(orderAttributes.getJSONArray("order_menus"));
                String category_id_help = "";
                for (int j = 0; j < order_menus_array.length(); j++) {
                    JSONObject order_menus_object = order_menus_array.getJSONObject(j);
                    String menusName = order_menus_object.optString("name", "");
                    String menusSubtotal = FormatMoneyValue(order_menus_object.opt("subtotal"));
                    String menusQuantity = resolveQuantity(order_menus_object);
                    String menusComment = normalizeNullable(order_menus_object.optString("comment", ""));
                    String category_id = order_menus_object.optString("category_id", "");
                    String category_name = order_menus_object.optString("category_name", "");
                    if (!category_id.equals(category_id_help)) {
                        printOrder += "[L]__________________ \n";
                        if (!category_name.isEmpty()) {
                            printOrder += "[L]<b>" + category_name + "</b> \n";
                        }
                        category_id_help = category_id;
                    }

                    printOrder += "[L]<b> " + menusQuantity + "x - " + menusName + ", [R]"
                            + menusSubtotal + "€</b> \n";
                    if (!menusComment.isEmpty()) {
                        printOrder += "[L]  Kommentar: " + menusComment + "\n";
                    }
                    // menu options
                    JSONArray menu_options_array = order_menus_object.optJSONArray("menu_options");
                    if (menu_options_array != null) {
                        for (int k = 0; k < menu_options_array.length(); k++) {
                             JSONObject menu_option_object = menu_options_array.getJSONObject(k);
                            String order_option_name = menu_option_object.optString("order_option_name",
                                    menu_option_object.optString("name", ""));
                            String order_option_price = FormatMoneyValue(menu_option_object.opt("order_option_price"));
                            printOrder += "[L]  Option: " + order_option_name + ", [R]" + order_option_price + "€\n";
                        }
                    }
                }
                //all costs
                JSONArray order_totals_array = orderAttributes.optJSONArray("order_totals");
                printAllCosts += "[L]====================================\n";
                if (order_totals_array != null) {
                    for (int j = 0; j < order_totals_array.length(); j++) {
                        JSONObject order_totals_object = order_totals_array.getJSONObject(j);
                        // assumption, that the JSON is already sorted by priority. The same sequence will be taken
                        String title = order_totals_object.optString("title", "");
                        String value = FormatMoneyValue(order_totals_object.opt("value"));
                        printAllCosts += "[L]" + title + "[R]" + value + "€\n";
                    }
                }
                //customer information
                String customer_name = resolveCustomerName(orderAttributes);
                if (customer_name.isEmpty()) {
                    customer_name = "nicht angegeben";
                }
                String telephone = normalizeNullable(orderAttributes.optString("telephone", ""));
                String comment = normalizeNullable(orderAttributes.optString("comment", ""));
                String formatted_address = normalizeNullable(orderAttributes.optString("formatted_address", ""))
                        .replaceAll("\\s+", " ").replaceAll(",\\s*,", ",");
                String google_api_url = "https://www.google.com/maps?q=" +
                        formatted_address.replaceAll("[,\\s]+", "+");
                String rawOrderType = orderAttributes.optString("order_type", "collection");
                if (!"delivery".equals(rawOrderType) || formatted_address.isEmpty()) {
                    formatted_address = "nicht angegeben";
                    isGoogleMaps = false;
                }
                if (telephone.isEmpty()) {
                    telephone = "nicht angegeben";
                }
                if (comment.isEmpty()) {
                    comment = "nicht angegeben";
                }

                printCustomer += "[L]Name: " + customer_name + "\n" +
                        "[L]Telefon: " + telephone + "\n" +
                        "[L]Adresse: " + formatted_address + "\n" +
                        "[L]Kommentar: " + comment + "\n";
                if (isGoogleMaps) {
                    printCustomer += "[C]Google Adresse Scannen \n" +
                            "[C]<qrcode size='20'>" + google_api_url + "</qrcode> \n";
                }
                // create full print String
                printOutput = printHeader +
                        printOrder +
                        printAllCosts +
                        printPayment +
                        "[L]====================================\n" +
                        "[L]Kundeninformation\n" +
                        printCustomer +
                        "[C]<b>Dies ist keine Rechnung</b>";
                this.customerReceipt = printOutput;
                printOutput = "";
                printHeader = "";
                printOrder = "";
                printAllCosts = "";
                printCustomer = "";
                printPayment = "";
//            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
//        return printOutput;
    }

    private void printDocketKitchenReceipt() {
        try {
            JSONObject orderAttributes = orders.getJSONObject("attributes");
            String order_time_is_asap = orderAttributes.optString("order_time_is_asap", "false");
            String order_date_time = FormatDate(orderAttributes.optString("order_date_time", ""));
            String orderId = resolveOrderId(orders, orderAttributes);
            // check if delivery is later, print bigger if delivery later
            String order_type_time = "";
            if (order_time_is_asap.equals("false")) {
                order_type_time = "<b>Gewünschte Zeit: " + " am " + order_date_time + "</b>\n";
            } else {
                order_type_time = "Sofort: " + " am " + order_date_time + "\n";
            }
            printHeader = "[C]<u><font size='big'>" + shop_name + "</font></u>\n" +
                    "[L]<font size='big'>Bestellung Nr." + orderId + "</font>\n" +
                    "[L]<font size='big'>" + order_type_time + "</font>\n";

            // menu entries, need to sort by category so on the print, a category is visible only once
            JSONArray order_menus_array = sortJSONArray(orderAttributes.getJSONArray("order_menus"));
            String category_id_help = "";
            for (int j = 0; j < order_menus_array.length(); j++) {
                JSONObject order_menus_object = order_menus_array.getJSONObject(j);
                String menusName = order_menus_object.optString("name", "");
                String menusQuantity = resolveQuantity(order_menus_object);
                String menusComment = normalizeNullable(order_menus_object.optString("comment", ""));
                String category_id = order_menus_object.optString("category_id", "");
                String category_name = order_menus_object.optString("category_name", "");
                if (!category_id.equals(category_id_help)) {
                    printOrder += "[L]__________________ \n";
                    if (!category_name.isEmpty()) {
                        printOrder += "[L]<font size='big'>" + category_name + "</font> \n";
                    }
                    category_id_help = category_id;
                }

                printOrder += "[L]<font size='big'>" + menusQuantity + "x - " + menusName + "</font>\n";
                if (!menusComment.isEmpty()) {
                    printOrder += "[L]<font size='big'>  Kommentar: " + menusComment + "</font>\n";
                }
                // menu options
                JSONArray menu_options_array = order_menus_object.optJSONArray("menu_options");
                if (menu_options_array != null) {
                    for (int k = 0; k < menu_options_array.length(); k++) {
                        JSONObject menu_option_object = menu_options_array.getJSONObject(k);
                        String order_option_name = menu_option_object.optString("order_option_name",
                                menu_option_object.optString("name", ""));
                        printOrder += "[L]<font size='big'>  Option: " + order_option_name + "</font>\n";
                    }
                }
            }
            // create full print String
            printOutput = printHeader +
                    printOrder +
                    printAllCosts;
            this.customerReceipt = printOutput;
            printOutput = "";
            printHeader = "";
            printOrder = "";
            printAllCosts = "";
            printCustomer = "";
            printPayment = "";
        } catch (JSONException e) {
            e.printStackTrace();
        }
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

    private String FormatMoneyValue(Object rawValue) {
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
            if (value.length() > 2 && !value.contains(".")) {
                return value.substring(0, value.length() - 2) + "." + value.substring(value.length() - 2);
            }
            return value;
        }
    }

    public static JSONArray filterPrintableOrders(JSONArray dataArray, Set<String> printedOrders) throws JSONException {
        return OrderStatusMapper.filterPrintable(dataArray, printedOrders);
    }

    private String FormatDate(String inputDateString) {
        String outputDateString = "";
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US);
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = inputFormat.parse(inputDateString);
            SimpleDateFormat outputFormat = new SimpleDateFormat("EEE, MMM d, yyyy HH:mm", Locale.GERMANY);
            outputFormat.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
            outputDateString = outputFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return outputDateString;
    }

    @SuppressLint("SimpleDateFormat")
    public static String TITestPrinter(boolean normal_click, DeviceConnection selectedDevice, Context context) {
        SimpleDateFormat format = new SimpleDateFormat("'on' yyyy-MM-dd 'at' HH:mm:ss");
        PrinterSettings settings = new PrinterSettings(context);
        PrinterSettings.PaperSize paper = settings.getPaperSize();
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(selectedDevice, paper.dpi, paper.widthMm, paper.charsPerLine);
        String print_info;
        if (normal_click) {
            print_info = "[C]Test\n" +
                    "[C]Ausdruck ae: ä ö ü ß Ä Ö Ü\n" +
                    "[C]Preis: 9,99€\n" +
                    "[C]<u><font size='big'>DRUCKER TEST ERFOLGREICH</font></u>";
        } else {
            print_info = "[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, context.getApplicationContext().getResources().getDrawableForDensity(R.drawable.logo, DisplayMetrics.DENSITY_MEDIUM)) + "</img>\n" +
                    "[L]\n" +
                    "[C]<u><font size='big'>TEST ÖRDER N°045</font></u>\n" +
                    "[L]\n" +
                    "[C]<u type='double'>" + format.format(new Date()) + "</u>\n" +
                    "[C]\n" +
                    "[C]================================\n" +
                    "[L]\n" +
                    "[L]<b>BEAUTIFUL SHIRT</b>[R]9.99€\n" +
                    "[L]  + Size : S\n" +
                    "[L]\n" +
                    "[L]<b>AWESOME HAT</b>[R]24.99€\n" +
                    "[L]  + Size : 57/58\n" +
                    "[L]\n" +
                    "[C]--------------------------------\n" +
                    "[R]TOTAL PRICE :[R]34.98€\n" +
                    "[R]TAX :[R]4.23€\n" +
                    "[L]\n" +
                    "[C]================================\n" +
                    "[L]\n" +
                    "[L]<u><font color='bg-black' size='tall'>Customer :</font></u>\n" +
                    "[L]Raymond DUPONT\n" +
                    "[L]5 rue des girafes\n" +
                    "[L]31547 PERPETES\n" +
                    "[L]Tel : +33801201456\n" +
                    "\n" +
                    "[C]<barcode type='ean13' height='10'>831254784551</barcode>\n" +
                    "[L]\n" +
                    "[C]<qrcode size='20'>https://jandiweb.de/</qrcode>\n";
        }
        return print_info;
    }

}
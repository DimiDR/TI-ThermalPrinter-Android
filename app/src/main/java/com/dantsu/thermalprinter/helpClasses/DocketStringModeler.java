package com.dantsu.thermalprinter.helpClasses;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.util.DisplayMetrics;

import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.textparser.PrinterTextParserImg;
import com.dantsu.thermalprinter.R;
import com.dantsu.thermalprinter.async.AsyncEscPosPrinter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

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


    public String startPrinting(JSONObject dataObject,   JSONObject  jsonObjectMenus, JSONObject jsonObjectCategories, MediaPlayer mediaPlayer, String shop_name){
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
                mediaPlayer.start(); //always play if new order is available
                String orderID = orders.getString("id");
                JSONObject orderAttributes = orders.getJSONObject("attributes");
                String payment = orderAttributes.getString("payment");//stripe, cod, paypalexpress
                String order_type = orderAttributes.getString("order_type");
                String order_time_is_asap = orderAttributes.getString("order_time_is_asap");
                String order_date_time = FormatDate(orderAttributes.getString("order_date_time"));
                String orderId = String.valueOf(orderAttributes.getInt("order_id"));
                String orderTotal = String.valueOf(orderAttributes.getDouble("order_total"));
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
                    order_type_time = "[C]<b>Gewünschte Zeit: " + " am " + order_date_time + "</b>\n";
                } else {
                    order_type_time = "[C] Sofort: " + " am " + order_date_time + "\n";
                }
                printHeader = "[C]<u><font size='big'>" + shop_name + "</font></u>\n" +
                        "[L]<font size='big'>Bestellung Nr." + orderId + "</font>\n" +
                        "[L]\n" +
                        "[L]<b>" + order_type + " - " + payment + " - <u type='double'>" +
                        orderTotal + "</u>€ </b>\n" +
                        order_type_time +
                        "[C]\n" +
                        "[L]====================================\n";

                // menu entries, need to sort by category so on the print, a category is visible only once
                JSONArray order_menus_array = sortJSONArray(orderAttributes.getJSONArray("order_menus"));
                String category_id_help = "";
                for (int j = 0; j < order_menus_array.length(); j++) {
                    JSONObject order_menus_object = order_menus_array.getJSONObject(j);
                    String menusName = order_menus_object.getString("name");
                    String menusSubtotal = FormatStringValue(order_menus_object.getString("subtotal"));
                    String menusQuantity = order_menus_object.getString("quantity");
                    String menusComment = order_menus_object.getString("comment");
                    String category_id = order_menus_object.getString("category_id");
                    String category_name = order_menus_object.getString("category_name");
                    if (!category_id.equals(category_id_help)) {
                        printOrder += "[L]__________________ \n";
                        printOrder += "[L]<b>" + category_name + "</b> \n";
                        category_id_help = category_id;
                    }

                    printOrder += "[L]<b> " + menusQuantity + "x - " + menusName + ", [R]"
                            + menusSubtotal + "€</b> \n";
                    if (menusComment != null && !menusComment.isEmpty()) {
                        printOrder += "[L]  Kommentar: " + menusComment + "\n";
                    }
                    // menu options
                    JSONArray menu_options_array = order_menus_object.getJSONArray("menu_options");
                    for (int k = 0; k < menu_options_array.length(); k++) {
                         JSONObject menu_option_object = menu_options_array.getJSONObject(k);
                        String order_option_name = menu_option_object.getString("order_option_name");
                        String order_option_price = FormatStringValue(menu_option_object.getString("order_option_price"));
                        printOrder += "[L]  Option: " + order_option_name + ", [R]" + order_option_price + "€\n";
                    }
                }
                //all costs
                JSONArray order_totals_array = orderAttributes.getJSONArray("order_totals");
                printAllCosts += "[L]====================================\n";
                for (int j = 0; j < order_totals_array.length(); j++) {
                    JSONObject order_totals_object = order_totals_array.getJSONObject(j);
                    // assumption, that the JSON is already sorted by priority. The same sequence will be taken
                    String title = order_totals_object.getString("title");
                    String value = FormatStringValue(order_totals_object.getString("value"));
                    printAllCosts += "[L]" + title + "[R]" + value + "€\n";
                }
                //customer information
                String customer_name = orderAttributes.getString("customer_name");
                String telephone = orderAttributes.getString("telephone");
                String comment = orderAttributes.getString("comment");
                String formatted_address = orderAttributes.getString("formatted_address")
                        .replaceAll("\\s+", " ").replaceAll(",\\s*,", ",");
                String google_api_url = "https://www.google.com/maps?q=" +
                        orderAttributes.getString("formatted_address").replaceAll("[,\\s]+", "+");
                if (formatted_address == "null" || formatted_address.isEmpty()) {
                    formatted_address = "nicht angegeben";
                    isGoogleMaps = false;
                }
                if (telephone == "null" || telephone.isEmpty()) {
                    telephone = "nicht angegeben";
                }
                if (comment == "null" || comment.isEmpty()) {
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
            mediaPlayer.start(); //always play if new order is available
            String orderID = orders.getString("id");
            JSONObject orderAttributes = orders.getJSONObject("attributes");
            String payment = orderAttributes.getString("payment");//stripe, cod, paypalexpress
            String order_type = orderAttributes.getString("order_type");
            String order_time_is_asap = orderAttributes.getString("order_time_is_asap");
            String order_date_time = FormatDate(orderAttributes.getString("order_date_time"));
            String orderId = String.valueOf(orderAttributes.getInt("order_id"));
            String orderTotal = String.valueOf(orderAttributes.getDouble("order_total"));
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
                order_type_time = "[C]<b>Gewünschte Zeit: " + " am " + order_date_time + "</b>\n";
            } else {
                order_type_time = "[C] Sofort: " + " am " + order_date_time + "\n";
            }
            printHeader = "[C]<u><font size='big'>" + shop_name + "</font></u>\n" +
                    "[L]<font size='big'>Bestellung Nr." + orderId + "</font>\n" +
                    "[L]\n" +
                    "[L]<b>" + order_type + " - " + payment + " - <u type='double'>" +
                    orderTotal + "</u>€ </b>\n" +
                    order_type_time +
                    "[C]\n" +
                    "[L]====================================\n";

            // menu entries, need to sort by category so on the print, a category is visible only once
            JSONArray order_menus_array = sortJSONArray(orderAttributes.getJSONArray("order_menus"));
            String category_id_help = "";
            for (int j = 0; j < order_menus_array.length(); j++) {
                JSONObject order_menus_object = order_menus_array.getJSONObject(j);
                String menusName = order_menus_object.getString("name");
                String menusSubtotal = FormatStringValue(order_menus_object.getString("subtotal"));
                String menusQuantity = order_menus_object.getString("quantity");
                String menusComment = order_menus_object.getString("comment");
                String category_id = order_menus_object.getString("category_id");
                String category_name = order_menus_object.getString("category_name");
                if (!category_id.equals(category_id_help)) {
                    printOrder += "[L]__________________ \n";
                    printOrder += "[L]<b>" + category_name + "</b> \n";
                    category_id_help = category_id;
                }

                printOrder += "[L]<b> " + menusQuantity + "x - " + menusName + ", [R]"
                        + menusSubtotal + "€</b> \n";
                if (menusComment != null && !menusComment.isEmpty()) {
                    printOrder += "[L]  Kommentar: " + menusComment + "\n";
                }
                // menu options
                JSONArray menu_options_array = order_menus_object.getJSONArray("menu_options");
                for (int k = 0; k < menu_options_array.length(); k++) {
                    JSONObject menu_option_object = menu_options_array.getJSONObject(k);
                    String order_option_name = menu_option_object.getString("order_option_name");
                    String order_option_price = FormatStringValue(menu_option_object.getString("order_option_price"));
                    printOrder += "[L]  Option: " + order_option_name + ", [R]" + order_option_price + "€\n";
                }
            }
            //all costs
            JSONArray order_totals_array = orderAttributes.getJSONArray("order_totals");
            printAllCosts += "[L]====================================\n";
            for (int j = 0; j < order_totals_array.length(); j++) {
                JSONObject order_totals_object = order_totals_array.getJSONObject(j);
                // assumption, that the JSON is already sorted by priority. The same sequence will be taken
                String title = order_totals_object.getString("title");
                String value = FormatStringValue(order_totals_object.getString("value"));
                printAllCosts += "[L]" + title + "[R]" + value + "€\n";
            }
            //customer information
            String customer_name = orderAttributes.getString("customer_name");
            String telephone = orderAttributes.getString("telephone");
            String comment = orderAttributes.getString("comment");
            String formatted_address = orderAttributes.getString("formatted_address")
                    .replaceAll("\\s+", " ").replaceAll(",\\s*,", ",");
            String google_api_url = "https://www.google.com/maps?q=" +
                    orderAttributes.getString("formatted_address").replaceAll("[,\\s]+", "+");
            if (formatted_address == "null" || formatted_address.isEmpty()) {
                formatted_address = "nicht angegeben";
                isGoogleMaps = false;
            }
            if (telephone == "null" || telephone.isEmpty()) {
                telephone = "nicht angegeben";
            }
            if (comment == "null" || comment.isEmpty()) {
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

    private String FormatStringValue(String value) {
        String output = value.substring(0, value.length() - 2);
        return output;
    }

    public static JSONArray filterPrintableOrders(JSONArray dataArray, Set<String>  printedOrders) throws JSONException {
        //checks for initial status of the order. Only orders with status initial will be printed.
        JSONArray printableOrders = new JSONArray();

        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject order = dataArray.getJSONObject(i);
            String orderId = order.getString("id");

            //TODO add a call to get the categories of order item and add to json
            // this can be used to order the orders by category on the print
            // add only the first category to the json to prevent ordering in two different categories
            // add to order_menus[i].category
            // make a call to get the categories

////        print special order ID for testing TODO deactivate
//            if (orderId.equals("70")) {
//                printableOrders.put(order);
////                return printableOrders;
//            }

//TODO: activate
            if (!printedOrders.contains(orderId)) { //TODO printedOrders is null
                JSONObject attributes = order.getJSONObject("attributes");
                JSONObject status = attributes.getJSONObject("status");
                int statusId = status.getInt("status_id"); // initial order

                if (statusId == 1) {
                    printableOrders.put(order);
                }
            }

        }
        return printableOrders;
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
    public static String TITestPrinter(boolean normal_click, BluetoothConnection selectedDevice, Context context) {
        SimpleDateFormat format = new SimpleDateFormat("'on' yyyy-MM-dd 'at' HH:mm:ss");
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(selectedDevice, 203, 48f, 32);
        String print_info;
        if (normal_click) {
            print_info = "[C] Test \n" +
                    "[C] Ausdruck \n" +
                    "[C]<u><font size='big'>DRUCKER TEST ERFOLGREICH</font></u>";
        } else {
            print_info = "[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, context.getApplicationContext().getResources().getDrawableForDensity(R.drawable.logo, DisplayMetrics.DENSITY_MEDIUM)) + "</img>\n" +
                    "[L]\n" +
                    "[C]<u><font size='big'>TEST ORDER N°045</font></u>\n" +
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
package com.dantsu.thermalprinter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
import com.dantsu.escposprinter.textparser.PrinterTextParserImg;
import com.dantsu.thermalprinter.async.AsyncBluetoothEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrint;
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
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TimeZone;
import java.util.HashSet;
import java.util.Set;

import com.dantsu.thermalprinter.helpClasses.IdManager;
import com.dantsu.thermalprinter.helpClasses.MyWakeLockManager;
import com.dantsu.thermalprinter.helpClasses.UserUtils;

import android.net.Uri;

public class MainActivity extends AppCompatActivity {
    //TEST BRANCH
    //set of orders IDs, which already been printed
    private static Set<String> printedOrders = new HashSet<>();
    private static Context context;
    private Button button_ti_print;
    //    private final String domain = "https://dimitrir14.sg-host.com"; //TODO change
    private final String domain = "https://bestellen.primavera-pizza-wickede.de";
    private final String tiOrdersEndpointURL = domain + "/api/orders?sort=order_id desc&pageLimit=50";
    private final String tiDashboardURL = domain + "/admin";
    private final String tiKitchenViewURL = domain + "/admin/thoughtco/kitchendisplay/summary/view/1";
    private final String tiUpdates = "https://jandiweb.de/integration/";
    private final String tiLandingPage = "https://primavera-pizza-wickede.de/";
    private static List<UserUtils.User> users;
    MediaPlayer mediaPlayer;
    long period = 60 * 1000; // set to 60000 for 1 minute //TODO change

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button = (Button) this.findViewById(R.id.button_bluetooth_browse);
        button.setOnClickListener(view -> browseBluetoothDevice());
        // TCP & USB printing deactivated
        //button = (Button) findViewById(R.id.button_bluetooth);
        //button.setOnClickListener(view -> printBluetooth());
        //button = (Button) this.findViewById(R.id.button_usb);
        //button.setOnClickListener(view -> printUsb());
        //button = (Button) this.findViewById(R.id.button_tcp);
        //button.setOnClickListener(view -> printTcp());
        button_ti_print = this.findViewById(R.id.button_ti_print_monitoring);
        button_ti_print.setOnClickListener(view -> tiPrintMonitoring());
        button = (Button) this.findViewById(R.id.button_ti_clear_ids);
        button.setOnClickListener(view -> tiClearIds());
        button = (Button) this.findViewById(R.id.button_ti_kitchen_view);
        button.setOnClickListener(view -> openWebpage(tiKitchenViewURL));
        button = (Button) this.findViewById(R.id.button_ti_dashboard);
        button.setOnClickListener(view -> openWebpage(tiDashboardURL));
        button = (Button) this.findViewById(R.id.button_ti_updates);
        button.setOnClickListener(view -> openWebpage(tiUpdates));
        button = (Button) this.findViewById(R.id.button_ti_landing_page);
        button.setOnClickListener(view -> openWebpage(tiLandingPage));
        button = (Button) this.findViewById(R.id.button_ti_testprint);
        button.setOnClickListener(view -> TITestPrinter(true));
        button.setOnLongClickListener(view -> TITestPrinter(false));
        button = (Button) this.findViewById(R.id.button_ti_login);
        button.setOnClickListener(view -> LoginUser());
        //get the already printed IDs or orders
        context = this;
        printedOrders = IdManager.getIds(context);
        // play new order sound
        mediaPlayer = MediaPlayer.create(context, R.raw.newordersound);
        // get valid users
        users = UserUtils.getUsers(this);
    }

    private String[] arrayOf(String postNotifications) {
        String[] strArray = new String[1];
        strArray[0] = postNotifications;
        return strArray;
    }


    /*==============================================================================================
    ======================================BLUETOOTH PART============================================
    ==============================================================================================*/

    public interface OnBluetoothPermissionsGranted {
        void onPermissionsGranted();
    }

    public static final int PERMISSION_BLUETOOTH = 1;
    public static final int PERMISSION_BLUETOOTH_ADMIN = 2;
    public static final int PERMISSION_BLUETOOTH_CONNECT = 3;
    public static final int PERMISSION_BLUETOOTH_SCAN = 4;

    public OnBluetoothPermissionsGranted onBluetoothPermissionsGranted;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            switch (requestCode) {
                case MainActivity.PERMISSION_BLUETOOTH:
                case MainActivity.PERMISSION_BLUETOOTH_ADMIN:
                case MainActivity.PERMISSION_BLUETOOTH_CONNECT:
                case MainActivity.PERMISSION_BLUETOOTH_SCAN:
                    this.checkBluetoothPermissions(this.onBluetoothPermissionsGranted);
                    break;
            }
        }
    }

    public void checkBluetoothPermissions(OnBluetoothPermissionsGranted onBluetoothPermissionsGranted) {
        this.onBluetoothPermissionsGranted = onBluetoothPermissionsGranted;
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, MainActivity.PERMISSION_BLUETOOTH);
        } else if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, MainActivity.PERMISSION_BLUETOOTH_ADMIN);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, MainActivity.PERMISSION_BLUETOOTH_CONNECT);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, MainActivity.PERMISSION_BLUETOOTH_SCAN);
        } else {
            this.onBluetoothPermissionsGranted.onPermissionsGranted();
        }
    }

    private BluetoothConnection selectedDevice;

    public void browseBluetoothDevice() {
        this.checkBluetoothPermissions(() -> {
            final BluetoothConnection[] bluetoothDevicesList = (new BluetoothPrintersConnections()).getList();

            if (bluetoothDevicesList != null) {
                final String[] items = new String[bluetoothDevicesList.length + 1];
                items[0] = "Default printer";
                int i = 0;
                for (BluetoothConnection device : bluetoothDevicesList) {
                    items[++i] = device.getDevice().getName();
                }

                AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                alertDialog.setTitle("Bluetooth printer selection");
                alertDialog.setItems(
                        items,
                        (dialogInterface, i1) -> {
                            int index = i1 - 1;
                            if (index == -1) {
                                selectedDevice = null;
                            } else {
                                selectedDevice = bluetoothDevicesList[index];
                            }
                            Button button = (Button) findViewById(R.id.button_bluetooth_browse);
                            button.setText(items[i1]);
                        }
                );

                AlertDialog alert = alertDialog.create();
                alert.setCanceledOnTouchOutside(false);
                alert.show();
            }
        });

    }

    //    public void printBluetooth() {
//        this.checkBluetoothPermissions(() -> {
//            new AsyncBluetoothEscPosPrint(
//                    this,
//                    new AsyncEscPosPrint.OnPrintFinished() {
//                        @Override
//                        public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
//                            Log.e("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : An error occurred !");
//                        }
//
//                        @Override
//                        public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
//                            Log.i("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : Print is finished !");
//                        }
//                    }
//            )
//                    .execute(this.getAsyncEscPosPrinter(selectedDevice));
//        });
//    }
//    /*==============================================================================================
//    ===========================================USB PART=============================================
//    ==============================================================================================*/
//
//    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
//    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
//        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//            if (MainActivity.ACTION_USB_PERMISSION.equals(action)) {
//                synchronized (this) {
//                    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
//                    UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
//                        if (usbManager != null && usbDevice != null) {
//                            new AsyncUsbEscPosPrint(
//                                    context,
//                                    new AsyncEscPosPrint.OnPrintFinished() {
//                                        @Override
//                                        public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
//                                            Log.e("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : An error occurred !");
//                                        }
//
//                                        @Override
//                                        public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
//                                            Log.i("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : Print is finished !");
//                                        }
//                                    }
//                            )
//                                    .execute(getAsyncEscPosPrinter(new UsbConnection(usbManager, usbDevice)));
//                        }
//                    }
//                }
//            }
//        }
//    };
//
//    public void printUsb() {
//        UsbConnection usbConnection = UsbPrintersConnections.selectFirstConnected(this);
//        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
//
//        if (usbConnection == null || usbManager == null) {
//            new AlertDialog.Builder(this)
//                    .setTitle("USB Connection")
//                    .setMessage("No USB printer found.")
//                    .show();
//            return;
//        }
//
//        PendingIntent permissionIntent = PendingIntent.getBroadcast(
//                this,
//                0,
//                new Intent(MainActivity.ACTION_USB_PERMISSION),
//                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0
//        );
//        IntentFilter filter = new IntentFilter(MainActivity.ACTION_USB_PERMISSION);
//        registerReceiver(this.usbReceiver, filter);
//        usbManager.requestPermission(usbConnection.getDevice(), permissionIntent);
//    }
//
//    /*==============================================================================================
//    =========================================TCP PART===============================================
//    ==============================================================================================*/
//
//    public void printTcp() {
//        final EditText ipAddress = (EditText) this.findViewById(R.id.edittext_tcp_ip);
//        final EditText portAddress = (EditText) this.findViewById(R.id.edittext_tcp_port);
//
//        try {
//            new AsyncTcpEscPosPrint(
//                    this,
//                    new AsyncEscPosPrint.OnPrintFinished() {
//                        @Override
//                        public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
//                            Log.e("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : An error occurred !");
//                        }
//
//                        @Override
//                        public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
//                            Log.i("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : Print is finished !");
//                        }
//                    }
//            )
//                    .execute(
//                            this.getAsyncEscPosPrinter(
//                                    new TcpConnection(
//                                            ipAddress.getText().toString(),
//                                            Integer.parseInt(portAddress.getText().toString())
//                                    )
//                            )
//                    );
//        } catch (NumberFormatException e) {
//            new AlertDialog.Builder(this)
//                    .setTitle("Invalid TCP port address")
//                    .setMessage("Port field must be an integer.")
//                    .show();
//            e.printStackTrace();
//        }
//    }
//
//    /*==============================================================================================
//    ===================================ESC/POS PRINTER PART=========================================
//    ==============================================================================================*/
//
//    /**
//     * Asynchronous printing
//     */
//    @SuppressLint("SimpleDateFormat")
//    public AsyncEscPosPrinter getAsyncEscPosPrinter(DeviceConnection printerConnection) {
//        SimpleDateFormat format = new SimpleDateFormat("'on' yyyy-MM-dd 'at' HH:mm:ss");
//        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(printerConnection, 203, 48f, 32);
////        return printer.addTextToPrint("[C]<u><font size='big'>DRUCKER TEST ERFOLGREICH</font></u>\n")
//
//        return printer.addTextToPrint(
//                "[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, this.getApplicationContext().getResources().getDrawableForDensity(R.drawable.logo, DisplayMetrics.DENSITY_MEDIUM)) + "</img>\n" +
//                        "[L]\n" +
//                        "[C]<u><font size='big'>ORDER N°045</font></u>\n" +
//                        "[L]\n" +
//                        "[C]<u type='double'>" + format.format(new Date()) + "</u>\n" +
//                        "[C]\n" +
//                        "[C]================================\n" +
//                        "[L]\n" +
//                        "[L]<b>BEAUTIFUL SHIRT</b>[R]9.99€\n" +
//                        "[L]  + Size : S\n" +
//                        "[L]\n" +
//                        "[L]<b>AWESOME HAT</b>[R]24.99€\n" +
//                        "[L]  + Size : 57/58\n" +
//                        "[L]\n" +
//                        "[C]--------------------------------\n" +
//                        "[R]TOTAL PRICE :[R]34.98€\n" +
//                        "[R]TAX :[R]4.23€\n" +
//                        "[L]\n" +
//                        "[C]================================\n" +
//                        "[L]\n" +
//                        "[L]<u><font color='bg-black' size='tall'>Customer :</font></u>\n" +
//                        "[L]Raymond DUPONT\n" +
//                        "[L]5 rue des girafes\n" +
//                        "[L]31547 PERPETES\n" +
//                        "[L]Tel : +33801201456\n" +
//                        "\n" +
//                        "[C]<barcode type='ean13' height='10'>831254784551</barcode>\n" +
//                        "[L]\n" +
//                        "[C]<qrcode size='20'>https://dantsu.com/</qrcode>\n"
//        );
//    }
//
//    /*==============================================================================================
//    ===================================Tasty Igniter Part=========================================
//    ==============================================================================================*/
    private boolean isServiceActive = false;
    private Timer timer;

    public void tiPrintMonitoring() {

        if (!isServiceActive) { //start printing
            //TODO: check if a user is selected. Else toast an error
            startService();
        } else { // Stop printing
            stopService();
        }
    }

    private void startService() {
        int buttonColor;
        timer = new Timer();

        buttonColor = ContextCompat.getColor(this, R.color.colorPrimaryDark);
        button_ti_print.setBackgroundColor(buttonColor);
        button_ti_print.setText("Drucker ist Aktiv");
        // stop wake lock to stop CPU. Critical!
        MyWakeLockManager.acquireFullWakeLock(this);
        // keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                new WebServiceTask().execute(tiOrdersEndpointURL);
            }
        }, 0, period);

        isServiceActive = true;
    }

    private void stopService() {
        int buttonColor;
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
        buttonColor = ContextCompat.getColor(this, R.color.colorAccent);
        button_ti_print.setBackgroundColor(buttonColor);
        button_ti_print.setText("Drucker ist Inaktiv");
        // stop wake lock to stop CPU. Critical!
        MyWakeLockManager.releaseFullWakeLock();
        // remove keep screen on
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        isServiceActive = false;
    }

    private class WebServiceTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            String resultJson = null;

            try {
                URL url = new URL(urls[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuilder buffer = new StringBuilder();
                if (inputStream == null) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line).append("\n");
                }

                if (buffer.length() == 0) {
                    return null;
                }
                resultJson = buffer.toString();
            } catch (IOException e) {
                Log.e("MainActivity", "Error ", e);
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e("MainActivity", "Error closing stream", e);
                    }
                }
            }
            return resultJson;
        }

        @Override
        protected void onPostExecute(String result) {
            // Process the result here
            if (result != null) {
                // Do something with the result
                Log.d("WebServiceResponse", result);

                // Call printDocketCustomerReceipt method
                printDocketCustomerReceipt(result);
            } else {
                Log.e("WebServiceResponse", "Failed to fetch data from web service");
            }
        }
    }

    private void printDocketCustomerReceipt(String json) {
        showToast("Überwachung ist AN");
        String printOutput = "";
        String printHeader = "";
        String printOrder = "";
        String printAllCosts = "";
        String printCustomer = "";
        String printPayment = "";
        boolean isGoogleMaps = true;

        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray dataArray = filterPrintableOrders(jsonObject.getJSONArray("data"));
            if (dataArray.length() == 0) {
                //showToast("Nothing to Print");
                return;
            } else {
                mediaPlayer.start(); //always play if new order is available
            }
            // main data object with single orders
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject dataObject = dataArray.getJSONObject(i);
                String orderID = dataObject.getString("id");
                JSONObject orderAttributes = dataObject.getJSONObject("attributes");
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
                    order_type_time = "[C]<font size='tall'><b>Gewünschte Zeit: " + " am " + order_date_time + "</b></font>\n";
                } else {
                    order_type_time = "[C] Sofort: " + " am " + order_date_time + "\n";
                }
                printHeader = "[C]<u><font size='big'> Primavera </font></u>\n" +
                        "[L]<font size='big'>Bestellung Nr." + orderId + "</font>\n" +
                        "[L]\n" +
                        "[L]<font size='tall'><b>" + order_type + " - " + payment + " - <u type='double'>" +
                        orderTotal + "€ </u></b><font size='tall'>\n" +
//                        "[C]" + order_type + " am " + order_date_time + "\n" +
                        order_type_time +
                        "[C]\n" +
                        "[L]====================================\n" +
                        "[L]\n";

                // menu entries
                JSONArray order_menus_array = orderAttributes.getJSONArray("order_menus");
                for (int j = 0; j < order_menus_array.length(); j++) {
                    JSONObject order_menus_object = order_menus_array.getJSONObject(j);
                    String menusName = order_menus_object.getString("name");
                    String menusSubtotal = FormatStringValue(order_menus_object.getString("subtotal"));
                    String menusQuantity = order_menus_object.getString("quantity");
                    String menusComment = order_menus_object.getString("comment");
                    printOrder += "[L]<b>" + menusQuantity + "x - " + menusName + ", Preis "
                            + menusSubtotal + "€</b> \n";
                    if (menusComment != null && !menusComment.isEmpty()) {
                        printOrder += "[L]Kommentar: " + menusComment + "\n";
                    }
                    // menu options
                    JSONArray menu_options_array = order_menus_object.getJSONArray("menu_options");
                    for (int k = 0; k < menu_options_array.length(); k++) {
                        JSONObject menu_option_object = menu_options_array.getJSONObject(k);
                        String order_option_name = menu_option_object.getString("order_option_name");
                        String order_option_price = FormatStringValue(menu_option_object.getString("order_option_price"));
                        printOrder += "[L]" + order_option_name + ", Preis " + order_option_price + "€\n"
                                + "[L]\n";
                    }
                }
                //all costs
                JSONArray order_totals_array = orderAttributes.getJSONArray("order_totals");
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
                        "[L]Kommentar: " + comment + "\n" +
                        "[L]Google Adresse Scannen \n";

                if (isGoogleMaps) {
                    printCustomer += "[C]<qrcode size='20'>" + google_api_url + "</qrcode>";
                }

                // create full print String
                printOutput = printHeader +
                        printOrder +
                        printAllCosts +
                        printPayment +
                        "[L]====================================\n" +
                        "[L]Kundeninformation\n" +
                        printCustomer;
                //execute print
                TIJobPrintBluetooth(printOutput, orderID);
                //TODO: I need to implement a timeout if printer is not working
                //clear texts
                printOutput = "";
                printHeader = "";
                printOrder = "";
                printAllCosts = "";
                printCustomer = "";
                printPayment = "";
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private static JSONArray filterPrintableOrders(JSONArray dataArray) throws JSONException {
        JSONArray printableOrders = new JSONArray();

        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject order = dataArray.getJSONObject(i);
            String orderId = order.getString("id");

            if (!printedOrders.contains(orderId)) {
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

    public void TIJobPrintBluetooth(String print_info, String orderId) {
        this.checkBluetoothPermissions(() -> {
            new AsyncBluetoothEscPosPrint(
                    this,
                    new AsyncEscPosPrint.OnPrintFinished() {
                        @Override
                        public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                            Log.e("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : An error occurred !");
                            stopService(); //stop the service loop
                        }

                        @Override
                        public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                            Log.i("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : Print is finished !");
                            // save the printed order IDs, to prevent reprinting
                            // clearIds is needed as Shared Preferences does not overwrite once saved
                            // this can be optimized, with Shared Preferences overwrite
                            mediaPlayer.start(); // play if printing done
                            printedOrders.add(orderId);
                            IdManager.clearIds(context);
                            IdManager.saveIds(context, printedOrders);
                        }
                    }
            )
                    .execute(this.TIgetAsyncEscPosPrinter(selectedDevice, print_info));
        });
    }

    @SuppressLint("SimpleDateFormat")
    public AsyncEscPosPrinter TIgetAsyncEscPosPrinter(DeviceConnection printerConnection, String print_info) {
        SimpleDateFormat format = new SimpleDateFormat("'on' yyyy-MM-dd 'at' HH:mm:ss");
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(printerConnection, 203, 60f, 37);
        return printer.addTextToPrint(print_info);
    }

    private void openWebpage(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            url = "http://" + url;
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
    }

    private void tiClearIds() {
        //clear the id, so that the printing of open orders can be restarted
        ResetPrintFragment dialog = new ResetPrintFragment();
        dialog.show(getSupportFragmentManager(), "CustomDialogFragment");
    }

    private void LoginUser() {
        LoginUserDialogFragment dialog = new LoginUserDialogFragment();
        dialog.show(getSupportFragmentManager(), "CustomDialogFragment");
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

    private String FormatStringValue(String value) {
        String output = value.substring(0, value.length() - 2);
        return output;
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }


    public static class ResetPrintFragment extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater inflater = requireActivity().getLayoutInflater();
            View view = inflater.inflate(R.layout.popup_reset_print, null);

            Button okButton = view.findViewById(R.id.button_ok);
            okButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //clear the id, so that the printing of open orders can be restarted
                    IdManager.clearIds(context);
                    printedOrders.clear();
                    dismiss();
                }
            });

            Button cancelButton = view.findViewById(R.id.button_cancel);
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss(); // Close the dialog when Cancel button is clicked
                }
            });
            builder.setView(view);
            return builder.create();
        }
    }


    public static class LoginUserDialogFragment extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater inflater = requireActivity().getLayoutInflater();
            View view = inflater.inflate(R.layout.popup_login_user, null);

            Button okButton = view.findViewById(R.id.button_ok);
            Button cancelButton = view.findViewById(R.id.button_cancel);
            EditText etUsername = view.findViewById(R.id.etUsername);
            EditText etPassword = view.findViewById(R.id.etPassword);


            okButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String username = etUsername.getText().toString(); //TODO wenn man Enter Druckt dann entsteht eine neue Zeile
                    String password = etPassword.getText().toString();
                    //check for valid createntials //TODO differentiate if password or user is wrong
                    String validCredentials = checkValidCredentials(username, password);
                    if (validCredentials.equals("OK")) {
                        Toast.makeText(getActivity(), "Dialog dismissed", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getActivity(), validCredentials, Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                }
            });

            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss(); // Close the dialog when Cancel button is clicked
                }
            });
            builder.setView(view);
            return builder.create();
        }


        private String checkValidCredentials(String username, String password) {
            // Hardcoded credentials validation
            //TODO check user and PW from users
            //TODO: remove UserUtils.java, and add logic here. It does not make sence to have an extra class
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).username.equals(username) && users.get(i).password.equals(password)) {
                    return "OK";
                } else if (users.get(i).username.equals(username) && !users.get(i).password.equals(password)) {
                    return "falsches Passwort";
                } else {
                    //TODO: return user not found
                    return "Der Benutzer wurde nicht gefunden";
                }

            }
            return username;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService();
    }


    @SuppressLint("SimpleDateFormat")
    public boolean TITestPrinter(boolean normal_click) {
        SimpleDateFormat format = new SimpleDateFormat("'on' yyyy-MM-dd 'at' HH:mm:ss");
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(selectedDevice, 203, 48f, 32);
        String print_info;
        if (normal_click) {
            print_info = "[C] Test \n" +
                    "[C] Ausdruck \n" +
                    "[C]<u><font size='big'>DRUCKER TEST ERFOLGREICH</font></u>";
        } else {
            print_info = "[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, this.getApplicationContext().getResources().getDrawableForDensity(R.drawable.logo, DisplayMetrics.DENSITY_MEDIUM)) + "</img>\n" +
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

        this.checkBluetoothPermissions(() -> {
            new AsyncBluetoothEscPosPrint(
                    this,
                    new AsyncEscPosPrint.OnPrintFinished() {
                        @Override
                        public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                            Log.e("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : An error occurred !");
                            stopService(); //stop the service loop
                        }

                        @Override
                        public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                            Log.i("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : Print is finished !");
                            // save the printed order IDs, to prevent reprinting
                            // clearIds is needed as Shared Preferences does not overwrite once saved
                            // this can be optimized, with Shared Preferences overwrite
                            mediaPlayer.start(); // play if printing done
                        }
                    }
            )
                    .execute(this.TIgetAsyncEscPosPrinter(selectedDevice, print_info));
        });
        return true; // for OnLongClickListener
    }
}
package com.dantsu.thermalprinter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
import com.dantsu.escposprinter.connection.tcp.TcpConnection;
import com.dantsu.escposprinter.connection.usb.UsbConnection;
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections;
import com.dantsu.escposprinter.textparser.PrinterTextParserImg;
import com.dantsu.thermalprinter.async.AsyncBluetoothEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrinter;
import com.dantsu.thermalprinter.async.AsyncTcpEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncUsbEscPosPrint;

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
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TimeZone;
import java.util.HashSet;
import java.util.Set;

import com.dantsu.thermalprinter.helpClasses.BackgroundService;
import com.dantsu.thermalprinter.helpClasses.ForegroundService;
import com.dantsu.thermalprinter.helpClasses.IdManager;

import android.net.Uri;

public class MainActivity extends AppCompatActivity {

    //set of orders IDs, which already been printed
    private static Set<String> printedOrders = new HashSet<>();
    private static Context context;
    private Button button_ti_print;

    private String tiOrdersEndpointURL = "https://bestellen.primavera-pizza-wickede.de/api/orders?sort=order_id desc&pageLimit=50";
    private String tiDashboardURL = "https://bestellen.primavera-pizza-wickede.de/admin";
    private String tiKitchenViewURL = "https://bestellen.primavera-pizza-wickede.de/admin/thoughtco/kitchendisplay/summary/view/1";

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
        button  = (Button) this.findViewById(R.id.button_ti_clear_ids);
        button.setOnClickListener(view -> tiClearIds());
        button  = (Button) this.findViewById(R.id.button_ti_kitchen_view);
        button.setOnClickListener(view -> tiKitchenView());
        button  = (Button) this.findViewById(R.id.button_ti_dashboard);
        button.setOnClickListener(view -> tiDashboard());

        //get the already printed IDs or orders
        context = this;
        printedOrders = IdManager.getIds(context);

        // running service for printing
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {   //min API 33
            ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0
            );
        }

    }

    private String[] arrayOf(String postNotifications) {
        String[] strArray= new String[1];
        strArray[0] = postNotifications;
        return  strArray;
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

    public void printBluetooth() {
        this.checkBluetoothPermissions(() -> {
            new AsyncBluetoothEscPosPrint(
                    this,
                    new AsyncEscPosPrint.OnPrintFinished() {
                        @Override
                        public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                            Log.e("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : An error occurred !");
                        }

                        @Override
                        public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                            Log.i("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : Print is finished !");
                        }
                    }
            )
                    .execute(this.getAsyncEscPosPrinter(selectedDevice));
        });
    }

    /*==============================================================================================
    ===========================================USB PART=============================================
    ==============================================================================================*/

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MainActivity.ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                    UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbManager != null && usbDevice != null) {
                            new AsyncUsbEscPosPrint(
                                    context,
                                    new AsyncEscPosPrint.OnPrintFinished() {
                                        @Override
                                        public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                                            Log.e("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : An error occurred !");
                                        }

                                        @Override
                                        public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                                            Log.i("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : Print is finished !");
                                        }
                                    }
                            )
                                    .execute(getAsyncEscPosPrinter(new UsbConnection(usbManager, usbDevice)));
                        }
                    }
                }
            }
        }
    };

    public void printUsb() {
        UsbConnection usbConnection = UsbPrintersConnections.selectFirstConnected(this);
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);

        if (usbConnection == null || usbManager == null) {
            new AlertDialog.Builder(this)
                    .setTitle("USB Connection")
                    .setMessage("No USB printer found.")
                    .show();
            return;
        }

        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(MainActivity.ACTION_USB_PERMISSION),
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0
        );
        IntentFilter filter = new IntentFilter(MainActivity.ACTION_USB_PERMISSION);
        registerReceiver(this.usbReceiver, filter);
        usbManager.requestPermission(usbConnection.getDevice(), permissionIntent);
    }

    /*==============================================================================================
    =========================================TCP PART===============================================
    ==============================================================================================*/

    public void printTcp() {
        final EditText ipAddress = (EditText) this.findViewById(R.id.edittext_tcp_ip);
        final EditText portAddress = (EditText) this.findViewById(R.id.edittext_tcp_port);

        try {
            new AsyncTcpEscPosPrint(
                    this,
                    new AsyncEscPosPrint.OnPrintFinished() {
                        @Override
                        public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                            Log.e("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : An error occurred !");
                        }

                        @Override
                        public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                            Log.i("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : Print is finished !");
                        }
                    }
            )
                    .execute(
                            this.getAsyncEscPosPrinter(
                                    new TcpConnection(
                                            ipAddress.getText().toString(),
                                            Integer.parseInt(portAddress.getText().toString())
                                    )
                            )
                    );
        } catch (NumberFormatException e) {
            new AlertDialog.Builder(this)
                    .setTitle("Invalid TCP port address")
                    .setMessage("Port field must be an integer.")
                    .show();
            e.printStackTrace();
        }
    }

    /*==============================================================================================
    ===================================ESC/POS PRINTER PART=========================================
    ==============================================================================================*/

    /**
     * Asynchronous printing
     */
    @SuppressLint("SimpleDateFormat")
    public AsyncEscPosPrinter getAsyncEscPosPrinter(DeviceConnection printerConnection) {

        SimpleDateFormat format = new SimpleDateFormat("'on' yyyy-MM-dd 'at' HH:mm:ss");
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(printerConnection, 203, 48f, 32);
        return printer.addTextToPrint(
                "[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, this.getApplicationContext().getResources().getDrawableForDensity(R.drawable.logo, DisplayMetrics.DENSITY_MEDIUM)) + "</img>\n" +
                        "[L]\n" +
                        "[C]<u><font size='big'>ORDER N°045</font></u>\n" +
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
                        "[C]<qrcode size='20'>https://dantsu.com/</qrcode>\n"
        );
    }

    /*==============================================================================================
    ===================================Tasty Igniter Part=========================================
    ==============================================================================================*/
    private boolean isServiceActive = false;
    private Timer timer;



    public void tiPrintMonitoring() {
        // app should be running if the screen is off
        PowerManager mgr = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = mgr.newWakeLock(PowerManager.FULL_WAKE_LOCK, "MyWakeLock");
        //TODO: https://stackoverflow.com/questions/6091270/how-can-i-keep-my-android-service-running-when-the-screen-is-turned-off
        int buttonColor;
        if (!isServiceActive) { //start printing
            //start in Foreground
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {   //min API 26
                Intent serviceIntent = new Intent(ForegroundService.Actions.START.toString());
                startForegroundService(serviceIntent);
            } else { //start in background
                Intent serviceIntent = new Intent(this, BackgroundService.class);
                startService(serviceIntent);
            }
            wakeLock.acquire();
            buttonColor = ContextCompat.getColor(this, R.color.colorPrimaryDark);
            button_ti_print.setBackgroundColor(buttonColor);
            button_ti_print.setText("Drucker ist Aktiv");
            startService();
        } else { // Stop printing
            //Stop in Foreground
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {   //min API 26
                Intent serviceIntent = new Intent(ForegroundService.Actions.STOP.toString());
                startService(serviceIntent);
            }else { //Stop in background
                Intent serviceIntent = new Intent(this, BackgroundService.class);
                stopService(serviceIntent);
            }
            wakeLock.release();
            buttonColor = ContextCompat.getColor(this, R.color.colorAccent);
            button_ti_print.setBackgroundColor(buttonColor);
            button_ti_print.setText("Drucker ist Inaktiv");
            stopService();
        }
    }

    private void startService() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                new WebServiceTask().execute(tiOrdersEndpointURL);
            }
        }, 0, 10000); // Execute every minute (60,000 milliseconds) TODO: change to 60000

        isServiceActive = true;
        //showToast("Service activated");
    }

    private void stopService() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }

        isServiceActive = false;
        //showToast("Service deactivated");
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
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
        showToast("Service activated");
        String printOutput = "";
        String printHeader = "";
        String printOrder = "";
        String printAllCosts = "";
        String printCustomer = "";
        String printPayment = "";

        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray dataArray = filterPrintableOrders(jsonObject.getJSONArray("data"));
            if (dataArray.length() == 0){
                //showToast("Nothing to Print");
                return;
            }
            // main data object with single orders
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject dataObject = dataArray.getJSONObject(i);
                String orderID = dataObject.getString("id");
                JSONObject orderAttributes = dataObject.getJSONObject("attributes");
                String payment = orderAttributes.getString("payment");//stripe, cod, paypalexpress
                String order_type = orderAttributes.getString("order_type");
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
                if (order_type == "delivery"){
                    order_type = "Lieferung";
                } else {
                    order_type = "Abholung";
                }
                printHeader = "[C]<u><font size='big'> Primavera </font></u>\n"+
                        "[L]<font size='big'>Bestellung Nr." + orderId + "</font>\n" +
                        "[L]\n" +
                        "[C]<b>" + order_type + " - " + payment + " - <u type='double'>" +
                        orderTotal + "€ </u></b>\n" +
                        "[C]" + order_type + " am " + order_date_time + "\n" +
                        "[C]\n" +
                        "[C]================================\n" +
                        "[L]\n";

                // menu entries
                JSONArray order_menus_array = orderAttributes.getJSONArray("order_menus");
                for (int j = 0; j < order_menus_array.length(); j++) {
                    JSONObject order_menus_object = order_menus_array.getJSONObject(j);
                    String menusName = order_menus_object.getString("name");
                    String menusSubtotal = FormatStringValue(order_menus_object.getString("subtotal"));
                    String menusQuantity = order_menus_object.getString("quantity");
                    printOrder +="[L]<b>" + menusQuantity + "x - "+ menusName + ", Preis "
                            + menusSubtotal + "€</b> \n";
                    // menu options
                    JSONArray menu_options_array = order_menus_object.getJSONArray("menu_options");
                    for (int k = 0; k < menu_options_array.length(); k++) {
                        JSONObject menu_option_object = menu_options_array.getJSONObject(k);
                        String order_option_name = menu_option_object.getString("order_option_name");
                        String order_option_price = FormatStringValue(menu_option_object.getString("order_option_price"));
                        printOrder += "[L]"+ order_option_name + ", Preis " + order_option_price +"€\n"
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

                printCustomer += "[L]Name: " + customer_name +"\n" +
                        "[L]Telefon: " + telephone +"\n" +
                        "[L]Adresse: "+ formatted_address + "\n" +
                        "[L]Kommentar: "+ comment + "\n" +
                        "[L]Google Adresse Scannen \n" +
                        "[C]<qrcode size='20'>" + google_api_url + "</qrcode>";

                // create print String
                printOutput = printHeader +
                        printOrder +
                        printAllCosts +
                        printPayment +
                        "[C]================================\n" +
                        "[L]Kundeninformation\n" +
                        printCustomer;
                //execute print
                //TIJobPrintBluetooth(printOutput, orderID); //TODO Activate
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

    private String FormatStringValue(String value) {
        String output = value.substring(0, value.length()-2);
        return output;
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

    public void TIJobPrintBluetooth(String print_info, String orderId) {
        // moved to PrintMonitoringService
        this.checkBluetoothPermissions(() -> {
            new AsyncBluetoothEscPosPrint(
                    this,
                    new AsyncEscPosPrint.OnPrintFinished() {
                        @Override
                        public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                            Log.e("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : An error occurred !");
                        }

                        @Override
                        public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                            Log.i("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : Print is finished !");
                            // save the printed order IDs, to prevent reprinting
                            printedOrders.add(orderId); // Add to printed orders set
                            IdManager.saveIds(context, printedOrders);
                        }
                    }
            )
                    .execute(this.TIgetAsyncEscPosPrinter(selectedDevice, print_info));
        });
    }

    private void tiClearIds(){
        //clear the id, so that the printing of open orders can be restarted
        IdManager.clearIds(context);
        printedOrders.clear();
    }

    @SuppressLint("SimpleDateFormat")
    public AsyncEscPosPrinter TIgetAsyncEscPosPrinter(DeviceConnection printerConnection, String print_info) {
        SimpleDateFormat format = new SimpleDateFormat("'on' yyyy-MM-dd 'at' HH:mm:ss");
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(printerConnection, 203, 48f, 32);
        return printer.addTextToPrint(print_info);
    }

    private void tiKitchenView(){
        String url = tiKitchenViewURL;
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            url = "http://" + url;
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
    }

    private void tiDashboard(){
        String url = tiDashboardURL;
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            url = "http://" + url;
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
    }

}

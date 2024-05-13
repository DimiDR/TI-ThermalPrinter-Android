package com.dantsu.thermalprinter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import android.widget.TextView;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TimeZone;
import java.util.HashSet;
import java.util.Set;

import com.dantsu.thermalprinter.helpClasses.DocketStringModeler;
import com.dantsu.thermalprinter.helpClasses.IdManager;
import com.dantsu.thermalprinter.helpClasses.MyWakeLockManager;
import com.dantsu.thermalprinter.helpClasses.UserUtils;

import android.net.Uri;

public class MainActivity extends AppCompatActivity {
    //set of orders IDs, which already been printed
    private static Set<String> printedOrders = new HashSet<>();
    private static Context context;
    private static Button button_bluetooth_browse;
    private static Button button_ti_print;
    private static Button button_ti_clear_ids;
    private static Button button_ti_kitchen_view;
    private static Button button_ti_dashboard;
    private static Button button_ti_updates;
    private static Button button_ti_landing_page;
    private static Button button_ti_testprint;
    private Button button_ti_login;
    private static TextView textview_ti_header;
    private boolean userSelected = false; // TODO: no buttons should work if user is not selected
    private static String domain_shop;
    private static String domain_website = "";
    private static String username = "";
    private static String shop_name = "";
    private static String tiOrdersEndpointURL = "";
    private static String tiDashboardURL  = "";
    private static String tiKitchenViewURL  = "";
    private static String tiLandingPage  = "";
    private static String tiMenusEndpointURL = "";
    private static String tiCategoriesEndpointURL = "";
    private final String tiUpdates  = "";
    private static List<UserUtils.User> users;
    MediaPlayer mediaPlayer;
    String resultJson;
    long period = 60 * 1000; // set to 60000 for 1 minute //TODO change
    DocketStringModeler docketStringModeler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button_bluetooth_browse = this.findViewById(R.id.button_bluetooth_browse);
        button_bluetooth_browse.setOnClickListener(view -> browseBluetoothDevice());
        // TCP & USB printing deactivated
        //button = (Button) findViewById(R.id.button_bluetooth);
        //button.setOnClickListener(view -> printBluetooth());
        //button = (Button) this.findViewById(R.id.button_usb);
        //button.setOnClickListener(view -> printUsb());
        //button = (Button) this.findViewById(R.id.button_tcp);
        //button.setOnClickListener(view -> printTcp());
        button_ti_print = this.findViewById(R.id.button_ti_print_monitoring);
        button_ti_print.setOnClickListener(view -> tiPrintMonitoring());
        textview_ti_header = this.findViewById(R.id.textview_ti_header);
        button_ti_clear_ids = this.findViewById(R.id.button_ti_clear_ids);
        button_ti_clear_ids.setOnClickListener(view -> tiClearIds());
        button_ti_kitchen_view = this.findViewById(R.id.button_ti_kitchen_view);
        button_ti_kitchen_view.setOnClickListener(view -> openWebpage("KitchenView"));
        button_ti_dashboard =  this.findViewById(R.id.button_ti_dashboard);
        button_ti_dashboard.setOnClickListener(view -> openWebpage("Administration"));
        button_ti_updates =  this.findViewById(R.id.button_ti_updates);
        button_ti_updates.setOnClickListener(view -> openWebpage("Updates"));
        button_ti_landing_page = this.findViewById(R.id.button_ti_landing_page);
        button_ti_landing_page.setOnClickListener(view -> openWebpage("LandingPage"));
        button_ti_testprint = this.findViewById(R.id.button_ti_testprint);
        button_ti_testprint.setOnClickListener(view -> TITestPrinter(true));
        button_ti_testprint.setOnLongClickListener(view -> TITestPrinter(false));
        button_ti_login = this.findViewById(R.id.button_ti_login);
        button_ti_login.setOnClickListener(view -> LoginUser());
        //get the already printed IDs or orders
        context = this;
        printedOrders = IdManager.getIds(context);
        // play new order sound
        mediaPlayer = MediaPlayer.create(context, R.raw.newordersound);
        // create string for docket
        docketStringModeler = new DocketStringModeler();
        // get valid users
        users = UserUtils.getUsers(this);
        // Retrieve saved login details from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        String savedUsername = sharedPreferences.getString("username", "");
        String savedPassword = sharedPreferences.getString("password", "");
        String savedDomainShop = sharedPreferences.getString("domain_shop", "");
        String savedDomainWebsite = sharedPreferences.getString("domain_website", "");

        if (!savedUsername.isEmpty() && !savedPassword.isEmpty()) {
            // Set saved URLs to your URL fields
            tiOrdersEndpointURL = savedDomainShop + "/api/orders?sort=order_id desc&pageLimit=50";
            tiDashboardURL = savedDomainShop + "/admin";
            tiKitchenViewURL = savedDomainShop + "/admin/thoughtco/kitchendisplay/summary/view/1";
            tiLandingPage = savedDomainWebsite + "/";
            //TODO: activate all buttons
            //TODO: deactivate all buttons except Login in XML
            //TODO: user2 kann ich einloggen
            textview_ti_header.setText(savedUsername);
            tiMenusEndpointURL = savedDomainShop + "/api/menus?include=categories&pageLimit=5000";
            tiCategoriesEndpointURL = savedDomainShop + "/api/categories";
            //activate the buttons
            button_bluetooth_browse.setEnabled(true);
            button_ti_print.setEnabled(true);
            button_ti_clear_ids.setEnabled(true);
            button_ti_kitchen_view.setEnabled(true);
            button_ti_dashboard.setEnabled(true);
            button_ti_updates.setEnabled(true);
            button_ti_landing_page.setEnabled(true);
            button_ti_testprint.setEnabled(true);
        }
        //TODO: all buttons needs to have a name so I can set to inactive
//        if (savedUsername.isEmpty()) {
        //TODO: here deactivate buttons
//        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        setContentView(R.layout.activity_main);
        super.onConfigurationChanged(newConfig);
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
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
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
                 new WebServiceTask().execute(tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL);
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

private class WebServiceTask extends AsyncTask<String, Void, String[]> {

    @Override
    protected String[] doInBackground(String... urls) {
        String[] results = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            try {
                URL url = new URL(urls[i]);
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
                results[i] = buffer.toString();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return results;
    }

    @Override
    protected void onPostExecute(String[] results) {
        // results[0] contains the response from the orders API
        // results[1] contains the response from the menus API
            String orderID;
            JSONObject dataObjectOrders;
            if (results[0] != null && results[1] != null) {
                try {
                    JSONObject jsonObjectOrders = new JSONObject(results[0]);
                    JSONObject jsonObjectMenus = new JSONObject(results[1]);
                    JSONObject jsonObjectCategories = new JSONObject(results[2]);
                    JSONArray dataArrayOrders  = DocketStringModeler.filterPrintableOrders(jsonObjectOrders.getJSONArray("data"));
                    for (int i = 0; i < dataArrayOrders.length(); i++) {
                        dataObjectOrders = dataArrayOrders.getJSONObject(i);
                        orderID = dataObjectOrders.getString("id");
                        TIJobPrintBluetooth(docketStringModeler.startPrinting(dataObjectOrders, jsonObjectMenus, jsonObjectCategories, mediaPlayer), orderID);
                    }

                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            } else {
                Log.e("WebServiceResponse", "Failed to fetch data from web service");
            }
    }
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
//        SimpleDateFormat format = new SimpleDateFormat("'on' yyyy-MM-dd 'at' HH:mm:ss");
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(printerConnection, 203, 48f, 32);
        return printer.addTextToPrint(print_info);
    }

    private void openWebpage(String action) {
        String url = "";
        //TODO Im changing the URLs due to multivendor.
        // As I am setting the Listener, old URLs are being saved. They need to be updated here
        // Implement other cases for differen URLs here

            switch (action) {
                case "LandingPage":
                    url = tiLandingPage;
                    break;
                case "APIEndpoint": //TODO why do I need to open API on the webpage?
                    url = tiOrdersEndpointURL;
                    break;
                case "KitchenView":
                    url = tiKitchenViewURL;
                    break;
                case "Administration":
                    url = tiDashboardURL;
                    break;
                case "Updates":
                    url = tiUpdates;
                    break;
                default:
                    url = "";
                    break;
            }

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
//                    AsyncTask tast = new WebServiceTask().execute(tiOrdersEndpointURL); //TODO test printing initial requests immediately
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
            TextView etError = view.findViewById(R.id.popup_error);

            okButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String username = etUsername.getText().toString(); //TODO wenn man Enter Druckt dann entsteht eine neue Zeile
                    String password = etPassword.getText().toString();
                    String validCredentials = checkValidCredentials(username, password);
                    if (validCredentials.equals("OK")) {
//                        Toast.makeText(getActivity(), "Nutzer gefunden", Toast.LENGTH_SHORT).show();
                        //TODO: activate all buttons
                        //activate the buttons
                        button_bluetooth_browse.setEnabled(true);
                        button_ti_print.setEnabled(true);
                        button_ti_clear_ids.setEnabled(true);
                        button_ti_kitchen_view.setEnabled(true);
                        button_ti_dashboard.setEnabled(true);
                        button_ti_updates.setEnabled(true);
                        button_ti_landing_page.setEnabled(true);
                        button_ti_testprint.setEnabled(true);
                        dismiss();
                    } else {
                        // activate the error text in the popup
                        etError.setVisibility(View.VISIBLE);
                        etError.setText(validCredentials);
                    }
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
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
            String userMessage = "EMPTY";
            // Hardcoded credentials validation
            //TODO check user and PW from users
            //TODO: remove UserUtils.java, and add logic here. It does not make sence to have an extra class
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).username.equals(username) && users.get(i).password.equals(password)) {
                    // username has to be unique in JSON
                    domain_shop = users.get(i).domain_shop;
                    domain_website = users.get(i).domain_website;
                    shop_name = users.get(i).shop_name;
                    username = users.get(i).username;
                    tiOrdersEndpointURL = domain_shop + "/api/orders?sort=order_id desc&pageLimit=50";
                    tiDashboardURL = domain_shop + "/admin";
                    tiKitchenViewURL = domain_shop + "/admin/thoughtco/kitchendisplay/summary/view/1";
                    tiLandingPage = domain_website + "/";
                    //save login data
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("username", username);
                    editor.putString("password", password);
                    editor.putString("shop_name", users.get(i).shop_name);
                    editor.putString("domain_shop", users.get(i).domain_shop);
                    editor.putString("domain_website", users.get(i).domain_website);
                    editor.apply();
                    //change header text
                    textview_ti_header.setText(shop_name);
                    return "OK";
                } else if (!users.get(i).username.equals(username)) {
                    userMessage = "FEHLER: Der Benutzer wurde nicht gefunden";
                } else if (users.get(i).username.equals(username) && !users.get(i).password.equals(password)) {
                    userMessage = "FEHLER: falsches Passwort";
                }
            }
            return userMessage;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService();
    }


    @SuppressLint("SimpleDateFormat")
    public boolean TITestPrinter(boolean normal_click) {
        String print_info;
        print_info = DocketStringModeler.TITestPrinter(normal_click, selectedDevice, context);

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
                            mediaPlayer.start(); // play if printing done
                        }
                    }
            )
                    .execute(this.TIgetAsyncEscPosPrinter(selectedDevice, print_info));
        });
        return true; // for OnLongClickListener
    }
}
package com.dantsu.thermalprinter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.FragmentManager;

import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.HashSet;
import java.util.Set;

import com.dantsu.thermalprinter.helpClasses.DocketStringModeler;
import com.dantsu.thermalprinter.helpClasses.IdManager;
import com.dantsu.thermalprinter.helpClasses.MyWakeLockManager;
import com.dantsu.thermalprinter.helpClasses.NetworkHelper;
import com.dantsu.thermalprinter.helpClasses.ShopConfigUtils;
import com.dantsu.thermalprinter.helpClasses.WebViewDialogFragment;
import com.dantsu.thermalprinter.model.NetworkHelperViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import android.net.Uri;

import javax.net.ssl.SSLException;

public class MainActivity extends AppCompatActivity implements NetworkHelper.NetworkCallback  {
    //set of orders IDs, which already been printed
    private static Set<String> printedOrders = new HashSet<>();
    private static Context context;
    private static Button button_bluetooth_browse;
    private static Button button_reprint;

    private static Button button_ti_print;
    private static Button button_ti_clear_ids;
    private static Button button_ti_kitchen_view;
    private static Button button_ti_native_kitchen;
    private static Button button_ti_dashboard;
    private static Button button_ti_updates;
    private static Button button_ti_landing_page;
    private static Button button_ti_testprint;
    private Button button_ti_login;
    private Button button_ti_logout;
    private static TextView textview_ti_header;
    private boolean userSelected = false; // TODO: no buttons should work if user is not selected
    private static Integer shop_id;
    private static String domain_shop;
    private static String domain_website = "";
    private static String username = "";
    private static String password = "";
    private static String shop_name = "";
    private static Integer location_id;
    private static String tiOrdersEndpointURL = "";
    private static String tiDashboardURL  = "";
    private static String tiKitchenViewURL  = "";
    private static String tiLandingPage  = "";
    private static String tiMenusEndpointURL = "";
    private static String tiCategoriesEndpointURL = "";
    private final String tiUpdates  = "";
    private static List<ShopConfigUtils.Shop> shops;
    private ChipGroup chipGroupPrinters;
    private Chip chipReceipt, chipKitchen;
    MediaPlayer mediaPlayer;
    String resultJson;
    long period = 6 * 10000; // set to 60000 for 1 minute
    long delayPeriod = 0;
    private boolean isLongerConnectionTime = false;
    DocketStringModeler docketStringModeler;
    private NetworkHelperViewModel networkHelperViewModel;
    private String apkFile;
    private ProgressBar progressBar;
    private Boolean isPrinterSelected = false;
    private Boolean isServiceStarted = false;
    private FragmentManager fragmentManager = getSupportFragmentManager();
    private int storedPrinterIndex = -1; // Initialize the printer selection from dropdown

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        networkHelperViewModel = new ViewModelProvider(this).get(NetworkHelperViewModel.class);

        button_bluetooth_browse = this.findViewById(R.id.button_bluetooth_browse);
        button_reprint = this.findViewById(R.id.button_reprint);
        progressBar = findViewById(R.id.progressBar);

        if (button_bluetooth_browse != null) {
            button_bluetooth_browse.setOnClickListener(view -> browseBluetoothDevice(-1, true));
        }
        button_ti_print = this.findViewById(R.id.button_ti_print_monitoring);
        if (button_ti_print != null) {
            button_ti_print.setOnClickListener(view -> tiPrintMonitoring());
        }
        textview_ti_header = this.findViewById(R.id.textview_ti_header);
        button_ti_clear_ids = this.findViewById(R.id.button_ti_clear_ids);
        if (button_ti_clear_ids != null) {
            button_ti_clear_ids.setOnClickListener(view -> tiClearIds());
        }
        button_ti_kitchen_view = this.findViewById(R.id.button_ti_kitchen_view);
        if (button_ti_kitchen_view != null) {
            button_ti_kitchen_view.setOnClickListener(view -> openWebpage("KitchenView"));
        }
        button_ti_native_kitchen = this.findViewById(R.id.button_ti_native_kitchen);
        if (button_ti_native_kitchen != null) {
            button_ti_native_kitchen.setOnClickListener(view -> openNativeKitchenDisplay());
        } else {
            Log.e("MainActivity", "button_ti_native_kitchen is null - check layout file");
        }
        button_ti_dashboard =  this.findViewById(R.id.button_ti_dashboard);
        if (button_ti_dashboard != null) {
            button_ti_dashboard.setOnClickListener(view -> openWebpage("Administration"));
        }
        button_ti_updates =  this.findViewById(R.id.button_ti_updates);
        if (button_ti_updates != null) {
            button_ti_updates.setOnClickListener(view -> showUpdatePopup());
        }
        button_ti_landing_page = this.findViewById(R.id.button_ti_landing_page);
        if (button_ti_landing_page != null) {
            button_ti_landing_page.setOnClickListener(view -> openWebpage("LandingPage"));
        }
        button_ti_testprint = this.findViewById(R.id.button_ti_testprint);
        if (button_ti_testprint != null) {
            button_ti_testprint.setOnClickListener(view -> TITestPrinter(true));
            button_ti_testprint.setOnLongClickListener(view -> TITestPrinter(false));
        }
        button_ti_login = this.findViewById(R.id.button_ti_login);
        if (button_ti_login != null) {
            button_ti_login.setOnClickListener(view -> LoginUser());
        }
        button_ti_logout = this.findViewById(R.id.button_ti_logout);
        if (button_ti_logout != null) {
            button_ti_logout.setOnClickListener(view -> LogoutUser());
        }
        // Initialize the ChipGroup and Chips
        chipGroupPrinters = findViewById(R.id.chipGroup_printers);
        chipReceipt = findViewById(R.id.chip_receipt);
        chipKitchen = findViewById(R.id.chip_kitchen);
        //get the already printed IDs or orders
        context = this;
        printedOrders = IdManager.getIds(context);
        // play new order sound
        mediaPlayer = MediaPlayer.create(context, R.raw.newordersound);
        // create string for docket
        docketStringModeler = new DocketStringModeler();
        // get valid shops
        shops = ShopConfigUtils.getShops(this);
        //initialize the URLs and activate the buttons
        initilizeURLs();
        //enable update button, if updates available
        if (getIntent().getBooleanExtra("isUpdate", false)){
            apkFile = getIntent().getStringExtra("apkFile");
            button_ti_updates.setEnabled(true);
            button_ti_updates.setText("Update Verfügbar");
            button_ti_updates.setBackgroundColor(ContextCompat.getColor(this, R.color.colorError));
            if (!networkHelperViewModel.isUpdatePopupShown()) {
                showUpdatePopup();
                networkHelperViewModel.setUpdatePopupShown(true);
            }
        }else{
            button_ti_updates.setEnabled(false);
            // show current version on the screen
            button_ti_updates.setText("Version " + getIntent().getStringExtra("currentAppVersion"));
        }

        //reprint orders
        if (button_reprint != null) {
            button_reprint.setOnClickListener(v -> {
            progressBar.setVisibility(View.VISIBLE);
            button_reprint.setVisibility(View.GONE);
            String[] urls = {tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL};
            networkHelperViewModel.getNetworkHelper().fetchData(urls, username, password, domain_shop, new NetworkHelper.NetworkCallback() {
                @Override
                public void onSuccess(String[] results) {
                    progressBar.setVisibility(View.GONE);
                    button_reprint.setVisibility(View.VISIBLE);
                    JSONObject dataObjectOrders;
                    List<JSONObject> ordersList = new ArrayList<>();
                    JSONObject jsonObjectOrders, jsonObjectMenus = null, jsonObjectCategories = null;
                    if (results != null && results[0] != null && results[1] != null) {
                        try {
                            jsonObjectOrders = new JSONObject(results[0]);
                            jsonObjectMenus = new JSONObject(results[1]);
                            jsonObjectCategories = new JSONObject(results[2]);

                            Log.e("MainActivity", "Full Orders JSON: " + jsonObjectOrders.toString());

                            JSONArray dataArrayOrders = jsonObjectOrders.getJSONArray("data");
                            for (int i = 0; i < dataArrayOrders.length(); i++) {
                                dataObjectOrders = dataArrayOrders.getJSONObject(i);
                                ordersList.add(dataObjectOrders);
                            }

                            Log.e("MainActivity", "Orders List Size: " + ordersList.size());

                        } catch (JSONException e) {
                            Toast.makeText(context, "" + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }

                        if (!ordersList.isEmpty()) {
                            showOrdersPopup(ordersList, jsonObjectMenus, jsonObjectCategories);
                        } else {
                            showError("No re-printable orders found");
                        }
                    } else {
                        showError("Failed to fetch data from web service");
                    }
                }

                @Override
                public void onError(Exception exception) {
                    progressBar.setVisibility(View.GONE);
                    button_reprint.setVisibility(View.VISIBLE);
                    Toast.makeText(context, "" + exception.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
        }

    }

    public void initilizeURLs() {
        // Check for persistent login
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        shop_id = sharedPreferences.getInt("shop_id", 0);
        String savedDomain = sharedPreferences.getString("domain_shop", "");
        String savedToken = sharedPreferences.getString("token", "");

        //apply automatic printer selection of last selected device
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        storedPrinterIndex = prefs.getInt("stored_index_printer", -1);
        browseBluetoothDevice(storedPrinterIndex, false);

        if (shop_id == 0 || savedDomain.isEmpty() || savedToken.isEmpty()) {
            // No persistent login found, show login dialog
            LoginUser();
            return;
        }

        // Validate token and auto-login if valid
        NetworkHelper networkHelper = new NetworkHelper(this);
        networkHelper.validateTokenAsync(savedDomain, new NetworkHelper.TokenCallback() {
            @Override
            public void onTokenGenerated(String token) {
                // Token is valid, proceed with auto-login
                runOnUiThread(() -> {
                    loadShopConfiguration(savedDomain);
                    enableAllButtons();
                });
            }

            @Override
            public void onTokenError(Exception exception) {
                // Token is invalid, clear stored data and show login
                runOnUiThread(() -> {
                    clearStoredLogin();
                    LoginUser();
                });
            }
        });
    }

    private void loadShopConfiguration(String domain) {
        for (ShopConfigUtils.Shop shop : shops) {
            if (shop.domain_shop.equals(domain)) {
                shop_name = shop.shop_name;
                domain_shop = shop.domain_shop;
                domain_website = shop.domain_website;
                tiDashboardURL = shop.domain_shop + "/admin";
                tiKitchenViewURL = shop.kitchen_view;
                tiLandingPage = shop.domain_website + "/";
                location_id = shop.location_id;
                textview_ti_header.setText(shop.shop_name);
                
                // Set API endpoints
                tiOrdersEndpointURL = shop.domain_shop + "/api/orders?sort=order_id desc&pageLimit=50&location=" + location_id;
                tiMenusEndpointURL = shop.domain_shop + "/api/menus?include=categories&pageLimit=5000&location=" + location_id;
                tiCategoriesEndpointURL = shop.domain_shop + "/api/categories?location=" + location_id;
                break;
            }
        }
    }

    private void enableAllButtons() {
        if (button_bluetooth_browse != null) button_bluetooth_browse.setEnabled(true);
        if (button_ti_print != null) button_ti_print.setEnabled(true);
        if (button_ti_clear_ids != null) button_ti_clear_ids.setEnabled(true);
        if (button_ti_kitchen_view != null) button_ti_kitchen_view.setEnabled(true);
        if (button_ti_native_kitchen != null) button_ti_native_kitchen.setEnabled(true);
        if (button_ti_dashboard != null) button_ti_dashboard.setEnabled(true);
        if (button_ti_landing_page != null) button_ti_landing_page.setEnabled(true);
        if (button_ti_testprint != null) button_ti_testprint.setEnabled(true);
        if (button_reprint != null) button_reprint.setEnabled(true);
        
        // Show logout button and hide login button
        if (button_ti_login != null) button_ti_login.setVisibility(View.GONE);
        if (button_ti_logout != null) button_ti_logout.setVisibility(View.VISIBLE);
    }

    private void clearStoredLogin() {
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        
        // Also clear API token
        NetworkHelper networkHelper = new NetworkHelper(this);
        networkHelper.clearToken();
    }

    private void clearWebViewCookies() {
        // Clear WebView cookies to logout from website
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        if (cookieManager != null) {
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        }
    }

    @SuppressLint("SimpleDateFormat")
    public AsyncEscPosPrinter TIgetAsyncEscPosPrinter(DeviceConnection printerConnection, String print_info) {
//        SimpleDateFormat format = new SimpleDateFormat("'on' yyyy-MM-dd 'at' HH:mm:ss");
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(printerConnection, 203, 48f, 32);
        printer.addTextToPrint(print_info);
        printer.getPrinterConnection().disconnect(); // important so that the printer can recconect
        return printer;
    }

    private void showOrdersPopup(List<JSONObject> ordersList, JSONObject jsonObjectMenus, JSONObject jsonObjectCategories) {
        // reprint functionality
        // Inflate the popup layout
        View popupView = getLayoutInflater().inflate(R.layout.popup_orders, null);

        // Create the popup window
        int width = LinearLayout.LayoutParams.MATCH_PARENT; // Set width to match_parent
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);

        // Set background drawable for the popup window
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popupWindow.setElevation(10.0f); // Adds shadow effect

        ImageView btnClose = popupView.findViewById(R.id.btnClose);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
            }
        });
        // Set up the ListView
        ListView listViewOrders = popupView.findViewById(R.id.listViewOrders);
        OrdersAdapter adapter = new OrdersAdapter(this, ordersList, new OrdersAdapter.PrintButtonClickListener() {
            @Override
            public void onPrintButtonClick(int position, String orderId) {
//                TIJobPrintBluetooth(docketStringModeler.startPrintingReceipt(ordersList.get(position), jsonObjectMenus, jsonObjectCategories, mediaPlayer, shop_name), orderId);
                //print receipt
                if (chipReceipt.isChecked()) {
                    TIJobPrintBluetooth(docketStringModeler.startPrintingReceipt(ordersList.get(position), jsonObjectMenus, jsonObjectCategories, mediaPlayer, shop_name), orderId);
                }
                // print receipt for kitchen
                if (chipKitchen.isChecked()) {
                    TIJobPrintBluetooth(docketStringModeler.startPrintingKitchen(ordersList.get(position), jsonObjectMenus, jsonObjectCategories, mediaPlayer, shop_name), orderId);
                }
            }
        });
        listViewOrders.setAdapter(adapter);

        // Show the popup window
        popupWindow.showAtLocation(findViewById(R.id.mainLayout), Gravity.CENTER, 0, 0);
    }



    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // changing orientation overwrites the UI settings to initial values
        updateUIBasedOnServiceStatus(); //TODO check if needed
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIBasedOnServiceStatus(); //TODO check if needed
    }
    private void showUpdatePopup() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Update Available")
                .setMessage("A new version of the app is available. Click update button to get latest version.")
                .setPositiveButton("Update", (dialog, which) -> {
                    // URL to open
                    // Create an intent with ACTION_VIEW
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(apkFile));

                    // Start the activity
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        //super.onBackPressed();
        showExitConfirmationDialog();
    }

    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to exit the app? It will stop any running process.")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Exit the app
                        stopService();
                        MainActivity.super.onBackPressed();

                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    private String[] arrayOf(String postNotifications) {
        String[] strArray = new String[1];
        strArray[0] = postNotifications;
        return strArray;
    }
    @Override
    public void onSuccess(String[] results) {
        String orderID;
        JSONObject dataObjectOrders;
        List<JSONObject> ordersList = new ArrayList<>();
        JSONObject jsonObjectOrders, jsonObjectMenus = null, jsonObjectCategories = null;
        if (results != null && results[0] != null && results[1] != null) {
            try {
                jsonObjectOrders = new JSONObject(results[0]);
                jsonObjectMenus = new JSONObject(results[1]);
                jsonObjectCategories = new JSONObject(results[2]);

                // Log the entire orders JSON before filtering
                Log.e("MainActivity", "Full Orders JSON: " + jsonObjectOrders.toString());

                JSONArray dataArrayOrders = jsonObjectOrders.getJSONArray("data");
                JSONArray filteredOrders = DocketStringModeler.filterPrintableOrders(dataArrayOrders, printedOrders);
                for (int i = 0; i < dataArrayOrders.length(); i++) {
                    dataObjectOrders = dataArrayOrders.getJSONObject(i);
                    ordersList.add(dataObjectOrders); // Add to orders list
                }

                for (int i = 0; i < filteredOrders.length(); i++) {
                    dataObjectOrders = filteredOrders.getJSONObject(i);
                    orderID = dataObjectOrders.getString("id");

                    //print receipt
                    if (chipReceipt.isChecked()) {
                        TIJobPrintBluetooth(docketStringModeler.startPrintingReceipt(dataObjectOrders, jsonObjectMenus, jsonObjectCategories, mediaPlayer, shop_name), orderID);
                    }
                    // print receipt for kitchen
                    if (chipKitchen.isChecked()) {
                        TIJobPrintBluetooth(docketStringModeler.startPrintingKitchen(dataObjectOrders, jsonObjectMenus, jsonObjectCategories, mediaPlayer, shop_name), orderID);
                    }

                    if (isLongerConnectionTime) { // stop loop if connection to printer has a problem
                        break;
                    }
                }

                button_reprint.setEnabled(true);

                Log.e("MainActivity", "Orders List Size: " + ordersList.size());

            } catch (JSONException e) {
                showError("JSON Error: " + e.getMessage());
            }

            // Show the popup when the button is clicked, only if ordersList is not empty
            if (!ordersList.isEmpty()) {
                JSONObject finalJsonObjectMenus = jsonObjectMenus;
                JSONObject finalJsonObjectCategories = jsonObjectCategories;
                button_reprint.setOnClickListener(v -> showOrdersPopup(ordersList, finalJsonObjectMenus, finalJsonObjectCategories));
            } else {
                showError("No printable orders found");
            }
        } else {
            showError("Failed to fetch data from web service");
        }
    }



    @Override
    public void onError(Exception exception) {
        if (exception != null) {
            if (exception instanceof SSLException) {
                showError("SSL Error: " + exception.getMessage());
            } else {
                showError("Error: " + exception.getMessage());
            }
            return;
        }
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, MainActivity.PERMISSION_BLUETOOTH);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, MainActivity.PERMISSION_BLUETOOTH_ADMIN);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, MainActivity.PERMISSION_BLUETOOTH_CONNECT);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, MainActivity.PERMISSION_BLUETOOTH_SCAN);
        } else {
            this.onBluetoothPermissionsGranted.onPermissionsGranted();
        }
    }

    private BluetoothConnection selectedDevice;

    public void browseBluetoothDevice(int selectedIndex, boolean manualOpen) {

        this.checkBluetoothPermissions(() -> {
            final BluetoothConnection[] bluetoothDevicesList = (new BluetoothPrintersConnections()).getList();

            if (bluetoothDevicesList != null) {
                final String[] items = new String[bluetoothDevicesList.length];
                int i = 0;
                for (BluetoothConnection device : bluetoothDevicesList) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    items[i] = device.getDevice().getName();
                    i++;
                }

                // Auto-select the device based on selectedIndex if it's within range
                if (selectedIndex >= 0 && selectedIndex < bluetoothDevicesList.length) {
                    selectedDevice = bluetoothDevicesList[selectedIndex];
                    isPrinterSelected = true;

                    // Update button appearance and store the selected index
                    int buttonColor = ContextCompat.getColor(this, R.color.light_green);
                    button_bluetooth_browse.setBackgroundColor(buttonColor);

                    // Save the selected index in SharedPreferences
                    SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("stored_index_printer", selectedIndex);
                    editor.apply();  // Save the index asynchronously

                    // Set button text to the selected device's name
                    Button button = findViewById(R.id.button_bluetooth_browse);
                    button.setText(items[selectedIndex]);

                    return; // Return after auto-selection to skip showing the dialog
                }
                if (!manualOpen){ // prevents opening of dialog onInit
                    return;
                }
                // Show dialog if selectedIndex is invalid (manual selection)
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                alertDialog.setTitle("Bluetooth printer selection");
                alertDialog.setItems(
                        items,
                        (dialogInterface, i1) -> {
                            selectedDevice = bluetoothDevicesList[i1];
                            isPrinterSelected = true;

                            // Update button appearance and store the selected index
                            int buttonColor = ContextCompat.getColor(this, R.color.light_green);
                            button_bluetooth_browse.setBackgroundColor(buttonColor);

                            SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putInt("stored_index_printer", i1);
                            editor.apply();

                            // Update button text to the selected device name
                            Button button = findViewById(R.id.button_bluetooth_browse);
                            button.setText(items[i1]);
                        }
                );

                AlertDialog alert = alertDialog.create();
                alert.show();
            }
        });
    }


//    /*==============================================================================================
//    ===================================Tasty Igniter Part=========================================
//    ==============================================================================================*/

    public void tiPrintMonitoring() {
        if (!Constants.isServiceActive) {
            // Start printing
            if (selectedDevice == null) {
                Toast.makeText(context, R.string.select_printer_text, Toast.LENGTH_SHORT).show();
                isPrinterSelected = false;
            } else {
                startService(); // This will handle starting and binding the service
                isPrinterSelected = true;
            }
        } else {
            // Stop printing
            stopService(); // This will handle stopping and unbinding the service
        }
    }

    private void startService() {
        if (!Constants.isServiceActive) {
            //bindToService();
            Log.d("MainActivity", "Service bound");

            // Update UI elements or perform other tasks related to starting the service
            int buttonColor = ContextCompat.getColor(this, R.color.light_green);
            if (button_ti_print != null) {
                button_ti_print.setBackgroundColor(buttonColor);
                button_ti_print.setText("Drucker ist Aktiv");
            }
            // Acquire wake lock to keep CPU running
            //MyWakeLockManager.acquireFullWakeLock(this); //TODO: remove as depricated
            MyWakeLockManager.acquirePartialWakeLock(this);
            Log.d("MainActivity", "Wake lock acquired");

            // Keep screen on
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.d("MainActivity", "Screen on flag set");

            // Start any other tasks related to the service
           // startWebServiceTask();
            startWebServiceTask(this, tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL);
            Log.d("MainActivity", "Web service task started");

            Constants.isServiceActive = true;
            isServiceStarted = true;
        }
    }

    private void stopService() {
        if (Constants.isServiceActive) {
            //stopAndUnbindServiceWithDelay();
            if (networkHelperViewModel.getNetworkHelper().isNetworkTaskRunning()) {
                networkHelperViewModel.getNetworkHelper().cancelNetworkTask();
            }
            networkHelperViewModel.cancelTimer();

            // Update UI elements or perform other tasks related to stopping the service
            int buttonColor = ContextCompat.getColor(this, R.color.colorAccent);
            if (button_ti_print != null) {
                button_ti_print.setBackgroundColor(buttonColor);
                button_ti_print.setText("Drucker ist inaktiv");
            }

            // Release wake lock to allow CPU to sleep
            //MyWakeLockManager.releaseFullWakeLock(); //TODO: remove as depricated
            MyWakeLockManager.releasePartialWakeLock();
            Log.d("MainActivity", "Wake lock released");

            // Remove keep screen on flag
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.d("MainActivity", "Screen on flag cleared");

            // Update service status flag
            Constants.isServiceActive = false;
            Log.d("MainActivity", "Service stopped, isServiceActive set to false");

            isServiceStarted = false;
        }
    }

    private void changeColorOfButton(int buttonColor, Button button) {
        int buttonColorID = ContextCompat.getColor(this, buttonColor);
        button.setBackgroundColor(buttonColorID);
    }

private void restartWebservice(int buttonColor){
//stopAndUnbindServiceWithDelay();
    if (networkHelperViewModel.getNetworkHelper().isNetworkTaskRunning()) {
        networkHelperViewModel.getNetworkHelper().cancelNetworkTask();
    }
    networkHelperViewModel.cancelTimer();
    startWebServiceTask(this, tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL);
    int buttonColorID = ContextCompat.getColor(this, buttonColor);
    if (button_ti_print != null) {
        button_ti_print.setBackgroundColor(buttonColorID); // set to yellow if connection to printer is broken
    }
}

    private void updateUIBasedOnServiceStatus() {
        // Retrieve saved login details from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        String savedUsername = sharedPreferences.getString("username", "");
        String savedPassword = sharedPreferences.getString("password", "");
        if (!savedUsername.isEmpty() && !savedPassword.isEmpty()){
            if (Constants.isServiceActive) {
                startService();
                // Update UI elements for active service
                int buttonColor = ContextCompat.getColor(this, R.color.colorPrimaryDark);
                if (button_ti_print != null) {
                    button_ti_print.setBackgroundColor(buttonColor);
                    button_ti_print.setText("Drucker ist Aktiv");
                }
                MyWakeLockManager.acquireFullWakeLock(this);
                Log.d("MainActivity", "Wake lock acquired");
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                Log.d("MainActivity", "Screen on flag set");
            } else {
                stopService();
                // Update UI elements for inactive service
                int buttonColor = ContextCompat.getColor(this, R.color.colorAccent);
                if (button_ti_print != null) {
                    button_ti_print.setBackgroundColor(buttonColor);
                    button_ti_print.setText("Drucker ist inaktiv");
                }
                MyWakeLockManager.releaseFullWakeLock();
                Log.d("MainActivity", "Wake lock released");
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                Log.d("MainActivity", "Screen on flag cleared");
            }
        }
    }

    public void startWebServiceTask(Activity activity, String ordersUrl, String menusUrl, String categoriesUrl) {
        Timer timer = networkHelperViewModel.getTimer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                String[] urls = {ordersUrl, menusUrl, categoriesUrl};

                Log.e("timer", "running: ");
                // Ensure the activity is still bound and the task can be executed
                networkHelperViewModel.getNetworkHelper().fetchData(urls, username, password, domain_shop, (NetworkHelper.NetworkCallback) activity);

            }
        }, delayPeriod, period);
    }

    private void showError(String errorMessage) {
        // Show the error message in an AlertDialog
        Toast.makeText(context, ""+errorMessage, Toast.LENGTH_SHORT).show();
    }
    public void TIJobPrintBluetooth(String print_info, String orderId) {
        this.checkBluetoothPermissions(() -> {
            new AsyncBluetoothEscPosPrint(
                    this,
                    new AsyncEscPosPrint.OnPrintFinished() {
                        @Override
                        public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                            Log.e("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : An error occurred !");
                            WebViewDialogFragment webViewDialogFragment =
                                    (WebViewDialogFragment) fragmentManager.findFragmentByTag("dialog_webview");
                            if (webViewDialogFragment != null) {
                                webViewDialogFragment.dismiss(); // need to close the dialog to update the communication on main thread
                            }
                                changeColorOfButton(R.color.warning, button_ti_print);
                                Toast.makeText(context, R.string.printer_connection_lost_text, Toast.LENGTH_LONG).show();
                                isLongerConnectionTime = true; //swiched to 5 minutes
                                button_ti_print.setText(R.string.printer_connection_disrupted_text);
                        }

                        @Override
                        public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                            Log.i("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : Print is finished !");
                            if (isLongerConnectionTime) { // connection to printer working
                                changeColorOfButton(R.color.light_green, button_ti_print);
                                Toast.makeText(context, R.string.printer_connection_reactivated_text, Toast.LENGTH_SHORT).show();
                                isLongerConnectionTime = false; // stop restarting the service
                                button_ti_print.setText("Drucker ist Aktiv");
                                //change button color
                                WebViewDialogFragment webViewDialogFragment =
                                        (WebViewDialogFragment) fragmentManager.findFragmentByTag("dialog_webview");
                                if (webViewDialogFragment != null) {
                                    // Update the colors dynamically
                                    webViewDialogFragment.setPrinterCircleColor(R.color.light_green);
                                }
                            }
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

    private void openWebpage(String action) {
        String url = "";
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
        showWebViewDialog(url);
    }

    private void showWebViewDialog(String url) {
        FragmentManager fm = getSupportFragmentManager();
        WebViewDialogFragment webViewDialogFragment;
        
        // Check if this is the Administration page and we have login credentials
        if (url.contains("/admin")) {
            // Get stored credentials for auto-login
            SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
            String savedUsername = sharedPreferences.getString("username", "");
            String savedPassword = sharedPreferences.getString("password", "");
            String savedDomain = sharedPreferences.getString("domain_shop", "");
            
            Log.d("MainActivity", "Admin page detected. Username: " + savedUsername + ", Domain: " + savedDomain);
            
            if (!savedUsername.isEmpty() && !savedPassword.isEmpty() && !savedDomain.isEmpty()) {
                Log.d("MainActivity", "Credentials found, creating WebView with auto-login");
                webViewDialogFragment = WebViewDialogFragment.newInstance(url, savedUsername, savedPassword, savedDomain);
            } else {
                Log.d("MainActivity", "No credentials found, creating WebView without auto-login");
                webViewDialogFragment = WebViewDialogFragment.newInstance(url);
            }
        } else {
            webViewDialogFragment = WebViewDialogFragment.newInstance(url);
        }

        // Set the initial circle colors based on the conditions
        int printerColor;
        int serviceColor;

        if (isPrinterSelected) {
            printerColor = ContextCompat.getColor(this, R.color.light_green);
        } else {
            printerColor = ContextCompat.getColor(this, R.color.colorError);
        }

        if (isLongerConnectionTime) {
            printerColor = ContextCompat.getColor(this, R.color.warning);
        }

        if (isServiceStarted) {
            serviceColor = ContextCompat.getColor(this, R.color.light_green);
        } else {
            serviceColor = ContextCompat.getColor(this, R.color.colorError);
        }

        // Pass the colors as arguments or set them after the fragment is shown
        webViewDialogFragment.setPrinterCircleColor(printerColor);
        webViewDialogFragment.setServiceCircleColor(serviceColor);

        webViewDialogFragment.show(fm, "dialog_webview");
    }

    private void openNativeKitchenDisplay() {
        Intent intent = new Intent(this, KitchenDisplayActivity.class);
        intent.putExtra("chip_receipt_checked", chipReceipt.isChecked());
        intent.putExtra("chip_kitchen_checked", chipKitchen.isChecked());
        startActivity(intent);
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

    private void LogoutUser() {
        // Clear stored login data
        clearStoredLogin();
        
        // Stop any running services
        stopService();
            
        // Clear web view cookies and logout from website
        clearWebViewCookies();
        
        // Reset UI
        if (textview_ti_header != null) textview_ti_header.setText("Please Login");
        if (button_ti_login != null) button_ti_login.setVisibility(View.VISIBLE);
        if (button_ti_logout != null) button_ti_logout.setVisibility(View.GONE);
        
        // Disable all buttons
        if (button_bluetooth_browse != null) button_bluetooth_browse.setEnabled(false);
        if (button_ti_print != null) button_ti_print.setEnabled(false);
        if (button_ti_clear_ids != null) button_ti_clear_ids.setEnabled(false);
        if (button_ti_kitchen_view != null) button_ti_kitchen_view.setEnabled(false);
        if (button_ti_native_kitchen != null) button_ti_native_kitchen.setEnabled(false);
        if (button_ti_dashboard != null) button_ti_dashboard.setEnabled(false);
        if (button_ti_landing_page != null) button_ti_landing_page.setEnabled(false);
        if (button_ti_testprint != null) button_ti_testprint.setEnabled(false);
        if (button_reprint != null) button_reprint.setEnabled(false);
        
        // Show login dialog
        LoginUser();
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
        private Button okButton;
        private TextView etError;
        private Spinner spinnerDomain;
        private EditText etUsername;
        private EditText etPassword;
        private List<ShopConfigUtils.Shop> shops;
        private ArrayAdapter<String> shopAdapter;
        
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater inflater = requireActivity().getLayoutInflater();
            View view = inflater.inflate(R.layout.popup_login_user, null);

            okButton = view.findViewById(R.id.button_ok);
            Button cancelButton = view.findViewById(R.id.button_cancel);
            spinnerDomain = view.findViewById(R.id.spinnerDomain);
            etUsername = view.findViewById(R.id.etUsername);
            etPassword = view.findViewById(R.id.etPassword);
            etError = view.findViewById(R.id.popup_error);

            // Load shops and populate spinner
            shops = ShopConfigUtils.getShops(getActivity());
            List<String> shopNames = new ArrayList<>();
            for (ShopConfigUtils.Shop shop : shops) {
                shopNames.add(shop.shop_name);
            }
            
            shopAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, shopNames);
            shopAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerDomain.setAdapter(shopAdapter);
            
            // Preselect last logged in shop
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
            String lastDomain = sharedPreferences.getString("domain_shop", "");
            if (!lastDomain.isEmpty()) {
                for (int i = 0; i < shops.size(); i++) {
                    if (shops.get(i).domain_shop.equals(lastDomain)) {
                        spinnerDomain.setSelection(i);
                        break;
                    }
                }
            }

            okButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String username = etUsername.getText().toString(); //TODO wenn man Enter Druckt dann entsteht eine neue Zeile
                    String password = etPassword.getText().toString();
                    String validCredentials = checkValidCredentials(username, password);
                    if (validCredentials.equals("OK")) {
                        //reinitialize the variables
                        ((MainActivity)getActivity()).initilizeURLs();

                        button_bluetooth_browse.setEnabled(true);
                        button_ti_print.setEnabled(true);
                        button_ti_clear_ids.setEnabled(true);
                        button_reprint.setEnabled(true);
                        button_ti_testprint.setEnabled(true);
                        //web browser buttons
                        button_ti_kitchen_view.setEnabled(true);
                        button_ti_dashboard.setEnabled(true);
                        button_ti_landing_page.setEnabled(true);
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
            // Show loading state
            etError.setVisibility(View.GONE);
            okButton.setEnabled(false);
            okButton.setText("Authenticating...");

            // Get selected shop from spinner
            int selectedPosition = spinnerDomain.getSelectedItemPosition();
            if (selectedPosition == -1 || selectedPosition >= shops.size()) {
                etError.setVisibility(View.VISIBLE);
                etError.setText("Please select a shop");
                okButton.setEnabled(true);
                okButton.setText("Login");
                return "SHOP_REQUIRED";
            }

            ShopConfigUtils.Shop selectedShop = shops.get(selectedPosition);
            final String finalDomain = selectedShop.domain_shop;

            // Authenticate with TastyIgniter API
            NetworkHelper networkHelper = new NetworkHelper(getActivity());
            networkHelper.generateTokenAsync(username, password, finalDomain, new NetworkHelper.TokenCallback() {
                @Override
                public void onTokenGenerated(String token) {
                    // Authentication successful
                    getActivity().runOnUiThread(() -> {
                        // Find matching shop configuration
                        ShopConfigUtils.Shop matchingShop = ShopConfigUtils.getShopByDomain(getActivity(), finalDomain);
                        if (matchingShop != null) {
                            // Save login data
                            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putInt("shop_id", matchingShop.shop_id);
                            editor.putString("domain_shop", matchingShop.domain_shop);
                            editor.putString("token", token);
                            editor.putString("username", username);
                            editor.putString("password", password);
                            editor.apply();

                            // Update global variables
                            shop_id = matchingShop.shop_id;
                            domain_shop = matchingShop.domain_shop;
                            domain_website = matchingShop.domain_website;
                            shop_name = matchingShop.shop_name;
                            tiKitchenViewURL = matchingShop.kitchen_view;
                            location_id = matchingShop.location_id;
                            textview_ti_header.setText(shop_name);

                            // Set API endpoints
                            tiOrdersEndpointURL = domain_shop + "/api/orders?sort=order_id desc&pageLimit=50&location=" + location_id;
                            tiMenusEndpointURL = domain_shop + "/api/menus?include=categories&pageLimit=5000&location=" + location_id;
                            tiCategoriesEndpointURL = domain_shop + "/api/categories?location=" + location_id;
                            tiDashboardURL = domain_shop + "/admin";
                            tiLandingPage = domain_website + "/";

                            // Reinitialize URLs and enable buttons
                            ((MainActivity) getActivity()).initilizeURLs();
                            button_bluetooth_browse.setEnabled(true);
                            button_ti_print.setEnabled(true);
                            button_ti_clear_ids.setEnabled(true);
                            button_reprint.setEnabled(true);
                            button_ti_testprint.setEnabled(true);
                            button_ti_kitchen_view.setEnabled(true);
                            button_ti_dashboard.setEnabled(true);
                            button_ti_landing_page.setEnabled(true);
                            dismiss();
                        } else {
                            etError.setVisibility(View.VISIBLE);
                            etError.setText("Shop configuration not found for this domain");
                            okButton.setEnabled(true);
                            okButton.setText("Login");
                        }
                    });
                }

                @Override
                public void onTokenError(Exception exception) {
                    // Authentication failed
                    getActivity().runOnUiThread(() -> {
                        etError.setVisibility(View.VISIBLE);
                        etError.setText("Authentication failed: " + exception.getMessage());
                        okButton.setEnabled(true);
                        okButton.setText("Login");
                    });
                }
            });

            return "PROCESSING";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
       // stopService();
      //  unbindFromService();

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

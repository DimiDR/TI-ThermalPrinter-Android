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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
import com.google.android.material.appbar.MaterialToolbar;

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
    private static Button button_ti_native_kitchen;
    private static Button button_ti_updates;
    private static Button button_ti_testprint;
    private Button button_ti_login;
    private Button button_ti_logout;
    private static TextView textview_ti_header;
    private TextView orderCountBadge;
    private int openOrdersCount = 0;
    private boolean isKitchenDisplayOpen = false;
    private boolean userSelected = false; // TODO: no buttons should work if user is not selected
    private static Integer shop_id;
    private static String domain_shop;
    private static String domain_website = "";
    private static String username = "";
    private static String password = "";
    private static String shop_name = "";
    private static Integer location_id;
    private static String tiOrdersEndpointURL = "";
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
    // Store orders data for reprint functionality
    private List<JSONObject> cachedOrdersList = new ArrayList<>();
    private JSONObject cachedJsonObjectMenus;
    private JSONObject cachedJsonObjectCategories;
    private boolean shouldShowReprintPopup = false; // Flag to show popup after fetching orders

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        networkHelperViewModel = new ViewModelProvider(this).get(NetworkHelperViewModel.class);
        
        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                getSupportActionBar().setDisplayShowHomeEnabled(false);
            }
        } else {
            Log.e("MainActivity", "Toolbar not found! Check if R.id.toolbar exists in the layout");
        }

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
        button_ti_native_kitchen = this.findViewById(R.id.button_ti_native_kitchen);
        if (button_ti_native_kitchen != null) {
            button_ti_native_kitchen.setOnClickListener(view -> {
                Intent intent = new Intent(this, KitchenDisplayActivity.class);
                startActivity(intent);
            });
        } else {
            Log.e("MainActivity", "button_ti_native_kitchen is null - check layout file");
        }
        button_ti_updates =  this.findViewById(R.id.button_ti_updates);
        if (button_ti_updates != null) {
            button_ti_updates.setOnClickListener(view -> showUpdatePopup());
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
        orderCountBadge = this.findViewById(R.id.order_count_badge);
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
            button_ti_updates.setText(R.string.update_available_button);
            button_ti_updates.setBackgroundColor(ContextCompat.getColor(this, R.color.colorError));
            if (!networkHelperViewModel.isUpdatePopupShown()) {
                showUpdatePopup();
                networkHelperViewModel.setUpdatePopupShown(true);
            }
        }else{
            button_ti_updates.setEnabled(false);
            // show current version on the screen
            button_ti_updates.setText(getString(R.string.version) + " " + getIntent().getStringExtra("currentAppVersion"));
        }

        //reprint orders - Set up button click listener to handle reprint functionality
        if (button_reprint != null) {
            button_reprint.setOnClickListener(v -> handleReprintOrders());
        }
        
        // Handle reprint intent from DashboardActivity - show popup after fetching orders
        String action = getIntent().getStringExtra("action");
        if ("reprint".equals(action)) {
            shouldShowReprintPopup = true;
            // Trigger fetching orders if URLs are already initialized
            if (tiOrdersEndpointURL != null && !tiOrdersEndpointURL.isEmpty() && 
                tiMenusEndpointURL != null && !tiMenusEndpointURL.isEmpty() &&
                tiCategoriesEndpointURL != null && !tiCategoriesEndpointURL.isEmpty()) {
                // Fetch orders data to show popup - NetworkHelper will use token from SharedPreferences
                String[] urls = {tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL};
                networkHelperViewModel.getNetworkHelper().fetchData(urls, "", "", domain_shop, this);
            }
        }

    }

    public void initilizeURLs() {
        // Check for persistent login
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        String savedDomain = sharedPreferences.getString("domain_shop", "");
        String savedToken = sharedPreferences.getString("token", "");

        //apply automatic printer selection of last selected device
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        storedPrinterIndex = prefs.getInt("stored_index_printer", -1);
        browseBluetoothDevice(storedPrinterIndex, false);
        
        // Update test print button state after printer selection attempt
        updateTestPrintButtonState();

        if (savedDomain.isEmpty() || savedToken.isEmpty()) {
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
                    // Configure from stored domain and location
                    domain_shop = savedDomain;
                    location_id = sharedPreferences.getInt("location_id", 1);
                    shop_name = savedDomain;
                    if (textview_ti_header != null) textview_ti_header.setText(shop_name);
                    tiOrdersEndpointURL = domain_shop + "/api/orders?sort=order_id desc&pageLimit=50&location=" + location_id;
                    tiMenusEndpointURL = domain_shop + "/api/menus?include=categories&pageLimit=5000&location=" + location_id;
                    tiCategoriesEndpointURL = domain_shop + "/api/categories?location=" + location_id;
                    enableAllButtons();
                    
                    // If reprint was requested, fetch orders now that URLs are initialized
                    if (shouldShowReprintPopup) {
                        String[] urls = {tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL};
                        // NetworkHelper will use token from SharedPreferences
                        networkHelperViewModel.getNetworkHelper().fetchData(urls, "", "", domain_shop, MainActivity.this);
                    }
                    
                    // Start initial refresh after 1 second delay
                    new Handler().postDelayed(() -> {
                        if (Constants.isServiceActive) {
                            startWebServiceTask(MainActivity.this, tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL);
                        }
                    }, 1000);
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
        if (button_ti_native_kitchen != null) button_ti_native_kitchen.setEnabled(true);
        updateTestPrintButtonState();
        if (button_reprint != null) button_reprint.setEnabled(true);
        
        // Show logout button and hide login button
        if (button_ti_login != null) button_ti_login.setVisibility(View.GONE);
        if (button_ti_logout != null) button_ti_logout.setVisibility(View.VISIBLE);
        
    }
    
    void updateTestPrintButtonState() {
        if (button_ti_testprint != null) {
            button_ti_testprint.setEnabled(isPrinterSelected && selectedDevice != null);
        }
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

    private void handleReprintOrders() {
        // Always fetch fresh orders for reprint to ensure we get the last 50 orders
        if (tiOrdersEndpointURL != null && !tiOrdersEndpointURL.isEmpty() && 
            tiMenusEndpointURL != null && !tiMenusEndpointURL.isEmpty() &&
            tiCategoriesEndpointURL != null && !tiCategoriesEndpointURL.isEmpty()) {
            shouldShowReprintPopup = true;
            // Ensure we request last 50 orders for reprint
            String reprintOrdersURL = domain_shop + "/api/orders?sort=order_id desc&pageLimit=50&location=" + location_id;
            String[] urls = {reprintOrdersURL, tiMenusEndpointURL, tiCategoriesEndpointURL};
            networkHelperViewModel.getNetworkHelper().fetchData(urls, "", "", domain_shop, this);
        } else {
            // Fallback to cached data if URLs not ready
            if (!cachedOrdersList.isEmpty() && cachedJsonObjectMenus != null && cachedJsonObjectCategories != null) {
                showOrdersPopup(cachedOrdersList, cachedJsonObjectMenus, cachedJsonObjectCategories);
            } else {
                Toast.makeText(this, "Please wait for orders to load", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showOrdersPopup(List<JSONObject> ordersList, JSONObject jsonObjectMenus, JSONObject jsonObjectCategories) {
        // reprint functionality
        // Log the total number of orders received
        Log.d("ReprintOrders", "Total orders received: " + ordersList.size());
        
        // Limit to last 50 orders without filtering
        int startIndex = Math.max(0, ordersList.size() - 50);
        List<JSONObject> displayOrdersList = new ArrayList<>(ordersList.subList(startIndex, ordersList.size()));
        
        Log.d("ReprintOrders", "Displaying orders: " + displayOrdersList.size() + " (from index " + startIndex + " to " + ordersList.size() + ")");
        
        // Inflate the popup layout
        View popupView = getLayoutInflater().inflate(R.layout.popup_orders, null);

        // Create the popup window
        int width = LinearLayout.LayoutParams.MATCH_PARENT; // Set width to match_parent
        int height = LinearLayout.LayoutParams.MATCH_PARENT; // Use match_parent so ListView can scroll properly
        boolean focusable = true; // lets taps outside the popup also dismiss it
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);

        // Set background drawable for the popup window
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popupWindow.setElevation(10.0f); // Adds shadow effect

        // Setup toolbar navigation click listener
        MaterialToolbar toolbar = popupView.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> popupWindow.dismiss());
        }
        
        // Check if printer is selected
        boolean printerAvailable = isPrinterSelected && selectedDevice != null;
        
        // Set up the ListView
        ListView listViewOrders = popupView.findViewById(R.id.listViewOrders);
        OrdersAdapter adapter = new OrdersAdapter(this, displayOrdersList, new OrdersAdapter.PrintButtonClickListener() {
            @Override
            public void onPrintButtonClick(int position, String orderId) {
                if (!printerAvailable) {
                    Toast.makeText(context, R.string.select_printer_text, Toast.LENGTH_SHORT).show();
                    return;
                }
                //print receipt
                if (chipReceipt.isChecked()) {
                    TIJobPrintBluetooth(docketStringModeler.startPrintingReceipt(displayOrdersList.get(position), jsonObjectMenus, jsonObjectCategories, mediaPlayer, shop_name), orderId);
                }
                // print receipt for kitchen
                if (chipKitchen.isChecked()) {
                    TIJobPrintBluetooth(docketStringModeler.startPrintingKitchen(displayOrdersList.get(position), jsonObjectMenus, jsonObjectCategories, mediaPlayer, shop_name), orderId);
                }
            }
        }, printerAvailable);
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
        
        // Update test print button state when activity resumes
        updateTestPrintButtonState();
        
        // Check if KitchenDisplayActivity was closed and restart timer if needed
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        boolean kitchenOpen = sharedPreferences.getBoolean("kitchen_display_open", false);
        if (!kitchenOpen && Constants.isServiceActive && !isKitchenDisplayOpen) {
            // KitchenDisplayActivity was closed, restart MainActivity timer
            startWebServiceTask(this, tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL);
        }
        isKitchenDisplayOpen = kitchenOpen;
    }
    private void showUpdatePopup() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.update_available)
                .setMessage(R.string.update_available_message)
                .setPositiveButton(R.string.update, (dialog, which) -> {
                    // URL to open
                    // Create an intent with ACTION_VIEW
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(apkFile));

                    // Start the activity
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
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
                .setMessage(R.string.exit_confirmation)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Exit the app
                        stopService();
                        MainActivity.super.onBackPressed();

                    }
                })
                .setNegativeButton(R.string.cancel, null)
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
                Log.d("ReprintOrders", "API returned " + dataArrayOrders.length() + " orders");
                JSONArray filteredOrders = DocketStringModeler.filterPrintableOrders(dataArrayOrders, printedOrders);
                
                // Count open orders (not completed - status_id != 5)
                int openOrderCount = 0;
                for (int i = 0; i < dataArrayOrders.length(); i++) {
                    dataObjectOrders = dataArrayOrders.getJSONObject(i);
                    ordersList.add(dataObjectOrders); // Add to orders list
                    
                    // Check if order is not completed
                    try {
                        JSONObject attributes = dataObjectOrders.optJSONObject("attributes");
                        JSONObject orderData = attributes != null ? attributes : dataObjectOrders;
                        int statusId = orderData.optInt("status_id", -1);
                        if (statusId != 5) { // Not completed
                            openOrderCount++;
                        }
                    } catch (Exception e) {
                        // If there's an error checking status, include the order
                        openOrderCount++;
                    }
                }
                
                // Update the order count badge
                updateOrderCountBadge(openOrderCount);

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

                // Cache orders data for reprint functionality
                cachedOrdersList = ordersList;
                cachedJsonObjectMenus = jsonObjectMenus;
                cachedJsonObjectCategories = jsonObjectCategories;
                
                Log.d("ReprintOrders", "Cached orders for reprint: " + cachedOrdersList.size());

                // Show popup automatically if requested (e.g., from DashboardActivity)
                if (shouldShowReprintPopup && !ordersList.isEmpty()) {
                    shouldShowReprintPopup = false;
                    showOrdersPopup(ordersList, jsonObjectMenus, jsonObjectCategories);
                }

            } catch (JSONException e) {
                showError("JSON Error: " + e.getMessage());
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

            // Handle null case (Bluetooth disabled or not available)
            if (bluetoothDevicesList == null) {
                if (manualOpen) {
                    runOnUiThread(() -> {
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                        alertDialog.setTitle(R.string.bluetooth_error);
                        alertDialog.setMessage(R.string.bluetooth_error_message);
                        alertDialog.setPositiveButton(R.string.ok, null);
                        alertDialog.show();
                    });
                }
                return;
            }

            // Handle empty array case (no printers found)
            if (bluetoothDevicesList.length == 0) {
                if (manualOpen) {
                    runOnUiThread(() -> {
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                        alertDialog.setTitle(R.string.no_bluetooth_printers_found);
                        alertDialog.setMessage(R.string.no_bluetooth_printers_message);
                        alertDialog.setPositiveButton(R.string.ok, null);
                        alertDialog.show();
                    });
                }
                return;
            }

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

                // Update test print button state
                updateTestPrintButtonState();

                return; // Return after auto-selection to skip showing the dialog
            }
            if (!manualOpen){ // prevents opening of dialog onInit
                return;
            }
            // Show dialog if selectedIndex is invalid (manual selection)
            runOnUiThread(() -> {
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                alertDialog.setTitle(R.string.bluetooth_printer_selection);
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

                            // Update test print button state
                            updateTestPrintButtonState();
                        }
                );

                AlertDialog alert = alertDialog.create();
                alert.show();
            });
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
                button_ti_print.setText(R.string.printer_active);
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
                button_ti_print.setText(R.string.printer_inactive);
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
                    button_ti_print.setText(R.string.printer_active);
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
                    button_ti_print.setText(R.string.printer_inactive);
                }
                MyWakeLockManager.releaseFullWakeLock();
                Log.d("MainActivity", "Wake lock released");
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                Log.d("MainActivity", "Screen on flag cleared");
            }
        }
    }

    public void startWebServiceTask(Activity activity, String ordersUrl, String menusUrl, String categoriesUrl) {
        startWebServiceTask(activity, ordersUrl, menusUrl, categoriesUrl, getRefreshInterval());
    }
    
    public void startWebServiceTask(Activity activity, String ordersUrl, String menusUrl, String categoriesUrl, long refreshInterval) {
        Timer timer = networkHelperViewModel.getTimer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // Check if user is still logged in before running refresh
                SharedPreferences sharedPreferences = activity.getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
                String savedToken = sharedPreferences.getString("token", "");
                if (savedToken.isEmpty()) {
                    Log.d("MainActivity", "User not logged in, skipping refresh");
                    return;
                }
                
                // Check if KitchenDisplayActivity is open
                boolean kitchenOpen = sharedPreferences.getBoolean("kitchen_display_open", false);
                if (kitchenOpen) {
                    Log.d("MainActivity", "KitchenDisplayActivity is open, skipping MainActivity refresh");
                    return;
                }

                String[] urls = {ordersUrl, menusUrl, categoriesUrl};

                Log.e("timer", "running: ");
                // Ensure the activity is still bound and the task can be executed
                networkHelperViewModel.getNetworkHelper().fetchData(urls, username, password, domain_shop, (NetworkHelper.NetworkCallback) activity);

            }
        }, delayPeriod, refreshInterval);
    }
    
    private long getRefreshInterval() {
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        boolean kitchenOpen = sharedPreferences.getBoolean("kitchen_display_open", false);
        return kitchenOpen ? Constants.REFRESH_INTERVAL_KITCHEN_OPEN : Constants.REFRESH_INTERVAL_KITCHEN_CLOSED;
    }

    private void showError(String errorMessage) {
        // Show the error message in an AlertDialog
        Toast.makeText(context, ""+errorMessage, Toast.LENGTH_SHORT).show();
    }
    
    private void updateOrderCountBadge(int count) {
        if (orderCountBadge != null) {
            if (count > 0) {
                orderCountBadge.setText(String.valueOf(count));
                orderCountBadge.setVisibility(View.VISIBLE);
            } else {
                orderCountBadge.setVisibility(View.GONE);
            }
        }
        openOrdersCount = count;
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
                                button_ti_print.setText(R.string.printer_active);
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
                case "APIEndpoint": //TODO why do I need to open API on the webpage?
                    url = tiOrdersEndpointURL;
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
        if (textview_ti_header != null) textview_ti_header.setText(R.string.login_text);
        if (button_ti_login != null) button_ti_login.setVisibility(View.VISIBLE);
        if (button_ti_logout != null) button_ti_logout.setVisibility(View.GONE);
        
        // Disable all buttons
        if (button_bluetooth_browse != null) button_bluetooth_browse.setEnabled(false);
        if (button_ti_print != null) button_ti_print.setEnabled(false);
        if (button_ti_clear_ids != null) button_ti_clear_ids.setEnabled(false);
        if (button_ti_native_kitchen != null) button_ti_native_kitchen.setEnabled(false);
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
        private EditText etDomain;
        private EditText etUsername;
        private EditText etPassword;
        
        /**
         * Check if internet connectivity is available
         */
        private boolean isInternetAvailable() {
            try {
                ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connectivityManager != null) {
                    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
                }
            } catch (Exception e) {
                Log.e("LoginUserDialogFragment", "Error checking internet connectivity", e);
            }
            return false;
        }
        
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater inflater = requireActivity().getLayoutInflater();
            View view = inflater.inflate(R.layout.popup_login_user, null);

            okButton = view.findViewById(R.id.button_ok);
            Button cancelButton = view.findViewById(R.id.button_cancel);
            etDomain = view.findViewById(R.id.etDomain);
            etUsername = view.findViewById(R.id.etUsername);
            etPassword = view.findViewById(R.id.etPassword);
            etError = view.findViewById(R.id.popup_error);
            
            // Prefill last domain if available
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
            String lastDomain = sharedPreferences.getString("domain_shop", "");
            if (!lastDomain.isEmpty()) {
                etDomain.setText(lastDomain);
            }
            
            // Check internet connectivity when dialog opens
            if (!isInternetAvailable()) {
                etError.setVisibility(View.VISIBLE);
                etError.setText(getString(R.string.no_internet_warning));
            }

            okButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String domainInput = etDomain.getText().toString();
                    String username = etUsername.getText().toString(); //TODO wenn man Enter Druckt dann entsteht eine neue Zeile
                    String password = etPassword.getText().toString();
                    String validCredentials = checkValidCredentials(domainInput, username, password);
                    if (validCredentials.equals("OK")) {
                        //reinitialize the variables
                        ((MainActivity)getActivity()).initilizeURLs();

                        button_bluetooth_browse.setEnabled(true);
                        button_ti_print.setEnabled(true);
                        button_ti_clear_ids.setEnabled(true);
                        button_reprint.setEnabled(true);
                        ((MainActivity)getActivity()).updateTestPrintButtonState();
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

        private String checkValidCredentials(String domainInput, String username, String password) {
            // Show loading state
            etError.setVisibility(View.GONE);
            okButton.setEnabled(false);
            okButton.setText(getString(R.string.authenticating));
            
            // Check internet connectivity first
            if (!isInternetAvailable()) {
                etError.setVisibility(View.VISIBLE);
                etError.setText(getString(R.string.no_internet_error));
                okButton.setEnabled(true);
                okButton.setText(getString(R.string.login_button));
                return "NO_INTERNET";
            }
            
            // Validate domain input
            if (domainInput == null || domainInput.trim().isEmpty()) {
                etError.setVisibility(View.VISIBLE);
                etError.setText(getString(R.string.domain_required));
                okButton.setEnabled(true);
                okButton.setText(getString(R.string.login_button));
                return "DOMAIN_REQUIRED";
            }
            // Normalize domain: remove all whitespace, trailing slashes, and ensure proper format
            String domainNormalized = domainInput.trim().replaceAll("\\s+", ""); // remove all whitespace
            domainNormalized = domainNormalized.replaceAll("/+$", ""); // remove trailing slashes
            
            // Validate URL format
            if (domainNormalized.isEmpty()) {
                etError.setVisibility(View.VISIBLE);
                etError.setText(getString(R.string.invalid_domain));
                okButton.setEnabled(true);
                okButton.setText(getString(R.string.login_button));
                return "INVALID_DOMAIN";
            }
            
            if (!domainNormalized.startsWith("http://") && !domainNormalized.startsWith("https://")) {
                domainNormalized = "https://" + domainNormalized;
            }
            final String finalDomain = domainNormalized;

            // Authenticate with TastyIgniter API
            NetworkHelper networkHelper = new NetworkHelper(getActivity());
            networkHelper.generateTokenAsync(username, password, finalDomain, new NetworkHelper.TokenCallback() {
                @Override
                public void onTokenGenerated(String token) {
                    // Authentication successful
                    getActivity().runOnUiThread(() -> {
                        // Save login data with manual domain
                        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putInt("shop_id", 1); // legacy field, no longer used for selection
                        editor.putString("domain_shop", finalDomain);
                        editor.putString("token", token);
                        editor.putString("username", username);
                        editor.putString("password", password);
                        // set a default location_id if absent
                        if (!sharedPreferences.contains("location_id")) {
                            editor.putInt("location_id", 1);
                        }
                        editor.apply();

                        // Update globals minimally
                        domain_shop = finalDomain;
                        shop_name = finalDomain; // show domain as header
                        location_id = sharedPreferences.getInt("location_id", 1);
                        if (textview_ti_header != null) textview_ti_header.setText(shop_name);

                        // Set API endpoints
                        tiOrdersEndpointURL = domain_shop + "/api/orders?sort=order_id desc&pageLimit=50&location=" + location_id;
                        tiMenusEndpointURL = domain_shop + "/api/menus?include=categories&pageLimit=5000&location=" + location_id;
                        tiCategoriesEndpointURL = domain_shop + "/api/categories?location=" + location_id;

                        // Reinitialize URLs and enable buttons
                        ((MainActivity) getActivity()).initilizeURLs();
                        button_bluetooth_browse.setEnabled(true);
                        button_ti_print.setEnabled(true);
                        button_ti_clear_ids.setEnabled(true);
                        button_reprint.setEnabled(true);
                        ((MainActivity) getActivity()).updateTestPrintButtonState();
                        dismiss();
                        
                        // Navigate to Kitchen Display after successful login
                        Intent intent = new Intent(getActivity(), KitchenDisplayActivity.class);
                        startActivity(intent);
                        getActivity().finish();
                    });
                }

                @Override
                public void onTokenError(Exception exception) {
                    // Authentication failed
                    getActivity().runOnUiThread(() -> {
                        etError.setVisibility(View.VISIBLE);
                        String errorMsg = exception.getMessage();
                        
                        // Check if it's a no internet error (check both English and German)
                        if (errorMsg != null && (errorMsg.contains("No internet connection") || 
                            errorMsg.contains("Keine Internetverbindung"))) {
                            etError.setText(getString(R.string.no_internet_error));
                        }
                        // Use the error message directly if it's already user-friendly (from NetworkHelper)
                        // Check for both English and German error messages
                        else if (errorMsg != null && (errorMsg.contains("Cannot connect") || 
                            errorMsg.contains("Connection timeout") || 
                            errorMsg.contains("Connection refused") ||
                            errorMsg.contains("SSL/TLS") ||
                            errorMsg.contains("DNS Resolution Failed") ||
                            errorMsg.contains("DNS-Auflösung") ||
                            errorMsg.contains("Verbindungszeitüberschreitung") ||
                            errorMsg.contains("Verbindung abgelehnt") ||
                            errorMsg.contains("SSL/TLS-Fehler") ||
                            errorMsg.contains("Hostname kann nicht aufgelöst werden"))) {
                            etError.setText(errorMsg);
                        } else {
                            etError.setText(getString(R.string.authentication_failed) + ": " + (errorMsg != null ? errorMsg : getString(R.string.unknown_error)));
                        }
            okButton.setEnabled(true);
            okButton.setText(getString(R.string.login_button));
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
        if (selectedDevice == null || !isPrinterSelected) {
            Toast.makeText(context, R.string.select_printer_text, Toast.LENGTH_SHORT).show();
            return false;
        }
        
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

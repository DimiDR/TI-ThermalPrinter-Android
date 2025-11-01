package com.dantsu.thermalprinter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.dantsu.thermalprinter.helpClasses.NetworkHelper;
import com.dantsu.thermalprinter.helpClasses.ShopConfigUtils;
import com.dantsu.thermalprinter.helpClasses.DocketStringModeler;
import com.dantsu.thermalprinter.model.NetworkHelperViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import android.media.MediaPlayer;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
import com.dantsu.thermalprinter.async.AsyncBluetoothEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrinter;
import com.dantsu.thermalprinter.helpClasses.IdManager;
import android.annotation.SuppressLint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class DashboardActivity extends AppCompatActivity implements NetworkHelper.NetworkCallback {
    
    private MaterialToolbar toolbar;
    private TextView textTotalOrders;
    private TextView textInProgressOrders;
    private TextView textCompletedOrders;
    private View viewPrinterStatus;
    private TextView textPrinterStatus;
    private View viewServiceStatus;
    private TextView textServiceStatus;
    private TextView textShopName;
    private Button buttonViewOrders;
    private Button buttonReprintOrders;
    private Button buttonSettings;
    private Button buttonAutoPrint;
    private SwipeRefreshLayout swipeRefreshLayout;
    
    private NetworkHelperViewModel networkHelperViewModel;
    private Timer refreshTimer;
    private Handler mainHandler;
    
    // Shop configuration
    private String domain_shop;
    private String tiOrdersEndpointURL;
    private String tiMenusEndpointURL;
    private String tiCategoriesEndpointURL;
    private Integer location_id;
    private String shop_name;
    
    // Statistics
    private int totalOrders = 0;
    private int inProgressOrders = 0;
    private int completedOrders = 0;
    
    // Sound notification
    private MediaPlayer mediaPlayer;
    private boolean isInitialLoad = true;
    
    // Printing functionality
    private BluetoothConnection selectedDevice;
    private DocketStringModeler docketStringModeler;
    private boolean chipReceiptChecked = true;  // default to receipt
    private boolean chipKitchenChecked = false; // default to not kitchen
    private boolean isLongerConnectionTime = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        
        // Initialize views
        initializeViews();
        
        // Setup toolbar
        setupToolbar();
        
        // Initialize ViewModel
        networkHelperViewModel = new ViewModelProvider(this).get(NetworkHelperViewModel.class);
        mainHandler = new Handler();
        
        // Load shop configuration
        loadShopConfiguration();
        
        // Setup button listeners
        setupButtonListeners();
        
        // Setup pull-to-refresh
        setupPullToRefresh();
        
        // Update button states based on printer availability
        updateButtonStates();
        
        // Initialize MediaPlayer for sound notifications
        mediaPlayer = MediaPlayer.create(this, R.raw.newordersound);
        
        // Initialize printing components
        docketStringModeler = new DocketStringModeler();
        loadPrinterConfiguration();
        loadChipPreferences();
        
        // Load initial data
        refreshData();
        
        // Start foreground-based API refresh
        startForegroundRefresh();
    }
    
    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        textTotalOrders = findViewById(R.id.textTotalOrders);
        textInProgressOrders = findViewById(R.id.textInProgressOrders);
        textCompletedOrders = findViewById(R.id.textCompletedOrders);
        viewPrinterStatus = findViewById(R.id.viewPrinterStatus);
        textPrinterStatus = findViewById(R.id.textPrinterStatus);
        viewServiceStatus = findViewById(R.id.viewServiceStatus);
        textServiceStatus = findViewById(R.id.textServiceStatus);
        textShopName = findViewById(R.id.textShopName);
        buttonViewOrders = findViewById(R.id.buttonViewOrders);
        buttonReprintOrders = findViewById(R.id.buttonReprintOrders);
        buttonSettings = findViewById(R.id.buttonSettings);
        buttonAutoPrint = findViewById(R.id.buttonAutoPrint);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
    }
    
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        // Dashboard is the first screen, so back button should exit app
        // Keep back button visible but remove navigation icon since it's the root
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setDisplayShowHomeEnabled(false);
        }
        
        // Handle back button press to exit app
        toolbar.setNavigationOnClickListener(v -> {
            finish();
        });
        
        // Override onBackPressed to exit app cleanly
    }
    
    @Override
    public void onBackPressed() {
        // Exit app when back is pressed from Dashboard (first screen)
        super.onBackPressed();
    }
    
    private void loadShopConfiguration() {
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        domain_shop = sharedPreferences.getString("domain_shop", "");
        location_id = sharedPreferences.getInt("location_id", 1);
        
        if (domain_shop.isEmpty()) {
            Toast.makeText(this, R.string.no_shop_configuration, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // Set API endpoints
        tiOrdersEndpointURL = domain_shop + "/api/orders?sort=order_id desc&pageLimit=50&location=" + location_id;
        tiMenusEndpointURL = domain_shop + "/api/menus?include=categories&pageLimit=5000&location=" + location_id;
        tiCategoriesEndpointURL = domain_shop + "/api/categories?location=" + location_id;
        
        // Get shop name
        List<ShopConfigUtils.Shop> shops = ShopConfigUtils.getShops(this);
        for (ShopConfigUtils.Shop shop : shops) {
            if (shop.domain_shop.equals(domain_shop)) {
                shop_name = shop.shop_name;
                textShopName.setText(shop_name);
                break;
            }
        }
    }
    
    private void loadPrinterConfiguration() {
        // Load printer configuration from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        int storedPrinterIndex = prefs.getInt("stored_index_printer", -1);
        
        if (storedPrinterIndex >= 0) {
            BluetoothConnection[] bluetoothDevicesList = (new BluetoothPrintersConnections()).getList();
            if (bluetoothDevicesList != null && storedPrinterIndex < bluetoothDevicesList.length) {
                selectedDevice = bluetoothDevicesList[storedPrinterIndex];
            }
        }
    }
    
    private void loadChipPreferences() {
        // Load chip preferences from SharedPreferences (set by MainActivity)
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        chipReceiptChecked = prefs.getBoolean("chip_receipt_checked", true);  // default to receipt
        chipKitchenChecked = prefs.getBoolean("chip_kitchen_checked", false); // default to not kitchen
    }
    
    private void setupButtonListeners() {
        buttonViewOrders.setOnClickListener(v -> {
            Intent intent = new Intent(this, KitchenDisplayActivity.class);
            startActivity(intent);
        });
        
        buttonReprintOrders.setOnClickListener(v -> {
            // Navigate to MainActivity for reprint functionality
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("action", "reprint");
            startActivity(intent);
        });
        
        buttonSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        });
        
        buttonAutoPrint.setOnClickListener(v -> toggleAutoPrint());
    }
    
    private void setupPullToRefresh() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                refreshData();
            });
        }
    }
    
    private void updateButtonStates() {
        // Update Auto Print button state based on printer availability and service status
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        int storedPrinterIndex = prefs.getInt("stored_index_printer", -1);
        boolean printerAvailable = storedPrinterIndex >= 0;
        
        if (buttonAutoPrint != null) {
            buttonAutoPrint.setEnabled(printerAvailable);
            updateAutoPrintButtonAppearance();
        }
    }
    
    private void toggleAutoPrint() {
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        int storedPrinterIndex = prefs.getInt("stored_index_printer", -1);
        
        if (storedPrinterIndex < 0) {
            Toast.makeText(this, R.string.select_printer_text, Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!Constants.isServiceActive) {
            // Start printing service
            startAutoPrintService();
        } else {
            // Stop printing service
            stopAutoPrintService();
        }
    }
    
    private void startAutoPrintService() {
        if (!Constants.isServiceActive) {
            // Update UI
            int buttonColor = ContextCompat.getColor(this, R.color.light_green);
            if (buttonAutoPrint != null) {
                setButtonBackgroundWithRoundedCorners(buttonAutoPrint, buttonColor);
                buttonAutoPrint.setText(R.string.printer_active);
            }
            
            // Note: API refresh is handled separately by foreground refresh
            // This button only controls automatic printing
            
            Constants.isServiceActive = true;
            updateSystemStatus();
        }
    }
    
    private void stopAutoPrintService() {
        if (Constants.isServiceActive) {
            // Note: API refresh is handled separately by foreground refresh
            // This only stops automatic printing
            
            // Update UI
            int buttonColor = ContextCompat.getColor(this, R.color.colorAccent);
            if (buttonAutoPrint != null) {
                setButtonBackgroundWithRoundedCorners(buttonAutoPrint, buttonColor);
                buttonAutoPrint.setText(R.string.auto_print);
            }
            
            Constants.isServiceActive = false;
            updateSystemStatus();
        }
    }
    
    private void updateAutoPrintButtonAppearance() {
        if (buttonAutoPrint == null) return;
        
        int buttonColor;
        int textResId;
        
        if (Constants.isServiceActive) {
            buttonColor = ContextCompat.getColor(this, R.color.light_green);
            textResId = R.string.printer_active;
        } else {
            buttonColor = ContextCompat.getColor(this, R.color.colorAccent);
            textResId = R.string.auto_print;
        }
        
        setButtonBackgroundWithRoundedCorners(buttonAutoPrint, buttonColor);
        buttonAutoPrint.setText(textResId);
    }
    
    private void setButtonBackgroundWithRoundedCorners(Button button, int color) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        float cornerRadiusPx = 8 * getResources().getDisplayMetrics().density;
        drawable.setCornerRadius(cornerRadiusPx);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            button.setBackground(drawable);
        } else {
            button.setBackgroundDrawable(drawable);
        }
    }
    
    
    private void refreshData() {
        if (domain_shop == null || domain_shop.isEmpty()) {
            return;
        }
        
        String[] urls = {tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL};
        networkHelperViewModel.getNetworkHelper().fetchData(urls, "", "", domain_shop, this);
    }
    
    private void startForegroundRefresh() {
        // Start API refresh timer when app is in foreground
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }
        refreshTimer = new Timer();
        refreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mainHandler.post(() -> {
                    SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
                    String savedToken = sharedPreferences.getString("token", "");
                    if (savedToken.isEmpty()) {
                        Log.d("DashboardActivity", "User not logged in, skipping refresh");
                        return;
                    }
                    refreshData();
                });
            }
        }, 0, Constants.REFRESH_INTERVAL_KITCHEN_OPEN);
    }
    
    private void stopForegroundRefresh() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
    }
    
    private void updateStatistics(List<JSONObject> orders) {
        totalOrders = 0;
        inProgressOrders = 0;
        completedOrders = 0;
        
        // Get today's date for filtering (used for Total and Completed only)
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        
        // Date format used by the API: "2020-05-24 12:58:43"
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayDateStr = dateOnlyFormat.format(today.getTime());
        
        for (JSONObject order : orders) {
            try {
                JSONObject attributes = order.optJSONObject("attributes");
                JSONObject orderData = attributes != null ? attributes : order;
                
                // Get status_id first to determine how to filter
                int statusId = orderData.optInt("status_id", -1);
                
                // Parse the created_at date (needed for today filter on Total/Completed)
                String createdAtStr = orderData.optString("created_at", "");
                Date createdAt = null;
                boolean isToday = false;
                
                if (!createdAtStr.isEmpty()) {
                    try {
                        createdAt = dateFormat.parse(createdAtStr);
                    } catch (ParseException e) {
                        // Try alternative format if standard format fails
                        try {
                            SimpleDateFormat altFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            createdAt = altFormat.parse(createdAtStr);
                        } catch (ParseException e2) {
                            Log.w("Dashboard", "Could not parse created_at: " + createdAtStr);
                        }
                    }
                    
                    if (createdAt != null) {
                        String orderDateStr = dateOnlyFormat.format(createdAt);
                        isToday = orderDateStr.equals(todayDateStr);
                    }
                }
                
                // Count In Progress orders from all available data (no date filter)
                // In Progress = all orders that are NOT Completed (status_id != 5)
                if (statusId != 5) { // Not Completed
                    inProgressOrders++;
                }
                
                // Count Total and Completed orders only from today (with date filter)
                if (createdAtStr.isEmpty() || !isToday) {
                    // Skip for Total/Completed if not today or no date, but continue to next order
                    // (In Progress already counted above)
                    continue;
                }
                
                // Order is from today, count it for Total
                totalOrders++;
                
                // Count Completed orders (today only)
                if (statusId == 5) { // Completed
                    completedOrders++;
                }
            } catch (Exception e) {
                Log.e("Dashboard", "Error parsing order status", e);
            }
        }
        
        // Update UI
        textTotalOrders.setText(String.valueOf(totalOrders));
        textInProgressOrders.setText(String.valueOf(inProgressOrders));
        textCompletedOrders.setText(String.valueOf(completedOrders));
    }
    
    private void updateSystemStatus() {
        // Update printer status
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        int storedPrinterIndex = prefs.getInt("stored_index_printer", -1);
        boolean printerConnected = storedPrinterIndex >= 0;
        
        if (printerConnected) {
            viewPrinterStatus.setBackground(ContextCompat.getDrawable(this, R.drawable.circle_green));
            textPrinterStatus.setText(R.string.connected);
            textPrinterStatus.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess));
        } else {
            viewPrinterStatus.setBackground(ContextCompat.getDrawable(this, R.drawable.circle_red));
            textPrinterStatus.setText(R.string.not_connected);
            textPrinterStatus.setTextColor(ContextCompat.getColor(this, R.color.colorError));
        }
        
        // Update service status
        boolean serviceActive = Constants.isServiceActive;
        if (serviceActive) {
            viewServiceStatus.setBackground(ContextCompat.getDrawable(this, R.drawable.circle_green));
            textServiceStatus.setText(R.string.active);
            textServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess));
        } else {
            viewServiceStatus.setBackground(ContextCompat.getDrawable(this, R.drawable.circle_red));
            textServiceStatus.setText(R.string.inactive);
            textServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.colorError));
        }
    }
    
    @Override
    public void onSuccess(String[] results) {
        // Stop refresh indicator if it's showing
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
        
        if (results != null && results[0] != null && results[1] != null && results[2] != null) {
            try {
                JSONObject jsonObjectOrders = new JSONObject(results[0]);
                JSONObject jsonObjectMenus = new JSONObject(results[1]);
                JSONObject jsonObjectCategories = new JSONObject(results[2]);
                JSONArray dataArrayOrders = jsonObjectOrders.getJSONArray("data");
                List<JSONObject> ordersList = new ArrayList<>();
                
                // Get static sets from MainActivity for tracking printed orders and sound played orders
                java.util.Set<String> printedOrders = MainActivity.getPrintedOrders();
                java.util.Set<String> soundPlayedOrders = MainActivity.getSoundPlayedOrders();
                
                // Filter printable orders (not yet printed)
                JSONArray filteredOrders = DocketStringModeler.filterPrintableOrders(dataArrayOrders, printedOrders);
                
                // Check for new unprinted orders and play sound
                boolean hasNewUnprintedOrder = false;
                List<String> newOrderIds = new ArrayList<>();
                
                // On initial load, populate soundPlayedOrders with all existing unprinted orders
                // to avoid playing sound for orders that were already there when activity started
                if (isInitialLoad) {
                    Log.d("Dashboard", "Initial load - marking all existing orders as 'sound played'");
                    for (int i = 0; i < dataArrayOrders.length(); i++) {
                        try {
                            JSONObject order = dataArrayOrders.getJSONObject(i);
                            String orderId = order.getString("id");
                            if (!printedOrders.contains(orderId)) {
                                soundPlayedOrders.add(orderId);
                            }
                        } catch (Exception e) {
                            Log.e("Dashboard", "Error processing order during initial load", e);
                        }
                    }
                    isInitialLoad = false;
                    Log.d("Dashboard", "Initial load complete. Marked " + soundPlayedOrders.size() + " orders as 'sound played'");
                }
                
                for (int i = 0; i < dataArrayOrders.length(); i++) {
                    JSONObject order = dataArrayOrders.getJSONObject(i);
                    ordersList.add(order);
                    
                    try {
                        String orderId = order.getString("id");
                        
                        // Check if this is a new unprinted order that we haven't played sound for yet
                        boolean isNotPrinted = !printedOrders.contains(orderId);
                        boolean hasNotPlayedSound = !soundPlayedOrders.contains(orderId);
                        
                        if (isNotPrinted && hasNotPlayedSound) {
                            hasNewUnprintedOrder = true;
                            newOrderIds.add(orderId);
                            soundPlayedOrders.add(orderId); // Mark that we've played sound for this order
                            Log.d("Dashboard", "New unprinted order detected: " + orderId);
                        }
                    } catch (Exception e) {
                        Log.e("Dashboard", "Error processing order for sound notification", e);
                    }
                }
                
                // Play sound for new unprinted orders
                if (hasNewUnprintedOrder) {
                    Log.d("Dashboard", "New unprinted orders found: " + newOrderIds.size() + " orders - " + newOrderIds.toString());
                    playNewOrderSound();
                }
                
                // Only print automatically if Auto Print service is active and printer is selected
                if (Constants.isServiceActive && selectedDevice != null) {
                    for (int i = 0; i < filteredOrders.length(); i++) {
                        JSONObject dataObjectOrders = filteredOrders.getJSONObject(i);
                        String orderID = dataObjectOrders.getString("id");

                        //print receipt
                        if (chipReceiptChecked) {
                            TIJobPrintBluetooth(docketStringModeler.startPrintingReceipt(dataObjectOrders, jsonObjectMenus, jsonObjectCategories, mediaPlayer, shop_name), orderID);
                        }
                        // print receipt for kitchen
                        if (chipKitchenChecked) {
                            TIJobPrintBluetooth(docketStringModeler.startPrintingKitchen(dataObjectOrders, jsonObjectMenus, jsonObjectCategories, mediaPlayer, shop_name), orderID);
                        }

                        if (isLongerConnectionTime) { // stop loop if connection to printer has a problem
                            break;
                        }
                    }
                }
                
                updateStatistics(ordersList);
                updateSystemStatus();
                
            } catch (JSONException e) {
                Log.e("Dashboard", "Error parsing orders JSON", e);
                Toast.makeText(this, R.string.error_loading_order_data, Toast.LENGTH_SHORT).show();
            }
        } else if (results != null && results[0] != null) {
            // Fallback: handle case where only orders data is available (for backwards compatibility)
            try {
                JSONObject jsonObjectOrders = new JSONObject(results[0]);
                JSONArray dataArrayOrders = jsonObjectOrders.getJSONArray("data");
                List<JSONObject> ordersList = new ArrayList<>();
                
                for (int i = 0; i < dataArrayOrders.length(); i++) {
                    JSONObject order = dataArrayOrders.getJSONObject(i);
                    ordersList.add(order);
                }
                
                updateStatistics(ordersList);
                updateSystemStatus();
            } catch (JSONException e) {
                Log.e("Dashboard", "Error parsing orders JSON", e);
                Toast.makeText(this, R.string.error_loading_order_data, Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @SuppressLint("SimpleDateFormat")
    public void TIJobPrintBluetooth(String print_info, String orderId) {
        // Check if printer is available
        if (selectedDevice == null) {
            Log.e("Dashboard", "Printer not selected");
            return;
        }
        
        // Create AsyncEscPosPrinter similar to MainActivity
        com.dantsu.thermalprinter.async.AsyncEscPosPrinter printer = new com.dantsu.thermalprinter.async.AsyncEscPosPrinter(selectedDevice, 203, 48f, 32);
        printer.addTextToPrint(print_info);
        printer.getPrinterConnection().disconnect(); // important so that the printer can reconnect
        
        new AsyncBluetoothEscPosPrint(
                this,
                new AsyncEscPosPrint.OnPrintFinished() {
                    @Override
                    public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                        Log.e("Dashboard", "Print error occurred");
                        isLongerConnectionTime = true;
                    }

                    @Override
                    public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                        Log.i("Dashboard", "Print finished successfully");
                        if (isLongerConnectionTime) {
                            isLongerConnectionTime = false;
                        }
                        
                        // Mark order as printed
                        java.util.Set<String> printedOrders = MainActivity.getPrintedOrders();
                        printedOrders.add(orderId);
                        IdManager.clearIds(DashboardActivity.this);
                        IdManager.saveIds(DashboardActivity.this, printedOrders);
                    }
                }
        ).execute(printer);
    }
    
    private void playNewOrderSound() {
        // Ensure MediaPlayer is initialized
        if (mediaPlayer == null) {
            Log.d("Dashboard", "MediaPlayer is null, creating new instance");
            mediaPlayer = MediaPlayer.create(this, R.raw.newordersound);
        }
        
        if (mediaPlayer != null) {
            try {
                // Reset MediaPlayer to beginning to allow replaying
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                    mediaPlayer.reset();
                } else {
                    mediaPlayer.reset();
                }
                
                // Recreate MediaPlayer to ensure it's in a fresh state
                mediaPlayer.release();
                mediaPlayer = MediaPlayer.create(this, R.raw.newordersound);
                
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                    Log.d("Dashboard", "Successfully played sound for new unprinted order(s)");
                } else {
                    Log.e("Dashboard", "Failed to create MediaPlayer instance");
                }
            } catch (Exception e) {
                Log.e("Dashboard", "Error playing new order sound", e);
                // If MediaPlayer fails, try recreating it
                try {
                    if (mediaPlayer != null) {
                        mediaPlayer.release();
                    }
                    mediaPlayer = MediaPlayer.create(this, R.raw.newordersound);
                    if (mediaPlayer != null) {
                        mediaPlayer.start();
                        Log.d("Dashboard", "Successfully played sound after recreating MediaPlayer");
                    }
                } catch (Exception e2) {
                    Log.e("Dashboard", "Error recreating MediaPlayer for new order sound", e2);
                }
            }
        } else {
            Log.e("Dashboard", "MediaPlayer is null and could not be created");
        }
    }
    
    @Override
    public void onError(Exception exception) {
        // Stop refresh indicator if it's showing
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
        
        Log.e("Dashboard", "Error fetching data", exception);
        Toast.makeText(this, getString(R.string.error_message, exception.getMessage()), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopForegroundRefresh();
        
        // Release MediaPlayer when activity is destroyed
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e("Dashboard", "Error releasing MediaPlayer", e);
            }
            mediaPlayer = null;
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        stopForegroundRefresh();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reload chip preferences in case they were changed in MainActivity
        loadChipPreferences();
        // Reload printer configuration in case it was changed
        loadPrinterConfiguration();
        startForegroundRefresh();
        updateSystemStatus();
        updateButtonStates();
        updateAutoPrintButtonAppearance();
    }
}

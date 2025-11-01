package com.dantsu.thermalprinter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;


import com.dantsu.thermalprinter.helpClasses.DocketStringModeler;
import com.dantsu.thermalprinter.helpClasses.NetworkHelper;
import com.dantsu.thermalprinter.helpClasses.ShopConfigUtils;
import com.dantsu.thermalprinter.helpClasses.IdManager;
import com.dantsu.thermalprinter.model.NetworkHelperViewModel;
import com.dantsu.thermalprinter.async.AsyncBluetoothEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrinter;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
import android.media.MediaPlayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class KitchenDisplayActivity extends AppCompatActivity implements NetworkHelper.NetworkCallback, KitchenOrderAdapter.ReceiptPreviewClickListener {
    
    private RecyclerView recyclerViewOrders;
    private KitchenOrderAdapter orderAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private ImageButton buttonCloseKitchen;
    private TextView textOrderCount;
    private NetworkHelperViewModel networkHelperViewModel;
    private Timer refreshTimer;
    private Handler mainHandler;
    
    
    // Shop configuration
    private String domain_shop;
    private String tiOrdersEndpointURL;
    private String tiMenusEndpointURL;
    private String tiCategoriesEndpointURL;
    private Integer location_id;
    
    // Refresh settings
    private boolean isRefreshing = false;
    
    // Pagination settings
    private int currentPage = 1;
    private boolean isLoadingMore = false;
    private boolean hasMoreData = true;
    
    // Printing functionality
    private BluetoothConnection selectedDevice;
    private DocketStringModeler docketStringModeler;
    private MediaPlayer mediaPlayer;
    private String shop_name;
    
    // Chip selection from main screen
    private boolean chipReceiptChecked = true;  // default to receipt
    private boolean chipKitchenChecked = false; // default to not kitchen
    
    // Flag to track if we've done the initial load for this activity
    private boolean isInitialLoad = true;
    
    // Flag to track printer connection issues
    private boolean isLongerConnectionTime = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kitchen_display);
        
        // Initialize views
        recyclerViewOrders = findViewById(R.id.recyclerViewOrders);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        buttonCloseKitchen = findViewById(R.id.button_close_kitchen);
        textOrderCount = findViewById(R.id.textOrderCount);
        
        
        // Get chip settings from main screen (fallback if not in SharedPreferences)
        chipReceiptChecked = getIntent().getBooleanExtra("chip_receipt_checked", true);
        chipKitchenChecked = getIntent().getBooleanExtra("chip_kitchen_checked", false);
        
        // Load chip preferences from SharedPreferences (will override Intent values if available)
        loadChipPreferences();
        
        // Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerViewOrders.setLayoutManager(layoutManager);
        
        // Load printer configuration first to determine printer availability
        loadPrinterConfiguration();
        
        // Check if printer is selected
        boolean printerAvailable = selectedDevice != null;
        
        orderAdapter = new KitchenOrderAdapter(new ArrayList<>(), new KitchenOrderAdapter.PrintButtonClickListener() {
            @Override
            public void onPrintButtonClick(JSONObject order) {
                printOrder(order);
            }
        }, new KitchenOrderAdapter.StatusChangeClickListener() {
            @Override
            public void onStatusChangeClick(JSONObject order, String statusName) {
                updateSingleOrderStatus(order, statusName);
            }
        }, this, printerAvailable);
        recyclerViewOrders.setAdapter(orderAdapter);
        
        // Add scroll listener for pagination
        recyclerViewOrders.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                if (dy > 0) { // Scrolling down
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    
                    if (!isLoadingMore && hasMoreData) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                                && firstVisibleItemPosition >= 0) {
                            loadMoreOrders();
                        }
                    }
                }
            }
        });
        
        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(this::refreshOrders);
        
        // Setup toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        toolbar.setNavigationOnClickListener(v -> finish());
        
        // Setup dashboard button
        Button buttonDashboard = findViewById(R.id.button_dashboard);
        if (buttonDashboard != null) {
            buttonDashboard.setOnClickListener(v -> {
                Intent intent = new Intent(this, DashboardActivity.class);
                startActivity(intent);
            });
        }
        
        // Setup close button
        if (buttonCloseKitchen != null) {
            buttonCloseKitchen.setOnClickListener(v -> finish());
        }
        
        // Initialize ViewModel
        networkHelperViewModel = new ViewModelProvider(this).get(NetworkHelperViewModel.class);
        mainHandler = new Handler();
        
        // Initialize printing components
        docketStringModeler = new DocketStringModeler();
        mediaPlayer = MediaPlayer.create(this, R.raw.newordersound);
        
        // Load shop configuration
        loadShopConfiguration();
        
        // Printer configuration is already loaded above, before adapter initialization
        
        // Start initial data load
        refreshOrders();
        
        // Set flag that KitchenDisplayActivity is open
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("kitchen_display_open", true);
        editor.apply();
        
        // Start foreground-based API refresh
        startForegroundRefresh();
        
        // Keep screen awake while Kitchen Display is open
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        
        // Update adapter with printer availability (if adapter has been initialized)
        if (orderAdapter != null) {
            orderAdapter.setPrinterAvailable(selectedDevice != null);
        }
    }
    
    private void loadChipPreferences() {
        // Load chip preferences from SharedPreferences (set by MainActivity)
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        chipReceiptChecked = prefs.getBoolean("chip_receipt_checked", true);  // default to receipt
        chipKitchenChecked = prefs.getBoolean("chip_kitchen_checked", false); // default to not kitchen
    }
    
    
    private void updateSingleOrderStatus(JSONObject order, String statusName) {
        try {
            String orderId = order.getString("id");
            updateOrderStatus(orderId, statusName);
            
            // If status is completed, refresh the list to filter out completed orders
            if ("completed".equals(statusName)) {
                // Delay the refresh to allow the API call to complete
                new Handler().postDelayed(() -> {
                    refreshOrders();
                    // Order count will be updated in onSuccess callback after refresh
                }, 1000);
            } else {
                // Update order count immediately for non-completed status changes
                updateOrderCount(orderAdapter.getItemCount());
            }
        } catch (JSONException e) {
            Log.e("KitchenDisplay", "Error getting order ID", e);
        }
    }
    
    private void updateOrderStatus(String orderId, String statusName) {
        new Thread(() -> {
            try {
                // Get the status ID based on status name
                int statusId = getStatusIdByName(statusName);
                if (statusId == -1) {
                    Log.e("KitchenDisplay", "Status not found: " + statusName);
                    return;
                }
                
                // Create the update payload
                JSONObject updatePayload = new JSONObject();
                updatePayload.put("status_id", statusId);
                
                // Make the API call
                String updateUrl = domain_shop + "/api/orders/" + orderId;
                String result = makePatchRequest(updateUrl, updatePayload.toString());
                
                if (result != null) {
                    Log.d("KitchenDisplay", "Order " + orderId + " status updated to " + statusName);
                } else {
                    Log.e("KitchenDisplay", "Failed to update order " + orderId);
                }
                
            } catch (Exception e) {
                Log.e("KitchenDisplay", "Error updating order status", e);
            }
        }).start();
    }
    
    private int getStatusIdByName(String statusName) {
        // Map status names to IDs based on the documentation
        switch (statusName.toLowerCase()) {
            case "confirmed":
                return 2; // Confirmed status ID
            case "preparation":
                return 3; // Preparation status ID
            case "delivery":
                return 4; // Delivery status ID
            case "completed":
                return 5; // Completed status ID
            default:
                return -1;
        }
    }
    
    private String makePatchRequest(String url, String jsonPayload) {
        try {
            // Get the authentication token from SharedPreferences
            SharedPreferences tokenPrefs = getSharedPreferences("api_token", Context.MODE_PRIVATE);
            String token = tokenPrefs.getString("token", null);
            
            if (token == null) {
                Log.e("KitchenDisplay", "No authentication token found");
                return null;
            }
            
            java.net.URL requestUrl = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("PATCH");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setDoOutput(true);
            
            // Write the JSON payload
            java.io.OutputStream os = connection.getOutputStream();
            os.write(jsonPayload.getBytes());
            os.flush();
            os.close();
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } else {
                Log.e("KitchenDisplay", "API request failed with code: " + responseCode);
                // Try to read error response
                try {
                    java.io.BufferedReader errorReader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    errorReader.close();
                    Log.e("KitchenDisplay", "Error response: " + errorResponse.toString());
                } catch (Exception e) {
                    Log.e("KitchenDisplay", "Could not read error response", e);
                }
                return null;
            }
        } catch (Exception e) {
            Log.e("KitchenDisplay", "Error making API request", e);
            return null;
        }
    }
    
    private void refreshOrders() {
        if (isRefreshing) return;
        
        isRefreshing = true;
        currentPage = 1;
        hasMoreData = true;
        progressBar.setVisibility(View.VISIBLE);
        
        String[] urls = {tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL};
        networkHelperViewModel.getNetworkHelper().fetchData(urls, "", "", domain_shop, this);
    }
    
    private void loadMoreOrders() {
        if (isLoadingMore || !hasMoreData) return;
        
        isLoadingMore = true;
        currentPage++;
        
        String nextPageUrl = domain_shop + "/api/orders?sort=order_id desc&pageLimit=50&page=" + currentPage + "&location=" + location_id;
        String[] urls = {nextPageUrl, tiMenusEndpointURL, tiCategoriesEndpointURL};
        
        // Create a custom callback for pagination
        NetworkHelper.NetworkCallback paginationCallback = new NetworkHelper.NetworkCallback() {
            @Override
            public void onSuccess(String[] results) {
                isLoadingMore = false;
                
                if (results != null && results[0] != null) {
                    try {
                        JSONObject jsonObjectOrders = new JSONObject(results[0]);
                        JSONArray dataArrayOrders = jsonObjectOrders.getJSONArray("data");
                        
                        if (dataArrayOrders.length() == 0) {
                            hasMoreData = false;
                            return;
                        }
                        
                        List<JSONObject> newOrders = new ArrayList<>();
                        for (int i = 0; i < dataArrayOrders.length(); i++) {
                            JSONObject order = dataArrayOrders.getJSONObject(i);
                            newOrders.add(order);
                        }
                        
                        // Append new orders to existing list
                        orderAdapter.appendOrders(newOrders);
                        
                        // Update order count after appending new orders
                        runOnUiThread(() -> {
                            updateOrderCount(orderAdapter.getItemCount());
                        });
                        
                        Log.d("KitchenDisplay", "Loaded " + newOrders.size() + " more orders (page " + currentPage + ")");
                        
                    } catch (JSONException e) {
                        Log.e("KitchenDisplay", "Error parsing pagination JSON", e);
                        hasMoreData = false;
                    }
                } else {
                    hasMoreData = false;
                }
            }
            
            @Override
            public void onError(Exception exception) {
                isLoadingMore = false;
                hasMoreData = false;
                Log.e("KitchenDisplay", "Error loading more orders", exception);
            }
        };
        
        networkHelperViewModel.getNetworkHelper().fetchData(urls, "", "", domain_shop, paginationCallback);
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
                        Log.d("KitchenDisplayActivity", "User not logged in, skipping refresh");
                        return;
                    }
                    if (!isRefreshing) {
                        refreshOrders();
                    }
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
    
    @Override
    public void onSuccess(String[] results) {
        isRefreshing = false;
        progressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        
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
                
                // Check for new unprinted orders and play sound
                boolean hasNewUnprintedOrder = false;
                List<String> newOrderIds = new ArrayList<>();
                
                // On initial load, populate soundPlayedOrders with all existing unprinted orders
                // to avoid playing sound for orders that were already there when activity started
                if (isInitialLoad) {
                    Log.d("KitchenDisplay", "Initial load - marking all existing orders as 'sound played'");
                    for (int i = 0; i < dataArrayOrders.length(); i++) {
                        try {
                            JSONObject order = dataArrayOrders.getJSONObject(i);
                            String orderId = order.getString("id");
                            if (!printedOrders.contains(orderId)) {
                                soundPlayedOrders.add(orderId);
                            }
                        } catch (Exception e) {
                            Log.e("KitchenDisplay", "Error processing order during initial load", e);
                        }
                    }
                    isInitialLoad = false;
                    Log.d("KitchenDisplay", "Initial load complete. Marked " + soundPlayedOrders.size() + " orders as 'sound played'");
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
                            Log.d("KitchenDisplay", "New unprinted order detected: " + orderId);
                        }
                    } catch (Exception e) {
                        Log.e("KitchenDisplay", "Error processing order for sound notification", e);
                    }
                }
                
                // Play sound for new unprinted orders
                if (hasNewUnprintedOrder) {
                    Log.d("KitchenDisplay", "New unprinted orders found: " + newOrderIds.size() + " orders - " + newOrderIds.toString());
                    playNewOrderSound();
                }
                
                // Filter printable orders (not yet printed) for automatic printing
                JSONArray filteredOrders = DocketStringModeler.filterPrintableOrders(dataArrayOrders, printedOrders);
                
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
                
                // Check if we have more data based on the number of orders returned
                if (ordersList.size() < 50) {
                    hasMoreData = false;
                }
                
                // Update adapter with new data
                orderAdapter.updateOrders(ordersList, jsonObjectMenus, jsonObjectCategories);
                
                // Update order count based on adapter's actual item count
                updateOrderCount(orderAdapter.getItemCount());
                
                Log.d("KitchenDisplay", "Loaded " + ordersList.size() + " orders (page " + currentPage + ")");
                
            } catch (JSONException e) {
                Log.e("KitchenDisplay", "Error parsing JSON", e);
                Toast.makeText(this, getString(R.string.error_loading_orders_with_message, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.failed_fetch_order_data, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void playNewOrderSound() {
        // Ensure MediaPlayer is initialized
        if (mediaPlayer == null) {
            Log.d("KitchenDisplay", "MediaPlayer is null, creating new instance");
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
                    Log.d("KitchenDisplay", "Successfully played sound for new unprinted order(s)");
                } else {
                    Log.e("KitchenDisplay", "Failed to create MediaPlayer instance");
                }
            } catch (Exception e) {
                Log.e("KitchenDisplay", "Error playing new order sound", e);
                // If MediaPlayer fails, try recreating it
                try {
                    if (mediaPlayer != null) {
                        mediaPlayer.release();
                    }
                    mediaPlayer = MediaPlayer.create(this, R.raw.newordersound);
                    if (mediaPlayer != null) {
                        mediaPlayer.start();
                        Log.d("KitchenDisplay", "Successfully played sound after recreating MediaPlayer");
                    }
                } catch (Exception e2) {
                    Log.e("KitchenDisplay", "Error recreating MediaPlayer for new order sound", e2);
                }
            }
        } else {
            Log.e("KitchenDisplay", "MediaPlayer is null and could not be created");
        }
    }
    
    @Override
    public void onError(Exception exception) {
        isRefreshing = false;
        progressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        
        Log.e("KitchenDisplay", "Error fetching orders", exception);
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
                Log.e("KitchenDisplay", "Error releasing MediaPlayer", e);
            }
            mediaPlayer = null;
        }
        
        // Clear flag that KitchenDisplayActivity is open
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("kitchen_display_open", false);
        editor.apply();
        
        // Remove screen awake flag when activity is destroyed
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
    }
    
    private void printOrder(JSONObject order) {
        if (selectedDevice == null) {
            return;
        }
        
        try {
            // Get the current menus and categories data from the adapter
            JSONObject menusData = orderAdapter.menusData;
            JSONObject categoriesData = orderAdapter.categoriesData;
            
            if (menusData == null || categoriesData == null) {
                return;
            }
            
            String orderId = order.optString("id");
            
            // Print receipt if chip is checked
            if (chipReceiptChecked) {
                String receiptPrintInfo = docketStringModeler.startPrintingReceipt(order, menusData, categoriesData, mediaPlayer, shop_name);
                executePrint(receiptPrintInfo, "Receipt");
            }
            
            // Print kitchen receipt if chip is checked
            if (chipKitchenChecked) {
                String kitchenPrintInfo = docketStringModeler.startPrintingKitchen(order, menusData, categoriesData, mediaPlayer, shop_name);
                executePrint(kitchenPrintInfo, "Kitchen");
            }
            
        } catch (Exception e) {
            Log.e("KitchenDisplay", "Error printing order", e);
        }
    }
    
    private void executePrint(String printInfo, String printType) {
        // Execute printing
        new AsyncBluetoothEscPosPrint(
            this,
            new AsyncEscPosPrint.OnPrintFinished() {
                @Override
                public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                    Log.e("KitchenDisplay", "Print error for " + printType + ": " + codeException);
                }

                @Override
                public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                    Log.i("KitchenDisplay", printType + " print successful");
                }
            }
        ).execute(getAsyncEscPosPrinter(selectedDevice, printInfo));
    }
    
    @SuppressLint("SimpleDateFormat")
    public void TIJobPrintBluetooth(String print_info, String orderId) {
        // Check if printer is available
        if (selectedDevice == null) {
            Log.e("KitchenDisplay", "Printer not selected");
            return;
        }
        
        // Create AsyncEscPosPrinter similar to MainActivity and DashboardActivity
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(selectedDevice, 203, 48f, 32);
        printer.addTextToPrint(print_info);
        printer.getPrinterConnection().disconnect(); // important so that the printer can reconnect
        
        new AsyncBluetoothEscPosPrint(
                this,
                new AsyncEscPosPrint.OnPrintFinished() {
                    @Override
                    public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                        Log.e("KitchenDisplay", "Print error occurred");
                        isLongerConnectionTime = true;
                    }

                    @Override
                    public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                        Log.i("KitchenDisplay", "Print finished successfully");
                        if (isLongerConnectionTime) {
                            isLongerConnectionTime = false;
                        }
                        
                        // Mark order as printed
                        java.util.Set<String> printedOrders = MainActivity.getPrintedOrders();
                        printedOrders.add(orderId);
                        // Remove from soundPlayedOrders since it's now printed (optional cleanup)
                        MainActivity.getSoundPlayedOrders().remove(orderId);
                        IdManager.clearIds(KitchenDisplayActivity.this);
                        IdManager.saveIds(KitchenDisplayActivity.this, printedOrders);
                    }
                }
        ).execute(printer);
    }
    
    private AsyncEscPosPrinter getAsyncEscPosPrinter(BluetoothConnection printerConnection, String print_info) {
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(printerConnection, 203, 48f, 32);
        printer.addTextToPrint(print_info);
        printer.getPrinterConnection().disconnect();
        return printer;
    }
    
    @Override
    public void onReceiptPreviewClick(JSONObject order) {
        showReceiptPreview(order);
    }
    
    private void updateOrderCount(int count) {
        if (textOrderCount != null) {
            textOrderCount.setText(String.valueOf(count));
        }
    }
    
    private void showReceiptPreview(JSONObject order) {
        try {
            // Get the current menus and categories data from the adapter
            JSONObject menusData = orderAdapter.menusData;
            JSONObject categoriesData = orderAdapter.categoriesData;
            
            if (menusData == null || categoriesData == null) {
                Toast.makeText(this, R.string.menu_data_not_available, Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Create and show the receipt preview dialog
            ReceiptPreviewDialogFragment dialog = ReceiptPreviewDialogFragment.newInstance(
                order, menusData, categoriesData, shop_name);
            dialog.show(getSupportFragmentManager(), "ReceiptPreviewDialog");
            
        } catch (Exception e) {
            Log.e("KitchenDisplay", "Error showing receipt preview", e);
            Toast.makeText(this, getString(R.string.error_showing_receipt_preview, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }
}

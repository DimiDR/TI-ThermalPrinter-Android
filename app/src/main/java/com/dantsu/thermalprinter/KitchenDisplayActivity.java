package com.dantsu.thermalprinter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
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

public class KitchenDisplayActivity extends AppCompatActivity implements NetworkHelper.NetworkCallback {
    
    private RecyclerView recyclerViewOrders;
    private KitchenOrderAdapter orderAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
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
    private long refreshInterval = 30000; // 30 seconds default
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kitchen_display);
        
        // Initialize views
        recyclerViewOrders = findViewById(R.id.recyclerViewOrders);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        
        
        // Get chip settings from main screen
        chipReceiptChecked = getIntent().getBooleanExtra("chip_receipt_checked", true);
        chipKitchenChecked = getIntent().getBooleanExtra("chip_kitchen_checked", false);
        
        // Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerViewOrders.setLayoutManager(layoutManager);
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
        });
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
        
        // Initialize ViewModel
        networkHelperViewModel = new ViewModelProvider(this).get(NetworkHelperViewModel.class);
        mainHandler = new Handler();
        
        // Initialize printing components
        docketStringModeler = new DocketStringModeler();
        mediaPlayer = MediaPlayer.create(this, R.raw.newordersound);
        
        // Load shop configuration
        loadShopConfiguration();
        
        // Load printer configuration
        loadPrinterConfiguration();
        
        // Start initial data load
        refreshOrders();
        
        // Start auto-refresh timer
        startAutoRefresh();
    }
    
    
    private void loadShopConfiguration() {
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        domain_shop = sharedPreferences.getString("domain_shop", "");
        location_id = sharedPreferences.getInt("location_id", 1);
        
        if (domain_shop.isEmpty()) {
            Toast.makeText(this, "No shop configuration found. Please login first.", Toast.LENGTH_LONG).show();
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
    }
    
    
    private void updateSingleOrderStatus(JSONObject order, String statusName) {
        try {
            String orderId = order.getString("id");
            updateOrderStatus(orderId, statusName);
            
            // If status is completed, refresh the list to filter out completed orders
            if ("completed".equals(statusName)) {
                Toast.makeText(this, "Order #" + orderId + " marked as completed. Refreshing list...", Toast.LENGTH_SHORT).show();
                refreshOrders();
            } else {
                Toast.makeText(this, "Order #" + orderId + " status updated to " + statusName, Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            Log.e("KitchenDisplay", "Error getting order ID", e);
            Toast.makeText(this, "Error updating order status", Toast.LENGTH_SHORT).show();
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
            java.net.URL requestUrl = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("PATCH");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
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
    
    private void startAutoRefresh() {
        refreshTimer = new Timer();
        refreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mainHandler.post(() -> {
                    if (!isRefreshing) {
                        refreshOrders();
                    }
                });
            }
        }, refreshInterval, refreshInterval);
    }
    
    private void stopAutoRefresh() {
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
                
                for (int i = 0; i < dataArrayOrders.length(); i++) {
                    JSONObject order = dataArrayOrders.getJSONObject(i);
                    ordersList.add(order);
                }
                
                // Check if we have more data based on the number of orders returned
                if (ordersList.size() < 50) {
                    hasMoreData = false;
                }
                
                // Update adapter with new data
                orderAdapter.updateOrders(ordersList, jsonObjectMenus, jsonObjectCategories);
                
                Log.d("KitchenDisplay", "Loaded " + ordersList.size() + " orders (page " + currentPage + ")");
                
            } catch (JSONException e) {
                Log.e("KitchenDisplay", "Error parsing JSON", e);
                Toast.makeText(this, "Error loading orders: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Failed to fetch order data", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onError(Exception exception) {
        isRefreshing = false;
        progressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        
        Log.e("KitchenDisplay", "Error fetching orders", exception);
        Toast.makeText(this, "Error: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRefresh();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        startAutoRefresh();
    }
    
    private void printOrder(JSONObject order) {
        if (selectedDevice == null) {
            Toast.makeText(this, "No printer selected. Please configure printer in main app.", Toast.LENGTH_LONG).show();
            return;
        }
        
        try {
            // Get the current menus and categories data from the adapter
            JSONObject menusData = orderAdapter.menusData;
            JSONObject categoriesData = orderAdapter.categoriesData;
            
            if (menusData == null || categoriesData == null) {
                Toast.makeText(this, "Menu data not available. Please refresh and try again.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String orderId = order.optString("id");
            boolean hasPrinted = false;
            
            // Print receipt if chip is checked
            if (chipReceiptChecked) {
                String receiptPrintInfo = docketStringModeler.startPrintingReceipt(order, menusData, categoriesData, mediaPlayer, shop_name);
                executePrint(receiptPrintInfo, "Receipt");
                hasPrinted = true;
            }
            
            // Print kitchen receipt if chip is checked
            if (chipKitchenChecked) {
                String kitchenPrintInfo = docketStringModeler.startPrintingKitchen(order, menusData, categoriesData, mediaPlayer, shop_name);
                executePrint(kitchenPrintInfo, "Kitchen");
                hasPrinted = true;
            }
            
            if (!hasPrinted) {
                Toast.makeText(this, "Please select at least one print option (Receipt or Kitchen)", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Log.e("KitchenDisplay", "Error printing order", e);
            Toast.makeText(this, "Error printing order: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(KitchenDisplayActivity.this, printType + " print failed", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                    Log.i("KitchenDisplay", printType + " print successful");
                    Toast.makeText(KitchenDisplayActivity.this, printType + " printed successfully", Toast.LENGTH_SHORT).show();
                }
            }
        ).execute(getAsyncEscPosPrinter(selectedDevice, printInfo));
    }
    
    private AsyncEscPosPrinter getAsyncEscPosPrinter(BluetoothConnection printerConnection, String print_info) {
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(printerConnection, 203, 48f, 32);
        printer.addTextToPrint(print_info);
        printer.getPrinterConnection().disconnect();
        return printer;
    }
}

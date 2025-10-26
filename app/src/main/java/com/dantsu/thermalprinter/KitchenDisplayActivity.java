package com.dantsu.thermalprinter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

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
    
    // Printing functionality
    private BluetoothConnection selectedDevice;
    private DocketStringModeler docketStringModeler;
    private MediaPlayer mediaPlayer;
    private String shop_name;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kitchen_display);
        
        // Initialize views
        recyclerViewOrders = findViewById(R.id.recyclerViewOrders);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        
        // Setup RecyclerView
        recyclerViewOrders.setLayoutManager(new LinearLayoutManager(this));
        orderAdapter = new KitchenOrderAdapter(new ArrayList<>(), new KitchenOrderAdapter.PrintButtonClickListener() {
            @Override
            public void onPrintButtonClick(JSONObject order) {
                printOrder(order);
            }
        });
        recyclerViewOrders.setAdapter(orderAdapter);
        
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
    
    private void refreshOrders() {
        if (isRefreshing) return;
        
        isRefreshing = true;
        progressBar.setVisibility(View.VISIBLE);
        
        String[] urls = {tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL};
        networkHelperViewModel.getNetworkHelper().fetchData(urls, "", "", domain_shop, this);
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
                
                // Update adapter with new data
                orderAdapter.updateOrders(ordersList, jsonObjectMenus, jsonObjectCategories);
                
                Log.d("KitchenDisplay", "Loaded " + ordersList.size() + " orders");
                
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
            
            // Print kitchen receipt
            String printInfo = docketStringModeler.startPrintingKitchen(order, menusData, categoriesData, mediaPlayer, shop_name);
            String orderId = order.optString("id");
            
            // Execute printing
            new AsyncBluetoothEscPosPrint(
                this,
                new AsyncEscPosPrint.OnPrintFinished() {
                    @Override
                    public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                        Log.e("KitchenDisplay", "Print error: " + codeException);
                        Toast.makeText(KitchenDisplayActivity.this, "Print failed", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                        Log.i("KitchenDisplay", "Print successful");
                        Toast.makeText(KitchenDisplayActivity.this, "Order printed successfully", Toast.LENGTH_SHORT).show();
                    }
                }
            ).execute(getAsyncEscPosPrinter(selectedDevice, printInfo));
            
        } catch (Exception e) {
            Log.e("KitchenDisplay", "Error printing order", e);
            Toast.makeText(this, "Error printing order: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private AsyncEscPosPrinter getAsyncEscPosPrinter(BluetoothConnection printerConnection, String print_info) {
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(printerConnection, 203, 48f, 32);
        printer.addTextToPrint(print_info);
        printer.getPrinterConnection().disconnect();
        return printer;
    }
}

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

import com.dantsu.thermalprinter.helpClasses.NetworkHelper;
import com.dantsu.thermalprinter.helpClasses.ShopConfigUtils;
import com.dantsu.thermalprinter.model.NetworkHelperViewModel;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class DashboardActivity extends AppCompatActivity implements NetworkHelper.NetworkCallback {
    
    private MaterialToolbar toolbar;
    private TextView textTotalOrders;
    private TextView textPendingOrders;
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
    private Button buttonTestPrint;
    private Button buttonRefresh;
    
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
    private int pendingOrders = 0;
    private int inProgressOrders = 0;
    private int completedOrders = 0;
    
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
        
        // Update button states based on printer availability
        updateButtonStates();
        
        // Load initial data
        refreshData();
        
        // Start auto-refresh timer
        startAutoRefresh();
    }
    
    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        textTotalOrders = findViewById(R.id.textTotalOrders);
        textPendingOrders = findViewById(R.id.textPendingOrders);
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
        buttonTestPrint = findViewById(R.id.buttonTestPrint);
        buttonRefresh = findViewById(R.id.buttonRefresh);
    }
    
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        toolbar.setNavigationOnClickListener(v -> finish());
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
        
        buttonTestPrint.setOnClickListener(v -> {
            // Check if printer is available before navigating
            SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
            int storedPrinterIndex = prefs.getInt("stored_index_printer", -1);
            if (storedPrinterIndex < 0) {
                Toast.makeText(this, R.string.no_printer_selected_settings, Toast.LENGTH_LONG).show();
                return;
            }
            // Navigate to MainActivity for test print
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("action", "test_print");
            startActivity(intent);
        });
        
        buttonRefresh.setOnClickListener(v -> refreshData());
    }
    
    private void updateButtonStates() {
        // Update Test Print button state based on printer availability
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        int storedPrinterIndex = prefs.getInt("stored_index_printer", -1);
        boolean printerAvailable = storedPrinterIndex >= 0;
        
        if (buttonTestPrint != null) {
            buttonTestPrint.setEnabled(printerAvailable);
            if (!printerAvailable) {
                buttonTestPrint.setAlpha(0.5f); // Make button appear disabled
            } else {
                buttonTestPrint.setAlpha(1.0f); // Make button appear enabled
            }
        }
    }
    
    private void refreshData() {
        if (domain_shop == null || domain_shop.isEmpty()) {
            return;
        }
        
        String[] urls = {tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL};
        networkHelperViewModel.getNetworkHelper().fetchData(urls, "", "", domain_shop, this);
    }
    
    private void startAutoRefresh() {
        refreshTimer = new Timer();
        refreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mainHandler.post(() -> refreshData());
            }
        }, 30000, 30000); // Refresh every 30 seconds
    }
    
    private void stopAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
    }
    
    private void updateStatistics(List<JSONObject> orders) {
        totalOrders = orders.size();
        pendingOrders = 0;
        inProgressOrders = 0;
        completedOrders = 0;
        
        for (JSONObject order : orders) {
            try {
                JSONObject attributes = order.optJSONObject("attributes");
                JSONObject orderData = attributes != null ? attributes : order;
                int statusId = orderData.optInt("status_id", -1);
                
                switch (statusId) {
                    case 1: // Pending
                        pendingOrders++;
                        break;
                    case 2: // Confirmed
                    case 3: // Preparation
                        inProgressOrders++;
                        break;
                    case 4: // Delivery
                        inProgressOrders++;
                        break;
                    case 5: // Completed
                        completedOrders++;
                        break;
                    default:
                        pendingOrders++;
                        break;
                }
            } catch (Exception e) {
                Log.e("Dashboard", "Error parsing order status", e);
                pendingOrders++;
            }
        }
        
        // Update UI
        textTotalOrders.setText(String.valueOf(totalOrders));
        textPendingOrders.setText(String.valueOf(pendingOrders));
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
        if (results != null && results[0] != null) {
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
    
    @Override
    public void onError(Exception exception) {
        Log.e("Dashboard", "Error fetching data", exception);
        Toast.makeText(this, getString(R.string.error_message, exception.getMessage()), Toast.LENGTH_SHORT).show();
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
        updateSystemStatus();
        updateButtonStates();
    }
}

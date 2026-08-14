package com.dantsu.thermalprinter;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.appbar.MaterialToolbar;

import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.thermalprinter.async.AsyncEscPosPrint;
import com.dantsu.thermalprinter.helpClasses.ApkUpdateInstaller;
import com.dantsu.thermalprinter.helpClasses.AuthStore;
import com.dantsu.thermalprinter.helpClasses.BluetoothPrinterHelper;
import com.dantsu.thermalprinter.helpClasses.DocketStringModeler;
import com.dantsu.thermalprinter.helpClasses.IdManager;
import com.dantsu.thermalprinter.helpClasses.NetworkHelper;
import com.dantsu.thermalprinter.helpClasses.OrderHookConfig;
import com.dantsu.thermalprinter.helpClasses.OrderStatusMapper;
import com.dantsu.thermalprinter.helpClasses.PrintDispatcher;
import com.dantsu.thermalprinter.helpClasses.PrinterConfigController;
import com.dantsu.thermalprinter.helpClasses.PrintMonitorService;
import com.dantsu.thermalprinter.helpClasses.PrinterSettings;
import com.dantsu.thermalprinter.helpClasses.ShopCatalogRepository;
import com.dantsu.thermalprinter.helpClasses.ShopConfigUtils;
import com.dantsu.thermalprinter.helpClasses.UpdateVerifier;
import com.dantsu.thermalprinter.helpClasses.WebViewDialogFragment;
import com.dantsu.thermalprinter.model.NetworkHelperViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLException;

public class MainActivity extends AppCompatActivity implements NetworkHelper.NetworkCallback, LoginUserDialogFragment.Host, PrinterConfigController.Listener, ReprintOrdersHelper.Host  {
    //set of orders IDs, which already been printed
    private static Set<String> printedOrders = new HashSet<>();
    //set of orders IDs for which we've already played the new order sound
    private static Set<String> soundPlayedOrders = new HashSet<>();
    // Flag to track if we've done the initial load (to avoid playing sound for all existing orders)
    private static boolean isInitialLoad = true;
    
    // Getter methods to access static sets from other activities
    public static Set<String> getPrintedOrders() {
        return printedOrders;
    }
    
    public static Set<String> getSoundPlayedOrders() {
        return soundPlayedOrders;
    }
    private static Context context;
    private static Button button_ti_print;
    private static Button button_ti_clear_ids;
    private static Button button_ti_updates;
    private static Button button_ti_testprint;
    private static Button button_dashboard;
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
    private String apkSha256;
    private ProgressBar progressBar;
    private Boolean isPrinterSelected = false;
    private Boolean isServiceStarted = false;
    private FragmentManager fragmentManager = getSupportFragmentManager();
    private DeviceConnection selectedDevice;
    private PrinterConfigController printerConfig;
    private final ShopCatalogRepository.CatalogCallback catalogObserver = new ShopCatalogRepository.CatalogCallback() {
        @Override
        public void onSuccess(ShopCatalogRepository.Catalog catalog) {
            MainActivity.this.onSuccess(catalog.asResults());
        }

        @Override
        public void onError(Exception exception) {
            MainActivity.this.onError(exception);
        }
    };

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
        
        // Check for reprint action early and hide Settings view if needed
        String action = getIntent().getStringExtra("action");
        boolean isReprintAction = "reprint".equals(action);
        
        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }
            
            // Update toolbar title for reprint action
            if (isReprintAction) {
                toolbar.setTitle(R.string.reprint_orders);
            }
            
            toolbar.setNavigationOnClickListener(v -> {
                // Navigate back to Dashboard - just finish to return to existing DashboardActivity in back stack
                finish();
            });
        } else {
            Log.e("MainActivity", "Toolbar not found! Check if R.id.toolbar exists in the layout");
        }
        
        // Hide Settings view if this is a reprint action
        if (isReprintAction) {
            NestedScrollView nestedScrollView = findViewById(R.id.nestedScrollView);
            if (nestedScrollView != null) {
                nestedScrollView.setVisibility(View.GONE);
            }
        }

        printerConfig = new PrinterConfigController(this, this);
        printerConfig.bind();
        selectedDevice = printerConfig.getSelectedDevice();
        isPrinterSelected = printerConfig.isPrinterSelected();
        progressBar = findViewById(R.id.progressBar);
        button_ti_print = this.findViewById(R.id.button_ti_print_monitoring);
        if (button_ti_print != null) {
            button_ti_print.setOnClickListener(view -> tiPrintMonitoring());
        }
        textview_ti_header = this.findViewById(R.id.textview_ti_header);
        button_ti_clear_ids = this.findViewById(R.id.button_ti_clear_ids);
        if (button_ti_clear_ids != null) {
            button_ti_clear_ids.setOnClickListener(view -> tiClearIds());
        }
        button_ti_updates =  this.findViewById(R.id.button_ti_updates);
        if (button_ti_updates != null) {
            button_ti_updates.setOnClickListener(view -> showUpdatePopup());
        }
        button_dashboard = this.findViewById(R.id.button_dashboard);
        if (button_dashboard != null) {
            button_dashboard.setOnClickListener(view -> {
                Intent intent = new Intent(this, DashboardActivity.class);
                startActivity(intent);
            });
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
        bindOrderHookCard();
        orderCountBadge = this.findViewById(R.id.order_count_badge);
        // Initialize the ChipGroup and Chips
        chipGroupPrinters = findViewById(R.id.chipGroup_printers);
        chipReceipt = findViewById(R.id.chip_receipt);
        chipKitchen = findViewById(R.id.chip_kitchen);
        
        // Load chip preferences from SharedPreferences
        SharedPreferences chipPrefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        boolean savedReceiptChecked = chipPrefs.getBoolean("chip_receipt_checked", true);
        boolean savedKitchenChecked = chipPrefs.getBoolean("chip_kitchen_checked", false);
        if (chipReceipt != null) chipReceipt.setChecked(savedReceiptChecked);
        if (chipKitchen != null) chipKitchen.setChecked(savedKitchenChecked);
        
        // Save chip preferences when they change
        if (chipReceipt != null) {
            chipReceipt.setOnCheckedChangeListener((buttonView, isChecked) -> {
                SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("chip_receipt_checked", isChecked);
                editor.apply();
            });
        }
        if (chipKitchen != null) {
            chipKitchen.setOnCheckedChangeListener((buttonView, isChecked) -> {
                SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("chip_kitchen_checked", isChecked);
                editor.apply();
            });
        }
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
            apkSha256 = getIntent().getStringExtra("apkSha256");
            if (!UpdateVerifier.isAllowedApkUrl(apkFile) || !UpdateVerifier.isValidSha256(apkSha256)) {
                apkFile = null;
                apkSha256 = null;
                button_ti_updates.setEnabled(false);
                button_ti_updates.setText(getString(R.string.version) + " " + Constants.currentAppVersion);
            } else {
            button_ti_updates.setEnabled(true);
            button_ti_updates.setText(R.string.update_available_button);
            setButtonBackgroundWithRoundedCorners(button_ti_updates, ContextCompat.getColor(this, R.color.colorError));
            if (!networkHelperViewModel.isUpdatePopupShown()) {
                showUpdatePopup();
                networkHelperViewModel.setUpdatePopupShown(true);
            }
            }
        }else{
            button_ti_updates.setEnabled(false);
            // show current version on the screen
            String versionName = getIntent().getStringExtra("currentAppVersion");
            if (versionName == null || versionName.isEmpty()) {
                versionName = Constants.currentAppVersion;
            }
            button_ti_updates.setText(getString(R.string.version) + " " + versionName);
        }

        // Handle reprint intent from DashboardActivity - show popup after fetching orders
        if ("reprint".equals(getIntent().getStringExtra("action"))) {
            shouldShowReprintPopup = true;
        }

    }

    public void initilizeURLs() {
        // Check for persistent login
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        String savedDomain = sharedPreferences.getString("domain_shop", "");
        String savedToken = new AuthStore(this).getToken();
        if (savedToken == null) {
            savedToken = "";
        }

        printerConfig.restore();
        selectedDevice = printerConfig.getSelectedDevice();
        isPrinterSelected = printerConfig.isPrinterSelected();
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
                    
                    // Try to get shop name from configuration
                    String configShopName = getShopNameFromConfiguration(MainActivity.this, savedDomain);
                    if (configShopName != null && !configShopName.isEmpty()) {
                        shop_name = configShopName;
                    } else {
                        shop_name = savedDomain; // fallback to domain
                    }
                    if (textview_ti_header != null) textview_ti_header.setText(shop_name);
                    String[] urls = ShopCatalogRepository.catalogUrls(domain_shop, location_id);
                    tiOrdersEndpointURL = urls[0];
                    tiMenusEndpointURL = urls[1];
                    tiCategoriesEndpointURL = urls[2];
                    enableAllButtons();
                    if (shouldShowReprintPopup) {
                        fetchCatalog(true);
                    } else if (ShopCatalogRepository.latest() == null) {
                        fetchCatalog(false);
                    } else {
                        catalogObserver.onSuccess(ShopCatalogRepository.latest());
                    }
                    
                    new Handler().postDelayed(() -> {
                        PrintMonitorService.requestNotificationPermissionIfNeeded(MainActivity.this);
                        PrintMonitorService.startListening(MainActivity.this);
                        if (PrintMonitorService.isEnabled(MainActivity.this)) {
                            startPrintMonitor();
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
    
    private static String getShopNameFromConfiguration(Context context, String domain) {
        List<ShopConfigUtils.Shop> shops = ShopConfigUtils.getShops(context);
        for (ShopConfigUtils.Shop shop : shops) {
            if (shop.domain_shop.equals(domain)) {
                return shop.shop_name;
            }
        }
        return null;
    }

    private void bindOrderHookCard() {
        TextView urlView = findViewById(R.id.text_order_hook_url);
        Button copyButton = findViewById(R.id.button_copy_hook_url);
        Button testButton = findViewById(R.id.button_test_hook);
        if (urlView == null) {
            return;
        }
        final String webhookUrl = OrderHookConfig.webhookUrl(this);
        urlView.setText(webhookUrl);
        if (copyButton != null) {
            copyButton.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("webhook", webhookUrl));
                    Toast.makeText(this, R.string.order_hook_copied, Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (testButton != null) {
            testButton.setOnClickListener(v -> sendTestOrderHook(webhookUrl));
        }
    }

    private void sendTestOrderHook(String webhookUrl) {
        Toast.makeText(this, R.string.order_hook_test_sent, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            java.net.HttpURLConnection connection = null;
            try {
                java.net.URL url = new java.net.URL(webhookUrl);
                connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
                byte[] body = "new_order".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                connection.getOutputStream().write(body);
                connection.getOutputStream().close();
                int code = connection.getResponseCode();
                Log.d("MainActivity", "Test order hook HTTP " + code);
            } catch (Exception e) {
                Log.e("MainActivity", "Test order hook failed", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "TestOrderHook").start();
    }

    private void enableAllButtons() {
        printerConfig.setEnabled(true);
        if (button_ti_print != null) button_ti_print.setEnabled(true);
        if (button_ti_clear_ids != null) button_ti_clear_ids.setEnabled(true);
        updateTestPrintButtonState();
        if (button_dashboard != null) button_dashboard.setEnabled(true);
        
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
        
        // Clear shop-specific data
        clearShopSpecificData();
    }
    
    /**
     * Clears shop-specific data when switching shops.
     * This includes printed orders, sound played orders, and cached data.
     */
    private static void clearShopSpecificData() {
        printedOrders.clear();
        soundPlayedOrders.clear();
        isInitialLoad = true;
        ShopCatalogRepository.invalidate();
        
        // Clear persisted order IDs from storage
        if (context != null) {
            IdManager.clearIds(context);
        }
        
        Log.d("MainActivity", "Cleared shop-specific data (printed orders, sound played orders, and cached IDs)");
    }

    private void clearWebViewCookies() {
        // Clear WebView cookies to logout from website
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        if (cookieManager != null) {
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        }
    }

    private void handleReprintOrders() {
        // Always fetch fresh orders for reprint to ensure we get the last 50 orders
        if (tiOrdersEndpointURL != null && !tiOrdersEndpointURL.isEmpty() && 
            tiMenusEndpointURL != null && !tiMenusEndpointURL.isEmpty() &&
            tiCategoriesEndpointURL != null && !tiCategoriesEndpointURL.isEmpty()) {
            shouldShowReprintPopup = true;
            // Ensure we request last 50 orders for reprint
            fetchCatalog(true);
        } else {
            // Fallback to cached data if URLs not ready
            if (!cachedOrdersList.isEmpty() && cachedJsonObjectMenus != null && cachedJsonObjectCategories != null) {
                showOrdersPopup(cachedOrdersList, cachedJsonObjectMenus, cachedJsonObjectCategories);
            } else {
                    Toast.makeText(this, R.string.please_wait_orders, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showOrdersPopup(List<JSONObject> ordersList, JSONObject jsonObjectMenus, JSONObject jsonObjectCategories) {
        ReprintOrdersHelper.show(
                this,
                this,
                ordersList,
                jsonObjectMenus,
                jsonObjectCategories,
                "reprint".equals(getIntent().getStringExtra("action"))
        );
    }

    @Override
    public boolean isPrinterAvailable() {
        return Boolean.TRUE.equals(isPrinterSelected) && selectedDevice != null;
    }

    @Override
    public Chip chipReceipt() {
        return chipReceipt;
    }

    @Override
    public Chip chipKitchen() {
        return chipKitchen;
    }

    @Override
    public String shopName() {
        return shop_name;
    }

    @Override
    public DocketStringModeler docketStringModeler() {
        return docketStringModeler;
    }

    @Override
    public android.media.MediaPlayer mediaPlayer() {
        return mediaPlayer;
    }

    @Override
    public void printOrder(String printInfo, String orderId) {
        TIJobPrintBluetooth(printInfo, orderId);
    }

    @Override
    public FragmentManager fragmentManager() {
        return getSupportFragmentManager();
    }

    @Override
    public void onPrinterChanged(DeviceConnection device, boolean selected) {
        selectedDevice = device;
        isPrinterSelected = selected;
        updateTestPrintButtonState();
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
        
        // Update test print button state when activity resumes
        updateTestPrintButtonState();
        
        // Fetch data immediately when app comes to foreground
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        String savedToken = new AuthStore(this).getToken();
        if (savedToken == null) {
            savedToken = "";
        }
        
        // Check if URLs are initialized and user is logged in
        ShopCatalogRepository.observe(catalogObserver);
        if (!savedToken.isEmpty() && domain_shop != null && !domain_shop.isEmpty() && location_id != null) {
            ShopCatalogRepository.Catalog latest = ShopCatalogRepository.latest();
            if (latest != null && !domain_shop.equals(latest.domain)) {
                ShopCatalogRepository.invalidate();
                latest = null;
            }
            if (shouldShowReprintPopup || latest == null) {
                fetchCatalog(shouldShowReprintPopup);
            } else {
                catalogObserver.onSuccess(latest);
            }
            if (PrintMonitorService.isEnabled(this)) {
                startPrintMonitor();
            }
        }
        
        updateUIBasedOnServiceStatus();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Save chip preferences when activity is paused
        ShopCatalogRepository.unobserve(catalogObserver);
        if (chipReceipt != null && chipKitchen != null) {
            SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("chip_receipt_checked", chipReceipt.isChecked());
            editor.putBoolean("chip_kitchen_checked", chipKitchen.isChecked());
            editor.apply();
        }
    }
    
    private void fetchCatalog(boolean force) {
        if (domain_shop == null || domain_shop.isEmpty() || location_id == null) {
            return;
        }
        ShopCatalogRepository.fetch(
                networkHelperViewModel.getNetworkHelper(),
                domain_shop,
                location_id,
                force,
                catalogObserver
        );
    }

    private void showUpdatePopup() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.update_available)
                .setMessage(R.string.update_available_message)
                .setPositiveButton(R.string.update, (dialog, which) -> {
                    ApkUpdateInstaller.downloadVerifyAndInstall(this, apkFile, apkSha256);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        // Navigate back to Dashboard - just finish to return to existing DashboardActivity in back stack
        finish();
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
        JSONObject dataObjectOrders;
        List<JSONObject> ordersList = new ArrayList<>();
        JSONObject jsonObjectOrders, jsonObjectMenus = null, jsonObjectCategories = null;
        if (results != null && results[0] != null && results[1] != null) {
            try {
                jsonObjectOrders = new JSONObject(results[0]);
                jsonObjectMenus = new JSONObject(results[1]);
                jsonObjectCategories = new JSONObject(results[2]);

                printedOrders = IdManager.getIds(this);
                JSONArray dataArrayOrders = jsonObjectOrders.getJSONArray("data");
                Log.d("ReprintOrders", "API returned " + dataArrayOrders.length() + " orders");
                
                // Count open orders (not completed - status_id != 5) and check for new unprinted orders
                int openOrderCount = 0;
                boolean hasNewUnprintedOrder = false;
                List<String> newOrderIds = new ArrayList<>();
                
                // On initial load, populate soundPlayedOrders with all existing unprinted orders
                // to avoid playing sound for orders that were already there when app started
                if (isInitialLoad) {
                    Log.d("MainActivity", "Initial load - marking all existing orders as 'sound played'");
                    for (int i = 0; i < dataArrayOrders.length(); i++) {
                        try {
                            JSONObject order = dataArrayOrders.getJSONObject(i);
                            String orderId = order.getString("id");
                            if (!printedOrders.contains(orderId)) {
                                soundPlayedOrders.add(orderId);
                            }
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error processing order during initial load", e);
                        }
                    }
                    isInitialLoad = false;
                    Log.d("MainActivity", "Initial load complete. Marked " + soundPlayedOrders.size() + " orders as 'sound played'");
                }
                
                for (int i = 0; i < dataArrayOrders.length(); i++) {
                    dataObjectOrders = dataArrayOrders.getJSONObject(i);
                    ordersList.add(dataObjectOrders); // Add to orders list
                    
                    try {
                        String orderId = dataObjectOrders.getString("id");
                        if (!OrderStatusMapper.isCompleted(dataObjectOrders)) {
                            openOrderCount++;
                        }
                        
                        boolean isNotPrinted = !printedOrders.contains(orderId);
                        boolean hasNotPlayedSound = !soundPlayedOrders.contains(orderId);
                        
                        if (isNotPrinted && hasNotPlayedSound) {
                            hasNewUnprintedOrder = true;
                            newOrderIds.add(orderId);
                            soundPlayedOrders.add(orderId);
                            Log.d("MainActivity", "New unprinted order detected: " + orderId);
                        }
                    } catch (Exception e) {
                        // If there's an error checking status, include the order
                        openOrderCount++;
                        Log.e("MainActivity", "Error processing order for sound notification", e);
                    }
                }
                
                if (hasNewUnprintedOrder) {
                    Log.d("MainActivity", "New unprinted orders found: " + newOrderIds.size() + " orders - " + newOrderIds.toString());
                } else {
                    Log.d("MainActivity", "No new unprinted orders found. Printed orders: " + printedOrders.size() + ", Sound played: " + soundPlayedOrders.size());
                }
                
                // Update the order count badge
                updateOrderCountBadge(openOrderCount);

                Log.d("MainActivity", "Orders List Size: " + ordersList.size());

                // Cache orders data for reprint functionality
                cachedOrdersList = ordersList;
                cachedJsonObjectMenus = jsonObjectMenus;
                cachedJsonObjectCategories = jsonObjectCategories;
                
                Log.d("ReprintOrders", "Cached orders for reprint: " + cachedOrdersList.size());

                // Show popup automatically if requested (e.g., from DashboardActivity)
                if (shouldShowReprintPopup && !ordersList.isEmpty()) {
                    shouldShowReprintPopup = false;
                    // Only show popup if Activity is still valid
                    if (!isFinishing() && !isDestroyed() && getWindow() != null && 
                        getWindow().getDecorView() != null && getWindow().getDecorView().getWindowToken() != null) {
                        showOrdersPopup(ordersList, jsonObjectMenus, jsonObjectCategories);
                    } else {
                        Log.w("MainActivity", "Cannot show popup - Activity is not in valid state");
                    }
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

    public BluetoothPrinterHelper.OnBluetoothPermissionsGranted onBluetoothPermissionsGranted;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        BluetoothPrinterHelper.handlePermissionResult(requestCode, grantResults, this, onBluetoothPermissionsGranted);
    }

    public void checkBluetoothPermissions(BluetoothPrinterHelper.OnBluetoothPermissionsGranted onBluetoothPermissionsGranted) {
        this.onBluetoothPermissionsGranted = onBluetoothPermissionsGranted;
        BluetoothPrinterHelper.checkPermissions(this, onBluetoothPermissionsGranted);
    }

    public void tiPrintMonitoring() {
        if (!PrintMonitorService.isEnabled(this)) {
            if (selectedDevice == null) {
                Toast.makeText(context, R.string.select_printer_text, Toast.LENGTH_SHORT).show();
                isPrinterSelected = false;
            } else {
                startPrintMonitor();
                isPrinterSelected = true;
            }
        } else {
            stopPrintMonitor();
        }
    }

    private void startPrintMonitor() {
        PrintMonitorService.requestNotificationPermissionIfNeeded(this);
        PrintMonitorService.start(this);
        isServiceStarted = true;
        int buttonColor = ContextCompat.getColor(this, R.color.light_green);
        if (button_ti_print != null) {
            setButtonBackgroundWithRoundedCorners(button_ti_print, buttonColor);
            button_ti_print.setText(R.string.printer_active);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void stopPrintMonitor() {
        PrintMonitorService.stop(this);
        isServiceStarted = false;
        if (networkHelperViewModel.getNetworkHelper().isNetworkTaskRunning()) {
            networkHelperViewModel.getNetworkHelper().cancelNetworkTask();
        }
        networkHelperViewModel.cancelTimer();
        int buttonColor = ContextCompat.getColor(this, R.color.colorAccent);
        if (button_ti_print != null) {
            setButtonBackgroundWithRoundedCorners(button_ti_print, buttonColor);
            button_ti_print.setText(R.string.printer_inactive);
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void startService() {
        startPrintMonitor();
    }

    private void stopService() {
        stopPrintMonitor();
    }

    private void changeColorOfButton(int buttonColor, Button button) {
        int buttonColorID = ContextCompat.getColor(this, buttonColor);
        setButtonBackgroundWithRoundedCorners(button, buttonColorID);
    }
    
    private void setButtonBackgroundWithRoundedCorners(Button button, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        // Convert 8dp to pixels for rounded corners (matching button_primary.xml)
        float cornerRadiusPx = 8 * getResources().getDisplayMetrics().density;
        drawable.setCornerRadius(cornerRadiusPx);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            button.setBackground(drawable);
        } else {
            button.setBackgroundDrawable(drawable);
        }
    }

private void restartWebservice(int buttonColor){
    int buttonColorID = ContextCompat.getColor(this, buttonColor);
    if (button_ti_print != null) {
        setButtonBackgroundWithRoundedCorners(button_ti_print, buttonColorID);
    }
}

    private void updateUIBasedOnServiceStatus() {
        boolean active = PrintMonitorService.isEnabled(this);
        isServiceStarted = active;
        Constants.isServiceActive = active;
        if (active) {
            int buttonColor = ContextCompat.getColor(this, R.color.light_green);
            if (button_ti_print != null) {
                setButtonBackgroundWithRoundedCorners(button_ti_print, buttonColor);
                button_ti_print.setText(R.string.printer_active);
            }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            int buttonColor = ContextCompat.getColor(this, R.color.colorAccent);
            if (button_ti_print != null) {
                setButtonBackgroundWithRoundedCorners(button_ti_print, buttonColor);
                button_ti_print.setText(R.string.printer_inactive);
            }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
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
        printWithSelectedPrinter(print_info, new AsyncEscPosPrint.OnPrintFinished() {
            @Override
            public void onError(com.dantsu.thermalprinter.async.AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                Log.e("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : An error occurred !");
                WebViewDialogFragment webViewDialogFragment =
                        (WebViewDialogFragment) fragmentManager.findFragmentByTag("dialog_webview");
                if (webViewDialogFragment != null) {
                    webViewDialogFragment.dismiss();
                }
                changeColorOfButton(R.color.warning, button_ti_print);
                Toast.makeText(context, R.string.printer_connection_lost_text, Toast.LENGTH_LONG).show();
                isLongerConnectionTime = true;
                button_ti_print.setText(R.string.printer_connection_disrupted_text);
            }

            @Override
            public void onSuccess(com.dantsu.thermalprinter.async.AsyncEscPosPrinter asyncEscPosPrinter) {
                Log.i("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : Print is finished !");
                if (isLongerConnectionTime) {
                    changeColorOfButton(R.color.light_green, button_ti_print);
                    Toast.makeText(context, R.string.printer_connection_reactivated_text, Toast.LENGTH_SHORT).show();
                    isLongerConnectionTime = false;
                    button_ti_print.setText(R.string.printer_active);
                    WebViewDialogFragment webViewDialogFragment =
                            (WebViewDialogFragment) fragmentManager.findFragmentByTag("dialog_webview");
                    if (webViewDialogFragment != null) {
                        webViewDialogFragment.setPrinterCircleColor(R.color.light_green);
                    }
                }
                printedOrders.add(orderId);
                soundPlayedOrders.remove(orderId);
                IdManager.addId(context, orderId);
            }
        });
    }

    private void printWithSelectedPrinter(String print_info, AsyncEscPosPrint.OnPrintFinished callback) {
        if (selectedDevice == null) {
            selectedDevice = PrintDispatcher.resolve(this);
        }
        if (selectedDevice == null) {
            Toast.makeText(context, R.string.select_printer_text, Toast.LENGTH_SHORT).show();
            return;
        }
        if (printerConfig.settings().getConnectionType() == PrinterSettings.ConnectionType.BLUETOOTH) {
            this.checkBluetoothPermissions(() -> PrintDispatcher.print(this, selectedDevice, print_info, callback));
        } else {
            PrintDispatcher.print(this, selectedDevice, print_info, callback);
        }
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
            url = "https://" + url;
        showWebViewDialog(url);
    }

    private void showWebViewDialog(String url) {
        FragmentManager fm = getSupportFragmentManager();
        WebViewDialogFragment webViewDialogFragment;
        
        webViewDialogFragment = WebViewDialogFragment.newInstance(url);

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
        
        // Stop order-hook listener
        PrintMonitorService.stopListening(this);
        isServiceStarted = false;
            
        // Clear web view cookies and logout from website
        clearWebViewCookies();
        
        // Reset UI
        if (textview_ti_header != null) textview_ti_header.setText(R.string.login_text);
        if (button_ti_login != null) button_ti_login.setVisibility(View.VISIBLE);
        if (button_ti_logout != null) button_ti_logout.setVisibility(View.GONE);
        
        // Disable all buttons
        printerConfig.setEnabled(false);
        if (button_ti_print != null) button_ti_print.setEnabled(false);
        if (button_ti_clear_ids != null) button_ti_clear_ids.setEnabled(false);
        if (button_ti_testprint != null) button_ti_testprint.setEnabled(false);
        if (button_dashboard != null) button_dashboard.setEnabled(false);
        
        // Show login dialog
        LoginUser();
    }

    public static void resetPrintHistory(Context ctx) {
        if (ctx != null) {
            IdManager.clearIds(ctx);
        }
        printedOrders.clear();
        soundPlayedOrders.clear();
        isInitialLoad = true;
        ShopCatalogRepository.invalidate();
    }

    @Override
    public void onLoginSuccess(String domain, String username) {
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        String previousDomain = sharedPreferences.getString("domain_shop", "");
        if (!previousDomain.equals(domain) && !previousDomain.isEmpty()) {
            Log.d("MainActivity", "Shop changed from " + previousDomain + " to " + domain + ". Clearing shop-specific data.");
            clearShopSpecificData();
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("shop_id", 1);
        editor.putString("domain_shop", domain);
        editor.putString("username", username);
        editor.remove("password");
        editor.remove("token");
        if (!sharedPreferences.contains("location_id")) {
            editor.putInt("location_id", 1);
        }
        editor.apply();

        domain_shop = domain;
        location_id = sharedPreferences.getInt("location_id", 1);
        String configShopName = getShopNameFromConfiguration(this, domain);
        shop_name = (configShopName != null && !configShopName.isEmpty()) ? configShopName : domain;
        if (textview_ti_header != null) {
            textview_ti_header.setText(shop_name);
        }
        String[] urls = ShopCatalogRepository.catalogUrls(domain_shop, location_id);
        tiOrdersEndpointURL = urls[0];
        tiMenusEndpointURL = urls[1];
        tiCategoriesEndpointURL = urls[2];
        initilizeURLs();
        enableAllButtons();

        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        ShopCatalogRepository.unobserve(catalogObserver);
        if (printerConfig != null) {
            printerConfig.unregister();
        }
        super.onDestroy();
    }


    @SuppressLint("SimpleDateFormat")
    public boolean TITestPrinter(boolean normal_click) {
        if (selectedDevice == null) {
            selectedDevice = PrintDispatcher.resolve(this);
        }
        if (selectedDevice == null || !isPrinterSelected) {
            Toast.makeText(context, R.string.select_printer_text, Toast.LENGTH_SHORT).show();
            return false;
        }

        String print_info = DocketStringModeler.TITestPrinter(normal_click, selectedDevice, context);
        printWithSelectedPrinter(print_info, new AsyncEscPosPrint.OnPrintFinished() {
            @Override
            public void onError(com.dantsu.thermalprinter.async.AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                Log.e("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : An error occurred !");
            }

            @Override
            public void onSuccess(com.dantsu.thermalprinter.async.AsyncEscPosPrinter asyncEscPosPrinter) {
                Log.i("Async.OnPrintFinished", "AsyncEscPosPrint.OnPrintFinished : Print is finished !");
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.stop();
                        }
                        mediaPlayer.reset();
                        mediaPlayer = MediaPlayer.create(context, R.raw.newordersound);
                        if (mediaPlayer != null) {
                            mediaPlayer.start();
                        }
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error playing test print sound", e);
                    }
                }
            }
        });
        return true;
    }
}

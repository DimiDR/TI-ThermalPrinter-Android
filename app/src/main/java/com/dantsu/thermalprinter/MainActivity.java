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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

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
import com.dantsu.thermalprinter.helpClasses.UserUtils;
import com.dantsu.thermalprinter.model.NetworkHelperViewModel;

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
    private static Button button_ti_dashboard;
    private static Button button_ti_updates;
    private static Button button_ti_landing_page;
    private static Button button_ti_testprint;
    private Button button_ti_login;
    private static TextView textview_ti_header;
    private boolean userSelected = false; // TODO: no buttons should work if user is not selected
    private static Integer shop_id;
    private static String domain_shop;
    private static String domain_website = "";
    private static String username = "";
    private static String password = "";
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
    long period = 6 * 10000; // set to 60000 for 1 minute //TODO change
    DocketStringModeler docketStringModeler;
    private NetworkHelperViewModel networkHelperViewModel;
    private String apkFile;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        networkHelperViewModel = new ViewModelProvider(this).get(NetworkHelperViewModel.class);

        button_bluetooth_browse = this.findViewById(R.id.button_bluetooth_browse);
        button_reprint = this.findViewById(R.id.button_reprint);
        progressBar = findViewById(R.id.progressBar);

        button_bluetooth_browse.setOnClickListener(view -> browseBluetoothDevice());
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
        button_ti_updates.setOnClickListener(view -> showUpdatePopup());
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

        button_reprint.setOnClickListener(v -> {
            progressBar.setVisibility(View.VISIBLE);
            button_reprint.setVisibility(View.GONE);
            RePrintTask task = new RePrintTask();
            task.execute(tiOrdersEndpointURL, tiMenusEndpointURL, tiCategoriesEndpointURL);
        });
    }

    public void initilizeURLs() {
        // Retrieve saved login details from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        shop_id = sharedPreferences.getInt("shop_id", 0);
//        String savedUsername = sharedPreferences.getString("username", "");
//        String savedPassword = sharedPreferences.getString("password", "");
//        String savedDomainShop = sharedPreferences.getString("domain_shop", "");
//        String savedDomainWebsite = sharedPreferences.getString("domain_website", "");
//        String savedKitchenViewWebsite = sharedPreferences.getString("kitchen_view", "");
//        shop_name  = sharedPreferences.getString("shop_name", "");

        if (shop_id == 0) {
            Toast.makeText(context, "No User selected", Toast.LENGTH_SHORT).show();
            return ;
        }

        for (UserUtils.User user : users) {
            if (Objects.equals(user.shop_id, shop_id)) {
                username = user.username;
                password = user.password;
                shop_name = user.shop_name;
                domain_shop = user.domain_shop;
                domain_website = user.domain_website;
                tiDashboardURL = user.domain_shop + "/admin";
                tiKitchenViewURL =  user.kitchen_view;
                tiLandingPage = user.domain_website + "/";
                textview_ti_header.setText(user.shop_name);
                tiOrdersEndpointURL = user.domain_shop + "/api/orders?sort=order_id desc&pageLimit=50";
                tiMenusEndpointURL = user.domain_shop + "/api/menus?include=categories&pageLimit=5000";
                tiCategoriesEndpointURL = user.domain_shop + "/api/categories";
                break; // Exit the loop once the user is found
            }
        }

//        domain_shop = users.get(i).domain_shop;
//        domain_website = users.get(i).domain_website;
//        shop_name = users.get(i).shop_name;
//        tiKitchenViewURL = users.get(i).kitchen_view;
//        username = users.get(i).username;
//        tiOrdersEndpointURL = domain_shop + "/api/orders?sort=order_id desc&pageLimit=50";
//        tiDashboardURL = domain_shop + "/admin";
//        //tiKitchenViewURL = domain_shop + "/admin/thoughtco/kitchendisplay/summary/view/1";
//        tiLandingPage = domain_website + "/";

//        if (!savedUsername.isEmpty() && !savedPassword.isEmpty()) {
            // Set saved URLs to your URL fields
//            tiOrdersEndpointURL = savedDomainShop + "/api/orders?sort=order_id desc&pageLimit=50";
//            tiDashboardURL = savedDomainShop + "/admin";
            //tiKitchenViewURL = savedDomainShop + "/admin/thoughtco/kitchendisplay/summary/view/1";
//            tiKitchenViewURL = savedKitchenViewWebsite;
//            tiLandingPage = savedDomainWebsite + "/";
//            textview_ti_header.setText(savedUsername);
//            tiMenusEndpointURL = savedDomainShop + "/api/menus?include=categories&pageLimit=5000";
//            tiCategoriesEndpointURL = savedDomainShop + "/api/categories";

            //activate the buttons
            button_bluetooth_browse.setEnabled(true);
            button_ti_print.setEnabled(true);
            button_ti_clear_ids.setEnabled(true);
            button_ti_kitchen_view.setEnabled(true);
            button_ti_dashboard.setEnabled(true);
            button_ti_landing_page.setEnabled(true);
            button_ti_testprint.setEnabled(true);
            button_reprint.setEnabled(true);
//        }
    }

    // Inside your MainActivity

    private void showOrdersPopup(List<JSONObject> ordersList, JSONObject jsonObjectMenus, JSONObject jsonObjectCategories) {
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
                TIJobPrintBluetooth(docketStringModeler.startPrinting(ordersList.get(position), jsonObjectMenus, jsonObjectCategories, mediaPlayer, shop_name), orderId);
            }
        });
        listViewOrders.setAdapter(adapter);

        // Show the popup window
        popupWindow.showAtLocation(findViewById(R.id.mainLayout), Gravity.CENTER, 0, 0);
    }



    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateUIBasedOnServiceStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIBasedOnServiceStatus();
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
                    TIJobPrintBluetooth(docketStringModeler.startPrinting(dataObjectOrders, jsonObjectMenus, jsonObjectCategories, mediaPlayer, shop_name), orderID);
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

    public void browseBluetoothDevice() {
        this.checkBluetoothPermissions(() -> {
            final BluetoothConnection[] bluetoothDevicesList = (new BluetoothPrintersConnections()).getList();

            if (bluetoothDevicesList != null) {
                //final String[] items = new String[bluetoothDevicesList.length + 1];// removed default printer
                final String[] items = new String[bluetoothDevicesList.length];
                //items[0] = "Default printer"; // removed default printer
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
                    //items[++i] = device.getDevice().getName();// removed default printer
                    items[i] = device.getDevice().getName();
                }

                AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                alertDialog.setTitle("Bluetooth printer selection");
                alertDialog.setItems(
                        items,
                        (dialogInterface, i1) -> {
                            //int index = i1 - 1;// removed default printer
                            int index = i1;
                            if (index == -1) {
                                selectedDevice = null;
                            } else {
                                selectedDevice = bluetoothDevicesList[index];
                                int buttonColor = ContextCompat.getColor(this, R.color.light_green);
                                button_bluetooth_browse.setBackgroundColor(buttonColor);
                            }
                            Button button = (Button) findViewById(R.id.button_bluetooth_browse);
                            button.setText(items[i1]);
                        }
                );

                AlertDialog alert = alertDialog.create();
                //alert.setCanceledOnTouchOutside(false);
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
                Toast.makeText(context, "Bitte einen Drucker wählen", Toast.LENGTH_SHORT).show();
            } else {
                startService(); // This will handle starting and binding the service
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
            button_ti_print.setBackgroundColor(buttonColor);
            button_ti_print.setText("Drucker ist Aktiv");
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
            button_ti_print.setBackgroundColor(buttonColor);
            button_ti_print.setText("Drucker ist inaktiv");

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
        }
    }



    private void updateUIBasedOnServiceStatus() {
        // Retrieve saved login details from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        String savedUsername = sharedPreferences.getString("username", "");
        String savedPassword = sharedPreferences.getString("password", "");
        if (!savedUsername.isEmpty() && !savedPassword.isEmpty()){
            if (Constants.isServiceActive) {
                // Update UI elements for active service
                int buttonColor = ContextCompat.getColor(this, R.color.colorPrimaryDark);
                button_ti_print.setBackgroundColor(buttonColor);
                button_ti_print.setText("Drucker ist Aktiv");
                MyWakeLockManager.acquireFullWakeLock(this);
                Log.d("MainActivity", "Wake lock acquired");
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                Log.d("MainActivity", "Screen on flag set");
            } else {
                // Update UI elements for inactive service
                int buttonColor = ContextCompat.getColor(this, R.color.colorAccent);
                button_ti_print.setBackgroundColor(buttonColor);
                button_ti_print.setText("Drucker ist inaktiv");
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

                Log.e("timerrrrrr", "running: ");
                // Ensure the activity is still bound and the task can be executed
                networkHelperViewModel.getNetworkHelper().fetchData(urls, (NetworkHelper.NetworkCallback) activity);

            }
        }, 0, period);
    }

    class RePrintTask extends AsyncTask<String, Void, String[]> {
        Exception exception;

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
                    exception = e;
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
            if (exception != null) {
                progressBar.setVisibility(View.GONE);
                button_reprint.setVisibility(View.VISIBLE);
                Toast.makeText(context, ""+exception.getMessage(), Toast.LENGTH_SHORT).show();
            } else {
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

                        // Log the entire orders JSON before filtering
                        Log.e("MainActivity", "Full Orders JSON: " + jsonObjectOrders.toString());

                        JSONArray dataArrayOrders = jsonObjectOrders.getJSONArray("data");
                        for (int i = 0; i < dataArrayOrders.length(); i++) {
                            dataObjectOrders = dataArrayOrders.getJSONObject(i);
                            ordersList.add(dataObjectOrders); // Add to orders list
                        }

                        Log.e("MainActivity", "Orders List Size: " + ordersList.size());

                    } catch (JSONException e) {
                        Toast.makeText(context, ""+e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                    // Show the popup when the button is clicked, only if ordersList is not empty
                    if (!ordersList.isEmpty()) {
                        showOrdersPopup(ordersList, jsonObjectMenus, jsonObjectCategories);
                    } else {
                        showError("No re-printable orders found");
                    }
                } else {
                    showError("Failed to fetch data from web service");
                }
            }
        }
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
                        //reinitialize the variables
                        ((MainActivity)getActivity()).initilizeURLs();

                        button_bluetooth_browse.setEnabled(true);
                        button_ti_print.setEnabled(true);
                        button_ti_clear_ids.setEnabled(true);
                        button_ti_kitchen_view.setEnabled(true);
                        button_ti_dashboard.setEnabled(true);
                        button_reprint.setEnabled(true);
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
                    shop_id = users.get(i).shop_id;
                    domain_shop = users.get(i).domain_shop;
                    domain_website = users.get(i).domain_website;
                    shop_name = users.get(i).shop_name;
                    tiKitchenViewURL = users.get(i).kitchen_view;
                    username = users.get(i).username;
                    tiOrdersEndpointURL = domain_shop + "/api/orders?sort=order_id desc&pageLimit=50";
                    tiDashboardURL = domain_shop + "/admin";
                    //tiKitchenViewURL = domain_shop + "/admin/thoughtco/kitchendisplay/summary/view/1";
                    tiLandingPage = domain_website + "/";
                    //save login data
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putInt("shop_id", shop_id);
//                    editor.putString("username", username);
//                    editor.putString("password", password);
//                    editor.putString("shop_name", users.get(i).shop_name);
//                    editor.putString("domain_shop", users.get(i).domain_shop);
//                    editor.putString("domain_website", users.get(i).domain_website);
//                    editor.putString("kitchen_view", users.get(i).kitchen_view);
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
package com.dantsu.thermalprinter.helpClasses;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
import com.dantsu.thermalprinter.Constants;
import com.dantsu.thermalprinter.DashboardActivity;
import com.dantsu.thermalprinter.MainActivity;
import com.dantsu.thermalprinter.R;
import com.dantsu.thermalprinter.async.AsyncBluetoothEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrinter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Foreground service that polls TastyIgniter and prints new orders.
 * Survives the activity going to the background; does not survive reboot.
 */
public class PrintMonitorService extends Service implements NetworkHelper.NetworkCallback {
    private static final String TAG = "PrintMonitorService";
    private static final String CHANNEL_ID = "print_monitor";
    private static final int NOTIFICATION_ID = 1001;
    public static final String PREF_AUTO_PRINT = "auto_print_enabled";
    private static final String PREFS = "MyPrefs";

    private NetworkHelper networkHelper;
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean pollInFlight = new AtomicBoolean(false);
    private volatile boolean printerError;
    private DocketStringModeler docketStringModeler;
    private boolean initialLoad = true;

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_AUTO_PRINT, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_AUTO_PRINT, enabled)
                .apply();
        Constants.isServiceActive = enabled;
    }

    public static void start(Context context) {
        setEnabled(context, true);
        Intent intent = new Intent(context, PrintMonitorService.class);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        setEnabled(context, false);
        context.stopService(new Intent(context, PrintMonitorService.class));
    }

    public static void restoreIfEnabled(Context context) {
        Constants.isServiceActive = isEnabled(context);
        if (Constants.isServiceActive) {
            start(context);
        }
    }

    public static void requestNotificationPermissionIfNeeded(android.app.Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(
                        activity,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        1101
                );
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        networkHelper = new NetworkHelper(this);
        docketStringModeler = new DocketStringModeler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundInternal();
        if (!isEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Constants.isServiceActive = true;
        startPolling();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopPolling();
        if (networkHelper != null) {
            networkHelper.shutdown();
        }
        if (!isEnabled(this)) {
            Constants.isServiceActive = false;
        }
        super.onDestroy();
    }

    private void startPolling() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(this::pollOnce, 0, Constants.REFRESH_INTERVAL_KITCHEN_OPEN, TimeUnit.MILLISECONDS);
    }

    private void stopPolling() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (networkHelper != null) {
            networkHelper.cancelNetworkTask();
        }
    }

    private void pollOnce() {
        if (!pollInFlight.compareAndSet(false, true)) {
            return;
        }
        SharedPreferences loginPrefs = getSharedPreferences("loginPrefs", MODE_PRIVATE);
        String domain = loginPrefs.getString("domain_shop", "");
        int locationId = loginPrefs.getInt("location_id", 1);
        if (domain.isEmpty() || !new AuthStore(this).hasToken()) {
            pollInFlight.set(false);
            Log.w(TAG, "Skipping poll: missing shop domain or token");
            return;
        }
        String orders = domain + "/api/orders?sort=order_id desc&pageLimit=50&location=" + locationId;
        String menus = domain + "/api/menus?include=categories&pageLimit=5000&location=" + locationId;
        String categories = domain + "/api/categories?location=" + locationId;
        networkHelper.fetchData(new String[]{orders, menus, categories}, domain, this);
    }

    @Override
    public void onSuccess(String[] results) {
        try {
            if (results == null || results.length < 3 || results[0] == null || results[1] == null || results[2] == null) {
                return;
            }
            JSONObject jsonOrders = new JSONObject(results[0]);
            JSONObject jsonMenus = new JSONObject(results[1]);
            JSONObject jsonCategories = new JSONObject(results[2]);
            JSONArray data = jsonOrders.getJSONArray("data");

            Set<String> printedOrders = IdManager.getIds(this);
            MainActivity.getPrintedOrders().addAll(printedOrders);
            Set<String> soundPlayed = MainActivity.getSoundPlayedOrders();

            if (initialLoad) {
                for (int i = 0; i < data.length(); i++) {
                    String orderId = data.getJSONObject(i).optString("id", "");
                    if (!orderId.isEmpty() && !printedOrders.contains(orderId)) {
                        soundPlayed.add(orderId);
                    }
                }
                initialLoad = false;
            }

            boolean hasNew = false;
            for (int i = 0; i < data.length(); i++) {
                String orderId = data.getJSONObject(i).optString("id", "");
                if (!orderId.isEmpty() && !printedOrders.contains(orderId) && !soundPlayed.contains(orderId)) {
                    hasNew = true;
                    soundPlayed.add(orderId);
                }
            }
            if (hasNew) {
                playNewOrderSound();
            }

            JSONArray printable = DocketStringModeler.filterPrintableOrders(data, printedOrders);
            BluetoothConnection printer = resolvePrinter();
            if (printer == null) {
                Log.w(TAG, "Auto-print running but no printer is selected");
                return;
            }

            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean printReceipt = prefs.getBoolean("chip_receipt_checked", true);
            boolean printKitchen = prefs.getBoolean("chip_kitchen_checked", false);
            String shopName = resolveShopName();

            printerError = false;
            for (int i = 0; i < printable.length(); i++) {
                JSONObject order = printable.getJSONObject(i);
                String orderId = order.getString("id");
                if (printReceipt) {
                    printOrder(printer, docketStringModeler.startPrintingReceipt(order, jsonMenus, jsonCategories, null, shopName), orderId);
                }
                if (printKitchen) {
                    printOrder(printer, docketStringModeler.startPrintingKitchen(order, jsonMenus, jsonCategories, null, shopName), orderId);
                }
                if (printerError) {
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to process orders", e);
        } finally {
            pollInFlight.set(false);
        }
    }

    @Override
    public void onError(Exception exception) {
        pollInFlight.set(false);
        Log.e(TAG, "Order poll failed", exception);
    }

    private void printOrder(BluetoothConnection device, String printInfo, String orderId) {
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(device, 203, 48f, 32);
        printer.addTextToPrint(printInfo);
        if (printer.getPrinterConnection() != null) {
            printer.getPrinterConnection().disconnect();
        }
        new AsyncBluetoothEscPosPrint(getApplicationContext(), new AsyncEscPosPrint.OnPrintFinished() {
            @Override
            public void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException) {
                printerError = true;
                Log.e(TAG, "Print failed for order " + orderId + " code=" + codeException);
            }

            @Override
            public void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter) {
                IdManager.addId(PrintMonitorService.this, orderId);
                MainActivity.getPrintedOrders().add(orderId);
            }
        }).execute(printer);
    }

    private BluetoothConnection resolvePrinter() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int index = prefs.getInt("stored_index_printer", -1);
        if (index < 0) {
            return null;
        }
        BluetoothConnection[] devices = (new BluetoothPrintersConnections()).getList();
        if (devices == null || index >= devices.length) {
            return null;
        }
        return devices[index];
    }

    private String resolveShopName() {
        SharedPreferences loginPrefs = getSharedPreferences("loginPrefs", MODE_PRIVATE);
        String domain = loginPrefs.getString("domain_shop", "");
        ShopConfigUtils.Shop shop = ShopConfigUtils.getShopByDomain(this, domain);
        return shop != null ? shop.shop_name : domain;
    }

    private void playNewOrderSound() {
        try {
            MediaPlayer player = MediaPlayer.create(this, R.raw.newordersound);
            if (player == null) {
                return;
            }
            player.setOnCompletionListener(MediaPlayer::release);
            player.start();
        } catch (Exception e) {
            Log.e(TAG, "Could not play new-order sound", e);
        }
    }

    private void startForegroundInternal() {
        Intent launch = new Intent(this, DashboardActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_print_monitor_title))
                .setContentText(getString(R.string.notification_print_monitor_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pending)
                .setOngoing(true)
                .setSilent(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_print_monitor_channel),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}

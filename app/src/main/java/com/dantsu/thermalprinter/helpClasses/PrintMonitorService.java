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

import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.thermalprinter.Constants;
import com.dantsu.thermalprinter.DashboardActivity;
import com.dantsu.thermalprinter.MainActivity;
import com.dantsu.thermalprinter.R;
import com.dantsu.thermalprinter.async.AsyncEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrinter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Foreground service that waits for a TastyIgniter webhook (via ntfy or LAN HTTP)
 * and then plays a sound / prints new orders. Survives the activity going to
 * the background; does not survive reboot.
 */
public class PrintMonitorService extends Service implements NetworkHelper.NetworkCallback {
    private static final String TAG = "PrintMonitorService";
    private static final String CHANNEL_ID = "print_monitor";
    private static final int NOTIFICATION_ID = 1001;
    public static final String PREF_AUTO_PRINT = "auto_print_enabled";
    private static final String PREFS = "MyPrefs";

    private NetworkHelper networkHelper;
    private NtfySubscriber ntfySubscriber;
    private LocalHookServer localHookServer;
    private final AtomicBoolean fetchInFlight = new AtomicBoolean(false);
    private final AtomicBoolean hookWhileBusy = new AtomicBoolean(false);
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
        startListening(context);
    }

    public static void stop(Context context) {
        setEnabled(context, false);
        Intent intent = new Intent(context, PrintMonitorService.class);
        if (new AuthStore(context).hasToken()) {
            ContextCompat.startForegroundService(context, intent);
        } else {
            context.stopService(intent);
        }
    }

    public static void startListening(Context context) {
        if (!new AuthStore(context).hasToken()) {
            return;
        }
        Intent intent = new Intent(context, PrintMonitorService.class);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stopListening(Context context) {
        context.stopService(new Intent(context, PrintMonitorService.class));
    }

    public static void restoreIfEnabled(Context context) {
        restoreIfLoggedIn(context);
    }

    public static void restoreIfLoggedIn(Context context) {
        Constants.isServiceActive = isEnabled(context);
        if (new AuthStore(context).hasToken()) {
            startListening(context);
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
        ntfySubscriber = new NtfySubscriber();
        localHookServer = new LocalHookServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundInternal();
        if (!new AuthStore(this).hasToken()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Constants.isServiceActive = isEnabled(this);
        startHookListeners();
        if (initialLoad && fetchInFlight.compareAndSet(false, true)) {
            fetchOrders();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopHookListeners();
        if (networkHelper != null) {
            networkHelper.shutdown();
        }
        Constants.isServiceActive = isEnabled(this);
        super.onDestroy();
    }

    private void startHookListeners() {
        ntfySubscriber.start(OrderHookConfig.subscribeUrl(this), this::onHook);
        localHookServer.start(this::onHook);
    }

    private void stopHookListeners() {
        if (ntfySubscriber != null) {
            ntfySubscriber.stop();
        }
        if (localHookServer != null) {
            localHookServer.stop();
        }
        if (networkHelper != null) {
            networkHelper.cancelNetworkTask();
        }
    }

    private void onHook() {
        Log.d(TAG, "Order hook received");
        if (!fetchInFlight.compareAndSet(false, true)) {
            hookWhileBusy.set(true);
            return;
        }
        fetchOrders();
    }

    private void fetchOrders() {
        SharedPreferences loginPrefs = getSharedPreferences("loginPrefs", MODE_PRIVATE);
        String domain = loginPrefs.getString("domain_shop", "");
        if (domain.isEmpty() || !new AuthStore(this).hasToken()) {
            Log.w(TAG, "Skipping fetch: missing shop domain or token");
            finishFetch();
            return;
        }
        int locationId = ShopConfigUtils.resolveLocationId(this, domain);
        ShopCatalogRepository.fetchAsCallback(networkHelper, domain, locationId, true, this);
    }

    private void finishFetch() {
        fetchInFlight.set(false);
        sendOrdersChanged();
        if (hookWhileBusy.compareAndSet(true, false)) {
            onHook();
        }
    }

    private void sendOrdersChanged() {
        Intent intent = new Intent(Constants.ACTION_ORDERS_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
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

            if (!isEnabled(this)) {
                return;
            }

            JSONArray printable = DocketStringModeler.filterPrintableOrders(data, printedOrders);
            DeviceConnection printer = PrintDispatcher.resolve(this);
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
            finishFetch();
        }
    }

    @Override
    public void onError(Exception exception) {
        Log.e(TAG, "Order fetch after hook failed", exception);
        finishFetch();
    }

    private void printOrder(DeviceConnection device, String printInfo, String orderId) {
        PrintDispatcher.print(this, device, printInfo, new AsyncEscPosPrint.OnPrintFinished() {
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
        });
    }

    private String resolveShopName() {
        SharedPreferences loginPrefs = getSharedPreferences("loginPrefs", MODE_PRIVATE);
        String domain = loginPrefs.getString("domain_shop", "");
        ShopConfigUtils.applyShopPreferences(this, domain);
        return ShopConfigUtils.resolveShopName(this, domain);
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
        boolean autoPrint = isEnabled(this);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(autoPrint
                        ? R.string.notification_print_monitor_title
                        : R.string.notification_order_hook_title))
                .setContentText(getString(autoPrint
                        ? R.string.notification_print_monitor_text
                        : R.string.notification_order_hook_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pending)
                .setOngoing(true)
                .setSilent(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // connectedDevice requires a granted runtime BT/USB permission; fall back to dataSync
            // (webhook / order fetch) when those are missing — e.g. emulator without Bluetooth.
            int type = hasConnectedDevicePermission()
                    ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    : ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            try {
                startForeground(NOTIFICATION_ID, notification, type);
            } catch (SecurityException e) {
                Log.w(TAG, "FGS type " + type + " rejected, falling back to dataSync", e);
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            }
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private boolean hasConnectedDevicePermission() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
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

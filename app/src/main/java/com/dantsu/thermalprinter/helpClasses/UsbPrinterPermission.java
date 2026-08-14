package com.dantsu.thermalprinter.helpClasses;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Requests USB host permission for a selected printer.
 */
public class UsbPrinterPermission {
    private static final String ACTION_USB_PERMISSION = "com.dantsu.thermalprinter.USB_PERMISSION";

    public interface Callback {
        void onPermissionResult(UsbDevice device, boolean granted);
    }

    private final Context context;
    private BroadcastReceiver receiver;

    public UsbPrinterPermission(Context context) {
        this.context = context.getApplicationContext();
    }

    public void request(@NonNull UsbDevice device, @NonNull Callback callback) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            callback.onPermissionResult(device, false);
            return;
        }
        if (usbManager.hasPermission(device)) {
            callback.onPermissionResult(device, true);
            return;
        }
        unregister();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!ACTION_USB_PERMISSION.equals(intent.getAction())) {
                    return;
                }
                UsbDevice extraDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                unregister();
                callback.onPermissionResult(extraDevice != null ? extraDevice : device, granted);
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(ACTION_USB_PERMISSION).setPackage(context.getPackageName()),
                flags
        );
        usbManager.requestPermission(device, permissionIntent);
    }

    public void unregister() {
        if (receiver != null) {
            try {
                context.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
            }
            receiver = null;
        }
    }
}

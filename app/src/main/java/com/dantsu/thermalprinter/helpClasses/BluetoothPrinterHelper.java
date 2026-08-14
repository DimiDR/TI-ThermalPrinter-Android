package com.dantsu.thermalprinter.helpClasses;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
import com.dantsu.thermalprinter.R;

public final class BluetoothPrinterHelper {
    public static final int PERMISSION_BLUETOOTH = 1;
    public static final int PERMISSION_BLUETOOTH_ADMIN = 2;
    public static final int PERMISSION_BLUETOOTH_CONNECT = 3;
    public static final int PERMISSION_BLUETOOTH_SCAN = 4;

    private BluetoothPrinterHelper() {}

    public interface OnBluetoothPermissionsGranted {
        void onPermissionsGranted();
    }

    public interface DeviceSelectionListener {
        void onDeviceSelected(BluetoothConnection device, int index, String name);
    }

    public static boolean handlePermissionResult(
            int requestCode,
            @NonNull int[] grantResults,
            Activity activity,
            OnBluetoothPermissionsGranted callback
    ) {
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        switch (requestCode) {
            case PERMISSION_BLUETOOTH:
            case PERMISSION_BLUETOOTH_ADMIN:
            case PERMISSION_BLUETOOTH_CONNECT:
            case PERMISSION_BLUETOOTH_SCAN:
                checkPermissions(activity, callback);
                return true;
            default:
                return false;
        }
    }

    public static void checkPermissions(Activity activity, OnBluetoothPermissionsGranted callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH}, PERMISSION_BLUETOOTH);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, PERMISSION_BLUETOOTH_ADMIN);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_BLUETOOTH_CONNECT);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH_SCAN}, PERMISSION_BLUETOOTH_SCAN);
        } else if (callback != null) {
            callback.onPermissionsGranted();
        }
    }

    public static void browse(
            Activity activity,
            int selectedIndex,
            boolean manualOpen,
            DeviceSelectionListener listener
    ) {
        checkPermissions(activity, () -> {
            final BluetoothConnection[] bluetoothDevicesList = (new BluetoothPrintersConnections()).getList();

            if (bluetoothDevicesList == null) {
                if (manualOpen) {
                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                            .setTitle(R.string.bluetooth_error)
                            .setMessage(R.string.bluetooth_error_message)
                            .setPositiveButton(R.string.ok, null)
                            .show());
                }
                return;
            }

            if (bluetoothDevicesList.length == 0) {
                if (manualOpen) {
                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                            .setTitle(R.string.no_bluetooth_printers_found)
                            .setMessage(R.string.no_bluetooth_printers_message)
                            .setPositiveButton(R.string.ok, null)
                            .show());
                }
                return;
            }

            final String[] items = new String[bluetoothDevicesList.length];
            for (int i = 0; i < bluetoothDevicesList.length; i++) {
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return;
                }
                android.bluetooth.BluetoothDevice device = bluetoothDevicesList[i].getDevice();
                String name = device != null ? device.getName() : null;
                String address = device != null ? device.getAddress() : "";
                if (name == null || name.isEmpty()) {
                    name = address;
                }
                if (device != null && device.getType() == android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE) {
                    name = name + " (BLE)";
                }
                items[i] = name;
            }

            if (selectedIndex >= 0 && selectedIndex < bluetoothDevicesList.length) {
                listener.onDeviceSelected(bluetoothDevicesList[selectedIndex], selectedIndex, items[selectedIndex]);
                return;
            }
            if (!manualOpen) {
                return;
            }

            activity.runOnUiThread(() -> {
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(activity);
                alertDialog.setTitle(R.string.bluetooth_printer_selection);
                alertDialog.setItems(items, (dialogInterface, i1) ->
                        listener.onDeviceSelected(bluetoothDevicesList[i1], i1, items[i1]));
                alertDialog.show();
            });
        });
    }
}

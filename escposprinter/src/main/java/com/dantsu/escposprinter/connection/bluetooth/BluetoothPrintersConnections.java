package com.dantsu.escposprinter.connection.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.Nullable;

import com.dantsu.escposprinter.exceptions.EscPosConnectionException;

public class BluetoothPrintersConnections extends BluetoothConnections {

    /**
     * Easy way to get the first bluetooth printer paired / connected.
     *
     * @return a EscPosPrinterCommands instance
     */
    @Nullable
    public static BluetoothConnection selectFirstPaired() {
        BluetoothPrintersConnections printers = new BluetoothPrintersConnections();
        BluetoothConnection[] bluetoothPrinters = printers.getList();

        if (bluetoothPrinters != null && bluetoothPrinters.length > 0) {
            for (BluetoothConnection printer : bluetoothPrinters) {
                try {
                    return printer.connect();
                } catch (EscPosConnectionException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * Find a paired device by MAC address.
     */
    @SuppressLint("MissingPermission")
    @Nullable
    public static BluetoothConnection findByAddress(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }
        BluetoothConnection[] devices = new BluetoothPrintersConnections().getList();
        if (devices == null) {
            return null;
        }
        for (BluetoothConnection connection : devices) {
            BluetoothDevice device = connection.getDevice();
            if (device != null && address.equalsIgnoreCase(device.getAddress())) {
                return connection;
            }
        }
        return null;
    }

    /**
     * Whether this device would have been listed by the old Imaging-class filter.
     * Used only to migrate a stored list index after the filter was removed.
     */
    public static boolean matchesLegacyPrinterClass(BluetoothDevice device) {
        if (device == null || device.getBluetoothClass() == null) {
            return false;
        }
        int majDeviceCl = device.getBluetoothClass().getMajorDeviceClass();
        int deviceCl = device.getBluetoothClass().getDeviceClass();
        return (majDeviceCl == BluetoothClass.Device.Major.IMAGING || majDeviceCl == 7936)
                && (deviceCl == 1664 || deviceCl == 7936 || deviceCl == BluetoothClass.Device.Major.IMAGING);
    }

    /**
     * Get a list of paired bluetooth devices that can be used as ESC/POS printers.
     * Chinese modules often report device class 0 / unclassified, so this no longer
     * filters on Imaging class.
     *
     * @return an array of BluetoothConnection
     */
    @SuppressLint("MissingPermission")
    @Nullable
    public BluetoothConnection[] getList() {
        return super.getList();
    }
}

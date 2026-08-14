package com.dantsu.escposprinter.connection.usb;

import android.content.Context;
import android.hardware.usb.UsbDevice;

import androidx.annotation.Nullable;

public class UsbPrintersConnections extends UsbConnections {

    /**
     * Create a new instance of UsbPrintersConnections
     *
     * @param context Application context
     */
    public UsbPrintersConnections(Context context) {
        super(context);
    }

    /**
     * Easy way to get the first USB printer paired / connected.
     *
     * @return a UsbConnection instance
     */
    @Nullable
    public static UsbConnection selectFirstConnected(Context context) {
        UsbPrintersConnections printers = new UsbPrintersConnections(context);
        UsbConnection[] bluetoothPrinters = printers.getList();
        
        if (bluetoothPrinters == null || bluetoothPrinters.length == 0) {
            return null;
        }

        return bluetoothPrinters[0];
    }
    
    
    /**
     * Get a list of USB printers.
     *
     * @return an array of UsbConnection
     */
    @Nullable
    public UsbConnection[] getList() {
        UsbConnection[] usbConnections = super.getList();

        if(usbConnections == null) {
            return null;
        }

        int i = 0;
        UsbConnection[] printersTmp = new UsbConnection[usbConnections.length];
        for (UsbConnection usbConnection : usbConnections) {
            UsbDevice device = usbConnection.getDevice();
            if (UsbDeviceHelper.findPrinterInterface(device) != null) {
                printersTmp[i++] = new UsbConnection(this.usbManager, device);
            }
        }

        UsbConnection[] usbPrinters = new UsbConnection[i];
        System.arraycopy(printersTmp, 0, usbPrinters, 0, i);
        return usbPrinters;
    }

    @Nullable
    public static UsbConnection findByIds(Context context, int vendorId, int productId) {
        UsbConnection[] printers = new UsbPrintersConnections(context).getList();
        if (printers == null) {
            return null;
        }
        for (UsbConnection connection : printers) {
            UsbDevice device = connection.getDevice();
            if (device != null && device.getVendorId() == vendorId && device.getProductId() == productId) {
                return connection;
            }
        }
        return null;
    }
}

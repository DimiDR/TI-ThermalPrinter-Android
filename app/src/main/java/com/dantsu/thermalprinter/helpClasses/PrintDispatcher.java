package com.dantsu.thermalprinter.helpClasses;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.hardware.usb.UsbDevice;

import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
import com.dantsu.escposprinter.connection.tcp.TcpConnection;
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections;
import com.dantsu.thermalprinter.async.AsyncBluetoothEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncEscPosPrinter;
import com.dantsu.thermalprinter.async.AsyncTcpEscPosPrint;
import com.dantsu.thermalprinter.async.AsyncUsbEscPosPrint;

/**
 * Resolves the saved printer and starts a print job over Bluetooth, USB or TCP.
 */
public final class PrintDispatcher {

    private PrintDispatcher() {
    }

    @SuppressLint("MissingPermission")
    public static DeviceConnection resolve(Context context) {
        PrinterSettings settings = new PrinterSettings(context);
        migrateLegacyBluetoothIndex(context, settings);
        switch (settings.getConnectionType()) {
            case USB:
                return UsbPrintersConnections.findByIds(
                        context,
                        settings.getUsbVendorId(),
                        settings.getUsbProductId()
                );
            case TCP:
                String host = settings.getTcpHost();
                if (host.isEmpty()) {
                    return null;
                }
                return new TcpConnection(host, settings.getTcpPort(), 15000);
            case BLUETOOTH:
            default:
                return BluetoothPrintersConnections.findByAddress(settings.getBluetoothAddress());
        }
    }

    public static AsyncEscPosPrinter createJob(Context context, DeviceConnection connection, String text) {
        PrinterSettings settings = new PrinterSettings(context);
        PrinterSettings.PaperSize paper = settings.getPaperSize();
        PrinterSettings.Charset charset = settings.getCharset();
        AsyncEscPosPrinter printer = new AsyncEscPosPrinter(
                connection,
                paper.dpi,
                paper.widthMm,
                paper.charsPerLine,
                charset.toEncoding()
        );
        printer.addTextToPrint(charset.sanitize(text));
        if (connection != null) {
            connection.disconnect();
        }
        return printer;
    }

    public static void print(Context context, DeviceConnection connection, String text, AsyncEscPosPrint.OnPrintFinished callback) {
        AsyncEscPosPrinter job = createJob(context, connection, text);
        createAsync(context, new PrinterSettings(context).getConnectionType(), callback).execute(job);
    }

    public static void printResolved(Context context, String text, AsyncEscPosPrint.OnPrintFinished callback) {
        print(context, resolve(context), text, callback);
    }

    private static AsyncEscPosPrint createAsync(Context context, PrinterSettings.ConnectionType type, AsyncEscPosPrint.OnPrintFinished callback) {
        switch (type) {
            case USB:
                return new AsyncUsbEscPosPrint(context, callback);
            case TCP:
                return new AsyncTcpEscPosPrint(context, callback);
            case BLUETOOTH:
            default:
                return new AsyncBluetoothEscPosPrint(context, callback);
        }
    }

    @SuppressLint("MissingPermission")
    public static void migrateLegacyBluetoothIndex(Context context, PrinterSettings settings) {
        if (!settings.getBluetoothAddress().isEmpty()) {
            return;
        }
        if (settings.getConnectionType() != PrinterSettings.ConnectionType.BLUETOOTH) {
            return;
        }
        int index = settings.getLegacyPrinterIndex();
        if (index < 0) {
            return;
        }
        BluetoothConnection[] devices = (new BluetoothPrintersConnections()).getList();
        if (devices == null) {
            return;
        }
        int filteredIndex = 0;
        for (BluetoothConnection connection : devices) {
            BluetoothDevice device = connection.getDevice();
            if (device != null && BluetoothPrintersConnections.matchesLegacyPrinterClass(device)) {
                if (filteredIndex == index) {
                    String name = device.getName();
                    settings.setBluetoothPrinter(device.getAddress(), name == null ? device.getAddress() : name);
                    return;
                }
                filteredIndex++;
            }
        }
    }
}

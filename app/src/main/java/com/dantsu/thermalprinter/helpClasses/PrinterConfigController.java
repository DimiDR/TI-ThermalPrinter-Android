package com.dantsu.thermalprinter.helpClasses;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.connection.usb.UsbConnection;
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections;
import com.dantsu.thermalprinter.R;

/**
 * Settings UI for Bluetooth / USB / TCP printer selection.
 */
public final class PrinterConfigController {
    public interface Listener {
        void onPrinterChanged(DeviceConnection device, boolean selected);
        void checkBluetoothPermissions(BluetoothPrinterHelper.OnBluetoothPermissionsGranted callback);
    }

    private final Activity activity;
    private final PrinterSettings printerSettings;
    private final UsbPrinterPermission usbPrinterPermission;
    private final Listener listener;

    private Button selectButton;
    private Spinner spinnerConnection;
    private Spinner spinnerPaper;
    private Spinner spinnerCharset;
    private LinearLayout layoutTcpSettings;
    private EditText editPrinterIp;
    private EditText editPrinterPort;
    private Button buttonSaveTcpPrinter;
    private boolean ignoreSpinnerEvents;
    private DeviceConnection selectedDevice;
    private boolean printerSelected;

    public PrinterConfigController(Activity activity, Listener listener) {
        this.activity = activity;
        this.printerSettings = new PrinterSettings(activity);
        this.usbPrinterPermission = new UsbPrinterPermission(activity);
        this.listener = listener;
    }

    public void bind() {
        selectButton = activity.findViewById(R.id.button_bluetooth_browse);
        spinnerConnection = activity.findViewById(R.id.spinner_printer_connection);
        spinnerPaper = activity.findViewById(R.id.spinner_printer_paper);
        spinnerCharset = activity.findViewById(R.id.spinner_printer_charset);
        layoutTcpSettings = activity.findViewById(R.id.layout_tcp_settings);
        editPrinterIp = activity.findViewById(R.id.edit_printer_ip);
        editPrinterPort = activity.findViewById(R.id.edit_printer_port);
        buttonSaveTcpPrinter = activity.findViewById(R.id.button_save_tcp_printer);

        bindSpinner(spinnerConnection, R.array.printer_connection_types);
        bindSpinner(spinnerPaper, R.array.printer_paper_sizes);
        bindSpinner(spinnerCharset, R.array.printer_charsets);

        if (selectButton != null) {
            selectButton.setOnClickListener(view -> onSelectPrinterClicked());
        }
        if (buttonSaveTcpPrinter != null) {
            buttonSaveTcpPrinter.setOnClickListener(view -> saveTcpPrinter());
        }

        ignoreSpinnerEvents = true;
        if (spinnerConnection != null) {
            spinnerConnection.setSelection(connectionTypeIndex(printerSettings.getConnectionType()), false);
            spinnerConnection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (ignoreSpinnerEvents) {
                        return;
                    }
                    printerSettings.setConnectionType(connectionTypeFromIndex(position));
                    selectedDevice = null;
                    printerSelected = false;
                    applyConnectionTypeUi();
                    restore();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
        if (spinnerPaper != null) {
            spinnerPaper.setSelection(printerSettings.getPaperSize() == PrinterSettings.PaperSize.MM_80 ? 1 : 0, false);
            spinnerPaper.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (ignoreSpinnerEvents) {
                        return;
                    }
                    printerSettings.setPaperSize(position == 1 ? PrinterSettings.PaperSize.MM_80 : PrinterSettings.PaperSize.MM_58);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
        if (spinnerCharset != null) {
            spinnerCharset.setSelection(charsetIndex(printerSettings.getCharset()), false);
            spinnerCharset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (ignoreSpinnerEvents) {
                        return;
                    }
                    printerSettings.setCharset(charsetFromIndex(position));
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
        ignoreSpinnerEvents = false;
        applyConnectionTypeUi();
        restore();
    }

    public void restore() {
        PrintDispatcher.migrateLegacyBluetoothIndex(activity, printerSettings);
        selectedDevice = PrintDispatcher.resolve(activity);
        printerSelected = selectedDevice != null
                || (printerSettings.getConnectionType() == PrinterSettings.ConnectionType.TCP
                && !printerSettings.getTcpHost().isEmpty());
        if (printerSelected && selectedDevice == null && printerSettings.getConnectionType() == PrinterSettings.ConnectionType.TCP) {
            selectedDevice = PrintDispatcher.resolve(activity);
            printerSelected = selectedDevice != null;
        }
        updateSelectedPrinterButton();
        listener.onPrinterChanged(selectedDevice, printerSelected);
    }

    public void setEnabled(boolean enabled) {
        if (selectButton != null) {
            selectButton.setEnabled(enabled);
        }
        if (spinnerConnection != null) {
            spinnerConnection.setEnabled(enabled);
        }
        if (spinnerPaper != null) {
            spinnerPaper.setEnabled(enabled);
        }
        if (spinnerCharset != null) {
            spinnerCharset.setEnabled(enabled);
        }
        if (buttonSaveTcpPrinter != null) {
            buttonSaveTcpPrinter.setEnabled(enabled);
        }
    }

    public DeviceConnection getSelectedDevice() {
        return selectedDevice;
    }

    public boolean isPrinterSelected() {
        return printerSelected;
    }

    public PrinterSettings settings() {
        return printerSettings;
    }

    public void unregister() {
        usbPrinterPermission.unregister();
    }

    private void bindSpinner(Spinner spinner, int arrayRes) {
        if (spinner == null) {
            return;
        }
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(activity, arrayRes, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private int connectionTypeIndex(PrinterSettings.ConnectionType type) {
        switch (type) {
            case USB:
                return 1;
            case TCP:
                return 2;
            case BLUETOOTH:
            default:
                return 0;
        }
    }

    private PrinterSettings.ConnectionType connectionTypeFromIndex(int index) {
        if (index == 1) {
            return PrinterSettings.ConnectionType.USB;
        }
        if (index == 2) {
            return PrinterSettings.ConnectionType.TCP;
        }
        return PrinterSettings.ConnectionType.BLUETOOTH;
    }

    private int charsetIndex(PrinterSettings.Charset charset) {
        switch (charset) {
            case CP437:
                return 1;
            case W1252:
                return 2;
            case GBK:
                return 3;
            case PC850:
            default:
                return 0;
        }
    }

    private PrinterSettings.Charset charsetFromIndex(int index) {
        switch (index) {
            case 1:
                return PrinterSettings.Charset.CP437;
            case 2:
                return PrinterSettings.Charset.W1252;
            case 3:
                return PrinterSettings.Charset.GBK;
            default:
                return PrinterSettings.Charset.PC850;
        }
    }

    private void applyConnectionTypeUi() {
        PrinterSettings.ConnectionType type = printerSettings.getConnectionType();
        boolean tcp = type == PrinterSettings.ConnectionType.TCP;
        if (layoutTcpSettings != null) {
            layoutTcpSettings.setVisibility(tcp ? View.VISIBLE : View.GONE);
        }
        if (selectButton != null) {
            selectButton.setVisibility(tcp ? View.GONE : View.VISIBLE);
            if (type == PrinterSettings.ConnectionType.USB) {
                selectButton.setText(R.string.select_usb_printer);
            } else if (!printerSelected) {
                selectButton.setText(R.string.select_bluetooth_printer);
            }
        }
        if (editPrinterIp != null) {
            editPrinterIp.setText(printerSettings.getTcpHost());
        }
        if (editPrinterPort != null) {
            editPrinterPort.setText(String.valueOf(printerSettings.getTcpPort()));
        }
    }

    private void updateSelectedPrinterButton() {
        if (selectButton == null || !printerSelected) {
            return;
        }
        int buttonColor = ContextCompat.getColor(activity, R.color.light_green);
        setRoundedBackground(selectButton, buttonColor);
        String name = printerSettings.getDisplayName();
        if (!name.isEmpty()) {
            selectButton.setText(name);
        }
        if (buttonSaveTcpPrinter != null && printerSettings.getConnectionType() == PrinterSettings.ConnectionType.TCP) {
            setRoundedBackground(buttonSaveTcpPrinter, buttonColor);
        }
    }

    private void onSelectPrinterClicked() {
        if (printerSettings.getConnectionType() == PrinterSettings.ConnectionType.USB) {
            browseUsbPrinter();
        } else {
            browseBluetoothDevice();
        }
    }

    private void saveTcpPrinter() {
        String host = editPrinterIp != null && editPrinterIp.getText() != null ? editPrinterIp.getText().toString().trim() : "";
        String portText = editPrinterPort != null && editPrinterPort.getText() != null ? editPrinterPort.getText().toString().trim() : "9100";
        if (host.isEmpty()) {
            Toast.makeText(activity, R.string.network_printer_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        int port = 9100;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ignored) {
        }
        printerSettings.setTcpPrinter(host, port);
        selectedDevice = PrintDispatcher.resolve(activity);
        printerSelected = selectedDevice != null;
        updateSelectedPrinterButton();
        listener.onPrinterChanged(selectedDevice, printerSelected);
        Toast.makeText(activity, R.string.network_printer_saved, Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("MissingPermission")
    private void browseBluetoothDevice() {
        listener.checkBluetoothPermissions(() -> BluetoothPrinterHelper.browse(activity, -1, true, (device, index, name) -> {
            android.bluetooth.BluetoothDevice btDevice = device.getDevice();
            String address = btDevice != null ? btDevice.getAddress() : "";
            printerSettings.setBluetoothPrinter(address, name);
            selectedDevice = device;
            printerSelected = true;
            updateSelectedPrinterButton();
            listener.onPrinterChanged(selectedDevice, true);
        }));
    }

    private void browseUsbPrinter() {
        UsbConnection[] usbPrinters = new UsbPrintersConnections(activity).getList();
        if (usbPrinters == null || usbPrinters.length == 0) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.no_usb_printers_found)
                    .setMessage(R.string.no_usb_printers_message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }
        final String[] items = new String[usbPrinters.length];
        for (int i = 0; i < usbPrinters.length; i++) {
            UsbDevice device = usbPrinters[i].getDevice();
            String name = device != null ? device.getProductName() : null;
            if (name == null || name.isEmpty()) {
                name = device != null
                        ? ("USB " + Integer.toHexString(device.getVendorId()) + ":" + Integer.toHexString(device.getProductId()))
                        : "USB";
            }
            items[i] = name;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.select_usb_printer)
                .setItems(items, (dialog, which) -> {
                    UsbConnection connection = usbPrinters[which];
                    UsbDevice device = connection.getDevice();
                    if (device == null) {
                        return;
                    }
                    usbPrinterPermission.request(device, (grantedDevice, granted) -> activity.runOnUiThread(() -> {
                        if (!granted) {
                            Toast.makeText(activity, R.string.usb_permission_denied, Toast.LENGTH_LONG).show();
                            return;
                        }
                        printerSettings.setUsbPrinter(grantedDevice.getVendorId(), grantedDevice.getProductId(), items[which]);
                        selectedDevice = new UsbConnection(
                                (android.hardware.usb.UsbManager) activity.getSystemService(Activity.USB_SERVICE),
                                grantedDevice
                        );
                        printerSelected = true;
                        updateSelectedPrinterButton();
                        listener.onPrinterChanged(selectedDevice, true);
                    }));
                })
                .show();
    }

    private void setRoundedBackground(Button button, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        float cornerRadiusPx = 8 * activity.getResources().getDisplayMetrics().density;
        drawable.setCornerRadius(cornerRadiusPx);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            button.setBackground(drawable);
        } else {
            button.setBackgroundDrawable(drawable);
        }
    }
}

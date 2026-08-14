package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.content.SharedPreferences;

import com.dantsu.escposprinter.EscPosCharsetEncoding;

/**
 * Persistent printer setup: connection type, paper width, and ESC/POS code page.
 */
public class PrinterSettings {
    public static final String PREFS = "MyPrefs";
    private static final String KEY_TYPE = "printer_connection_type";
    private static final String KEY_BT_ADDRESS = "printer_bt_address";
    private static final String KEY_BT_NAME = "printer_bt_name";
    private static final String KEY_USB_VENDOR = "printer_usb_vendor";
    private static final String KEY_USB_PRODUCT = "printer_usb_product";
    private static final String KEY_USB_NAME = "printer_usb_name";
    private static final String KEY_TCP_HOST = "printer_tcp_host";
    private static final String KEY_TCP_PORT = "printer_tcp_port";
    private static final String KEY_PAPER = "printer_paper_size";
    private static final String KEY_CHARSET = "printer_charset";
    private static final String KEY_INDEX_LEGACY = "stored_index_printer";

    public enum ConnectionType {
        BLUETOOTH,
        USB,
        TCP
    }

    public enum PaperSize {
        MM_58(203, 48f, 32),
        MM_80(203, 72f, 48);

        public final int dpi;
        public final float widthMm;
        public final int charsPerLine;

        PaperSize(int dpi, float widthMm, int charsPerLine) {
            this.dpi = dpi;
            this.widthMm = widthMm;
            this.charsPerLine = charsPerLine;
        }

        public static PaperSize fromKey(String key) {
            if ("80".equals(key)) {
                return MM_80;
            }
            return MM_58;
        }

        public String key() {
            return this == MM_80 ? "80" : "58";
        }
    }

    /**
     * ESC/POS code pages. Cheap Chinese printers often ignore Windows-1252 (16)
     * and stay on CP437/GBK, which makes äöüß look wrong.
     */
    public enum Charset {
        PC850("IBM850", 2),
        CP437("IBM437", 0),
        W1252("windows-1252", 16),
        GBK("GBK", 255);

        public final String javaName;
        public final int escPosId;

        Charset(String javaName, int escPosId) {
            this.javaName = javaName;
            this.escPosId = escPosId;
        }

        public EscPosCharsetEncoding toEncoding() {
            return new EscPosCharsetEncoding(javaName, escPosId);
        }

        public String sanitize(String text) {
            if (text == null || this == W1252) {
                return text;
            }
            return text
                    .replace("€", "EUR")
                    .replace("–", "-")
                    .replace("—", "-")
                    .replace("„", "\"")
                    .replace("“", "\"")
                    .replace("”", "\"")
                    .replace("‚", "'")
                    .replace("‘", "'")
                    .replace("’", "'")
                    .replace("…", "...");
        }

        public static Charset fromKey(String key) {
            if (key == null) {
                return PC850;
            }
            switch (key) {
                case "cp437":
                    return CP437;
                case "w1252":
                    return W1252;
                case "gbk":
                    return GBK;
                default:
                    return PC850;
            }
        }

        public String key() {
            switch (this) {
                case CP437:
                    return "cp437";
                case W1252:
                    return "w1252";
                case GBK:
                    return "gbk";
                default:
                    return "pc850";
            }
        }
    }

    private final SharedPreferences prefs;

    public PrinterSettings(Context context) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public ConnectionType getConnectionType() {
        String type = prefs.getString(KEY_TYPE, ConnectionType.BLUETOOTH.name());
        try {
            return ConnectionType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return ConnectionType.BLUETOOTH;
        }
    }

    public void setConnectionType(ConnectionType type) {
        prefs.edit().putString(KEY_TYPE, type.name()).apply();
    }

    public String getBluetoothAddress() {
        return prefs.getString(KEY_BT_ADDRESS, "");
    }

    public String getBluetoothName() {
        return prefs.getString(KEY_BT_NAME, "");
    }

    public void setBluetoothPrinter(String address, String name) {
        prefs.edit()
                .putString(KEY_TYPE, ConnectionType.BLUETOOTH.name())
                .putString(KEY_BT_ADDRESS, address == null ? "" : address)
                .putString(KEY_BT_NAME, name == null ? "" : name)
                .putInt(KEY_INDEX_LEGACY, address == null || address.isEmpty() ? -1 : 0)
                .apply();
    }

    public int getUsbVendorId() {
        return prefs.getInt(KEY_USB_VENDOR, -1);
    }

    public int getUsbProductId() {
        return prefs.getInt(KEY_USB_PRODUCT, -1);
    }

    public String getUsbName() {
        return prefs.getString(KEY_USB_NAME, "");
    }

    public void setUsbPrinter(int vendorId, int productId, String name) {
        prefs.edit()
                .putString(KEY_TYPE, ConnectionType.USB.name())
                .putInt(KEY_USB_VENDOR, vendorId)
                .putInt(KEY_USB_PRODUCT, productId)
                .putString(KEY_USB_NAME, name == null ? "" : name)
                .putInt(KEY_INDEX_LEGACY, 0)
                .apply();
    }

    public String getTcpHost() {
        return prefs.getString(KEY_TCP_HOST, "");
    }

    public int getTcpPort() {
        return prefs.getInt(KEY_TCP_PORT, 9100);
    }

    public void setTcpPrinter(String host, int port) {
        prefs.edit()
                .putString(KEY_TYPE, ConnectionType.TCP.name())
                .putString(KEY_TCP_HOST, host == null ? "" : host.trim())
                .putInt(KEY_TCP_PORT, port > 0 ? port : 9100)
                .putInt(KEY_INDEX_LEGACY, host == null || host.trim().isEmpty() ? -1 : 0)
                .apply();
    }

    public PaperSize getPaperSize() {
        return PaperSize.fromKey(prefs.getString(KEY_PAPER, "58"));
    }

    public void setPaperSize(PaperSize paperSize) {
        prefs.edit().putString(KEY_PAPER, paperSize.key()).apply();
    }

    public Charset getCharset() {
        return Charset.fromKey(prefs.getString(KEY_CHARSET, "pc850"));
    }

    public void setCharset(Charset charset) {
        prefs.edit().putString(KEY_CHARSET, charset.key()).apply();
    }

    public int getLegacyPrinterIndex() {
        return prefs.getInt(KEY_INDEX_LEGACY, -1);
    }

    public boolean isConfigured() {
        switch (getConnectionType()) {
            case USB:
                return getUsbVendorId() >= 0 && getUsbProductId() >= 0;
            case TCP:
                return !getTcpHost().isEmpty();
            case BLUETOOTH:
            default:
                return !getBluetoothAddress().isEmpty() || getLegacyPrinterIndex() >= 0;
        }
    }

    public String getDisplayName() {
        switch (getConnectionType()) {
            case USB:
                return getUsbName().isEmpty() ? "USB" : getUsbName();
            case TCP:
                if (getTcpHost().isEmpty()) {
                    return "";
                }
                return getTcpHost() + ":" + getTcpPort();
            case BLUETOOTH:
            default:
                if (!getBluetoothName().isEmpty()) {
                    return getBluetoothName();
                }
                return getBluetoothAddress();
        }
    }
}

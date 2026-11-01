package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import java.net.URL;

public final class ConnectivityUtils {

    private ConnectivityUtils() {
    }

    /**
     * Returns true when the device has an active network transport (Wi‑Fi, cellular, etc.).
     * Does not require validated internet access so emulator/local dev still works.
     */
    public static boolean isNetworkAvailable(Context context) {
        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                if (network == null) {
                    return false;
                }
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                if (capabilities == null) {
                    return false;
                }
                return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            }
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Hosts reachable without public internet (emulator loopback, LAN, etc.). */
    public static boolean isLocalDevHost(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return false;
        }
        String normalized = domain.trim().replaceAll("\\s+", "");
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        try {
            String host = new URL(normalized).getHost();
            if (host == null || host.isEmpty()) {
                return false;
            }
            host = host.toLowerCase();
            if ("localhost".equals(host) || "127.0.0.1".equals(host) || "10.0.2.2".equals(host)) {
                return true;
            }
            return host.startsWith("192.168.") || host.startsWith("10.");
        } catch (Exception e) {
            String lower = domain.toLowerCase();
            return lower.contains("localhost")
                    || lower.contains("127.0.0.1")
                    || lower.contains("10.0.2.2");
        }
    }

    public static boolean requiresInternetCheck(String domain) {
        return !isLocalDevHost(domain);
    }

    /**
     * Emulator host aliases for the developer's machine:
     * {@code 10.0.2.2} is the default host loopback; {@code 127.0.0.1} works with
     * {@code adb reverse tcp:PORT tcp:PORT} when the emulator network stack is broken.
     */
    public static String alternateEmulatorHostUrl(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return null;
        }
        String normalized = domain.trim().replaceAll("\\s+", "");
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        try {
            URL url = new URL(normalized);
            String host = url.getHost();
            if (host == null) {
                return null;
            }
            String replacement;
            if ("10.0.2.2".equalsIgnoreCase(host)) {
                replacement = "127.0.0.1";
            } else if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)) {
                replacement = "10.0.2.2";
            } else {
                return null;
            }
            StringBuilder rebuilt = new StringBuilder();
            rebuilt.append(url.getProtocol()).append("://").append(replacement);
            if (url.getPort() != -1) {
                rebuilt.append(':').append(url.getPort());
            }
            String path = url.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) {
                rebuilt.append(path);
            }
            if (url.getQuery() != null) {
                rebuilt.append('?').append(url.getQuery());
            }
            return rebuilt.toString().replaceAll("/+$", "");
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isConnectFailure(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase();
            if (lower.contains("failed to connect")
                    || lower.contains("connection refused")
                    || lower.contains("network is unreachable")
                    || lower.contains("no route to host")
                    || lower.contains("econnrefused")
                    || lower.contains("enetrunreach")) {
                return true;
            }
        }
        return false;
    }
}

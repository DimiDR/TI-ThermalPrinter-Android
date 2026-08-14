package com.dantsu.thermalprinter.helpClasses;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Long-lived JSON stream to ntfy. Only "message" events are treated as order hooks.
 */
public class NtfySubscriber {
    private static final String TAG = "NtfySubscriber";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 120000;
    private static final int MAX_BACKOFF_MS = 30000;

    public interface Listener {
        void onOrderHook();
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    private volatile HttpURLConnection connection;

    public void start(String subscribeUrl, Listener listener) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worker = new Thread(() -> listenLoop(subscribeUrl, listener), "NtfySubscriber");
        worker.start();
    }

    public void stop() {
        running.set(false);
        HttpURLConnection conn = connection;
        if (conn != null) {
            conn.disconnect();
        }
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * @return true when this ntfy line is a real published message (not open/keepalive).
     */
    public static boolean isOrderHookEvent(String jsonLine) {
        if (jsonLine == null) {
            return false;
        }
        String trimmed = jsonLine.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        try {
            JSONObject object = new JSONObject(trimmed);
            return "message".equals(object.optString("event", ""));
        } catch (Exception e) {
            return false;
        }
    }

    private void listenLoop(String subscribeUrl, Listener listener) {
        int backoffMs = 1000;
        boolean reconnect = false;
        while (running.get()) {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                String urlWithSince = subscribeUrl;
                if (reconnect) {
                    urlWithSince += (subscribeUrl.contains("?") ? "&" : "?") + "since=2m";
                }
                URL url = new URL(urlWithSince);
                conn = (HttpURLConnection) url.openConnection();
                connection = conn;
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestProperty("Accept", "application/x-ndjson");
                conn.connect();
                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new java.io.IOException("ntfy subscribe HTTP " + code);
                }
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                backoffMs = 1000;
                reconnect = true;
                Log.d(TAG, "Subscribed to order hook stream");
                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    if (isOrderHookEvent(line) && listener != null) {
                        listener.onOrderHook();
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    Log.w(TAG, "Order hook stream interrupted, reconnecting", e);
                }
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ignored) { }
                }
                if (conn != null) {
                    conn.disconnect();
                }
                connection = null;
            }
            if (running.get()) {
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }
        }
    }
}

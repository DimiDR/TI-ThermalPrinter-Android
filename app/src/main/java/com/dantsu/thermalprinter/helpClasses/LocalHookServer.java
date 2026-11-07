package com.dantsu.thermalprinter.helpClasses;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional LAN hook: TastyIgniter (or a test curl) can POST to http://TABLET:8765/hook
 * when the shop server can reach this device.
 */
public class LocalHookServer {
    private static final String TAG = "LocalHookServer";
    public static final int PORT = 8765;
    private static final int ACCEPT_TIMEOUT_MS = 2000;
    private static final byte[] OK_RESPONSE =
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 11\r\nConnection: close\r\n\r\n{\"ok\":true}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] NOT_FOUND =
            "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8);

    public interface Listener {
        void onOrderHook();
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object socketLock = new Object();
    private ServerSocket serverSocket; // guarded by socketLock
    private long runId; // guarded by socketLock; bumped by start() and stop()
    private Thread acceptThread;
    private ExecutorService handlers;

    public void start(Listener listener) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        handlers = Executors.newCachedThreadPool();
        final long myRun;
        synchronized (socketLock) {
            myRun = ++runId;
        }
        acceptThread = new Thread(() -> acceptLoop(listener, myRun), "LocalHookServer");
        acceptThread.start();
    }

    public void stop() {
        running.set(false);
        synchronized (socketLock) {
            runId++;
            closeQuietly(serverSocket);
            serverSocket = null;
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
        if (handlers != null) {
            handlers.shutdownNow();
            handlers = null;
        }
    }

    static boolean isHookRequest(String requestLine) {
        if (requestLine == null) {
            return false;
        }
        String line = requestLine.trim();
        return line.startsWith("POST /hook") || line.startsWith("GET /hook")
                || line.startsWith("POST / ") || line.startsWith("POST / HTTP");
    }

    private boolean isCurrentRun(long myRun) {
        synchronized (socketLock) {
            return running.get() && runId == myRun;
        }
    }

    private void acceptLoop(Listener listener, long myRun) {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(PORT));
            socket.setSoTimeout(ACCEPT_TIMEOUT_MS);
            synchronized (socketLock) {
                if (!running.get() || runId != myRun) {
                    // stop() ran while we were binding; it could not see this socket.
                    return;
                }
                serverSocket = socket;
            }
            Log.d(TAG, "Listening for local order hooks on port " + PORT);
            while (isCurrentRun(myRun)) {
                try {
                    Socket client = socket.accept();
                    ExecutorService pool = handlers;
                    if (pool != null && !pool.isShutdown()) {
                        pool.execute(() -> handleClient(client, listener));
                    } else {
                        try {
                            client.close();
                        } catch (Exception ignored) { }
                    }
                } catch (SocketTimeoutException ignored) {
                    // used so stop() can unwind
                }
            }
        } catch (Exception e) {
            if (isCurrentRun(myRun)) {
                Log.e(TAG, "Local hook server failed", e);
            }
        } finally {
            // Always release the port, whatever ended the loop.
            closeQuietly(socket);
            synchronized (socketLock) {
                if (serverSocket == socket) {
                    serverSocket = null;
                }
            }
        }
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) { }
        }
    }

    private void handleClient(Socket client, Listener listener) {
        try {
            client.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            String header;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {
                // drain headers
            }
            OutputStream out = client.getOutputStream();
            if (isHookRequest(requestLine)) {
                out.write(OK_RESPONSE);
                out.flush();
                if (listener != null) {
                    listener.onOrderHook();
                }
            } else {
                out.write(NOT_FOUND);
                out.flush();
            }
        } catch (Exception e) {
            Log.w(TAG, "Local hook request failed", e);
        } finally {
            try {
                client.close();
            } catch (Exception ignored) { }
        }
    }
}

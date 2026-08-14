package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dantsu.thermalprinter.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class NetworkHelper {
    private static final String TAG = "NetworkHelper";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AuthStore authStore;
    private final Context context;
    private Future<?> networkTask;
    private String token;

    public interface NetworkCallback {
        void onSuccess(String[] results);
        void onError(Exception exception);
    }

    public NetworkHelper(Context context) {
        this.context = context.getApplicationContext();
        this.authStore = new AuthStore(context);
        this.token = authStore.getToken();
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network connectivity", e);
        }
        return false;
    }

    private String extractHostname(String domain) {
        try {
            URL url = new URL(domain);
            return url.getHost();
        } catch (Exception e) {
            return domain.replaceAll("^https?://", "").replaceAll("/.*$", "");
        }
    }

    private String getFriendlyErrorMessage(Exception e, String domain) {
        String errorMessage = e.getMessage();
        String exceptionType = e.getClass().getSimpleName();

        Log.e(TAG, "Network error: " + exceptionType);
        Log.e(TAG, "Domain host: " + extractHostname(domain));

        boolean networkAvailable = isNetworkAvailable();

        if (errorMessage == null) {
            return context.getString(R.string.network_error_occurred);
        }

        if (errorMessage.contains("Unable to resolve host") ||
            errorMessage.contains("No address associated with hostname") ||
            errorMessage.contains("UnknownHostException") ||
            exceptionType.contains("UnknownHost") ||
            (errorMessage.contains("hostname") && errorMessage.contains("resolve"))) {

            String hostname = extractHostname(domain);
            if (!networkAvailable) {
                return context.getString(R.string.dns_resolution_failed_no_network, hostname, domain) + "\n\n" +
                       context.getString(R.string.unknown_error) + ": " + errorMessage;
            } else {
                return context.getString(R.string.dns_resolution_failed, hostname, domain) + "\n\n" +
                       context.getString(R.string.unknown_error) + ": " + errorMessage;
            }
        }

        if (errorMessage.contains("timeout") ||
            errorMessage.contains("Connection timed out") ||
            errorMessage.contains("SocketTimeoutException") ||
            exceptionType.contains("Timeout")) {
            return context.getString(R.string.connection_timeout);
        }

        if (errorMessage.contains("Connection refused") ||
            errorMessage.contains("ECONNREFUSED") ||
            errorMessage.contains("Connection reset")) {
            return context.getString(R.string.connection_refused);
        }

        if (errorMessage.contains("SSL") ||
            errorMessage.contains("TLS") ||
            errorMessage.contains("certificate") ||
            errorMessage.contains("javax.net.ssl")) {
            return context.getString(R.string.ssl_tls_error);
        }

        if (errorMessage.contains("Network is unreachable") ||
            errorMessage.contains("No route to host")) {
            return context.getString(R.string.network_unreachable);
        }

        return errorMessage;
    }

    private void generateToken(String username, String password, String domain_shop) throws IOException {
        if (!isNetworkAvailable()) {
            throw new IOException(context.getString(R.string.no_internet_error));
        }

        String normalizedDomain = domain_shop.trim().replaceAll("\\s+", "");
        normalizedDomain = normalizedDomain.replaceAll("/+$", "");

        if (normalizedDomain.isEmpty()) {
            throw new IOException(context.getString(R.string.invalid_domain_spaces));
        }

        String tokenUrl = normalizedDomain + "/api/token";
        String hostname = extractHostname(normalizedDomain);

        Log.d(TAG, "Generating token for host: " + hostname);

        try {
            InetAddress.getByName(hostname);
        } catch (Exception dnsException) {
            Log.e(TAG, "DNS resolution failed for " + hostname, dnsException);
        }

        HttpURLConnection urlConnection = null;
        try {
            URL urlObj;
            try {
                urlObj = new URL(tokenUrl);
            } catch (java.net.MalformedURLException e) {
                throw new IOException(context.getString(R.string.invalid_url_format, tokenUrl), e);
            }
            urlConnection = (HttpURLConnection) urlObj.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            urlConnection.setReadTimeout(READ_TIMEOUT_MS);
            urlConnection.setDoOutput(true);

            JSONObject jsonParam = new JSONObject();
            jsonParam.put("username", username);
            jsonParam.put("password", password);
            jsonParam.put("device_name", "my_device");

            OutputStream os = urlConnection.getOutputStream();
            os.write(jsonParam.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int responseCode = urlConnection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                this.token = jsonResponse.getString("token");
                authStore.setToken(this.token);
            } else {
                StringBuilder errorResponse = new StringBuilder();
                try {
                    InputStream errorStream = urlConnection.getErrorStream();
                    if (errorStream != null) {
                        BufferedReader errReader = new BufferedReader(new InputStreamReader(errorStream));
                        String errLine;
                        while ((errLine = errReader.readLine()) != null) {
                            errorResponse.append(errLine);
                        }
                        errReader.close();
                    }
                } catch (Exception ignored) { }

                String message = "Token generation failed. Response code: " + responseCode;
                if (errorResponse.length() > 0) {
                    message += ", body: " + errorResponse;
                }
                throw new IOException(message);
            }
        } catch (IOException e) {
            throw new IOException(getFriendlyErrorMessage(e, normalizedDomain), e);
        } catch (Exception e) {
            throw new IOException(getFriendlyErrorMessage(e, normalizedDomain), e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    public void fetchData(String[] urls, String domain_shop, NetworkCallback callback) {
        fetchData(urls, null, null, domain_shop, callback);
    }

    public void fetchData(String[] urls, String username, String password, String domain_shop, NetworkCallback callback) {
        cancelNetworkTask();

        networkTask = executor.submit(() -> {
            Exception exception = null;
            String[] results = null;
            try {
                if (token == null) {
                    token = authStore.getToken();
                }
                if (token == null) {
                    if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                        generateToken(username, password, domain_shop);
                    } else {
                        throw new IOException(context.getString(R.string.session_expired));
                    }
                }

                results = new String[urls.length];
                for (int i = 0; i < urls.length; i++) {
                    results[i] = getWithAuth(urls[i], username, password, domain_shop);
                }
            } catch (Exception e) {
                exception = (e instanceof IOException) ? e : new IOException(getFriendlyErrorMessage(e, domain_shop), e);
            }

            final Exception postedException = exception;
            final String[] postedResults = results;
            mainHandler.post(() -> {
                if (postedException != null) {
                    callback.onError(postedException);
                } else {
                    callback.onSuccess(postedResults);
                }
            });
        });
    }

    private String getWithAuth(String requestUrl, String username, String password, String domain_shop) throws IOException {
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;
        try {
            urlConnection = openGet(requestUrl);
            if (urlConnection.getResponseCode() == 401) {
                urlConnection.disconnect();
                if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                    generateToken(username, password, domain_shop);
                    urlConnection = openGet(requestUrl);
                } else {
                    authStore.clearToken();
                    token = null;
                    throw new IOException(context.getString(R.string.session_expired));
                }
            }

            InputStream inputStream = urlConnection.getInputStream();
            if (inputStream == null) {
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append("\n");
            }
            return buffer.length() == 0 ? null : buffer.toString();
        } catch (IOException e) {
            throw new IOException(getFriendlyErrorMessage(e, domain_shop != null ? domain_shop : requestUrl), e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) { }
            }
        }
    }

    private HttpURLConnection openGet(String requestUrl) throws IOException {
        URL url = new URL(requestUrl);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        urlConnection.setReadTimeout(READ_TIMEOUT_MS);
        if (token != null) {
            urlConnection.setRequestProperty("Authorization", "Bearer " + token);
        }
        urlConnection.connect();
        return urlConnection;
    }

    public void cancelNetworkTask() {
        if (networkTask != null && !networkTask.isDone()) {
            networkTask.cancel(true);
            networkTask = null;
        }
    }

    public boolean isNetworkTaskRunning() {
        return networkTask != null && !networkTask.isDone();
    }

    public void shutdown() {
        cancelNetworkTask();
        executor.shutdownNow();
    }

    public interface TokenCallback {
        void onTokenGenerated(String token);
        void onTokenError(Exception exception);
    }

    public interface LocationCallback {
        void onLocationsFetched(String locationsJson);
        void onLocationError(Exception exception);
    }

    public void generateTokenAsync(String username, String password, String domain_shop, TokenCallback callback) {
        executor.submit(() -> {
            try {
                generateToken(username, password, domain_shop);
                final String result = token;
                mainHandler.post(() -> callback.onTokenGenerated(result));
            } catch (IOException e) {
                mainHandler.post(() -> callback.onTokenError(e));
            }
        });
    }

    public void validateTokenAsync(String domain_shop, TokenCallback callback) {
        executor.submit(() -> {
            try {
                if (token == null) {
                    token = authStore.getToken();
                }
                if (token == null) {
                    throw new IOException(context.getString(R.string.session_expired));
                }

                String testUrl = domain_shop + "/api/locations";
                HttpURLConnection urlConnection = openGet(testUrl);
                int responseCode = urlConnection.getResponseCode();
                urlConnection.disconnect();
                if (responseCode == 401) {
                    authStore.clearToken();
                    token = null;
                    throw new IOException(context.getString(R.string.session_expired));
                } else if (responseCode != 200) {
                    throw new IOException("Token validation failed. Response code: " + responseCode);
                }
                final String result = token;
                mainHandler.post(() -> callback.onTokenGenerated(result));
            } catch (Exception e) {
                Exception wrapped = (e instanceof IOException) ? e : new IOException(getFriendlyErrorMessage(e, domain_shop), e);
                mainHandler.post(() -> callback.onTokenError(wrapped));
            }
        });
    }

    public void fetchLocationsAsync(String domain_shop, LocationCallback callback) {
        executor.submit(() -> {
            try {
                if (token == null) {
                    token = authStore.getToken();
                }
                if (token == null) {
                    throw new IOException(context.getString(R.string.session_expired));
                }

                String locationsUrl = domain_shop + "/api/locations";
                String body = getWithAuth(locationsUrl, null, null, domain_shop);
                mainHandler.post(() -> callback.onLocationsFetched(body));
            } catch (Exception e) {
                Exception wrapped = (e instanceof IOException) ? e : new IOException(getFriendlyErrorMessage(e, domain_shop), e);
                mainHandler.post(() -> callback.onLocationError(wrapped));
            }
        });
    }

    public void clearToken() {
        this.token = null;
        authStore.clearToken();
    }
}

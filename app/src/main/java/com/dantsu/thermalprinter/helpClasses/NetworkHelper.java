package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
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
    private final Object callbackLock = new Object();
    private Future<?> networkTask;
    private NetworkCallback activeCallback;
    private String token;
    private String lastResolvedDomain;

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
        return ConnectivityUtils.isNetworkAvailable(context);
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
            if (ConnectivityUtils.isLocalDevHost(domain)) {
                return context.getString(R.string.local_dev_connection_failed);
            }
            return context.getString(R.string.network_unreachable);
        }

        if (errorMessage.contains("Failed to connect")) {
            if (ConnectivityUtils.isLocalDevHost(domain)) {
                return context.getString(R.string.local_dev_connection_failed);
            }
            return context.getString(R.string.connection_refused);
        }

        return errorMessage;
    }

    private String formatTokenError(int responseCode, String body) {
        if (body != null && !body.isEmpty()) {
            try {
                JSONObject json = new JSONObject(body);
                StringBuilder details = new StringBuilder();
                if (json.has("errors")) {
                    Object errors = json.get("errors");
                    if (errors instanceof JSONObject) {
                        JSONObject errObj = (JSONObject) errors;
                        java.util.Iterator<String> keys = errObj.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            Object value = errObj.get(key);
                            if (value instanceof org.json.JSONArray) {
                                org.json.JSONArray arr = (org.json.JSONArray) value;
                                for (int i = 0; i < arr.length(); i++) {
                                    if (details.length() > 0) {
                                        details.append(" ");
                                    }
                                    details.append(arr.optString(i));
                                }
                            } else if (value != null) {
                                if (details.length() > 0) {
                                    details.append(" ");
                                }
                                details.append(value);
                            }
                        }
                    }
                }
                if (details.length() == 0 && json.has("message")) {
                    details.append(json.optString("message"));
                }
                if (details.length() > 0) {
                    return details.toString();
                }
            } catch (Exception ignored) { }
        }
        if (responseCode == 401 || responseCode == 403) {
            return context.getString(R.string.authentication_failed);
        }
        if (responseCode == 422) {
            return context.getString(R.string.authentication_failed);
        }
        return context.getString(R.string.authentication_failed) + " (" + responseCode + ")";
    }

    private void generateToken(String username, String password, String domain_shop) throws IOException {
        String normalizedDomain = domain_shop.trim().replaceAll("\\s+", "");
        normalizedDomain = normalizedDomain.replaceAll("/+$", "");

        if (ConnectivityUtils.requiresInternetCheck(normalizedDomain) && !isNetworkAvailable()) {
            throw new IOException(context.getString(R.string.no_internet_error));
        }

        if (normalizedDomain.isEmpty()) {
            throw new IOException(context.getString(R.string.invalid_domain_spaces));
        }

        try {
            generateTokenOnce(username, password, normalizedDomain);
            lastResolvedDomain = normalizedDomain;
        } catch (IOException primaryError) {
            String alternate = ConnectivityUtils.alternateEmulatorHostUrl(normalizedDomain);
            if (alternate == null || !ConnectivityUtils.isConnectFailure(primaryError)) {
                throw primaryError;
            }
            Log.w(TAG, "Local host unreachable at " + normalizedDomain + ", retrying " + alternate);
            try {
                generateTokenOnce(username, password, alternate);
                lastResolvedDomain = alternate;
            } catch (IOException alternateError) {
                if (ConnectivityUtils.isConnectFailure(alternateError)) {
                    throw new IOException(context.getString(R.string.local_dev_connection_failed), alternateError);
                }
                throw alternateError;
            }
        }
    }

    private void generateTokenOnce(String username, String password, String normalizedDomain) throws IOException {
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
            // Staff/printer app needs an admin token (customer lookup fails with admin email)
            jsonParam.put("email", username);
            jsonParam.put("username", username);
            jsonParam.put("password", password);
            jsonParam.put("device_name", "thermal_printer_app");
            jsonParam.put("is_admin", 1);

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

                throw new IOException(formatTokenError(responseCode, errorResponse.toString()));
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
        replaceActiveCallback(callback);

        final NetworkCallback thisCallback = callback;
        networkTask = executor.submit(() -> {
            Exception exception = null;
            String[] results = null;
            try {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Request cancelled");
                }
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
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Request cancelled");
                    }
                    results[i] = getWithAuth(urls[i], username, password, domain_shop);
                }
            } catch (Exception e) {
                exception = (e instanceof IOException) ? e : new IOException(getFriendlyErrorMessage(e, domain_shop), e);
            }

            final Exception postedException = exception;
            final String[] postedResults = results;
            mainHandler.post(() -> {
                if (!clearActiveCallback(thisCallback)) {
                    return;
                }
                if (postedException != null) {
                    thisCallback.onError(postedException);
                } else {
                    thisCallback.onSuccess(postedResults);
                }
            });
        });
    }

    private String getWithAuth(String requestUrl, String username, String password, String domain_shop) throws IOException {
        try {
            return getWithAuthOnce(requestUrl, username, password, domain_shop);
        } catch (IOException primaryError) {
            String alternateUrl = ConnectivityUtils.alternateEmulatorHostUrl(requestUrl);
            String alternateDomain = ConnectivityUtils.alternateEmulatorHostUrl(domain_shop);
            if (alternateUrl == null || !ConnectivityUtils.isConnectFailure(primaryError)) {
                throw primaryError;
            }
            Log.w(TAG, "Retrying request via alternate local host: " + alternateUrl);
            String body = getWithAuthOnce(alternateUrl, username, password,
                    alternateDomain != null ? alternateDomain : domain_shop);
            if (alternateDomain != null) {
                lastResolvedDomain = alternateDomain;
            }
            return body;
        }
    }

    private String getWithAuthOnce(String requestUrl, String username, String password, String domain_shop) throws IOException {
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = openGet(requestUrl);
            int responseCode = urlConnection.getResponseCode();
            if (responseCode == 401) {
                urlConnection.disconnect();
                if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                    generateToken(username, password, domain_shop);
                    urlConnection = openGet(requestUrl);
                    responseCode = urlConnection.getResponseCode();
                } else {
                    authStore.clearToken();
                    token = null;
                    throw new IOException(context.getString(R.string.session_expired));
                }
            }

            if (responseCode >= 400) {
                String errorBody = readStreamQuietly(urlConnection.getErrorStream());
                Log.e(TAG, "HTTP " + responseCode + " for " + requestUrl
                        + (errorBody.isEmpty() ? "" : " body=" + truncate(errorBody, 200)));
                throw new IOException("HTTP " + responseCode + " for " + requestUrl);
            }

            return readStreamQuietly(urlConnection.getInputStream());
        } catch (IOException e) {
            throw new IOException(getFriendlyErrorMessage(e, domain_shop != null ? domain_shop : requestUrl), e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private static String readStreamQuietly(InputStream stream) throws IOException {
        if (stream == null) {
            return null;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        try {
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append("\n");
            }
            return buffer.length() == 0 ? null : buffer.toString();
        } finally {
            try {
                reader.close();
            } catch (IOException ignored) { }
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max) + "…";
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
        replaceActiveCallback(null);
    }

    public boolean isNetworkTaskRunning() {
        return networkTask != null && !networkTask.isDone();
    }

    public void shutdown() {
        cancelNetworkTask();
        executor.shutdownNow();
    }

    /**
     * Swaps the in-flight callback. The previous request is cancelled and receives
     * {@code onError} so callers (e.g. ShopCatalogRepository) never leave a latch stuck.
     */
    private void replaceActiveCallback(NetworkCallback next) {
        NetworkCallback previous;
        synchronized (callbackLock) {
            previous = activeCallback;
            activeCallback = next;
        }
        if (networkTask != null && !networkTask.isDone()) {
            networkTask.cancel(true);
            networkTask = null;
        }
        if (previous != null) {
            mainHandler.post(() -> previous.onError(new IOException("Request cancelled")));
        }
    }

    private boolean clearActiveCallback(NetworkCallback expected) {
        synchronized (callbackLock) {
            if (activeCallback != expected) {
                return false;
            }
            activeCallback = null;
            return true;
        }
    }

    public interface TokenCallback {
        void onTokenGenerated(String token);
        void onTokenError(Exception exception);

        /** Optional: domain that actually worked (e.g. after 10.0.2.2 → 127.0.0.1 fallback). */
        default void onTokenGenerated(String token, String resolvedDomain) {
            onTokenGenerated(token);
        }
    }

    public interface LocationCallback {
        void onLocationsFetched(String locationsJson);
        void onLocationError(Exception exception);
    }

    public interface PublicCallback {
        void onSuccess(String body);
        void onError(Exception exception);
    }

    /** Unauthenticated GET. Used for the public tenant directory. */
    public void fetchFirstPublicAsync(java.util.List<String> urls, PublicCallback callback) {
        executor.submit(() -> {
            Exception last = null;
            if (urls != null) {
                for (String requestUrl : urls) {
                    if (requestUrl == null || requestUrl.trim().isEmpty()) {
                        continue;
                    }
                    try {
                        String body = getPublic(requestUrl);
                        if (body != null && body.contains("\"shops\"")) {
                            final String ok = body;
                            mainHandler.post(() -> callback.onSuccess(ok));
                            return;
                        }
                    } catch (Exception e) {
                        last = e;
                        Log.d(TAG, "Public fetch failed for " + requestUrl + ": " + e.getMessage());
                    }
                }
            }
            Exception fail = last != null ? last : new IOException("No shop directory");
            mainHandler.post(() -> callback.onError(fail));
        });
    }

    private String getPublic(String requestUrl) throws IOException {
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(requestUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            urlConnection.setReadTimeout(READ_TIMEOUT_MS);
            int responseCode = urlConnection.getResponseCode();
            if (responseCode >= 400) {
                throw new IOException("HTTP " + responseCode + " for " + requestUrl);
            }
            String body = readStreamQuietly(urlConnection.getInputStream());
            if (body == null) {
                throw new IOException("Empty response for " + requestUrl);
            }
            return body;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    public void generateTokenAsync(String username, String password, String domain_shop, TokenCallback callback) {
        executor.submit(() -> {
            try {
                lastResolvedDomain = domain_shop;
                generateToken(username, password, domain_shop);
                final String result = token;
                final String resolved = lastResolvedDomain != null ? lastResolvedDomain : domain_shop;
                mainHandler.post(() -> callback.onTokenGenerated(result, resolved));
            } catch (IOException e) {
                Log.e(TAG, "Token generation failed: " + e.getMessage(), e);
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

                String workingDomain = domain_shop;
                String testUrl = domain_shop + "/api/locations";
                try {
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
                } catch (IOException primaryError) {
                    String alternateDomain = ConnectivityUtils.alternateEmulatorHostUrl(domain_shop);
                    if (alternateDomain == null || !ConnectivityUtils.isConnectFailure(primaryError)) {
                        throw primaryError;
                    }
                    workingDomain = alternateDomain;
                    HttpURLConnection urlConnection = openGet(alternateDomain + "/api/locations");
                    int responseCode = urlConnection.getResponseCode();
                    urlConnection.disconnect();
                    if (responseCode == 401) {
                        authStore.clearToken();
                        token = null;
                        throw new IOException(context.getString(R.string.session_expired));
                    } else if (responseCode != 200) {
                        throw new IOException("Token validation failed. Response code: " + responseCode);
                    }
                    lastResolvedDomain = alternateDomain;
                }
                final String result = token;
                final String resolved = workingDomain;
                mainHandler.post(() -> callback.onTokenGenerated(result, resolved));
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

    public interface StaffProfileCallback {
        void onStaffProfileLoaded(StaffProfileResolver.StaffProfile profile);
        void onStaffProfileError(Exception exception);
    }

    public void fetchStaffProfileAsync(String domain_shop, String loginIdentifier, StaffProfileCallback callback) {
        executor.submit(() -> {
            try {
                if (token == null) {
                    token = authStore.getToken();
                }
                if (token == null) {
                    throw new IOException(context.getString(R.string.session_expired));
                }

                String usersUrl = domain_shop.replaceAll("/+$", "") + "/api/users?include=role&pageLimit=100";
                String body = getWithAuth(usersUrl, null, null, domain_shop);
                StaffProfileResolver.StaffProfile profile =
                        StaffProfileResolver.resolve(body, loginIdentifier);
                mainHandler.post(() -> callback.onStaffProfileLoaded(profile));
            } catch (Exception e) {
                Exception wrapped = (e instanceof IOException) ? e : new IOException(getFriendlyErrorMessage(e, domain_shop), e);
                mainHandler.post(() -> callback.onStaffProfileError(wrapped));
            }
        });
    }

    public void clearToken() {
        this.token = null;
        authStore.clearToken();
    }
}

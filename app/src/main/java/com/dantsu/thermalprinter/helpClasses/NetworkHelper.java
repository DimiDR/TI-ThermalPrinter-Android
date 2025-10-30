package com.dantsu.thermalprinter.helpClasses;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
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

public class NetworkHelper {
    private AsyncTask<String, Void, String[]> networkTask;
    private String token;
    private Context context;

    public interface NetworkCallback {
        void onSuccess(String[] results);
        void onError(Exception exception);
    }

    public NetworkHelper(Context context) {
        this.context = context;
        SharedPreferences prefs = context.getSharedPreferences("api_token", Context.MODE_PRIVATE);
        this.token = prefs.getString("token", null);
    }

    /**
     * Check if network connectivity is available
     */
    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.e("NetworkHelper", "Error checking network connectivity", e);
        }
        return false;
    }
    
    /**
     * Extract hostname from domain URL
     */
    private String extractHostname(String domain) {
        try {
            URL url = new URL(domain);
            return url.getHost();
        } catch (Exception e) {
            // If URL parsing fails, try to extract manually
            String host = domain.replaceAll("^https?://", "").replaceAll("/.*$", "");
            return host;
        }
    }
    
    /**
     * Helper method to detect and provide user-friendly messages for network errors
     */
    private String getFriendlyErrorMessage(Exception e, String domain) {
        String errorMessage = e.getMessage();
        String exceptionType = e.getClass().getSimpleName();
        
        // Log the exception for debugging
        Log.e("NetworkHelper", "Network error: " + exceptionType + " - " + errorMessage);
        Log.e("NetworkHelper", "Domain: " + domain);
        
        // Check network connectivity
        boolean networkAvailable = isNetworkAvailable();
        Log.d("NetworkHelper", "Network available: " + networkAvailable);
        
        if (errorMessage == null) {
            return context.getString(R.string.network_error_occurred);
        }
        
        // Check for DNS resolution failures (multiple patterns)
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
        
        // Check for connection timeout
        if (errorMessage.contains("timeout") || 
            errorMessage.contains("Connection timed out") ||
            errorMessage.contains("SocketTimeoutException") ||
            exceptionType.contains("Timeout")) {
            return context.getString(R.string.connection_timeout);
        }
        
        // Check for connection refused
        if (errorMessage.contains("Connection refused") || 
            errorMessage.contains("ECONNREFUSED") ||
            errorMessage.contains("Connection reset")) {
            return context.getString(R.string.connection_refused);
        }
        
        // Check for SSL/TLS errors
        if (errorMessage.contains("SSL") || 
            errorMessage.contains("TLS") || 
            errorMessage.contains("certificate") ||
            errorMessage.contains("javax.net.ssl")) {
            return context.getString(R.string.ssl_tls_error);
        }
        
        // Check for network unreachable
        if (errorMessage.contains("Network is unreachable") ||
            errorMessage.contains("No route to host")) {
            return context.getString(R.string.network_unreachable);
        }
        
        // Return original message if it's already user-friendly or for other errors
        return errorMessage;
    }

    private void generateToken(String username, String password, String domain_shop) throws IOException {
        // Check network connectivity first
        if (!isNetworkAvailable()) {
            throw new IOException(context.getString(R.string.no_internet_error));
        }
        
        // Normalize domain: remove all whitespace, trailing slashes, and ensure proper format
        String normalizedDomain = domain_shop.trim().replaceAll("\\s+", ""); // remove all whitespace
        normalizedDomain = normalizedDomain.replaceAll("/+$", ""); // remove trailing slashes
        
        // Validate the domain format
        if (normalizedDomain.isEmpty()) {
            throw new IOException(context.getString(R.string.invalid_domain_spaces));
        }
        
        String tokenUrl = normalizedDomain + "/api/token";
        String hostname = extractHostname(normalizedDomain);
        
        Log.d("NetworkHelper", "Generating token for URL: " + tokenUrl);
        Log.d("NetworkHelper", "Domain: " + normalizedDomain);
        Log.d("NetworkHelper", "Hostname: " + hostname);
        
        // Try DNS resolution test
        try {
            Log.d("NetworkHelper", "Attempting DNS resolution for: " + hostname);
            InetAddress.getByName(hostname);
            Log.d("NetworkHelper", "DNS resolution successful");
        } catch (Exception dnsException) {
            Log.e("NetworkHelper", "DNS resolution failed for " + hostname, dnsException);
            // Continue anyway - sometimes DNS check fails but connection works
        }
        
        try {
            // Validate URL format before attempting connection
            URL urlObj;
            try {
                urlObj = new URL(tokenUrl);
            } catch (java.net.MalformedURLException e) {
                Log.e("NetworkHelper", "Malformed URL: " + tokenUrl, e);
                throw new IOException(context.getString(R.string.invalid_url_format, tokenUrl), e);
            }
            HttpURLConnection urlConnection = (HttpURLConnection) urlObj.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setConnectTimeout(15000);
            urlConnection.setReadTimeout(15000);
            urlConnection.setDoOutput(true);

            JSONObject jsonParam = new JSONObject();
            jsonParam.put("username", username);
            jsonParam.put("password", password);
            jsonParam.put("device_name", "my_device");

            OutputStream os = urlConnection.getOutputStream();
            os.write(jsonParam.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int responseCode = urlConnection.getResponseCode();

            // Consider both 200 OK and 201 Created as successful token responses
            if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                this.token = jsonResponse.getString("token");

                SharedPreferences.Editor editor = context.getSharedPreferences("api_token", Context.MODE_PRIVATE).edit();
                editor.putString("token", this.token);
                editor.apply();
            } else {
                // Try to surface server-provided error details when available
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
            // Wrap DNS/network errors with user-friendly messages
            String friendlyMessage = getFriendlyErrorMessage(e, normalizedDomain);
            throw new IOException(friendlyMessage, e);
        } catch (Exception e) {
            // Fallback for unexpected exceptions
            String friendlyMessage = getFriendlyErrorMessage(e, normalizedDomain);
            throw new IOException(friendlyMessage, e);
        }
    }

    @SuppressLint("StaticFieldLeak")
    public void fetchData(String[] urls, String username, String password, String domain_shop, NetworkCallback callback) {
        cancelNetworkTask(); // Cancel any existing network task

        networkTask = new AsyncTask<String, Void, String[]>() {
            private Exception exception = null;

            @Override
            protected String[] doInBackground(String... params) {
                try {
                    if (token == null) {
                        generateToken(username, password, domain_shop);
                    }
                } catch (IOException e) {
                    exception = e;
                    return null;
                }

                String[] results = new String[urls.length];
                for (int i = 0; i < urls.length; i++) {
                    HttpURLConnection urlConnection = null;
                    BufferedReader reader = null;

                    try {
                        URL url = new URL(urls[i]);
                        urlConnection = (HttpURLConnection) url.openConnection();
                        urlConnection.setRequestMethod("GET");
                        if (token != null) {
                            urlConnection.setRequestProperty("Authorization", "Bearer " + token);
                        }
                        urlConnection.connect();

                        // Check for 401 Unauthorized and try to regenerate token
                        if (urlConnection.getResponseCode() == 401) {
                            try {
                                generateToken(username, password, domain_shop);
                                // Re-run the request with the new token
                                urlConnection.disconnect();
                                urlConnection = (HttpURLConnection) url.openConnection();
                                urlConnection.setRequestMethod("GET");
                                urlConnection.setRequestProperty("Authorization", "Bearer " + token);
                                urlConnection.connect();
                            } catch (IOException e) {
                                exception = e;
                                return null;
                            }
                        }

                        InputStream inputStream = urlConnection.getInputStream();
                        StringBuilder buffer = new StringBuilder();
                        if (inputStream == null) {
                            return null;
                        }
                        reader = new BufferedReader(new InputStreamReader(inputStream));

                        String line;
                        while ((line = reader.readLine()) != null) {
                            buffer.append(line).append("\n");
                        }

                        if (buffer.length() == 0) {
                            return null;
                        }
                        results[i] = buffer.toString();
                    } catch (IOException e) {
                        e.printStackTrace();
                        // Wrap DNS/network errors with user-friendly messages
                        String friendlyMessage = getFriendlyErrorMessage(e, domain_shop != null ? domain_shop : urls[i]);
                        exception = new IOException(friendlyMessage, e);
                        return null;
                    } finally {
                        if (urlConnection != null) {
                            urlConnection.disconnect();
                        }
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (final IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                return results;
            }

            @Override
            protected void onPostExecute(String[] results) {
                if (exception != null) {
                    callback.onError(exception);
                } else {
                    callback.onSuccess(results);
                }
            }
        }.execute(urls);
    }

    public void cancelNetworkTask() {
        if (networkTask != null && !networkTask.isCancelled()) {
            networkTask.cancel(true);
            networkTask = null;
        }
    }

    public boolean isNetworkTaskRunning() {
        return networkTask != null && !networkTask.isCancelled();
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
        new AsyncTask<Void, Void, String>() {
            private Exception exception = null;

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    generateToken(username, password, domain_shop);
                    return token;
                } catch (IOException e) {
                    exception = e;
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (exception != null) {
                    callback.onTokenError(exception);
                } else {
                    callback.onTokenGenerated(result);
                }
            }
        }.execute();
    }

    public void validateTokenAsync(String domain_shop, TokenCallback callback) {
        new AsyncTask<Void, Void, String>() {
            private Exception exception = null;

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    if (token == null) {
                        exception = new IOException("No token available");
                        return null;
                    }

                    // Test token with a simple API call
                    String testUrl = domain_shop + "/api/locations";
                    URL url = new URL(testUrl);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.setRequestProperty("Authorization", "Bearer " + token);
                    urlConnection.connect();

                    int responseCode = urlConnection.getResponseCode();
                    if (responseCode == 401) {
                        exception = new IOException("Token expired or invalid");
                        return null;
                    } else if (responseCode != 200) {
                        exception = new IOException("Token validation failed. Response code: " + responseCode);
                        return null;
                    }

                    return token;
                } catch (Exception e) {
                    // Wrap DNS/network errors with user-friendly messages
                    String friendlyMessage = getFriendlyErrorMessage(e, domain_shop);
                    exception = new IOException(friendlyMessage, e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (exception != null) {
                    callback.onTokenError(exception);
                } else {
                    callback.onTokenGenerated(result);
                }
            }
        }.execute();
    }

    public void fetchLocationsAsync(String domain_shop, LocationCallback callback) {
        new AsyncTask<Void, Void, String>() {
            private Exception exception = null;

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    if (token == null) {
                        exception = new IOException("No token available");
                        return null;
                    }

                    String locationsUrl = domain_shop + "/api/locations";
                    URL url = new URL(locationsUrl);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.setRequestProperty("Authorization", "Bearer " + token);
                    urlConnection.connect();

                    int responseCode = urlConnection.getResponseCode();
                    if (responseCode == 401) {
                        exception = new IOException("Token expired or invalid");
                        return null;
                    } else if (responseCode != 200) {
                        exception = new IOException("Failed to fetch locations. Response code: " + responseCode);
                        return null;
                    }

                    BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    return response.toString();
                } catch (Exception e) {
                    // Wrap DNS/network errors with user-friendly messages
                    String friendlyMessage = getFriendlyErrorMessage(e, domain_shop);
                    exception = new IOException(friendlyMessage, e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (exception != null) {
                    callback.onLocationError(exception);
                } else {
                    callback.onLocationsFetched(result);
                }
            }
        }.execute();
    }

    public void clearToken() {
        this.token = null;
        SharedPreferences.Editor editor = context.getSharedPreferences("api_token", Context.MODE_PRIVATE).edit();
        editor.remove("token");
        editor.apply();
    }
}

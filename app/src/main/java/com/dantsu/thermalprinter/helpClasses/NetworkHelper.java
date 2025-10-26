package com.dantsu.thermalprinter.helpClasses;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
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

    private void generateToken(String username, String password, String domain_shop) throws IOException {
        try {
            String tokenUrl = domain_shop + "/api/token";
            URL urlObj = new URL(tokenUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) urlObj.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setDoOutput(true);

            JSONObject jsonParam = new JSONObject();
            jsonParam.put("username", username);
            jsonParam.put("password", password);
            jsonParam.put("device_name", "my_device");

            OutputStream os = urlConnection.getOutputStream();
            os.write(jsonParam.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int responseCode = urlConnection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_CREATED) {
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
                throw new IOException("Token generation failed. Response code: " + responseCode);
            }
        } catch (Exception e) {
            throw new IOException("Error generating token", e);
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
                        exception = e;
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
                    exception = e;
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

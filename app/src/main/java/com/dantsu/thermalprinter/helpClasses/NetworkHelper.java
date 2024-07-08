package com.dantsu.thermalprinter.helpClasses;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetworkHelper {
    private AsyncTask<String, Void, String[]> networkTask;

    public interface NetworkCallback {
        void onSuccess(String[] results);
        void onError(Exception exception);
    }

    @SuppressLint("StaticFieldLeak")
    public void fetchData(String[] urls, NetworkCallback callback) {
        cancelNetworkTask(); // Cancel any existing network task

        networkTask = new AsyncTask<String, Void, String[]>() {
            private Exception exception = null;

            @Override
            protected String[] doInBackground(String... urls) {
                String[] results = new String[urls.length];
                for (int i = 0; i < urls.length; i++) {
                    HttpURLConnection urlConnection = null;
                    BufferedReader reader = null;

                    try {
                        URL url = new URL(urls[i]);
                        urlConnection = (HttpURLConnection) url.openConnection();
                        urlConnection.setRequestMethod("GET");
                        urlConnection.connect();

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
}

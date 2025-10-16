package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WebServiceTask {
    private Context context;
    private String token;

    public WebServiceTask(Context context) {
        this.context = context;
    }

    public void generateToken(String url, String username, String password) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                URL urlObj = new URL(url);
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
                    Log.e("WebServiceTask", "Token generation failed. Response code: " + responseCode);
                }
            } catch (Exception e) {
                Log.e("WebServiceTask", "Error generating token", e);
            }
        });
        executor.shutdown();
    }

    public void execute(String url) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                HttpURLConnection urlConnection = null;
                BufferedReader reader = null;
                String resultJson = null;

                try {
                    URL urlObj = new URL(url);
                    urlConnection = (HttpURLConnection) urlObj.openConnection();
                    urlConnection.setRequestMethod("GET");
                    if (token != null) {
                        urlConnection.setRequestProperty("Authorization", "Bearer " + token);
                    }
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
                    resultJson = buffer.toString();
                } catch (IOException e) {
                    Log.e("MainActivity", "Error ", e);
                    return null;
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                    }
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (final IOException e) {
                            Log.e("MainActivity", "Error closing stream", e);
                        }
                    }
                }
                return resultJson;
            }
        });

        try {
            String result = future.get();
            if (result != null) {
                Log.d("WebServiceResponse", result);
                SharedPreferences.Editor editor = context.getSharedPreferences("currentJSONResponse", Context.MODE_PRIVATE).edit();
                editor.putString("jsonResult", result);
                editor.apply();
            } else {
                Log.e("WebServiceResponse", "Failed to fetch data from web service");
            }
        } catch (Exception e) {
            Log.e("WebServiceTask", "Error executing web service task", e);
        } finally {
            executor.shutdown();
        }
    }
}
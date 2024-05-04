package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

//needs to be switched later to this implementation. Execution in MainActivity:
//Global:WebServiceTask webServiceTask;
// onInit: webServiceTask = new WebServiceTask(this);

//private void startService() {
//        timer = new Timer();
//        // keep screen on
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        timer.scheduleAtFixedRate(new TimerTask() {
//@Override
//public void run() {
//        webServiceTask.execute(tiOrdersEndpointURL);
//        SharedPreferences sharedPreferences = getSharedPreferences("currentJSONResponse", MODE_PRIVATE);
//        String jsonResult = sharedPreferences.getString("jsonResult", "");
//        printDocketCustomerReceipt(jsonResult);
//        }
//        }, 0, period);
//
//        isServiceActive = true;
//        }

// AsyncTask is deprecated and this implementation should be used.
// However the printer implementation is also using async task executing
//public void TIJobPrintBluetooth(String print_info, String orderId) {
//        this.checkBluetoothPermissions(() -> {
//        new AsyncBluetoothEscPosPrint(

//This creates an error if I mixe up new and also async implementation
//java.lang.RuntimeException: Can't create handler inside thread Thread[Timer-0,5,main] that has not called Looper.prepare()

// for now I will use the async implementation in MainActivity

public class WebServiceTask {
    private Context context;

    public WebServiceTask(Context context) {
        this.context = context;
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
            String result = future.get(); // This line will block until the result is available
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
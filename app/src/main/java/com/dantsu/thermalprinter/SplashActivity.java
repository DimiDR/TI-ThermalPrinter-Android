package com.dantsu.thermalprinter;


import static com.dantsu.thermalprinter.Constants.APP_DETAILS_URL;
import static com.dantsu.thermalprinter.Constants.currentAppVersion;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DISPLAY_LENGTH = 1000; // 1 seconds

    private boolean isUpdate = false;
    private String apkFile = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        //check for updates only if internet connection is available
        if (isNetworkAvailable()){
            checkForUpdates();
        } else {
            goToNextActivity();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void checkForUpdates() {
        new FetchAppDetailsTask().execute(APP_DETAILS_URL);
    }

    private class FetchAppDetailsTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                return result.toString();
            } catch (Exception e) {
                e.printStackTrace();
                goToNextActivity();
                return null;

            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                try {
                    JSONObject jsonObject = new JSONObject(result);
                    String appVersion = jsonObject.getString("app_version");
                    String fileToDownload = jsonObject.getString("apk_file");

                    if (isNewVersionAvailable(currentAppVersion, appVersion)) {
                        isUpdate = true;
                        apkFile = fileToDownload;
                    }

                    goToNextActivity();

                } catch (JSONException e) {
                    e.printStackTrace();
                    goToNextActivity();
                }
            }
        }
    }

    public boolean isNewVersionAvailable(String currentAppVersion, String appVersion) {
        String[] currentVersionParts = currentAppVersion.split("\\.");
        String[] newVersionParts = appVersion.split("\\.");

        int length = Math.max(currentVersionParts.length, newVersionParts.length);

        for (int i = 0; i < length; i++) {
            int currentPart = i < currentVersionParts.length ? Integer.parseInt(currentVersionParts[i]) : 0;
            int newPart = i < newVersionParts.length ? Integer.parseInt(newVersionParts[i]) : 0;

            if (currentPart < newPart) {
                return true;
            } else if (currentPart > newPart) {
                return false;
            }
        }
        return false;
    }


    public void goToNextActivity(){

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent mainIntent = new Intent(SplashActivity.this, MainActivity.class);
                mainIntent.putExtra("isUpdate", isUpdate);
                mainIntent.putExtra("apkFile", apkFile);
                mainIntent.putExtra("currentAppVersion", currentAppVersion);
                startActivity(mainIntent);
                finish();
            }
        }, SPLASH_DISPLAY_LENGTH);
    }
}

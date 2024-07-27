package com.dantsu.thermalprinter;


import static com.dantsu.thermalprinter.Constants.APP_DETAILS_URL;
import static com.dantsu.thermalprinter.Constants.currentAppVersion;

import android.content.Intent;
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
        checkForUpdates();

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

                    if (!currentAppVersion.equals(appVersion)) {
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


    public void goToNextActivity(){

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent mainIntent = new Intent(SplashActivity.this, MainActivity.class);
                mainIntent.putExtra("isUpdate", isUpdate);
                mainIntent.putExtra("apkFile", apkFile);
                startActivity(mainIntent);
                finish();
            }
        }, SPLASH_DISPLAY_LENGTH);
    }
}

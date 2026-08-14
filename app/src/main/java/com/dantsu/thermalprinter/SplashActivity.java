package com.dantsu.thermalprinter;


import static com.dantsu.thermalprinter.Constants.APP_DETAILS_URL;
import static com.dantsu.thermalprinter.Constants.currentAppVersion;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.dantsu.thermalprinter.helpClasses.AuthStore;
import com.dantsu.thermalprinter.helpClasses.PrintMonitorService;
import com.dantsu.thermalprinter.helpClasses.UpdateVerifier;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DISPLAY_LENGTH = 1000;

    private boolean isUpdate = false;
    private String apkFile = "";
    private String apkSha256 = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new AuthStore(this);
        PrintMonitorService.restoreIfEnabled(this);
        if (isNetworkAvailable()) {
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
        Executors.newSingleThreadExecutor().execute(() -> {
            String result = fetch(APP_DETAILS_URL);
            UpdateVerifier.UpdateInfo info = result == null ? null : UpdateVerifier.parseUpdateJson(result, currentAppVersion);
            runOnUiThread(() -> {
                if (info != null) {
                    isUpdate = true;
                    apkFile = info.apkUrl;
                    apkSha256 = info.sha256;
                }
                goToNextActivity();
            });
        });
    }

    private String fetch(String urlString) {
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(15000);
            urlConnection.setReadTimeout(15000);
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    public void goToNextActivity() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent dashboardIntent = new Intent(SplashActivity.this, DashboardActivity.class);
            if (isUpdate) {
                Intent mainIntent = new Intent(SplashActivity.this, MainActivity.class);
                mainIntent.putExtra("isUpdate", true);
                mainIntent.putExtra("apkFile", apkFile);
                mainIntent.putExtra("apkSha256", apkSha256);
                mainIntent.putExtra("currentAppVersion", currentAppVersion);
                startActivity(mainIntent);
            } else {
                startActivity(dashboardIntent);
            }
            finish();
        }, SPLASH_DISPLAY_LENGTH);
    }
}

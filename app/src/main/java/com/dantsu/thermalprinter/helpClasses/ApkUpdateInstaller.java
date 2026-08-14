package com.dantsu.thermalprinter.helpClasses;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.dantsu.thermalprinter.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public final class ApkUpdateInstaller {
    private static final String TAG = "ApkUpdateInstaller";
    private static final String PROVIDER = ".fileprovider";

    private ApkUpdateInstaller() {}

    public static void downloadVerifyAndInstall(Activity activity, String apkUrl, String expectedSha256) {
        if (!UpdateVerifier.isAllowedApkUrl(apkUrl) || !UpdateVerifier.isValidSha256(expectedSha256)) {
            Toast.makeText(activity, R.string.update_url_rejected, Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            settings.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            Toast.makeText(activity, R.string.update_allow_unknown_sources, Toast.LENGTH_LONG).show();
            return;
        }

        ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setTitle(R.string.update_available);
        dialog.setMessage(activity.getString(R.string.update_downloading));
        dialog.setCancelable(false);
        dialog.show();

        Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            File apkFile = null;
            try {
                apkFile = downloadToCache(activity, apkUrl);
                String actual = UpdateVerifier.sha256File(apkFile);
                if (!UpdateVerifier.hashesMatch(expectedSha256, actual)) {
                    if (apkFile.exists()) {
                        apkFile.delete();
                    }
                    main.post(() -> {
                        dialog.dismiss();
                        Toast.makeText(activity, R.string.update_hash_mismatch, Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                File verified = apkFile;
                main.post(() -> {
                    dialog.dismiss();
                    launchInstaller(activity, verified);
                });
            } catch (Exception e) {
                Log.e(TAG, "Update download failed", e);
                if (apkFile != null && apkFile.exists()) {
                    apkFile.delete();
                }
                main.post(() -> {
                    dialog.dismiss();
                    Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static File downloadToCache(Context context, String apkUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(apkUrl).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        File outFile = new File(context.getCacheDir(), "update.apk");
        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
        return outFile;
    }

    private static void launchInstaller(Activity activity, File apkFile) {
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + PROVIDER, apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }
}

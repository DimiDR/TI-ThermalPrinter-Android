package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

/**
 * Stores the API bearer token. Never persists the shop password.
 * Migrates leftover plaintext tokens and scrubs passwords from legacy prefs.
 */
public final class AuthStore {
    private static final String TAG = "AuthStore";
    private static final String SECURE_PREFS = "secure_auth";
    private static final String FALLBACK_PREFS = "secure_auth_fallback";
    private static final String LEGACY_LOGIN = "loginPrefs";
    private static final String LEGACY_TOKEN = "api_token";
    private static final String KEY_TOKEN = "token";

    private final Context appContext;
    private final SharedPreferences securePrefs;

    public AuthStore(Context context) {
        this.appContext = context.getApplicationContext();
        this.securePrefs = createSecurePrefs();
        migrateAndScrub();
    }

    private SharedPreferences createSecurePrefs() {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    SECURE_PREFS,
                    masterKeyAlias,
                    appContext,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "EncryptedSharedPreferences unavailable, using private fallback", e);
            return appContext.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE);
        }
    }

    public synchronized String getToken() {
        String token = securePrefs.getString(KEY_TOKEN, null);
        return token == null || token.isEmpty() ? null : token;
    }

    public synchronized void setToken(String token) {
        securePrefs.edit().putString(KEY_TOKEN, token).apply();
        scrubLegacySecrets();
    }

    public synchronized void clearToken() {
        securePrefs.edit().remove(KEY_TOKEN).apply();
        scrubLegacySecrets();
    }

    public synchronized boolean hasToken() {
        return getToken() != null;
    }

    private void migrateAndScrub() {
        SharedPreferences loginPrefs = appContext.getSharedPreferences(LEGACY_LOGIN, Context.MODE_PRIVATE);
        SharedPreferences apiTokenPrefs = appContext.getSharedPreferences(LEGACY_TOKEN, Context.MODE_PRIVATE);

        if (getToken() == null) {
            String legacy = loginPrefs.getString(KEY_TOKEN, null);
            if (legacy == null || legacy.isEmpty()) {
                legacy = apiTokenPrefs.getString(KEY_TOKEN, null);
            }
            if (legacy != null && !legacy.isEmpty()) {
                securePrefs.edit().putString(KEY_TOKEN, legacy).apply();
            }
        }
        scrubLegacySecrets();
    }

    private void scrubLegacySecrets() {
        appContext.getSharedPreferences(LEGACY_LOGIN, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TOKEN)
                .remove("password")
                .apply();
        appContext.getSharedPreferences(LEGACY_TOKEN, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }
}

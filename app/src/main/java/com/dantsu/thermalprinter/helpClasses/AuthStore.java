package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

/**
 * Stores the API bearer token. Never persists the shop password.
 * If the Android Keystore is unavailable, the token is not stored and the user must log in again.
 */
public final class AuthStore {
    private static final String TAG = "AuthStore";
    private static final String SECURE_PREFS = "secure_auth";
    private static final String FALLBACK_PREFS = "secure_auth_fallback";
    private static final String LEGACY_LOGIN = "loginPrefs";
    private static final String LEGACY_TOKEN = "api_token";
    private static final String KEY_TOKEN = "token";

    /** Shared across instances: creating EncryptedSharedPreferences costs a Keystore round-trip. */
    private static final Object INIT_LOCK = new Object();
    private static boolean initialized;
    private static SharedPreferences sharedSecurePrefs;

    private final Context appContext;
    private final SharedPreferences securePrefs;

    public AuthStore(Context context) {
        this.appContext = context.getApplicationContext();
        synchronized (INIT_LOCK) {
            if (!initialized) {
                sharedSecurePrefs = createSecurePrefs();
                appContext.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
                initialized = true;
                this.securePrefs = sharedSecurePrefs;
                // One-time migration of tokens from the old plain-text prefs.
                migrateAndScrub();
            } else {
                this.securePrefs = sharedSecurePrefs;
            }
        }
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
            Log.e(TAG, "EncryptedSharedPreferences unavailable; token will not be persisted", e);
            return null;
        }
    }

    public boolean isAvailable() {
        return securePrefs != null;
    }

    public String getToken() {
        if (securePrefs == null) {
            return null;
        }
        String token = securePrefs.getString(KEY_TOKEN, null);
        return token == null || token.isEmpty() ? null : token;
    }

    public void setToken(String token) {
        if (securePrefs == null) {
            Log.e(TAG, "Refusing to store token without encryption");
            return;
        }
        securePrefs.edit().putString(KEY_TOKEN, token).apply();
        scrubLegacySecrets();
    }

    public void clearToken() {
        if (securePrefs != null) {
            securePrefs.edit().remove(KEY_TOKEN).apply();
        }
        scrubLegacySecrets();
    }

    public boolean hasToken() {
        return getToken() != null;
    }

    private void migrateAndScrub() {
        SharedPreferences loginPrefs = appContext.getSharedPreferences(LEGACY_LOGIN, Context.MODE_PRIVATE);
        SharedPreferences apiTokenPrefs = appContext.getSharedPreferences(LEGACY_TOKEN, Context.MODE_PRIVATE);

        if (securePrefs != null && getToken() == null) {
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

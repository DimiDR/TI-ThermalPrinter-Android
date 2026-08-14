package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class IdManager {
    private static final String PREFS_NAME = "MyAppPrefs";
    private static final String KEY_IDS = "StoredIds";

    /**
     * Defensive copy so callers cannot mutate the live SharedPreferences StringSet.
     */
    static Set<String> snapshot(Set<String> stored) {
        return stored == null ? new HashSet<String>() : new HashSet<String>(stored);
    }

    public static void saveIds(Context context, Set<String> ids) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putStringSet(KEY_IDS, snapshot(ids));
        editor.apply();
    }

    public static Set<String> getIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return snapshot(prefs.getStringSet(KEY_IDS, null));
    }

    public static void addId(Context context, String id) {
        Set<String> ids = getIds(context);
        ids.add(id);
        saveIds(context, ids);
    }

    public static void removeId(Context context, String id) {
        Set<String> ids = getIds(context);
        ids.remove(id);
        saveIds(context, ids);
    }

    public static void clearIds(Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.remove(KEY_IDS);
        editor.apply();
    }
}

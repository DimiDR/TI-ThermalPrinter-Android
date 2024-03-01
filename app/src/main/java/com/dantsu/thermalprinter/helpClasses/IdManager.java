package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class IdManager {
    private static final String PREFS_NAME = "MyAppPrefs";
    private static final String KEY_IDS = "StoredIds";

    public static void saveIds(Context context, Set<String> ids) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putStringSet(KEY_IDS, ids);
        editor.apply();
    }

    public static Set<String> getIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_IDS, new HashSet<>());
    }

    public static void addId(Context context, String id) {
        Set<String> ids = getIds(context); // Get the stored IDs
        ids.remove(id); // Remove the ID if it already exists
        ids.add(id); // Add the updated ID set
        saveIds(context, ids); // Save the updated ID set
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

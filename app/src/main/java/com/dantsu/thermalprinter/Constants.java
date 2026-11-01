package com.dantsu.thermalprinter;

public class Constants {
    /** Synced from PrintMonitorService; prefer PrintMonitorService.isEnabled(context). */
    public static boolean isServiceActive = false;
    public static final String APP_DETAILS_URL = "https://app-version.jandiweb.de/printer-app/app-details.json";
    public static final String DEFAULT_PLATFORM_URL = "https://foodninja.app";
    public static final String SHOP_DIRECTORY_PATH = "/api/tenancy/shops";
    public static final String[] UPDATE_ALLOWED_HOSTS = {
            "app-version.jandiweb.de",
            "jandiweb.de"
    };
    public static String currentAppVersion = "1.1.0";

    /** Broadcast when a shop webhook arrives so open screens can refresh. */
    public static final String ACTION_ORDERS_CHANGED = "com.dantsu.thermalprinter.ACTION_ORDERS_CHANGED";
}

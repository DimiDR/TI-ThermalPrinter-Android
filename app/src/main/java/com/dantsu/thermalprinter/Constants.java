package com.dantsu.thermalprinter;

public class Constants {
    /** Synced from PrintMonitorService; prefer PrintMonitorService.isEnabled(context). */
    public static boolean isServiceActive = false;
    public static final String APP_DETAILS_URL = "https://app-version.jandiweb.de/printer-app/app-details.json";
    public static final String[] UPDATE_ALLOWED_HOSTS = {
            "app-version.jandiweb.de",
            "jandiweb.de"
    };
    public static String currentAppVersion = "1.1.0";

    public static final long REFRESH_INTERVAL_KITCHEN_OPEN = 60000;
}

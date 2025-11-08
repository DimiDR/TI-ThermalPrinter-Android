package com.dantsu.thermalprinter;

public class Constants {
    public static boolean isServiceActive = false;
    public static String APP_DETAILS_URL = "https://app-version.jandiweb.de/printer-app/app-details.json";
    public static String currentAppVersion = "1.1.0";
    
    // Refresh interval for order monitoring when app is in foreground
    public static final long REFRESH_INTERVAL_KITCHEN_OPEN = 60000; // 1 minute when app is in foreground
}
package com.dantsu.thermalprinter;

public class Constants {
    public static boolean isServiceActive = false;
    public static String APP_DETAILS_URL = "https://app-version.jandiweb.de/printer-app/app-details.json";
    public static String currentAppVersion = "1.0.9";
    
    // Refresh intervals for order monitoring
    public static final long REFRESH_INTERVAL_KITCHEN_OPEN = 60000; // 1 minute when KitchenDisplayActivity is open
    public static final long REFRESH_INTERVAL_KITCHEN_CLOSED = 300000; // 5 minutes when KitchenDisplayActivity is closed
}
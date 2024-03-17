package com.dantsu.thermalprinter.helpClasses;
import android.content.Context;
import android.os.PowerManager;

public class MyWakeLockManager {
    private static PowerManager.WakeLock partialWakeLock;
    private static PowerManager.WakeLock screenWakeLock;
    private static PowerManager.WakeLock fullWakeLock;

//    Partial wake lock: Allows the device's CPU to remain on while the screen and other system
//    components can still go to sleep. This type of wake lock is less restrictive
//    and consumes less power compared to other types.
    public static void acquirePartialWakeLock(Context context) {
        if (partialWakeLock == null) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            partialWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp:PartialWakeLock");
        }
        if (!partialWakeLock.isHeld()) {
            partialWakeLock.acquire();
        }
    }

    public static void releasePartialWakeLock() {
        if (partialWakeLock != null && partialWakeLock.isHeld()) {
            partialWakeLock.release();
            partialWakeLock = null;
        }
    }

//    Screen wake lock: Keeps the screen turned on while the device is idle. This type of wake
//    lock prevents the screen from going into sleep mode but does not necessarily keep the CPU
//    or other components awake.
    public static void acquireScreenWakeLock(Context context) {
        if (screenWakeLock == null) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            screenWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "MyApp:ScreenWakeLock");
        }
        if (!screenWakeLock.isHeld()) {
            screenWakeLock.acquire();
        }
    }

    public static void releaseScreenWakeLock() {
        if (screenWakeLock != null && screenWakeLock.isHeld()) {
            screenWakeLock.release();
            screenWakeLock = null;
        }
    }

    public static void acquireFullWakeLock(Context context) {
        if (fullWakeLock == null) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            fullWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "MyApp:FullWakeLock");
        }
        if (!fullWakeLock.isHeld()) {
            fullWakeLock.acquire();
        }
    }

//    Full wake lock: Keeps the entire device awake, including the CPU, screen, and other components.
//    This type of wake lock consumes the most power and should be used judiciously to avoid draining
//    the device's battery quickly.
    public static void releaseFullWakeLock() {
        if (fullWakeLock != null && fullWakeLock.isHeld()) {
            fullWakeLock.release();
            fullWakeLock = null;
        }
    }
}

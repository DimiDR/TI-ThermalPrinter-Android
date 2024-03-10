package com.dantsu.thermalprinter.helpClasses;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.dantsu.thermalprinter.R;
import android.app.PendingIntent;
import androidx.core.app.NotificationCompat;

public class ForegroundService extends Service {

    private static final int NOTIFICATION_ID = 1001;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (Actions.START.toString().equals(action)) {
            start();
        } else if (Actions.STOP.toString().equals(action)) {
            stopSelf();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void start(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // min level 26

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "running_channel")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Run is active")
                    .setContentText("Hello Text");

            Intent notificationIntent = new Intent(this, ForegroundService.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            builder.setContentIntent(pendingIntent);

            startForeground(NOTIFICATION_ID, builder.build());
        }
    }

    public enum Actions {
        START, STOP
    }
}

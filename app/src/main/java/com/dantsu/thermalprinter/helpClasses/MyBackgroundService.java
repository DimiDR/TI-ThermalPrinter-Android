package com.dantsu.thermalprinter.helpClasses;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.dantsu.thermalprinter.MainActivity;

import java.util.Timer;
import java.util.TimerTask;

public class MyBackgroundService extends Service {
    private final IBinder binder = new LocalBinder();
    private Timer timer;
    private int period = 5000; // example period, set as needed
    private NetworkHelper networkHelper;

    public class LocalBinder extends Binder {
        public MyBackgroundService getService() {
            return MyBackgroundService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If needed, start the timer task here or wait for the activity to call it
        return START_STICKY;
    }


    public void startWebServiceTask(Activity activity, String ordersUrl, String menusUrl, String categoriesUrl) {
        timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                networkHelper = new NetworkHelper(MyBackgroundService.this);
                // String[] urls = {ordersUrl, menusUrl, categoriesUrl}; // Diese Zeile entfernen oder auskommentieren

                Log.e("timerrrrrr", "running: ");

                // KORRIGIERTER AUFRUF: Übergeben Sie die URLs als separate Argumente
                networkHelper.fetchData(new String[]{ordersUrl, menusUrl, categoriesUrl}, ordersUrl, menusUrl, categoriesUrl, (NetworkHelper.NetworkCallback) activity);
            }
        }, 0, period);
    }


    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }
    @Override
    public boolean onUnbind(Intent intent) {
        if (timer != null) {
            networkHelper.cancelNetworkTask();
            timer.cancel();
        }
        Log.d("ServiceOnUnBind", "ServiceOnUnBind");
        //return super.onUnbind(intent);
        return true;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            networkHelper.cancelNetworkTask();
            timer.cancel();
            Toast.makeText(this, "Background service destroyed", Toast.LENGTH_SHORT).show();
        }
    }
}

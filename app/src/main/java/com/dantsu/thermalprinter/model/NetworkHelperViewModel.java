package com.dantsu.thermalprinter.model;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.thermalprinter.helpClasses.BluetoothPrinterHelper;
import com.dantsu.thermalprinter.helpClasses.NetworkHelper;

import java.util.Timer;

public class NetworkHelperViewModel extends AndroidViewModel {
    private NetworkHelper networkHelper;
    private Timer timer;
    private BluetoothConnection selectedDevice;
    private boolean isUpdatePopupShown = false;

    public NetworkHelperViewModel(@NonNull Application application) {
        super(application);
        networkHelper = new NetworkHelper(application.getApplicationContext());
    }

    public NetworkHelper getNetworkHelper() {
        return networkHelper;
    }

    public Timer getTimer() {
        if (timer == null) {
            timer = new Timer();
        }
        return timer;
    }

    public void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    public BluetoothConnection getSelectedDevice() {
        return selectedDevice;
    }

    public void setSelectedDevice(BluetoothConnection selectedDevice) {
        this.selectedDevice = selectedDevice;
    }

    public boolean isUpdatePopupShown() {
        return isUpdatePopupShown;
    }

    public void setUpdatePopupShown(boolean shown) {
        isUpdatePopupShown = shown;
    }
    public void browseBluetoothDevice(Context context, BluetoothPrinterHelper.OnBluetoothPermissionsGranted onBluetoothPermissionsGranted) {
        if (context instanceof Activity) {
            BluetoothPrinterHelper.checkPermissions((Activity) context, onBluetoothPermissionsGranted);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelTimer();
        if (networkHelper != null) {
            networkHelper.shutdown();
        }
    }
}

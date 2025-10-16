package com.dantsu.thermalprinter.model;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;

import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.thermalprinter.MainActivity;
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
    public void browseBluetoothDevice(Context context, MainActivity.OnBluetoothPermissionsGranted onBluetoothPermissionsGranted) {
        checkBluetoothPermissions(context, onBluetoothPermissionsGranted);
    }

    private void checkBluetoothPermissions(Context context, MainActivity.OnBluetoothPermissionsGranted onBluetoothPermissionsGranted) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{android.Manifest.permission.BLUETOOTH}, MainActivity.PERMISSION_BLUETOOTH);
        } else if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{android.Manifest.permission.BLUETOOTH_ADMIN}, MainActivity.PERMISSION_BLUETOOTH_ADMIN);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, MainActivity.PERMISSION_BLUETOOTH_CONNECT);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{android.Manifest.permission.BLUETOOTH_SCAN}, MainActivity.PERMISSION_BLUETOOTH_SCAN);
        } else {
            onBluetoothPermissionsGranted.onPermissionsGranted();
        }
    }
}

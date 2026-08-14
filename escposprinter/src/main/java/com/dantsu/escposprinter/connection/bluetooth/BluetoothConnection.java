package com.dantsu.escposprinter.connection.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.exceptions.EscPosConnectionException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;

public class BluetoothConnection extends DeviceConnection {

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothDevice device;
    private BluetoothSocket socket = null;

    /**
     * Create un instance of BluetoothConnection.
     *
     * @param device an instance of BluetoothDevice
     */
    public BluetoothConnection(BluetoothDevice device) {
        super();
        this.device = device;
    }

    /**
     * Get the instance BluetoothDevice connected.
     *
     * @return an instance of BluetoothDevice
     */
    public BluetoothDevice getDevice() {
        return this.device;
    }

    /**
     * Check if OutputStream is open.
     *
     * @return true if is connected
     */
    @Override
    public boolean isConnected() {
        return this.socket != null && this.socket.isConnected() && super.isConnected();
    }

    /**
     * Start socket connection with the bluetooth device.
     * Cheap ESC/POS modules often reject a secure SPP socket, so this tries
     * insecure SPP, then secure SPP, then RFCOMM channel 1.
     */
    @SuppressLint("MissingPermission")
    public BluetoothConnection connect() throws EscPosConnectionException {
        if (this.isConnected()) {
            return this;
        }

        if (this.device == null) {
            throw new EscPosConnectionException("Bluetooth device is not connected.");
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }

        UUID uuid = BluetoothConnection.SPP_UUID;

        if (this.tryConnect(() -> this.device.createInsecureRfcommSocketToServiceRecord(uuid))) {
            return this;
        }
        if (this.tryConnect(() -> this.device.createRfcommSocketToServiceRecord(uuid))) {
            return this;
        }
        if (this.tryConnect(this::createChannelOneSocket)) {
            return this;
        }

        this.disconnect();
        throw new EscPosConnectionException("Unable to connect to bluetooth device.");
    }

    private boolean tryConnect(SocketFactory factory) {
        this.closeSocketQuietly();
        try {
            this.socket = factory.create();
            this.socket.connect();
            this.outputStream = this.socket.getOutputStream();
            this.data = new byte[0];
            return true;
        } catch (Exception e) {
            this.closeSocketQuietly();
            return false;
        }
    }

    private BluetoothSocket createChannelOneSocket() throws Exception {
        Method method = this.device.getClass().getMethod("createRfcommSocket", int.class);
        return (BluetoothSocket) method.invoke(this.device, 1);
    }

    private void closeSocketQuietly() {
        if (this.outputStream != null) {
            try {
                this.outputStream.close();
            } catch (IOException ignored) {
            }
            this.outputStream = null;
        }
        if (this.socket != null) {
            try {
                this.socket.close();
            } catch (IOException ignored) {
            }
            this.socket = null;
        }
        this.data = new byte[0];
    }

    /**
     * Close the socket connection with the bluetooth device.
     */
    public BluetoothConnection disconnect() {
        this.closeSocketQuietly();
        return this;
    }

    private interface SocketFactory {
        BluetoothSocket create() throws Exception;
    }
}

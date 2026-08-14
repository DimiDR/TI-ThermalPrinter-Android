package com.dantsu.thermalprinter.async;

import android.content.Context;

import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.exceptions.EscPosConnectionException;

public class AsyncTcpEscPosPrint extends AsyncEscPosPrint {
    public AsyncTcpEscPosPrint(Context context) {
        super(context);
    }

    public AsyncTcpEscPosPrint(Context context, OnPrintFinished onPrintFinished) {
        super(context, onPrintFinished);
    }

    protected PrinterStatus doInBackground(AsyncEscPosPrinter... printersData) {
        if (printersData.length == 0) {
            return new PrinterStatus(null, AsyncEscPosPrint.FINISH_NO_PRINTER);
        }
        DeviceConnection deviceConnection = printersData[0].getPrinterConnection();
        this.publishProgress(AsyncEscPosPrint.PROGRESS_CONNECTING);
        if (deviceConnection == null) {
            return new PrinterStatus(null, AsyncEscPosPrint.FINISH_NO_PRINTER);
        }
        try {
            deviceConnection.connect();
        } catch (EscPosConnectionException e) {
            e.printStackTrace();
        }
        return super.doInBackground(printersData);
    }
}

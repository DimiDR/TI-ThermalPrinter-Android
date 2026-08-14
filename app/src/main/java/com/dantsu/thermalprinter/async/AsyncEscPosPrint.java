package com.dantsu.thermalprinter.async;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.dantsu.thermalprinter.R;
import com.dantsu.escposprinter.EscPosCharsetEncoding;
import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.exceptions.EscPosBarcodeException;
import com.dantsu.escposprinter.exceptions.EscPosConnectionException;
import com.dantsu.escposprinter.exceptions.EscPosEncodingException;
import com.dantsu.escposprinter.exceptions.EscPosParserException;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AsyncEscPosPrint {
    public final static int FINISH_SUCCESS = 1;
    public final static int FINISH_NO_PRINTER = 2;
    public final static int FINISH_PRINTER_DISCONNECTED = 3;
    public final static int FINISH_PARSER_ERROR = 4;
    public final static int FINISH_ENCODING_ERROR = 5;
    public final static int FINISH_BARCODE_ERROR = 6;

    protected final static int PROGRESS_CONNECTING = 1;
    protected final static int PROGRESS_CONNECTED = 2;
    protected final static int PROGRESS_PRINTING = 3;
    protected final static int PROGRESS_PRINTED = 4;

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    protected ProgressDialog dialog;
    protected WeakReference<Context> weakContext;
    protected OnPrintFinished onPrintFinished;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AsyncEscPosPrint(Context context) {
        this(context, null);
    }

    public AsyncEscPosPrint(Context context, OnPrintFinished onPrintFinished) {
        this.weakContext = new WeakReference<>(context);
        this.onPrintFinished = onPrintFinished;
    }

    public void execute(AsyncEscPosPrinter... printersData) {
        mainHandler.post(() -> {
            onPreExecute();
            EXECUTOR.execute(() -> {
                PrinterStatus result = doInBackground(printersData);
                mainHandler.post(() -> onPostExecute(result));
            });
        });
    }

    protected PrinterStatus doInBackground(AsyncEscPosPrinter... printersData) {
        if (printersData.length == 0) {
            return new PrinterStatus(null, AsyncEscPosPrint.FINISH_NO_PRINTER);
        }

        this.publishProgress(AsyncEscPosPrint.PROGRESS_CONNECTING);

        AsyncEscPosPrinter printerData = printersData[0];

        try {
            DeviceConnection deviceConnection = printerData.getPrinterConnection();

            if (deviceConnection == null) {
                return new PrinterStatus(null, AsyncEscPosPrint.FINISH_NO_PRINTER);
            }

            EscPosCharsetEncoding charsetEncoding = printerData.getCharsetEncoding();
            if (charsetEncoding == null) {
                charsetEncoding = new EscPosCharsetEncoding("IBM850", 2);
            }
            EscPosPrinter printer = new EscPosPrinter(
                    deviceConnection,
                    printerData.getPrinterDpi(),
                    printerData.getPrinterWidthMM(),
                    printerData.getPrinterNbrCharactersPerLine(),
                    charsetEncoding
            );

            this.publishProgress(AsyncEscPosPrint.PROGRESS_PRINTING);

            String[] textsToPrint = printerData.getTextsToPrint();

            for (String textToPrint : textsToPrint) {
                printer.printFormattedTextAndCut(textToPrint);
                Thread.sleep(500);
            }

            this.publishProgress(AsyncEscPosPrint.PROGRESS_PRINTED);

        } catch (EscPosConnectionException e) {
            e.printStackTrace();
            return new PrinterStatus(printerData, AsyncEscPosPrint.FINISH_PRINTER_DISCONNECTED);
        } catch (EscPosParserException e) {
            e.printStackTrace();
            return new PrinterStatus(printerData, AsyncEscPosPrint.FINISH_PARSER_ERROR);
        } catch (EscPosEncodingException e) {
            e.printStackTrace();
            return new PrinterStatus(printerData, AsyncEscPosPrint.FINISH_ENCODING_ERROR);
        } catch (EscPosBarcodeException e) {
            e.printStackTrace();
            return new PrinterStatus(printerData, AsyncEscPosPrint.FINISH_BARCODE_ERROR);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
        return new PrinterStatus(printerData, AsyncEscPosPrint.FINISH_SUCCESS);
    }

    protected void publishProgress(Integer... progress) {
        mainHandler.post(() -> onProgressUpdate(progress));
    }

    protected void onPreExecute() {
        if (this.dialog == null) {
            Context context = weakContext.get();

            if (!(context instanceof Activity) || ((Activity) context).isFinishing()) {
                return;
            }

            this.dialog = new ProgressDialog(context);
            this.dialog.setTitle(R.string.printing_in_progress);
            this.dialog.setMessage("...");
            this.dialog.setProgressNumberFormat("%1d / %2d");
            this.dialog.setCancelable(false);
            this.dialog.setIndeterminate(false);
            this.dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            this.dialog.show();
        }
    }

    protected void onProgressUpdate(Integer... progress) {
        Context context = weakContext.get();
        if (context == null || this.dialog == null) {
            return;
        }

        switch (progress[0]) {
            case AsyncEscPosPrint.PROGRESS_CONNECTING:
                this.dialog.setMessage(context.getString(R.string.connecting_printer));
                break;
            case AsyncEscPosPrint.PROGRESS_CONNECTED:
                this.dialog.setMessage(context.getString(R.string.printer_connected));
                break;
            case AsyncEscPosPrint.PROGRESS_PRINTING:
                this.dialog.setMessage(context.getString(R.string.printer_printing));
                break;
            case AsyncEscPosPrint.PROGRESS_PRINTED:
                this.dialog.setMessage(context.getString(R.string.printer_finished));
                break;
        }
        this.dialog.setProgress(progress[0]);
        this.dialog.setMax(4);
    }

    protected void onPostExecute(PrinterStatus result) {
        if (this.dialog != null) {
            this.dialog.dismiss();
            this.dialog = null;
        }

        Context context = weakContext.get();
        boolean canShowDialog = context instanceof Activity && !((Activity) context).isFinishing();

        if (context == null) {
            return;
        }

        switch (result.getPrinterStatus()) {
            case AsyncEscPosPrint.FINISH_SUCCESS:
                break;
            case AsyncEscPosPrint.FINISH_NO_PRINTER:
                if (canShowDialog) {
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.no_printer_title)
                            .setMessage(R.string.no_printer_message)
                            .show();
                }
                break;
            case AsyncEscPosPrint.FINISH_PRINTER_DISCONNECTED:
                if (canShowDialog) {
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.broken_connection_title)
                            .setMessage(R.string.broken_connection_message)
                            .show();
                }
                break;
            case AsyncEscPosPrint.FINISH_PARSER_ERROR:
                if (canShowDialog) {
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.invalid_format_title)
                            .setMessage(R.string.invalid_format_message)
                            .show();
                }
                break;
            case AsyncEscPosPrint.FINISH_ENCODING_ERROR:
                if (canShowDialog) {
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.encoding_error_title)
                            .setMessage(R.string.encoding_error_message)
                            .show();
                }
                break;
            case AsyncEscPosPrint.FINISH_BARCODE_ERROR:
                if (canShowDialog) {
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.barcode_error_title)
                            .setMessage(R.string.barcode_error_message)
                            .show();
                }
                break;
        }
        if (this.onPrintFinished != null) {
            if (result.getPrinterStatus() == AsyncEscPosPrint.FINISH_SUCCESS) {
                this.onPrintFinished.onSuccess(result.getAsyncEscPosPrinter());
            } else {
                this.onPrintFinished.onError(result.getAsyncEscPosPrinter(), result.getPrinterStatus());
            }
        }
    }

    public static class PrinterStatus {
        private AsyncEscPosPrinter asyncEscPosPrinter;
        private int printerStatus;

        public PrinterStatus(AsyncEscPosPrinter asyncEscPosPrinter, int printerStatus) {
            this.asyncEscPosPrinter = asyncEscPosPrinter;
            this.printerStatus = printerStatus;
        }

        public AsyncEscPosPrinter getAsyncEscPosPrinter() {
            return asyncEscPosPrinter;
        }

        public int getPrinterStatus() {
            return printerStatus;
        }
    }

    public static abstract class OnPrintFinished {
        public abstract void onError(AsyncEscPosPrinter asyncEscPosPrinter, int codeException);
        public abstract void onSuccess(AsyncEscPosPrinter asyncEscPosPrinter);
    }
}

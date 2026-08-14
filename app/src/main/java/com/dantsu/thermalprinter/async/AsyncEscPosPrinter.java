package com.dantsu.thermalprinter.async;

import com.dantsu.escposprinter.EscPosCharsetEncoding;
import com.dantsu.escposprinter.EscPosPrinterSize;
import com.dantsu.escposprinter.connection.DeviceConnection;

public class AsyncEscPosPrinter extends EscPosPrinterSize {
    private DeviceConnection printerConnection;
    private String[] textsToPrint = new String[0];
    private EscPosCharsetEncoding charsetEncoding;

    public AsyncEscPosPrinter(DeviceConnection printerConnection, int printerDpi, float printerWidthMM, int printerNbrCharactersPerLine) {
        this(printerConnection, printerDpi, printerWidthMM, printerNbrCharactersPerLine, new EscPosCharsetEncoding("IBM850", 2));
    }

    public AsyncEscPosPrinter(DeviceConnection printerConnection, int printerDpi, float printerWidthMM, int printerNbrCharactersPerLine, EscPosCharsetEncoding charsetEncoding) {
        super(printerDpi, printerWidthMM, printerNbrCharactersPerLine);
        this.printerConnection = printerConnection;
        this.charsetEncoding = charsetEncoding;
    }

    public DeviceConnection getPrinterConnection() {
        return this.printerConnection;
    }

    public EscPosCharsetEncoding getCharsetEncoding() {
        return this.charsetEncoding;
    }

    public AsyncEscPosPrinter setTextsToPrint(String[] textsToPrint) {
        this.textsToPrint = textsToPrint;
        return this;
    }

    public AsyncEscPosPrinter addTextToPrint(String textToPrint) {
        String[] tmp = new String[this.textsToPrint.length + 1];
        System.arraycopy(this.textsToPrint, 0, tmp, 0, this.textsToPrint.length);
        tmp[this.textsToPrint.length] = textToPrint;
        this.textsToPrint = tmp;
        return this;
    }

    public String[] getTextsToPrint() {
        return this.textsToPrint;
    }
}

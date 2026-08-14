package com.dantsu.thermalprinter.helpClasses;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.dantsu.thermalprinter.BuildConfig;
import com.dantsu.thermalprinter.R;

public class WebViewDialogFragment extends DialogFragment {

    private WebView webView;
    private static final String ARG_URL = "url";

    private Integer printerCircleColor;
    private Integer serviceCircleColor;
    private View circlePrinter;
    private View circleService;

    public static WebViewDialogFragment newInstance(String url) {
        WebViewDialogFragment fragment = new WebViewDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_webview_dialog, container, false);

        circlePrinter = view.findViewById(R.id.circlePrinter);
        circleService = view.findViewById(R.id.circleService);

        if (printerCircleColor != null) {
            setPrinterCircleColor(printerCircleColor);
        }
        if (serviceCircleColor != null) {
            setServiceCircleColor(serviceCircleColor);
        }

        webView = view.findViewById(R.id.webView);
        configureWebView(webView);

        String url = getArguments() != null ? getArguments().getString(ARG_URL) : null;
        if (url != null) {
            webView.loadUrl(url);
        }

        Button btnBack = view.findViewById(R.id.btn_back);
        Button btnZoomIn = view.findViewById(R.id.btn_zoom_in);
        Button btnZoomOut = view.findViewById(R.id.btn_zoom_out);
        Button btnClose = view.findViewById(R.id.btn_close);

        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });

        final int[] zoomLevel = {130};
        webView.setInitialScale(zoomLevel[0]);

        btnZoomIn.setOnClickListener(v -> {
            zoomLevel[0] += 10;
            webView.setInitialScale(zoomLevel[0]);
        });

        btnZoomOut.setOnClickListener(v -> {
            zoomLevel[0] -= 10;
            webView.setInitialScale(zoomLevel[0]);
        });

        btnClose.setOnClickListener(v -> dismiss());

        return view;
    }

    private void configureWebView(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Log.e("WebViewDialogFragment", "SSL error, rejecting: " + error);
                handler.cancel();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        }
    }

    public void setPrinterCircleColor(int color) {
        printerCircleColor = color;
        if (circlePrinter != null) {
            circlePrinter.setBackgroundColor(color);
        }
    }

    public void setServiceCircleColor(int color) {
        serviceCircleColor = color;
        if (circleService != null) {
            circleService.setBackgroundColor(color);
        }
    }
}

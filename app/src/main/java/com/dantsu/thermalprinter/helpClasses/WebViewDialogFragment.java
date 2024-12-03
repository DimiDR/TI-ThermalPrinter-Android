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

import com.dantsu.thermalprinter.R;

public class WebViewDialogFragment extends DialogFragment {

    private WebView webView;
    private Button btnBack, btnZoomIn, btnZoomOut;
    private View circlePrinter, circleService;
    private static final String ARG_URL = "url";

    // To store colors until the view is created
    private Integer printerCircleColor;
    private Integer serviceCircleColor;

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
        if (getDialog() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_webview_dialog, container, false);

        // Initialize circles
        circlePrinter = view.findViewById(R.id.circlePrinter);
        circleService = view.findViewById(R.id.circleService);

        // Apply stored colors when view is created
        if (printerCircleColor != null) {
            setPrinterCircleColor(printerCircleColor);
        }
        if (serviceCircleColor != null) {
            setServiceCircleColor(serviceCircleColor);
        }

        // Get the WebView and configure it
        webView = view.findViewById(R.id.webView);

        configureWebView(webView);

        // Load the URL passed as an argument
        String url = getArguments().getString(ARG_URL);
        if (url != null) {
            webView.loadUrl(url);
        }

        // Initialize buttons
        btnBack = view.findViewById(R.id.btn_back);
        btnZoomIn = view.findViewById(R.id.btn_zoom_in);
        btnZoomOut = view.findViewById(R.id.btn_zoom_out);

        // Handle back button click
        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });

        final int[] zoomLevel = {130};
        webView.setInitialScale(zoomLevel[0]);

        // Handle zoom in button click
        btnZoomIn.setOnClickListener(v -> {
            zoomLevel[0] += 10;
            WebSettings settings = webView.getSettings();
            webView.setInitialScale(zoomLevel[0]);
//            settings.setTextZoom(zoomLevel[0]); // Increase zoom by 10%
//            settings.setTextZoom(settings.getTextZoom() + 10); // Increase zoom by 10%
        });

        // Handle zoom out button click
        btnZoomOut.setOnClickListener(v -> {
            zoomLevel[0] -= 10;
            WebSettings settings = webView.getSettings();
            webView.setInitialScale(zoomLevel[0]);
//            settings.setTextZoom(zoomLevel[0]); // Decrease zoom by 10%
//            settings.setTextZoom(settings.getTextZoom() - 10); // Decrease zoom by 10%
        });

        // Handle close button
        Button btnClose = view.findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> dismiss());

        return view;
    }

    // Method to configure WebView settings
    private void configureWebView(WebView webView) {
        // Enable JavaScript
        webView.getSettings().setJavaScriptEnabled(true);

        // Enable built-in zoom controls (but hide default controls)
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        // Enable cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Set WebViewClient to handle redirects and SSL errors
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // Ensure WebView handles URLs internally
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed(); // Ignore SSL certificate errors (for testing purposes)
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d("WebViewDialogFragment", "Page loaded: " + url);
            }
        });

        // Enable WebView debugging for development purposes (API level 19+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    // Method to change circle colors from MainActivity
    public void setPrinterCircleColor(int color) {
        printerCircleColor = color; // Store color if the view is not created yet
        if (circlePrinter != null) {
            circlePrinter.setBackgroundColor(color); // Apply the color if the view is ready
        }
    }

    public void setServiceCircleColor(int color) {
        serviceCircleColor = color;
        if (circleService != null) {
            circleService.setBackgroundColor(color); // Apply the color if the view is ready
        }
    }
}

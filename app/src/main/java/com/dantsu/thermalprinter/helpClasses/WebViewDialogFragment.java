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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.dantsu.thermalprinter.R;

public class WebViewDialogFragment extends DialogFragment {

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

        // Get the WebView and configure it for login and navigation
        WebView webView = view.findViewById(R.id.webView);
        configureWebView(webView);

        // Load the URL passed as an argument
        String url = getArguments().getString(ARG_URL);
        if (url != null) {
            webView.loadUrl(url);
        }

        // Handle close button
        Button btnClose = view.findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> dismiss());

        // Set colors if they were set before the view was created
        if (printerCircleColor != null) {
            setPrinterCircleColor(printerCircleColor);
        }
        if (serviceCircleColor != null) {
            setServiceCircleColor(serviceCircleColor);
        }

        return view;
    }

    // Method to configure WebView settings for login functionality
    private void configureWebView(WebView webView) {
        // Enable JavaScript
        webView.getSettings().setJavaScriptEnabled(true);

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
//    public void setPrinterCircleColor(int color) {
//        if (circlePrinter != null) {
//            circlePrinter.setBackgroundColor(color);
//        } else {
//            printerCircleColor = color; // Store color if circlePrinter is not initialized
//        }
//    }
//
//    public void setServiceCircleColor(int color) {
//        if (circleService != null) {
//            circleService.setBackgroundColor(color);
//        } else {
//            serviceCircleColor = color; // Store color if circleService is not initialized
//        }
//    }
    public void setPrinterCircleColor(int color) {
        printerCircleColor = color; // Always store the color
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

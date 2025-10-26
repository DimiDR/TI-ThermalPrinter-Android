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
    private static final String ARG_USERNAME = "username";
    private static final String ARG_PASSWORD = "password";
    private static final String ARG_DOMAIN = "domain";

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

    public static WebViewDialogFragment newInstance(String url, String username, String password, String domain) {
        WebViewDialogFragment fragment = new WebViewDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putString(ARG_USERNAME, username);
        args.putString(ARG_PASSWORD, password);
        args.putString(ARG_DOMAIN, domain);
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
        String username = getArguments().getString(ARG_USERNAME);
        String password = getArguments().getString(ARG_PASSWORD);
        String domain = getArguments().getString(ARG_DOMAIN);
        
        if (url != null) {
            if (username != null && password != null && domain != null) {
                Log.d("WebViewDialogFragment", "Auto-login credentials received - Username: " + username + ", Domain: " + domain);
                // Auto-login to the website
                performAutoLogin(url, username, password, domain);
            } else {
                Log.d("WebViewDialogFragment", "No auto-login credentials, loading URL normally");
                webView.loadUrl(url);
            }
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
                
                // Check if we need to perform auto-login
                String username = getArguments().getString(ARG_USERNAME);
                String password = getArguments().getString(ARG_PASSWORD);
                if (username != null && password != null) {
                    Log.d("WebViewDialogFragment", "Credentials available, attempting auto-login on page: " + url);
                    // Add a delay to ensure the page is fully rendered
                    view.postDelayed(() -> {
                        performAutoLogin(view, username, password);
                    }, 2000); // Wait 2 seconds for page to fully load
                }
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

    private void performAutoLogin(String url, String username, String password, String domain) {
        // Load the URL first
        webView.loadUrl(url);
    }

    private void performAutoLogin(WebView view, String username, String password) {
        Log.d("WebViewDialogFragment", "Attempting auto-login with username: " + username);
        
        // JavaScript to perform auto-login with more comprehensive selectors
        String loginScript = String.format(
            "javascript:(function() {" +
            "  console.log('Starting auto-login process');" +
            "  " +
            "  // Try multiple selectors for username field" +
            "  var usernameField = document.querySelector('input[name=\"username\"], input[name=\"email\"], input[type=\"email\"], input[id*=\"username\"], input[id*=\"email\"], input[id*=\"user\"], input[placeholder*=\"username\"], input[placeholder*=\"email\"]');" +
            "  " +
            "  // Try multiple selectors for password field" +
            "  var passwordField = document.querySelector('input[name=\"password\"], input[type=\"password\"], input[id*=\"password\"], input[id*=\"pass\"]');" +
            "  " +
            "  console.log('Username field found:', !!usernameField);" +
            "  console.log('Password field found:', !!passwordField);" +
            "  " +
            "  if (usernameField && passwordField) {" +
            "    console.log('Filling credentials');" +
            "    usernameField.value = '%s';" +
            "    passwordField.value = '%s';" +
            "    " +
            "    // Trigger multiple events to ensure form validation" +
            "    usernameField.dispatchEvent(new Event('input', { bubbles: true }));" +
            "    usernameField.dispatchEvent(new Event('change', { bubbles: true }));" +
            "    passwordField.dispatchEvent(new Event('input', { bubbles: true }));" +
            "    passwordField.dispatchEvent(new Event('change', { bubbles: true }));" +
            "    " +
            "    // Try to find login button with multiple selectors" +
            "    var loginButton = document.querySelector('button[type=\"submit\"], input[type=\"submit\"], button:contains(\"Login\"), button:contains(\"Sign In\"), button:contains(\"Log In\"), input[value*=\"Login\"], input[value*=\"Sign In\"]');" +
            "    " +
            "    console.log('Login button found:', !!loginButton);" +
            "    " +
            "    if (loginButton) {" +
            "      console.log('Clicking login button');" +
            "      loginButton.click();" +
            "    } else {" +
            "      console.log('No login button found, trying to submit form');" +
            "      // Try to submit the form" +
            "      var form = usernameField.closest('form');" +
            "      if (form) {" +
            "        console.log('Submitting form');" +
            "        form.submit();" +
            "      } else {" +
            "        console.log('No form found');" +
            "      }" +
            "    }" +
            "  } else {" +
            "    console.log('Required fields not found');" +
            "  }" +
            "})();",
            username, password
        );
        
        // Add a small delay to ensure the page is fully loaded
        view.postDelayed(() -> {
            view.evaluateJavascript(loginScript, result -> {
                Log.d("WebViewDialogFragment", "Auto-login script executed, result: " + result);
            });
        }, 1000);
    }

    private boolean isLoggedIn(WebView view) {
        // Check if user is already logged in by looking for common logout elements
        String checkScript = 
            "javascript:(function() {" +
            "  var logoutElements = document.querySelectorAll('a[href*=\"logout\"], button:contains(\"Logout\"), a:contains(\"Logout\"), a:contains(\"Sign Out\")');" +
            "  var userElements = document.querySelectorAll('.user-name, .user-info, [class*=\"user\"], [id*=\"user\"]');" +
            "  var adminElements = document.querySelectorAll('[href*=\"admin\"], [href*=\"dashboard\"], [class*=\"admin\"]');" +
            "  return logoutElements.length > 0 || userElements.length > 0 || adminElements.length > 0;" +
            "})();";
        
        // For now, return false to always attempt login
        // In a real implementation, you'd evaluate the JavaScript and return the result
        return false;
    }
}

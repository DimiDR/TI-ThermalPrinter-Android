package com.dantsu.thermalprinter;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;

import com.dantsu.thermalprinter.helpClasses.NetworkHelper;

public class LoginUserDialogFragment extends DialogFragment {
    private Button okButton;
    private TextView etError;
    private EditText etDomain;
    private EditText etUsername;
    private EditText etPassword;

    public interface Host {
        void onLoginSuccess(String domain, String username);
    }

    private boolean isInternetAvailable() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) requireActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.e("LoginUserDialogFragment", "Error checking internet connectivity", e);
        }
        return false;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.popup_login_user, null);

        okButton = view.findViewById(R.id.button_ok);
        Button cancelButton = view.findViewById(R.id.button_cancel);
        etDomain = view.findViewById(R.id.etDomain);
        etUsername = view.findViewById(R.id.etUsername);
        etPassword = view.findViewById(R.id.etPassword);
        etError = view.findViewById(R.id.popup_error);

        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        String lastDomain = sharedPreferences.getString("domain_shop", "");
        if (!lastDomain.isEmpty()) {
            etDomain.setText(lastDomain);
        }

        if (!isInternetAvailable()) {
            etError.setVisibility(View.VISIBLE);
            etError.setText(getString(R.string.no_internet_warning));
        }

        okButton.setOnClickListener(v -> authenticate(
                etDomain.getText().toString(),
                etUsername.getText().toString(),
                etPassword.getText().toString()
        ));
        cancelButton.setOnClickListener(v -> dismiss());

        Dialog dialog = builder.setView(view).create();
        dialog.setOnDismissListener(this::onLoginDismissed);
        return dialog;
    }

    private void onLoginDismissed(DialogInterface dialogInterface) {
        if (!(getActivity() instanceof MainActivity)) {
            return;
        }
        MainActivity activity = (MainActivity) getActivity();
        NestedScrollView nestedScrollView = activity.findViewById(R.id.nestedScrollView);
        if (nestedScrollView != null) {
            nestedScrollView.setVisibility(View.VISIBLE);
        }
        Button loginButton = activity.findViewById(R.id.button_ti_login);
        if (loginButton != null) {
            loginButton.setVisibility(View.VISIBLE);
        }
    }

    private void authenticate(String domainInput, String username, String password) {
        etError.setVisibility(View.GONE);
        okButton.setEnabled(false);
        okButton.setText(getString(R.string.authenticating));

        if (!isInternetAvailable()) {
            showError(getString(R.string.no_internet_error));
            return;
        }
        if (domainInput == null || domainInput.trim().isEmpty()) {
            showError(getString(R.string.domain_required));
            return;
        }
        String domainNormalized = domainInput.trim().replaceAll("\\s+", "");
        domainNormalized = domainNormalized.replaceAll("/+$", "");
        if (domainNormalized.isEmpty()) {
            showError(getString(R.string.invalid_domain));
            return;
        }
        if (!domainNormalized.startsWith("http://") && !domainNormalized.startsWith("https://")) {
            domainNormalized = "https://" + domainNormalized;
        }
        final String finalDomain = domainNormalized;

        NetworkHelper networkHelper = new NetworkHelper(getActivity());
        networkHelper.generateTokenAsync(username, password, finalDomain, new NetworkHelper.TokenCallback() {
            @Override
            public void onTokenGenerated(String token) {
                requireActivity().runOnUiThread(() -> {
                    if (getActivity() instanceof Host) {
                        ((Host) getActivity()).onLoginSuccess(finalDomain, username);
                    }
                    dismiss();
                });
            }

            @Override
            public void onTokenError(Exception exception) {
                requireActivity().runOnUiThread(() -> {
                    String errorMsg = exception.getMessage();
                    if (errorMsg != null && (errorMsg.contains("No internet connection")
                            || errorMsg.contains("Keine Internetverbindung"))) {
                        showError(getString(R.string.no_internet_error));
                    } else if (errorMsg != null && (errorMsg.contains("Cannot connect")
                            || errorMsg.contains("Connection timeout")
                            || errorMsg.contains("Connection refused")
                            || errorMsg.contains("SSL/TLS")
                            || errorMsg.contains("DNS Resolution Failed")
                            || errorMsg.contains("DNS-Auflösung")
                            || errorMsg.contains("Verbindungszeitüberschreitung")
                            || errorMsg.contains("Verbindung abgelehnt")
                            || errorMsg.contains("SSL/TLS-Fehler")
                            || errorMsg.contains("Hostname kann nicht aufgelöst werden"))) {
                        showError(errorMsg);
                    } else {
                        showError(getString(R.string.authentication_failed) + ": "
                                + (errorMsg != null ? errorMsg : getString(R.string.unknown_error)));
                    }
                });
            }
        });
    }

    private void showError(String message) {
        etError.setVisibility(View.VISIBLE);
        etError.setText(message);
        okButton.setEnabled(true);
        okButton.setText(getString(R.string.login_button));
    }
}

package com.dantsu.thermalprinter;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;

import com.dantsu.thermalprinter.helpClasses.ConnectivityUtils;
import com.dantsu.thermalprinter.helpClasses.NetworkHelper;
import com.dantsu.thermalprinter.helpClasses.ShopConfigUtils;
import com.dantsu.thermalprinter.helpClasses.ShopDirectoryRepository;

import java.util.ArrayList;
import java.util.List;

public class LoginUserDialogFragment extends DialogFragment {
    private Button okButton;
    private TextView etError;
    private EditText etDomain;
    private AutoCompleteTextView etShop;
    private List<ShopConfigUtils.Shop> shops = new ArrayList<>();
    private String customShopLabel;
    private EditText etUsername;
    private EditText etPassword;

    public interface Host {
        void onLoginSuccess(String domain, String username);
    }

    private boolean isInternetAvailable(String domain) {
        if (ConnectivityUtils.isLocalDevHost(domain)) {
            return true;
        }
        return ConnectivityUtils.isNetworkAvailable(requireContext());
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
        etShop = view.findViewById(R.id.etShop);
        etUsername = view.findViewById(R.id.etUsername);
        etPassword = view.findViewById(R.id.etPassword);
        etError = view.findViewById(R.id.popup_error);
        customShopLabel = getString(R.string.custom_shop_url);

        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        String lastDomain = sharedPreferences.getString("domain_shop", "");
        if (!lastDomain.isEmpty()) {
            etDomain.setText(lastDomain);
        }
        bindShopPicker(lastDomain);
        refreshShopDirectory(lastDomain);

        if (!isInternetAvailable(lastDomain)) {
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
        dialog.setOnShowListener(d -> {
            android.view.Window window = dialog.getWindow();
            if (window == null) {
                return;
            }
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            requireActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int maxHeight = (int) (metrics.heightPixels * 0.85f);
            android.view.View scroll = view.findViewById(R.id.login_scroll);
            if (scroll != null) {
                scroll.post(() -> {
                    int buttonsHeight = view.findViewById(R.id.button_ok).getHeight()
                            + view.findViewById(R.id.popup_header).getHeight()
                            + view.findViewById(R.id.popup_description).getHeight()
                            + (int) (80 * metrics.density);
                    int scrollMax = Math.max((int) (200 * metrics.density), maxHeight - buttonsHeight);
                    android.view.ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                    if (scroll.getHeight() > scrollMax) {
                        lp.height = scrollMax;
                        scroll.setLayoutParams(lp);
                    }
                });
            }
        });
        return dialog;
    }

    private void bindShopPicker(String lastDomain) {
        if (getContext() == null) {
            return;
        }
        bindShopPicker(lastDomain, ShopConfigUtils.getShops(requireContext()));
    }

    private void bindShopPicker(String lastDomain, List<ShopConfigUtils.Shop> loaded) {
        if (etShop == null || getContext() == null || loaded == null) {
            return;
        }
        shops = loaded;
        List<String> labels = shopLabels();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                labels);
        etShop.setAdapter(adapter);
        etShop.setThreshold(1);
        etShop.setOnClickListener(v -> etShop.showDropDown());
        etShop.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                etShop.showDropDown();
            }
        });
        etShop.setOnItemClickListener((parent, view, position, id) -> {
            String selected = adapter.getItem(position);
            if (selected == null || customShopLabel.equals(selected)) {
                if (etDomain != null) {
                    etDomain.requestFocus();
                }
                return;
            }
            ShopConfigUtils.Shop shop = shopForLabel(selected);
            if (shop != null && etDomain != null) {
                etDomain.setText(shop.domain_shop);
            }
        });
        selectShopForDomain(lastDomain, labels);
    }

    private void refreshShopDirectory(String lastDomain) {
        if (getContext() == null) {
            return;
        }
        NetworkHelper networkHelper = new NetworkHelper(requireContext());
        ShopDirectoryRepository.fetch(requireContext(), networkHelper, new ShopDirectoryRepository.Callback() {
            @Override
            public void onShopsLoaded(List<ShopConfigUtils.Shop> loaded) {
                if (!isAdded() || etShop == null) {
                    return;
                }
                shops = loaded;
                bindShopPicker(etDomain != null ? etDomain.getText().toString() : lastDomain, loaded);
            }

            @Override
            public void onShopsError(Exception exception) {
                Log.w("LoginUserDialogFragment", "Using bundled shops", exception);
            }
        });
    }

    private List<String> shopLabels() {
        List<String> labels = new ArrayList<>();
        for (ShopConfigUtils.Shop shop : shops) {
            labels.add(ShopConfigUtils.shopLabel(shop, shops));
        }
        labels.add(customShopLabel);
        return labels;
    }

    private ShopConfigUtils.Shop shopForLabel(String label) {
        for (ShopConfigUtils.Shop shop : shops) {
            if (ShopConfigUtils.shopLabel(shop, shops).equals(label)) {
                return shop;
            }
        }
        return null;
    }

    private void selectShopForDomain(String domain, List<String> labels) {
        if (etShop == null || labels == null || labels.isEmpty()) {
            return;
        }
        ShopConfigUtils.Shop match = ShopConfigUtils.findShopByDomain(shops, domain);
        String label = match != null ? ShopConfigUtils.shopLabel(match, shops) : customShopLabel;
        if (!labels.contains(label)) {
            label = customShopLabel;
        }
        etShop.setText(label, false);
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

        if (!ConnectivityUtils.isCleartextPermitted(finalDomain)) {
            showError(getString(R.string.cleartext_not_permitted));
            return;
        }

        if (!isInternetAvailable(finalDomain)) {
            showError(getString(R.string.no_internet_error));
            return;
        }

        NetworkHelper networkHelper = new NetworkHelper(getActivity());
        networkHelper.generateTokenAsync(username, password, finalDomain, new NetworkHelper.TokenCallback() {
            @Override
            public void onTokenGenerated(String token) {
                // unused — resolved-domain overload is preferred
            }

            @Override
            public void onTokenGenerated(String token, String resolvedDomain) {
                requireActivity().runOnUiThread(() -> {
                    String domainToUse = resolvedDomain != null ? resolvedDomain : finalDomain;
                    if (etDomain != null && domainToUse != null && !domainToUse.equals(etDomain.getText().toString())) {
                        etDomain.setText(domainToUse);
                    }
                    if (getActivity() instanceof Host) {
                        ((Host) getActivity()).onLoginSuccess(domainToUse, username);
                    }
                    dismiss();
                });
            }

            @Override
            public void onTokenError(Exception exception) {
                Log.e("LoginUserDialogFragment", "Login failed", exception);
                requireActivity().runOnUiThread(() -> {
                    String errorMsg = exception.getMessage();
                    if (errorMsg != null && (errorMsg.contains("No internet connection")
                            || errorMsg.contains("Keine Internetverbindung"))) {
                        showError(getString(R.string.no_internet_error));
                    } else if (errorMsg != null && (errorMsg.contains("Cannot connect")
                            || errorMsg.contains("Failed to connect")
                            || errorMsg.contains("Cannot reach your local server")
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
        Log.e("LoginUserDialogFragment", "UI error: " + message);
        etError.setVisibility(View.VISIBLE);
        String shortMessage = message == null ? getString(R.string.unknown_error) : message.trim();
        if (shortMessage.length() > 180) {
            shortMessage = shortMessage.substring(0, 177) + "…";
        }
        etError.setText(shortMessage);
        okButton.setEnabled(true);
        okButton.setText(getString(R.string.login_button));
        View scroll = getDialog() != null ? getDialog().findViewById(R.id.login_scroll) : null;
        if (scroll instanceof android.widget.ScrollView) {
            scroll.post(() -> ((android.widget.ScrollView) scroll).fullScroll(View.FOCUS_DOWN));
        }
    }
}

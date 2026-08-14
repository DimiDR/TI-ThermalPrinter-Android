package com.dantsu.thermalprinter;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.dantsu.thermalprinter.helpClasses.DocketStringModeler;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reprint popup for the last 50 orders.
 */
public final class ReprintOrdersHelper {
    public interface Host {
        boolean isPrinterAvailable();
        Chip chipReceipt();
        Chip chipKitchen();
        String shopName();
        DocketStringModeler docketStringModeler();
        android.media.MediaPlayer mediaPlayer();
        void printOrder(String printInfo, String orderId);
        FragmentManager fragmentManager();
    }

    private ReprintOrdersHelper() {}

    public static void show(
            FragmentActivity activity,
            Host host,
            List<JSONObject> ordersList,
            JSONObject jsonObjectMenus,
            JSONObject jsonObjectCategories,
            boolean finishOnDismiss
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            Log.w("ReprintOrders", "Cannot show popup - Activity is finishing or destroyed");
            return;
        }
        if (activity.getWindow() == null || activity.getWindow().getDecorView() == null
                || activity.getWindow().getDecorView().getWindowToken() == null) {
            Log.w("ReprintOrders", "Cannot show popup - Window token is not valid");
            return;
        }

        int startIndex = Math.max(0, ordersList.size() - 50);
        List<JSONObject> displayOrdersList = new ArrayList<>(ordersList.subList(startIndex, ordersList.size()));

        View popupView = activity.getLayoutInflater().inflate(R.layout.popup_orders, null);
        final PopupWindow popupWindow = new PopupWindow(
                popupView,
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popupWindow.setElevation(10.0f);

        MaterialToolbar toolbar = popupView.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> popupWindow.dismiss());
        }
        if (finishOnDismiss) {
            popupWindow.setOnDismissListener(activity::finish);
        }

        boolean printerAvailable = host.isPrinterAvailable();
        ListView listViewOrders = popupView.findViewById(R.id.listViewOrders);
        final JSONObject menus = jsonObjectMenus;
        final JSONObject categories = jsonObjectCategories;

        OrdersAdapter adapter = new OrdersAdapter(activity, displayOrdersList, (position, orderId) -> {
            if (!printerAvailable) {
                Toast.makeText(activity, R.string.select_printer_text, Toast.LENGTH_SHORT).show();
                return;
            }
            Chip receipt = host.chipReceipt();
            Chip kitchen = host.chipKitchen();
            if (receipt != null && receipt.isChecked()) {
                host.printOrder(host.docketStringModeler().startPrintingReceipt(
                        displayOrdersList.get(position), menus, categories, host.mediaPlayer(), host.shopName()), orderId);
            }
            if (kitchen != null && kitchen.isChecked()) {
                host.printOrder(host.docketStringModeler().startPrintingKitchen(
                        displayOrdersList.get(position), menus, categories, host.mediaPlayer(), host.shopName()), orderId);
            }
        }, order -> showReceiptPreview(activity, host, order, menus, categories), printerAvailable);
        listViewOrders.setAdapter(adapter);

        View anchor = activity.findViewById(R.id.mainLayout);
        if (anchor == null) {
            anchor = activity.getWindow().getDecorView();
        }
        popupWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
    }

    private static void showReceiptPreview(
            Activity activity,
            Host host,
            JSONObject order,
            JSONObject menusData,
            JSONObject categoriesData
    ) {
        try {
            if (menusData == null || categoriesData == null) {
                Toast.makeText(activity, R.string.menu_data_not_available, Toast.LENGTH_SHORT).show();
                return;
            }
            ReceiptPreviewDialogFragment dialog = ReceiptPreviewDialogFragment.newInstance(
                    order, menusData, categoriesData, host.shopName());
            dialog.show(host.fragmentManager(), "ReceiptPreviewDialog");
        } catch (Exception e) {
            Log.e("ReprintOrders", "Error showing receipt preview", e);
            Toast.makeText(activity, activity.getString(R.string.error_showing_receipt_preview, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }
}

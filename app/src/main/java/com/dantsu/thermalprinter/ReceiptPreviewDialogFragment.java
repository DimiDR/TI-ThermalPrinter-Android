package com.dantsu.thermalprinter;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.dantsu.thermalprinter.helpClasses.ReceiptTextParser;

import org.json.JSONObject;

public class ReceiptPreviewDialogFragment extends DialogFragment {
    
    private static final String ARG_ORDER = "order";
    private static final String ARG_MENUS_DATA = "menus_data";
    private static final String ARG_CATEGORIES_DATA = "categories_data";
    private static final String ARG_SHOP_NAME = "shop_name";
    
    private JSONObject order;
    private JSONObject menusData;
    private JSONObject categoriesData;
    private String shopName;
    
    public static ReceiptPreviewDialogFragment newInstance(JSONObject order, JSONObject menusData, 
                                                          JSONObject categoriesData, String shopName) {
        ReceiptPreviewDialogFragment fragment = new ReceiptPreviewDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORDER, order.toString());
        args.putString(ARG_MENUS_DATA, menusData.toString());
        args.putString(ARG_CATEGORIES_DATA, categoriesData.toString());
        args.putString(ARG_SHOP_NAME, shopName);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Material_Light_NoActionBar);
        if (getArguments() != null) {
            try {
                order = new JSONObject(getArguments().getString(ARG_ORDER));
                menusData = new JSONObject(getArguments().getString(ARG_MENUS_DATA));
                categoriesData = new JSONObject(getArguments().getString(ARG_CATEGORIES_DATA));
                shopName = getArguments().getString(ARG_SHOP_NAME);
            } catch (Exception e) {
                Log.e("ReceiptPreview", "Error parsing arguments", e);
            }
        }
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        
        if (window != null) {
            // Set dialog to fill the app window (not system window)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.white);
            window.getAttributes().windowAnimations = android.R.style.Animation_Dialog;
            
            // Get window attributes and set flags to ensure dialog stays within app boundaries
            WindowManager.LayoutParams params = window.getAttributes();
            
            // Position dialog to fill the app content area (respects system bars)
            params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
            params.x = 0;
            params.y = 0;
            
            // Ensure dialog respects system UI insets (status bar, navigation bar)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Don't draw behind system bars - keeps dialog within app window
                window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
                window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
            
            window.setAttributes(params);
        }
        
        return dialog;
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_receipt_preview, container, false);
        
        // Apply window insets to respect system bars (status bar, navigation bar)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
                WindowInsetsCompat windowInsets = insets;
                int statusBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                
                // Apply padding to account for status bar
                if (v.getPaddingTop() < statusBarHeight) {
                    v.setPadding(v.getPaddingLeft(), statusBarHeight, 
                               v.getPaddingRight(), v.getPaddingBottom());
                }
                
                return insets;
            });
        }
        
        // Setup close button
        ImageView btnClose = view.findViewById(R.id.btnClose);
        btnClose.setOnClickListener(v -> dismiss());
        
        // Generate receipt content
        generateReceiptContent(view);
        
        return view;
    }
    
    private void generateReceiptContent(View view) {
        try {
            // Build receipt data directly from JSON using ReceiptTextParser
            ReceiptTextParser.ReceiptData receiptData = ReceiptTextParser.buildReceiptData(
                order, menusData, categoriesData, shopName);
            
            // Populate header fields
            populateHeader(view, receiptData.header);
            
            // Populate order items
            LinearLayout orderItemsContainer = view.findViewById(R.id.orderItemsContainer);
            populateOrderItems(orderItemsContainer, receiptData.items);
            
            // Populate order totals
            LinearLayout orderTotalsContainer = view.findViewById(R.id.orderTotalsContainer);
            populateOrderTotals(orderTotalsContainer, receiptData.totals);
            
            // Populate customer info
            LinearLayout customerInfoContainer = view.findViewById(R.id.customerInfoContainer);
            populateCustomerInfo(customerInfoContainer, receiptData.customerInfo);
            
            // Handle QR code
            handleQRCode(view, receiptData.customerInfo);
            
        } catch (Exception e) {
            Log.e("ReceiptPreview", "Error generating receipt content", e);
        }
    }
    
    private void populateHeader(View view, ReceiptTextParser.ReceiptHeader header) {
        TextView textShopName = view.findViewById(R.id.textShopName);
        TextView textOrderNumber = view.findViewById(R.id.textOrderNumber);
        TextView textOrderDetails = view.findViewById(R.id.textOrderDetails);
        TextView textOrderTime = view.findViewById(R.id.textOrderTime);
        
        textShopName.setText(header.shopName);
        textOrderNumber.setText(header.orderNumber);
        textOrderDetails.setText(header.orderType + " - " + header.payment + " - " + header.orderTotal + "€");
        textOrderTime.setText(header.orderTime);
    }
    
    private void populateOrderItems(LinearLayout container, List<ReceiptTextParser.OrderItem> items) {
        container.removeAllViews();
        
        String currentCategoryId = "";
        
        for (ReceiptTextParser.OrderItem item : items) {
            // Add category header if category changed
            if (!item.categoryId.equals(currentCategoryId)) {
                // Add category divider
                View divider = new View(getContext());
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(Color.GRAY);
                container.addView(divider);
                
                // Add category header
                TextView categoryHeader = new TextView(getContext());
                categoryHeader.setText(item.categoryName);
                categoryHeader.setTextSize(16);
                categoryHeader.setTypeface(null, Typeface.BOLD);
                categoryHeader.setPadding(0, 16, 0, 8);
                container.addView(categoryHeader);
                
                currentCategoryId = item.categoryId;
            }
            
            // Add item name and quantity
            TextView itemText = new TextView(getContext());
            itemText.setText(item.quantity + "x - " + item.name + "  " + item.subtotal + "€");
            itemText.setTextSize(14);
            itemText.setTypeface(null, Typeface.BOLD);
            itemText.setPadding(0, 4, 0, 4);
            container.addView(itemText);
            
            // Add comment if exists
            if (!"null".equals(item.comment) && !item.comment.isEmpty()) {
                TextView commentText = new TextView(getContext());
                commentText.setText("  " + getString(R.string.comment) + " " + item.comment);
                commentText.setTextSize(12);
                commentText.setPadding(0, 2, 0, 2);
                container.addView(commentText);
            }
            
            // Add options
            for (ReceiptTextParser.OrderOption option : item.options) {
                TextView optionText = new TextView(getContext());
                optionText.setText("  " + getString(R.string.option) + " " + option.name + "  " + option.price + "€");
                optionText.setTextSize(12);
                optionText.setPadding(0, 2, 0, 2);
                container.addView(optionText);
            }
        }
    }
    
    private void populateOrderTotals(LinearLayout container, List<ReceiptTextParser.OrderTotal> totals) {
        container.removeAllViews();
        
        for (ReceiptTextParser.OrderTotal total : totals) {
            TextView totalText = new TextView(getContext());
            totalText.setText(total.title + "  " + total.value + "€");
            totalText.setTextSize(14);
            totalText.setPadding(0, 2, 0, 2);
            totalText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
            container.addView(totalText);
        }
    }
    
    private void populateCustomerInfo(LinearLayout container, ReceiptTextParser.CustomerInfo customerInfo) {
        container.removeAllViews();
        
        TextView nameText = new TextView(getContext());
        nameText.setText(getString(R.string.name) + " " + customerInfo.name);
        nameText.setTextSize(14);
        nameText.setPadding(0, 2, 0, 2);
        container.addView(nameText);
        
        TextView phoneText = new TextView(getContext());
        phoneText.setText(getString(R.string.phone) + " " + customerInfo.telephone);
        phoneText.setTextSize(14);
        phoneText.setPadding(0, 2, 0, 2);
        container.addView(phoneText);
        
        TextView addressText = new TextView(getContext());
        addressText.setText(getString(R.string.address) + " " + customerInfo.address);
        addressText.setTextSize(14);
        addressText.setPadding(0, 2, 0, 2);
        container.addView(addressText);
        
        // Only show overall order comment if it exists and is not "nicht angegeben"
        if (customerInfo.comment != null && !customerInfo.comment.isEmpty() && !"nicht angegeben".equals(customerInfo.comment)) {
            TextView commentText = new TextView(getContext());
            commentText.setText(getString(R.string.comment) + " " + customerInfo.comment);
            commentText.setTextSize(14);
            commentText.setPadding(0, 2, 0, 2);
            container.addView(commentText);
        }
    }
    
    private void handleQRCode(View view, ReceiptTextParser.CustomerInfo customerInfo) {
        LinearLayout qrCodeContainer = view.findViewById(R.id.qrCodeContainer);
        ImageView qrCodeImage = view.findViewById(R.id.qrCodeImage);
        
        if (customerInfo.hasGoogleMaps && customerInfo.googleMapsUrl != null && !customerInfo.googleMapsUrl.isEmpty()) {
            Bitmap qrBitmap = ReceiptTextParser.generateQRCode(customerInfo.googleMapsUrl, 250, 250);
            if (qrBitmap != null) {
                qrCodeImage.setImageBitmap(qrBitmap);
                qrCodeContainer.setVisibility(View.VISIBLE);
            }
        } else {
            qrCodeContainer.setVisibility(View.GONE);
        }
    }
}

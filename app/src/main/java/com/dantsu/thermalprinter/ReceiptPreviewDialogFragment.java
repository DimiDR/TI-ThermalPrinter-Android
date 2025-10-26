package com.dantsu.thermalprinter;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
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
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.white);
        dialog.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
        return dialog;
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_receipt_preview, container, false);
        
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
                commentText.setText("  Kommentar: " + item.comment);
                commentText.setTextSize(12);
                commentText.setPadding(0, 2, 0, 2);
                container.addView(commentText);
            }
            
            // Add options
            for (ReceiptTextParser.OrderOption option : item.options) {
                TextView optionText = new TextView(getContext());
                optionText.setText("  Option: " + option.name + "  " + option.price + "€");
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
        nameText.setText("Name: " + customerInfo.name);
        nameText.setTextSize(14);
        nameText.setPadding(0, 2, 0, 2);
        container.addView(nameText);
        
        TextView phoneText = new TextView(getContext());
        phoneText.setText("Telefon: " + customerInfo.telephone);
        phoneText.setTextSize(14);
        phoneText.setPadding(0, 2, 0, 2);
        container.addView(phoneText);
        
        TextView addressText = new TextView(getContext());
        addressText.setText("Adresse: " + customerInfo.address);
        addressText.setTextSize(14);
        addressText.setPadding(0, 2, 0, 2);
        container.addView(addressText);
        
        TextView commentText = new TextView(getContext());
        commentText.setText("Kommentar: " + customerInfo.comment);
        commentText.setTextSize(14);
        commentText.setPadding(0, 2, 0, 2);
        container.addView(commentText);
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

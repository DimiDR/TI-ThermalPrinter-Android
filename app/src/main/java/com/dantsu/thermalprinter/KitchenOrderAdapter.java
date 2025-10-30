package com.dantsu.thermalprinter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class KitchenOrderAdapter extends RecyclerView.Adapter<KitchenOrderAdapter.OrderViewHolder> {
    
    public List<JSONObject> orders;
    public JSONObject menusData;
    public JSONObject categoriesData;
    private PrintButtonClickListener printListener;
    private StatusChangeClickListener statusChangeListener;
    private ReceiptPreviewClickListener receiptPreviewListener;
    private boolean printerAvailable = true;
    
    public interface PrintButtonClickListener {
        void onPrintButtonClick(JSONObject order);
    }
    
    public interface StatusChangeClickListener {
        void onStatusChangeClick(JSONObject order, String statusName);
    }
    
    public interface ReceiptPreviewClickListener {
        void onReceiptPreviewClick(JSONObject order);
    }
    
    public KitchenOrderAdapter(List<JSONObject> orders) {
        this.orders = orders != null ? orders : new ArrayList<>();
    }
    
    public KitchenOrderAdapter(List<JSONObject> orders, PrintButtonClickListener printListener) {
        this.orders = orders != null ? orders : new ArrayList<>();
        this.printListener = printListener;
    }
    
    public KitchenOrderAdapter(List<JSONObject> orders, PrintButtonClickListener printListener, StatusChangeClickListener statusChangeListener) {
        this.orders = orders != null ? orders : new ArrayList<>();
        this.printListener = printListener;
        this.statusChangeListener = statusChangeListener;
    }
    
    public KitchenOrderAdapter(List<JSONObject> orders, PrintButtonClickListener printListener, StatusChangeClickListener statusChangeListener, ReceiptPreviewClickListener receiptPreviewListener) {
        this.orders = orders != null ? orders : new ArrayList<>();
        this.printListener = printListener;
        this.statusChangeListener = statusChangeListener;
        this.receiptPreviewListener = receiptPreviewListener;
    }
    
    public KitchenOrderAdapter(List<JSONObject> orders, PrintButtonClickListener printListener, StatusChangeClickListener statusChangeListener, ReceiptPreviewClickListener receiptPreviewListener, boolean printerAvailable) {
        this.orders = orders != null ? orders : new ArrayList<>();
        this.printListener = printListener;
        this.statusChangeListener = statusChangeListener;
        this.receiptPreviewListener = receiptPreviewListener;
        this.printerAvailable = printerAvailable;
    }
    
    public void setPrinterAvailable(boolean available) {
        this.printerAvailable = available;
        notifyDataSetChanged();
    }
    
    public void updateOrders(List<JSONObject> newOrders, JSONObject menusData, JSONObject categoriesData) {
        // Filter out completed orders (status_id = 5)
        List<JSONObject> filteredOrders = new ArrayList<>();
        if (newOrders != null) {
            for (JSONObject order : newOrders) {
                try {
                    // Check if order has attributes object (JSON:API format)
                    JSONObject attributes = order.optJSONObject("attributes");
                    JSONObject orderData = attributes != null ? attributes : order;
                    
                    int statusId = orderData.optInt("status_id", -1);
                    if (statusId != 5) { // Exclude completed orders (status_id = 5)
                        filteredOrders.add(order);
                    }
                } catch (Exception e) {
                    // If there's an error checking status, include the order
                    filteredOrders.add(order);
                }
            }
        }
        
        this.orders = filteredOrders;
        this.menusData = menusData;
        this.categoriesData = categoriesData;
        notifyDataSetChanged();
    }
    
    public void appendOrders(List<JSONObject> newOrders) {
        // Filter out completed orders (status_id = 5) and append to existing list
        List<JSONObject> filteredOrders = new ArrayList<>();
        if (newOrders != null) {
            for (JSONObject order : newOrders) {
                try {
                    // Check if order has attributes object (JSON:API format)
                    JSONObject attributes = order.optJSONObject("attributes");
                    JSONObject orderData = attributes != null ? attributes : order;
                    
                    int statusId = orderData.optInt("status_id", -1);
                    if (statusId != 5) { // Exclude completed orders (status_id = 5)
                        filteredOrders.add(order);
                    }
                } catch (Exception e) {
                    // If there's an error checking status, include the order
                    filteredOrders.add(order);
                }
            }
        }
        
        int startPosition = this.orders.size();
        this.orders.addAll(filteredOrders);
        notifyItemRangeInserted(startPosition, filteredOrders.size());
    }
    
    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_kitchen_order_card, parent, false);
        return new OrderViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        JSONObject order = orders.get(position);
        try {
            holder.bind(order, menusData, categoriesData, printListener, statusChangeListener, receiptPreviewListener, printerAvailable);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public int getItemCount() {
        return orders.size();
    }
    
    static class OrderViewHolder extends RecyclerView.ViewHolder {
        private TextView textOrderId;
        private TextView textOrderTime;
        private TextView textOrderValue;
        private TextView textCustomerName;
        private TextView textOrderType;
        private TextView textPhone;
        private TextView textAddress;
        private TextView textOrderItems;
        private TextView textOrderComment;
        private TextView textOrderStatus;
        private TextView textPaymentMethod;
        private View viewStatusIndicator;
        private Button buttonPrint;
        private Button buttonStatusConfirmed;
        private Button buttonStatusPreparation;
        private Button buttonStatusDelivery;
        private Button buttonStatusCompleted;
        private ImageView btnViewReceipt;
        
        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            textOrderId = itemView.findViewById(R.id.textOrderId);
            textOrderTime = itemView.findViewById(R.id.textOrderTime);
            textOrderValue = itemView.findViewById(R.id.textOrderValue);
            textCustomerName = itemView.findViewById(R.id.textCustomerName);
            textOrderType = itemView.findViewById(R.id.textOrderType);
            textPhone = itemView.findViewById(R.id.textPhone);
            textAddress = itemView.findViewById(R.id.textAddress);
            textOrderItems = itemView.findViewById(R.id.textOrderItems);
            textOrderComment = itemView.findViewById(R.id.textOrderComment);
            textOrderStatus = itemView.findViewById(R.id.textOrderStatus);
            textPaymentMethod = itemView.findViewById(R.id.textPaymentMethod);
            viewStatusIndicator = itemView.findViewById(R.id.viewStatusIndicator);
            buttonPrint = itemView.findViewById(R.id.buttonPrint);
            buttonStatusConfirmed = itemView.findViewById(R.id.buttonStatusConfirmed);
            buttonStatusPreparation = itemView.findViewById(R.id.buttonStatusPreparation);
            buttonStatusDelivery = itemView.findViewById(R.id.buttonStatusDelivery);
            buttonStatusCompleted = itemView.findViewById(R.id.buttonStatusCompleted);
            btnViewReceipt = itemView.findViewById(R.id.btnViewReceipt);
        }
        
        public void bind(JSONObject order, JSONObject menusData, JSONObject categoriesData, PrintButtonClickListener printListener, StatusChangeClickListener statusChangeListener, ReceiptPreviewClickListener receiptPreviewListener, boolean printerAvailable) throws JSONException {
            // Check if order has attributes object (JSON:API format)
            JSONObject attributes = order.optJSONObject("attributes");
            JSONObject orderData = attributes != null ? attributes : order;
            
            // Order ID
            textOrderId.setText("#" + order.getString("id"));
            
            // Order time
            String orderTime = orderData.optString("order_time", "");
            if (!orderTime.isEmpty()) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    Date time = inputFormat.parse(orderTime);
                    textOrderTime.setText(outputFormat.format(time));
                } catch (ParseException e) {
                    textOrderTime.setText(orderTime);
                }
            } else {
                textOrderTime.setText("ASAP");
            }
            
            // Order value
            double orderTotal = orderData.optDouble("order_total", 0.0);
            textOrderValue.setText(String.format("€%.2f", orderTotal));
            
            // Customer name
            String firstName = orderData.optString("first_name", "");
            String lastName = orderData.optString("last_name", "");
            textCustomerName.setText(firstName + " " + lastName);
            
            // Order type
            String orderType = orderData.optString("order_type", "collection");
            textOrderType.setText(orderType.toUpperCase());
            
            // Phone
            textPhone.setText(orderData.optString("telephone", ""));
            
            // Address
            JSONObject address = orderData.optJSONObject("address");
            if (address != null) {
                String addressText = address.optString("address_1", "") + 
                                   (address.optString("address_2", "").isEmpty() ? "" : ", " + address.optString("address_2", "")) +
                                   ", " + address.optString("city", "");
                textAddress.setText(addressText);
            } else {
                textAddress.setText("Collection");
            }
            
            // Order items
            JSONArray orderMenus = orderData.optJSONArray("order_menus");
            StringBuilder itemsText = new StringBuilder();
            if (orderMenus != null) {
                for (int i = 0; i < orderMenus.length(); i++) {
                    JSONObject menuItem = orderMenus.getJSONObject(i);
                    int quantity = menuItem.optInt("qty", 1);
                    String name = menuItem.optString("name", "");
                    itemsText.append(quantity).append("x ").append(name).append("\n");
                }
            }
            textOrderItems.setText(itemsText.toString().trim());
            
            // Order comment
            String comment = orderData.optString("comment", "");
            if (!comment.isEmpty()) {
                textOrderComment.setText(comment);
                textOrderComment.setVisibility(View.VISIBLE);
            } else {
                textOrderComment.setVisibility(View.GONE);
            }
            
            // Order status - get from status_id and map to status name
            int statusId = orderData.optInt("status_id", -1);
            String statusName = getStatusNameById(statusId);
            textOrderStatus.setText(statusName);
            
            // Set status color based on status ID
            int statusColor = getStatusColorById(statusId);
            viewStatusIndicator.setBackgroundColor(statusColor);
            
            // Payment method
            String paymentCode = orderData.optString("payment", "cod");
            textPaymentMethod.setText(paymentCode.toUpperCase());
            
            // Set up print button click listener
            if (buttonPrint != null) {
                buttonPrint.setOnClickListener(v -> {
                    if (printListener != null) {
                        printListener.onPrintButtonClick(order);
                    }
                });
                // Enable/disable print button based on printer availability
                buttonPrint.setEnabled(printerAvailable);
                if (!printerAvailable) {
                    buttonPrint.setAlpha(0.5f); // Make button appear disabled
                } else {
                    buttonPrint.setAlpha(1.0f); // Make button appear enabled
                }
            }
            
            // Set up status change button click listeners
            if (buttonStatusConfirmed != null) {
                buttonStatusConfirmed.setOnClickListener(v -> {
                    if (statusChangeListener != null) {
                        statusChangeListener.onStatusChangeClick(order, "confirmed");
                    }
                });
            }
            
            if (buttonStatusPreparation != null) {
                buttonStatusPreparation.setOnClickListener(v -> {
                    if (statusChangeListener != null) {
                        statusChangeListener.onStatusChangeClick(order, "preparation");
                    }
                });
            }
            
            if (buttonStatusDelivery != null) {
                buttonStatusDelivery.setOnClickListener(v -> {
                    if (statusChangeListener != null) {
                        statusChangeListener.onStatusChangeClick(order, "delivery");
                    }
                });
            }
            
            if (buttonStatusCompleted != null) {
                buttonStatusCompleted.setOnClickListener(v -> {
                    if (statusChangeListener != null) {
                        statusChangeListener.onStatusChangeClick(order, "completed");
                    }
                });
            }
            
            // Set up receipt preview button click listener
            if (btnViewReceipt != null) {
                btnViewReceipt.setOnClickListener(v -> {
                    if (receiptPreviewListener != null) {
                        receiptPreviewListener.onReceiptPreviewClick(order);
                    }
                });
            }
        }
        
        private String getStatusNameById(int statusId) {
            switch (statusId) {
                case 1:
                    return "Pending";
                case 2:
                    return "Confirmed";
                case 3:
                    return "Preparation";
                case 4:
                    return "Delivery";
                case 5:
                    return "Completed";
                default:
                    return "Unknown";
            }
        }
        
        private int getStatusColorById(int statusId) {
            switch (statusId) {
                case 1:
                    return Color.parseColor("#FFA500"); // Orange for Pending
                case 2:
                    return Color.parseColor("#FFD700"); // Gold for Confirmed
                case 3:
                    return Color.parseColor("#FF6B35"); // Orange-Red for Preparation
                case 4:
                    return Color.parseColor("#4CAF50"); // Green for Delivery
                case 5:
                    return Color.parseColor("#9E9E9E"); // Gray for Completed
                default:
                    return Color.parseColor("#FF0000"); // Red for Unknown
            }
        }
    }
}

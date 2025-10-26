package com.dantsu.thermalprinter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
    
    private List<JSONObject> orders;
    public JSONObject menusData;
    public JSONObject categoriesData;
    private PrintButtonClickListener printListener;
    
    public interface PrintButtonClickListener {
        void onPrintButtonClick(JSONObject order);
    }
    
    public KitchenOrderAdapter(List<JSONObject> orders) {
        this.orders = orders != null ? orders : new ArrayList<>();
    }
    
    public KitchenOrderAdapter(List<JSONObject> orders, PrintButtonClickListener printListener) {
        this.orders = orders != null ? orders : new ArrayList<>();
        this.printListener = printListener;
    }
    
    public void updateOrders(List<JSONObject> newOrders, JSONObject menusData, JSONObject categoriesData) {
        // Filter out completed orders
        List<JSONObject> filteredOrders = new ArrayList<>();
        if (newOrders != null) {
            for (JSONObject order : newOrders) {
                try {
                    JSONObject status = order.optJSONObject("status");
                    if (status != null) {
                        String statusName = status.optString("status_name", "").toLowerCase();
                        if (!statusName.equals("completed")) {
                            filteredOrders.add(order);
                        }
                    } else {
                        // If no status info, include the order
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
            holder.bind(order, menusData, categoriesData, printListener);
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
        }
        
        public void bind(JSONObject order, JSONObject menusData, JSONObject categoriesData, PrintButtonClickListener printListener) throws JSONException {
            // Order ID
            textOrderId.setText("#" + order.getString("id"));
            
            // Order time
            String orderTime = order.optString("order_time", "");
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
            JSONObject totals = order.optJSONObject("totals");
            if (totals != null) {
                double total = totals.optDouble("total", 0.0);
                textOrderValue.setText(String.format("€%.2f", total));
            }
            
            // Customer name
            String firstName = order.optString("first_name", "");
            String lastName = order.optString("last_name", "");
            textCustomerName.setText(firstName + " " + lastName);
            
            // Order type
            String orderType = order.optString("order_type", "collection");
            textOrderType.setText(orderType.toUpperCase());
            
            // Phone
            textPhone.setText(order.optString("telephone", ""));
            
            // Address
            JSONObject address = order.optJSONObject("address");
            if (address != null) {
                String addressText = address.optString("address_1", "") + 
                                   (address.optString("address_2", "").isEmpty() ? "" : ", " + address.optString("address_2", "")) +
                                   ", " + address.optString("city", "");
                textAddress.setText(addressText);
            } else {
                textAddress.setText("Collection");
            }
            
            // Order items
            JSONArray orderMenus = order.optJSONArray("order_menus");
            StringBuilder itemsText = new StringBuilder();
            if (orderMenus != null) {
                for (int i = 0; i < orderMenus.length(); i++) {
                    JSONObject menuItem = orderMenus.getJSONObject(i);
                    int quantity = menuItem.optInt("quantity", 1);
                    String name = menuItem.optString("name", "");
                    itemsText.append(quantity).append("x ").append(name).append("\n");
                }
            }
            textOrderItems.setText(itemsText.toString().trim());
            
            // Order comment
            String comment = order.optString("comment", "");
            if (!comment.isEmpty()) {
                textOrderComment.setText(comment);
                textOrderComment.setVisibility(View.VISIBLE);
            } else {
                textOrderComment.setVisibility(View.GONE);
            }
            
            // Order status
            JSONObject status = order.optJSONObject("status");
            if (status != null) {
                String statusName = status.optString("status_name", "Unknown");
                textOrderStatus.setText(statusName);
                
                // Set status color
                String statusColor = status.optString("status_color", "#FF0000");
                try {
                    int color = Color.parseColor(statusColor);
                    viewStatusIndicator.setBackgroundColor(color);
                } catch (IllegalArgumentException e) {
                    viewStatusIndicator.setBackgroundColor(Color.parseColor("#FF0000"));
                }
            }
            
            // Payment method
            JSONObject payment = order.optJSONObject("payment_method");
            if (payment != null) {
                String paymentCode = payment.optString("code", "CASH");
                textPaymentMethod.setText(paymentCode.toUpperCase());
            } else {
                textPaymentMethod.setText("CASH");
            }
            
            // Set up print button click listener
            if (buttonPrint != null) {
                buttonPrint.setOnClickListener(v -> {
                    if (printListener != null) {
                        printListener.onPrintButtonClick(order);
                    }
                });
            }
        }
    }
}

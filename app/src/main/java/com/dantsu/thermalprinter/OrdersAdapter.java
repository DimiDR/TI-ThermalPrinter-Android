package com.dantsu.thermalprinter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class OrdersAdapter extends ArrayAdapter<JSONObject> {
    private final Context context;
    private final List<JSONObject> orders;
    private final PrintButtonClickListener listener;
    private final boolean printerAvailable;


    public interface PrintButtonClickListener {
        void onPrintButtonClick(int position, String orderId);
    }

    public OrdersAdapter(Context context, List<JSONObject> orders, PrintButtonClickListener listener) {
        super(context, 0, orders);
        this.context = context;
        this.orders = orders;
        this.listener = listener;
        this.printerAvailable = true; // Default to enabled for backward compatibility
    }

    public OrdersAdapter(Context context, List<JSONObject> orders, PrintButtonClickListener listener, boolean printerAvailable) {
        super(context, 0, orders);
        this.context = context;
        this.orders = orders;
        this.listener = listener;
        this.printerAvailable = printerAvailable;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.order_item, parent, false);
        }

        JSONObject order = orders.get(position);

        TextView textViewOrderNr = convertView.findViewById(R.id.textViewOrderNr);
        TextView textViewTime = convertView.findViewById(R.id.textViewTime);
        TextView textViewName = convertView.findViewById(R.id.textViewName);
        TextView textViewStatus = convertView.findViewById(R.id.textViewStatus);
        Button buttonPrint = convertView.findViewById(R.id.buttonPrint);

        try {
            // Extract data from the "attributes" object
            JSONObject attributes = order.getJSONObject("attributes");

            textViewOrderNr.setText("Order ID: "+order.getString("id"));
            textViewTime.setText(attributes.getString("order_time"));
            textViewName.setText(attributes.getString("first_name") + " " + attributes.getString("last_name"));
            textViewStatus.setText(attributes.getJSONObject("status").getString("status_name"));
            Log.e("adapterrrrr", "getView: " + attributes.getString("first_name") + " " + attributes.getString("last_name"));

        } catch (JSONException e) {
            e.printStackTrace();
        }

        buttonPrint.setOnClickListener(v -> {
            // Call the listener method
            String orderID = order.optString("id");
            listener.onPrintButtonClick(position, orderID);
        });
        
        // Enable/disable print button based on printer availability
        buttonPrint.setEnabled(printerAvailable);
        if (!printerAvailable) {
            buttonPrint.setAlpha(0.5f); // Make button appear disabled
        } else {
            buttonPrint.setAlpha(1.0f); // Make button appear enabled
        }


        return convertView;
    }
}

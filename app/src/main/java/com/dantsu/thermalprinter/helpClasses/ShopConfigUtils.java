package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ShopConfigUtils {

    public static List<Shop> getShops(Context context) {
        List<Shop> shops = new ArrayList<>();

        try {
            InputStream is = context.getAssets().open("users.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            String json = new String(buffer, "UTF-8");
            JSONObject jsonObject = new JSONObject(json);
            JSONArray jsonArray = jsonObject.getJSONArray("shops");

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject shopObject = jsonArray.getJSONObject(i);
                Integer shop_id = shopObject.getInt("shop_id");
                String shop_name = shopObject.getString("shop_name");
                String domain_shop = shopObject.getString("domain_shop");
                String domain_website = shopObject.getString("domain_website");
                String kitchen_view = shopObject.getString("kitchen_view");
                Integer location_id = shopObject.getInt("location_id");
                shops.add(new Shop(shop_id, shop_name, domain_shop, domain_website, kitchen_view, location_id));
            }

        } catch (IOException | JSONException e) {
            e.printStackTrace();
            Toast.makeText(context, "Check if users.json exists", Toast.LENGTH_SHORT).show();
        }

        return shops;
    }

    public static Shop getShopByDomain(Context context, String domain) {
        List<Shop> shops = getShops(context);
        for (Shop shop : shops) {
            if (shop.domain_shop.equals(domain)) {
                return shop;
            }
        }
        return null;
    }

    public static class Shop {
        public String shop_name;
        public String domain_shop;
        public String domain_website;
        public String kitchen_view;
        public Integer shop_id;
        public Integer location_id;

        public Shop(Integer shop_id, String shop_name, String domain_shop, 
                   String domain_website, String kitchen_view, Integer location_id) {
            this.shop_id = shop_id;
            this.shop_name = shop_name;
            this.domain_shop = domain_shop;
            this.domain_website = domain_website;
            this.kitchen_view = kitchen_view;
            this.location_id = location_id;
        }
    }
}

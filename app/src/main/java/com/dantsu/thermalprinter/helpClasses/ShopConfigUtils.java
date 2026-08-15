package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ShopConfigUtils {

    private static final String PREFS_LOGIN = "loginPrefs";
    private static final String KEY_SHOP_NAME = "shop_name";

    public static List<Shop> getShops(Context context) {
        try {
            InputStream is = context.getAssets().open("users.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return parseShops(new String(buffer, "UTF-8"));
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            Toast.makeText(context, "Check if users.json exists", Toast.LENGTH_SHORT).show();
            return new ArrayList<>();
        }
    }

    /** Parses the shops array from users.json content. Package-visible for unit tests. */
    static List<Shop> parseShops(String json) throws JSONException {
        List<Shop> shops = new ArrayList<>();
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
        return shops;
    }

    public static Shop getShopByDomain(Context context, String domain) {
        return findShopByDomain(getShops(context), domain);
    }

    static Shop findShopByDomain(List<Shop> shops, String domain) {
        if (domain == null || domain.isEmpty() || shops == null) {
            return null;
        }
        for (Shop shop : shops) {
            if (shop.domain_shop.equals(domain)) {
                return shop;
            }
        }
        return null;
    }

    /** Prefer users.json name for the domain; otherwise use the domain itself. */
    public static String resolveShopName(Context context, String domain) {
        return resolveShopName(getShops(context), domain);
    }

    static String resolveShopName(List<Shop> shops, String domain) {
        Shop shop = findShopByDomain(shops, domain);
        if (shop != null && shop.shop_name != null && !shop.shop_name.isEmpty()) {
            return shop.shop_name;
        }
        return domain != null ? domain : "";
    }

    /**
     * Writes shop_name / shop_id / location_id from users.json for the given domain.
     * Falls back to the domain as display name and keeps existing location_id (default 1).
     * Uses commit() so callers can safely read location_id immediately afterwards.
     */
    public static void applyShopPreferences(Context context, String domain) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Shop shop = getShopByDomain(context, domain);
        if (shop != null) {
            editor.putString(KEY_SHOP_NAME, shop.shop_name);
            if (shop.shop_id != null) {
                editor.putInt("shop_id", shop.shop_id);
            }
            if (shop.location_id != null) {
                editor.putInt("location_id", shop.location_id);
            }
        } else {
            editor.putString(KEY_SHOP_NAME, domain != null ? domain : "");
            if (!prefs.contains("location_id")) {
                editor.putInt("location_id", 1);
            }
        }
        editor.commit();
    }

    /** Resolved location for a domain from users.json, else prefs, else 1. */
    public static int resolveLocationId(Context context, String domain) {
        Shop shop = getShopByDomain(context, domain);
        if (shop != null && shop.location_id != null) {
            return shop.location_id;
        }
        return context.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE)
                .getInt("location_id", 1);
    }

    public static void saveShopName(Context context, String shopName) {
        if (shopName == null) {
            shopName = "";
        }
        context.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SHOP_NAME, shopName)
                .apply();
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

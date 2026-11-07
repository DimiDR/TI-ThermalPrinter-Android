package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class ShopConfigUtils {

    private static final String PREFS_LOGIN = "loginPrefs";
    private static final String KEY_SHOP_NAME = "shop_name";
    static final String KEY_CACHED_DIRECTORY = "cached_shop_directory";
    static final String KEY_PLATFORM_URL = "platform_url";

    public static List<Shop> getShops(Context context) {
        ShopFile bundled = readBundledShopFile(context);
        List<Shop> cached = readCachedShops(context);
        if (bundled.shops.isEmpty() && cached.isEmpty()) {
            Toast.makeText(context, "Check if users.json exists", Toast.LENGTH_SHORT).show();
        }
        return mergeShops(bundled.shops, cached);
    }

    public static String platformUrl(Context context) {
        ShopFile bundled = readBundledShopFile(context);
        if (bundled.platformUrl != null && !bundled.platformUrl.isEmpty()) {
            return bundled.platformUrl.replaceAll("/+$", "");
        }
        String fromPrefs = context.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE)
                .getString(KEY_PLATFORM_URL, "");
        if (fromPrefs != null && !fromPrefs.isEmpty()) {
            return fromPrefs.replaceAll("/+$", "");
        }
        return com.dantsu.thermalprinter.Constants.DEFAULT_PLATFORM_URL;
    }

    public static void cacheDirectory(Context context, String json) {
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        context.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHED_DIRECTORY, json)
                .commit();
    }

    /** Debug-only shops (local dev server); the file only exists in src/debug/assets. */
    private static final String DEV_SHOPS_ASSET = "users_dev.json";

    static ShopFile readBundledShopFile(Context context) {
        ShopFile file;
        try {
            String json = readAsset(context, "users.json");
            file = json.isEmpty() ? new ShopFile("", new ArrayList<>()) : parseShopFile(json);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            file = new ShopFile("", new ArrayList<>());
        }
        try {
            String devJson = readAsset(context, DEV_SHOPS_ASSET);
            if (!devJson.isEmpty()) {
                file.shops.addAll(parseShopFile(devJson).shops);
            }
        } catch (IOException ignored) {
            // Release builds ship without the dev file.
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return file;
    }

    private static String readAsset(Context context, String name) throws IOException {
        try (InputStream is = context.getAssets().open(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static List<Shop> readCachedShops(Context context) {
        String json = context.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE)
                .getString(KEY_CACHED_DIRECTORY, "");
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return parseShopFile(json).shops;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    /** Parses users.json or GET /api/tenancy/shops payloads. Package-visible for unit tests. */
    static List<Shop> parseShops(String json) throws JSONException {
        return parseShopFile(json).shops;
    }

    static ShopFile parseShopFile(String json) throws JSONException {
        JSONObject jsonObject = new JSONObject(json);
        String platformUrl = jsonObject.optString("platform_url", "").trim();
        JSONArray jsonArray = jsonObject.optJSONArray("shops");
        List<Shop> shops = new ArrayList<>();
        if (jsonArray != null) {
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject shopObject = jsonArray.getJSONObject(i);
                shops.add(parseShopObject(shopObject));
            }
        }
        return new ShopFile(platformUrl, shops);
    }

    static Shop parseShopObject(JSONObject shopObject) throws JSONException {
        Integer shop_id = shopObject.has("shop_id") ? shopObject.getInt("shop_id") : 0;
        String shop_name = firstNonEmpty(shopObject.optString("shop_name"), shopObject.optString("name"));
        String domain_shop = firstNonEmpty(shopObject.optString("domain_shop"), shopObject.optString("url"));
        if (domain_shop.isEmpty() && shopObject.has("domain")) {
            String host = shopObject.optString("domain");
            domain_shop = host.contains("://") ? host : "https://" + host;
        }
        domain_shop = domain_shop.replaceAll("/+$", "");
        String domain_website = firstNonEmpty(shopObject.optString("domain_website"), domain_shop);
        Integer location_id = shopObject.has("location_id") ? shopObject.getInt("location_id") : 1;
        String kitchen_view = shopObject.optString("kitchen_view", "");
        if (kitchen_view.isEmpty() && !domain_shop.isEmpty()) {
            kitchen_view = domain_shop + "/admin/thoughtco/kitchendisplay/summary/view/" + location_id;
        }
        return new Shop(shop_id, shop_name, domain_shop, domain_website, kitchen_view, location_id);
    }

    /**
     * Remote directory wins on the same host so new tenants and location ids update
     * without an APK rebuild. Bundled-only shops (emulator, not-yet-migrated) stay.
     */
    static List<Shop> mergeShops(List<Shop> bundled, List<Shop> remote) {
        LinkedHashMap<String, Shop> byHost = new LinkedHashMap<>();
        if (bundled != null) {
            for (Shop shop : bundled) {
                String key = hostKey(shop.domain_shop);
                if (!key.isEmpty()) {
                    byHost.put(key, shop);
                }
            }
        }
        if (remote != null) {
            for (Shop shop : remote) {
                String key = hostKey(shop.domain_shop);
                if (!key.isEmpty()) {
                    byHost.put(key, shop);
                }
            }
        }
        return new ArrayList<>(byHost.values());
    }

    static String hostKey(String urlOrHost) {
        if (urlOrHost == null) {
            return "";
        }
        String value = urlOrHost.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        try {
            if (!value.contains("://")) {
                value = "https://" + value;
            }
            URL url = new URL(value);
            String host = url.getHost();
            if (host == null) {
                return "";
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host;
        } catch (Exception e) {
            return value.replaceAll("^https?://", "").replaceAll("/.*$", "").replaceAll("^www\\.", "");
        }
    }

    public static String shopLabel(Shop shop, List<Shop> all) {
        if (shop == null || shop.shop_name == null) {
            return "";
        }
        int sameName = 0;
        if (all != null) {
            for (Shop other : all) {
                if (shop.shop_name.equals(other.shop_name)) {
                    sameName++;
                }
            }
        }
        if (sameName > 1) {
            String host = hostKey(shop.domain_shop);
            return host.isEmpty() ? shop.shop_name : shop.shop_name + " (" + host + ")";
        }
        return shop.shop_name;
    }

    public static Shop getShopByDomain(Context context, String domain) {
        return findShopByDomain(getShops(context), domain);
    }

    public static Shop findShopByDomain(List<Shop> shops, String domain) {
        if (domain == null || domain.isEmpty() || shops == null) {
            return null;
        }
        String key = hostKey(domain);
        for (Shop shop : shops) {
            if (shop.domain_shop.equals(domain) || hostKey(shop.domain_shop).equals(key)) {
                return shop;
            }
        }
        return null;
    }

    /** Prefer users.json / directory name for the domain; otherwise use the domain itself. */
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
     * Writes shop_name / shop_id / location_id from the catalog for the given domain.
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

    /** Resolved location for a domain from the catalog, else prefs, else 1. */
    public static int resolveLocationId(Context context, String domain) {
        Shop shop = getShopByDomain(context, domain);
        if (shop != null && shop.location_id != null) {
            return shop.location_id;
        }
        return context.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE)
                .getInt("location_id", 1);
    }

    public static void saveLocationId(Context context, int locationId) {
        context.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE)
                .edit()
                .putInt("location_id", locationId)
                .commit();
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

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    static final class ShopFile {
        final String platformUrl;
        final List<Shop> shops;

        ShopFile(String platformUrl, List<Shop> shops) {
            this.platformUrl = platformUrl != null ? platformUrl : "";
            this.shops = shops != null ? shops : new ArrayList<>();
        }
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

package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared fetch for orders/menus/categories plus optional statuses.
 * Short TTL cache avoids three Activities hitting the API at the same time.
 */
public final class ShopCatalogRepository {
    private static final long CACHE_TTL_MS = 8000;
    private static final AtomicReference<Catalog> CACHE = new AtomicReference<>();
    private static final AtomicLong CACHE_AT = new AtomicLong(0);

    private ShopCatalogRepository() {}

    public static final class Catalog {
        public final String domain;
        public final String ordersJson;
        public final String menusJson;
        public final String categoriesJson;
        public final String statusesJson;

        public Catalog(String domain, String ordersJson, String menusJson, String categoriesJson, String statusesJson) {
            this.domain = domain;
            this.ordersJson = ordersJson;
            this.menusJson = menusJson;
            this.categoriesJson = categoriesJson;
            this.statusesJson = statusesJson;
        }

        public String[] asResults() {
            return new String[]{ordersJson, menusJson, categoriesJson};
        }
    }

    public interface CatalogCallback {
        void onSuccess(Catalog catalog);
        void onError(Exception exception);
    }

    public static String[] catalogUrls(String domain, int locationId) {
        return new String[]{
                domain + "/api/orders?sort=order_id desc&pageLimit=50&location=" + locationId,
                domain + "/api/menus?include=categories&pageLimit=5000&location=" + locationId,
                domain + "/api/categories?location=" + locationId
        };
    }

    public static String ordersUrl(String domain, int locationId, int page) {
        String url = domain + "/api/orders?sort=order_id desc&pageLimit=50&location=" + locationId;
        if (page > 1) {
            url += "&page=" + page;
        }
        return url;
    }

    public static String statusesUrl(String domain) {
        return domain + "/api/statuses?pageLimit=100";
    }

    public static void fetchAsCallback(NetworkHelper helper, String domain, int locationId, boolean force, NetworkHelper.NetworkCallback callback) {
        fetch(helper, domain, locationId, force, new CatalogCallback() {
            @Override
            public void onSuccess(Catalog catalog) {
                callback.onSuccess(catalog.asResults());
            }

            @Override
            public void onError(Exception exception) {
                callback.onError(exception);
            }
        });
    }

    public static void fetch(NetworkHelper helper, String domain, int locationId, boolean force, CatalogCallback callback) {
        Catalog cached = CACHE.get();
        long age = System.currentTimeMillis() - CACHE_AT.get();
        if (!force && cached != null && domain.equals(cached.domain) && age < CACHE_TTL_MS) {
            callback.onSuccess(cached);
            return;
        }
        helper.fetchData(catalogUrls(domain, locationId), domain, new NetworkHelper.NetworkCallback() {
            @Override
            public void onSuccess(String[] results) {
                if (results == null || results.length < 3 || results[0] == null || results[1] == null || results[2] == null) {
                    callback.onError(new Exception("Incomplete catalog response"));
                    return;
                }
                Catalog withoutStatuses = new Catalog(domain, results[0], results[1], results[2], null);
                helper.fetchData(new String[]{statusesUrl(domain)}, domain, new NetworkHelper.NetworkCallback() {
                    @Override
                    public void onSuccess(String[] statusResults) {
                        String statuses = statusResults != null && statusResults.length > 0 ? statusResults[0] : null;
                        OrderStatusMapper.loadFromStatusesJson(statuses);
                        putCache(new Catalog(domain, results[0], results[1], results[2], statuses), callback);
                    }

                    @Override
                    public void onError(Exception exception) {
                        putCache(withoutStatuses, callback);
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                callback.onError(exception);
            }
        });
    }

    public static void invalidate() {
        CACHE.set(null);
        CACHE_AT.set(0);
    }

    public static int locationId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        return prefs.getInt("location_id", 1);
    }

    public static String domain(Context context) {
        return context.getSharedPreferences("loginPrefs", Context.MODE_PRIVATE).getString("domain_shop", "");
    }

    private static void putCache(Catalog catalog, CatalogCallback callback) {
        CACHE.set(catalog);
        CACHE_AT.set(System.currentTimeMillis());
        callback.onSuccess(catalog);
    }
}

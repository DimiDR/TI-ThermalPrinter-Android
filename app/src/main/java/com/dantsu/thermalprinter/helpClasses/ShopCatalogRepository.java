package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single in-flight fetch for orders/menus/categories. Open screens observe the
 * latest snapshot instead of each calling the API.
 */
public final class ShopCatalogRepository {
    static final long CACHE_TTL_MS = 30000;
    private static final AtomicReference<Catalog> CACHE = new AtomicReference<>();
    private static final AtomicLong CACHE_AT = new AtomicLong(0);
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);
    private static final List<CatalogCallback> PENDING = new ArrayList<>();
    private static final CopyOnWriteArrayList<CatalogCallback> OBSERVERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();

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

    public static Catalog latest() {
        return CACHE.get();
    }

    public static void observe(CatalogCallback observer) {
        if (observer == null) {
            return;
        }
        if (!OBSERVERS.contains(observer)) {
            OBSERVERS.add(observer);
        }
    }

    public static void unobserve(CatalogCallback observer) {
        OBSERVERS.remove(observer);
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
        Catalog cached;
        boolean startFetch;
        synchronized (LOCK) {
            cached = CACHE.get();
            long age = System.currentTimeMillis() - CACHE_AT.get();
            boolean cacheOk = !force && cached != null && domain.equals(cached.domain) && age < CACHE_TTL_MS;
            if (cacheOk) {
                startFetch = false;
            } else if (IN_FLIGHT.get()) {
                enqueuePending(callback);
                return;
            } else {
                IN_FLIGHT.set(true);
                enqueuePending(callback);
                startFetch = true;
                cached = null;
            }
        }
        if (!startFetch) {
            callback.onSuccess(cached);
            return;
        }
        helper.fetchData(catalogUrls(domain, locationId), domain, new NetworkHelper.NetworkCallback() {
            @Override
            public void onSuccess(String[] results) {
                if (results == null || results.length < 3 || results[0] == null || results[1] == null || results[2] == null) {
                    complete(null, new Exception("Incomplete catalog response"));
                    return;
                }
                Catalog withoutStatuses = new Catalog(domain, results[0], results[1], results[2], null);
                helper.fetchData(new String[]{statusesUrl(domain)}, domain, new NetworkHelper.NetworkCallback() {
                    @Override
                    public void onSuccess(String[] statusResults) {
                        String statuses = statusResults != null && statusResults.length > 0 ? statusResults[0] : null;
                        OrderStatusMapper.loadFromStatusesJson(statuses);
                        complete(new Catalog(domain, results[0], results[1], results[2], statuses), null);
                    }

                    @Override
                    public void onError(Exception exception) {
                        complete(withoutStatuses, null);
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                complete(null, exception);
            }
        });
    }

    public static void invalidate() {
        synchronized (LOCK) {
            CACHE.set(null);
            CACHE_AT.set(0);
        }
    }

    public static int locationId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        return prefs.getInt("location_id", 1);
    }

    public static String domain(Context context) {
        return context.getSharedPreferences("loginPrefs", Context.MODE_PRIVATE).getString("domain_shop", "");
    }

    private static void enqueuePending(CatalogCallback callback) {
        if (callback != null && !PENDING.contains(callback)) {
            PENDING.add(callback);
        }
    }

    static void replaceCacheForTest(Catalog catalog) {
        synchronized (LOCK) {
            CACHE.set(catalog);
            CACHE_AT.set(catalog == null ? 0 : System.currentTimeMillis());
        }
    }

    private static void complete(Catalog catalog, Exception error) {
        List<CatalogCallback> oneShot;
        synchronized (LOCK) {
            IN_FLIGHT.set(false);
            oneShot = new ArrayList<>(PENDING);
            PENDING.clear();
            if (catalog != null) {
                CACHE.set(catalog);
                CACHE_AT.set(System.currentTimeMillis());
            }
        }
        MAIN.post(() -> {
            if (error != null) {
                for (CatalogCallback callback : oneShot) {
                    callback.onError(error);
                }
                return;
            }
            for (CatalogCallback callback : oneShot) {
                callback.onSuccess(catalog);
            }
            for (CatalogCallback observer : OBSERVERS) {
                if (!oneShot.contains(observer)) {
                    observer.onSuccess(catalog);
                }
            }
        });
    }
}

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
    private static final long IN_FLIGHT_STALE_MS = 90000;
    private static final AtomicReference<Catalog> CACHE = new AtomicReference<>();
    private static final AtomicLong CACHE_AT = new AtomicLong(0);
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicLong IN_FLIGHT_SINCE = new AtomicLong(0);
    private static final AtomicLong FETCH_GEN = new AtomicLong(0);
    private static final List<CatalogCallback> PENDING = new ArrayList<>();
    private static final CopyOnWriteArrayList<CatalogCallback> OBSERVERS = new CopyOnWriteArrayList<>();
    private static final Object LOCK = new Object();
    private static Handler mainHandler;

    private ShopCatalogRepository() {}

    private static Handler mainHandler() {
        synchronized (LOCK) {
            if (mainHandler == null) {
                mainHandler = new Handler(Looper.getMainLooper());
            }
            return mainHandler;
        }
    }

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

    /** Normalize shop base URL (no trailing slash). */
    public static String normalizeDomain(String domain) {
        if (domain == null) {
            return "";
        }
        return domain.trim().replaceAll("/+$", "");
    }

    public static String[] catalogUrls(String domain, int locationId) {
        String base = normalizeDomain(domain);
        return new String[]{
                ordersUrl(base, locationId, 1),
                base + "/api/menus?include=categories&pageLimit=5000&location=" + locationId,
                base + "/api/categories?location=" + locationId
        };
    }

    /**
     * Orders list URL. Sort value is percent-encoded — a raw space makes many
     * TastyIgniter installs return HTTP 400 (seen on Android as FileNotFoundException).
     */
    public static String ordersUrl(String domain, int locationId, int page) {
        String url = normalizeDomain(domain)
                + "/api/orders?sort=order_id%20desc&pageLimit=50&location=" + locationId;
        if (page > 1) {
            url += "&page=" + page;
        }
        return url;
    }

    /** TastyIgniter registers the status resource as {@code /api/status} (singular). */
    public static String statusesUrl(String domain) {
        return normalizeDomain(domain) + "/api/status?pageLimit=100";
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
        final long fetchGen;
        synchronized (LOCK) {
            cached = CACHE.get();
            long age = System.currentTimeMillis() - CACHE_AT.get();
            boolean cacheOk = !force && cached != null && domain.equals(cached.domain) && age < CACHE_TTL_MS;
            if (cacheOk) {
                startFetch = false;
                fetchGen = 0;
            } else {
                boolean inFlight = IN_FLIGHT.get();
                long flightAge = System.currentTimeMillis() - IN_FLIGHT_SINCE.get();
                boolean staleFlight = inFlight && IN_FLIGHT_SINCE.get() > 0 && flightAge > IN_FLIGHT_STALE_MS;
                if (inFlight && !staleFlight) {
                    enqueuePending(callback);
                    return;
                }
                fetchGen = FETCH_GEN.incrementAndGet();
                IN_FLIGHT.set(true);
                IN_FLIGHT_SINCE.set(System.currentTimeMillis());
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
                    complete(fetchGen, null, new Exception("Incomplete catalog response"));
                    return;
                }
                Catalog withoutStatuses = new Catalog(domain, results[0], results[1], results[2], null);
                helper.fetchData(new String[]{statusesUrl(domain)}, domain, new NetworkHelper.NetworkCallback() {
                    @Override
                    public void onSuccess(String[] statusResults) {
                        String statuses = statusResults != null && statusResults.length > 0 ? statusResults[0] : null;
                        OrderStatusMapper.loadFromStatusesJson(statuses);
                        complete(fetchGen, new Catalog(domain, results[0], results[1], results[2], statuses), null);
                    }

                    @Override
                    public void onError(Exception exception) {
                        // Statuses are optional; cancelled/failed status fetch still publishes orders.
                        complete(fetchGen, withoutStatuses, null);
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                complete(fetchGen, null, exception);
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

    private static void complete(long fetchGen, Catalog catalog, Exception error) {
        List<CatalogCallback> oneShot;
        synchronized (LOCK) {
            if (fetchGen != FETCH_GEN.get()) {
                // Superseded by a newer forced/stale recovery fetch.
                return;
            }
            IN_FLIGHT.set(false);
            IN_FLIGHT_SINCE.set(0);
            oneShot = new ArrayList<>(PENDING);
            PENDING.clear();
            if (catalog != null) {
                CACHE.set(catalog);
                CACHE_AT.set(System.currentTimeMillis());
            }
        }
        mainHandler().post(() -> {
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

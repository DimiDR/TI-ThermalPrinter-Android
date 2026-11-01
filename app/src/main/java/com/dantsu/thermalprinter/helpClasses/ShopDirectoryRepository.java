package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.util.Log;

import com.dantsu.thermalprinter.Constants;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Loads {@code GET /api/tenancy/shops} from the platform (or any tenant host)
 * and merges it with bundled {@code users.json}.
 */
public final class ShopDirectoryRepository {
    private static final String TAG = "ShopDirectory";

    public interface Callback {
        void onShopsLoaded(List<ShopConfigUtils.Shop> shops);
        void onShopsError(Exception exception);
    }

    private ShopDirectoryRepository() {}

    public static List<String> directoryUrls(Context context) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        String platform = ShopConfigUtils.platformUrl(context);
        addDirectoryUrl(urls, platform);
        List<ShopConfigUtils.Shop> bundled = ShopConfigUtils.readBundledShopFile(context).shops;
        for (ShopConfigUtils.Shop shop : bundled) {
            addDirectoryUrl(urls, shop.domain_shop);
        }
        return new ArrayList<>(urls);
    }

    public static void fetch(Context context, NetworkHelper networkHelper, Callback callback) {
        List<String> urls = directoryUrls(context);
        networkHelper.fetchFirstPublicAsync(urls, new NetworkHelper.PublicCallback() {
            @Override
            public void onSuccess(String body) {
                try {
                    List<ShopConfigUtils.Shop> remote = ShopConfigUtils.parseShops(body);
                    ShopConfigUtils.cacheDirectory(context, body);
                    List<ShopConfigUtils.Shop> merged = ShopConfigUtils.mergeShops(
                            ShopConfigUtils.readBundledShopFile(context).shops,
                            remote);
                    Log.d(TAG, "Loaded " + remote.size() + " platform shops, " + merged.size() + " total");
                    callback.onShopsLoaded(merged);
                } catch (Exception e) {
                    Log.w(TAG, "Directory parse failed", e);
                    callback.onShopsError(e);
                }
            }

            @Override
            public void onError(Exception exception) {
                Log.w(TAG, "Directory fetch failed, using bundled shops", exception);
                callback.onShopsError(exception);
            }
        });
    }

    private static void addDirectoryUrl(LinkedHashSet<String> urls, String base) {
        if (base == null || base.trim().isEmpty()) {
            return;
        }
        String normalized = base.trim().replaceAll("/+$", "");
        urls.add(normalized + Constants.SHOP_DIRECTORY_PATH);
    }
}

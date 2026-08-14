package com.dantsu.thermalprinter.helpClasses;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class ShopCatalogRepositoryTest {

    @After
    public void tearDown() {
        ShopCatalogRepository.invalidate();
        ShopCatalogRepository.replaceCacheForTest(null);
    }

    @Test
    public void latestReturnsCachedCatalog() {
        ShopCatalogRepository.Catalog catalog = new ShopCatalogRepository.Catalog(
                "https://shop.example", "{}", "{}", "{}", null);
        ShopCatalogRepository.replaceCacheForTest(catalog);
        assertSame(catalog, ShopCatalogRepository.latest());
        ShopCatalogRepository.invalidate();
        assertNull(ShopCatalogRepository.latest());
    }

    @Test
    public void observeRegistersWithoutPushingCache() {
        ShopCatalogRepository.Catalog catalog = new ShopCatalogRepository.Catalog(
                "https://shop.example", "{\"data\":[]}", "{}", "{}", null);
        ShopCatalogRepository.replaceCacheForTest(catalog);
        AtomicInteger hits = new AtomicInteger();
        ShopCatalogRepository.CatalogCallback observer = new ShopCatalogRepository.CatalogCallback() {
            @Override
            public void onSuccess(ShopCatalogRepository.Catalog received) {
                hits.incrementAndGet();
            }

            @Override
            public void onError(Exception exception) {
            }
        };
        ShopCatalogRepository.observe(observer);
        ShopCatalogRepository.unobserve(observer);
        assertEquals(0, hits.get());
        assertSame(catalog, ShopCatalogRepository.latest());
    }
}

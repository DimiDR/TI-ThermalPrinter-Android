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

    @Test
    public void ordersUrlPercentEncodesSortSpace() {
        String url = ShopCatalogRepository.ordersUrl("http://10.0.2.2:8080/", 10, 1);
        assertEquals(
                "http://10.0.2.2:8080/api/orders?sort=order_id%20desc&pageLimit=50&location=10",
                url);
        assertEquals(-1, url.indexOf("order_id desc"));
    }

    @Test
    public void statusesUrlUsesSingularResource() {
        assertEquals(
                "http://10.0.2.2:8080/api/status?pageLimit=100",
                ShopCatalogRepository.statusesUrl("http://10.0.2.2:8080/"));
    }

    @Test
    public void catalogUrlsMatchOrdersMenusCategories() {
        String[] urls = ShopCatalogRepository.catalogUrls("https://shop.example", 3);
        assertEquals(3, urls.length);
        assertEquals(ShopCatalogRepository.ordersUrl("https://shop.example", 3, 1), urls[0]);
        assertEquals("https://shop.example/api/menus?include=categories&pageLimit=5000&location=3", urls[1]);
        assertEquals("https://shop.example/api/categories?location=3", urls[2]);
    }
}

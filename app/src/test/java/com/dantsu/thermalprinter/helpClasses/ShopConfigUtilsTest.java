package com.dantsu.thermalprinter.helpClasses;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ShopConfigUtilsTest {

    private List<ShopConfigUtils.Shop> shops;

    @Before
    public void setUp() throws Exception {
        Path assets = Paths.get("src", "main", "assets", "users.json");
        if (!Files.exists(assets)) {
            assets = Paths.get("app", "src", "main", "assets", "users.json");
        }
        String json = new String(Files.readAllBytes(assets), StandardCharsets.UTF_8);
        shops = ShopConfigUtils.parseShops(json);
    }

    @Test
    public void parsesAllConfiguredShops() {
        assertEquals(6, shops.size());
    }

    @Test
    public void resolvesLocalEmulatorShopNameAndLocation() {
        ShopConfigUtils.Shop shop = ShopConfigUtils.findShopByDomain(shops, "http://10.0.2.2:8080");
        assertNotNull(shop);
        assertEquals("Harbour View", shop.shop_name);
        assertEquals(Integer.valueOf(1), shop.location_id);
        assertEquals(Integer.valueOf(6), shop.shop_id);
        assertEquals("Harbour View", ShopConfigUtils.resolveShopName(shops, "http://10.0.2.2:8080"));
    }

    @Test
    public void resolvesProductionHarbourView() {
        ShopConfigUtils.Shop shop = ShopConfigUtils.findShopByDomain(shops, "https://foodninja.app");
        assertNotNull(shop);
        assertEquals("Harbour View", shop.shop_name);
        assertEquals(Integer.valueOf(10), shop.location_id);
    }

    @Test
    public void fallsBackToDomainWhenUnknown() {
        assertNull(ShopConfigUtils.findShopByDomain(shops, "http://unknown.example"));
        assertEquals("http://unknown.example",
                ShopConfigUtils.resolveShopName(shops, "http://unknown.example"));
    }

    @Test
    public void resolvesKnownShopsByDomain() {
        assertEquals("Primavera",
                ShopConfigUtils.resolveShopName(shops, "https://bestellen.primavera-pizza-wickede.de"));
        assertEquals("Mevlana Walsum",
                ShopConfigUtils.resolveShopName(shops, "https://bestellen.mevlana-walsum.de"));
        assertEquals("Tandoori Love",
                ShopConfigUtils.resolveShopName(shops, "https://bestellen.tandoori-love.de"));
    }
}

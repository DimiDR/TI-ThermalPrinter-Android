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
        ShopConfigUtils.Shop shop = ShopConfigUtils.findShopByDomain(shops, "http://127.0.0.1:8080");
        assertNotNull(shop);
        assertEquals("Harbour View", shop.shop_name);
        assertEquals(Integer.valueOf(1), shop.location_id);
        assertEquals(Integer.valueOf(6), shop.shop_id);
        assertEquals("Harbour View", ShopConfigUtils.resolveShopName(shops, "http://127.0.0.1:8080"));
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

    @Test
    public void parsesPlatformUrlAndDirectoryPayload() throws Exception {
        ShopConfigUtils.ShopFile file = ShopConfigUtils.parseShopFile(
                "{\"platform_url\":\"https://foodninja.app\",\"shops\":["
                        + "{\"shop_id\":9,\"shop_name\":\"Pizza Roma\","
                        + "\"domain\":\"pizza-roma.de\",\"location_id\":12}]}");
        assertEquals("https://foodninja.app", file.platformUrl);
        assertEquals(1, file.shops.size());
        ShopConfigUtils.Shop shop = file.shops.get(0);
        assertEquals("Pizza Roma", shop.shop_name);
        assertEquals("https://pizza-roma.de", shop.domain_shop);
        assertEquals(Integer.valueOf(12), shop.location_id);
        assertEquals(
                "https://pizza-roma.de/admin/thoughtco/kitchendisplay/summary/view/12",
                shop.kitchen_view);
    }

    @Test
    public void matchesShopsByHostIgnoringWwwAndSlash() {
        ShopConfigUtils.Shop shop = ShopConfigUtils.findShopByDomain(
                shops, "https://www.foodninja.app/");
        assertNotNull(shop);
        assertEquals("Harbour View", shop.shop_name);
    }

    @Test
    public void mergePrefersRemoteLocationButKeepsLocalOnlyShops() {
        List<ShopConfigUtils.Shop> remote = new java.util.ArrayList<>();
        remote.add(new ShopConfigUtils.Shop(5, "Harbour View Live",
                "https://foodninja.app", "https://foodninja.app",
                "https://foodninja.app/admin/kds", 42));
        List<ShopConfigUtils.Shop> merged = ShopConfigUtils.mergeShops(shops, remote);
        ShopConfigUtils.Shop harbour = ShopConfigUtils.findShopByDomain(merged, "https://foodninja.app");
        assertNotNull(harbour);
        assertEquals(Integer.valueOf(42), harbour.location_id);
        assertEquals("Harbour View Live", harbour.shop_name);
        assertNotNull(ShopConfigUtils.findShopByDomain(merged, "http://127.0.0.1:8080"));
        assertEquals(6, merged.size());
    }

    @Test
    public void labelsDisambiguateDuplicateShopNames() {
        assertEquals("Harbour View (foodninja.app)",
                ShopConfigUtils.shopLabel(
                        ShopConfigUtils.findShopByDomain(shops, "https://foodninja.app"), shops));
        assertEquals("Harbour View (127.0.0.1)",
                ShopConfigUtils.shopLabel(
                        ShopConfigUtils.findShopByDomain(shops, "http://127.0.0.1:8080"), shops));
        assertEquals("Primavera",
                ShopConfigUtils.shopLabel(
                        ShopConfigUtils.findShopByDomain(shops, "https://bestellen.primavera-pizza-wickede.de"),
                        shops));
    }
}

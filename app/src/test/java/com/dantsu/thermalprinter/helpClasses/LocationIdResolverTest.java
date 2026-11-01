package com.dantsu.thermalprinter.helpClasses;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LocationIdResolverTest {

    @Test
    public void prefersCatalogIdWhenStillPresent() {
        String json = "{\"data\":["
                + "{\"id\":\"3\",\"type\":\"locations\"},"
                + "{\"id\":\"10\",\"type\":\"locations\"}"
                + "]}";
        assertEquals(10, LocationIdResolver.resolve(json, 10));
    }

    @Test
    public void usesFirstLocationWhenPreferredMissing() {
        String json = "{\"data\":[{\"id\":\"22\",\"type\":\"locations\"}]}";
        assertEquals(22, LocationIdResolver.resolve(json, 1));
    }

    @Test
    public void fallsBackToPreferredOnEmptyPayload() {
        assertEquals(7, LocationIdResolver.resolve("{\"data\":[]}", 7));
        assertEquals(1, LocationIdResolver.resolve("", 0));
    }
}

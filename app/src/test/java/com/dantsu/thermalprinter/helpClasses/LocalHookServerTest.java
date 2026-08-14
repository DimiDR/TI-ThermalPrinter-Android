package com.dantsu.thermalprinter.helpClasses;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalHookServerTest {

    @Test
    public void acceptsHookPaths() {
        assertTrue(LocalHookServer.isHookRequest("POST /hook HTTP/1.1"));
        assertTrue(LocalHookServer.isHookRequest("POST /hook?token=abc HTTP/1.1"));
        assertTrue(LocalHookServer.isHookRequest("GET /hook HTTP/1.1"));
        assertTrue(LocalHookServer.isHookRequest("POST / HTTP/1.1"));
        assertFalse(LocalHookServer.isHookRequest("GET / HTTP/1.1"));
        assertFalse(LocalHookServer.isHookRequest("POST /admin HTTP/1.1"));
        assertFalse(LocalHookServer.isHookRequest(null));
    }
}

package com.dantsu.thermalprinter.helpClasses;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConnectivityUtilsTest {

    @Test
    public void detectsLocalDevHosts() {
        assertTrue(ConnectivityUtils.isLocalDevHost("http://10.0.2.2:8080"));
        assertTrue(ConnectivityUtils.isLocalDevHost("http://127.0.0.1:8080"));
        assertTrue(ConnectivityUtils.isLocalDevHost("http://localhost:8080"));
        assertTrue(ConnectivityUtils.isLocalDevHost("192.168.1.50"));
        assertTrue(ConnectivityUtils.isLocalDevHost("http://10.0.0.5:3000"));
    }

    @Test
    public void rejectsPublicHosts() {
        assertFalse(ConnectivityUtils.isLocalDevHost("https://demo.tastyigniter.com"));
        assertFalse(ConnectivityUtils.isLocalDevHost("https://myshop.example.com"));
    }

    @Test
    public void requiresInternetCheckOnlyForPublicHosts() {
        assertFalse(ConnectivityUtils.requiresInternetCheck("http://10.0.2.2:8080"));
        assertTrue(ConnectivityUtils.requiresInternetCheck("https://demo.tastyigniter.com"));
    }

    @Test
    public void alternatesEmulatorLoopbackHosts() {
        assertEquals("http://127.0.0.1:8080",
                ConnectivityUtils.alternateEmulatorHostUrl("http://10.0.2.2:8080"));
        assertEquals("http://10.0.2.2:8080",
                ConnectivityUtils.alternateEmulatorHostUrl("http://127.0.0.1:8080"));
        assertEquals("http://10.0.2.2:8080",
                ConnectivityUtils.alternateEmulatorHostUrl("http://localhost:8080"));
        assertNull(ConnectivityUtils.alternateEmulatorHostUrl("https://foodninja.app"));
    }

    @Test
    public void detectsConnectFailures() {
        assertTrue(ConnectivityUtils.isConnectFailure(
                new java.io.IOException("Failed to connect to /10.0.2.2:8080")));
        assertTrue(ConnectivityUtils.isConnectFailure(
                new java.io.IOException("Network is unreachable")));
        assertFalse(ConnectivityUtils.isConnectFailure(
                new java.io.IOException("Authentication failed")));
    }
}

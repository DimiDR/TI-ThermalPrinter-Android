package com.dantsu.thermalprinter.helpClasses;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateVerifierTest {

    @Test
    public void allowsHttpsOnAllowlistedHost() {
        assertTrue(UpdateVerifier.isAllowedApkUrl(
                "https://app-version.jandiweb.de/printer-app/jandiweb_printer_1.1.0.apk"));
        assertTrue(UpdateVerifier.isAllowedApkUrl(
                "https://jandiweb.de/file.apk"));
    }

    @Test
    public void rejectsHttpAndForeignHosts() {
        assertFalse(UpdateVerifier.isAllowedApkUrl("http://app-version.jandiweb.de/file.apk"));
        assertFalse(UpdateVerifier.isAllowedApkUrl("https://evil.example/malware.apk"));
        assertFalse(UpdateVerifier.isAllowedApkUrl("https://eviljandiweb.de/file.apk"));
        assertFalse(UpdateVerifier.isAllowedApkUrl(null));
        assertFalse(UpdateVerifier.isAllowedApkUrl("not-a-url"));
    }
}

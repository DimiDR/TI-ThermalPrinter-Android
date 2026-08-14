package com.dantsu.thermalprinter.helpClasses;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

    @Test
    public void acceptsValidSha256Only() {
        assertTrue(UpdateVerifier.isValidSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        assertFalse(UpdateVerifier.isValidSha256("short"));
        assertFalse(UpdateVerifier.isValidSha256(""));
        assertFalse(UpdateVerifier.isValidSha256(null));
    }

    @Test
    public void hashesMatchIsConstantTimeEquality() {
        String hash = UpdateVerifier.sha256Hex("hello".getBytes(StandardCharsets.UTF_8));
        assertTrue(UpdateVerifier.hashesMatch(hash, hash.toUpperCase()));
        assertFalse(UpdateVerifier.hashesMatch(hash, "0".repeat(64)));
    }

    @Test
    public void versionCompareDetectsNewer() {
        assertTrue(UpdateVerifier.isNewVersionAvailable("1.1.0", "1.2.0"));
        assertTrue(UpdateVerifier.isNewVersionAvailable("1.1.0", "1.1.1"));
        assertFalse(UpdateVerifier.isNewVersionAvailable("1.1.0", "1.1.0"));
        assertFalse(UpdateVerifier.isNewVersionAvailable("1.2.0", "1.1.9"));
        assertFalse(UpdateVerifier.isNewVersionAvailable("1.1.0", "not.a.version"));
    }

    @Test
    public void parseUpdateJsonRequiresHashAndNewerVersion() throws Exception {
        JSONObject valid = new JSONObject()
                .put("app_version", "9.9.9")
                .put("apk_file", "https://app-version.jandiweb.de/app.apk")
                .put("apk_sha256", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        UpdateVerifier.UpdateInfo info = UpdateVerifier.parseUpdateJson(valid.toString(), "1.1.0");
        assertNotNull(info);
        assertEquals("9.9.9", info.version);

        JSONObject missingHash = new JSONObject()
                .put("app_version", "9.9.9")
                .put("apk_file", "https://app-version.jandiweb.de/app.apk");
        assertNull(UpdateVerifier.parseUpdateJson(missingHash.toString(), "1.1.0"));

        JSONObject sameVersion = new JSONObject()
                .put("app_version", "1.1.0")
                .put("apk_file", "https://app-version.jandiweb.de/app.apk")
                .put("apk_sha256", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertNull(UpdateVerifier.parseUpdateJson(sameVersion.toString(), "1.1.0"));
    }
}

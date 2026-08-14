package com.dantsu.thermalprinter.helpClasses;

import com.dantsu.thermalprinter.Constants;

import org.json.JSONObject;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class UpdateVerifier {
    private UpdateVerifier() {}

    public static final class UpdateInfo {
        public final String version;
        public final String apkUrl;
        public final String sha256;

        public UpdateInfo(String version, String apkUrl, String sha256) {
            this.version = version;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
        }
    }

    public static boolean isAllowedApkUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }
        try {
            URL url = new URL(urlString.trim());
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                return false;
            }
            String host = url.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            for (String allowed : Constants.UPDATE_ALLOWED_HOSTS) {
                String needle = allowed.toLowerCase(Locale.ROOT);
                if (host.equals(needle) || host.endsWith("." + needle)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidSha256(String hex) {
        if (hex == null) {
            return false;
        }
        String normalized = hex.trim();
        return normalized.matches("(?i)^[a-f0-9]{64}$");
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return toHex(hash);
        } catch (Exception e) {
            return "";
        }
    }

    public static String sha256File(java.io.File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream in = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (Exception e) {
            return "";
        }
    }

    private static String toHex(byte[] hash) {
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    public static boolean hashesMatch(String expectedHex, String actualHex) {
        if (!isValidSha256(expectedHex) || !isValidSha256(actualHex)) {
            return false;
        }
        byte[] expected = expectedHex.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = actualHex.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        if (expected.length != actual.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expected.length; i++) {
            diff |= expected[i] ^ actual[i];
        }
        return diff == 0;
    }

    public static boolean isNewVersionAvailable(String currentAppVersion, String appVersion) {
        if (currentAppVersion == null || appVersion == null) {
            return false;
        }
        String[] currentVersionParts = currentAppVersion.split("\\.");
        String[] newVersionParts = appVersion.split("\\.");
        int length = Math.max(currentVersionParts.length, newVersionParts.length);
        try {
            for (int i = 0; i < length; i++) {
                int currentPart = i < currentVersionParts.length ? Integer.parseInt(currentVersionParts[i]) : 0;
                int newPart = i < newVersionParts.length ? Integer.parseInt(newVersionParts[i]) : 0;
                if (currentPart < newPart) {
                    return true;
                } else if (currentPart > newPart) {
                    return false;
                }
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static UpdateInfo parseUpdateJson(String json, String currentVersion) {
        try {
            JSONObject object = new JSONObject(json);
            String appVersion = object.getString("app_version");
            String apkUrl = object.getString("apk_file");
            String sha256 = object.optString("apk_sha256", "");
            if (!isNewVersionAvailable(currentVersion, appVersion)) {
                return null;
            }
            if (!isAllowedApkUrl(apkUrl) || !isValidSha256(sha256)) {
                return null;
            }
            return new UpdateInfo(appVersion, apkUrl, sha256.trim().toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}

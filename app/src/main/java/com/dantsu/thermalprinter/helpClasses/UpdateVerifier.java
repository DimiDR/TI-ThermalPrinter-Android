package com.dantsu.thermalprinter.helpClasses;

import com.dantsu.thermalprinter.Constants;

import java.net.URL;
import java.util.Locale;

public final class UpdateVerifier {
    private UpdateVerifier() {}

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
}

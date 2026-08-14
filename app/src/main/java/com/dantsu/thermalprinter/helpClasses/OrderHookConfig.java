package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

/**
 * Per-device webhook identity. TastyIgniter POSTs to the ntfy URL;
 * this tablet subscribes to the same topic and reacts immediately.
 */
public final class OrderHookConfig {
    private static final String PREFS = "MyPrefs";
    private static final String KEY_TOPIC = "order_hook_topic";
    static final String NTFY_BASE = "https://ntfy.sh";
    static final String TOPIC_PREFIX = "jwprinter_";
    private static final int TOPIC_RANDOM_BYTES = 16;

    private OrderHookConfig() {}

    public static String getOrCreateTopic(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String topic = prefs.getString(KEY_TOPIC, null);
        if (isValidTopic(topic)) {
            return topic;
        }
        topic = TOPIC_PREFIX + randomHex(TOPIC_RANDOM_BYTES);
        prefs.edit().putString(KEY_TOPIC, topic).apply();
        return topic;
    }

    public static String webhookUrl(Context context) {
        return webhookUrlForTopic(getOrCreateTopic(context));
    }

    public static String subscribeUrl(Context context) {
        return webhookUrl(context) + "/json";
    }

    public static String webhookUrlForTopic(String topic) {
        return NTFY_BASE + "/" + topic;
    }

    public static boolean isValidTopic(String topic) {
        return topic != null && topic.matches("^jwprinter_[a-f0-9]{32}$");
    }

    static String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(byteCount * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

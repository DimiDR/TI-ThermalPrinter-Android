package com.dantsu.thermalprinter.helpClasses;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OrderHookConfigTest {

    @Test
    public void acceptsGeneratedTopicShape() {
        String topic = OrderHookConfig.TOPIC_PREFIX + OrderHookConfig.randomHex(16);
        assertTrue(OrderHookConfig.isValidTopic(topic));
        assertEquals("https://ntfy.sh/" + topic, OrderHookConfig.webhookUrlForTopic(topic));
    }

    @Test
    public void rejectsGuessableOrEmptyTopics() {
        assertFalse(OrderHookConfig.isValidTopic(null));
        assertFalse(OrderHookConfig.isValidTopic(""));
        assertFalse(OrderHookConfig.isValidTopic("orders"));
        assertFalse(OrderHookConfig.isValidTopic("jwprinter_abc"));
        assertFalse(OrderHookConfig.isValidTopic("jwprinter_" + "g".repeat(32)));
    }
}

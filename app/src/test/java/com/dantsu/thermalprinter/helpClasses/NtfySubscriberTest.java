package com.dantsu.thermalprinter.helpClasses;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NtfySubscriberTest {

    @Test
    public void onlyMessageEventsTriggerTheApp() {
        assertTrue(NtfySubscriber.isOrderHookEvent(
                "{\"id\":\"a\",\"time\":1,\"event\":\"message\",\"topic\":\"t\",\"message\":\"new_order\"}"));
        assertFalse(NtfySubscriber.isOrderHookEvent(
                "{\"id\":\"a\",\"time\":1,\"event\":\"open\",\"topic\":\"t\"}"));
        assertFalse(NtfySubscriber.isOrderHookEvent(
                "{\"id\":\"a\",\"time\":1,\"event\":\"keepalive\",\"topic\":\"t\"}"));
        assertFalse(NtfySubscriber.isOrderHookEvent(""));
        assertFalse(NtfySubscriber.isOrderHookEvent("not-json"));
        assertFalse(NtfySubscriber.isOrderHookEvent(null));
    }
}

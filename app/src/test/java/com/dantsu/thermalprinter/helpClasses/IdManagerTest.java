package com.dantsu.thermalprinter.helpClasses;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IdManagerTest {

    @Test
    public void snapshot_isIndependentCopy() {
        Set<String> stored = new HashSet<>();
        stored.add("42");

        Set<String> snapshot = IdManager.snapshot(stored);
        snapshot.add("99");
        snapshot.remove("42");

        assertTrue(stored.contains("42"));
        assertFalse(stored.contains("99"));
        assertEquals(1, stored.size());
    }

    @Test
    public void snapshot_nullBecomesEmptySet() {
        Set<String> snapshot = IdManager.snapshot(null);
        assertTrue(snapshot.isEmpty());
        snapshot.add("1");
        assertEquals(1, snapshot.size());
    }
}

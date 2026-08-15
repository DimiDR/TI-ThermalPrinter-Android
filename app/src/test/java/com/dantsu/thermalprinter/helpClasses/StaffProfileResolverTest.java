package com.dantsu.thermalprinter.helpClasses;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StaffProfileResolverTest {

    private static final String SAMPLE = "{"
            + "\"data\":[{"
            + "\"type\":\"users\",\"id\":\"1\","
            + "\"attributes\":{"
            + "\"name\":\"Dimitri\",\"email\":\"dima_rybak@yahoo.de\","
            + "\"username\":\"admin\",\"super_user\":true,\"full_name\":\"Dimitri\""
            + "},"
            + "\"relationships\":{\"role\":{\"data\":{\"type\":\"role\",\"id\":\"1\"}}}"
            + "}],"
            + "\"included\":[{"
            + "\"type\":\"role\",\"id\":\"1\","
            + "\"attributes\":{\"name\":\"Owner\",\"code\":\"owner\"}"
            + "}]"
            + "}";

    @Test
    public void resolvesByEmail() {
        StaffProfileResolver.StaffProfile profile =
                StaffProfileResolver.resolve(SAMPLE, "dima_rybak@yahoo.de");
        assertEquals("Dimitri", profile.primaryIdentity());
        assertEquals("admin", profile.username);
        assertEquals("Owner · Super Admin", profile.roleLabel());
        assertTrue(profile.superUser);
    }

    @Test
    public void resolvesByUsername() {
        StaffProfileResolver.StaffProfile profile =
                StaffProfileResolver.resolve(SAMPLE, "admin");
        assertEquals("Owner · Super Admin", profile.roleLabel());
        assertEquals("dima_rybak@yahoo.de", profile.email);
    }
}

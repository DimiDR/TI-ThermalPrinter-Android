package com.dantsu.thermalprinter.helpClasses;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NetworkHelperLoginFieldTest {
    @Test
    public void plainUsernameUsesUsernameField() {
        assertEquals("username", NetworkHelper.loginFieldFor("admprimavera2"));
    }

    @Test
    public void emailUsesEmailField() {
        assertEquals("email", NetworkHelper.loginFieldFor("chef@primavera-pizza-wickede.de"));
    }

    @Test
    public void nullFallsBackToUsername() {
        assertEquals("username", NetworkHelper.loginFieldFor(null));
    }
}

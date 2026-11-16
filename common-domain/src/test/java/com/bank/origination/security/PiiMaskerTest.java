package com.bank.origination.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {
    @Test
    void masksSsnBirthDateAndBearerToken() {
        String masked = PiiMasker.mask("SSN 123456789 DOB 1988-04-03 Bearer eyJhbGciOiJIUzI1NiJ9.abc.xyz");

        assertTrue(masked.contains("***-**-****"));
        assertTrue(masked.contains("[DATE REDACTED]"));
        assertTrue(masked.contains("Bearer [REDACTED]"));
        assertFalse(masked.contains("123456789"));
        assertFalse(masked.contains("1988-04-03"));
        assertFalse(masked.contains("eyJhbGciOiJIUzI1NiJ9"));
    }
}

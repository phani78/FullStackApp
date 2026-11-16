package com.bank.origination.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MockExternalAdaptersTest {
    private final MockExternalAdapters adapters = new MockExternalAdapters();

    @Test
    void deterministicPersonasCoverPrimeThinFileAndSensitiveValidation() {
        LocalDate syntheticDob = LocalDate.of(1990, 1, 2);

        var prime = adapters.validate("900000001", syntheticDob, "100 Test Street", "10000", MockPersona.PRIME);
        var thin = adapters.validate("900000002", syntheticDob, "100 Test Street", "10000", MockPersona.THIN_FILE);
        var sanctions = adapters.validate("900000003", syntheticDob, "100 Test Street", "10000", MockPersona.OFAC_MATCH);

        assertEquals(780, prime.score());
        assertNull(thin.score());
        assertTrue(sanctions.sensitive());
        assertEquals("SANCTIONS_POTENTIAL_MATCH", sanctions.category());
    }

    @Test
    void bureauMockUsesIdentityInputsForUnspecifiedPersona() {
        LocalDate firstDob = LocalDate.of(1990, 1, 2);
        LocalDate secondDob = LocalDate.of(1990, 1, 3);

        var first = adapters.validate("900000001", firstDob, "100 Test Street", "10000", null);
        var second = adapters.validate("900000002", secondDob, "100 Test Street", "10000", null);

        assertTrue(first.score() >= 620 && first.score() <= 769);
        assertTrue(second.score() >= 620 && second.score() <= 769);
    }
}

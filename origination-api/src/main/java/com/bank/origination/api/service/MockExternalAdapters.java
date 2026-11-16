package com.bank.origination.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockExternalAdapters {
    @Value("${app.mock.latency-ms:25}")
    private long latencyMs;
    @Value("${app.mock.failure-rate:0}")
    private double failureRate;

    public ValidationResult validate(String ssn, LocalDate dob, String addressLine, String postalCode, MockPersona persona) {
        simulateLatency();
        if (persona == MockPersona.ADAPTER_FAILURE || ThreadLocalRandom.current().nextDouble() < failureRate) {
            return ValidationResult.sensitive("ADAPTER_UNAVAILABLE");
        }
        if (persona == MockPersona.INVALID_ADDRESS || addressLine.toLowerCase().contains("invalid")) {
            return ValidationResult.sensitive("ADDRESS_UNVERIFIABLE");
        }
        if (persona == MockPersona.KYC_MISMATCH) {
            return ValidationResult.sensitive("IDENTITY_MISMATCH");
        }
        if (persona == MockPersona.OFAC_MATCH) {
            return ValidationResult.sensitive("SANCTIONS_POTENTIAL_MATCH");
        }
        if (persona == MockPersona.FRAUD_ALERT) {
            return ValidationResult.sensitive("FRAUD_ALERT");
        }

        BureauResult bureau = switch (persona) {
            case null -> scoreFromIdentity(ssn, dob);
            case THIN_FILE -> new BureauResult(null, "THIN", false);
            case LOW_SCORE -> new BureauResult(520, "THICK", false);
            case PRIME -> new BureauResult(780, "THICK", false);
            default -> scoreFromIdentity(ssn, dob);
        };
        return new ValidationResult(false, "CLEARED", bureau.score(), bureau.fileType(), bureau.fraudAlert());
    }

    private BureauResult scoreFromIdentity(String ssn, LocalDate dob) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((ssn + "|" + dob).getBytes(StandardCharsets.UTF_8));
            int score = 620 + (Math.floorMod(digest[0], 150));
            return new BureauResult(score, "THICK", false);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
        }
    }

    private void simulateLatency() {
        if (latencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(Math.min(latencyMs, 5000));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalAdapterException();
        }
    }

    public record ValidationResult(
            boolean sensitive, String category, Integer score, String fileType, boolean fraudAlert) {
        static ValidationResult sensitive(String category) {
            return new ValidationResult(true, category, null, null, false);
        }
    }

    private record BureauResult(Integer score, String fileType, boolean fraudAlert) {}

    public static final class ExternalAdapterException extends RuntimeException {
        public ExternalAdapterException() {
            super("A mock external adapter failed.");
        }
    }
}

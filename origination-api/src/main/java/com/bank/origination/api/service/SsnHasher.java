package com.bank.origination.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SsnHasher {
    private final byte[] salt;

    public SsnHasher(@Value("${app.security.ssn-hash-salt}") String salt) {
        if (salt.length() < 32) {
            throw new IllegalArgumentException("SSN_HASH_SALT must contain at least 32 characters.");
        }
        this.salt = salt.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String ssn) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return HexFormat.of().formatHex(digest.digest(ssn.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
        }
    }
}

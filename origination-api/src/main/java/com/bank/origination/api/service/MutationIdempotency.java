package com.bank.origination.api.service;

import com.bank.origination.api.persistence.IdempotencyRecordEntity;
import com.bank.origination.api.persistence.IdempotencyRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MutationIdempotency {
    private final IdempotencyRecordRepository repository;
    private final SsnHasher hasher;
    private final ObjectMapper objectMapper;

    public MutationIdempotency(
            IdempotencyRecordRepository repository, SsnHasher hasher, ObjectMapper objectMapper) {
        this.repository = repository;
        this.hasher = hasher;
        this.objectMapper = objectMapper;
    }

    public boolean begin(String principal, String key, Object request) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,160}")) {
            throw new OriginationException(HttpStatus.BAD_REQUEST, "A valid X-Idempotency-Key header is required.");
        }
        String requestHash;
        try {
            requestHash = hasher.hash(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to fingerprint mutation.", exception);
        }
        var existing = repository.findById(new IdempotencyRecordEntity.Key(principal, key));
        OffsetDateTime now = OffsetDateTime.now();
        if (existing.isPresent() && existing.get().getExpiresAt().isAfter(now)) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new OriginationException(HttpStatus.CONFLICT, "The idempotency key was already used for a different request.");
            }
            return false;
        }
        existing.ifPresent(repository::delete);
        repository.save(new IdempotencyRecordEntity(
                principal, key, requestHash, 200, "{\"result\":\"APPLIED\"}", now, now.plusDays(1)));
        return true;
    }
}

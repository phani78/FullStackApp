package com.bank.origination.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_record")
@IdClass(IdempotencyRecordEntity.Key.class)
public class IdempotencyRecordEntity {
    @Id
    @Column(name = "principal_id", length = 160)
    private String principalId;
    @Id
    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(name = "response_status", nullable = false)
    private int responseStatus;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body_redacted", nullable = false, columnDefinition = "jsonb")
    private String responseBodyRedacted;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected IdempotencyRecordEntity() {}

    public IdempotencyRecordEntity(String principalId, String idempotencyKey, String requestHash,
            int responseStatus, String responseBodyRedacted, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        this.principalId = principalId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBodyRedacted = responseBodyRedacted;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getRequestHash() { return requestHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public String getResponseBodyRedacted() { return responseBodyRedacted; }

    public static class Key implements Serializable {
        private String principalId;
        private String idempotencyKey;
        public Key() {}
        public Key(String principalId, String idempotencyKey) {
            this.principalId = principalId;
            this.idempotencyKey = idempotencyKey;
        }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(principalId, key.principalId) && Objects.equals(idempotencyKey, key.idempotencyKey);
        }
        @Override public int hashCode() { return Objects.hash(principalId, idempotencyKey); }
    }
}

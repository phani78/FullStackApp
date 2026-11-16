package com.bank.origination.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_record")
public class AuditRecordEntity {
    @Id
    private UUID id;
    @Column(name = "actor_id", nullable = false, length = 160)
    private String actorId;
    @Column(nullable = false, length = 100)
    private String action;
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;
    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "change_summary_redacted", nullable = false, columnDefinition = "jsonb")
    private String changeSummaryRedacted;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AuditRecordEntity() {}

    public AuditRecordEntity(UUID id, String actorId, String action, String entityType, UUID entityId,
            String correlationId, String summary, OffsetDateTime createdAt) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.correlationId = correlationId;
        this.changeSummaryRedacted = summary;
        this.createdAt = createdAt;
    }
}

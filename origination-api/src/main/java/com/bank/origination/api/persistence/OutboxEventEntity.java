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
@Table(name = "outbox_event")
public class OutboxEventEntity {
    @Id
    private UUID eventId;
    @Column(nullable = false, length = 200)
    private String topic;
    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected OutboxEventEntity() {}

    public OutboxEventEntity(UUID eventId, String topic, String eventKey, String payload,
            String correlationId, OffsetDateTime createdAt) {
        this.eventId = eventId;
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }

    public UUID getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public String getEventKey() { return eventKey; }
    public String getPayload() { return payload; }
    public String getCorrelationId() { return correlationId; }
    public void markPublished(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
}

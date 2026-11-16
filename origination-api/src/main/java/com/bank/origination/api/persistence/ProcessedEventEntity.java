package com.bank.origination.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "processed_event")
@IdClass(ProcessedEventEntity.Key.class)
public class ProcessedEventEntity {
    @Id
    @Column(name = "consumer_name", length = 100)
    private String consumerName;
    @Id
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected ProcessedEventEntity() {}

    public ProcessedEventEntity(String consumerName, UUID eventId, OffsetDateTime processedAt) {
        this.consumerName = consumerName;
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public static class Key implements Serializable {
        private String consumerName;
        private UUID eventId;

        public Key() {}
        public Key(String consumerName, UUID eventId) {
            this.consumerName = consumerName;
            this.eventId = eventId;
        }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(consumerName, key.consumerName) && Objects.equals(eventId, key.eventId);
        }
        @Override public int hashCode() { return Objects.hash(consumerName, eventId); }
    }
}

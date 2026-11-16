package com.bank.origination.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "underwriter_assignment")
public class UnderwriterAssignmentEntity {
    @Id
    private UUID id;
    @Column(name = "application_id", nullable = false)
    private UUID applicationId;
    @Column(name = "assigned_to", nullable = false, length = 120)
    private String assignedTo;
    @Column(name = "queue_status", nullable = false, length = 32)
    private String queueStatus;
    @Column(name = "notes_restricted")
    private String notesRestricted;
    @Column(name = "proposed_outcome", length = 32)
    private String proposedOutcome;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposed_reason_codes", columnDefinition = "jsonb")
    private String proposedReasonCodes;
    @Column(name = "proposed_by", length = 120)
    private String proposedBy;
    @Column(name = "override_pending", nullable = false)
    private boolean overridePending;
    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;
    @Version
    private long version;

    protected UnderwriterAssignmentEntity() {}

    public UnderwriterAssignmentEntity(UUID id, UUID applicationId, String assignedTo, OffsetDateTime assignedAt) {
        this.id = id;
        this.applicationId = applicationId;
        this.assignedTo = assignedTo;
        this.queueStatus = "OPEN";
        this.assignedAt = assignedAt;
    }

    public UUID getApplicationId() { return applicationId; }
    public String getAssignedTo() { return assignedTo; }
    public String getQueueStatus() { return queueStatus; }
    public String getNotesRestricted() { return notesRestricted; }
    public String getProposedOutcome() { return proposedOutcome; }
    public String getProposedReasonCodes() { return proposedReasonCodes; }
    public String getProposedBy() { return proposedBy; }
    public boolean isOverridePending() { return overridePending; }
    public void appendNote(String note) {
        this.notesRestricted = notesRestricted == null || notesRestricted.isBlank()
                ? note : notesRestricted + "\n" + note;
    }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public void propose(String outcome, String reasons, String proposedBy) {
        this.proposedOutcome = outcome;
        this.proposedReasonCodes = reasons;
        this.proposedBy = proposedBy;
        this.overridePending = true;
        this.queueStatus = "PENDING_SUPERVISOR";
    }
    public void resolveProposal() {
        this.overridePending = false;
        this.queueStatus = "CLOSED";
    }
}

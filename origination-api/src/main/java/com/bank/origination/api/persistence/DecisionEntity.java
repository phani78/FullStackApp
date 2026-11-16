package com.bank.origination.api.persistence;

import com.bank.origination.domain.DecisionOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "decision")
public class DecisionEntity {
    @Id
    private UUID id;
    @Column(name = "application_id", nullable = false)
    private UUID applicationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DecisionOutcome outcome;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "internal_reason_codes", nullable = false, columnDefinition = "jsonb")
    private String internalReasonCodes;
    @Column(name = "fico_score")
    private Integer ficoScore;
    @Column(name = "dti_ratio")
    private BigDecimal dtiRatio;
    @Column(name = "approved_limit")
    private BigDecimal approvedLimit;
    @Column(name = "assigned_apr")
    private BigDecimal assignedApr;
    @Column(name = "policy_version", nullable = false, length = 80)
    private String policyVersion;
    @Column(name = "decided_by", nullable = false, length = 120)
    private String decidedBy;
    @Column(name = "decided_at", nullable = false)
    private OffsetDateTime decidedAt;
    @Version
    private long version;

    protected DecisionEntity() {}

    public DecisionEntity(UUID id, UUID applicationId, DecisionOutcome outcome, String reasons,
            Integer score, BigDecimal dti, BigDecimal limit, BigDecimal apr, String policyVersion,
            String decidedBy, OffsetDateTime decidedAt) {
        this.id = id;
        this.applicationId = applicationId;
        this.outcome = outcome;
        this.internalReasonCodes = reasons;
        this.ficoScore = score;
        this.dtiRatio = dti;
        this.approvedLimit = limit;
        this.assignedApr = apr;
        this.policyVersion = policyVersion;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }
}

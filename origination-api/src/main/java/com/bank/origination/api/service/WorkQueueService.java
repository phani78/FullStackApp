package com.bank.origination.api.service;

import com.bank.origination.api.persistence.AuditRecordEntity;
import com.bank.origination.api.persistence.AuditRecordRepository;
import com.bank.origination.api.persistence.CreditApplicationEntity;
import com.bank.origination.api.persistence.CreditApplicationRepository;
import com.bank.origination.api.persistence.CardProductRepository;
import com.bank.origination.api.persistence.UnderwriterAssignmentEntity;
import com.bank.origination.api.persistence.UnderwriterAssignmentRepository;
import com.bank.origination.api.persistence.SensitiveValidationRepository;
import com.bank.origination.api.persistence.SensitiveValidationEntity;
import com.bank.origination.domain.ApiModels.ReviewQueueItem;
import com.bank.origination.domain.ApiModels.SensitiveReviewItem;
import com.bank.origination.domain.ApplicationStatus;
import com.bank.origination.security.PiiMasker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkQueueService {
    private final CreditApplicationRepository applications;
    private final UnderwriterAssignmentRepository assignments;
    private final CardProductRepository products;
    private final AuditRecordRepository audit;
    private final ObjectMapper objectMapper;
    private final MutationIdempotency idempotency;
    private final SensitiveValidationRepository sensitiveValidations;

    public WorkQueueService(
            CreditApplicationRepository applications,
            CardProductRepository products,
            UnderwriterAssignmentRepository assignments,
            AuditRecordRepository audit,
            ObjectMapper objectMapper,
            MutationIdempotency idempotency,
            SensitiveValidationRepository sensitiveValidations) {
        this.applications = applications;
        this.products = products;
        this.assignments = assignments;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.idempotency = idempotency;
        this.sensitiveValidations = sensitiveValidations;
    }

    @Transactional(readOnly = true)
    public List<ReviewQueueItem> getQueue() {
        return applications.findTop100ByStatusInOrderBySubmittedAtAsc(List.of(ApplicationStatus.MANUAL_REVIEW))
                .stream().map(application -> new ReviewQueueItem(
                        application.getId(),
                        application.getApplicantFirstName() + " " + application.getApplicantLastName(),
                        products.findById(application.getProductId()).map(product -> product.getProductCode()).orElse("UNKNOWN"),
                        application.getStatus(),
                        application.getSsnLast4(),
                        application.getAnnualIncome(),
                        application.getMonthlyHousingPayment(),
                        application.getMonthlyDebtPayment(),
                        application.getBureauScore(),
                        "CLEARED".equals(application.getAddressValidationStatus()) ? "CLEARED" : "REVIEW",
                        application.getSubmittedAt()))
                .toList();
    }

    @Transactional
    public List<SensitiveReviewItem> getSensitiveQueue(String supervisor, String correlationId) {
        List<SensitiveValidationEntity> cases = sensitiveValidations.findAllByOrderByCreatedAtAsc();
        for (SensitiveValidationEntity sensitive : cases) {
            recordAudit(supervisor, "SENSITIVE_VALIDATION_ACCESSED", sensitive.getApplicationId(), correlationId,
                    "{\"category\":\"" + sensitive.getValidationCategory() + "\"}");
        }
        return cases.stream().map(sensitive -> new SensitiveReviewItem(
                sensitive.getApplicationId(), sensitive.getValidationCategory(),
                sensitive.getProviderReference(), sensitive.getCreatedAt())).toList();
    }

    @Transactional
    public void claim(UUID applicationId, String actor, String correlationId, String idempotencyKey) {
        if (!idempotency.begin(actor, idempotencyKey, "claim:" + applicationId)) {
            return;
        }
        CreditApplicationEntity application = findReview(applicationId);
        UnderwriterAssignmentEntity assignment = findAssignment(applicationId);
        if (!"unassigned".equals(assignment.getAssignedTo()) && !assignment.getAssignedTo().equals(actor)) {
            throw new OriginationException(HttpStatus.CONFLICT, "Application is assigned to another underwriter.");
        }
        assignment.setAssignedTo(actor);
        recordAudit(actor, "APPLICATION_CLAIMED", applicationId, correlationId, "{}");
    }

    @Transactional
    public void addNote(UUID applicationId, String actor, String note, String correlationId, String idempotencyKey) {
        if (!idempotency.begin(actor, idempotencyKey, "note:" + applicationId + ":" + note)) {
            return;
        }
        findReview(applicationId);
        UnderwriterAssignmentEntity assignment = findAssignment(applicationId);
        if (!"unassigned".equals(assignment.getAssignedTo()) && !assignment.getAssignedTo().equals(actor)) {
            throw new OriginationException(HttpStatus.FORBIDDEN, "Application is assigned to another underwriter.");
        }
        String sanitized = PiiMasker.mask(note);
        assignment.appendNote(sanitized);
        recordAudit(actor, "REVIEW_NOTE_ADDED", applicationId, correlationId, "{\"note\":\"REDACTED\"}");
    }

    private CreditApplicationEntity findReview(UUID applicationId) {
        CreditApplicationEntity application = applications.findById(applicationId)
                .orElseThrow(() -> new OriginationException(HttpStatus.NOT_FOUND, "Application not found."));
        if (application.getStatus() != ApplicationStatus.MANUAL_REVIEW) {
            throw new OriginationException(HttpStatus.CONFLICT, "Application is not awaiting manual review.");
        }
        return application;
    }

    private UnderwriterAssignmentEntity findAssignment(UUID applicationId) {
        return assignments.findFirstByApplicationIdOrderByAssignedAtDesc(applicationId)
                .orElseThrow(() -> new IllegalStateException("Manual-review assignment is missing."));
    }

    private void recordAudit(String actor, String action, UUID applicationId, String correlationId, String summary) {
        audit.save(new AuditRecordEntity(
                UUID.randomUUID(), actor, action, "APPLICATION", applicationId, correlationId, summary, OffsetDateTime.now()));
    }
}

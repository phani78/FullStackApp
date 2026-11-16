package com.bank.origination.api.service;

import com.bank.origination.api.persistence.AuditRecordEntity;
import com.bank.origination.api.persistence.AuditRecordRepository;
import com.bank.origination.api.persistence.CardProductEntity;
import com.bank.origination.api.persistence.CardProductRepository;
import com.bank.origination.api.persistence.CreditApplicationEntity;
import com.bank.origination.api.persistence.CreditApplicationRepository;
import com.bank.origination.api.persistence.DecisionEntity;
import com.bank.origination.api.persistence.DecisionRepository;
import com.bank.origination.api.persistence.OutboxEventEntity;
import com.bank.origination.api.persistence.OutboxEventRepository;
import com.bank.origination.api.persistence.ProcessedEventEntity;
import com.bank.origination.api.persistence.ProcessedEventRepository;
import com.bank.origination.api.persistence.UnderwriterAssignmentEntity;
import com.bank.origination.api.persistence.UnderwriterAssignmentRepository;
import com.bank.origination.domain.ApiModels.ReviewDecisionRequest;
import com.bank.origination.domain.ApplicationEvent;
import com.bank.origination.domain.ApplicationStatus;
import com.bank.origination.domain.DecisionOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnderwritingService {
    private static final String CONSUMER = "application-underwriting-v1";
    private final ProcessedEventRepository processedEvents;
    private final CreditApplicationRepository applications;
    private final CardProductRepository products;
    private final DecisionRepository decisions;
    private final UnderwriterAssignmentRepository assignments;
    private final OutboxEventRepository outbox;
    private final AuditRecordRepository audit;
    private final ObjectMapper objectMapper;
    private final MutationIdempotency idempotency;

    public UnderwritingService(
            ProcessedEventRepository processedEvents,
            CreditApplicationRepository applications,
            CardProductRepository products,
            DecisionRepository decisions,
            UnderwriterAssignmentRepository assignments,
            OutboxEventRepository outbox,
            AuditRecordRepository audit,
            ObjectMapper objectMapper,
            MutationIdempotency idempotency) {
        this.processedEvents = processedEvents;
        this.applications = applications;
        this.products = products;
        this.decisions = decisions;
        this.assignments = assignments;
        this.outbox = outbox;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.idempotency = idempotency;
    }

    @Transactional
    public void process(ApplicationEvent event) {
        ProcessedEventEntity.Key key = new ProcessedEventEntity.Key(CONSUMER, event.eventId());
        if (processedEvents.existsById(key)) {
            return;
        }
        CreditApplicationEntity application = applications.findById(event.applicationId())
                .orElseThrow(() -> new IllegalStateException("Application referenced by event was not found."));
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            processedEvents.save(new ProcessedEventEntity(CONSUMER, event.eventId(), OffsetDateTime.now()));
            return;
        }
        CardProductEntity product = products.findById(application.getProductId())
                .orElseThrow(() -> new IllegalStateException("Application product reference was not found."));
        BigDecimal monthlyIncome = application.getAnnualIncome().divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
        BigDecimal monthlyDebt = application.getMonthlyHousingPayment().add(application.getMonthlyDebtPayment());
        BigDecimal dti = monthlyIncome.signum() == 0
                ? BigDecimal.ONE
                : monthlyDebt.divide(monthlyIncome, 6, RoundingMode.HALF_UP);
        DecisionOutcome outcome = decide(application, product, dti);
        List<String> reasons = reasons(application, product, dti, outcome);
        BigDecimal proposedLimit = outcome == DecisionOutcome.APPROVED ? calculateLimit(application, product) : null;
        if (outcome == DecisionOutcome.APPROVED && proposedLimit.compareTo(product.getCreditLimitMin()) < 0) {
            outcome = DecisionOutcome.DECLINED;
            reasons = List.of("AFFORDABILITY_INSUFFICIENT");
            proposedLimit = null;
        }
        BigDecimal apr = outcome == DecisionOutcome.APPROVED ? assignedApr(application, product) : null;
        application.setStatus(switch (outcome) {
            case APPROVED -> ApplicationStatus.APPROVED;
            case DECLINED -> ApplicationStatus.DECLINED;
            case MANUAL_REVIEW -> ApplicationStatus.MANUAL_REVIEW;
            case SENSITIVE_VALIDATION -> ApplicationStatus.SENSITIVE_VALIDATION_REVIEW;
        });
        OffsetDateTime now = OffsetDateTime.now();
        if (outcome == DecisionOutcome.APPROVED) {
            application.setActivatedAt(now);
        }
        decisions.save(new DecisionEntity(
                UUID.randomUUID(), application.getId(), outcome, toJson(reasons),
                application.getBureauScore(), dti, proposedLimit, apr, "local-policy-v1", "SYSTEM", now));
        if (outcome == DecisionOutcome.MANUAL_REVIEW) {
            assignments.save(new UnderwriterAssignmentEntity(
                    UUID.randomUUID(), application.getId(), "unassigned", now));
        }
        audit.save(new AuditRecordEntity(
                UUID.randomUUID(), "SYSTEM", "UNDERWRITING_COMPLETED", "APPLICATION", application.getId(),
                event.correlationId(), "{\"outcome\":\"" + outcome + "\"}", now));
        ApplicationEvent decisionEvent = new ApplicationEvent(
                UUID.randomUUID(), "ApplicationDecisionReady", 1, application.getId(), event.correlationId(), now, null);
        outbox.save(new OutboxEventEntity(
                decisionEvent.eventId(), "card.application.decision-ready.v1", application.getId().toString(),
                toJson(decisionEvent), event.correlationId(), now));
        processedEvents.save(new ProcessedEventEntity(CONSUMER, event.eventId(), now));
    }

    private DecisionOutcome decide(CreditApplicationEntity application, CardProductEntity product, BigDecimal dti) {
        if (application.hasBureauFraudAlert()) {
            return DecisionOutcome.SENSITIVE_VALIDATION;
        }
        if (application.getBureauScore() == null) {
            return DecisionOutcome.MANUAL_REVIEW;
        }
        int score = application.getBureauScore();
        if (score < product.getMinimumFico() - 20
                || dti.compareTo(product.getMaximumDti().add(new BigDecimal("0.05"))) > 0) {
            return DecisionOutcome.DECLINED;
        }
        if (score < product.getMinimumFico()
                || dti.compareTo(product.getMaximumDti()) > 0) {
            return DecisionOutcome.MANUAL_REVIEW;
        }
        return DecisionOutcome.APPROVED;
    }

    private List<String> reasons(
            CreditApplicationEntity application, CardProductEntity product, BigDecimal dti, DecisionOutcome outcome) {
        if (outcome == DecisionOutcome.MANUAL_REVIEW) {
            return application.getBureauScore() == null ? List.of("THIN_FILE") : List.of("BORDERLINE_POLICY");
        }
        if (outcome == DecisionOutcome.DECLINED) {
            if (application.getBureauScore() != null
                    && application.getBureauScore() < product.getMinimumFico() - 20) {
                return List.of("LOW_CREDIT_SCORE");
            }
            if (dti.compareTo(product.getMaximumDti().add(new BigDecimal("0.05"))) > 0) {
                return List.of("DTI_ABOVE_POLICY");
            }
            return List.of("CREDIT_POLICY");
        }
        return List.of();
    }

    private BigDecimal calculateLimit(CreditApplicationEntity application, CardProductEntity product) {
        BigDecimal multiplier = switch (product.getProductCode()) {
            case "SECURED" -> new BigDecimal("0.20");
            case "CASHBACK" -> new BigDecimal("0.30");
            default -> new BigDecimal("0.35");
        };
        BigDecimal monthlyObligations = application.getMonthlyHousingPayment()
                .add(application.getMonthlyDebtPayment()).multiply(BigDecimal.valueOf(6));
        BigDecimal affordability = application.getAnnualIncome().multiply(multiplier).subtract(monthlyObligations);
        return affordability.max(BigDecimal.ZERO)
                .min(product.getCreditLimitMax()).setScale(2, RoundingMode.DOWN);
    }

    private BigDecimal assignedApr(CreditApplicationEntity application, CardProductEntity product) {
        if (application.getBureauScore() != null && application.getBureauScore() >= 760) {
            return product.getAprMin();
        }
        return product.getAprMin().add(product.getAprMax()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public void recordDecision(
            UUID applicationId, String actor, ReviewDecisionRequest request, String correlationId, String idempotencyKey) {
        if (!idempotency.begin(actor, idempotencyKey, "decision:" + applicationId + ":" + request)) {
            return;
        }
        if (request.outcome() != DecisionOutcome.APPROVED && request.outcome() != DecisionOutcome.DECLINED) {
            throw new OriginationException(HttpStatus.BAD_REQUEST, "A manual decision must approve or decline.");
        }
        CreditApplicationEntity application = applications.findById(applicationId)
                .orElseThrow(() -> new OriginationException(HttpStatus.NOT_FOUND, "Application not found."));
        if (application.getStatus() != ApplicationStatus.MANUAL_REVIEW) {
            throw new OriginationException(HttpStatus.CONFLICT, "Application is not awaiting manual review.");
        }
        UnderwriterAssignmentEntity assignment = assignments.findFirstByApplicationIdOrderByAssignedAtDesc(applicationId)
                .orElseThrow(() -> new IllegalStateException("Manual-review assignment is missing."));
        if (!"unassigned".equals(assignment.getAssignedTo()) && !assignment.getAssignedTo().equals(actor)) {
            throw new OriginationException(HttpStatus.FORBIDDEN, "Application is assigned to another underwriter.");
        }
        assignment.propose(request.outcome().name(), toJson(request.reasonCodes() == null ? List.of() : request.reasonCodes()), actor);
        if (request.note() != null && !request.note().isBlank()) {
            assignment.appendNote(request.note());
        }
        audit.save(new AuditRecordEntity(
                UUID.randomUUID(), actor, "DECISION_PROPOSED", "APPLICATION", applicationId,
                correlationId, "{\"outcome\":\"" + request.outcome() + "\"}", OffsetDateTime.now()));
    }

    @Transactional
    public void approveProposal(
            UUID applicationId, String supervisor, boolean approve, String correlationId, String idempotencyKey) {
        if (!idempotency.begin(supervisor, idempotencyKey, "approval:" + applicationId + ":" + approve)) {
            return;
        }
        UnderwriterAssignmentEntity assignment = assignments
                .findFirstByApplicationIdOrderByAssignedAtDesc(applicationId)
                .orElseThrow(() -> new OriginationException(HttpStatus.NOT_FOUND, "Review assignment not found."));
        if (!assignment.isOverridePending() || supervisor.equals(assignment.getProposedBy())) {
            throw new OriginationException(HttpStatus.CONFLICT, "A different supervisor must review a pending proposal.");
        }
        CreditApplicationEntity application = applications.findById(applicationId)
                .orElseThrow(() -> new OriginationException(HttpStatus.NOT_FOUND, "Application not found."));
        if (approve) {
            DecisionOutcome outcome = DecisionOutcome.valueOf(assignment.getProposedOutcome());
            OffsetDateTime now = OffsetDateTime.now();
            CardProductEntity product = products.findById(application.getProductId())
                    .orElseThrow(() -> new IllegalStateException("Application product reference was not found."));
            BigDecimal monthlyIncome = application.getAnnualIncome().divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
            BigDecimal monthlyDebt = application.getMonthlyHousingPayment().add(application.getMonthlyDebtPayment());
            BigDecimal dti = monthlyIncome.signum() == 0
                    ? BigDecimal.ONE
                    : monthlyDebt.divide(monthlyIncome, 6, RoundingMode.HALF_UP);
            BigDecimal approvedLimit = outcome == DecisionOutcome.APPROVED ? calculateLimit(application, product) : null;
            if (outcome == DecisionOutcome.APPROVED
                    && (approvedLimit.compareTo(product.getCreditLimitMin()) < 0
                    || dti.compareTo(product.getMaximumDti().add(new BigDecimal("0.05"))) > 0)) {
                throw new OriginationException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "The proposal does not satisfy the configured affordability limits.");
            }
            decisions.save(new DecisionEntity(UUID.randomUUID(), applicationId, outcome,
                    assignment.getProposedReasonCodes(), application.getBureauScore(), dti, approvedLimit,
                    outcome == DecisionOutcome.APPROVED ? assignedApr(application, product) : null,
                    "manual-review-v1", supervisor, now));
            application.setStatus(outcome == DecisionOutcome.APPROVED
                    ? ApplicationStatus.APPROVED : ApplicationStatus.DECLINED);
            if (outcome == DecisionOutcome.APPROVED) {
                application.setActivatedAt(now);
            }
            ApplicationEvent decisionEvent = new ApplicationEvent(
                    UUID.randomUUID(), "ApplicationDecisionReady", 1, applicationId, correlationId, now, null);
            outbox.save(new OutboxEventEntity(
                    decisionEvent.eventId(), "card.application.decision-ready.v1", applicationId.toString(),
                    toJson(decisionEvent), correlationId, now));
        }
        assignment.resolveProposal();
        audit.save(new AuditRecordEntity(
                UUID.randomUUID(), supervisor, approve ? "DECISION_APPROVED" : "DECISION_REJECTED",
                "APPLICATION", applicationId, correlationId, "{\"maker\":\"" + assignment.getProposedBy() + "\"}",
                OffsetDateTime.now()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize a redacted decision record.", exception);
        }
    }
}

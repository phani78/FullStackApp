package com.bank.origination.api.service;

import com.bank.origination.api.persistence.AuditRecordEntity;
import com.bank.origination.api.persistence.AuditRecordRepository;
import com.bank.origination.api.persistence.CardProductEntity;
import com.bank.origination.api.persistence.CardProductRepository;
import com.bank.origination.api.persistence.CreditApplicationEntity;
import com.bank.origination.api.persistence.CreditApplicationRepository;
import com.bank.origination.api.persistence.IdempotencyRecordEntity;
import com.bank.origination.api.persistence.IdempotencyRecordRepository;
import com.bank.origination.api.persistence.OutboxEventEntity;
import com.bank.origination.api.persistence.OutboxEventRepository;
import com.bank.origination.api.persistence.SensitiveValidationEntity;
import com.bank.origination.api.persistence.SensitiveValidationRepository;
import com.bank.origination.domain.ApiModels.ApplicationReceipt;
import com.bank.origination.domain.ApplicationEvent;
import com.bank.origination.domain.ApplicationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationSubmissionWriter {
    private static final String CUSTOMER_VALIDATION_MESSAGE =
            "Sorry, we ran into an issue while processing your application. Please call customer service at 1-800-XXX-XXXX.";
    private final CardProductRepository productRepository;
    private final CreditApplicationRepository applicationRepository;
    private final SensitiveValidationRepository sensitiveRepository;
    private final OutboxEventRepository outboxRepository;
    private final AuditRecordRepository auditRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final SsnHasher ssnHasher;
    private final ObjectMapper objectMapper;

    public ApplicationSubmissionWriter(
            CardProductRepository productRepository,
            CreditApplicationRepository applicationRepository,
            SensitiveValidationRepository sensitiveRepository,
            OutboxEventRepository outboxRepository,
            AuditRecordRepository auditRepository,
            IdempotencyRecordRepository idempotencyRepository,
            SsnHasher ssnHasher,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.applicationRepository = applicationRepository;
        this.sensitiveRepository = sensitiveRepository;
        this.outboxRepository = outboxRepository;
        this.auditRepository = auditRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.ssnHasher = ssnHasher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApplicationReceipt persist(
            com.bank.origination.domain.ApiModels.ApplicationRequest request,
            String requestHash,
            String idempotencyKey,
            String principal,
            String correlationId,
            String mockPersona,
            MockExternalAdapters.ValidationResult validation) {
        CardProductEntity product = productRepository.findByProductCodeAndActiveTrue(request.productCode())
                .orElseThrow(() -> new OriginationException(HttpStatus.BAD_REQUEST, "The selected product is unavailable."));
        OffsetDateTime now = OffsetDateTime.now();
        UUID applicationId = UUID.randomUUID();
        boolean sensitive = validation.sensitive();
        ApplicationStatus status = sensitive ? ApplicationStatus.SENSITIVE_VALIDATION_REVIEW : ApplicationStatus.SUBMITTED;
        String customerMessage = sensitive ? CUSTOMER_VALIDATION_MESSAGE : "Application received and is being processed.";
        CreditApplicationEntity application = new CreditApplicationEntity(
                applicationId,
                product.getId(),
                request.email().trim().toLowerCase(),
                request.firstName().trim(),
                request.lastName().trim(),
                ssnHasher.hash(request.ssn()),
                request.ssn().substring(request.ssn().length() - 4),
                request.annualIncome(),
                request.monthlyHousingPayment(),
                request.monthlyDebtPayment(),
                request.employmentStatus().trim(),
                request.city().trim(),
                request.state().trim().toUpperCase(),
                request.postalCode().trim(),
                validation.score(),
                validation.fileType(),
                validation.fraudAlert(),
                sensitive ? validation.category() : "CLEARED",
                status,
                customerMessage,
                correlationId,
                mockPersona,
                now,
                now.plusDays(30));
        applicationRepository.save(application);

        if (sensitive) {
            sensitiveRepository.save(new SensitiveValidationEntity(
                    UUID.randomUUID(), applicationId, validation.category(), UUID.randomUUID().toString(),
                    "RESTRICTED_VALIDATION_REVIEW", now));
        } else {
            ApplicationEvent event = new ApplicationEvent(
                    UUID.randomUUID(), "ApplicationSubmitted", 1, applicationId, correlationId, now, mockPersona);
            outboxRepository.save(new OutboxEventEntity(
                    event.eventId(), "card.application.submitted.v1", applicationId.toString(),
                    toJson(event), correlationId, now));
        }

        auditRepository.save(new AuditRecordEntity(
                UUID.randomUUID(), principal, "APPLICATION_SUBMITTED", "APPLICATION", applicationId,
                correlationId, "{\"status\":\"" + status + "\"}", now));
        ApplicationReceipt receipt = new ApplicationReceipt(
                applicationId, sensitive ? ApplicationStatus.SUBMITTED : status, customerMessage);
        idempotencyRepository.save(new IdempotencyRecordEntity(
                principal, idempotencyKey, requestHash, 202, toJson(receipt), now, now.plusDays(1)));
        return receipt;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize an internal event or response.", exception);
        }
    }
}

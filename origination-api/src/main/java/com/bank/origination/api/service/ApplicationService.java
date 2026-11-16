package com.bank.origination.api.service;

import com.bank.origination.api.persistence.CardProductEntity;
import com.bank.origination.api.persistence.CardProductRepository;
import com.bank.origination.api.persistence.CreditApplicationEntity;
import com.bank.origination.api.persistence.CreditApplicationRepository;
import com.bank.origination.api.persistence.IdempotencyRecordEntity;
import com.bank.origination.api.persistence.IdempotencyRecordRepository;
import com.bank.origination.domain.ApiModels.ApplicationReceipt;
import com.bank.origination.domain.ApiModels.ApplicationRequest;
import com.bank.origination.domain.ApiModels.ApplicationStatusResponse;
import com.bank.origination.domain.ApiModels.ProductResponse;
import com.bank.origination.security.SecurityConstants;
import com.bank.origination.api.service.SsnHasher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ApplicationService {
    private static final String CUSTOMER_VALIDATION_MESSAGE =
            "Sorry, we ran into an issue while processing your application. Please call customer service at 1-800-XXX-XXXX.";
    private final MockExternalAdapters adapters;
    private final ApplicationSubmissionWriter writer;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final CreditApplicationRepository applicationRepository;
    private final CardProductRepository productRepository;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final SsnHasher ssnHasher;

    public ApplicationService(
            MockExternalAdapters adapters,
            ApplicationSubmissionWriter writer,
            IdempotencyRecordRepository idempotencyRepository,
            CreditApplicationRepository applicationRepository,
            CardProductRepository productRepository,
            ObjectMapper objectMapper,
            Environment environment,
            SsnHasher ssnHasher) {
        this.adapters = adapters;
        this.writer = writer;
        this.idempotencyRepository = idempotencyRepository;
        this.applicationRepository = applicationRepository;
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.ssnHasher = ssnHasher;
    }

    public ApplicationReceipt submit(
            ApplicationRequest request, String idempotencyKey, String correlationId, String principal, String personaHeader) {
        if (!Boolean.TRUE.equals(request.consentToCreditCheck())) {
            throw new OriginationException(HttpStatus.BAD_REQUEST, "Credit-check consent is required.");
        }
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{8,160}")) {
            throw new OriginationException(HttpStatus.BAD_REQUEST, "A valid X-Idempotency-Key header is required.");
        }
        String persona = resolvePersona(personaHeader);
        String requestHash = hashRequest(request);
        IdempotencyRecordEntity.Key key = new IdempotencyRecordEntity.Key(principal, idempotencyKey);
        var existing = idempotencyRepository.findById(key);
        if (existing.isPresent() && existing.get().getExpiresAt().isAfter(OffsetDateTime.now())
                && existing.get().getRequestHash().equals(requestHash)) {
            try {
                return objectMapper.readValue(existing.get().getResponseBodyRedacted(), ApplicationReceipt.class);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Stored idempotency response is invalid.", exception);
            }
        }
        if (existing.isPresent() && existing.get().getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new OriginationException(HttpStatus.CONFLICT, "The idempotency key was already used for a different request.");
        }
        existing.ifPresent(idempotencyRepository::delete);
        try {
            MockExternalAdapters.ValidationResult validation =
                    adapters.validate(request.ssn(), request.dateOfBirth(), request.addressLine(), request.postalCode(),
                            persona == null ? null : MockPersona.valueOf(persona));
            return writer.persist(request, requestHash, idempotencyKey, principal, correlationId, persona, validation);
        } catch (MockExternalAdapters.ExternalAdapterException exception) {
            MockExternalAdapters.ValidationResult failure =
                    MockExternalAdapters.ValidationResult.sensitive("ADAPTER_UNAVAILABLE");
            return writer.persist(request, requestHash, idempotencyKey, principal, correlationId, persona, failure);
        }
    }

    public ApplicationStatusResponse getStatus(UUID applicationId, String principal) {
        CreditApplicationEntity application = applicationRepository.findByIdAndApplicantEmail(applicationId, principal)
                .orElseThrow(() -> new OriginationException(HttpStatus.NOT_FOUND, "Application not found."));
        CardProductEntity product = productRepository.findById(application.getProductId())
                .orElseThrow(() -> new IllegalStateException("Application product reference is missing."));
        boolean sensitiveValidation = application.getStatus()
                == com.bank.origination.domain.ApplicationStatus.SENSITIVE_VALIDATION_REVIEW;
        String message = sensitiveValidation
                ? CUSTOMER_VALIDATION_MESSAGE
                : switch (application.getStatus()) {
                    case DECLINED -> "We couldn't approve this application at this time. Additional information will be sent by mail.";
                    case APPROVED -> "Your application has been approved.";
                    case MANUAL_REVIEW -> "Your application is being reviewed.";
                    default -> "Application received and is being processed.";
                };
        return new ApplicationStatusResponse(applicationId, product.getProductCode(),
                sensitiveValidation ? com.bank.origination.domain.ApplicationStatus.SUBMITTED : application.getStatus(),
                message, application.getSubmittedAt());
    }

    public List<ProductResponse> getProducts() {
        return productRepository.findAllByActiveTrueOrderByProductCodeAsc().stream()
                .map(product -> new ProductResponse(
                        product.getProductCode(), product.getTier(), product.getAprMin(), product.getAprMax(),
                        product.getAnnualFee(), product.getRewardsDescription(),
                        product.getCreditLimitMin(), product.getCreditLimitMax()))
                .toList();
    }

    private String resolvePersona(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!environment.acceptsProfiles(Profiles.of("local", "test"))) {
            throw new OriginationException(HttpStatus.BAD_REQUEST, "Mock personas are not available.");
        }
        try {
            return MockPersona.valueOf(value.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new OriginationException(HttpStatus.BAD_REQUEST, "Unknown mock persona.");
        }
    }

    private String hashRequest(ApplicationRequest request) {
        try {
            return ssnHasher.hash(objectMapper.writeValueAsString(request));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint application request.", exception);
        }
    }
}

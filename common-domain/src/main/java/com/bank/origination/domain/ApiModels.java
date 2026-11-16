package com.bank.origination.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}

    public record ApplicationRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "\\d{9}") String ssn,
            @NotNull @Past LocalDate dateOfBirth,
            @NotBlank @Size(max = 200) String addressLine,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String state,
            @NotBlank @Size(max = 20) String postalCode,
            @NotNull @PositiveOrZero BigDecimal annualIncome,
            @NotNull @PositiveOrZero BigDecimal monthlyHousingPayment,
            @NotNull @PositiveOrZero BigDecimal monthlyDebtPayment,
            @NotBlank String employmentStatus,
            @NotNull Boolean consentToCreditCheck,
            @NotBlank @Size(max = 40) String productCode) {
        @Override
        public String toString() {
            return "ApplicationRequest[identity=REDACTED, financialDisclosures=REDACTED, productCode="
                    + productCode + "]";
        }
    }

    public record ApplicationReceipt(UUID applicationId, ApplicationStatus status, String message) {}

    public record ApplicationStatusResponse(
            UUID applicationId, String productCode, ApplicationStatus status, String message, OffsetDateTime submittedAt) {}

    public record ProductResponse(
            String productCode,
            String tier,
            BigDecimal aprMin,
            BigDecimal aprMax,
            BigDecimal annualFee,
            String rewardsDescription,
            BigDecimal creditLimitMin,
            BigDecimal creditLimitMax) {}

    public record ReviewDecisionRequest(
            @NotNull DecisionOutcome outcome,
            @Size(max = 10) List<@NotBlank @Size(max = 80) String> reasonCodes,
            @Size(max = 2000) String note) {}

    public record ReviewQueueItem(
            UUID applicationId,
            String applicantName,
            String productCode,
            ApplicationStatus status,
            String ssnLast4,
            BigDecimal annualIncome,
            BigDecimal monthlyHousingPayment,
            BigDecimal monthlyDebtPayment,
            Integer bureauScore,
            String addressValidationStatus,
            OffsetDateTime submittedAt) {}

    public record SensitiveReviewItem(UUID applicationId, String validationCategory, String providerReference, OffsetDateTime createdAt) {}

    public record ProblemResponse(String type, String title, int status, String detail, String instance, String correlationId) {}
}

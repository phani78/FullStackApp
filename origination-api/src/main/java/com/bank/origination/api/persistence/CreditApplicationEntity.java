package com.bank.origination.api.persistence;

import com.bank.origination.domain.ApplicationStatus;
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

@Entity
@Table(name = "application")
public class CreditApplicationEntity {
    @Id
    private UUID id;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "applicant_email", nullable = false, length = 254)
    private String applicantEmail;
    @Column(name = "applicant_first_name", nullable = false, length = 100)
    private String applicantFirstName;
    @Column(name = "applicant_last_name", nullable = false, length = 100)
    private String applicantLastName;
    @Column(name = "ssn_hash", nullable = false, length = 64)
    private String ssnHash;
    @Column(name = "ssn_last4", nullable = false, length = 4)
    private String ssnLast4;
    @Column(name = "annual_income", nullable = false)
    private BigDecimal annualIncome;
    @Column(name = "monthly_housing_payment", nullable = false)
    private BigDecimal monthlyHousingPayment;
    @Column(name = "monthly_debt_payment", nullable = false)
    private BigDecimal monthlyDebtPayment;
    @Column(name = "employment_status", nullable = false, length = 40)
    private String employmentStatus;
    @Column(name = "address_city", length = 100)
    private String addressCity;
    @Column(name = "address_state", length = 2)
    private String addressState;
    @Column(name = "address_postal_code", length = 20)
    private String addressPostalCode;
    @Column(name = "bureau_score")
    private Integer bureauScore;
    @Column(name = "bureau_file_type", length = 32)
    private String bureauFileType;
    @Column(name = "bureau_fraud_alert", nullable = false)
    private boolean bureauFraudAlert;
    @Column(name = "address_validation_status", length = 32)
    private String addressValidationStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private ApplicationStatus status;
    @Column(name = "customer_message", length = 500)
    private String customerMessage;
    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;
    @Column(name = "mock_persona", length = 48)
    private String mockPersona;
    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;
    @Column(name = "embossing_exported_at")
    private OffsetDateTime embossingExportedAt;
    @Version
    private long version;

    protected CreditApplicationEntity() {}

    public CreditApplicationEntity(
            UUID id, UUID productId, String email, String firstName, String lastName,
            String ssnHash, String ssnLast4, BigDecimal annualIncome, BigDecimal housingPayment,
            BigDecimal debtPayment, String employmentStatus, String city, String state,
            String postalCode, Integer bureauScore, String bureauFileType, boolean fraudAlert,
            String addressValidationStatus, ApplicationStatus status, String customerMessage,
            String correlationId, String mockPersona, OffsetDateTime submittedAt, OffsetDateTime expiresAt) {
        this.id = id;
        this.productId = productId;
        this.applicantEmail = email;
        this.applicantFirstName = firstName;
        this.applicantLastName = lastName;
        this.ssnHash = ssnHash;
        this.ssnLast4 = ssnLast4;
        this.annualIncome = annualIncome;
        this.monthlyHousingPayment = housingPayment;
        this.monthlyDebtPayment = debtPayment;
        this.employmentStatus = employmentStatus;
        this.addressCity = city;
        this.addressState = state;
        this.addressPostalCode = postalCode;
        this.bureauScore = bureauScore;
        this.bureauFileType = bureauFileType;
        this.bureauFraudAlert = fraudAlert;
        this.addressValidationStatus = addressValidationStatus;
        this.status = status;
        this.customerMessage = customerMessage;
        this.correlationId = correlationId;
        this.mockPersona = mockPersona;
        this.submittedAt = submittedAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getApplicantEmail() { return applicantEmail; }
    public String getApplicantFirstName() { return applicantFirstName; }
    public String getApplicantLastName() { return applicantLastName; }
    public String getSsnLast4() { return ssnLast4; }
    public BigDecimal getAnnualIncome() { return annualIncome; }
    public BigDecimal getMonthlyHousingPayment() { return monthlyHousingPayment; }
    public BigDecimal getMonthlyDebtPayment() { return monthlyDebtPayment; }
    public Integer getBureauScore() { return bureauScore; }
    public String getBureauFileType() { return bureauFileType; }
    public String getAddressValidationStatus() { return addressValidationStatus; }
    public boolean hasBureauFraudAlert() { return bureauFraudAlert; }
    public ApplicationStatus getStatus() { return status; }
    public String getCorrelationId() { return correlationId; }
    public String getMockPersona() { return mockPersona; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public void setActivatedAt(OffsetDateTime activatedAt) { this.activatedAt = activatedAt; }
}

package com.bank.origination.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sensitive_validation")
public class SensitiveValidationEntity {
    @Id
    private UUID id;
    @Column(name = "application_id", nullable = false)
    private UUID applicationId;
    @Column(name = "validation_category", nullable = false, length = 64)
    private String validationCategory;
    @Column(name = "provider_reference", length = 200)
    private String providerReference;
    @Column(name = "access_policy", nullable = false, length = 100)
    private String accessPolicy;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SensitiveValidationEntity() {}

    public SensitiveValidationEntity(UUID id, UUID applicationId, String category, String providerReference,
            String accessPolicy, OffsetDateTime createdAt) {
        this.id = id;
        this.applicationId = applicationId;
        this.validationCategory = category;
        this.providerReference = providerReference;
        this.accessPolicy = accessPolicy;
        this.createdAt = createdAt;
    }

    public UUID getApplicationId() { return applicationId; }
    public String getValidationCategory() { return validationCategory; }
    public String getProviderReference() { return providerReference; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

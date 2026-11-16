package com.bank.origination.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "card_product")
public class CardProductEntity {
    @Id
    private UUID id;

    @Column(name = "product_code", nullable = false, unique = true, length = 40)
    private String productCode;

    @Column(nullable = false, length = 40)
    private String tier;

    @Column(name = "apr_min", nullable = false)
    private BigDecimal aprMin;

    @Column(name = "apr_max", nullable = false)
    private BigDecimal aprMax;

    @Column(name = "annual_fee", nullable = false)
    private BigDecimal annualFee;

    @Column(name = "rewards_description", nullable = false, length = 500)
    private String rewardsDescription;

    @Column(name = "credit_limit_min", nullable = false)
    private BigDecimal creditLimitMin;

    @Column(name = "credit_limit_max", nullable = false)
    private BigDecimal creditLimitMax;

    @Column(name = "minimum_fico", nullable = false)
    private int minimumFico;

    @Column(name = "maximum_dti", nullable = false)
    private BigDecimal maximumDti;

    @Column(nullable = false)
    private boolean active;

    @Version
    private long version;

    protected CardProductEntity() {}

    public UUID getId() { return id; }
    public String getProductCode() { return productCode; }
    public String getTier() { return tier; }
    public BigDecimal getAprMin() { return aprMin; }
    public BigDecimal getAprMax() { return aprMax; }
    public BigDecimal getAnnualFee() { return annualFee; }
    public String getRewardsDescription() { return rewardsDescription; }
    public BigDecimal getCreditLimitMin() { return creditLimitMin; }
    public BigDecimal getCreditLimitMax() { return creditLimitMax; }
    public int getMinimumFico() { return minimumFico; }
    public BigDecimal getMaximumDti() { return maximumDti; }
    public boolean isActive() { return active; }
}

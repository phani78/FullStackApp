package com.bank.origination.domain;

public enum ApplicationStatus {
    SUBMITTED,
    UNDERWRITING,
    APPROVED,
    DECLINED,
    MANUAL_REVIEW,
    SENSITIVE_VALIDATION_REVIEW,
    EXPIRED
}

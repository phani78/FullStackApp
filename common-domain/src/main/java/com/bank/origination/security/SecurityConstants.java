package com.bank.origination.security;

public final class SecurityConstants {
    public static final String CORRELATION_ID = "X-Correlation-ID";
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final int MAX_CORRELATION_ID_LENGTH = 100;

    private SecurityConstants() {}
}

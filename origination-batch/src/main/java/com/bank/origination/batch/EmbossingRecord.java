package com.bank.origination.batch;

import java.math.BigDecimal;
import java.util.UUID;

public record EmbossingRecord(
        UUID applicationId,
        String firstName,
        String lastName,
        String productCode,
        BigDecimal creditLimit,
        BigDecimal apr) {}

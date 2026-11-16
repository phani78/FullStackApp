package com.bank.origination.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ApplicationEvent(
        @NotNull UUID eventId,
        @NotBlank String eventType,
        int schemaVersion,
        @NotNull UUID applicationId,
        @NotBlank String correlationId,
        @NotNull OffsetDateTime occurredAt,
        String mockPersona) {}

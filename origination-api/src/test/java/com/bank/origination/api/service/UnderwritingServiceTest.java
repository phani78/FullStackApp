package com.bank.origination.api.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.origination.api.persistence.AuditRecordRepository;
import com.bank.origination.api.persistence.CardProductRepository;
import com.bank.origination.api.persistence.CreditApplicationRepository;
import com.bank.origination.api.persistence.DecisionRepository;
import com.bank.origination.api.persistence.OutboxEventRepository;
import com.bank.origination.api.persistence.ProcessedEventEntity;
import com.bank.origination.api.persistence.ProcessedEventRepository;
import com.bank.origination.api.persistence.UnderwriterAssignmentRepository;
import com.bank.origination.domain.ApplicationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

class UnderwritingServiceTest {
    @Test
    void duplicateKafkaEventDoesNotRepeatBusinessWork() {
        ProcessedEventRepository processedEvents = mock(ProcessedEventRepository.class);
        CreditApplicationRepository applications = mock(CreditApplicationRepository.class);
        CardProductRepository products = mock(CardProductRepository.class);
        DecisionRepository decisions = mock(DecisionRepository.class);
        UnderwriterAssignmentRepository assignments = mock(UnderwriterAssignmentRepository.class);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        AuditRecordRepository audit = mock(AuditRecordRepository.class);
        MutationIdempotency idempotency = mock(MutationIdempotency.class);
        UnderwritingService service = new UnderwritingService(
                processedEvents, applications, products, decisions, assignments, outbox, audit,
                new ObjectMapper(), idempotency);
        ApplicationEvent event = new ApplicationEvent(
                UUID.randomUUID(), "ApplicationSubmitted", 1, UUID.randomUUID(),
                "test-correlation", OffsetDateTime.now(), null);
        when(processedEvents.existsById(any(ProcessedEventEntity.Key.class))).thenReturn(true);

        service.process(event);

        verify(applications, never()).findById(any());
        verify(decisions, never()).save(any());
        verify(processedEvents, never()).save(any());
    }
}

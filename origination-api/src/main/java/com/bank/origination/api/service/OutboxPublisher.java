package com.bank.origination.api.service;

import com.bank.origination.api.persistence.OutboxEventEntity;
import com.bank.origination.api.persistence.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        for (OutboxEventEntity event : repository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            MDC.put("correlationId", event.getCorrelationId());
            try {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(event.getTopic(), event.getEventKey(), event.getPayload());
                record.headers().add(new RecordHeader(
                        "X-Correlation-ID", event.getCorrelationId().getBytes(StandardCharsets.UTF_8)));
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
                event.markPublished(OffsetDateTime.now());
            } catch (Exception exception) {
                log.error("Outbox publish failed eventId={} errorType={}",
                        event.getEventId(), exception.getClass().getSimpleName());
                return;
            } finally {
                MDC.remove("correlationId");
            }
        }
    }
}

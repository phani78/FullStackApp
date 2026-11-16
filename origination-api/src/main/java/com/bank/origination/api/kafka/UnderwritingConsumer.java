package com.bank.origination.api.kafka;

import com.bank.origination.api.service.UnderwritingService;
import com.bank.origination.domain.ApplicationEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class UnderwritingConsumer {
    private static final Logger log = LoggerFactory.getLogger(UnderwritingConsumer.class);
    private final UnderwritingService service;
    private final MeterRegistry meterRegistry;

    public UnderwritingConsumer(UnderwritingService service, MeterRegistry meterRegistry) {
        this.service = service;
        this.meterRegistry = meterRegistry;
    }

    @RetryableTopic(
            attempts = "${app.kafka.retry-attempts:3}",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlt")
    @KafkaListener(
            topics = "${app.kafka.topics.submitted}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ApplicationEvent event) {
        MDC.put("correlationId", event.correlationId());
        try {
            service.process(event);
            log.info("Application event processed eventId={} applicationId={}", event.eventId(), event.applicationId());
        } finally {
            MDC.remove("correlationId");
        }
    }

    @DltHandler
    public void handleDeadLetter(
            ConsumerRecord<String, ApplicationEvent> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = "kafka_dlt-exception-fqcn", required = false) String rootCauseType) {
        ApplicationEvent event = record.value();
        String eventId = event == null ? "unknown" : event.eventId().toString();
        meterRegistry.counter("origination.kafka.dlt", "topic", topic).increment();
        log.error("Underwriting event routed to DLT eventId={} topic={} partition={} offset={} rootCauseType={}",
                eventId, topic, partition, offset, rootCauseType == null ? "unknown" : rootCauseType);
    }
}

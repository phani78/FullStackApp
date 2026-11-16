package com.bank.origination.api.kafka;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.origination.domain.ApplicationEvent;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.retry.annotation.Backoff;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

@SpringJUnitConfig(EmbeddedKafkaRetryTest.TestConfiguration.class)
@EmbeddedKafka(
        partitions = 1,
        topics = {"test.application.submitted", "test.application.submitted.dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class EmbeddedKafkaRetryTest {
    @Autowired
    private KafkaTemplate<String, ApplicationEvent> kafkaTemplate;
    @Autowired
    private RetryTestListener listener;
    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void failedMessageIsRetriedAndRoutedToDeadLetterTopic() throws Exception {
        ApplicationEvent event = new ApplicationEvent(
                UUID.randomUUID(), "ApplicationSubmitted", 1, UUID.randomUUID(),
                "embedded-test-correlation", OffsetDateTime.now(), null);

        kafkaTemplate.send("test.application.submitted", event.applicationId().toString(), event)
                .get(10, TimeUnit.SECONDS);

        assertTrue(listener.awaitDeadLetter(30, TimeUnit.SECONDS));
        assertTrue(listener.failureAttempts() >= 2);
    }

    @Configuration
    @EnableKafka
    static class TestConfiguration {
        @Bean
        KafkaAdmin kafkaAdmin(EmbeddedKafkaBroker broker) {
            return new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()));
        }

        @Bean
        ProducerFactory<String, ApplicationEvent> producerFactory(EmbeddedKafkaBroker broker) {
            Map<String, Object> properties = new HashMap<>(KafkaTestUtils.producerProps(broker));
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            return new DefaultKafkaProducerFactory<>(properties);
        }

        @Bean
        KafkaTemplate<String, ApplicationEvent> kafkaTemplate(ProducerFactory<String, ApplicationEvent> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }

        @Bean
        ConsumerFactory<String, ApplicationEvent> consumerFactory(EmbeddedKafkaBroker broker) {
            Map<String, Object> properties = new HashMap<>(KafkaTestUtils.consumerProps("retry-test", "false", broker));
            properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            properties.put(JsonDeserializer.TRUSTED_PACKAGES, "com.bank.origination.domain");
            return new DefaultKafkaConsumerFactory<>(
                    properties, new StringDeserializer(), new JsonDeserializer<>(ApplicationEvent.class, false));
        }

        @Bean
        ConcurrentKafkaListenerContainerFactory<String, ApplicationEvent> kafkaListenerContainerFactory(
                ConsumerFactory<String, ApplicationEvent> consumerFactory) {
            ConcurrentKafkaListenerContainerFactory<String, ApplicationEvent> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory);
            return factory;
        }

        @Bean
        RetryTestListener retryTestListener() {
            return new RetryTestListener();
        }
    }

    static class RetryTestListener {
        private final CountDownLatch deadLetter = new CountDownLatch(1);
        private final java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();

        @RetryableTopic(
                attempts = "2",
                backoff = @Backoff(delay = 100),
                dltTopicSuffix = ".dlt",
                autoCreateTopics = "true",
                numPartitions = "1",
                replicationFactor = "1")
        @KafkaListener(topics = "test.application.submitted", groupId = "embedded-retry-test")
        public void consume(ApplicationEvent event) {
            failures.incrementAndGet();
            throw new IllegalStateException("synthetic adapter failure");
        }

        @DltHandler
        public void dlt(ApplicationEvent event) {
            deadLetter.countDown();
        }

        boolean awaitDeadLetter(long timeout, TimeUnit unit) throws InterruptedException {
            return deadLetter.await(timeout, unit);
        }

        int failureAttempts() {
            return failures.get();
        }
    }
}

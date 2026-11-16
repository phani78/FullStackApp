---
mode: 'agent'
description: 'Generate an enterprise-grade, idempotent Spring Kafka consumer with DLT, tracing, and tests'
---

Create a new Spring Kafka consumer for the domain event specified by the user. Follow all rules in `.github/copilot-instructions.md` and generate the complete set of files below:

### 1. Event Payload & Idempotency Entity
- **Event Record (`com.<org>.<service>.kafka.event`):** Create an immutable Java `record` for the incoming event payload. Include standard metadata fields (`eventId`, `correlationId`, `eventTimestamp`, `sourceSystem`) and Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`).
- **Processed Event Entity (`com.<org>.<service>.domain.entity`):** If not already present in the workspace, create a JPA entity `ProcessedEvent` (`eventId` as primary key, `consumerGroup`, `processedAt`) and its Spring Data JPA repository to guarantee at-least-once idempotent processing.

### 2. Kafka Consumer Implementation (`com.<org>.<service>.kafka.consumer`)
- Annotate the listener method with `@KafkaListener` using externalized topic and group-id properties (`${app.kafka.topics.<topic-name>}`).
- Configure non-blocking retries and Dead Letter Topic routing using `@RetryableTopic` (3 attempts, exponential backoff starting at 1000ms, excluding non-recoverable exceptions like `SerializationException` or `ConstraintViolationException`).
- Extract `correlationId` from Kafka `Headers` (or the event payload) and populate SLF4J `MDC` inside a `try/finally` block so it is always cleared after execution.
- Mask any PII or account numbers before logging event metadata.
- Delegate business execution to a `@Transactional` service method that checks `ProcessedEventRepository.existsById(event.eventId())` before processing.

### 3. Dead Letter Topic Handler
- Add a `@DltHandler` method in the consumer class that logs the failed `eventId`, original topic, partition, offset, and exception root cause at `ERROR` level without leaking PII, and increments a Micrometer failure counter (`MeterRegistry`).

### 4. Configuration & Testing
- Update `application.yml` with placeholder topic configurations and consumer properties (`enable-auto-commit: false`, `isolation-level: read_committed`).
- Generate a unit test using JUnit 5 + Mockito verifying:
  1. Happy path processing and `ProcessedEvent` persistence.
  2. Duplicate `eventId` is skipped safely without re-executing business logic.
  3. Validation failure triggers exception handling.
- Generate an integration test using `@SpringBootTest` and `@EmbeddedKafka` (or Testcontainers if configured in `pom.xml`) verifying end-to-end consumption and DLT routing on failure.

# Enterprise Banking Coding Standards

## General & Security (OWASP / Banking Compliance)
- Never hardcode secrets, credentials, API keys, or connection strings. Always use environment variables or Spring Cloud Config placeholders.
- Sanitize all inputs and prevent SQL/NoSQL injection and XSS.
- Never log Personally Identifiable Information (PII), account numbers, SSNs, or tokens. Mask sensitive fields in `toString()` and log statements.
- Use SLF4J with structured JSON logging and include `correlationId` / `traceId` in all logs.

## Spring Boot, REST & JPA
- Use Java 21 features (Records for DTOs, pattern matching).
- Follow strict layered architecture: `Controller -> Service -> Repository`. Never expose JPA `@Entity` classes directly in REST controllers; always map to immutable DTOs.
- Use constructor-based dependency injection only (no `@Autowired` on fields).
- Implement global exception handling using `@RestControllerAdvice` returning RFC 7807 `ProblemDetail` responses.
- For JPA, explicitly define fetch types (`FetchType.LAZY`), use optimistic locking (`@Version`) for financial transactions, and annotate write operations with `@Transactional`.

## Apache Kafka (Producers & Consumers)
- Ensure idempotency on producers (`enable.idempotence=true`, `acks=all`).
- Implement Dead Letter Topics (DLT) and exponential backoff retry mechanisms (`@RetryableTopic`) for all consumers.
- Make all Kafka consumers idempotent by tracking processed `eventId` / `idempotencyKey` in the database before executing business logic.

## Spring Batch
- Use chunk-oriented processing with explicit commit intervals, `SkipPolicy`, and `RetryPolicy`.
- Ensure all `ItemReader` and `ItemWriter` implementations are restartable and thread-safe.
- Externalize job parameters and validate them via `JobParametersValidator`.

## ReactJS (TypeScript)
- Use functional components with strict TypeScript interfaces (`strict: true`).
- Use React Query (TanStack Query) for server state and API caching; avoid raw `useEffect` for data fetching.
- Implement error boundaries and sanitize any dynamic HTML rendering.

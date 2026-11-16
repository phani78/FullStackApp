---
mode: 'agent'
description: 'Generate a restartable, fault-tolerant Spring Batch chunk-oriented job with listeners and tests'
---

Create a production-ready Spring Batch job based on the user's specification. Follow `.github/copilot-instructions.md` and generate:

### 1. Job & Step Configuration (`com.<org>.<service>.batch.config`)
- Define a `@Configuration` class configuring a `Job` and chunk-oriented `Step` using `JobBuilder` and `StepBuilder`.
- Inject `JobRepository` and `PlatformTransactionManager` explicitly.
- Configure a `JobParametersValidator` (`DefaultJobParametersValidator`) to enforce required runtime parameters (e.g., `runDate`, `filePath`).
- Configure fault tolerance on the step: `.faultTolerant()`, `.skipLimit(...)`, `.skip(FlatFileParseException.class)`, `.retryLimit(3)`, and `.retry(TransientDataAccessException.class)`.

### 2. Reader, Processor, and Writer (`com.<org>.<service>.batch.step`)
- **ItemReader:** Use a restartable reader (`FlatFileItemReader` or `JpaPagingItemReader` with explicit `sortKeys` and `pageSize` matching the chunk size).
- **ItemProcessor:** Implement validation and transformation logic. Route invalid records to a reject/audit table or log via a `SkipListener` without halting the entire batch.
- **ItemWriter:** Use `JpaItemWriter` or `JdbcBatchItemWriter` for bulk persistence.

### 3. Observability & Audit Listener (`com.<org>.<service>.batch.listener`)
- Implement `JobExecutionListener` and `SkipListener` to log job start/end times, read/write/skip counts, and exit status in structured JSON format.

### 4. Tests
- Generate an end-to-end batch test using `@SpringBatchTest` and `JobLauncherTestUtils` verifying `BatchStatus.COMPLETED`, expected item write counts, and skip behavior on malformed input records.

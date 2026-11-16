package com.bank.origination.batch;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.transform.LineAggregator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.DefaultJobParametersValidator;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.Order;

@Configuration
@EnableBatchProcessing
public class BatchJobConfiguration {
    private static final int CHUNK_SIZE = 100;

    @Bean
    JobParametersValidator nightlyParametersValidator() {
        return new DefaultJobParametersValidator(
                new String[] {"businessDate", "outputDirectory"}, new String[0]);
    }

    @Bean
    @StepScope
    JdbcPagingItemReader<EmbossingRecord> embossingReader(
            javax.sql.DataSource dataSource,
            @Value("#{jobParameters['businessDate']}") String businessDate) {
        LocalDate asOfDate = LocalDate.parse(businessDate);
        return new JdbcPagingItemReaderBuilder<EmbossingRecord>()
                .name("embossingReader")
                .dataSource(dataSource)
                .selectClause("""
                        SELECT a.id AS application_id, a.applicant_first_name, a.applicant_last_name,
                               p.product_code, d.approved_limit, d.assigned_apr
                        """)
                .fromClause("""
                        FROM application a
                        JOIN card_product p ON p.id = a.product_id
                        JOIN decision d ON d.application_id = a.id
                        """)
                .whereClause("""
                        WHERE a.status = 'APPROVED'
                          AND a.activated_at IS NOT NULL
                          AND a.activated_at < :businessDate + INTERVAL '1 day'
                          AND a.embossing_exported_at IS NULL
                          AND d.outcome = 'APPROVED'
                        """)
                .parameterValues(Map.of("businessDate", asOfDate))
                .sortKeys(Map.of("a.id", Order.ASCENDING))
                .pageSize(CHUNK_SIZE)
                .rowMapper((rs, rowNum) -> new EmbossingRecord(
                        rs.getObject("application_id", UUID.class),
                        rs.getString("applicant_first_name"),
                        rs.getString("applicant_last_name"),
                        rs.getString("product_code"),
                        rs.getBigDecimal("approved_limit"),
                        rs.getBigDecimal("assigned_apr")))
                .saveState(true)
                .build();
    }

    @Bean
    @StepScope
    EmbossingFileWriter embossingWriter(
            JdbcTemplate jdbcTemplate,
            @Value("#{jobParameters['outputDirectory']}") String outputDirectory,
            @Value("#{jobParameters['businessDate']}") String businessDate) {
        return new EmbossingFileWriter(Path.of(outputDirectory), LocalDate.parse(businessDate), jdbcTemplate);
    }

    @Bean
    Step cardEmbossingStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcPagingItemReader<EmbossingRecord> embossingReader,
            EmbossingFileWriter embossingWriter,
            BatchTelemetryListener telemetryListener) {
        return new StepBuilder("cardEmbossingStep", jobRepository)
                .<EmbossingRecord, EmbossingRecord>chunk(CHUNK_SIZE, transactionManager)
                .reader(embossingReader)
                .processor(item -> item)
                .writer(embossingWriter)
                .faultTolerant()
                .skipLimit(20)
                .skip(FlatFileParseException.class)
                .retryLimit(3)
                .retry(TransientDataAccessException.class)
                .listener(embossingWriter)
                .listener(telemetryListener)
                .build();
    }

    @Bean
    Job cardEmbossingJob(
            JobRepository jobRepository, Step cardEmbossingStep,
            JobParametersValidator nightlyParametersValidator, BatchTelemetryListener telemetryListener) {
        return new JobBuilder("cardEmbossingJob", jobRepository)
                .validator(nightlyParametersValidator)
                .start(cardEmbossingStep)
                .listener(telemetryListener)
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<UUID> staleApplicationReader(javax.sql.DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<UUID>()
                .name("staleApplicationReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT id FROM application
                        WHERE status IN ('SUBMITTED', 'UNDERWRITING', 'MANUAL_REVIEW')
                          AND expires_at < CURRENT_TIMESTAMP
                        ORDER BY expires_at, id
                        """)
                .rowMapper((rs, rowNum) -> rs.getObject("id", UUID.class))
                .saveState(true)
                .build();
    }

    @Bean
    JdbcBatchItemWriter<UUID> staleApplicationWriter(javax.sql.DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<UUID>()
                .dataSource(dataSource)
                .sql("""
                        UPDATE application SET status = 'EXPIRED'
                        WHERE id = :id AND status IN ('SUBMITTED', 'UNDERWRITING', 'MANUAL_REVIEW')
                        """)
                .itemSqlParameterSourceProvider(id -> new MapSqlParameterSource("id", id))
                .assertUpdates(false)
                .build();
    }

    @Bean
    Step staleApplicationExpiryStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcCursorItemReader<UUID> staleApplicationReader,
            JdbcBatchItemWriter<UUID> staleApplicationWriter,
            BatchTelemetryListener telemetryListener) {
        return new StepBuilder("staleApplicationExpiryStep", jobRepository)
                .<UUID, UUID>chunk(CHUNK_SIZE, transactionManager)
                .reader(staleApplicationReader)
                .processor(item -> item)
                .writer(staleApplicationWriter)
                .faultTolerant()
                .skipLimit(20)
                .skip(FlatFileParseException.class)
                .retryLimit(3)
                .retry(TransientDataAccessException.class)
                .listener(telemetryListener)
                .build();
    }

    @Bean
    Job staleApplicationExpiryJob(
            JobRepository jobRepository, Step staleApplicationExpiryStep,
            JobParametersValidator nightlyParametersValidator, BatchTelemetryListener telemetryListener) {
        return new JobBuilder("staleApplicationExpiryJob", jobRepository)
                .validator(nightlyParametersValidator)
                .start(staleApplicationExpiryStep)
                .listener(telemetryListener)
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<BureauReportRecord> bureauReportReader(javax.sql.DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<BureauReportRecord>()
                .name("bureauReportReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT a.id AS application_id, a.bureau_score, d.outcome, d.decided_at
                        FROM application a
                        JOIN decision d ON d.application_id = a.id
                        WHERE a.status IN ('APPROVED', 'DECLINED')
                        ORDER BY a.id, d.decided_at DESC
                        """)
                .rowMapper((rs, rowNum) -> new BureauReportRecord(
                        rs.getObject("application_id", UUID.class),
                        (Integer) rs.getObject("bureau_score"),
                        rs.getString("outcome"),
                        rs.getTimestamp("decided_at").toInstant().toString()))
                .saveState(true)
                .build();
    }

    @Bean
    @StepScope
    FlatFileItemWriter<BureauReportRecord> bureauReportWriter(
            @Value("#{jobParameters['outputDirectory']}") String outputDirectory,
            @Value("#{jobParameters['businessDate']}") String businessDate) throws IOException {
        Path directory = Path.of(outputDirectory);
        Files.createDirectories(directory);
        return new FlatFileItemWriterBuilder<BureauReportRecord>()
                .name("bureauReportWriter")
                .resource(new FileSystemResource(directory.resolve("bureau-report-" + businessDate + ".csv")))
                .headerCallback(writer -> writer.write("applicationId,bureauScore,outcome,decidedAt"))
                .lineAggregator(record -> String.join(",",
                        record.applicationId().toString(),
                        record.bureauScore() == null ? "" : record.bureauScore().toString(),
                        record.outcome(),
                        record.decidedAt()))
                .shouldDeleteIfExists(false)
                .saveState(true)
                .build();
    }

    @Bean
    Step bureauReportingStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcCursorItemReader<BureauReportRecord> bureauReportReader,
            FlatFileItemWriter<BureauReportRecord> bureauReportWriter,
            BatchTelemetryListener telemetryListener) {
        return new StepBuilder("bureauReportingStep", jobRepository)
                .<BureauReportRecord, BureauReportRecord>chunk(CHUNK_SIZE, transactionManager)
                .reader(bureauReportReader)
                .processor(item -> item)
                .writer(bureauReportWriter)
                .faultTolerant()
                .skipLimit(20)
                .skip(FlatFileParseException.class)
                .retryLimit(3)
                .retry(TransientDataAccessException.class)
                .listener(telemetryListener)
                .build();
    }

    @Bean
    Job bureauReportingJob(
            JobRepository jobRepository, Step bureauReportingStep,
            JobParametersValidator nightlyParametersValidator, BatchTelemetryListener telemetryListener) {
        return new JobBuilder("bureauReportingJob", jobRepository)
                .validator(nightlyParametersValidator)
                .start(bureauReportingStep)
                .listener(telemetryListener)
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<NoticeRecord> adverseActionReader(javax.sql.DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<NoticeRecord>()
                .name("adverseActionReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT d.application_id, d.internal_reason_codes::text AS reason_codes, d.policy_version
                        FROM decision d
                        WHERE d.outcome = 'DECLINED'
                        ORDER BY d.decided_at, d.application_id
                        """)
                .rowMapper((rs, rowNum) -> new NoticeRecord(
                        rs.getObject("application_id", UUID.class),
                        rs.getString("reason_codes"),
                        rs.getString("policy_version")))
                .saveState(true)
                .build();
    }

    @Bean
    JdbcBatchItemWriter<NoticeRecord> adverseActionWriter(
            javax.sql.DataSource dataSource, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return new JdbcBatchItemWriterBuilder<NoticeRecord>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO customer_notice
                            (id, application_id, notice_type, delivery_status, template_version, notice_payload, generated_at)
                        VALUES (:id, :applicationId, 'ADVERSE_ACTION', 'PREPARED', :templateVersion,
                                CAST(:noticePayload AS jsonb), CURRENT_TIMESTAMP)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .itemSqlParameterSourceProvider(record -> new MapSqlParameterSource()
                        .addValue("id", UUID.nameUUIDFromBytes(
                                (record.applicationId() + ":ADVERSE_ACTION:" + record.policyVersion())
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .addValue("applicationId", record.applicationId())
                        .addValue("templateVersion", "notice-" + record.policyVersion())
                        .addValue("noticePayload", "{\"reasonCodes\":" + record.reasonCodes() + "}"))
                .assertUpdates(false)
                .build();
    }

    @Bean
    Step adverseActionNoticeStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcCursorItemReader<NoticeRecord> adverseActionReader,
            JdbcBatchItemWriter<NoticeRecord> adverseActionWriter,
            BatchTelemetryListener telemetryListener) {
        return new StepBuilder("adverseActionNoticeStep", jobRepository)
                .<NoticeRecord, NoticeRecord>chunk(CHUNK_SIZE, transactionManager)
                .reader(adverseActionReader)
                .processor(item -> item)
                .writer(adverseActionWriter)
                .faultTolerant()
                .skipLimit(20)
                .skip(FlatFileParseException.class)
                .retryLimit(3)
                .retry(TransientDataAccessException.class)
                .listener(telemetryListener)
                .build();
    }

    @Bean
    Job adverseActionNoticeJob(
            JobRepository jobRepository, Step adverseActionNoticeStep, Step adverseActionDeliveryStep,
            JobParametersValidator nightlyParametersValidator, BatchTelemetryListener telemetryListener) {
        return new JobBuilder("adverseActionNoticeJob", jobRepository)
                .validator(nightlyParametersValidator)
                .start(adverseActionNoticeStep)
                .next(adverseActionDeliveryStep)
                .listener(telemetryListener)
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<PreparedNotice> preparedNoticeReader(javax.sql.DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<PreparedNotice>()
                .name("preparedNoticeReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT id, application_id
                        FROM customer_notice
                        WHERE notice_type = 'ADVERSE_ACTION' AND delivery_status = 'PREPARED'
                        ORDER BY generated_at, id
                        """)
                .rowMapper((rs, rowNum) -> new PreparedNotice(
                        rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class)))
                .saveState(true)
                .build();
    }

    @Bean
    JdbcBatchItemWriter<PreparedNotice> preparedNoticeWriter(javax.sql.DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<PreparedNotice>()
                .dataSource(dataSource)
                .sql("""
                        UPDATE customer_notice
                        SET delivery_status = 'SIMULATED_SENT', delivered_at = CURRENT_TIMESTAMP
                        WHERE id = :id AND delivery_status = 'PREPARED'
                        """)
                .itemSqlParameterSourceProvider(record -> new MapSqlParameterSource("id", record.noticeId()))
                .assertUpdates(false)
                .build();
    }

    @Bean
    Step adverseActionDeliveryStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcCursorItemReader<PreparedNotice> preparedNoticeReader,
            JdbcBatchItemWriter<PreparedNotice> preparedNoticeWriter,
            BatchTelemetryListener telemetryListener) {
        return new StepBuilder("adverseActionDeliveryStep", jobRepository)
                .<PreparedNotice, PreparedNotice>chunk(CHUNK_SIZE, transactionManager)
                .reader(preparedNoticeReader)
                .processor(item -> item)
                .writer(preparedNoticeWriter)
                .faultTolerant()
                .skipLimit(20)
                .skip(FlatFileParseException.class)
                .retryLimit(3)
                .retry(TransientDataAccessException.class)
                .listener(telemetryListener)
                .build();
    }

    public record BureauReportRecord(UUID applicationId, Integer bureauScore, String outcome, String decidedAt) {}
    public record NoticeRecord(UUID applicationId, String reasonCodes, String policyVersion) {}
    public record PreparedNotice(UUID noticeId, UUID applicationId) {}
}

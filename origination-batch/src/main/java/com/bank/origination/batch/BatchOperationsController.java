package com.bank.origination.batch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.slf4j.MDC;
import com.bank.origination.security.SecurityConstants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ops/batch")
@PreAuthorize("hasRole('OPERATIONS')")
public class BatchOperationsController {
    private static final Set<String> ALLOWED_JOBS =
            Set.of("cardEmbossingJob", "bureauReportingJob", "staleApplicationExpiryJob", "adverseActionNoticeJob");
    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final org.springframework.context.ApplicationContext applicationContext;
    private final Path outputDirectory;
    private final JdbcTemplate jdbcTemplate;

    public BatchOperationsController(
            JobLauncher jobLauncher,
            JobExplorer jobExplorer,
            org.springframework.context.ApplicationContext applicationContext,
            @Value("${app.batch.output-directory}") String outputDirectory,
            JdbcTemplate jdbcTemplate) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.applicationContext = applicationContext;
        this.outputDirectory = Path.of(outputDirectory).toAbsolutePath().normalize();
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/jobs")
    public List<JobSummary> jobs() {
        return ALLOWED_JOBS.stream().sorted()
                .flatMap(name -> jobExplorer.getJobInstances(name, 0, 20).stream()
                        .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream()
                                .map(execution -> new JobSummary(
                                        execution.getId(), name, execution.getStatus().name(),
                                        execution.getStartTime() == null ? null : execution.getStartTime().toString(),
                                        execution.getExitStatus().getExitCode()))))
                .toList();
    }

    @GetMapping("/jobs/{executionId}")
    public JobSummary execution(@PathVariable Long executionId) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch execution not found.");
        }
        return new JobSummary(
                execution.getId(), execution.getJobInstance().getJobName(), execution.getStatus().name(),
                execution.getStartTime() == null ? null : execution.getStartTime().toString(),
                execution.getExitStatus().getExitCode());
    }

    @PostMapping("/jobs/{jobName}/executions")
    public JobSummary launch(
            @PathVariable String jobName,
            @Valid @RequestBody StartJobRequest request,
            @RequestHeader("X-Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestHeader(value = SecurityConstants.CORRELATION_ID, required = false) String correlationId,
            Authentication authentication) throws Exception {
        if (!idempotencyKey.matches("[A-Za-z0-9._:-]{8,160}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid X-Idempotency-Key header is required.");
        }
        if (!ALLOWED_JOBS.contains(jobName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Job name is not allowlisted.");
        }
        LocalDate businessDate;
        try {
            businessDate = LocalDate.parse(request.businessDate());
        } catch (java.time.DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business date is invalid.");
        }
        String principal = authentication.getName();
        String requestHash = hash(jobName + "|" + businessDate + "|" + outputDirectory);
        var existing = jdbcTemplate.query(
                "SELECT request_hash, execution_id FROM batch_job_request WHERE principal_id = ? AND idempotency_key = ?",
                (rs, rowNum) -> new Object[] {rs.getString("request_hash"), (Long) rs.getObject("execution_id")},
                principal, idempotencyKey);
        if (!existing.isEmpty()) {
            Object[] record = existing.get(0);
            if (!requestHash.equals(record[0])) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The idempotency key was already used for a different request.");
            }
            if (record[1] == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The batch request is already being processed.");
            }
            return summary(jobExplorer.getJobExecution((Long) record[1]));
        }

        jdbcTemplate.update("""
                INSERT INTO batch_job_request (principal_id, idempotency_key, request_hash, job_name, business_date, created_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, principal, idempotencyKey, requestHash, jobName, java.sql.Date.valueOf(businessDate));
        try {
            Job job = applicationContext.getBean(jobName, Job.class);
            JobParameters parameters = new JobParametersBuilder()
                    .addString("businessDate", businessDate.toString())
                    .addString("outputDirectory", outputDirectory.toString())
                    .addString(SecurityConstants.MDC_CORRELATION_ID,
                            correlationId == null ? MDC.get(SecurityConstants.MDC_CORRELATION_ID) : correlationId)
                    .toJobParameters();
            JobExecution execution = jobLauncher.run(job, parameters);
            jdbcTemplate.update("""
                    UPDATE batch_job_request SET execution_id = ?
                    WHERE principal_id = ? AND idempotency_key = ?
                    """, execution.getId(), principal, idempotencyKey);
            return summary(execution);
        } catch (Exception exception) {
            jdbcTemplate.update("DELETE FROM batch_job_request WHERE principal_id = ? AND idempotency_key = ? AND execution_id IS NULL",
                    principal, idempotencyKey);
            throw exception;
        }
    }

    private JobSummary summary(JobExecution execution) {
        if (execution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch execution not found.");
        }
        return new JobSummary(
                execution.getId(), execution.getJobInstance().getJobName(), execution.getStatus().name(),
                execution.getStartTime() == null ? null : execution.getStartTime().toString(),
                execution.getExitStatus().getExitCode());
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
        }
    }

    public record StartJobRequest(@NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String businessDate) {}
    public record JobSummary(Long executionId, String jobName, String status, String startedAt, String exitCode) {}
}

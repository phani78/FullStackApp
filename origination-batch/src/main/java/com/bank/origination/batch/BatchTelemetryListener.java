package com.bank.origination.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;
import com.bank.origination.security.SecurityConstants;

@Component
public class BatchTelemetryListener implements JobExecutionListener, SkipListener<Object, Object> {
    private static final Logger log = LoggerFactory.getLogger(BatchTelemetryListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String correlationId = jobExecution.getJobParameters().getString(SecurityConstants.MDC_CORRELATION_ID);
        if (correlationId != null) {
            MDC.put(SecurityConstants.MDC_CORRELATION_ID, correlationId);
        }
        log.info("Batch job started jobName={} executionId={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("Batch job completed jobName={} executionId={} status={} exitCode={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId(),
                jobExecution.getStatus(), jobExecution.getExitStatus().getExitCode());
        MDC.remove(SecurityConstants.MDC_CORRELATION_ID);
    }

    @Override
    public void onSkipInRead(Throwable throwable) {
        log.error("Batch record skipped phase=read errorType={}", throwable.getClass().getSimpleName());
    }

    @Override
    public void onSkipInWrite(Object item, Throwable throwable) {
        log.error("Batch record skipped phase=write errorType={}", throwable.getClass().getSimpleName());
    }

    @Override
    public void onSkipInProcess(Object item, Throwable throwable) {
        log.error("Batch record skipped phase=process errorType={}", throwable.getClass().getSimpleName());
    }
}

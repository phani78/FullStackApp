package com.bank.origination.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringBatchTest
@SpringJUnitConfig(BatchChunkIntegrationTest.TestConfiguration.class)
class BatchChunkIntegrationTest {
    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private ItemWriteRecorder recorder;

    @Test
    void chunkJobPersistsExecutionAndWritesAllItems() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder().addString("runId", UUID.randomUUID().toString()).toJobParameters());

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(List.of(1, 2, 3), recorder.items);
        assertTrue(execution.getStepExecutions().stream().allMatch(step -> step.getWriteCount() == 3));
    }

    @Configuration
    @EnableBatchProcessing
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .addScript("org/springframework/batch/core/schema-h2.sql")
                    .build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }

        @Bean
        ItemWriteRecorder itemWriteRecorder() {
            return new ItemWriteRecorder();
        }

        @Bean
        Step testChunkStep(JobRepository jobRepository, PlatformTransactionManager transactionManager, ItemWriteRecorder recorder) {
            return new StepBuilder("testChunkStep", jobRepository)
                    .<Integer, Integer>chunk(2, transactionManager)
                    .reader(new ListItemReader<>(List.of(1, 2, 3)))
                    .processor(item -> item)
                    .writer(chunk -> recorder.items.addAll(chunk.getItems()))
                    .build();
        }

        @Bean
        Job testChunkJob(JobRepository jobRepository, Step testChunkStep) {
            return new JobBuilder("testChunkJob", jobRepository).start(testChunkStep).build();
        }
    }

    static class ItemWriteRecorder {
        private final List<Integer> items = new ArrayList<>();
    }
}

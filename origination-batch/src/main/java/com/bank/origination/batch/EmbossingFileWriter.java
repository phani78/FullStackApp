package com.bank.origination.batch;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

public class EmbossingFileWriter implements ItemStreamWriter<EmbossingRecord>, StepExecutionListener {
    private static final Logger log = LoggerFactory.getLogger(EmbossingFileWriter.class);
    private static final String LINE_END = "\r\n";
    private final Path outputDirectory;
    private final LocalDate businessDate;
    private final JdbcTemplate jdbcTemplate;
    private final Set<String> applicationIds = ConcurrentHashMap.newKeySet();
    private Path temporaryFile;
    private Path completedFile;
    private BufferedWriter writer;
    private MessageDigest checksum;
    private long recordCount;

    public EmbossingFileWriter(Path outputDirectory, LocalDate businessDate, JdbcTemplate jdbcTemplate) {
        this.outputDirectory = outputDirectory;
        this.businessDate = businessDate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        try {
            Files.createDirectories(outputDirectory);
            String name = "embossing-" + businessDate + ".dat";
            temporaryFile = outputDirectory.resolve(name + ".tmp");
            completedFile = outputDirectory.resolve(name);
            if (Files.exists(completedFile)) {
                throw new ItemStreamException("Completed embossing output already exists; reconcile before rerun.");
            }
            checksum = MessageDigest.getInstance("SHA-256");
            if (Files.exists(temporaryFile)) {
                rebuildState();
                writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } else {
                writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                writeLine(headerLine(0, "0".repeat(64)));
            }
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new ItemStreamException("Unable to open embossing output.", exception);
        }
    }

    @Override
    public void write(Chunk<? extends EmbossingRecord> chunk) throws Exception {
        for (EmbossingRecord record : chunk) {
            String applicationId = record.applicationId().toString();
            if (!applicationIds.add(applicationId)) {
                continue;
            }
            String line = format(record);
            writeLine(line);
            checksum.update((line + LINE_END).getBytes(StandardCharsets.UTF_8));
            recordCount++;
        }
    }

    @Override
    public void update(ExecutionContext executionContext) {
        executionContext.putLong("embossing.recordCount", recordCount);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (!ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode())) {
            return stepExecution.getExitStatus();
        }
        try {
            String digest = HexFormat.of().formatHex(checksum.digest());
            writeLine("T" + String.format(Locale.ROOT, "%010d", recordCount) + digest);
            writer.close();
            rewriteHeader(headerLine(recordCount, digest));
            try {
                Files.move(temporaryFile, completedFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, completedFile);
            }
            Path manifest = completedFile.resolveSibling(completedFile.getFileName() + ".manifest");
            Files.writeString(manifest,
                    "businessDate=" + businessDate + "\nrecords=" + recordCount + "\nsha256=" + digest + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            persistManifest(stepExecution, manifest, digest);
            log.info("Embossing file generated jobExecutionId={} records={} checksum={}",
                    stepExecution.getJobExecutionId(), recordCount, digest);
            return ExitStatus.COMPLETED;
        } catch (Exception exception) {
            log.error("Embossing output finalization failed jobExecutionId={} errorType={}",
                    stepExecution.getJobExecutionId(), exception.getClass().getSimpleName());
            return ExitStatus.FAILED;
        }
    }

    private void persistManifest(StepExecution stepExecution, Path manifest, String digest) {
        jdbcTemplate.update("""
                INSERT INTO batch_execution (id, job_name, business_date, status, output_manifest, started_at, ended_at)
                VALUES (?, 'cardEmbossingJob', ?, 'COMPLETED', ?, ?, ?)
                ON CONFLICT (job_name, business_date) DO UPDATE
                SET status = EXCLUDED.status, output_manifest = EXCLUDED.output_manifest, ended_at = EXCLUDED.ended_at
                """,
                java.util.UUID.randomUUID(), Date.valueOf(businessDate), manifest.toString(),
                java.sql.Timestamp.valueOf(stepExecution.getStartTime()),
                java.sql.Timestamp.from(OffsetDateTime.now().toInstant()));
        for (String applicationId : applicationIds) {
            jdbcTemplate.update("UPDATE application SET embossing_exported_at = CURRENT_TIMESTAMP WHERE id = ?::uuid AND embossing_exported_at IS NULL",
                    applicationId);
        }
    }

    private void rebuildState() throws IOException {
        List<String> lines = Files.readAllLines(temporaryFile, StandardCharsets.UTF_8);
        List<String> recovered = new ArrayList<>();
        recovered.add(headerLine(0, "0".repeat(64)));
        for (String line : lines) {
            if (line.startsWith("D") && line.length() == 134) {
                recovered.add(line);
                checksum.update((line + LINE_END).getBytes(StandardCharsets.UTF_8));
                recordCount++;
                String applicationId = line.substring(1, 37).trim();
                if (!applicationId.isEmpty()) {
                    applicationIds.add(applicationId);
                }
            }
        }
        Files.writeString(temporaryFile, String.join(LINE_END, recovered) + LINE_END,
                StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private String headerLine(long count, String digest) {
        return "H" + businessDate.toString().replace("-", "")
                + String.format(Locale.ROOT, "%010d", count) + digest;
    }

    private void rewriteHeader(String header) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(temporaryFile.toFile(), "rw")) {
            file.seek(0);
            file.write(header.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeLine(String line) throws IOException {
        writer.write(line);
        writer.write(LINE_END);
    }

    private String format(EmbossingRecord record) {
        return "D"
                + fixed(record.applicationId().toString(), 36)
                + fixed(record.firstName(), 30)
                + fixed(record.lastName(), 30)
                + fixed(record.productCode(), 20)
                + String.format(Locale.ROOT, "%012.2f", amount(record.creditLimit()))
                + String.format(Locale.ROOT, "%05.2f", amount(record.apr()));
    }

    private String fixed(String value, int width) {
        String input = value == null ? "" : value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ");
        int codePoints = input.codePointCount(0, input.length());
        if (codePoints > width) {
            input = input.substring(0, input.offsetByCodePoints(0, width));
            codePoints = width;
        }
        return input + " ".repeat(width - codePoints);
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException exception) {
            throw new ItemStreamException("Unable to close embossing output.", exception);
        }
    }
}

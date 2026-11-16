package com.bank.origination.api.web;

import com.bank.origination.api.service.OriginationException;
import com.bank.origination.api.service.UnderwritingService;
import com.bank.origination.api.service.WorkQueueService;
import com.bank.origination.domain.ApiModels.ReviewDecisionRequest;
import com.bank.origination.domain.ApiModels.ReviewQueueItem;
import com.bank.origination.domain.ApiModels.SensitiveReviewItem;
import com.bank.origination.security.SecurityConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workbench")
@PreAuthorize("hasAnyRole('UNDERWRITER', 'UNDERWRITER_SUPERVISOR')")
public class WorkbenchController {
    private final WorkQueueService queueService;
    private final UnderwritingService underwritingService;

    public WorkbenchController(WorkQueueService queueService, UnderwritingService underwritingService) {
        this.queueService = queueService;
        this.underwritingService = underwritingService;
    }

    @GetMapping("/applications")
    public List<ReviewQueueItem> queue() {
        return queueService.getQueue();
    }

    @GetMapping("/sensitive-validations")
    @PreAuthorize("hasRole('UNDERWRITER_SUPERVISOR')")
    public List<SensitiveReviewItem> sensitiveQueue(
            Authentication authentication,
            @RequestHeader(value = SecurityConstants.CORRELATION_ID, required = false) String correlationId) {
        return queueService.getSensitiveQueue(authentication.getName(), correlation(correlationId));
    }

    @PostMapping("/applications/{applicationId}/claim")
    public void claim(
            @PathVariable UUID applicationId,
            @RequestHeader(SecurityConstants.IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SecurityConstants.CORRELATION_ID, required = false) String correlationId,
            Authentication authentication) {
        requireKey(idempotencyKey);
        queueService.claim(applicationId, authentication.getName(), correlation(correlationId), idempotencyKey);
    }

    @PostMapping("/applications/{applicationId}/notes")
    public void addNote(
            @PathVariable UUID applicationId,
            @Valid @RequestBody NoteRequest request,
            @RequestHeader(SecurityConstants.IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SecurityConstants.CORRELATION_ID, required = false) String correlationId,
            Authentication authentication) {
        requireKey(idempotencyKey);
        queueService.addNote(applicationId, authentication.getName(), request.note(),
                correlation(correlationId), idempotencyKey);
    }

    @PostMapping("/applications/{applicationId}/decisions")
    public void decide(
            @PathVariable UUID applicationId,
            @Valid @RequestBody ReviewDecisionRequest request,
            @RequestHeader(SecurityConstants.IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SecurityConstants.CORRELATION_ID, required = false) String correlationId,
            Authentication authentication) {
        requireKey(idempotencyKey);
        underwritingService.recordDecision(applicationId, authentication.getName(), request,
                correlation(correlationId), idempotencyKey);
    }

    @PostMapping("/applications/{applicationId}/decision-approval")
    @PreAuthorize("hasRole('UNDERWRITER_SUPERVISOR')")
    public void approveProposal(
            @PathVariable UUID applicationId,
            @Valid @RequestBody SupervisorDecision request,
            @RequestHeader(SecurityConstants.IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SecurityConstants.CORRELATION_ID, required = false) String correlationId,
            Authentication authentication) {
        requireKey(idempotencyKey);
        underwritingService.approveProposal(applicationId, authentication.getName(),
                request.approve(), correlation(correlationId), idempotencyKey);
    }

    private void requireKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,160}")) {
            throw new OriginationException(HttpStatus.BAD_REQUEST, "A valid X-Idempotency-Key header is required.");
        }
    }

    private String correlation(String correlationId) {
        return correlationId == null ? MDC.get(SecurityConstants.MDC_CORRELATION_ID) : correlationId;
    }

    public record NoteRequest(@NotBlank @Size(max = 2000) String note) {}
    public record SupervisorDecision(@NotNull Boolean approve, @Size(max = 2000) String note) {}
}

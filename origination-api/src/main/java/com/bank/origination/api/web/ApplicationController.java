package com.bank.origination.api.web;

import com.bank.origination.api.service.ApplicationService;
import com.bank.origination.domain.ApiModels.ApplicationReceipt;
import com.bank.origination.domain.ApiModels.ApplicationRequest;
import com.bank.origination.domain.ApiModels.ApplicationStatusResponse;
import com.bank.origination.domain.ApiModels.ProductResponse;
import com.bank.origination.security.SecurityConstants;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ApplicationController {
    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/products")
    public List<ProductResponse> products() {
        return applicationService.getProducts();
    }

    @PostMapping("/applications")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApplicationReceipt> submit(
            @Valid @RequestBody ApplicationRequest request,
            @RequestHeader(SecurityConstants.IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SecurityConstants.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = "X-Mock-Persona", required = false) String persona,
            Authentication authentication) {
        ApplicationReceipt receipt = applicationService.submit(
                request, idempotencyKey,
                correlationId == null ? MDC.get(SecurityConstants.MDC_CORRELATION_ID) : correlationId,
                authentication.getName(), persona);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/applications/" + receipt.applicationId()))
                .body(receipt);
    }

    @GetMapping("/applications/{applicationId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApplicationStatusResponse status(@PathVariable UUID applicationId, Authentication authentication) {
        return applicationService.getStatus(applicationId, authentication.getName());
    }
}

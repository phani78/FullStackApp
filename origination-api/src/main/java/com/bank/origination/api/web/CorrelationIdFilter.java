package com.bank.origination.api.web;

import com.bank.origination.security.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String supplied = request.getHeader(SecurityConstants.CORRELATION_ID);
        if (supplied != null && !VALID.matcher(supplied).matches()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid correlation identifier.");
            return;
        }
        String correlationId = supplied == null ? UUID.randomUUID().toString() : supplied;
        MDC.put(SecurityConstants.MDC_CORRELATION_ID, correlationId);
        response.setHeader(SecurityConstants.CORRELATION_ID, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(SecurityConstants.MDC_CORRELATION_ID);
        }
    }
}

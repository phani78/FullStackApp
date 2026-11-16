package com.bank.origination.batch;

import com.bank.origination.security.SecurityConstants;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class BatchApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String detail = status.is4xxClientError() ? exception.getReason() : "Batch request could not be completed.";
        return problem(status, detail, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed.", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Batch request could not be completed.", request);
    }

    private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        String correlationId = request.getHeader(SecurityConstants.CORRELATION_ID);
        problem.setProperty("correlationId", correlationId == null ? UUID.randomUUID().toString() : correlationId);
        return problem;
    }
}

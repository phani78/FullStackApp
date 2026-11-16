package com.bank.origination.api.web;

import com.bank.origination.api.service.OriginationException;
import com.bank.origination.security.SecurityConstants;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(OriginationException.class)
    ProblemDetail handleOrigination(OriginationException exception, HttpServletRequest request) {
        return problem(exception.getStatus(), safeMessage(exception), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Request parameters are invalid.", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail handleMissingHeader(MissingRequestHeaderException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "A required request header is missing.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Request body is invalid.", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", correlationId(request));
        return problem;
    }

    private String safeMessage(OriginationException exception) {
        return exception.getStatus().is5xxServerError() ? "An unexpected error occurred." : exception.getMessage();
    }

    private String correlationId(HttpServletRequest request) {
        String correlationId = request.getHeader(SecurityConstants.CORRELATION_ID);
        return correlationId == null ? UUID.randomUUID().toString() : correlationId;
    }
}

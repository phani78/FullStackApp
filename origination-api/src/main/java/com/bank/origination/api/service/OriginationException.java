package com.bank.origination.api.service;

import org.springframework.http.HttpStatus;

public class OriginationException extends RuntimeException {
    private final HttpStatus status;

    public OriginationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}

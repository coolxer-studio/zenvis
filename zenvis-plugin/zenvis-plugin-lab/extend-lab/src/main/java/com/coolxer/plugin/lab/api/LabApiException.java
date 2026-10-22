package com.coolxer.plugin.lab.api;

import org.springframework.http.HttpStatus;

public class LabApiException extends RuntimeException {

    private final HttpStatus status;

    public LabApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static LabApiException badRequest(String message) {
        return new LabApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static LabApiException notFound(String message) {
        return new LabApiException(HttpStatus.NOT_FOUND, message);
    }

    public static LabApiException unavailable(String message) {
        return new LabApiException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}

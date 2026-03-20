package com.tirupurconnect.exception;
import org.springframework.http.HttpStatus;

public class SearchException extends AppException {
    public SearchException(String message, Throwable cause) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
        initCause(cause);
    }
}

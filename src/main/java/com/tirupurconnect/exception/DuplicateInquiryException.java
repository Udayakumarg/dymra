package com.tirupurconnect.exception;
import org.springframework.http.HttpStatus;

public class DuplicateInquiryException extends AppException {
    public DuplicateInquiryException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}

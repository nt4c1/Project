package com.health.common.exception;

import io.grpc.Status;

public class BookingException extends DomainException {
    public BookingException(String message) {
        super(message, Status.FAILED_PRECONDITION);
    }
}
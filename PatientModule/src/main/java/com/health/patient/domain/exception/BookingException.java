package com.health.patient.domain.exception;


import io.grpc.Status;

public class BookingException extends DomainException {

    public BookingException(String message) {
        super(message,Status.RESOURCE_EXHAUSTED);
    }
}

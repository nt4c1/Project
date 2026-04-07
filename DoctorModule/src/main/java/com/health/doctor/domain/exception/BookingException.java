package com.health.doctor.domain.exception;

import com.health.doctor.domain.exception.DomainException;
import io.grpc.Status;

public class BookingException extends DomainException {

    public BookingException(String message) {
        super(message,Status.RESOURCE_EXHAUSTED);
    }
}

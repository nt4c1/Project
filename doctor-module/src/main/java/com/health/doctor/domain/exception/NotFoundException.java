package com.health.doctor.domain.exception;

import com.health.doctor.domain.exception.DomainException;
import io.grpc.Status;

public class NotFoundException extends DomainException {
    public NotFoundException(String message) {
        super(message, Status.NOT_FOUND);
    }
}

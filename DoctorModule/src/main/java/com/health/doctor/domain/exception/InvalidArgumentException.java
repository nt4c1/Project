package com.health.doctor.domain.exception;

import com.health.doctor.domain.exception.DomainException;
import io.grpc.Status;

public class InvalidArgumentException extends DomainException {
    public InvalidArgumentException(String message) {
        super(message, Status.INVALID_ARGUMENT);
    }
}

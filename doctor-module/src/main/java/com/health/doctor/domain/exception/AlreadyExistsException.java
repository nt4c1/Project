package com.health.doctor.domain.exception;

import com.health.doctor.domain.exception.DomainException;
import io.grpc.Status;

public class AlreadyExistsException extends DomainException {

    public AlreadyExistsException(String message) {
        super(message, Status.ALREADY_EXISTS);
    }
}

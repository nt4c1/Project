package com.health.common.exception;

import io.grpc.Status;

public class AlreadyExistsException extends DomainException {
    public AlreadyExistsException(String message) {
        super(message, Status.ALREADY_EXISTS);
    }
}
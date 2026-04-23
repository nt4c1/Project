package com.health.common.exception;

import io.grpc.Status;

public class NotFoundException extends DomainException {
    public NotFoundException(String message) {
        super(message, Status.NOT_FOUND);
    }
}
package com.health.common.exception;

import io.grpc.Status;

public class InvalidArgumentException extends DomainException {
    public InvalidArgumentException(String message) {
        super(message, Status.INVALID_ARGUMENT);
    }
}
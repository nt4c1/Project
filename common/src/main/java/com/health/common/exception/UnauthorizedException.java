package com.health.common.exception;

import io.grpc.Status;

public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String message) {
        super(message, Status.PERMISSION_DENIED);
    }
}

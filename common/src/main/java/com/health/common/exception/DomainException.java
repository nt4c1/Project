package com.health.common.exception;

import io.grpc.Status;

public class DomainException extends RuntimeException {
    private final Status grpcStatus;

    public DomainException(String message, Status grpcStatus) {
        super(message);
        this.grpcStatus = grpcStatus;
    }

    public Status getGrpcStatus() {
        return grpcStatus;
    }
}
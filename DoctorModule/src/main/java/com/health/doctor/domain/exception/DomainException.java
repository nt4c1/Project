package com.health.doctor.domain.exception;

import io.grpc.Status;

public abstract class DomainException extends RuntimeException{
    private final Status grpcStatus;

    public DomainException(String message,Status grpcStatus) {
        super(message);
        this.grpcStatus = grpcStatus;
    }

    public Status getGrpcStatus(){return grpcStatus;}
}

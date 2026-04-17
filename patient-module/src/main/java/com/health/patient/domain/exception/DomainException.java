package com.health.patient.domain.exception;

import io.grpc.Status;
import lombok.Getter;

@Getter
public class DomainException extends RuntimeException{
    private final Status grpcStatus;

    public DomainException(String message,Status grpcStatus) {
        super(message);
        this.grpcStatus = grpcStatus;
    }

}

package com.health.doctor.domain.exception;

import io.grpc.Status;

public class ScheduleException extends DomainException {
    public ScheduleException(String message) {
        super(message, Status.INVALID_ARGUMENT);
    }
}

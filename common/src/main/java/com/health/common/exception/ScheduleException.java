package com.health.common.exception;

import io.grpc.Status;

public class ScheduleException extends DomainException {
    public ScheduleException(String message) {
        super(message, Status.FAILED_PRECONDITION);
    }
}
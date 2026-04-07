package com.health.doctor.application.usecase.interfaces;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface CreateAppointmentUseCaseInterface {
    UUID execute(UUID doctorId, UUID patientId, LocalDate date, LocalTime time);
}
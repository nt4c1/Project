package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.Appointment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GetPendingAppointmentsUseCaseInterface {
    List<Appointment> execute(UUID doctorId, LocalDate date);
}

package com.health.doctor.application.usecase.interfaces;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface CancelAppointmnetInterface {
    void execute(UUID AppointmentId, UUID PatientId, UUID DoctorId, LocalDate date, Instant time);
}

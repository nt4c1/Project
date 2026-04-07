package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.usecase.interfaces.CancelAppointmnetInterface;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Singleton
public class CancelAppointmentUseCase implements CancelAppointmnetInterface {
    private final AppointmentRepositoryPort repo;

    public CancelAppointmentUseCase(AppointmentRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public void execute(UUID AppointmentId, UUID PatientId, UUID DoctorId, LocalDate date, Instant time) {
        repo.cancel(AppointmentId,
                PatientId,
                DoctorId,
                date,
                time
                );
    }
}

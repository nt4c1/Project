package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.usecase.interfaces.GetPendingAppointmentsUseCaseInterface;
import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Singleton
public class GetPendingAppointmentsUseCase implements GetPendingAppointmentsUseCaseInterface {

    private final AppointmentRepositoryPort repo;

    public GetPendingAppointmentsUseCase(AppointmentRepositoryPort repo) {
        this.repo = repo;
    }

    public List<Appointment> execute(UUID doctorId, LocalDate date) {
        return repo.findPending(doctorId, date);
    }
}
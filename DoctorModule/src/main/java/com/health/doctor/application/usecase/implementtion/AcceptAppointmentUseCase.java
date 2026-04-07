package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.usecase.interfaces.AcceptAppointmentUseCaseInterface;
import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.AppointmentStatus;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import jakarta.inject.Singleton;
import jakarta.validation.ValidationException;

@Singleton
public class AcceptAppointmentUseCase implements AcceptAppointmentUseCaseInterface {
    private final AppointmentRepositoryPort repo;

    public AcceptAppointmentUseCase(AppointmentRepositoryPort repo) {
        this.repo = repo;
    }

    public void execute (Appointment appointment){
        if(appointment==null)
            throw new ValidationException("Appointment cannot be null");
        if(appointment.getStatus() != AppointmentStatus.PENDING)
            throw new ValidationException(
                    "Only Pending Appointments can be accepted, Current: "+appointment.getStatus()
            );
        repo.updateStatus(appointment, AppointmentStatus.ACCEPTED.name());
    }
}

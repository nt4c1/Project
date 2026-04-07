package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.usecase.interfaces.PostponeAppointmentUseCaseInterface;
import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.AppointmentStatus;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import jakarta.inject.Singleton;
import jakarta.validation.ValidationException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Singleton
public class PostponeAppointmentUseCase implements PostponeAppointmentUseCaseInterface {
    private final AppointmentRepositoryPort repo;

    public PostponeAppointmentUseCase(AppointmentRepositoryPort repo) {
        this.repo = repo;
    }

    public void execute(Appointment appointment){
        if (appointment.getStatus() == AppointmentStatus.CANCELLED)
            throw new ValidationException("Cannot Postpone a Cancelled Appointment");

        LocalDate newDate = appointment.getAppointmentDate().plusDays(1);

        Appointment postponed = new Appointment(
                appointment.getId(),
                appointment.getDoctorId(),
                appointment.getPatientId(),
                newDate,
                appointment.getScheduleTime(),
                AppointmentStatus.POSTPONED,
                appointment.getCreatedAt(),
                ZonedDateTime.now(ZoneId.of("Asia/Kathmandu")).toInstant()
        );
        repo.updateStatus(postponed, AppointmentStatus.POSTPONED.name());
    }
}

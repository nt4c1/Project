package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.usecase.interfaces.CreateAppointmentUseCaseInterface;
import com.health.doctor.domain.exception.*;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import com.health.doctor.domain.ports.ScheduleRepositoryPort;
import jakarta.inject.Singleton;
import jakarta.validation.ValidationException;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class CreateAppointmentUseCase implements CreateAppointmentUseCaseInterface {

    private static final ZoneId NPT = ZoneId.of("Asia/Kathmandu");
    private final AppointmentRepositoryPort repo;
    private final ScheduleRepositoryPort scheduleRepo;

    public CreateAppointmentUseCase(AppointmentRepositoryPort repo,
                                        ScheduleRepositoryPort scheduleRepo) {
        this.repo = repo;
        this.scheduleRepo = scheduleRepo;
    }

    @Override
    public UUID execute(UUID doctorId, UUID patientId, LocalDate date, LocalTime time) {
        if (doctorId == null) throw new ValidationException("doctorId is required");
        if (patientId == null) throw new ValidationException("patientId is required");
        if (date == null) throw new ValidationException("date is required");
        if (time == null) throw new ValidationException("time is required");

        DoctorSchedule schedule = scheduleRepo.findByDoctorId(doctorId);

        if(schedule==null){
            throw new NotFoundException("Doctor schedule not found");
        }
        if (!schedule.isWorkingDay(date.getDayOfWeek())) {
            throw new ValidationException(
                    "Doctor does not work on " + date.getDayOfWeek());
        }

        Instant appointmentDateTime = ZonedDateTime.of(date, time, NPT).toInstant();
        if (!schedule.isValidSlot(appointmentDateTime)) {
            throw new ValidationException("Selected time is outside of doctor's working hours");
        }

        Instant now = ZonedDateTime.now(NPT).toInstant();
        UUID appointmentId = UUID.randomUUID();

        Appointment appointment = new Appointment(
                appointmentId, doctorId, patientId,
                date, time, AppointmentStatus.PENDING, now, now
        );
        repo.save(appointment);
        return appointmentId;
    }
}
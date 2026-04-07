package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.usecase.interfaces.AppointmentInterface;
import com.health.doctor.domain.exception.NotFoundException;
import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.AppointmentStatus;
import com.health.doctor.domain.model.DoctorSchedule;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import com.health.doctor.domain.ports.ScheduleRepositoryPort;
import jakarta.validation.ValidationException;

import java.time.*;
import java.util.List;
import java.util.UUID;

public class AppointmentUseCase implements AppointmentInterface {


    private static final ZoneId NPT = ZoneId.of("Asia/Kathmandu");
    private final AppointmentRepositoryPort repo;
    private final ScheduleRepositoryPort scheduleRepo;

    public AppointmentUseCase(AppointmentRepositoryPort repo, ScheduleRepositoryPort scheduleRepo) {
        this.repo = repo;
        this.scheduleRepo = scheduleRepo;
    }


    @Override
    public UUID createAppointment(UUID doctorId, UUID patientId, LocalDate date, LocalTime time) {
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

    @Override
    public void acceptAppointment(Appointment appointment) {
        if(appointment==null)
            throw new ValidationException("Appointment cannot be null");
        if(appointment.getStatus() != AppointmentStatus.PENDING)
            throw new ValidationException(
                    "Only Pending Appointments can be accepted, Current: "+appointment.getStatus()
            );
        repo.updateStatus(appointment, AppointmentStatus.ACCEPTED.name());
    }

    @Override
    public void cancelAppointment(UUID AppointmentId, UUID PatientId, UUID DoctorId, LocalDate date, Instant time) {
        repo.cancel(AppointmentId,
                PatientId,
                DoctorId,
                date,
                time
        );
    }

    @Override
    public void postponeAppointment(Appointment appointment) {
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

    @Override
    public List<Appointment> getAppointment(UUID doctorId, LocalDate date) {
        return repo.findByDoctorAndDate(doctorId, date);
    }

    @Override
    public List<Appointment> pendingAppointment(UUID doctorId, LocalDate date) {
            return repo.findPending(doctorId, date);
        }
    }

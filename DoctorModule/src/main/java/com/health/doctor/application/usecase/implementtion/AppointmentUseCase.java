package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.usecase.interfaces.AppointmentInterface;
import com.health.doctor.domain.exception.InvalidArgumentException;
import com.health.doctor.domain.exception.NotFoundException;
import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.AppointmentStatus;
import com.health.doctor.domain.model.DoctorSchedule;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import com.health.doctor.domain.ports.ScheduleRepositoryPort;
import jakarta.inject.Singleton;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Singleton
public class AppointmentUseCase implements AppointmentInterface {


    private static final ZoneId NPT = ZoneId.of("Asia/Kathmandu");
    private final AppointmentRepositoryPort repo;
    private final ScheduleRepositoryPort scheduleRepo;

    public AppointmentUseCase(AppointmentRepositoryPort repo, ScheduleRepositoryPort scheduleRepo) {
        this.repo = repo;
        this.scheduleRepo = scheduleRepo;
    }

    @Override
    public UUID createAppointment(UUID doctorId, UUID patientId, LocalDate date, LocalTime time, String reasonForVisit) {
        if (doctorId == null) throw new InvalidArgumentException("Doctor ID is required");
        if (patientId == null) throw new InvalidArgumentException("Patient ID is required");
        if (date == null) throw new InvalidArgumentException("Date is required");
        if (time == null) throw new InvalidArgumentException("Time is required");

        DoctorSchedule schedule = scheduleRepo.findByDoctorId(doctorId)
                .orElseThrow(() -> new NotFoundException("Doctor schedule not found for doctor: " + doctorId));

        if (!schedule.isWorkingDay(date.getDayOfWeek())) {
            throw new InvalidArgumentException(
                    "Doctor does not work on " + date.getDayOfWeek());
        }

        if(time.isBefore(schedule.getStartTime()))
            throw new InvalidArgumentException("Selected time is before doctor's opening time");

        if (!schedule.isValidSlot(time)) {
            throw new InvalidArgumentException("Selected time is outside of doctor's working hours");
        }

        Instant now = Instant.now(Clock.system(NPT));
        UUID appointmentId = UUID.randomUUID();

        Appointment appointment = new Appointment(
                appointmentId, doctorId, patientId,
                date, time, AppointmentStatus.PENDING, now, now
        );
        appointment.setReasonForVisit(reasonForVisit);
        repo.save(appointment);
        return appointmentId;
    }

    @Override
    public void acceptAppointment(Appointment appointment) {
        if(appointment == null)
            throw new InvalidArgumentException("Appointment cannot be null");
        if(appointment.getStatus() != AppointmentStatus.PENDING)
            throw new InvalidArgumentException(
                    "Only Pending Appointments can be accepted, Current status: " + appointment.getStatus()
            );
        repo.updateStatus(appointment, AppointmentStatus.ACCEPTED.name());
    }

    @Override
    public void cancelAppointment(UUID appointmentId, UUID patientId, UUID doctorId, LocalDate date, LocalTime time, String cancellationReason) {
        if (appointmentId == null) throw new InvalidArgumentException("Appointment ID is required");
        repo.cancel(appointmentId,
                patientId,
                doctorId,
                date,
                time,
                cancellationReason
        );
    }

    @Override
    public void postponeAppointment(Appointment appointment) {
        if (appointment == null) throw new InvalidArgumentException("Appointment cannot be null");
        if (appointment.getStatus() == AppointmentStatus.CANCELLED)
            throw new InvalidArgumentException("Cannot postpone a cancelled appointment");

        DoctorSchedule schedule = scheduleRepo.findByDoctorId(appointment.getDoctorId())
                .orElseThrow(() -> new InvalidArgumentException("Doctor schedule not found for doctor: " + appointment.getDoctorId()));

        LocalDate nextWorkingDay = nextWorkingDay(appointment.getAppointmentDate(), schedule);
        LocalDate oldDate = appointment.getAppointmentDate();

        Appointment postponed = new Appointment(
                appointment.getId(),
                appointment.getDoctorId(),
                appointment.getPatientId(),
                nextWorkingDay,
                appointment.getScheduleTime(),
                AppointmentStatus.POSTPONED,
                appointment.getCreatedAt(),
                ZonedDateTime.now(NPT).toInstant()
        );
        repo.postpone(oldDate, postponed);
    }

    private LocalDate nextWorkingDay(LocalDate from,DoctorSchedule schedule) {
        Set<DayOfWeek> workingDays = schedule.getWorkingDays();

        if(workingDays == null || workingDays.isEmpty())
            throw new InvalidArgumentException("Doctor has no working days configured");

        LocalDate candidate = from.plusDays(1);
        int limit = 30;
        while (limit-- > 0 ){
            if (workingDays.contains(candidate.getDayOfWeek())){
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        throw new InvalidArgumentException(
                "No Working Days Found"+schedule.getDoctorId()
        );
    }

    @Override
    public List<Appointment> getAppointment(UUID doctorId, LocalDate date) {
        if (doctorId == null) throw new InvalidArgumentException("Doctor ID is required");
        if (date == null) throw new InvalidArgumentException("Date is required");
        return repo.findByDoctorAndDate(doctorId, date);
    }

    @Override
    public List<Appointment> pendingAppointment(UUID doctorId, LocalDate date) {
        if (doctorId == null) throw new InvalidArgumentException("Doctor ID is required");
        if (date == null) throw new InvalidArgumentException("Date is required");
        return repo.findPending(doctorId, date);
    }
}
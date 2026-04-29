package com.health.doctor.application.usecase.implementation;

import com.health.common.exception.NotFoundException;
import com.health.doctor.application.usecase.interfaces.AppointmentInterface;
import com.health.common.exception.InvalidArgumentException;
import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.AppointmentStatus;
import com.health.doctor.domain.model.DoctorSchedule;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import com.health.doctor.domain.ports.ScheduleRepositoryPort;
import com.health.doctor.adapters.output.nats.DoctorNatsClient;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.micronaut.validation.Validated;
import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Singleton
@Validated
public class AppointmentUseCase implements AppointmentInterface {

    private static final ZoneId NPT = ZoneId.of("Asia/Kathmandu");
    private final AppointmentRepositoryPort repo;
    private final ScheduleRepositoryPort scheduleRepo;
    private final DoctorNatsClient natsClient;

    public AppointmentUseCase(AppointmentRepositoryPort repo,
                              ScheduleRepositoryPort scheduleRepo,
                              DoctorNatsClient natsClient) {
        this.repo = repo;
        this.scheduleRepo = scheduleRepo;
        this.natsClient = natsClient;
    }

    @Override
    public UUID createAppointment(@NotNull UUID doctorId,
                                   @NotNull UUID patientId,
                                   @NotNull UUID clinicId,
                                   @NotNull @FutureOrPresent LocalDate date,
                                   @NotNull LocalTime time,
                                   @NotBlank String reasonForVisit) {

        // Trigger all database checks in parallel CompletableFuture for faster response
        java.util.concurrent.CompletableFuture<Optional<DoctorSchedule>> scheduleFuture = 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> scheduleRepo.findByDoctorAndClinic(doctorId, clinicId));
        
        java.util.concurrent.CompletableFuture<Long> countFuture = 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> repo.countByDoctorAndDate(doctorId, date));
        
        java.util.concurrent.CompletableFuture<Boolean> patientExistsFuture = 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> repo.existsByPatientDoctorAndDate(patientId, doctorId, date));
        
        java.util.concurrent.CompletableFuture<Boolean> slotTakenFuture = 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> repo.existsByDoctorAndSlot(doctorId, date, time));

        // Join all futures
        DoctorSchedule schedule = scheduleFuture.join()
                .orElseThrow(() -> new NotFoundException("Doctor schedule not found for doctor: " + doctorId + " at clinic: " + clinicId));

        if (!schedule.isWorkingDay(date.getDayOfWeek())) {
            throw new InvalidArgumentException("Doctor does not work on " + date.getDayOfWeek());
        }

        if (!schedule.isValidSlot(time)) {
            throw new InvalidArgumentException("Selected time is invalid. Please select a time within working hours that aligns with the " + schedule.getSlotDurationMinutes() + " minute slots.");
        }

        if (countFuture.join() >= schedule.getMaxAppointmentsPerDay()) {
            throw new InvalidArgumentException("Doctor is fully booked for " + date + ". Max appointments: " + schedule.getMaxAppointmentsPerDay());
        }

        if (patientExistsFuture.join()) {
            throw new InvalidArgumentException("You already have an appointment with this doctor on " + date);
        }

        if (slotTakenFuture.join()) {
            throw new InvalidArgumentException("This appointment slot is already booked: " + time);
        }

        Instant now = Instant.now(Clock.system(NPT));
        UUID appointmentId = UUID.randomUUID();

        Appointment appointment = new Appointment(
                appointmentId, doctorId, patientId,
                date, time, AppointmentStatus.APPOINTMENT_STATUS_PENDING, now, now
        );
        appointment.setClinicId(clinicId);
        appointment.setReasonForVisit(reasonForVisit);
        repo.save(appointment);
        
        natsClient.sendAppointmentCreated(appointmentId.toString());
        
        return appointmentId;
    }

    @Override
    public void acceptAppointment(@NotNull Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.APPOINTMENT_STATUS_PENDING)
            throw new InvalidArgumentException(
                    "Only Pending Appointments can be accepted, Current status: " + appointment.getStatus()
            );
        repo.updateStatus(appointment, AppointmentStatus.APPOINTMENT_STATUS_ACCEPTED.name());
        natsClient.sendAppointmentAccepted(appointment.getId().toString());
    }

    @Override
    public void cancelAppointment(@NotNull UUID appointmentId,
                                   @NotNull UUID patientId,
                                   @NotNull UUID doctorId,
                                   @NotNull LocalDate date,
                                   @NotNull LocalTime time,
                                   @NotBlank String cancellationReason) {
        repo.cancel(appointmentId, patientId, doctorId, date, time, cancellationReason);
        natsClient.sendAppointmentCancelled(appointmentId.toString());
    }

    @Override
    public void postponeAppointment(@NotNull Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.APPOINTMENT_STATUS_CANCELLED)
            throw new InvalidArgumentException("Cannot postpone a cancelled appointment");

        DoctorSchedule schedule = scheduleRepo.findByDoctorAndClinic(appointment.getDoctorId(), appointment.getClinicId())
                .orElseThrow(() -> new InvalidArgumentException("Doctor schedule not found"));

        LocalDate nextWorkingDay = nextWorkingDay(appointment.getAppointmentDate(), schedule);
        LocalDate oldDate = appointment.getAppointmentDate();

        Appointment postponed = new Appointment(
                appointment.getId(),
                appointment.getDoctorId(),
                appointment.getPatientId(),
                nextWorkingDay,
                appointment.getScheduleTime(),
                AppointmentStatus.APPOINTMENT_STATUS_POSTPONED,
                appointment.getCreatedAt(),
                ZonedDateTime.now(NPT).toInstant()
        );
        postponed.setClinicId(appointment.getClinicId());
        repo.postpone(oldDate, postponed);
        natsClient.sendAppointmentPostponed(appointment.getId().toString());
    }

    private LocalDate nextWorkingDay(LocalDate from, DoctorSchedule schedule) {
        Set<DayOfWeek> workingDays = schedule.getWorkingDays();
        if (workingDays == null || workingDays.isEmpty())
            throw new InvalidArgumentException("Doctor has no working days configured");

        LocalDate candidate = from.plusDays(1);
        int limit = 30;
        while (limit-- > 0) {
            if (workingDays.contains(candidate.getDayOfWeek())) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        throw new InvalidArgumentException("No Working Days Found in next 30 days");
    }

    @Override
    public List<Appointment> getAppointment(@NotNull UUID doctorId, @NotNull LocalDate date) {
        return repo.findByDoctorAndDate(doctorId, date);
    }

    @Override
    public List<Appointment> pendingAppointment(@NotNull UUID doctorId, @NotNull LocalDate date) {
        return repo.findPending(doctorId, date);
    }
}

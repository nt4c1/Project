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
import com.health.doctor.mapper.MapperClass;
import com.health.grpc.notification.AppointmentAcceptedEvent;
import com.health.grpc.notification.AppointmentBookedEvent;
import com.health.grpc.notification.AppointmentCancelledEvent;
import com.health.grpc.notification.AppointmentCompletedEvent;
import com.health.grpc.notification.AppointmentNoShowEvent;
import com.health.grpc.notification.AppointmentPostponedEvent;
import io.micronaut.serde.annotation.Serdeable;
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
@Serdeable.Serializable
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
        
        natsClient.sendAppointmentCreated(AppointmentBookedEvent.newBuilder()
                .setAppointment(MapperClass.toApptMsg(appointment))
                .build().toByteArray());
        
        return appointmentId;
    }

    @Override
    public void acceptAppointment(@NotNull Appointment appointment, String meetingLink) {
        if (appointment.getStatus() != AppointmentStatus.APPOINTMENT_STATUS_PENDING)
            throw new InvalidArgumentException(
                    "Only Pending Appointments can be accepted, Current status: " + appointment.getStatus()
            );
        
        appointment.setMeetingLink(meetingLink);
        repo.updateStatus(appointment, AppointmentStatus.APPOINTMENT_STATUS_ACCEPTED.name());
        appointment.setStatus(AppointmentStatus.APPOINTMENT_STATUS_ACCEPTED);
        
        natsClient.sendAppointmentAccepted(AppointmentAcceptedEvent.newBuilder()
                .setAppointment(MapperClass.toApptMsg(appointment))
                .build().toByteArray());
    }

    @Override
    public void cancelAppointment(@NotNull UUID appointmentId,
                                   @NotNull UUID patientId,
                                   @NotNull UUID doctorId,
                                   @NotNull LocalDate date,
                                   @NotNull LocalTime time,
                                   @NotBlank String cancellationReason) {
        repo.cancel(appointmentId, patientId, doctorId, date, time, cancellationReason);
        
        // Build a minimal appointment for the event
        Appointment a = new Appointment(appointmentId, doctorId, patientId, date, time, 
                AppointmentStatus.APPOINTMENT_STATUS_CANCELLED, Instant.now(), Instant.now());
        a.setCancellationReason(cancellationReason);
        
        natsClient.sendAppointmentCancelled(AppointmentCancelledEvent.newBuilder()
                .setAppointment(MapperClass.toApptMsg(a))
                .setCancelledBy("UNKNOWN") // Could be enriched if we had more context
                .build().toByteArray());
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
        
        natsClient.sendAppointmentPostponed(AppointmentPostponedEvent.newBuilder()
                .setAppointment(MapperClass.toApptMsg(postponed))
                .setOldDate(oldDate.toString())
                .build().toByteArray());
    }

    @Override
    public void completeAppointment(@NotNull Appointment appointment) {
        validateStatusUpdate(appointment);
        repo.updateStatus(appointment, AppointmentStatus.APPOINTMENT_STATUS_COMPLETED.name());
        appointment.setStatus(AppointmentStatus.APPOINTMENT_STATUS_COMPLETED);

        natsClient.sendAppointmentCompleted(AppointmentCompletedEvent.newBuilder()
                .setAppointment(MapperClass.toApptMsg(appointment))
                .build().toByteArray());
    }

    @Override
    public void noShowAppointment(@NotNull Appointment appointment) {
        validateStatusUpdate(appointment);
        repo.updateStatus(appointment, AppointmentStatus.APPOINTMENT_STATUS_NO_SHOW.name());
        appointment.setStatus(AppointmentStatus.APPOINTMENT_STATUS_NO_SHOW);

        natsClient.sendAppointmentNoShow(AppointmentNoShowEvent.newBuilder()
                .setAppointment(MapperClass.toApptMsg(appointment))
                .build().toByteArray());
    }

    private void validateStatusUpdate(Appointment appointment) {
        ZonedDateTime nowNpt = ZonedDateTime.now(NPT);
        LocalDate today = nowNpt.toLocalDate();
        LocalTime nowTime = nowNpt.toLocalTime();

        if (!appointment.getAppointmentDate().equals(today)) {
            throw new InvalidArgumentException("This action can only be performed on the date of the appointment.");
        }
        if (nowTime.isBefore(appointment.getScheduleTime())) {
            throw new InvalidArgumentException("This action can only be performed after the appointment time.");
        }

        if (appointment.getStatus() != AppointmentStatus.APPOINTMENT_STATUS_ACCEPTED && 
            appointment.getStatus() != AppointmentStatus.APPOINTMENT_STATUS_POSTPONED) {
            throw new InvalidArgumentException("Only Accepted or Postponed appointments can be marked as Completed or No-Show. Current status: " + appointment.getStatus());
        }
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

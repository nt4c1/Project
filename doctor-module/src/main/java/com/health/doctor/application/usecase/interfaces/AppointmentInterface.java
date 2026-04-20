package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.Appointment;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentInterface {

    UUID createAppointment(@NotNull UUID doctorId,
                           @NotNull UUID patientId,
                           @NotNull UUID clinicId,
                           @NotNull @FutureOrPresent LocalDate date,
                           @NotNull LocalTime time,
                           @NotBlank String reasonForVisit);

    void acceptAppointment(@NotNull Appointment appointment);

    void cancelAppointment(@NotNull UUID appointmentId,
                           @NotNull UUID patientId,
                           @NotNull UUID doctorId,
                           @NotNull LocalDate date,
                           @NotNull LocalTime time,
                           @NotBlank String cancellationReason);

    void postponeAppointment(@NotNull Appointment appointment);

    List<Appointment> getAppointment(@NotNull UUID doctorId, @NotNull LocalDate date);

    List<Appointment> pendingAppointment(@NotNull UUID doctorId, @NotNull LocalDate date);
}
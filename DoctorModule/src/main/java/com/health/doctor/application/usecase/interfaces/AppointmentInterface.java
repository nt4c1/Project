package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.Appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentInterface {

    UUID createAppointment(UUID doctorId, UUID patientId,
                           LocalDate date, LocalTime time,
                           String reasonForVisit);

    void acceptAppointment(Appointment appointment);

    void cancelAppointment(UUID appointmentId, UUID patientId, UUID doctorId,
                           LocalDate date, LocalTime time,
                           String cancellationReason);

    void postponeAppointment(Appointment appointment);

    List<Appointment> getAppointment(UUID doctorId, LocalDate date);

    List<Appointment> pendingAppointment(UUID doctorId, LocalDate date);
}
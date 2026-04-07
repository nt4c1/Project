package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.Appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentInterface {
    UUID createAppointment(UUID doctorId, UUID patientId, LocalDate date, LocalTime time);
    void acceptAppointment(Appointment appointment);
    void cancelAppointment(UUID AppointmentId, UUID PatientId, UUID DoctorId, LocalDate date, Instant time);
    void postponeAppointment(Appointment appointment);
    List<Appointment> getAppointment(UUID doctorId, LocalDate date);
    List<Appointment> pendingAppointment(UUID doctorId, LocalDate date);
}

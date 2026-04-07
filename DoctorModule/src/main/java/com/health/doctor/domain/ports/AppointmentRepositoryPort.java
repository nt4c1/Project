package com.health.doctor.domain.ports;

import com.health.doctor.domain.model.Appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepositoryPort {
    void save(Appointment appointment);
    List<Appointment> findByDoctorAndDate(UUID doctorId, LocalDate date);
    List<Appointment> findDoctorAndStatus(UUID doctorId, String status, LocalDate date);
    List<Appointment> findPending(UUID doctorId, LocalDate date);
    List<Appointment> findByPatientAndDate(UUID patientId, LocalDate date);
    void updateStatus(Appointment appointment, String newStatus);
    void cancel(UUID appointmentId, UUID patientId, UUID doctorId, LocalDate date, Instant time);
    void decrementCount(UUID doctorId, LocalDate date);
}
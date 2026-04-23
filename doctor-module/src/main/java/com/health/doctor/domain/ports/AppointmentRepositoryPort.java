package com.health.doctor.domain.ports;

import com.health.doctor.domain.model.Appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepositoryPort {

    void save(Appointment appointment);

    List<Appointment> findByDoctorAndDate(UUID doctorId, LocalDate date);

    List<Appointment> findDoctorAndStatus(UUID doctorId, String status, LocalDate date);

    List<Appointment> findPending(UUID doctorId, LocalDate date);

    List<Appointment> findByPatientAndDate(UUID patientId, LocalDate date);

    void updateStatus(Appointment appointment, String newStatus);

    void cancel(UUID appointmentId, UUID patientId, UUID doctorId,
                LocalDate date, LocalTime time, String cancellationReason);

    Optional<Appointment> findById(UUID appointmentId);

    long countByDoctorAndDate(UUID doctorId, LocalDate date);

    boolean existsByDoctorAndSlot(UUID doctorId, LocalDate date, LocalTime time);

    boolean existsByPatientDoctorAndDate(UUID patientId, UUID doctorId, LocalDate date);

    void decrementCount(UUID doctorId, LocalDate date);

    void postpone(LocalDate oldDate , Appointment updated);
}
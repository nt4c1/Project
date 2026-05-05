package com.health.doctor;

import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.DoctorSchedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorModuleApi {

    // ── Doctor discovery ──────────────────────────────────────────────────────
    List<Doctor> getDoctorsByLocation(String location, com.health.grpc.common.AvailabilityFilter filter);
    Optional<DoctorSchedule> getDoctorSchedule(UUID doctorId, UUID clinicId);

    // ── Appointments (patient-initiated actions) ──────────────────────────────
    UUID bookAppointment(UUID doctorId, UUID patientId, UUID clinicId,
                         LocalDate date, LocalTime time,
                         String reasonForVisit);

    void cancelAppointment(UUID appointmentId, UUID patientId, UUID doctorId,
                           LocalDate date, LocalTime time,
                           String cancellationReason);

    List<Appointment> getPatientAppointments(UUID patientId, LocalDate date);
}

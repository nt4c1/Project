package com.health.doctor;

import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.DoctorSchedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The public API surface of the doctor module.
 *
 * Why a dedicated interface instead of importing use cases directly?
 *   Direct use-case imports couple patient to doctor internals. If you rename
 *   AppointmentUseCaseImpl or split it, patient code breaks. This interface
 *   is a stable contract — doctor internals can change freely behind it.
 */
public interface DoctorModuleApi {

    // ── Doctor discovery ──────────────────────────────────────────────────────
    List<Doctor> getDoctorsByLocation(String location);
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

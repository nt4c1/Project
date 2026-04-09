package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.AppointmentStatus;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import jakarta.inject.Singleton;

import java.time.*;
import java.util.*;

@Singleton
public class AppointmentRepositoryImpl implements AppointmentRepositoryPort {

    private final CqlSession session;

    public AppointmentRepositoryImpl(CqlSession session) {
        this.session = session;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @Override
    public void save(Appointment a) {
        //Count
        Row countRow = session.execute(
                "SELECT count FROM doctor_service.appointment_count_by_doctor_date " +
                        "WHERE doctor_id=? AND appointment_date=?",
                a.getDoctorId(), a.getAppointmentDate()
        ).one();

        long current = countRow != null ? countRow.getLong("count") : 0L;

        //Limit
        if (current >= 50) {
            throw new RuntimeException("Doctor fully booked for the day");
        }


        Instant now = Instant.now();
        BatchStatement batch = BatchStatement.newInstance(DefaultBatchType.LOGGED)
                // appointments_by_id — source of truth for direct lookups
                .add(SimpleStatement.newInstance(
                        "INSERT INTO doctor_service.appointments_by_id " +
                                "(appointment_id, doctor_id, patient_id, appointment_date, " +
                                " scheduled_time, status, reason_for_visit, " +
                                " cancellation_reason, created_at, updated_at) " +
                                "VALUES (?,?,?,?,?,?,?,?,?,?) IF NOT EXISTS",
                        a.getId(), a.getDoctorId(), a.getPatientId(),
                        a.getAppointmentDate(), a.getScheduleTime(),
                        a.getStatus().name(), a.getReasonForVisit(),
                        null, now, now))
                // appointments_by_doctor — doctor daily schedule view
                .add(SimpleStatement.newInstance(
                        "INSERT INTO doctor_service.appointments_by_doctor " +
                                "(doctor_id, appointment_date, scheduled_time, appointment_id, " +
                                " patient_id, patient_name, patient_phone, status, " +
                                " reason_for_visit, created_at, updated_at) " +
                                "VALUES (?,?,?,?,?,?,?,?,?,?,?) IF NOT EXISTS",
                        a.getDoctorId(), a.getAppointmentDate(), a.getScheduleTime(),
                        a.getId(), a.getPatientId(),
                        a.getPatientName(), a.getPatientPhone(),
                        a.getStatus().name(), a.getReasonForVisit(),
                        now, now))
                // appointments_by_doctor_status — status-filtered view
                .add(SimpleStatement.newInstance(
                        "INSERT INTO doctor_service.appointments_by_doctor_status " +
                                "(doctor_id, status, appointment_date, scheduled_time, " +
                                " appointment_id, patient_id, patient_name) " +
                                "VALUES (?,?,?,?,?,?,?) IF NOT EXISTS",
                        a.getDoctorId(), a.getStatus().name(),
                        a.getAppointmentDate(), a.getScheduleTime(),
                        a.getId(), a.getPatientId(), a.getPatientName()))
                // appointments_by_patient — patient history view (doctor/clinic info denormalized)
                .add(SimpleStatement.newInstance(
                        "INSERT INTO doctor_service.appointments_by_patient " +
                                "(patient_id, appointment_date, scheduled_time, appointment_id, " +
                                " doctor_id, doctor_name, clinic_name, specialization, " +
                                " status, reason_for_visit) " +
                                "VALUES (?,?,?,?,?,?,?,?,?,?) IF NOT EXISTS",
                        a.getPatientId(), a.getAppointmentDate(), a.getScheduleTime(),
                        a.getId(), a.getDoctorId(),
                        a.getDoctorName(), a.getClinicName(), a.getSpecialization(),
                        a.getStatus().name(), a.getReasonForVisit()));

        try {
            session.execute(batch);
        } catch (Exception e) {
            // Batch failed — no counter was incremented yet, nothing to roll back
            throw e;
        }

        // ── 3. Counter update
        try {
            session.execute(
                    "UPDATE doctor_service.appointment_count_by_doctor_date " +
                            "SET count = count + 1 " +
                            "WHERE doctor_id=? AND appointment_date=?",
                    a.getDoctorId(), a.getAppointmentDate()
            );
        } catch (Exception e) {
            // Non-fatal: log and continue — counter is advisory, not authoritative
        }
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Override
    public List<Appointment> findByDoctorAndDate(UUID doctorId, LocalDate date) {
        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.appointments_by_doctor " +
                        "WHERE doctor_id=? AND appointment_date=?",
                doctorId, date
        );
        List<Appointment> list = new ArrayList<>();
        for (Row r : rs) list.add(mapDoctorRow(r));
        return list;
    }

    @Override
    public List<Appointment> findDoctorAndStatus(UUID doctorId, String status, LocalDate date) {
        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.appointments_by_doctor_status " +
                        "WHERE doctor_id=? AND status=? AND appointment_date=?",
                doctorId, status, date
        );
        List<Appointment> list = new ArrayList<>();
        for (Row r : rs) {
            Appointment a = new Appointment(
                    r.getUuid("appointment_id"), r.getUuid("doctor_id"),
                    r.getUuid("patient_id"), r.getLocalDate("appointment_date"),
                    r.getLocalTime("scheduled_time"),
                    AppointmentStatus.valueOf(status),
                    null, null
            );
            a.setPatientName(r.getString("patient_name"));
            list.add(a);
        }
        return list;
    }

    @Override
    public List<Appointment> findPending(UUID doctorId, LocalDate date) {
        return findDoctorAndStatus(doctorId, "PENDING", date);
    }

    @Override
    public List<Appointment> findByPatientAndDate(UUID patientId, LocalDate date) {
        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.appointments_by_patient " +
                        "WHERE patient_id=? AND appointment_date=?",
                patientId, date
        );
        List<Appointment> list = new ArrayList<>();
        for (Row r : rs) {
            Appointment a = new Appointment(
                    r.getUuid("appointment_id"), r.getUuid("doctor_id"),
                    r.getUuid("patient_id"), r.getLocalDate("appointment_date"),
                    r.getLocalTime("scheduled_time"),
                    AppointmentStatus.valueOf(r.getString("status")),
                    null, null
            );
            // Denormalized fields — no extra lookups needed
            a.setDoctorName(r.getString("doctor_name"));
            a.setClinicName(r.getString("clinic_name"));
            a.setSpecialization(r.getString("specialization"));
            a.setReasonForVisit(r.getString("reason_for_visit"));
            list.add(a);
        }
        return list;
    }

    @Override
    public Optional<Appointment> findById(UUID id) {
        Row r = session.execute(
                "SELECT * FROM doctor_service.appointments_by_id WHERE appointment_id=?",
                id
        ).one();
        return Optional.ofNullable(r).map(this::mapIdRow);
    }

    // ── Status update ─────────────────────────────────────────────────────────

    @Override
    public void updateStatus(Appointment a, String newStatus) {
        Instant now = Instant.now();

        // appointments_by_id always update first as it is truth
        session.execute(
                "UPDATE doctor_service.appointments_by_id " +
                        "SET status=?, updated_at=? " +
                        "WHERE appointment_id=?",
                newStatus, now, a.getId()
        );

        // appointments_by_doctor — in-place update, status is a regular column
        session.execute(
                "UPDATE doctor_service.appointments_by_doctor " +
                        "SET status=?, updated_at=? " +
                        "WHERE doctor_id=? AND appointment_date=? " +
                        "  AND scheduled_time=? AND appointment_id=?",
                newStatus, now,
                a.getDoctorId(), a.getAppointmentDate(),
                a.getScheduleTime(), a.getId()
        );

        // appointments_by_doctor_status — status is part of partition key:
        // must DELETE old row and INSERT new one
        session.execute(
                "DELETE FROM doctor_service.appointments_by_doctor_status " +
                        "WHERE doctor_id=? AND status=? AND appointment_date=? " +
                        "  AND scheduled_time=? AND appointment_id=?",
                a.getDoctorId(), a.getStatus().name(),
                a.getAppointmentDate(), a.getScheduleTime(), a.getId()
        );
        session.execute(
                "INSERT INTO doctor_service.appointments_by_doctor_status " +
                        "(doctor_id, status, appointment_date, scheduled_time, " +
                        " appointment_id, patient_id, patient_name) " +
                        "VALUES (?,?,?,?,?,?,?)",
                a.getDoctorId(), newStatus,
                a.getAppointmentDate(), a.getScheduleTime(),
                a.getId(), a.getPatientId(), a.getPatientName()
        );

        // appointments_by_patient — in-place update
        session.execute(
                "UPDATE doctor_service.appointments_by_patient " +
                        "SET status=? " +
                        "WHERE patient_id=? AND appointment_date=? " +
                        "  AND scheduled_time=? AND appointment_id=?",
                newStatus,
                a.getPatientId(), a.getAppointmentDate(),
                a.getScheduleTime(), a.getId()
        );
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Override
    public void cancel(UUID appointmentId, UUID patientId, UUID doctorId,
                       LocalDate date, LocalTime time, String cancellationReason) {
        Instant now = Instant.now();

        // appointments_by_id — store the cancellation reason
        session.execute(
                "UPDATE doctor_service.appointments_by_id " +
                        "SET status='CANCELLED', cancellation_reason=?, updated_at=? " +
                        "WHERE appointment_id=?",
                cancellationReason, now, appointmentId
        );

        // appointments_by_doctor
        session.execute(
                "UPDATE doctor_service.appointments_by_doctor " +
                        "SET status='CANCELLED', updated_at=? " +
                        "WHERE doctor_id=? AND appointment_date=? " +
                        "  AND scheduled_time=? AND appointment_id=?",
                now, doctorId, date, time, appointmentId
        );

        // appointments_by_patient
        session.execute(
                "UPDATE doctor_service.appointments_by_patient " +
                        "SET status='CANCELLED' " +
                        "WHERE patient_id=? AND appointment_date=? " +
                        "  AND scheduled_time=? AND appointment_id=?",
                patientId, date, time, appointmentId
        );

        // appointments_by_doctor_status — remove from PENDING, insert as CANCELLED
        session.execute(
                "DELETE FROM doctor_service.appointments_by_doctor_status " +
                        "WHERE doctor_id=? AND status='PENDING' AND appointment_date=? " +
                        "  AND scheduled_time=? AND appointment_id=?",
                doctorId, date, time, appointmentId
        );
        session.execute(
                "INSERT INTO doctor_service.appointments_by_doctor_status " +
                        "(doctor_id, status, appointment_date, scheduled_time, appointment_id, patient_id) " +
                        "VALUES (?,?,?,?,?,?)",
                doctorId, "CANCELLED", date, time, appointmentId, patientId
        );

        decrementCount(doctorId, date);
    }

    // ── Counter ───────────────────────────────────────────────────────────────

    @Override
    public void decrementCount(UUID doctorId, LocalDate date) {
        session.execute(
                "UPDATE doctor_service.appointment_count_by_doctor_date " +
                        "SET count = count - 1 " +
                        "WHERE doctor_id=? AND appointment_date=?",
                doctorId, date
        );
    }

    // ── Row mappers ───────────────────────────────────────────────────────────

    /** Maps a row from appointments_by_doctor (includes denormalized patient fields). */
    private Appointment mapDoctorRow(Row r) {
        Appointment a = new Appointment(
                r.getUuid("appointment_id"), r.getUuid("doctor_id"),
                r.getUuid("patient_id"), r.getLocalDate("appointment_date"),
                r.getLocalTime("scheduled_time"),
                AppointmentStatus.valueOf(r.getString("status")),
                r.getInstant("created_at"), r.getInstant("updated_at")
        );
        a.setPatientName(r.getString("patient_name"));
        a.setPatientPhone(r.getString("patient_phone"));
        a.setReasonForVisit(r.getString("reason_for_visit"));
        return a;
    }

    /** Maps a row from appointments_by_id (full data including cancellation_reason). */
    private Appointment mapIdRow(Row r) {
        Appointment a = new Appointment(
                r.getUuid("appointment_id"), r.getUuid("doctor_id"),
                r.getUuid("patient_id"), r.getLocalDate("appointment_date"),
                r.getLocalTime("scheduled_time"),
                AppointmentStatus.valueOf(r.getString("status")),
                r.getInstant("created_at"), r.getInstant("updated_at")
        );
        a.setReasonForVisit(r.getString("reason_for_visit"));
        a.setCancellationReason(r.getString("cancellation_reason"));
        return a;
    }
}
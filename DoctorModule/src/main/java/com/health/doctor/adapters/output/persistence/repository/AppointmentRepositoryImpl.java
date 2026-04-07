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

    @Override
    public void save(Appointment a) {

        // Initialize row to 0 if not exists
        session.execute(
                "INSERT INTO doctor_service.appointment_count_by_doctor_date " +
                        "(doctor_id, appointment_date, count) VALUES (?,?,0) IF NOT EXISTS",
                a.getDoctorId(), a.getAppointmentDate()
        );

        // Read current count
        Row countRow = session.execute(
                "SELECT count FROM doctor_service.appointment_count_by_doctor_date " +
                        "WHERE doctor_id=? AND appointment_date=?",
                a.getDoctorId(), a.getAppointmentDate()
        ).one();

        int current = countRow != null ? countRow.getInt("count") : 0;

        if (current >= 50) {
            throw new RuntimeException("Doctor fully booked for the day");
        }

        // LWT optimistic lock
        ResultSet rs = session.execute(
                "UPDATE doctor_service.appointment_count_by_doctor_date " +
                        "SET count = ? " +
                        "WHERE doctor_id=? AND appointment_date=? IF count = ?",
                current + 1, a.getDoctorId(), a.getAppointmentDate(), current
        );

        if (!rs.wasApplied()) {
            throw new RuntimeException("Concurrent booking conflict, please retry");
        }

        try {
            // main table
            session.execute(
                    "INSERT INTO doctor_service.appointments_by_doctor " +
                            "(doctor_id, appointment_date, scheduled_time, appointment_id, patient_id, status, created_at, updated_at) " +
                            "VALUES (?,?,?,?,?,?,?,?)",
                    a.getDoctorId(), a.getAppointmentDate(), a.getScheduleTime(),
                    a.getId(), a.getPatientId(), a.getStatus().name(),
                    a.getCreatedAt(), a.getUpdateAt()
            );

            // status table
            session.execute(
                    "INSERT INTO doctor_service.appointments_by_doctor_status " +
                            "(doctor_id, status, appointment_date, scheduled_time, appointment_id, patient_id) " +
                            "VALUES (?,?,?,?,?,?)",
                    a.getDoctorId(), a.getStatus().name(), a.getAppointmentDate(),
                    a.getScheduleTime(), a.getId(), a.getPatientId()
            );

            // patient view table
            session.execute(
                    "INSERT INTO doctor_service.appointments_by_patient " +
                            "(patient_id, appointment_date, scheduled_time, appointment_id, doctor_id, status) " +
                            "VALUES (?,?,?,?,?,?)",
                    a.getPatientId(), a.getAppointmentDate(), a.getScheduleTime(),
                    a.getId(), a.getDoctorId(), a.getStatus().name()
            );

        } catch (Exception e) {
            decrementCount(a.getDoctorId(), a.getAppointmentDate());
            throw e;
        }
    }

    @Override
    public List<Appointment> findByDoctorAndDate(UUID doctorId, LocalDate date) {
        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.appointments_by_doctor WHERE doctor_id=? AND appointment_date=?",
                doctorId, date
        );
        List<Appointment> list = new ArrayList<>();
        for (Row r : rs) list.add(mapRow(r));
        return list;
    }

    @Override
    public List<Appointment> findDoctorAndStatus(UUID doctorId, String status, LocalDate date) {
        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.appointments_by_doctor_status WHERE doctor_id=? AND status=? AND appointment_date=?",
                doctorId, status, date
        );
        List<Appointment> list = new ArrayList<>();
        for (Row r : rs) {
            list.add(new Appointment(
                    r.getUuid("appointment_id"), r.getUuid("doctor_id"),
                    r.getUuid("patient_id"), r.getLocalDate("appointment_date"),
                    r.getLocalTime("scheduled_time"), AppointmentStatus.valueOf(status),
                    null, null
            ));
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
                "SELECT * FROM doctor_service.appointments_by_patient WHERE patient_id=? AND appointment_date=?",
                patientId, date
        );
        List<Appointment> list = new ArrayList<>();
        for (Row r : rs) {
            list.add(new Appointment(
                    r.getUuid("appointment_id"), r.getUuid("doctor_id"),
                    r.getUuid("patient_id"), r.getLocalDate("appointment_date"),
                    r.getLocalTime("scheduled_time"),
                    AppointmentStatus.valueOf(r.getString("status")),
                    null, null
            ));
        }
        return list;
    }

    @Override
    public void updateStatus(Appointment a, String newStatus) {
        // Delete old row
        session.execute(
                "DELETE FROM doctor_service.appointments_by_doctor " +
                        "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                a.getDoctorId(), a.getAppointmentDate().minusDays(1),
                a.getScheduleTime(), a.getId()
        );

        // Insert new row with new date
        session.execute(
                "INSERT INTO doctor_service.appointments_by_doctor " +
                        "(doctor_id, appointment_date, scheduled_time, appointment_id, patient_id, status, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?,?,?,?)",
                a.getDoctorId(), a.getAppointmentDate(), a.getScheduleTime(),
                a.getId(), a.getPatientId(), newStatus, a.getCreatedAt(),
                ZonedDateTime.now(ZoneId.of("Asia/Kathmandu")).toInstant()
        );

        // Delete old status row
        session.execute(
                "DELETE FROM doctor_service.appointments_by_doctor_status " +
                        "WHERE doctor_id=? AND status=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                a.getDoctorId(), a.getStatus().name(),
                a.getAppointmentDate().minusDays(1), a.getScheduleTime(), a.getId()
        );

        // Insert new status row
        session.execute(
                "INSERT INTO doctor_service.appointments_by_doctor_status " +
                        "(doctor_id, status, appointment_date, scheduled_time, appointment_id, patient_id) " +
                        "VALUES (?,?,?,?,?,?)",
                a.getDoctorId(), newStatus, a.getAppointmentDate(),
                a.getScheduleTime(), a.getId(), a.getPatientId()
        );

        // Update patient view
        session.execute(
                "UPDATE doctor_service.appointments_by_patient SET status=? " +
                        "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                newStatus, a.getPatientId(), a.getAppointmentDate(),
                a.getScheduleTime(), a.getId()
        );
    }

    @Override
    public void cancel(UUID appointmentId, UUID patientId, UUID doctorId, LocalDate date, Instant time) {
        // Update patient view
        session.execute(
                "UPDATE doctor_service.appointments_by_patient SET status='CANCELLED' " +
                        "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                patientId, date, time, appointmentId
        );

        // Update main table
        session.execute(
                "UPDATE doctor_service.appointments_by_doctor SET status='CANCELLED' " +
                        "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                doctorId, date, time, appointmentId
        );
    }

    @Override
    public void decrementCount(UUID doctorId, LocalDate date) {
        Row countRow = session.execute(
                "SELECT count FROM doctor_service.appointment_count_by_doctor_date " +
                        "WHERE doctor_id=? AND appointment_date=?",
                doctorId, date
        ).one();
        int current = countRow != null ? countRow.getInt("count") : 0;
        if (current <= 0) return;
        session.execute(
                "UPDATE doctor_service.appointment_count_by_doctor_date " +
                        "SET count = ? WHERE doctor_id=? AND appointment_date=? IF count = ?",
                current - 1, doctorId, date, current
        );
    }

    private Appointment mapRow(Row r) {
        return new Appointment(
                r.getUuid("appointment_id"), r.getUuid("doctor_id"),
                r.getUuid("patient_id"), r.getLocalDate("appointment_date"),
                r.getLocalTime("scheduled_time"),
                AppointmentStatus.valueOf(r.getString("status")),
                r.getInstant("created_at"), r.getInstant("updated_at")
        );
    }
}
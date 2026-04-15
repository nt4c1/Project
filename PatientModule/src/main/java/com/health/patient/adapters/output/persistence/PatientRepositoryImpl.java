package com.health.patient.adapters.output.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.patient.domain.exception.BookingException;
import com.health.patient.domain.model.Patient;
import com.health.patient.domain.ports.PatientRepositoryPort;
import com.health.patient.mapper.Mapper;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static com.health.patient.mapper.Mapper.mapRow;

@Singleton
public class PatientRepositoryImpl implements PatientRepositoryPort {

    private final CqlSession session;

    public PatientRepositoryImpl(CqlSession session) {
        this.session = session;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @Override
    public void save(Patient patient) {
        Instant now = Instant.parse(Mapper.ToNptTime(Instant.now()));

        // patients — canonical row with audit fields
        session.execute(
                "INSERT INTO doctor_service.patients " +
                        "(patient_id, name, email, phone, password_hash, " +
                        " is_deleted, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?,?,?,?) IF NOT EXISTS",
                patient.getId(), patient.getName(),
                patient.getEmail(), patient.getPhone(),
                patient.getPasswordHash(),
                false, now, now
        );

        // patients_by_email — O(1) login lookup
        session.execute(
                "INSERT INTO doctor_service.patients_by_email (email, patient_id) " +
                        "VALUES (?,?) IF NOT EXISTS",
                patient.getEmail(), patient.getId()
        );
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Override
    public Optional<Patient> findById(UUID patientId) {
        Row r = session.execute(
                "SELECT * FROM doctor_service.patients WHERE patient_id=?",
                patientId
        ).one();
        if (r == null || r.getBoolean("is_deleted")) return Optional.empty();
        return Optional.of(mapRow(r));
    }

    @Override
    public Optional<Patient> findByEmail(String email) {
        // Step 1: O(1) partition read to resolve patient_id
        Row lookup = session.execute(
                "SELECT patient_id FROM doctor_service.patients_by_email WHERE email=?",
                email
        ).one();
        if (lookup == null) return Optional.empty();

        // Step 2: fetch full patient row
        return findById(lookup.getUuid("patient_id"));
    }

    @Override
    public void updatePatient(UUID patientID, String email, String password) {
        Instant now = Instant.now();
        String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());

        Optional<Patient> oldProfile = findById(patientID);
        if (oldProfile.isEmpty()) return;

        // If email changed, update the lookup table
        if (!oldProfile.get().getEmail().equals(email)) {
            session.execute("DELETE FROM doctor_service.patients_by_email WHERE email = ?", oldProfile.get().getEmail());
            session.execute("INSERT INTO doctor_service.patients_by_email (email, patient_id) VALUES (?, ?)", email, patientID);
        }

        session.execute(
                "UPDATE doctor_service.patients SET email = ?, password_hash = ?, updated_at = ? WHERE patient_id = ?",
                email, passwordHash, now, patientID
        );
    }

    @Override
    public void deletePatient(UUID patientId) {
        //Appointment Search
        ResultSet activeRs = session.execute(
                "SELECT status FROM doctor_service.appointments_by_patient WHERE patient_id=? ALLOW FILTERING",
                patientId
        );
        for (Row r : activeRs) {
            String status = r.getString("status");
            if ("PENDING".equals(status) || "ACCEPTED".equals(status)) {
                throw new BookingException(
                        "Cannot delete patient with pending or accepted appointments. " +
                                "Cancel all appointments first.");
            }
        }


        ResultSet allAppts = session.execute(
                "SELECT appointment_id, appointment_date, scheduled_time, doctor_id, status " +
                        "FROM doctor_service.appointments_by_patient WHERE patient_id=?",
                patientId
        );

        for (Row r : allAppts) {
            UUID      apptId   = r.getUuid("appointment_id");
            LocalDate apptDate = r.getLocalDate("appointment_date");
            Instant   apptTime = r.getInstant("scheduled_time");
            UUID      doctorId = r.getUuid("doctor_id");
            String    status   = r.getString("status");

            // Delete from appointments_by_id
            session.execute(
                    "DELETE FROM doctor_service.appointments_by_id WHERE appointment_id=?",
                    apptId);

            // Delete from appointments_by_patient (current row)
            session.execute(
                    "DELETE FROM doctor_service.appointments_by_patient " +
                            "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                    patientId, apptDate, apptTime, apptId);

            // Delete from appointments_by_doctor
            session.execute(
                    "DELETE FROM doctor_service.appointments_by_doctor " +
                            "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                    doctorId, apptDate, apptTime, apptId);

            // Delete from appointments_by_doctor_status
            session.execute(
                    "DELETE FROM doctor_service.appointments_by_doctor_status " +
                            "WHERE doctor_id=? AND status=? AND appointment_date=? " +
                            "AND scheduled_time=? AND appointment_id=?",
                    doctorId, status, apptDate, apptTime, apptId);

            // Decrement counter
            session.execute(
                    "UPDATE doctor_service.appointment_count_by_doctor_date " +
                            "SET count = count - 1 WHERE doctor_id=? AND appointment_date=?",
                    doctorId, apptDate);
        }

        // 3. Delete the patient profile rows
        Optional<Patient> p = findById(patientId);
        if (p.isPresent()) {
            BatchStatement batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                    .addStatement(SimpleStatement.newInstance(
                            "DELETE FROM doctor_service.patients WHERE patient_id=?", patientId))
                    .addStatement(SimpleStatement.newInstance(
                            "DELETE FROM doctor_service.patients_by_email WHERE email=?",
                            p.get().getEmail()))
                    .build();
            session.execute(batch);
        }
    }

}
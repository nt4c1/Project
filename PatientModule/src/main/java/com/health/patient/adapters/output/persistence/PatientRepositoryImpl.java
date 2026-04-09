package com.health.patient.adapters.output.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.patient.domain.model.Patient;
import com.health.patient.domain.ports.PatientRepositoryPort;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class PatientRepositoryImpl implements PatientRepositoryPort {

    private final CqlSession session;

    public PatientRepositoryImpl(CqlSession session) {
        this.session = session;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @Override
    public void save(Patient patient) {
        Instant now = Instant.now();

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

    // ── Mapper ────────────────────────────────────────────────────────────────

    private Patient mapRow(Row r) {
        return new Patient(
                r.getUuid("patient_id"),
                r.getString("name"),
                r.getString("email"),
                r.getString("phone"),
                r.getString("password_hash"),
                r.getBoolean("is_deleted"),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        );
    }
}
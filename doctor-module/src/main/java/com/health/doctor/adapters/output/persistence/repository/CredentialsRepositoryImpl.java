package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.doctor.domain.model.DoctorCredentials;
import com.health.doctor.domain.ports.CredentialsRepositoryPort;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class CredentialsRepositoryImpl implements CredentialsRepositoryPort {

    private final CqlSession session;

    public CredentialsRepositoryImpl(CqlSession session) {
        this.session = session;
    }

    @Override
    public void save(DoctorCredentials creds) {
        Instant now = Instant.now();

        session.execute(
                "INSERT INTO doctor_service.doctor_credentials " +
                        "(doctor_id, email, password_hash, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?) IF NOT EXISTS",
                creds.getDoctorId(), creds.getEmail(),
                creds.getPasswordHash(), now, now
        );

        // Email → doctor_id lookup table
        session.execute(
                "INSERT INTO doctor_service.doctors_by_email (email, doctor_id) " +
                        "VALUES (?,?) IF NOT EXISTS",
                creds.getEmail(), creds.getDoctorId()
        );
    }

    @Override
    public Optional<DoctorCredentials> findByEmail(String email) {
        // Step 1: O(1) partition read to resolve doctor_id
        Row lookup = session.execute(
                "SELECT doctor_id FROM doctor_service.doctors_by_email WHERE email=?",
                email
        ).one();
        if (lookup == null) return Optional.empty();

        // Step 2: fetch full credentials
        return findByDoctorId(lookup.getUuid("doctor_id"));
    }

    @Override
    public Optional<DoctorCredentials> findByDoctorId(UUID doctorId) {
        Row r = session.execute(
                "SELECT * FROM doctor_service.doctor_credentials WHERE doctor_id=?",
                doctorId
        ).one();
        if (r == null) return Optional.empty();
        return Optional.of(new DoctorCredentials(
                r.getUuid("doctor_id"),
                r.getString("email"),
                r.getString("password_hash"),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        ));
    }

    @Override
    public void updatePassword(UUID doctorId, String passwordHash) {
        session.execute(
                "UPDATE doctor_service.doctor_credentials SET password_hash=?, updated_at=? WHERE doctor_id=?",
                passwordHash, Instant.now(), doctorId
        );
    }
}
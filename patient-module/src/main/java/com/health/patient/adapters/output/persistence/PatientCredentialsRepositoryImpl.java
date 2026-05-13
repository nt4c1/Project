package com.health.patient.adapters.output.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.patient.domain.model.PatientCredentials;
import com.health.patient.domain.ports.PatientCredentialsRepositoryPort;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class PatientCredentialsRepositoryImpl implements PatientCredentialsRepositoryPort {

    private final CqlSession session;

    private final PreparedStatement insertCredentials;
    private final PreparedStatement selectByPatientId;
    private final PreparedStatement selectIdByEmail;
    private final PreparedStatement updatePassword;
    private final PreparedStatement updateEmail;

    public PatientCredentialsRepositoryImpl(CqlSession session) {
        this.session = session;

        this.insertCredentials = session.prepare(
                "INSERT INTO doctor_service.patient_credentials " +
                        "(patient_id, email, phone, password_hash, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?,?) IF NOT EXISTS"
        );
        this.selectByPatientId = session.prepare(
                "SELECT * FROM doctor_service.patient_credentials WHERE patient_id=?"
        );
        this.selectIdByEmail = session.prepare(
                "SELECT patient_id FROM doctor_service.patients_by_email WHERE email=?"
        );
        this.updatePassword = session.prepare(
                "UPDATE doctor_service.patient_credentials SET password_hash=?, updated_at=? WHERE patient_id=?"
        );
        this.updateEmail = session.prepare(
                "UPDATE doctor_service.patient_credentials SET email=?, updated_at=? WHERE patient_id=?"
        );
    }

    @Override
    public void save(PatientCredentials credentials) {
        session.execute(insertCredentials.bind(
                credentials.getPatientId(), credentials.getEmail(),
                credentials.getPhone(), credentials.getPasswordHash(),
                credentials.getCreatedAt(), credentials.getUpdatedAt()
        ));
    }

    @Override
    public Optional<PatientCredentials> findByEmail(String email) {
        Row lookup = session.execute(selectIdByEmail.bind(email)).one();
        if (lookup == null) return Optional.empty();
        return findByPatientId(lookup.getUuid("patient_id"));
    }

    @Override
    public Optional<PatientCredentials> findByPatientId(UUID patientId) {
        Row r = session.execute(selectByPatientId.bind(patientId)).one();
        if (r == null) return Optional.empty();
        return Optional.of(new PatientCredentials(
                r.getUuid("patient_id"),
                r.getString("email"),
                r.getString("phone"),
                r.getString("password_hash"),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        ));
    }

    @Override
    public void updatePassword(UUID patientId, String passwordHash) {
        session.execute(updatePassword.bind(passwordHash, Instant.now(), patientId));
    }

    @Override
    public void updateEmail(UUID patientId, String email) {
        session.execute(updateEmail.bind(email, Instant.now(), patientId));
    }
}

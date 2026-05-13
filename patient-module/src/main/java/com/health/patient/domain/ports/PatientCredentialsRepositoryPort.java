package com.health.patient.domain.ports;

import com.health.patient.domain.model.PatientCredentials;

import java.util.Optional;
import java.util.UUID;

public interface PatientCredentialsRepositoryPort {
    void save(PatientCredentials credentials);
    Optional<PatientCredentials> findByEmail(String email);
    Optional<PatientCredentials> findByPatientId(UUID patientId);
    void updatePassword(UUID patientId, String passwordHash);
    void updateEmail(UUID patientId, String email);
}

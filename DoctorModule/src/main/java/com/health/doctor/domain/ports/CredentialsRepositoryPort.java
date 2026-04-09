package com.health.doctor.domain.ports;

import com.health.doctor.domain.model.DoctorCredentials;

import java.util.Optional;
import java.util.UUID;

public interface CredentialsRepositoryPort {

    void save(DoctorCredentials credentials);

    Optional<DoctorCredentials> findByEmail(String email);

    Optional<DoctorCredentials> findByDoctorId(UUID doctorId);
}
package com.health.admin.domain.ports;

import com.health.admin.domain.model.Admin;

import java.util.Optional;
import java.util.UUID;

public interface AdminRepositoryPort {
    Optional<Admin> findByEmail(String email);

    Optional<Admin> findById(UUID uuid);

    long countDoctors();

    long countPatients();

    long countClinics();

    long countAppointments();
}

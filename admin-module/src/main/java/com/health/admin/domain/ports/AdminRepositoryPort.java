package com.health.admin.domain.ports;

import com.health.admin.domain.model.Admin;

import java.util.Optional;

public interface AdminRepositoryPort {
    Optional<Admin> findByEmail(String email);
}

package com.health.doctor.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class ClinicCredentials {
    private UUID clinicId;
    private String email;
    private String passwordHash;
    private Instant createdAt;
    private Instant updatedAt;

    public ClinicCredentials(UUID clinicId, String email, String passwordHash,
                             Instant createdAt, Instant updatedAt) {
        this.clinicId = clinicId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}

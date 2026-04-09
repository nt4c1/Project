package com.health.doctor.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class DoctorCredentials {

    private UUID doctorId;
    private String email;
    private String passwordHash;
    private Instant createdAt;
    private Instant updatedAt;

    public DoctorCredentials(UUID doctorId, String email, String passwordHash,
                             Instant createdAt, Instant updatedAt) {
        this.doctorId = doctorId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
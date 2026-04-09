package com.health.patient.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class Patient {

    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String passwordHash;
    private boolean isDeleted;
    private Instant createdAt;
    private Instant updatedAt;

    public Patient(UUID id, String name) {
        this.id   = id;
        this.name = name;
    }

    public Patient(UUID id, String name, String email, String phone, String passwordHash) {
        this.id           = id;
        this.name         = name;
        this.email        = email;
        this.phone        = phone;
        this.passwordHash = passwordHash;
    }

    public Patient(UUID id, String name, String email, String phone,
                   String passwordHash, boolean isDeleted,
                   Instant createdAt, Instant updatedAt) {
        this(id, name, email, phone, passwordHash);
        this.isDeleted  = isDeleted;
        this.createdAt  = createdAt;
        this.updatedAt  = updatedAt;
    }
}
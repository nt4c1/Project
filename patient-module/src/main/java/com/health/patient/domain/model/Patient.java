package com.health.patient.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Patient implements Serializable {

    private UUID id;
    private String name;
    private String email;
    private String phone;
    private boolean isDeleted;
    private Instant createdAt;
    private Instant updatedAt;

    public Patient(UUID id, String name, String email, String phone) {
        this.id           = id;
        this.name         = name;
        this.email        = email;
        this.phone        = phone;
    }

    public Patient(UUID id, String name, String email, String phone,
                   boolean isDeleted, Instant createdAt, Instant updatedAt) {
        this(id, name, email, phone);
        this.isDeleted  = isDeleted;
        this.createdAt  = createdAt;
        this.updatedAt  = updatedAt;
    }
    }
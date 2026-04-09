package com.health.doctor.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class Doctor {

    private UUID id;
    private String name;
    private UUID clinicId;
    private DoctorType type;
    private String specialization;
    private boolean isActive;
    private boolean isDeleted;
    private Instant createdAt;
    private Instant updatedAt;

    public Doctor(UUID id, String name, UUID clinicId, DoctorType type,
                  String specialization, boolean isActive) {
        this.id = id;
        this.name = name;
        this.clinicId = clinicId;
        this.type = type;
        this.specialization = specialization;
        this.isActive = isActive;
    }

    public Doctor(UUID id, String name, UUID clinicId, DoctorType type,
                  String specialization, boolean isActive,
                  boolean isDeleted, Instant createdAt, Instant updatedAt) {
        this(id, name, clinicId, type, specialization, isActive);
        this.isDeleted = isDeleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
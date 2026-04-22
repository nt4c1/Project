package com.health.doctor.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Doctor {

    private UUID id;
    private String name;
    private List<UUID> clinicIds;
    private DoctorType type;
    private String specialization;
    private boolean isActive;
    private boolean isDeleted;
    private Instant createdAt;
    private Instant updatedAt;
    private double distance;

    public Doctor(UUID id, String name, List<UUID> clinicIds, DoctorType type,
                  String specialization, boolean isActive) {
        this.id = id;
        this.name = name;
        this.clinicIds = clinicIds;
        this.type = type;
        this.specialization = specialization;
        this.isActive = isActive;
    }

    public Doctor(UUID id, String name, List<UUID> clinicIds, DoctorType type,
                  String specialization, boolean isActive, double distance) {
        this(id, name, clinicIds, type, specialization, isActive);
        this.distance = distance;
    }

    public Doctor(UUID id, String name, List<UUID> clinicIds, DoctorType type,
                  String specialization, boolean isActive,
                  boolean isDeleted, Instant createdAt, Instant updatedAt) {
        this(id, name, clinicIds, type, specialization, isActive);
        this.isDeleted = isDeleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
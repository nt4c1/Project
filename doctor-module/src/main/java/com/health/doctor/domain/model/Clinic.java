package com.health.doctor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class Clinic {

    private UUID id;
    private String name;
    private Location location;
    private boolean isActive;
    private boolean isDeleted;
    private Instant createdAt;
    private Instant updatedAt;

    public Clinic(UUID id, String name, Location location, boolean isActive) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.isActive = isActive;
    }

    public Clinic(UUID id, String name, Location location, boolean isActive,
                  boolean isDeleted, Instant createdAt, Instant updatedAt) {
        this(id, name, location, isActive);
        this.isDeleted = isDeleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getLocationText() {
        return location != null ? location.getLocationText() : null;
    }
}
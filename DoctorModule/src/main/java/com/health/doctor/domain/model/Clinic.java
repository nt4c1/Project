package com.health.doctor.domain.model;

import java.util.UUID;

public class Clinic {
    private UUID id;
    private String name;
    private Location location;
    private boolean isActive;

    public Clinic(UUID id, String name, Location location, boolean isActive) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.isActive = isActive;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}

package com.health.patient.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class Patient {
    private UUID id;
    private String name;
    private String email;
    private String passwordHash;
    private String phone;

    public Patient(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public Patient(UUID id, String name, String email, String passwordHash, String phone) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
    }
}
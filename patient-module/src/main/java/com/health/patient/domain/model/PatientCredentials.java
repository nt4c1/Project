package com.health.patient.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientCredentials {
    private UUID patientId;
    private String email;
    private String phone;
    private String passwordHash;
    private Instant createdAt;
    private Instant updatedAt;
}

package com.health.admin.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Admin {
    private UUID id;
    private String name;
    private String email;
    private String passwordHash;
    private Instant createdAt;
    private Instant updatedAt;
}

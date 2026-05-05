package com.health.doctor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Review {
    private UUID doctorId;
    private UUID patientId;
    private String patientName;
    private int rating;
    private String comment;
    private Instant createdAt;
}

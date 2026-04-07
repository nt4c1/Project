package com.health.patient.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
public class Appointment {
    private UUID id;
    private UUID doctorId;
    private UUID patientId;
    private LocalDate date;
    private LocalTime time;
    private String status;

    public Appointment(UUID id, UUID doctorId, UUID patientId,
                       LocalDate date, LocalTime time, String status) {
        this.id = id;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.date = date;
        this.time = time;
        this.status = status;
    }
}
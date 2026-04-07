package com.health.doctor.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
public class Appointment {
    private UUID id;
    private UUID doctorId;
    private UUID patientId;
    private LocalDate appointmentDate;
    private LocalTime scheduleTime;
    private AppointmentStatus status;
    private Instant createdAt;
    private Instant updateAt;

    public Appointment(UUID id, UUID doctorId, UUID patientId, LocalDate appointmentDate, LocalTime scheduleTime, AppointmentStatus status, Instant createdAt, Instant updateAt) {
        this.id = id;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.appointmentDate = appointmentDate;
        this.scheduleTime = scheduleTime;
        this.status = status;
        this.createdAt = createdAt;
        this.updateAt = updateAt;
    }

}

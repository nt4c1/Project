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
    private Instant updatedAt;
    private String patientName;
    private String patientPhone;
    private String doctorName;
    private String clinicName;
    private String specialization;
    private String reasonForVisit;
    private String cancellationReason;

    public Appointment(UUID id, UUID doctorId, UUID patientId,
                       LocalDate appointmentDate, LocalTime scheduleTime,
                       AppointmentStatus status,
                       Instant createdAt, Instant updatedAt,
                       String patientName, String patientPhone,
                       String doctorName, String clinicName, String specialization,
                       String reasonForVisit, String cancellationReason) {
        this.id = id;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.appointmentDate = appointmentDate;
        this.scheduleTime = scheduleTime;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.patientName = patientName;
        this.patientPhone = patientPhone;
        this.doctorName = doctorName;
        this.clinicName = clinicName;
        this.specialization = specialization;
        this.reasonForVisit = reasonForVisit;
        this.cancellationReason = cancellationReason;
    }

    public Appointment(UUID id, UUID doctorId, UUID patientId,
                       LocalDate appointmentDate, LocalTime scheduleTime,
                       AppointmentStatus status,
                       Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.appointmentDate = appointmentDate;
        this.scheduleTime = scheduleTime;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Appointment(UUID patientId, LocalDate appointmentDate, LocalTime scheduleTime,
                       UUID id, String clinicName, UUID doctorId, String doctorName,
                       String reasonForVisit, String specialization, AppointmentStatus status) {
        this.patientId = patientId;
        this.appointmentDate = appointmentDate;
        this.scheduleTime = scheduleTime;
        this.id = id;
        this.clinicName = clinicName;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.reasonForVisit = reasonForVisit;
        this.specialization = specialization;
        this.status = status;
    }
}
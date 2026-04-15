package com.health.doctor.mapper;

import com.datastax.oss.driver.api.core.cql.Row;
import com.health.doctor.domain.model.*;
import com.health.grpc.doctor.AppointmentActionRequest;
import com.health.grpc.doctor.AppointmentMessage;
import com.health.grpc.doctor.DoctorMessage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

public class MapperClass {

    private static final ZoneId NPT = ZoneId.of("Asia/Kathmandu");

    // ── Row mappers ───────────────────────────────────────────────────────────

    public static Appointment mapDoctorRow(Row r) {
        Appointment a = new Appointment(
                r.getUuid("appointment_id"),
                r.getUuid("doctor_id"),
                r.getUuid("patient_id"),
                r.getLocalDate("appointment_date"),
                toLocalTime(r.getInstant("scheduled_time")),
                AppointmentStatus.valueOf(r.getString("status")),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        );
        a.setPatientName(r.getString("patient_name"));
        a.setPatientPhone(r.getString("patient_phone"));
        a.setReasonForVisit(r.getString("reason_for_visit"));
        return a;
    }

    public static Appointment mapIdRow(Row r) {
        Appointment a = new Appointment(
                r.getUuid("appointment_id"),
                r.getUuid("doctor_id"),
                r.getUuid("patient_id"),
                r.getLocalDate("appointment_date"),
                toLocalTime(r.getInstant("scheduled_time")),
                AppointmentStatus.valueOf(r.getString("status")),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        );
        a.setReasonForVisit(r.getString("reason_for_visit"));
        a.setCancellationReason(r.getString("cancellation_reason"));
        return a;
    }

    public static Clinic mapRowToClinic(Row r) {
        if (r == null) return null;
        Location loc = new Location(
                r.getDouble("latitude"),
                r.getDouble("longitude"),
                r.getString("geohash"),
                r.getString("location_text")
        );
        return new Clinic(
                r.getUuid("clinic_id"),
                r.getString("name"),
                loc,
                r.getBoolean("is_active"),
                r.getBoolean("is_deleted"),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        );
    }

    public static Doctor mapProfileRow(Row r) {
        return new Doctor(
                r.getUuid("doctor_id"),
                r.getString("name"),
                r.getUuid("clinic_id"),
                DoctorType.valueOf(r.getString("type")),
                r.getString("specialization"),
                r.getBoolean("is_active"),
                r.getBoolean("is_deleted"),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        );
    }

    public static Doctor mapLocationRow(Row r) {
        return new Doctor(
                r.getUuid("doctor_id"),
                r.getString("name"),
                r.getUuid("clinic_id"),
                null,
                r.getString("specialization"),
                r.getBoolean("is_active")
        );
    }

    // ── Proto mappers ─────────────────────────────────────────────────────────

    public static DoctorMessage toMsg(Doctor d) {
        DoctorMessage.Builder b = DoctorMessage.newBuilder()
                .setDoctorId(d.getId() != null ? d.getId().toString() : "")
                .setName(d.getName() != null ? d.getName() : "")
                .setSpecialization(d.getSpecialization() != null ? d.getSpecialization() : "")
                .setIsActive(d.isActive());
        if (d.getType() != null)
            b.setType(com.health.grpc.doctor.DoctorType.valueOf(d.getType().name()));
        if (d.getClinicId() != null)
            b.setClinicId(d.getClinicId().toString());
        return b.build();
    }

    public static AppointmentMessage toApptMsg(Appointment a) {
        AppointmentMessage.Builder b = AppointmentMessage.newBuilder()
                .setAppointmentId(a.getId().toString())
                .setDoctorId(a.getDoctorId().toString())
                .setPatientId(a.getPatientId().toString())
                .setDate(a.getAppointmentDate().toString())
                .setTime(a.getScheduleTime().toString())
                .setStatus(com.health.grpc.doctor.AppointmentStatus.valueOf(a.getStatus().name()));
        if (a.getPatientName()        != null) b.setPatientName(a.getPatientName());
        if (a.getPatientPhone()       != null) b.setPatientPhone(a.getPatientPhone());
        if (a.getDoctorName()         != null) b.setDoctorName(a.getDoctorName());
        if (a.getClinicName()         != null) b.setClinicName(a.getClinicName());
        if (a.getReasonForVisit()     != null) b.setReasonForVisit(a.getReasonForVisit());
        if (a.getCancellationReason() != null) b.setCancellationReason(a.getCancellationReason());
        return b.build();
    }

    public static Appointment toAppointment(AppointmentActionRequest r) {
        return new Appointment(
                UUID.fromString(r.getAppointmentId()),
                UUID.fromString(r.getDoctorId()),
                UUID.fromString(r.getPatientId()),
                LocalDate.parse(r.getDate()),
                LocalTime.parse(r.getTime()),
                AppointmentStatus.valueOf(r.getStatus().name()),
                null,
                null
        );
    }

    // ── Time helpers ──────────────────────────────────────────────────────────

    public static Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(NPT).toInstant();
    }

    public static LocalTime toLocalTime(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(NPT).toLocalTime();
    }
}
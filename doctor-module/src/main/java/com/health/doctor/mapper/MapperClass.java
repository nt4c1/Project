package com.health.doctor.mapper;

import com.datastax.oss.driver.api.core.cql.Row;
import com.health.doctor.domain.model.*;
import com.health.grpc.common.AppointmentMessage;
import com.health.grpc.common.DoctorMessage;
import com.health.doctor.domain.ports.PatientLookUpPort.PatientSummary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Collectors;

public class MapperClass {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapperClass.class);
    private static final ZoneId NPT = ZoneId.of("Asia/Kathmandu");
    private static final UUID NO_CLINIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static DoctorMessage toMsg(Doctor d) {
        DoctorMessage.Builder b = DoctorMessage.newBuilder()
                .setDoctorId(d.getId() != null ? d.getId().toString() : "")
                .setName(d.getName() != null ? d.getName() : "")
                .setSpecialization(d.getSpecialization() != null ? d.getSpecialization() : "")
                .setIsActive(d.isActive())
                .setDistance(d.getDistance());

        if (d.getPhone() != null) b.setPhone(d.getPhone());
        if (d.getNextPossibleDate() != null) b.setNextPossibleDate(d.getNextPossibleDate());

        if (d.getType() != null) {
            b.setType(com.health.grpc.common.DoctorType.valueOf(d.getType().name()));
        }
        if (d.getClinicIds() != null) {
            b.addAllClinicIds(d.getClinicIds().stream().map(UUID::toString).collect(Collectors.toList()));
        }
        return b.build();
    }

    public static AppointmentMessage toApptMsg(Appointment a) {
        AppointmentMessage.Builder b = AppointmentMessage.newBuilder()
                .setAppointmentId(a.getId().toString())
                .setDoctorId(a.getDoctorId().toString())
                .setPatientId(a.getPatientId().toString())
                .setDate(a.getAppointmentDate().toString())
                .setTime(a.getScheduleTime().toString())
                .setStatus(com.health.grpc.common.AppointmentStatus.valueOf(a.getStatus().name()));
        if (a.getPatientName()        != null) b.setPatientName(a.getPatientName());
        if (a.getPatientPhone()       != null) b.setPatientPhone(a.getPatientPhone());
        if (a.getDoctorName()         != null) b.setDoctorName(a.getDoctorName());
        if (a.getClinicName()         != null) b.setClinicName(a.getClinicName());
        if (a.getReasonForVisit()     != null) b.setReasonForVisit(a.getReasonForVisit());
        if (a.getCancellationReason() != null) b.setCancellationReason(a.getCancellationReason());
        if (a.getClinicId()           != null) b.setClinicId(a.getClinicId().toString());
        return b.build();
    }

    public static Appointment mapDoctorRow(Row r) {
        if (r == null) return null;
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
        a.setClinicId(r.getUuid("clinic_id"));
        return a;
    }

    public static Clinic mapRowToClinic(Row r) {
        if (r == null) return null;
        return new Clinic(
                r.getUuid("clinic_id"),
                r.getString("name"),
                mapRowToLocation(r),
                r.getBoolean("is_active"),
                r.getBoolean("is_deleted"),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        );
    }

    public static Location mapRowToLocation(Row r) {
        if (r == null) return null;
        return new Location(
                r.getDouble("latitude"),
                r.getDouble("longitude"),
                r.getString("geohash"),
                r.getString("location_text")
        );
    }

    public static Doctor mapProfileRow(Row r) {
        if (r == null) return null;
        return new Doctor(
                r.getUuid("doctor_id"),
                r.getString("name"),
                r.getList("clinic_ids", UUID.class),
                DoctorType.valueOf(r.getString("type")),
                r.getString("specialization"),
                r.getString("phone"),
                r.getBoolean("is_active"),
                r.getBoolean("is_deleted"),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        );
    }

    public static Appointment mapIdRow(Row r) {
        if (r == null) return null;
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
        a.setClinicId(r.getUuid("clinic_id"));
        return a;
    }

    public static Doctor mapLocationRow(Row r, DoctorType type, double distance) {
        if (r == null) return null;
        UUID doctorId = r.getUuid("doctor_id");
        UUID clinicId = r.getUuid("clinic_id");
        java.util.List<UUID> clinicIds = (clinicId == null || NO_CLINIC_ID.equals(clinicId))
                ? java.util.Collections.emptyList()
                : java.util.Collections.singletonList(clinicId);
        return new Doctor(doctorId, r.getString("name"), clinicIds, type, r.getString("specialization"), r.getString("phone"), r.getBoolean("is_active"), distance);
    }

    public static PatientSummary mapRowToPatientSummary(Row r) {
        if (r == null) return null;
        return new PatientSummary(r.getUuid("patient_id"), r.getString("name"), r.getString("phone"));
    }

    public static Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(NPT).toInstant();
    }

    public static LocalTime toLocalTime(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(NPT).toLocalTime();
    }
}

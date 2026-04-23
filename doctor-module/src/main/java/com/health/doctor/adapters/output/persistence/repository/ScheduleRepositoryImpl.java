package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.doctor.domain.model.DoctorSchedule;
import com.health.doctor.domain.ports.ScheduleRepositoryPort;
import jakarta.inject.Singleton;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class ScheduleRepositoryImpl implements ScheduleRepositoryPort {

    private final CqlSession session;

    public ScheduleRepositoryImpl(CqlSession session) {
        this.session = session;
    }

    @Override
    public void save(DoctorSchedule schedule) {
        Set<String> days = schedule.getWorkingDays().stream()
                .map(DayOfWeek::name)
                .collect(Collectors.toSet());

        Instant now = Instant.now();

        session.execute(
                "INSERT INTO doctor_service.doctor_schedules " +
                        "(doctor_id, clinic_id, working_days, start_time, end_time, " +
                        " slot_duration_minutes, max_appointments_day, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                schedule.getDoctorId(),
                schedule.getClinicId(),
                days,
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getSlotDurationMinutes(),
                schedule.getMaxAppointmentsPerDay(),
                now, now
        );

        // schedules_by_clinic — denormalized view
        if (schedule.getClinicId() != null) {
            session.execute(
                    "INSERT INTO doctor_service.schedules_by_clinic " +
                            "(clinic_id, doctor_id, doctor_name, working_days, " +
                            " start_time, end_time, slot_duration_minutes) " +
                            "VALUES (?,?,?,?,?,?,?)",
                    schedule.getClinicId(),
                    schedule.getDoctorId(),
                    schedule.getDoctorName(),
                    days,
                    schedule.getStartTime(),
                    schedule.getEndTime(),
                    schedule.getSlotDurationMinutes()
            );
        }
    }

    @Override
    public Optional<DoctorSchedule> findByDoctorAndClinic(UUID doctorId, UUID clinicId) {
        Row r = session.execute(
                "SELECT * FROM doctor_service.doctor_schedules WHERE doctor_id=? AND clinic_id=?",
                doctorId, clinicId
        ).one();
        if (r == null) return Optional.empty();

        Set<DayOfWeek> days = Objects.requireNonNull(r.getSet("working_days", String.class))
                .stream()
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());

        DoctorSchedule schedule = new DoctorSchedule(
                r.getUuid("doctor_id"),
                r.getUuid("clinic_id"),
                days,
                r.getLocalTime("start_time"),
                r.getLocalTime("end_time"),
                r.getInt("slot_duration_minutes"),
                r.getInt("max_appointments_day")
        );
        return Optional.of(schedule);
    }

    @Override
    public List<DoctorSchedule> findByDoctors(List<UUID> doctorIds) {
        if (doctorIds == null || doctorIds.isEmpty()) return Collections.emptyList();

        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.doctor_schedules WHERE doctor_id IN ?",
                doctorIds
        );

        List<DoctorSchedule> list = new ArrayList<>();
        for (Row r : rs) {
            Set<DayOfWeek> days = Objects.requireNonNull(r.getSet("working_days", String.class))
                    .stream()
                    .map(DayOfWeek::valueOf)
                    .collect(Collectors.toSet());

            list.add(new DoctorSchedule(
                    r.getUuid("doctor_id"),
                    r.getUuid("clinic_id"),
                    days,
                    r.getLocalTime("start_time"),
                    r.getLocalTime("end_time"),
                    r.getInt("slot_duration_minutes"),
                    r.getInt("max_appointments_day")
            ));
        }
        return list;
    }
}
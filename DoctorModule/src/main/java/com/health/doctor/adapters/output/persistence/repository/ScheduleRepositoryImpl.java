package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.doctor.domain.model.DoctorSchedule;
import com.health.doctor.domain.ports.ScheduleRepositoryPort;
import jakarta.inject.Singleton;

import java.time.DayOfWeek;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

        session.execute(
                "INSERT INTO doctor_service.doctor_schedules " +
                        "(doctor_id, working_days, start_time, end_time, slot_duration_minutes, max_appointments_day) " +
                        "VALUES (?,?,?,?,?,?)",
                schedule.getDoctorId(),
                days,
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getSlotDurationMinutes(),
                schedule.getMaxAppointmentsPerDay()
        );
    }

    @Override
    public DoctorSchedule findByDoctorId(UUID doctorId) {
        Row r = session.execute(
                "SELECT * FROM doctor_service.doctor_schedules WHERE doctor_id=?",
                doctorId
        ).one();

        if (r == null) return null;

        Set<DayOfWeek> days = Objects.requireNonNull(r.getSet("working_days", String.class))
                .stream()
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());

        return new DoctorSchedule(
                r.getUuid("doctor_id"),
                days,
                r.getInstant("start_time"),
                r.getInstant("end_time"),
                r.getInt("slot_duration_minutes"),
                r.getInt("max_appointments_day")
        );
    }
}
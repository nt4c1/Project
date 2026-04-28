package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.common.redis.RedisUtil;
import com.health.doctor.domain.model.DoctorSchedule;
import com.health.doctor.domain.ports.ScheduleRepositoryPort;
import jakarta.inject.Singleton;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.health.doctor.mapper.MapperClass.log;

@Singleton
public class ScheduleRepositoryImpl implements ScheduleRepositoryPort {

    private final CqlSession session;
    private final PreparedStatement insertSchedule;
    private final PreparedStatement insertScheduleByClinic;
    private final PreparedStatement updateSchedule;
    private final PreparedStatement updateScheduleByClinic;
    private final PreparedStatement selectByDoctorAndClinic;
    private final PreparedStatement selectByDoctors;
    private final PreparedStatement deleteByDoctor;
    private final PreparedStatement deleteByClinicSchedule;
    private final PreparedStatement deleteByDoctorOverrides;
    private final PreparedStatement deleteByDoctorUnavailability;
    private final RedisUtil redisUtil;
    private final String CACHE_SCHEDULE_PREFIX = "schedule:";

    public ScheduleRepositoryImpl(CqlSession session, RedisUtil redisUtil) {
        this.session = session;
        this.insertSchedule = session.prepare(
                "INSERT INTO doctor_service.doctor_schedules " +
                        "(doctor_id, clinic_id, working_days, start_time, end_time, " +
                        " slot_duration_minutes, max_appointments_day, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)"
        );
        this.insertScheduleByClinic = session.prepare(
                "INSERT INTO doctor_service.schedules_by_clinic " +
                        "(clinic_id, doctor_id, doctor_name, working_days, " +
                        " start_time, end_time, slot_duration_minutes) " +
                        "VALUES (?,?,?,?,?,?,?)"
        );
        this.updateSchedule = session.prepare(
                "UPDATE doctor_service.doctor_schedules SET working_days=?, start_time=?, end_time=?, " +
                        " slot_duration_minutes=?, max_appointments_day=?, updated_at=? " +
                        "WHERE doctor_id=? AND clinic_id=?"
        );
        this.updateScheduleByClinic = session.prepare(
                "UPDATE doctor_service.schedules_by_clinic SET doctor_name=?, working_days=?, " +
                        " start_time=?, end_time=?, slot_duration_minutes=? " +
                        "WHERE clinic_id=? AND doctor_id=?"
        );
        this.selectByDoctorAndClinic = session.prepare(
                "SELECT * FROM doctor_service.doctor_schedules WHERE doctor_id=? AND clinic_id=?"
        );
        this.selectByDoctors = session.prepare(
                "SELECT * FROM doctor_service.doctor_schedules WHERE doctor_id IN ?"
        );
        this.deleteByDoctor = session.prepare(
                "DELETE FROM doctor_service.doctor_schedules WHERE doctor_id = ?"
        );
        this.deleteByClinicSchedule = session.prepare(
                "DELETE FROM doctor_service.schedules_by_clinic WHERE clinic_id = ? AND doctor_id = ?"
        );
        this.deleteByDoctorOverrides = session.prepare(
                "DELETE FROM doctor_service.doctor_schedule_overrides WHERE doctor_id = ?"
        );
        this.deleteByDoctorUnavailability = session.prepare(
                "DELETE FROM doctor_service.doctor_unavailability WHERE doctor_id = ?"
        );
        this.redisUtil = redisUtil;
    }

    @Override
    public void save(DoctorSchedule schedule) {
        String cacheKey = CACHE_SCHEDULE_PREFIX + schedule.getDoctorId();
        redisUtil.deleteAsync(cacheKey);

        Set<String> days = schedule.getWorkingDays().stream()
                .map(DayOfWeek::name)
                .collect(Collectors.toSet());

        Instant now = Instant.now();

        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(insertSchedule.bind(
                        schedule.getDoctorId(),
                        schedule.getClinicId(),
                        days,
                        schedule.getStartTime(),
                        schedule.getEndTime(),
                        schedule.getSlotDurationMinutes(),
                        schedule.getMaxAppointmentsPerDay(),
                        now, now
                ));

        if (schedule.getClinicId() != null) {
            batch.addStatement(insertScheduleByClinic.bind(
                    schedule.getClinicId(),
                    schedule.getDoctorId(),
                    schedule.getDoctorName(),
                    days,
                    schedule.getStartTime(),
                    schedule.getEndTime(),
                    schedule.getSlotDurationMinutes()
            ));
        }

        session.execute(batch.build());
        redisUtil.setAsync(cacheKey, schedule, 3600);

    }

    @Override
    public void update(DoctorSchedule schedule) {
        String cacheKey = CACHE_SCHEDULE_PREFIX + schedule.getDoctorId();
        redisUtil.deleteAsync(cacheKey);

        Set<String> days = schedule.getWorkingDays().stream()
                .map(DayOfWeek::name)
                .collect(Collectors.toSet());

        Instant now = Instant.now();

        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(updateSchedule.bind(
                        days,
                        schedule.getStartTime(),
                        schedule.getEndTime(),
                        schedule.getSlotDurationMinutes(),
                        schedule.getMaxAppointmentsPerDay(),
                        now,
                        schedule.getDoctorId(),
                        schedule.getClinicId()
                ));

        if (schedule.getClinicId() != null) {
            batch.addStatement(updateScheduleByClinic.bind(
                    schedule.getDoctorName(),
                    days,
                    schedule.getStartTime(),
                    schedule.getEndTime(),
                    schedule.getSlotDurationMinutes(),
                    schedule.getClinicId(),
                    schedule.getDoctorId()
            ));
        }

        session.execute(batch.build());
        redisUtil.setAsync(cacheKey, schedule, 3600);
    }

    @Override
    public Optional<DoctorSchedule> findByDoctorAndClinic(UUID doctorId, UUID clinicId) {

        String cacheKey = CACHE_SCHEDULE_PREFIX + doctorId;
        try {
            DoctorSchedule cached = redisUtil.get(cacheKey, DoctorSchedule.class);
            if (cached != null) {
                log.info("Redis hit for findByDoctorAndClinic: {}", doctorId);
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.warn("Redis GET failed for findByDoctorAndClinic: {}", e.getMessage());
        }

        Row r = session.execute(selectByDoctorAndClinic.bind(doctorId, clinicId)).one();
        if (r == null) {
            return Optional.empty();
        }

        DoctorSchedule schedule = mapRowToSchedule(r);
        redisUtil.setAsync(cacheKey, schedule, 3600);

        return Optional.of(schedule);
    }

    @Override
    public List<DoctorSchedule> findByDoctors(List<UUID> doctorIds) {
        if (doctorIds == null || doctorIds.isEmpty()) return Collections.emptyList();

        ResultSet rs = session.execute(selectByDoctors.bind(doctorIds));

        List<DoctorSchedule> list = new ArrayList<>();
        for (Row r : rs) {
            list.add(mapRowToSchedule(r));
        }
        return list;
    }

    @Override
    public void hardDeleteByDoctor(UUID doctorId) {
        String cacheKey = CACHE_SCHEDULE_PREFIX + doctorId;
        redisUtil.deleteAsync(cacheKey);

        // Find all schedules for this doctor (they might have multiple clinics)
        ResultSet rs = session.execute(selectByDoctors.bind(List.of(doctorId)));
        
        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED);
        batch.addStatement(deleteByDoctor.bind(doctorId));
        batch.addStatement(deleteByDoctorOverrides.bind(doctorId));
        batch.addStatement(deleteByDoctorUnavailability.bind(doctorId));

        for (Row row : rs) {
            UUID clinicId = row.getUuid("clinic_id");
            if (clinicId != null) {
                batch.addStatement(deleteByClinicSchedule.bind(clinicId, doctorId));
            }
        }

        session.execute(batch.build());
    }

    private DoctorSchedule mapRowToSchedule(Row r) {
        Set<DayOfWeek> days = Objects.requireNonNull(r.getSet("working_days", String.class))
                .stream()
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());

        return new DoctorSchedule(
                r.getUuid("doctor_id"),
                r.getUuid("clinic_id"),
                days,
                r.getLocalTime("start_time"),
                r.getLocalTime("end_time"),
                r.getInt("slot_duration_minutes"),
                r.getInt("max_appointments_day")
        );
    }
}
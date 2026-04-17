package com.health.doctor.domain.ports;

import com.health.doctor.domain.model.DoctorSchedule;

import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepositoryPort {
    void save(DoctorSchedule schedule);
    Optional<DoctorSchedule> findByDoctorId(UUID doctorId);
}
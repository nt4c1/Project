package com.health.doctor.domain.ports;

import com.health.doctor.domain.model.DoctorSchedule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepositoryPort {
    void save(DoctorSchedule schedule);

    void update(DoctorSchedule schedule);

    Optional<DoctorSchedule> findByDoctorAndClinic(UUID doctorId, UUID clinicId);

    List<DoctorSchedule> findByDoctors(List<UUID> doctorIds);

    void hardDeleteByDoctor(UUID doctorId);
}
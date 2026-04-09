package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.DoctorSchedule;

import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ScheduleInterface {
    public void createSchedule(UUID doctorId, Set<String> workingDays, LocalTime startTime,
                               LocalTime endTime, int slotDuration, int maxPerDay);
    Optional<Optional<DoctorSchedule>> getSchedule(UUID doctorId);
}

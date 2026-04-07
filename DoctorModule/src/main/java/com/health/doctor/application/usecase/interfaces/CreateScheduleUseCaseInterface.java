package com.health.doctor.application.usecase.interfaces;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

public interface CreateScheduleUseCaseInterface {
    public void execute(UUID doctorId, Set<String> workingDays, Instant startTime,
                        Instant endTime, int slotDuration, int maxPerDay);
}

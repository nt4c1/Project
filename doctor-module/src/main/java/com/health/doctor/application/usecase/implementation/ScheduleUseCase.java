package com.health.doctor.application.usecase.implementation;

import com.health.doctor.application.usecase.interfaces.ScheduleInterface;
import com.health.common.exception.NotFoundException;
import com.health.common.exception.ScheduleException;
import com.health.doctor.domain.model.DoctorSchedule;
import com.health.doctor.domain.ports.ClinicRepositoryPort;
import com.health.doctor.domain.ports.ScheduleRepositoryPort;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import io.micronaut.validation.Validated;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
@Validated
public class ScheduleUseCase implements ScheduleInterface {
    private static final ZoneId NPT = ZoneId.of("Asia/Kathmandu");

    private final ScheduleRepositoryPort repo;
    private final ClinicRepositoryPort clinicRepo;

    public ScheduleUseCase(ScheduleRepositoryPort repo, ClinicRepositoryPort clinicRepo) {

        this.repo = repo;
        this.clinicRepo = clinicRepo;
    }

    @Override
    public void createSchedule(@NotNull UUID doctorId,
                                @NotNull UUID clinicId,
                                @NotEmpty Set<String> workingDays,
                                @NotNull LocalTime startTime,
                                @NotNull LocalTime endTime,
                                @Min(1) int slotDuration,
                                @Min(1) int maxPerDay) {
        Set<DayOfWeek> days = workingDays.stream()
                .map(day -> DayOfWeek.valueOf(day.toUpperCase()))
                .collect(Collectors.toSet());

        if (!startTime.isBefore(endTime))
            throw new ScheduleException("Start time must be before end time");

        long availableMinutes = Duration.between(startTime, endTime).toMinutes();
        int maxPossible = (int) (availableMinutes / slotDuration);

        if (maxPossible == 0)
            throw new ScheduleException("Time window too small for even one slot");

        if (maxPerDay > maxPossible)
            throw new ScheduleException(
                    "Max appointments (" + maxPerDay + ") exceeds available slots (" + maxPossible + ")");


        if (repo.findByDoctorAndClinic(doctorId, clinicId).isPresent())
            throw new ScheduleException("Schedule already exists for this doctor and clinic");

        DoctorSchedule schedule = new DoctorSchedule(
                doctorId, clinicId, days, startTime, endTime, slotDuration, maxPerDay
        );
        repo.save(schedule);
    }

    @Override
    public Optional<DoctorSchedule> getSchedule(@NotNull UUID doctorId, @NotNull UUID clinicId) {
        return (repo.findByDoctorAndClinic(doctorId, clinicId));
    }
}

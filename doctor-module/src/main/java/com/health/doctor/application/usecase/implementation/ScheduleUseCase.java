package com.health.doctor.application.usecase.implementation;

import com.health.doctor.application.usecase.interfaces.ScheduleInterface;
import com.health.doctor.domain.exception.NotFoundException;
import com.health.doctor.domain.model.DoctorSchedule;
import com.health.doctor.domain.ports.ClinicRepositoryPort;
import com.health.doctor.domain.ports.ScheduleRepositoryPort;
import jakarta.inject.Singleton;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
public class ScheduleUseCase implements ScheduleInterface {
    private static final ZoneId NPT = ZoneId.of("Asia/Kathmandu");

    private final ScheduleRepositoryPort repo;
    private final ClinicRepositoryPort clinicRepo;

    public ScheduleUseCase(ScheduleRepositoryPort repo, ClinicRepositoryPort clinicRepo) {

        this.repo = repo;
        this.clinicRepo = clinicRepo;
    }

    @Override
    public void createSchedule(UUID doctorId, Set<String> workingDays, LocalTime startTime, LocalTime endTime, int slotDuration, int maxPerDay) {
        Set<DayOfWeek> days = workingDays.stream()
                .map(day -> DayOfWeek.valueOf(day.toUpperCase()))
                .collect(Collectors.toSet());

        var clinic = clinicRepo.findDoctorAndClinic(doctorId);

        if (clinic != null) {
            DoctorSchedule schedule = new DoctorSchedule(
                    doctorId, clinic.getId(), days, startTime, endTime, slotDuration, maxPerDay
            );
            repo.save(schedule);
        }
        else{
            DoctorSchedule schedule = new DoctorSchedule(
                    doctorId, null, days, startTime, endTime, slotDuration, maxPerDay
            );
            repo.save(schedule);
        }

    }


    @Override
    public Optional<DoctorSchedule> getSchedule(UUID doctorId) {
        if(doctorId == null ) throw new NotFoundException("doctorId is required");
        return (repo.findByDoctorId(doctorId));
    }
}

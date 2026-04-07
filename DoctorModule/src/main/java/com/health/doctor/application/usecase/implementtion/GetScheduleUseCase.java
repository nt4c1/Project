package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.usecase.interfaces.GetScheduleUseCaseInterface;
import com.health.doctor.domain.exception.NotFoundException;
import com.health.doctor.domain.model.DoctorSchedule;
import com.health.doctor.domain.ports.ScheduleRepositoryPort;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class GetScheduleUseCase implements GetScheduleUseCaseInterface {
    private final ScheduleRepositoryPort repo;

    public GetScheduleUseCase(ScheduleRepositoryPort repo) {
        this.repo = repo;
    }

    public Optional<DoctorSchedule> execute(UUID doctorId) {
        if(doctorId == null ) throw new NotFoundException("doctorId is required");
        return Optional.ofNullable(repo.findByDoctorId(doctorId));
    }
}
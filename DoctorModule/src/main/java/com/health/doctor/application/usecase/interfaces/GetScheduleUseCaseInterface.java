package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.DoctorSchedule;

import java.util.Optional;
import java.util.UUID;

public interface GetScheduleUseCaseInterface {
    Optional<DoctorSchedule> execute(UUID doctorId);
}

package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.DoctorSchedule;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ScheduleInterface {
    void createSchedule(@NotNull UUID doctorId,
                        @NotNull UUID clinicId,
                        @NotEmpty Set<String> workingDays,
                        @NotNull LocalTime startTime,
                        @NotNull LocalTime endTime,
                        @Min(1) int slotDurationMinutes,
                        @Min(1) int maxAppointmentsDay);

    Optional<DoctorSchedule> getSchedule(@NotNull UUID doctorId, @NotNull UUID clinicId);
}

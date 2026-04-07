package com.health.doctor.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class DoctorSchedule {
    private UUID doctorId;
    private Set<DayOfWeek> workingDays;
    private Instant startTime;
    private Instant endTime;
    private int slotDurationMinutes;
    private int maxAppointmentsPerDay;

    public DoctorSchedule(UUID doctorId, Set<DayOfWeek> workingDays, Instant startTime,
                          Instant endTime, int slotDurationMinutes, int maxAppointmentsPerDay) {
        this.doctorId = doctorId;
        this.workingDays = workingDays;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotDurationMinutes = slotDurationMinutes;
        this.maxAppointmentsPerDay = maxAppointmentsPerDay;
    }

    public boolean isWorkingDay(DayOfWeek day) {
        return workingDays.contains(day);
    }

    public boolean isValidSlot(Instant time) {
        return !time.isBefore(startTime) && !time.plusSeconds(slotDurationMinutes * 60L).isAfter(endTime);
    }
}
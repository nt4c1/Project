package com.health.doctor.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class DoctorSchedule {

    private UUID doctorId;
    private UUID clinicId;
    private String doctorName;
    private Set<DayOfWeek> workingDays;
    private LocalTime startTime;
    private LocalTime endTime;
    private int slotDurationMinutes;
    private int maxAppointmentsPerDay;

    public DoctorSchedule(UUID doctorId, UUID clinicId,
                          Set<DayOfWeek> workingDays,
                          LocalTime startTime, LocalTime endTime,
                          int slotDurationMinutes, int maxAppointmentsPerDay) {
        this.doctorId = doctorId;
        this.clinicId = clinicId;
        this.workingDays = workingDays;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotDurationMinutes = slotDurationMinutes;
        this.maxAppointmentsPerDay = maxAppointmentsPerDay;
    }

    public boolean isWorkingDay(DayOfWeek day) {
        return workingDays.contains(day);
    }

    public boolean isValidSlot(LocalTime time) {
        if (time.isBefore(startTime) || time.plusMinutes(slotDurationMinutes).isAfter(endTime)) {
            return false;
        }

        // alignment check: (requestedTime - startTime) must be a multiple of slotDurationMinutes
        long minutesFromStart = java.time.Duration.between(startTime, time).toMinutes();
        return minutesFromStart % slotDurationMinutes == 0;
    }
}
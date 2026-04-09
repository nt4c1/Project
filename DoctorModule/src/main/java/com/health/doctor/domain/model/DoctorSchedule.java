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

    public DoctorSchedule(UUID doctorId, Set<DayOfWeek> workingDays,
                          LocalTime startTime, LocalTime endTime,
                          int slotDurationMinutes, int maxAppointmentsPerDay) {
        this(doctorId, null, workingDays, startTime, endTime,
                slotDurationMinutes, maxAppointmentsPerDay);
    }

    public boolean isWorkingDay(DayOfWeek day) {
        return workingDays.contains(day);
    }

    public boolean isValidSlot(LocalTime time) {
        return !time.isBefore(startTime)
                && !time.plusSeconds(slotDurationMinutes * 60L).isAfter(endTime);
    }
}
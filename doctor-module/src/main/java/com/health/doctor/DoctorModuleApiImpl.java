package com.health.doctor;

import com.health.doctor.application.usecase.interfaces.AppointmentInterface;
import com.health.doctor.application.usecase.interfaces.DoctorInterface;
import com.health.doctor.application.usecase.interfaces.ScheduleInterface;
import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.DoctorSchedule;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements the doctor module's public API.
 * Lives inside the doctor module — delegates to use cases.
 * The patient module sees only the DoctorModuleApi interface, never this class.
 */
@Singleton
public class DoctorModuleApiImpl implements DoctorModuleApi {

    private final DoctorInterface          doctorUseCase;
    private final AppointmentInterface     appointmentUseCase;
    private final ScheduleInterface        scheduleUseCase;
    private final AppointmentRepositoryPort appointmentRepo;

    public DoctorModuleApiImpl(DoctorInterface doctorUseCase,
                               AppointmentInterface appointmentUseCase,
                               ScheduleInterface scheduleUseCase,
                               AppointmentRepositoryPort appointmentRepo) {
        this.doctorUseCase      = doctorUseCase;
        this.appointmentUseCase = appointmentUseCase;
        this.scheduleUseCase    = scheduleUseCase;
        this.appointmentRepo    = appointmentRepo;
    }

    @Override
    public List<Doctor> getDoctorsByLocation(String location) {
        return doctorUseCase.getDoctorsByLocation(location);
    }

    @Override
    public Optional<DoctorSchedule> getDoctorSchedule(UUID doctorId, UUID clinicId) {
        return scheduleUseCase.getSchedule(doctorId, clinicId);
    }

    @Override
    public UUID bookAppointment(UUID doctorId, UUID patientId, UUID clinicId,
                                LocalDate date, LocalTime time,
                                String reasonForVisit) {
        return appointmentUseCase.createAppointment(
                doctorId, patientId, clinicId, date, time, reasonForVisit);
    }

    @Override
    public void cancelAppointment(UUID appointmentId, UUID patientId,
                                  UUID doctorId, LocalDate date, LocalTime time,
                                  String cancellationReason) {
        appointmentUseCase.cancelAppointment(
                appointmentId, patientId, doctorId, date, time, cancellationReason);
    }

    @Override
    public List<Appointment> getPatientAppointments(UUID patientId, LocalDate date) {
        return appointmentRepo.findByPatientAndDate(patientId, date);
    }
}

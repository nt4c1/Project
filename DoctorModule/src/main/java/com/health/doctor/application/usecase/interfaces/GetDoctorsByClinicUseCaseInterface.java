package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.Doctor;

import java.util.List;
import java.util.UUID;

public interface GetDoctorsByClinicUseCaseInterface {
    List<Doctor> execute(UUID clinicId);
}

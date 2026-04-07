package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.Doctor;

import java.util.List;

public interface GetDoctorsByLocationUseCaseInterface {
    List<Doctor> executeNear(String locationText);
    List<Doctor> execute(String geohashPrefix);
}

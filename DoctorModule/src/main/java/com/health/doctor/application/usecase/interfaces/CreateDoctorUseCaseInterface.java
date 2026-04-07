package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.DoctorType;

import java.util.UUID;

public interface CreateDoctorUseCaseInterface {
    UUID execute(String name,
                 UUID clinicId,
                 DoctorType type,
                 String specialization,String email,
                 String password);
}

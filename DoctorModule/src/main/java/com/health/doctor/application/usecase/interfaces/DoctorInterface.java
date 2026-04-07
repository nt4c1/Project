package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.DoctorType;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.grpc.doctor.DoctorLoginResponse;

import java.util.List;
import java.util.UUID;

public interface DoctorInterface {
    UUID createDoctor(String name, UUID clinicId, DoctorType type, String specialization, String email, String password);
    DoctorLoginResponse loginDoctor(String email, String password);
    ValidateTokenResponse validateDoctor(String token);
    List<Doctor> getDoctorsByClinic(UUID clinicId);
    List<Doctor> getDoctorsByLocationText(String locationText);
    List<Doctor> getDoctorsByLocationGeohash(String geohashPrefix);
    void UpdateDoctorLocation(UUID doctorId, String text);
}

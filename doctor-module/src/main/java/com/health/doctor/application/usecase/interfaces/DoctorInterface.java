package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.DoctorType;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.ValidateTokenResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public interface DoctorInterface {

    UUID createDoctor(@NotBlank String name,
                      List<UUID> clinicIds,
                      @NotNull DoctorType type,
                      @NotBlank String specialization,
                      @NotBlank @Email String email,
                      @NotBlank @Size(min = 6) String password);

    TokenResponse loginDoctor(@NotBlank @Email String email, @NotBlank String password);

    ValidateTokenResponse validateDoctor(@NotBlank String token);

    List<Doctor> getDoctorsByClinic(@NotNull UUID clinicId);

    List<Doctor> getDoctorsByLocationText(@NotBlank String locationText);

    List<Doctor> getDoctorsByLocationGeohash(@NotBlank String geohashPrefix);

    void updateDoctorLocation(@NotNull UUID doctorId, @NotNull UUID clinicId, @NotBlank String locationText);

    void updateDoctor(@NotNull UUID doctorId,
                      @NotBlank @Email String email,
                      @NotBlank @Size(min = 6) String password);

    void deleteDoctor(@NotNull UUID doctorId,
                      @NotBlank @Email String email,
                      @NotBlank @Size(min = 6) String password);

}
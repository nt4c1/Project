package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.DoctorType;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.ValidateTokenResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public interface DoctorInterface {

    UUID createDoctor(@NotBlank String name,
                      List<UUID> clinicIds,
                      @NotNull DoctorType type,
                      @NotBlank String specialization,
                      @NotBlank String email,
                      @NotBlank String password,
                      @NotBlank String phone);

    TokenResponse loginDoctor(@NotBlank String email, @NotBlank String password);

    ValidateTokenResponse validateDoctor(@NotBlank String token);

    List<Doctor> getDoctorsByClinic(@NotNull UUID clinicId);

    List<Doctor> getDoctorsByLocationText(@NotBlank String locationText);

    List<Doctor> getDoctorsByLocationGeohash(@NotBlank String geohashPrefix);

    void updateDoctorLocation(@NotNull UUID doctorId,UUID clinicId, @NotBlank String locationText);

    void updateDoctor(@NotNull UUID doctorId,
                      @NotBlank String email,
                      @NotBlank String password,
                      @NotBlank String phone);

    void addClinicToDoctor(@NotNull UUID doctorId, @NotEmpty List<UUID> clinicIds);

    void removeClinicFromDoctor(@NotNull UUID doctorId, @NotEmpty List<UUID> clinicIds);

    void deleteDoctor(@NotNull UUID doctorId,
                      @NotBlank  String email,
                      @NotBlank  String password);

    String forgotPassword(@NotBlank  String email);

    void resetPassword(@NotBlank String newPassword);

    com.health.grpc.doctor.DoctorActiveResponse isDoctorActive(@NotNull java.util.UUID doctorId, java.util.UUID clinicId);

}
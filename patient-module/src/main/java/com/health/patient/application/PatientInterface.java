package com.health.patient.application;

import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.patient.domain.model.Patient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Optional;
import java.util.UUID;

public interface PatientInterface {

    UUID registerPatient(@NotBlank String name,
                         @NotBlank @Email String email,
                         @NotBlank @Size(min = 6) String password,
                         @NotBlank String phone);

    UUID createPatient(@NotBlank String name,
                       @NotBlank @Email String email,
                       @NotBlank String phone,
                       @NotBlank @Size(min = 6) String password);

    Optional<Patient> getPatient(@NotNull UUID id);

    TokenResponse loginPatient(@NotBlank @Email String email,
                               @NotBlank String password);

    ValidateTokenResponse validatePatient(@NotBlank String token);

    void updatePatient(@NotNull UUID patientId,
                       @NotBlank @Email String email,
                       @NotBlank @Size(min = 6) String password);

    void deletePatient(@NotNull UUID patientId,
                       @NotBlank @Email String email,
                       @NotBlank String password);

}
package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.Clinic;
import com.health.grpc.auth.TokenResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public interface ClinicInterface {
    UUID createClinic(@NotBlank String name, @NotBlank String locationText, @NotBlank String email, @NotBlank String password, String phone);

    TokenResponse loginClinic(@NotBlank String email, @NotBlank String password);

    void updateClinic(@NotNull UUID clinicId, @NotBlank String name);

    void deleteClinic(@NotNull UUID clinicId);

    void updateClinicLocation(@NotNull UUID clinicId, @NotBlank String locationText);

    List<Clinic> getClinicsByLocationText(String locationText);

    List<Clinic> getClinicsByLocationGeohash(String geohashPrefix);

    Clinic getClinicById(UUID clinicId);

    List<Clinic> findByName(String name);

    List<Clinic> getNearbyClinicsByLocationText(String locationText);

    List<Clinic> searchClinics(String name, int page, int size);

    long countClinicsByName(String name);

    String forgotPassword(String email);

    void resetPassword(@NotBlank String newPassword);
}

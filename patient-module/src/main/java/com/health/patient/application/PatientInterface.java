package com.health.patient.application;

import com.health.grpc.auth.ValidateTokenResponse;
import com.health.grpc.patient.PatientLoginResponse;
import com.health.patient.domain.model.Patient;

import java.util.Optional;
import java.util.UUID;

public interface PatientInterface {

    UUID registerPatient(String name, String email, String password, String phone);

    UUID createPatient(String name, String email, String phone, String password);

    Optional<Patient> getPatient(UUID id);

    PatientLoginResponse loginPatient(String email, String password);

    ValidateTokenResponse validatePatient(String token);

    void updatePatient(UUID patientId, String email, String password);

    void deletePatient(UUID patientId, String email, String password);

}
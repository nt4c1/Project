package com.health.patient.domain.ports;

import com.health.patient.domain.model.Patient;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepositoryPort {
    void save(Patient patient);

    Optional<Patient> findById(UUID patientId);

    Optional<Patient> findByEmail(String email);

    void updatePatient(UUID patientID,String email,String password);

    void deletePatient(UUID patientId);
}
package com.health.patient.application;

import com.health.patient.domain.model.Patient;

import java.util.Optional;
import java.util.UUID;

public interface GetPatientUseCaseInterface {
    Optional<Patient> execute(UUID id);
}

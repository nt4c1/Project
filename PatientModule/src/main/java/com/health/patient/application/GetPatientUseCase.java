package com.health.patient.application;

import com.health.patient.domain.model.Patient;
import com.health.patient.domain.ports.PatientRepositoryPort;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class GetPatientUseCase implements GetPatientUseCaseInterface{
    private final PatientRepositoryPort repo;

    public GetPatientUseCase(PatientRepositoryPort repo) {
        this.repo = repo;
    }

    public Optional<Patient> execute(UUID id) {
        return repo.findById(id);
    }
}
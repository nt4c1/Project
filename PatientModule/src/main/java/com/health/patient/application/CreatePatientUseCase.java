package com.health.patient.application;

import com.health.patient.domain.model.Patient;
import com.health.patient.domain.ports.PatientRepositoryPort;
import jakarta.inject.Singleton;
import org.mindrot.jbcrypt.BCrypt;

import java.util.UUID;

@Singleton
public class CreatePatientUseCase implements CreatePatientUseCaseInterface{
    private final PatientRepositoryPort repo;

    public CreatePatientUseCase(PatientRepositoryPort repo) {
        this.repo = repo;
    }

    public UUID execute(String name,String email,String phone,String password) {
        UUID id = UUID.randomUUID();
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());

        repo.save(new Patient
                (id, name,email,phone,passwordHash));
        return id;
    }
}
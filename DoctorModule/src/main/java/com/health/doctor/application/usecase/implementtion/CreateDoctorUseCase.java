package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.usecase.interfaces.CreateDoctorUseCaseInterface;
import com.health.doctor.domain.exception.AlreadyExistsException;
import com.health.doctor.domain.exception.InvalidArgumentException;
import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.DoctorType;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import jakarta.inject.Singleton;
import org.mindrot.jbcrypt.BCrypt;

import java.util.UUID;

@Singleton
public class CreateDoctorUseCase implements CreateDoctorUseCaseInterface {

    private final DoctorRepositoryPort repo;

    public CreateDoctorUseCase(DoctorRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public UUID execute(String name, UUID clinicId, DoctorType type,
                        String specialization, String email, String password) {
        if (name == null || name.isBlank())
            throw new InvalidArgumentException("Name is required");
        if (email == null || email.isBlank())
            throw new InvalidArgumentException("Email is required");
        if (password == null || password.length() < 6)
            throw new InvalidArgumentException("Password must be at least 6 characters");

        if (repo.findByEmail(email).isPresent())
            throw new AlreadyExistsException("Doctor already exists with email: " + email);

        UUID doctorId = UUID.randomUUID();
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());

        Doctor doctor = new Doctor(doctorId, name, clinicId, type,
                specialization, true, email, passwordHash);
        repo.save(doctor);
        return doctorId;
    }
}
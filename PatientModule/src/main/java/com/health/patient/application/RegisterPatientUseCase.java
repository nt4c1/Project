package com.health.patient.application;

import com.health.patient.domain.exception.AlreadyExistsException;
import com.health.patient.domain.exception.InvalidArgumentException;
import com.health.patient.domain.exception.NotFoundException;
import com.health.patient.domain.model.Patient;
import com.health.patient.domain.ports.PatientRepositoryPort;
import com.health.patient.infrastructure.JwtProvider;
import com.health.grpc.patient.PatientLoginResponse;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.util.UUID;

@Singleton
@Slf4j
public class RegisterPatientUseCase implements RegisterPatientUseCaseInterface {

    private final PatientRepositoryPort repo;
    private final JwtProvider jwtProvider;

    public RegisterPatientUseCase(PatientRepositoryPort repo,
                                  JwtProvider jwtProvider) {
        this.repo = repo;
        this.jwtProvider = jwtProvider;
    }

    public UUID execute(String name, String email,
                        String password, String phone) {
        if (name == null || name.isBlank())
            throw new InvalidArgumentException("Name is required");
        if (email == null || email.isBlank())
            throw new InvalidArgumentException("Email is required");
        if (password == null || password.length() < 6)
            throw new InvalidArgumentException(
                    "Password must be at least 6 characters");

        if (repo.findByEmail(email).isPresent())
            throw new AlreadyExistsException(
                    "Patient already exists with email: " + email);

        UUID id = UUID.randomUUID();
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        repo.save(new Patient(id, name, email, phone, hash));
        log.info("Patient registered: {}", id);
        return id;
    }

    public PatientLoginResponse login(String email, String password) {
        if (email == null || email.isBlank())
            throw new InvalidArgumentException("Email is required");
        if (password == null || password.isBlank())
            throw new InvalidArgumentException("Password is required");

        Patient patient = repo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(
                        "Patient not found: " + email));

        if (!BCrypt.checkpw(password, patient.getPasswordHash()))
            throw new InvalidArgumentException("Invalid credentials");

        String accessToken = jwtProvider.generateAccessToken(
                patient.getId().toString(), "PATIENT");
        String refreshToken = jwtProvider.generateRefreshToken(
                patient.getId().toString());

        log.info("Patient logged in: {}", patient.getId());

        return PatientLoginResponse.newBuilder()
                .setSuccess(true)
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setPatientId(patient.getId().toString())
                .setMessage("Login successful")
                .build();
    }
}
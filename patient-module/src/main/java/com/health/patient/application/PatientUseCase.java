package com.health.patient.application;

import com.health.common.auth.JwtProvider;
import com.health.common.utils.ValidationUtil;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.PatientMessage;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.patient.domain.exception.AlreadyExistsException;
import com.health.patient.domain.exception.InvalidArgumentException;
import com.health.patient.domain.exception.NotFoundException;
import com.health.patient.domain.model.Patient;
import com.health.patient.domain.ports.PatientRepositoryPort;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import io.micronaut.validation.Validated;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.cache.annotation.CacheInvalidate;

import com.health.patient.adapters.output.nats.PatientNatsClient;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
@Validated
public class PatientUseCase implements PatientInterface {

    private final PatientRepositoryPort repo;
    private final JwtProvider           jwtProvider;
    private final PatientNatsClient     natsClient;

    public PatientUseCase(PatientRepositoryPort repo, JwtProvider jwtProvider, PatientNatsClient natsClient) {
        this.repo        = repo;
        this.jwtProvider = jwtProvider;
        this.natsClient  = natsClient;
    }

    @Cacheable("patients")
    @Override
    public Optional<Patient> getPatient(@NotNull UUID id) {
        return repo.findById(id);
    }

    @Override
    public UUID registerPatient(@NotBlank String name, @NotBlank @Email String email, @NotBlank @Size(min = 6) String password, @NotBlank String phone) {
        // findByEmail now uses patients_by_email lookup

        if(!ValidationUtil.isValidEmail(email))
            throw new InvalidArgumentException("Invalid email format");
        if(!ValidationUtil.isValidPassword(password))
            throw new InvalidArgumentException("Invalid password format");
        if(!ValidationUtil.isValidPhone(phone))
            throw new InvalidArgumentException("Invalid phone number format");
        if (repo.findByEmail(email).isPresent())
            throw new AlreadyExistsException("Patient already exists with email: " + email);

        UUID   id   = UUID.randomUUID();
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        repo.save(new Patient(id, name, email, phone, hash));
        log.info("Patient registered: {}", id);
        natsClient.sendPatientCreated(id.toString());
        return id;
    }

    @CacheInvalidate(value = "patients", parameters = "patientId")
    @Override
    public void updatePatient(@NotNull UUID patientId, @NotBlank @Email String email, @NotBlank @Size(min = 6) String password) {
        repo.updatePatient(patientId, email, password);
        natsClient.sendPatientUpdated(patientId.toString());
    }

    @Override
    public TokenResponse loginPatient(@NotBlank @Email String email, @NotBlank String password) {
        Patient patient = repo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + email));

        if (!BCrypt.checkpw(password, patient.getPasswordHash()))
            throw new InvalidArgumentException("Invalid credentials");

        String accessToken = jwtProvider.generateAccessToken(
                patient.getId().toString(), "Patient");
        String refreshToken = jwtProvider.generateRefreshToken(
                patient.getId().toString());

        log.info("Patient logged in: {}", patient.getId());

        return TokenResponse.newBuilder()
                .setSuccess(true)
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setPatient(PatientMessage.newBuilder()
                        .setPatientId(patient.getId().toString())
                        .setName(patient.getName())
                        .setEmail(patient.getEmail())
                        .setPhone(patient.getPhone())
                        .build())
                .setMessage("Login Successful")
                .build();
    }

    @Override
    public ValidateTokenResponse validatePatient(@NotBlank String token) {
        try {
            String  userId = jwtProvider.extractUserId(token);
            String  role   = jwtProvider.extractRole(token);
            boolean valid  = jwtProvider.isValid(token);
            return ValidateTokenResponse.newBuilder()
                    .setValid(valid)
                    .setUserId(userId)
                    .setRole(role)
                    .setMessage(valid ? "Token valid" : "Token invalid")
                    .build();
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return ValidateTokenResponse.newBuilder()
                    .setValid(false)
                    .setMessage("Token invalid: " + e.getMessage())
                    .build();
        }
    }

    @CacheInvalidate(value = "patients", parameters = "patientId")
    @Override
    public void deletePatient(@NotNull UUID patientId, @NotBlank @Email String email, @NotBlank String password) {
        repo.deletePatient(patientId);
    }
}

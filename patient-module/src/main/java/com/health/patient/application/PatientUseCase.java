package com.health.patient.application;

import com.health.common.auth.JwtAuthInterceptor;
import com.health.common.auth.JwtProvider;
import com.health.common.exception.AlreadyExistsException;
import com.health.common.exception.InvalidArgumentException;
import com.health.common.exception.NotFoundException;
import com.health.common.utils.SecurityUtils;
import com.health.common.utils.ValidationUtil;
import com.health.grpc.common.PatientMessage;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.patient.adapters.output.nats.PatientNatsClient;
import com.health.patient.domain.model.Patient;
import com.health.patient.domain.model.PatientCredentials;
import com.health.patient.domain.ports.PatientCredentialsRepositoryPort;
import com.health.patient.domain.ports.PatientRepositoryPort;
import io.micronaut.validation.Validated;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
@Validated
public class PatientUseCase implements PatientInterface {

    private final PatientRepositoryPort repo;
    private final PatientCredentialsRepositoryPort credentialsRepo;
    private final JwtProvider           jwtProvider;
    private final PatientNatsClient     natsClient;

    public PatientUseCase(PatientRepositoryPort repo,
                          PatientCredentialsRepositoryPort credentialsRepo,
                          JwtProvider jwtProvider,
                          PatientNatsClient natsClient) {
        this.repo        = repo;
        this.credentialsRepo = credentialsRepo;
        this.jwtProvider = jwtProvider;
        this.natsClient  = natsClient;
    }

    @Override
    public Optional<Patient> getPatient(@NotNull UUID id) {
        // Ownership check: If a Patient is calling, they can only see their own profile
        if (SecurityUtils.isPatient()) {
            SecurityUtils.validateOwnership(id);
        }
        return repo.findById(id);
    }

    @Override
    public UUID registerPatient(@NotBlank String name, @NotBlank @Email String email, @NotBlank @Size(min = 6) String password, @NotBlank String phone) {
        if(!ValidationUtil.isValidEmail(email))
            throw new InvalidArgumentException("Invalid email format");
        if(!ValidationUtil.isValidPassword(password))
            throw new InvalidArgumentException("Invalid password format");
        if(!ValidationUtil.isValidPhone(phone))
            throw new InvalidArgumentException("Invalid phone number format");
        
        Optional<PatientCredentials> existingCreds = credentialsRepo.findByEmail(email);
        if (existingCreds.isPresent()) {
            UUID patientId = existingCreds.get().getPatientId();
            if (repo.isDeleted(patientId)) {
                log.info("Reactivating soft-deleted patient: {}", patientId);
                String hash = BCrypt.hashpw(password, BCrypt.gensalt());
                Instant now = Instant.now();
                Patient updated = new Patient(patientId, name, email, phone, false, now, now);
                repo.reactivate(updated);
                credentialsRepo.updatePassword(patientId, hash);
                natsClient.sendPatientCreated(patientId.toString());
                return patientId;
            }
            throw new AlreadyExistsException("Patient already exists with email: " + email);
        }

        UUID   id   = UUID.randomUUID();
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        Instant now = Instant.now();
        
        repo.save(new Patient(id, name, email, phone, false, now, now));
        credentialsRepo.save(new PatientCredentials(id, email, phone, hash, now, now));
        
        log.info("Patient registered: {}", id);
        natsClient.sendPatientCreated(id.toString());
        return id;
    }

    @Override
    public void updatePatient(@NotNull UUID patientId, @NotBlank @Email String email, @NotBlank @Size(min = 6) String password) {
        // Ownership check
        SecurityUtils.validateOwnership(patientId);

        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        repo.updatePatient(patientId, email);
        credentialsRepo.updateEmail(patientId, email);
        credentialsRepo.updatePassword(patientId, hash);
        
        natsClient.sendPatientUpdated(patientId.toString());
    }

    @Override
    public TokenResponse refreshToken(@NotBlank String refreshToken) {
        if (!jwtProvider.isValid(refreshToken)) {
            throw new InvalidArgumentException("Invalid refresh token");
        }

        String type = jwtProvider.extractClaims(refreshToken).get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new InvalidArgumentException("Invalid token type");
        }

        String userId = jwtProvider.extractUserId(refreshToken);
        Patient patient = repo.findById(UUID.fromString(userId))
                .orElseThrow(() -> new NotFoundException("Patient not found"));

        if (repo.isDeleted(patient.getId())) {
            throw new NotFoundException("Patient not found");
        }

        String accessToken = jwtProvider.generateAccessToken(patient.getId().toString(), "Patient");
        String newRefreshToken = jwtProvider.generateRefreshToken(patient.getId().toString());

        return TokenResponse.newBuilder()
                .setSuccess(true)
                .setAccessToken(accessToken)
                .setRefreshToken(newRefreshToken)
                .setMessage("Token refreshed successfully")
                .setPatient(PatientMessage.newBuilder()
                        .setPatientId(patient.getId().toString())
                        .setName(patient.getName())
                        .setEmail(patient.getEmail())
                        .setPhone(patient.getPhone())
                        .build())
                .build();
    }

    @Override
    public TokenResponse loginPatient(@NotBlank @Email String email, @NotBlank String password) {
        PatientCredentials credentials = credentialsRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + email));

        if (repo.isDeleted(credentials.getPatientId())) {
            throw new NotFoundException("Patient not found: " + email);
        }

        if (!BCrypt.checkpw(password, credentials.getPasswordHash()))
            throw new InvalidArgumentException("Invalid credentials");

        Patient patient = repo.findById(credentials.getPatientId())
                .orElseThrow(() -> new NotFoundException("Patient profile not found"));

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

    @Override
    public void deletePatient(@NotNull UUID patientId, @NotBlank @Email String email, @NotBlank @Size(min = 6) String password) {
        // Ownership check
        SecurityUtils.validateOwnership(patientId);

        PatientCredentials credentials = credentialsRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + email));

        if (!credentials.getPatientId().equals(patientId)) {
            throw new InvalidArgumentException("Patient ID does not match email");
        }

        if (!BCrypt.checkpw(password, credentials.getPasswordHash())) {
            throw new InvalidArgumentException("Invalid credentials");
        }

        repo.deletePatient(patientId);
        natsClient.sendPatientDeleted(patientId.toString());
    }

    @Override
    public String forgotPassword(@NotBlank @Email String email) {
        PatientCredentials credentials = credentialsRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + email));
        
        return jwtProvider.generateResetToken(credentials.getPatientId().toString(), "Patient");
    }

    @Override
    public void resetPassword(@NotBlank String newPassword) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        String type = JwtAuthInterceptor.TOKEN_TYPE_KEY.get();

        if (currentUserId == null || role == null || type == null) {
            throw new InvalidArgumentException("Authentication context missing");
        }

        if (!"reset".equals(type)) {
            throw new InvalidArgumentException("Invalid token type: reset token required");
        }

        if (!"Patient".equalsIgnoreCase(role)) {
            throw new InvalidArgumentException("Token is not for a Patient");
        }

        String passwordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        credentialsRepo.updatePassword(currentUserId, passwordHash);
        log.info("Password reset successful for patient: {}", currentUserId);
    }
}

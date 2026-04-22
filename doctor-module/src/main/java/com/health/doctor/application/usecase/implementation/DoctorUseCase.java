package com.health.doctor.application.usecase.implementation;

import com.health.common.auth.JwtProvider;
import com.health.common.utils.ValidationUtil;
import com.health.doctor.application.usecase.interfaces.DoctorInterface;
import com.health.doctor.application.service.LocationService;
import com.health.doctor.domain.exception.AlreadyExistsException;
import com.health.doctor.domain.exception.InvalidArgumentException;
import com.health.doctor.domain.exception.NotFoundException;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.ports.*;
import com.health.doctor.adapters.output.nats.DoctorNatsClient;
import com.health.doctor.adapters.output.persistence.repository.DoctorRepositoryImpl;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.grpc.common.DoctorMessage;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import io.micronaut.validation.Validated;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@Validated
public class DoctorUseCase implements DoctorInterface {

    private final DoctorRepositoryPort repo;
    private final CredentialsRepositoryPort credentialsRepo;
    private final ClinicRepositoryPort clinicRepo;
    private final JwtProvider jwtProvider;
    private final LocationService locationService;
    private final DoctorNatsClient natsClient;

    public DoctorUseCase(DoctorRepositoryPort repo,
                         CredentialsRepositoryPort credentialsRepo,
                         ClinicRepositoryPort clinicRepo,
                         JwtProvider jwtProvider,
                         LocationService locationService,
                         DoctorNatsClient natsClient) {
        this.repo = repo;
        this.credentialsRepo = credentialsRepo;
        this.clinicRepo = clinicRepo;
        this.jwtProvider = jwtProvider;
        this.locationService = locationService;
        this.natsClient = natsClient;
    }

    @Override
    public UUID createDoctor(@NotBlank String name,
                             List<UUID> clinicIds,
                             @NotNull DoctorType type,
                             @NotBlank String specialization,
                             @NotBlank  String email,
                             @NotBlank  String password) {

        if (credentialsRepo.findByEmail(email).isPresent())
            throw new AlreadyExistsException("Doctor already exists with email: " + email);

        if(!ValidationUtil.isValidEmail(email))
            throw new InvalidArgumentException("Invalid email format");
        if(!ValidationUtil.isValidPassword(password))
            throw new InvalidArgumentException("Invalid password format");
        if(!ValidationUtil.isValidPassword(password))
            throw new InvalidArgumentException("Invalid password format");

        UUID doctorId = UUID.randomUUID();
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        Instant now = Instant.now(Clock.system(ZoneId.of("Asia/Kathmandu")));

        Doctor doctor = new Doctor(doctorId, name, clinicIds, type, specialization, true, false, now, now);
        DoctorCredentials credentials = new DoctorCredentials(doctorId, email, passwordHash, now, now);

        credentialsRepo.save(credentials);
        repo.save(doctor);

        if (clinicIds != null && !clinicIds.isEmpty()) {
            for (UUID clinicId : clinicIds) {
                Location local = clinicRepo.getLocation(clinicId);
                if (local != null) {
                    repo.updateLocation(doctorId, clinicId, local);
                }
            }
        }
        natsClient.sendDoctorCreated(doctorId.toString());
        return doctorId;
    }

    @Override
    public TokenResponse loginDoctor(@NotBlank @Email String email, @NotBlank String password) {
        DoctorCredentials credentials = credentialsRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + email));

        if (!BCrypt.checkpw(password, credentials.getPasswordHash()))
            throw new InvalidArgumentException("Invalid credentials");

        Doctor doctor = repo.findById(credentials.getDoctorId())
                .orElseThrow(() -> new NotFoundException("Doctor profile not found"));

        String accessToken = jwtProvider.generateAccessToken(doctor.getId().toString(), "Doctor");
        String refreshToken = jwtProvider.generateRefreshToken(doctor.getId().toString());

        log.info("Doctor logged in: {} ({})", doctor.getName(), doctor.getId());

        List<String> clinicIdsString = (doctor.getClinicIds() != null && !doctor.getClinicIds().isEmpty())
                ? doctor.getClinicIds().stream().map(UUID::toString).collect(Collectors.toList())
                : List.of(DoctorRepositoryImpl.NO_CLINIC_ID.toString());

        return TokenResponse.newBuilder()
                .setSuccess(true)
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setDoctor(DoctorMessage.newBuilder()
                        .setDoctorId(doctor.getId().toString())
                        .setName(doctor.getName())
                        .setType(com.health.grpc.common.DoctorType.valueOf(doctor.getType().name()))
                        .setSpecialization(doctor.getSpecialization())
                        .setIsActive(doctor.isActive())
                        .addAllClinicIds(clinicIdsString)
                )
                .setMessage("Login Successfully")
                .build();
    }

    @Override
    public ValidateTokenResponse validateDoctor(@NotBlank String token) {
        try {
            String userid = jwtProvider.extractUserId(token);
            String role = jwtProvider.extractRole(token);
            boolean valid = jwtProvider.isValid(token);

            return ValidateTokenResponse.newBuilder()
                    .setValid(valid)
                    .setUserId(userid)
                    .setRole(role)
                    .setMessage(valid ? "Token Valid" : "Token Invalid")
                    .build();
        } catch (Exception e) {
            log.warn("Token Validation Failed: {}", e.getMessage());
            return ValidateTokenResponse.newBuilder()
                    .setValid(false)
                    .setMessage("Token Invalid: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public List<Doctor> getDoctorsByClinic(@NotNull UUID clinicId) {
        return repo.findByClinicId(clinicId);
    }

    @Override
    public List<Doctor> getDoctorsByLocationText(@NotBlank String locationText) {
        Location location = locationService.resolve(locationText);
        List<Doctor> found = repo.findNearby(location.getGeohash(), location.getLatitude(), location.getLongitude());
        log.info("UseCase: Found {} doctors for {}: {}", found.size(), locationText,
                found.stream().map(d -> d.getName() + "=" + d.getDistance()).toList());
        return found;
    }

    @Override
    public List<Doctor> getDoctorsByLocationGeohash(@NotBlank String geohashPrefix) {
        return repo.findByGeohashPrefix(geohashPrefix);
    }

    @Override
    public void updateDoctorLocation(@NotNull UUID doctorId,UUID clinicId, @NotBlank String locationText) {
        Location location = locationService.resolve(locationText);
        repo.updateLocation(doctorId, clinicId, location);
        natsClient.sendDoctorUpdated(doctorId.toString());
    }

    @Override
    public void updateDoctor(@NotNull UUID doctorId, @NotBlank @Email String email, @NotBlank @Size(min = 6) String password) {
        repo.updateDoctor(doctorId, email, password);
        natsClient.sendDoctorUpdated(doctorId.toString());
    }

    @Override
    public void deleteDoctor(@NotNull UUID doctorId, @NotBlank @Email String email, @NotBlank @Size(min = 6) String password) {
        repo.deleteDoctor(doctorId, email, password);
        natsClient.sendDoctorUpdated(doctorId.toString());
    }
}
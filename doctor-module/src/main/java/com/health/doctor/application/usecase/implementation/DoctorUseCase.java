package com.health.doctor.application.usecase.implementation;

import com.health.doctor.application.usecase.interfaces.DoctorInterface;
import com.health.doctor.domain.exception.AlreadyExistsException;
import com.health.doctor.domain.exception.InvalidArgumentException;
import com.health.doctor.domain.exception.NotFoundException;
import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.DoctorCredentials;
import com.health.doctor.domain.model.DoctorType;
import com.health.doctor.domain.model.Location;
import com.health.doctor.domain.ports.ClinicRepositoryPort;
import com.health.doctor.domain.ports.CredentialsRepositoryPort;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import com.health.doctor.infrastructure.JwtProvider;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.grpc.doctor.TokenResponse;
import com.health.doctor.application.service.LocationService;
import com.health.grpc.doctor.DoctorMessage;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
public class DoctorUseCase implements DoctorInterface {

    private final DoctorRepositoryPort repo;
    private final CredentialsRepositoryPort credentialsRepo;
    private final ClinicRepositoryPort clinicRepo;
    private final JwtProvider jwtProvider;
    private final LocationService locationService;

    public DoctorUseCase(DoctorRepositoryPort repo, CredentialsRepositoryPort credentialsRepo, ClinicRepositoryPort clinicRepo, JwtProvider jwtProvider, LocationService locationService) {
        this.repo = repo;
        this.credentialsRepo = credentialsRepo;
        this.clinicRepo = clinicRepo;
        this.jwtProvider = jwtProvider;
        this.locationService = locationService;
    }


    @Override
    public UUID createDoctor(String name, UUID clinicId, DoctorType type, String specialization, String email, String password) {

        if (name == null || name.isBlank())
            throw new InvalidArgumentException("Name is required");
        if (type == null)
            throw new InvalidArgumentException("Doctor type is required");
        if (email == null || email.isBlank())
            throw new InvalidArgumentException("Email is required");
        if (password == null || password.length() < 6)
            throw new InvalidArgumentException("Password must be at least 6 characters");
        if (specialization == null || specialization.isBlank())
            throw new InvalidArgumentException("Specialization is required");

        if (credentialsRepo.findByEmail(email).isPresent())
            throw new AlreadyExistsException("Doctor already exists with email: " + email);

        UUID doctorId = UUID.randomUUID();

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());

        Instant now = Instant.now(Clock.system(ZoneId.of("Asia/Kathmandu")));

        Doctor doctor = new Doctor(
                doctorId,
                name,
                clinicId,
                type,
                specialization,
                true,
                false,
                now,
                now
        );

        DoctorCredentials credentials = new DoctorCredentials(
                doctorId,
                email,
                passwordHash,
                Instant.now(),
                Instant.now()
        );

        credentialsRepo.save(credentials);
        repo.save(doctor);

        if(clinicId != null)
        {
            Location local = clinicRepo.getLocation(clinicId);
            repo.updateLocation(doctorId,local);
        }

        return doctorId;
    }

    @Override
    public TokenResponse loginDoctor(String email, String password) {

        if(email==null || email.isBlank())
            throw new InvalidArgumentException("Email is Required");
        if (password == null || password.isBlank())
            throw new InvalidArgumentException("Password is Required");

        DoctorCredentials credentials = credentialsRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + email));

        if (!BCrypt.checkpw(password, credentials.getPasswordHash()))
            throw new InvalidArgumentException("Invalid credentials");

        Doctor doctor = repo.findById(credentials.getDoctorId())
                .orElseThrow(() -> new NotFoundException("Doctor profile not found"));

        String accessToken = jwtProvider.generateAccessToken(
                doctor.getId().toString(), "Doctor");
        String refreshToken = jwtProvider.generateRefreshToken(
                doctor.getId().toString());
        log.info("Doctor logged in: {} ({})", doctor.getName(), doctor.getId());

        return TokenResponse.newBuilder()
                .setSuccess(true)
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setDoctor(DoctorMessage.newBuilder()
                        .setDoctorId(doctor.getId().toString())
                        .setName(doctor.getName())
                        .setType(com.health.grpc.doctor.DoctorType.valueOf(doctor.getType().name()))
                        .setSpecialization(doctor.getSpecialization())
                        .setIsActive(doctor.isActive())
                        .setClinicId(doctor.getClinicId() != null ? doctor.getClinicId().toString() : "None")
                )
                .setMessage("Login Successfully")
                .build();
    }

    @Override
    public ValidateTokenResponse validateDoctor(String token) {
        try{
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
    public List<Doctor> getDoctorsByClinic(UUID clinicId) {
        if (clinicId == null) throw new InvalidArgumentException("Clinic ID is required");
        return repo.findByClinicId(clinicId);
    }

    @Override
    public List<Doctor> getDoctorsByLocationText(String locationText) {
        if (locationText == null || locationText.isBlank()) 
            throw new InvalidArgumentException("Location text is required");
            
        Location location = locationService.resolve(locationText);
        String geohash = location.getGeohash().substring(0,5);
        log.info("Searching geohash: {}", geohash);
        return repo.findNearby(geohash, location.getLatitude(), location.getLongitude());
    }

    @Override
    public List<Doctor> getDoctorsByLocationGeohash(String geohashPrefix) {
        if (geohashPrefix == null || geohashPrefix.isBlank())
            throw new InvalidArgumentException("Geohash prefix is required");
        return repo.findByGeohashPrefix(geohashPrefix);
    }

    @Override
    public void updateDoctorLocation(UUID doctorId, String locationText) {
        if (doctorId == null) throw new InvalidArgumentException("Doctor ID is required");
        if (locationText == null || locationText.isBlank()) throw new InvalidArgumentException("Location text is required");

        UUID clinicId = repo.findClinicId(doctorId);

        if(clinicId !=null)
            throw new AlreadyExistsException("Clinic Doctor's location can only be handled by Clinics");

        Location location = locationService.resolve(locationText);
        repo.updateLocation(doctorId, location);
    }

    @Override
    public void updateDoctor(UUID doctorId, String email, String password) {
        if (doctorId == null) throw new InvalidArgumentException("Doctor ID is required");
        if (email == null || email.isBlank()) throw new InvalidArgumentException("Email is required");
        if (password == null || password.isBlank()) throw new InvalidArgumentException("Password is required");

        repo.updateDoctor(doctorId, email, password);
    }

    @Override
    public void deleteDoctor(UUID doctorId, String email, String password) {
        if (doctorId == null) throw new InvalidArgumentException("Doctor ID is required");
        if (email == null || email.isBlank()) throw new InvalidArgumentException("Email is required");
        if (password == null || password.isBlank()) throw new InvalidArgumentException("Password is required");

        repo.deleteDoctor(doctorId, email, password);
    }

}

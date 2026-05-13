package com.health.doctor.application.usecase.implementation;

import com.health.common.auth.JwtAuthInterceptor;
import com.health.common.auth.JwtProvider;
import com.health.common.exception.AlreadyExistsException;
import com.health.common.exception.InvalidArgumentException;
import com.health.common.exception.NotFoundException;
import com.health.common.redis.RedisUtil;
import com.health.common.utils.SecurityUtils;
import com.health.common.utils.ValidationUtil;
import com.health.doctor.adapters.output.nats.DoctorNatsClient;
import com.health.doctor.application.service.LocationService;
import com.health.doctor.application.usecase.interfaces.ClinicInterface;
import com.health.doctor.domain.model.Clinic;
import com.health.doctor.domain.model.ClinicCredentials;
import com.health.doctor.domain.model.Location;
import com.health.doctor.domain.ports.ClinicRepositoryPort;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.clinic.GetClinicStatsResponse;
import io.micronaut.validation.Validated;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
@Validated
public class ClinicUseCase implements ClinicInterface {
    private final ClinicRepositoryPort repo;
    private final LocationService locationService;
    private final JwtProvider jwtProvider;
    private final RedisUtil redisUtil;
    private final DoctorRepositoryPort doctorRepo;
    private final DoctorNatsClient natsClient;

    public ClinicUseCase(ClinicRepositoryPort repo, 
                         LocationService locationService, 
                         JwtProvider jwtProvider,
                         RedisUtil redisUtil,
                         DoctorRepositoryPort doctorRepo,
                         DoctorNatsClient natsClient) {
        this.repo = repo;
        this.locationService = locationService;
        this.jwtProvider = jwtProvider;
        this.redisUtil = redisUtil;
        this.doctorRepo = doctorRepo;
        this.natsClient = natsClient;
    }

    @Override
    public TokenResponse loginClinic(String email, String password) {
        ClinicCredentials creds = repo.findCredentialsByEmail(email)
                .orElseThrow(() -> new NotFoundException("Clinic not found: " + email));

        if (!BCrypt.checkpw(password, creds.getPasswordHash()))
            throw new InvalidArgumentException("Invalid credentials");

        String accessToken = jwtProvider.generateAccessToken(creds.getClinicId().toString(), "Clinic");
        String refreshToken = jwtProvider.generateRefreshToken(creds.getClinicId().toString());

        return TokenResponse.newBuilder()
                .setSuccess(true)
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setMessage("Login successful")
                .build();
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
        UUID clinicId = UUID.fromString(userId);
        
        repo.findById(clinicId)
                .orElseThrow(() -> new NotFoundException("Clinic not found"));

        String accessToken = jwtProvider.generateAccessToken(clinicId.toString(), "Clinic");
        String newRefreshToken = jwtProvider.generateRefreshToken(clinicId.toString());

        return TokenResponse.newBuilder()
                .setSuccess(true)
                .setAccessToken(accessToken)
                .setRefreshToken(newRefreshToken)
                .setMessage("Token refreshed successfully")
                .build();
    }

    @Override
    public GetClinicStatsResponse getClinicStats(@NotNull UUID clinicId) {
        // Ownership check
        SecurityUtils.validateOwnership(clinicId);

        String clinicApptKey = "stats:clinic:" + clinicId + ":appointments";
        String clinicPatientSetKey = "stats:clinic:" + clinicId + ":patients";

        long appts = getLong(clinicApptKey);
        long patients = redisUtil.scard(clinicPatientSetKey);
        long doctors = doctorRepo.findByClinicId(clinicId).size();

        return GetClinicStatsResponse.newBuilder()
                .setTotalDoctors(doctors)
                .setTotalPatients(patients)
                .setTotalAppointments(appts)
                .setMessage("Stats retrieved successfully")
                .build();
    }

    private long getLong(String key) {
        String val = redisUtil.get(key, String.class);
        return val != null ? Long.parseLong(val) : 0L;
    }

    @Override
    public UUID createClinic(String name, String locationText, String email, String password, String phone) {
        if (name == null || locationText == null)
            throw new RuntimeException("Name or location is required");

        List<Clinic> existing = repo.findByLocationText(locationText);
        if (existing != null && !existing.isEmpty()) {
             for (Clinic c : existing) {
                 if (name.equalsIgnoreCase(c.getName())) {
                     throw new RuntimeException("Clinic already exists at this location with this name");
                 }
             }
        }

        if (email != null && repo.findCredentialsByEmail(email).isPresent()) {
            throw new AlreadyExistsException("Clinic already exists with email: " + email);
        }
        ValidationUtil.validateEmail(email);
        ValidationUtil.validatePassword(password);

        Location location = locationService.resolve(locationText);
        UUID clinicId = UUID.randomUUID();

        Clinic clinic = new Clinic(clinicId, name, location, true);
        repo.save(clinic);

        if (email != null && password != null) {
            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
            ClinicCredentials creds = new ClinicCredentials(clinicId, email, passwordHash, Instant.now(), Instant.now());
            repo.saveCredentials(creds);
        }

        log.info("Sending NATS event clinic.created for ID: {}", clinicId);
        natsClient.sendClinicCreated(clinicId.toString());
        log.info("NATS event clinic.created initiation complete for ID: {}", clinicId);

        return clinicId;
    }

    @Override
    public List<Clinic> getClinicsByLocation(@NotBlank String location) {
        List<Clinic> found;

        if (isGeohash(location)) {
            // Direct geohash search
            found = repo.findByLocationGeohash(location);
        } else {
            // Text resolution
            Location resolved = locationService.resolve(location);

            // Check DB for exact match of resolved canonical name
            found = repo.findByLocationText(resolved.getLocationText());

            // If not found, fallback to geohash zoom-out logic
            if (found == null || found.isEmpty()) {
                String geohash = resolved.getGeohash().substring(0, Math.min(resolved.getGeohash().length(), 6));
                log.info("Searching clinics near geohash={} lat={} lon={}",
                        geohash, resolved.getLatitude(), resolved.getLongitude());
                found = repo.findNearby(geohash, resolved.getLatitude(), resolved.getLongitude());
            }
        }

        return found;
    }

    private boolean isGeohash(String input) {
        return input != null && input.matches("^[a-z0-9]{4,12}$") && !input.contains(" ");
    }

    @Override
    public List<Clinic> searchClinics(String name, int page, int size) {
        if (name == null || name.isBlank())
            throw new InvalidArgumentException("Search name is required");
        if (page < 0)
            throw new InvalidArgumentException("Page must be >= 0");
        if (size <= 0)
            throw new InvalidArgumentException("Size must be > 0");
        return repo.searchByName(name, page, size);
    }

    @Override
    public long countClinicsByName(String name) {
        if (name == null || name.isBlank())
            throw new InvalidArgumentException("Search name is required");
        return repo.countByName(name);
    }

    @Override
    public String forgotPassword(String email) {
        ClinicCredentials credentials = repo.findCredentialsByEmail(email)
                .orElseThrow(() -> new NotFoundException("Clinic not found: " + email));

        return jwtProvider.generateResetToken(credentials.getClinicId().toString(), "Clinic");
    }

    @Override
    public void resetPassword(String newPassword) {
        String userId = JwtAuthInterceptor.USER_ID_KEY.get();
        String role = JwtAuthInterceptor.ROLE_KEY.get();
        String type = JwtAuthInterceptor.TOKEN_TYPE_KEY.get();

        if (userId == null || role == null || type == null) {
            throw new InvalidArgumentException("Authentication context missing");
        }

        if (!"reset".equals(type)) {
            throw new InvalidArgumentException("Invalid token type: reset token required");
        }

        if (!"Clinic".equalsIgnoreCase(role)) {
            throw new InvalidArgumentException("Token is not for a Doctor");
        }

        String passwordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        repo.updatePassword(UUID.fromString(userId),passwordHash);
        log.info("Password reset successful for clinic: {}", userId);
    }

    @Override
    public void updateClinic(UUID clinicId, String name) {
        Clinic clinic = repo.findById(clinicId)
                .orElseThrow(() -> new NotFoundException("Clinic not found: " + clinicId));
        clinic.setName(name);
        repo.update(clinic);
        natsClient.sendClinicUpdated(clinicId.toString());
    }

    @Override
    public void deleteClinic(UUID clinicId) {
        repo.delete(clinicId);
        natsClient.sendClinicDeleted(clinicId.toString());
    }

    @Override
    public void updateClinicLocation(UUID clinicId, String locationText) {
        repo.findById(clinicId)
                .orElseThrow(() -> new NotFoundException("Clinic not found: " + clinicId));
        Location location = locationService.resolve(locationText);
        repo.updateLocation(clinicId, location);
        natsClient.sendClinicUpdated(clinicId.toString());
    }

    @Override
    public Clinic getClinicById(UUID clinicId) {
        return repo.findById(clinicId)
                .orElseThrow(() -> new NotFoundException("Clinic not found: " + clinicId));
    }

    @Override
    public List<Clinic> findByName(String name) {
        return List.of(repo.findByName(name));
    }
}

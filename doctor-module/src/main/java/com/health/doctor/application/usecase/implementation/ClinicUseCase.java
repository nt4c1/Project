package com.health.doctor.application.usecase.implementation;

import com.health.common.auth.GrpcAuthInterceptor;
import com.health.common.utils.ValidationUtil;
import com.health.doctor.application.service.LocationService;
import com.health.doctor.application.usecase.interfaces.ClinicInterface;
import com.health.common.auth.JwtProvider;
import com.health.common.exception.AlreadyExistsException;
import com.health.common.exception.InvalidArgumentException;
import com.health.common.exception.NotFoundException;
import com.health.doctor.domain.model.Clinic;
import com.health.doctor.domain.model.ClinicCredentials;
import com.health.doctor.domain.model.Location;
import com.health.doctor.domain.ports.ClinicRepositoryPort;
import com.health.grpc.auth.TokenResponse;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
public class ClinicUseCase implements ClinicInterface {
    private final ClinicRepositoryPort repo;
    private final LocationService locationService;
    private final JwtProvider jwtProvider;

    public ClinicUseCase(ClinicRepositoryPort repo, LocationService locationService, JwtProvider jwtProvider) {
        this.repo = repo;
        this.locationService = locationService;
        this.jwtProvider = jwtProvider;
    }

    @Override
    public UUID createClinic(String name, String locationText, String email, String password, String phone) {
        if (name == null || locationText == null)
            throw new RuntimeException("Name or location is required");

        Clinic existing = repo.findByLocationText(locationText);
        if (existing != null && locationText.equals(existing.getLocationText()))
            throw new RuntimeException("Clinic already exists");

        if (email != null && repo.findCredentialsByEmail(email).isPresent()) {
            throw new AlreadyExistsException("Clinic already exists with email: " + email);
        }
        ValidationUtil.validateEmail(email);
        ValidationUtil.validatePassword(password);
        ValidationUtil.validatePhone(phone);

        Location location = locationService.resolve(locationText);
        UUID clinicId = UUID.randomUUID();

        Clinic clinic = new Clinic(clinicId, name, location, true);
        repo.save(clinic);

        if (email != null && password != null) {
            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
            ClinicCredentials creds = new ClinicCredentials(clinicId, email, passwordHash, Instant.now(), Instant.now());
            repo.saveCredentials(creds);
        }

        return clinicId;
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
    public List<Clinic> getClinicsByLocationText(String locationText) {
        Clinic clinic = repo.findByLocationText(locationText);
        return clinic != null ? List.of(clinic) : List.of();
    }

    @Override
    public List<Clinic> getClinicsByLocationGeohash(String geohashPrefix) {
        Clinic clinic = repo.findByLocationGeohash(geohashPrefix);
        return clinic != null ? List.of(clinic) : List.of();
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

    @Override
    public List<Clinic> getNearbyClinicsByLocationText(String locationText) {
        if (locationText == null || locationText.isBlank())
            throw new InvalidArgumentException("Location text is required");

        Location location = locationService.resolve(locationText);
        String geohash = location.getGeohash().substring(0, 5);

        log.info("Searching clinics near geohash={} lat={} lon={}",
                geohash, location.getLatitude(), location.getLongitude());

        return repo.findNearby(geohash, location.getLatitude(), location.getLongitude());
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
        String userId = GrpcAuthInterceptor.USER_ID_KEY.get();
        String role = GrpcAuthInterceptor.ROLE_KEY.get();
        String type = GrpcAuthInterceptor.TOKEN_TYPE_KEY.get();

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
    }

    @Override
    public void deleteClinic(UUID clinicId) {
        repo.delete(clinicId);
    }

    @Override
    public void updateClinicLocation(UUID clinicId, String locationText) {
        repo.findById(clinicId)
                .orElseThrow(() -> new NotFoundException("Clinic not found: " + clinicId));
        Location location = locationService.resolve(locationText);
        repo.updateLocation(clinicId, location);
    }

}
package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.usecase.interfaces.DoctorInterface;
import com.health.doctor.domain.exception.AlreadyExistsException;
import com.health.doctor.domain.exception.InvalidArgumentException;
import com.health.doctor.domain.exception.NotFoundException;
import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.DoctorType;
import com.health.doctor.domain.model.Location;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import com.health.doctor.infrastructure.JwtProvider;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.grpc.doctor.DoctorLoginResponse;
import com.health.doctor.application.service.LocationService;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
public class DoctorUseCase implements DoctorInterface {

    private final DoctorRepositoryPort repo;
    private final JwtProvider jwtProvider;
    private final LocationService locationService;

    public DoctorUseCase(DoctorRepositoryPort repo, JwtProvider jwtProvider, LocationService locationService) {
        this.repo = repo;
        this.jwtProvider = jwtProvider;
        this.locationService = locationService;
    }


    @Override 
    public UUID createDoctor(String name, UUID clinicId, DoctorType type, String specialization, String email, String password) {

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

    @Override
    public DoctorLoginResponse loginDoctor(String email, String password) {

        if(email==null || email.isBlank())
            throw new InvalidArgumentException("Email is Required");
        if (password == null || password.isBlank())
            throw new InvalidArgumentException("Password is Required");

        Doctor doctor = repo.findByEmail(email)
                .orElseThrow(()-> new NotFoundException("Doctor not found"+email));
        //PasswordCheck baki with Bcrypt
        if (!BCrypt.checkpw(password, doctor.getPasswordHash()))
            throw new InvalidArgumentException("Invalid credentials");

        String accessToken = jwtProvider.generateAccessToken(
                doctor.getId().toString(),"Doctor");
        String refreshToken = jwtProvider.generateRefreshToken(
                doctor.getId().toString());
        log.info("Doctor logged in : {}{}",doctor.getId(),doctor.getName());

        return DoctorLoginResponse.newBuilder()
                .setSuccess(true)
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setDoctorId(doctor.getId().toString())
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
                    .setMessage(valid ? "Token Valid " : "Token Invalid" )
                    .build();
        } catch (Exception e) {
            log.warn("Token Validation Failed:{}",e.getMessage());
            return ValidateTokenResponse.newBuilder()
                    .setValid(false)
                    .setMessage("Token Invalid"+e.getMessage())
                    .build();
        }
    }

    @Override
    public List<Doctor> getDoctorsByClinic(UUID clinicId) {
        return repo.findByClinicId(clinicId);
    }

    @Override
    public List<Doctor> getDoctorsByLocationText(String locationText) {
        Location location = locationService.resolve(locationText);
        String geohash = location.getGeohash().substring(0,5);
        log.info("Searching geohash: {}", geohash);
        log.info("Neighbors will also be searched");
        return repo.findNearby(geohash);
    }

    @Override
    public List<Doctor> getDoctorsByLocationGeohash(String geohashPrefix) {
        return repo.findByGeohashPrefix(geohashPrefix);
    }

    @Override
    public void UpdateDoctorLocation(UUID doctorId, String text) {
        Location location = locationService.resolve(text);
        repo.updateLocation(doctorId,location);
    }


}
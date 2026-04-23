package com.health.doctor.application.usecase.implementation;

import com.health.common.auth.GrpcAuthInterceptor;
import com.health.common.auth.JwtProvider;
import com.health.common.exception.AlreadyExistsException;
import com.health.common.exception.NotFoundException;
import com.health.common.utils.ValidationUtil;
import com.health.common.exception.InvalidArgumentException;
import com.health.doctor.application.service.LocationService;
import com.health.doctor.application.usecase.interfaces.DoctorInterface;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.ports.*;
import com.health.doctor.adapters.output.nats.DoctorNatsClient;
import com.health.doctor.adapters.output.persistence.repository.DoctorRepositoryImpl;
import com.health.doctor.mapper.MapperClass;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.grpc.doctor.DoctorActiveResponse;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import io.micronaut.validation.Validated;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@Validated
public class DoctorUseCase implements DoctorInterface {

    private final DoctorRepositoryPort repo;
    private final CredentialsRepositoryPort credentialsRepo;
    private final ClinicRepositoryPort clinicRepo;
    private final ScheduleRepositoryPort scheduleRepo;
    private final JwtProvider jwtProvider;
    private final LocationService locationService;
    private final DoctorNatsClient natsClient;
    private final ValidationUtil validationUtil;

    public DoctorUseCase(DoctorRepositoryPort repo,
                         CredentialsRepositoryPort credentialsRepo,
                         ClinicRepositoryPort clinicRepo,
                         ScheduleRepositoryPort scheduleRepo,
                         JwtProvider jwtProvider,
                         LocationService locationService,
                         DoctorNatsClient natsClient, ValidationUtil validationUtil) {
        this.repo = repo;
        this.credentialsRepo = credentialsRepo;
        this.clinicRepo = clinicRepo;
        this.scheduleRepo = scheduleRepo;
        this.jwtProvider = jwtProvider;
        this.locationService = locationService;
        this.natsClient = natsClient;
        this.validationUtil = validationUtil;
    }

    @Override
    public UUID createDoctor(@NotBlank String name,
                             List<UUID> clinicIds,
                             @NotNull DoctorType type,
                             @NotBlank String specialization,
                             @NotBlank  String email,
                             @NotBlank  String password,
                             @NotBlank String phone) {

        if (credentialsRepo.findByEmail(email).isPresent())
            throw new AlreadyExistsException("Doctor already exists with email: " + email);

        ValidationUtil.validateEmail(email);
        ValidationUtil.validatePhone(phone);
        ValidationUtil.validatePassword(password);


        UUID doctorId = UUID.randomUUID();
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        Instant now = Instant.now(Clock.system(ZoneId.of("Asia/Kathmandu")));

        Doctor doctor = new Doctor(doctorId, name, clinicIds, type, specialization, phone, true, false, now, now);
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

        return TokenResponse.newBuilder()
                .setSuccess(true)
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setDoctor(MapperClass.toMsg(doctor))
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

        enrichDoctorsWithScheduleStatus(found);
        return found;
    }

    @Override
    public List<Doctor> getDoctorsByLocationGeohash(@NotBlank String geohashPrefix) {
        List<Doctor> found = repo.findByGeohashPrefix(geohashPrefix);
        enrichDoctorsWithScheduleStatus(found);
        return found;
    }

    private void enrichDoctorsWithScheduleStatus(List<Doctor> doctors) {
        if (doctors == null || doctors.isEmpty()) return;

        List<UUID> doctorIds = doctors.stream().map(Doctor::getId).collect(Collectors.toList());
        Map<UUID, DoctorSchedule> scheduleMap = scheduleRepo.findByDoctors(doctorIds).stream()
                .collect(Collectors.toMap(DoctorSchedule::getDoctorId, s -> s));

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kathmandu"));
        DayOfWeek currentDay = now.getDayOfWeek();
        LocalTime currentTime = now.toLocalTime();

        for (Doctor doctor : doctors) {
            DoctorSchedule schedule = scheduleMap.get(doctor.getId());

            if (schedule != null) {
                boolean isWorkingDay = schedule.getWorkingDays().contains(currentDay);
                boolean isWithinHours = !currentTime.isBefore(schedule.getStartTime()) && !currentTime.isAfter(schedule.getEndTime());

                if (isWorkingDay && isWithinHours) {
                    doctor.setActive(true);
                    doctor.setNextPossibleDate("Available Today");
                } else {
                    doctor.setActive(false);
                    doctor.setNextPossibleDate(calculateNextPossibleDate(schedule, now));
                }
            } else {
                log.warn("No schedule found for doctor {}", doctor.getId());
                doctor.setActive(false);
                doctor.setNextPossibleDate("Doctor hasn't made schedule");
            }
        }
    }

    private String calculateNextPossibleDate(DoctorSchedule schedule, ZonedDateTime now) {
        if (schedule.getWorkingDays() == null || schedule.getWorkingDays().isEmpty()) {
            return "No upcoming schedule";
        }

        ZonedDateTime next = now;
        // Check next 7 days
        for (int i = 0; i < 7; i++) {
            if (i > 0 || now.toLocalTime().isBefore(schedule.getStartTime())) {
                if (schedule.getWorkingDays().contains(next.getDayOfWeek())) {
                    return next.toLocalDate().toString() + " at " + schedule.getStartTime().toString();
                }
            }
            next = next.plusDays(1);
        }
        return "No upcoming schedule within 7 days";
    }

    @Override
    public void updateDoctorLocation(@NotNull UUID doctorId,UUID clinicId, @NotBlank String locationText) {
        Location location = locationService.resolve(locationText);
        repo.updateLocation(doctorId, clinicId, location);
        natsClient.sendDoctorUpdated(doctorId.toString());
    }

    @Override
    public void updateDoctor(@NotNull UUID doctorId, @NotBlank @Email String email, @NotBlank @Size(min = 6) String password, @NotBlank String phone) {
        repo.updateDoctor(doctorId, email, password, phone);
        natsClient.sendDoctorUpdated(doctorId.toString());
    }

    @Override
    public void deleteDoctor(@NotNull UUID doctorId, @NotBlank @Email String email, @NotBlank @Size(min = 6) String password) {
        repo.deleteDoctor(doctorId, email, password);
        natsClient.sendDoctorUpdated(doctorId.toString());
    }

    @Override
    public String forgotPassword(@NotBlank @Email String email) {
        DoctorCredentials credentials = credentialsRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + email));
        
        return jwtProvider.generateResetToken(credentials.getDoctorId().toString(), "Doctor");
    }

    @Override
    public void resetPassword(@NotBlank @Size(min = 6) String newPassword) {
        String userId = GrpcAuthInterceptor.USER_ID_KEY.get();
        String role = GrpcAuthInterceptor.ROLE_KEY.get();
        String type = GrpcAuthInterceptor.TOKEN_TYPE_KEY.get();

        if (userId == null || role == null || type == null) {
            throw new InvalidArgumentException("Authentication context missing");
        }

        if (!"reset".equals(type)) {
            throw new InvalidArgumentException("Invalid token type: reset token required");
        }

        if (!"Doctor".equalsIgnoreCase(role)) {
            throw new InvalidArgumentException("Token is not for a Doctor");
        }

        String passwordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        credentialsRepo.updatePassword(UUID.fromString(userId), passwordHash);
        log.info("Password reset successful for doctor: {}", userId);
    }

    @Override
    public DoctorActiveResponse isDoctorActive(@NotNull UUID doctorId, UUID clinicId) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kathmandu"));
        DayOfWeek currentDay = now.getDayOfWeek();

        Optional<DoctorSchedule> scheduleOpt;
        if (clinicId != null && !DoctorRepositoryImpl.NO_CLINIC_ID.equals(clinicId)) {
            scheduleOpt = scheduleRepo.findByDoctorAndClinic(doctorId, clinicId);
        } else {
            scheduleOpt = scheduleRepo.findByDoctors(List.of(doctorId)).stream().findFirst();
        }

        if (scheduleOpt.isPresent()) {
            DoctorSchedule schedule = scheduleOpt.get();
            boolean isActive = schedule.getWorkingDays().contains(currentDay);
            return DoctorActiveResponse.newBuilder()
                    .setIsActive(isActive)
                    .setMessage(isActive ? "Doctor is active today (" + currentDay + ")" : "Doctor is not active today (" + currentDay + ")")
                    .build();
        } else {
            return DoctorActiveResponse.newBuilder()
                    .setIsActive(false)
                    .setMessage("no schedule is for this doctor")
                    .build();
        }
    }
}
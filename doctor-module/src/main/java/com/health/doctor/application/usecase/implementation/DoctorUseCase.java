package com.health.doctor.application.usecase.implementation;

import com.health.common.auth.JwtAuthInterceptor;
import com.health.common.auth.JwtProvider;
import com.health.common.exception.AlreadyExistsException;
import com.health.common.exception.BookingException;
import com.health.common.exception.InvalidArgumentException;
import com.health.common.exception.NotFoundException;
import com.health.common.exception.UnauthorizedException;
import com.health.common.redis.RedisUtil;
import com.health.common.utils.DateTimeUtils;
import com.health.common.utils.SecurityUtils;
import com.health.common.utils.ValidationUtil;
import com.health.doctor.adapters.output.nats.DoctorNatsClient;
import com.health.doctor.adapters.output.persistence.repository.DoctorRepositoryImpl;
import com.health.doctor.application.service.LocationService;
import com.health.doctor.application.usecase.interfaces.DoctorInterface;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.ports.*;
import com.health.doctor.mapper.MapperClass;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.grpc.doctor.DoctorActiveResponse;
import io.micronaut.validation.Validated;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@Validated
public class DoctorUseCase implements DoctorInterface {

    private final DoctorRepositoryPort repo;
    private final CredentialsRepositoryPort credentialsRepo;
    private final ClinicRepositoryPort clinicRepo;
    private final ScheduleRepositoryPort scheduleRepo;
    private final AppointmentRepositoryPort appointmentRepo;
    private final JwtProvider jwtProvider;
    private final LocationService locationService;
    private final DoctorNatsClient natsClient;
    private final RedisUtil redisUtil;
    private final ReviewRepositoryPort reviewRepo;
    private final PatientLookUpPort patientLookUp;
    private static final String CACHE_LOCATION_PREFIX = "doctor-location:";
    private static final long TTL_LOCATION = 600; // 10m


    public DoctorUseCase(DoctorRepositoryPort repo,
                         CredentialsRepositoryPort credentialsRepo,
                         ClinicRepositoryPort clinicRepo,
                         ScheduleRepositoryPort scheduleRepo,
                         AppointmentRepositoryPort appointmentRepo,
                         JwtProvider jwtProvider,
                         LocationService locationService,
                         DoctorNatsClient natsClient, 
                         RedisUtil redisUtil,
                         ReviewRepositoryPort reviewRepo,
                         PatientLookUpPort patientLookUp) {
        this.repo = repo;
        this.credentialsRepo = credentialsRepo;
        this.clinicRepo = clinicRepo;
        this.scheduleRepo = scheduleRepo;
        this.appointmentRepo = appointmentRepo;
        this.jwtProvider = jwtProvider;
        this.locationService = locationService;
        this.natsClient = natsClient;
        this.redisUtil = redisUtil;
        this.reviewRepo = reviewRepo;
        this.patientLookUp = patientLookUp;
    }

    @Override
    public UUID createDoctor(@NotBlank String name,
                             List<UUID> clinicIds,
                             @NotNull DoctorType type,
                             @NotBlank String specialization,
                             @NotBlank  String email,
                             @NotBlank  String password,
                             @NotBlank String phone) {

        Optional<DoctorCredentials> existingCreds = credentialsRepo.findByEmail(email);
        if (existingCreds.isPresent()) {
            UUID existingId = existingCreds.get().getDoctorId();
            if (repo.isDeleted(existingId)) {
                log.info("Reactivating soft-deleted doctor: {}", existingId);
                String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
                Instant now = DateTimeUtils.now().toInstant();
                
                Doctor updatedDoctor = new Doctor(existingId, name, clinicIds, type, specialization, phone, true, false, now, now);
                repo.reactivate(updatedDoctor);
                
                credentialsRepo.updatePassword(existingId, passwordHash);
                
                if (clinicIds != null && !clinicIds.isEmpty()) {
                    for (UUID clinicId : clinicIds) {
                        Location local = clinicRepo.getLocation(clinicId);
                        if (local != null) {
                            repo.updateLocation(existingId, clinicId, local);
                        }
                    }
                }
                log.info("Sending NATS event doctor.created (reactivation) for ID: {}", existingId);
                natsClient.sendDoctorCreated(existingId.toString());
                log.info("NATS event doctor.created (reactivation) initiation complete for ID: {}", existingId);
                return existingId;
            }
            throw new AlreadyExistsException("Doctor already exists with email: " + email);
        }
        ValidationUtil.validateEmail(email);
        ValidationUtil.validatePhone(phone);
        ValidationUtil.validatePassword(password);


        UUID doctorId = UUID.randomUUID();
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        Instant now = DateTimeUtils.now().toInstant();

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
        log.info("Sending NATS event doctor.created for ID: {}", doctorId);
        natsClient.sendDoctorCreated(doctorId.toString());
        log.info("NATS event doctor.created initiation complete for ID: {}", doctorId);
        return doctorId;
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
        Doctor doctor = repo.findById(UUID.fromString(userId))
                .orElseThrow(() -> new NotFoundException("Doctor not found"));

        if (repo.isDeleted(doctor.getId())) {
            throw new NotFoundException("Doctor not found");
        }

        String accessToken = jwtProvider.generateAccessToken(doctor.getId().toString(), "Doctor");
        String newRefreshToken = jwtProvider.generateRefreshToken(doctor.getId().toString());

        return TokenResponse.newBuilder()
                .setSuccess(true)
                .setAccessToken(accessToken)
                .setRefreshToken(newRefreshToken)
                .setDoctor(MapperClass.toMsg(doctor))
                .setMessage("Token refreshed successfully")
                .build();
    }

    @Override
    public TokenResponse loginDoctor(@NotBlank String email, @NotBlank String password) {
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
    public Optional<Doctor> getDoctor(@NotNull UUID doctorId) {
        return repo.findById(doctorId);
    }

    @Override
    public List<Doctor> getDoctorsByClinic(@NotNull UUID clinicId) {
        return repo.findByClinicId(clinicId);
    }

    @Override
    public List<Doctor> getDoctorsByLocation(@NotBlank String location, com.health.grpc.common.AvailabilityFilter filter) {
        // Resolve text to coordinates and canonical location info
        Location resolved = locationService.resolve(location);
        String geohash = resolved.getGeohash();
        String prefix = geohash.substring(0, Math.min(geohash.length(), 6));//TODO safety check is it required
        
        // Try Geohash Cache first
        String geoCacheKey = CACHE_LOCATION_PREFIX + "nb:" + prefix;
        List<Doctor> found = null;
        try {
            found = redisUtil.get(geoCacheKey, new com.fasterxml.jackson.core.type.TypeReference<List<Doctor>>() {});//TODO other Options to TypeReference
            if (found != null) {
                log.info("Redis cache hit for Nearby (Geohash): {}", prefix);
            }
        } catch (Exception e) {
            log.warn("Redis GET failed for Geohash cache: {}", e.getMessage());
        }

        // Database Search if not in cache
        if (found == null) { //TODO if possible trim the api to English and then check
            found = repo.findByLocationText(resolved.getLocationText());
            log.info("Doctors found by Location_Text: {} (count: {})", resolved.getLocationText(), found != null ? found.size() : 0);

            if (found == null || found.isEmpty()) {
                found = repo.findNearby(geohash, resolved.getLatitude(), resolved.getLongitude());
                log.info("Doctors found by geohash zoom-out (count: {})", found != null ? found.size() : 0);
            }

            if (found != null && !found.isEmpty()) {
                redisUtil.setAsync(geoCacheKey, found, TTL_LOCATION);
            }
        }

        // Enrich with current schedule status
        enrichDoctorsWithScheduleStatus(found);
        enrichDoctorsWithRatings(found);

        // Apply Instant Availability Filters
        if (found != null && filter != null && filter != com.health.grpc.common.AvailabilityFilter.AVAILABILITY_FILTER_UNSPECIFIED) {
            found = applyAvailabilityFilter(found, filter);
        }

        return found;
    }

    private void enrichDoctorsWithRatings(List<Doctor> doctors) {
        if (doctors == null || doctors.isEmpty()) return;
        for (Doctor d : doctors) {
            ReviewRepositoryPort.RatingSummary summary = reviewRepo.getRatingSummary(d.getId());
            d.setAverageRating(summary.averageRating());
            d.setReviewCount(summary.reviewCount());
        }
    }

    @Override
    public void addReview(@NotNull UUID doctorId, @NotNull UUID patientId, int rating, String comment) {
        if (rating < 1 || rating > 10) throw new InvalidArgumentException("Rating must be between 1 and 10");

        List<Appointment> patientAppointments = appointmentRepo.findByPatient(patientId);
        boolean hasCompletedAppointment = patientAppointments.stream()
                .anyMatch(a -> doctorId.equals(a.getDoctorId()) && 
                               a.getStatus() == AppointmentStatus.APPOINTMENT_STATUS_COMPLETED);

        if (!hasCompletedAppointment) {
            throw new BookingException("You can only review a doctor after a completed appointment.");
        }

        String patientName = patientLookUp.findById(patientId)
                .map(p -> p.name())
                .orElse("Anonymous Patient");

        Review review = new Review(doctorId, patientId, patientName, rating, comment, Instant.now());
        reviewRepo.save(review);
        log.info("Review added for doctor {} by patient {}", doctorId, patientId);
    }

    @Override
    public List<Review> getDoctorReviews(@NotNull UUID doctorId) {
        return reviewRepo.findByDoctorId(doctorId);
    }

    @Override
    public double getAverageRating(@NotNull UUID doctorId) {
        return reviewRepo.getAverageRating(doctorId);
    }

    private List<Doctor> applyAvailabilityFilter(List<Doctor> doctors, com.health.grpc.common.AvailabilityFilter filter) {
        if (filter == com.health.grpc.common.AvailabilityFilter.AVAILABILITY_FILTER_NOW) {
            return doctors.stream()
                    .filter(Doctor::isActive)
                    .collect(Collectors.toList());
        } else if (filter == com.health.grpc.common.AvailabilityFilter.AVAILABILITY_FILTER_THIS_WEEKEND) {
            return doctors.stream()
                    .filter(d -> {
                        String next = d.getNextPossibleDate();
                        return next != null && (next.toLowerCase().contains("saturday") || next.toLowerCase().contains("sunday") || isWorkingThisWeekend(d.getId()));
                    })
                    .collect(Collectors.toList());
        }
        return doctors;
    }

    private boolean isWorkingThisWeekend(UUID doctorId) {
        return scheduleRepo.findByDoctors(List.of(doctorId)).stream()
                .anyMatch(s -> s.getWorkingDays().contains(DayOfWeek.SATURDAY) || s.getWorkingDays().contains(DayOfWeek.SUNDAY));
    }

    private void enrichDoctorsWithScheduleStatus(List<Doctor> doctors) {
        if (doctors == null || doctors.isEmpty()) return;

        List<UUID> doctorIds = doctors.stream().map(Doctor::getId).toList();
        Map<UUID, DoctorSchedule> scheduleMap = scheduleRepo.findByDoctors(doctorIds).stream()
                .collect(Collectors.toMap(DoctorSchedule::getDoctorId, s -> s, (s1, s2) -> s1));

        ZonedDateTime now = DateTimeUtils.now();
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
    public void updateDoctorLocation(@NotNull UUID doctorId, UUID clinicId, @NotBlank String locationText) {
        // Authorization check
        String role = SecurityUtils.getCurrentUserRole();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        if (clinicId != null) {
            // Clinic-based update: Only the clinic itself can perform this
            if (!"Clinic".equalsIgnoreCase(role) || !currentUserId.equals(clinicId)) {
                throw new UnauthorizedException("Only the clinic can update doctor location for its clinic ID.");
            }
            // Verify doctor belongs to this clinic
            Doctor doc = repo.findById(doctorId)
                    .orElseThrow(() -> new NotFoundException("Doctor not found: " + doctorId));
            if (doc.getClinicIds() == null || !doc.getClinicIds().contains(clinicId)) {
                throw new UnauthorizedException("Doctor does not belong to this clinic.");
            }
        } else {
            // Individual doctor update: Only the doctor themselves can perform this
            SecurityUtils.validateOwnership(doctorId);
        }

        Location location = locationService.resolve(locationText);
        repo.updateLocation(doctorId, clinicId, location);
        natsClient.sendDoctorUpdated(doctorId.toString());
    }

    @Override
    public void updateDoctor(@NotNull UUID doctorId, @NotBlank String email, @NotBlank String password, @NotBlank String phone) {
        // Ownership check
        SecurityUtils.validateOwnership(doctorId);

        repo.updateDoctor(doctorId, email, password, phone);
        natsClient.sendDoctorUpdated(doctorId.toString());
    }

    @Override
    public void addClinicToDoctor(@NotNull UUID doctorId, @NotNull List<UUID> clinicIds) {
        // Authorization check: Only Clinics (for themselves) or Admin can add doctors
        String role = SecurityUtils.getCurrentUserRole();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        if ("Clinic".equalsIgnoreCase(role)) {
            if (clinicIds.size() != 1 || !clinicIds.get(0).equals(currentUserId)) {
                throw new UnauthorizedException("Clinics can only add doctors to themselves.");
            }
        } else if (!"Admin".equalsIgnoreCase(role)) {
            throw new UnauthorizedException("Only Clinics or Admins can add doctors to clinics.");
        }

        if (clinicIds.isEmpty()) return;

        Doctor doctor = repo.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + doctorId));

        List<UUID> existingClinics = doctor.getClinicIds() != null ? doctor.getClinicIds() : Collections.emptyList();
        
        List<UUID> newClinicIds = clinicIds.stream()
                .filter(id -> !existingClinics.contains(id))
                .distinct()
                .toList();

        if (newClinicIds.isEmpty()) return;

        // Requirement: change type to CLINIC_DOCTOR and remove from individual practice
        if (doctor.getType() == DoctorType.DOCTOR_TYPE_INDIVIDUAL) {
            repo.updateType(doctorId, DoctorType.DOCTOR_TYPE_CLINIC_DOCTOR);
            repo.removeIndividualPractice(doctorId);
            doctor.setType(DoctorType.DOCTOR_TYPE_CLINIC_DOCTOR);
        }

        for (UUID clinicId : newClinicIds) {
            repo.addClinicId(doctorId, clinicId);
            Location local = clinicRepo.getLocation(clinicId);
            if (local != null) {
                repo.updateLocation(doctorId, clinicId, local);
            }
        }
        natsClient.sendDoctorUpdated(doctorId.toString());
    }

    @Override
    public void removeClinicFromDoctor(@NotNull UUID doctorId, @NotNull List<UUID> clinicIds) {
        // Authorization check: Only Clinics (for themselves) or Admin can remove doctors
        String role = SecurityUtils.getCurrentUserRole();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        if ("Clinic".equalsIgnoreCase(role)) {
            if (clinicIds.size() != 1 || !clinicIds.get(0).equals(currentUserId)) {
                throw new UnauthorizedException("Clinics can only remove doctors from themselves.");
            }
        } else if (!"Admin".equalsIgnoreCase(role)) {
            throw new UnauthorizedException("Only Clinics or Admins can remove doctors from clinics.");
        }

        if (clinicIds.isEmpty()) return;

        Doctor doctor = repo.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + doctorId));

        List<UUID> existingClinics = doctor.getClinicIds() != null ? doctor.getClinicIds() : Collections.emptyList();

        List<UUID> toRemove = clinicIds.stream()
                .filter(existingClinics::contains)
                .distinct()
                .toList();

        if (toRemove.isEmpty()) return;

        // Check for active appointments (Accepted or Postponed) at these clinics
        List<Appointment> allAppointments = appointmentRepo.findByDoctor(doctorId);
        
        for (UUID clinicId : toRemove) {
            boolean hasActive = allAppointments.stream()
                    .filter(a -> clinicId.equals(a.getClinicId()))
                    .anyMatch(a -> a.getStatus() == AppointmentStatus.APPOINTMENT_STATUS_ACCEPTED || 
                                   a.getStatus() == AppointmentStatus.APPOINTMENT_STATUS_POSTPONED);
            
            if (hasActive) {
                throw new com.health.common.exception.BookingException(
                        "Cannot remove clinic " + clinicId + " because there are Accepted or Postponed appointments. " +
                        "Please complete or cancel them first.");
            }
        }

        for (UUID clinicId : toRemove) {
            repo.removeClinicId(doctorId, clinicId);
        }

        // vice versa: if no more clinics, change back to individual
        Doctor updatedDoc = repo.findById(doctorId).orElse(null);
        if (updatedDoc != null && (updatedDoc.getClinicIds() == null || updatedDoc.getClinicIds().isEmpty())) {
            updatedDoc.setType(DoctorType.DOCTOR_TYPE_INDIVIDUAL);
            repo.updateType(doctorId, DoctorType.DOCTOR_TYPE_INDIVIDUAL);
            // Re-save to add to doctors_by_individual (since save() handles it based on type)
            repo.save(updatedDoc);
        }

        natsClient.sendDoctorUpdated(doctorId.toString());
    }

    @Override
    public void deleteDoctor(@NotNull UUID doctorId, @NotBlank String email, @NotBlank String password) {
        // Ownership check
        SecurityUtils.validateOwnership(doctorId);

        DoctorCredentials credentials = credentialsRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Doctor not found with email: " + email));

        if (!credentials.getDoctorId().equals(doctorId)) {
            throw new InvalidArgumentException("Doctor ID does not match email");
        }

        if (!BCrypt.checkpw(password, credentials.getPasswordHash())) {
            throw new InvalidArgumentException("Invalid credentials");
        }

        repo.deleteDoctor(doctorId, email, password);
        natsClient.sendDoctorDeleted(doctorId.toString());
    }

    @Override
    public String forgotPassword(@NotBlank @Email String email) {
        DoctorCredentials credentials = credentialsRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + email));
        
        return jwtProvider.generateResetToken(credentials.getDoctorId().toString(), "Doctor");
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

        if (!"Doctor".equalsIgnoreCase(role)) {
            throw new InvalidArgumentException("Token is not for a Doctor");
        }

        String passwordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        credentialsRepo.updatePassword(currentUserId, passwordHash);
        log.info("Password reset successful for doctor: {}", currentUserId);
    }

    @Override
    public DoctorActiveResponse isDoctorActive(@NotNull UUID doctorId, UUID clinicId) {
        ZonedDateTime now = DateTimeUtils.now();
        DayOfWeek currentDay = now.getDayOfWeek();

        UUID effectiveClinicId = (clinicId != null) ? clinicId : DoctorRepositoryPort.NO_CLINIC_ID;
        Optional<DoctorSchedule> scheduleOpt = scheduleRepo.findByDoctorAndClinic(doctorId, effectiveClinicId);

        // Fallback: If no specific schedule found for individual practice, check if they are active anywhere?
        // Actually, the user wants to differentiate, so we should stay in the requested context.
        
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

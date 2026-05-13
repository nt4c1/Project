package com.health.admin.application;

import com.health.admin.domain.model.Admin;
import com.health.admin.domain.ports.AdminRepositoryPort;
import com.health.common.auth.JwtProvider;
import com.health.common.exception.InvalidArgumentException;
import com.health.common.exception.NotFoundException;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.ValidateTokenResponse;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

@Slf4j
@Singleton
public class AdminUseCase implements AdminInterface {

    private final AdminRepositoryPort repo;
    private final JwtProvider jwtProvider;
    private final com.health.common.redis.RedisUtil redisUtil;

    public AdminUseCase(AdminRepositoryPort repo, 
                        JwtProvider jwtProvider,
                        com.health.common.redis.RedisUtil redisUtil) {
        this.repo = repo;
        this.jwtProvider = jwtProvider;
        this.redisUtil = redisUtil;
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        if (!jwtProvider.isValid(refreshToken)) {
            throw new InvalidArgumentException("Invalid refresh token");
        }

        String type = jwtProvider.extractClaims(refreshToken).get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new InvalidArgumentException("Invalid token type");
        }

        String userId = jwtProvider.extractUserId(refreshToken);
        Admin admin = repo.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new NotFoundException("Admin not found"));

        String accessToken = jwtProvider.generateAccessToken(admin.getId().toString(), "Admin");
        String newRefreshToken = jwtProvider.generateRefreshToken(admin.getId().toString());

        return TokenResponse.newBuilder()
                .setSuccess(true)
                .setAccessToken(accessToken)
                .setRefreshToken(newRefreshToken)
                .setMessage("Token refreshed successfully")
                .build();
    }

    @Override
    public TokenResponse loginAdmin(String email, String password) {
        Admin admin = repo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Admin not found: " + email));

        if (!BCrypt.checkpw(password, admin.getPasswordHash())) {
            throw new InvalidArgumentException("Invalid credentials");
        }

        String accessToken = jwtProvider.generateAccessToken(admin.getId().toString(), "Admin");
        String refreshToken = jwtProvider.generateRefreshToken(admin.getId().toString());

        log.info("Admin logged in: {} ({})", admin.getName(), admin.getId());

        return TokenResponse.newBuilder()
                .setSuccess(true)
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setMessage("Login Successfully")
                .build();
    }

    @Override
    public ValidateTokenResponse validateAdmin(String token) {
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
            log.warn("Admin Token Validation Failed: {}", e.getMessage());
            return ValidateTokenResponse.newBuilder()
                    .setValid(false)
                    .setMessage("Token Invalid: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public com.health.grpc.admin.GetStatsResponse getSystemStats() {
        long doctors = getStat("stats:total:doctors", repo::countDoctors);
        long patients = getStat("stats:total:patients", repo::countPatients);
        long clinics = getStat("stats:total:clinics", repo::countClinics);
        long appts = getStat("stats:total:appointments", repo::countAppointments);

        return com.health.grpc.admin.GetStatsResponse.newBuilder()
                .setTotalDoctors(doctors)
                .setTotalPatients(patients)
                .setTotalClinics(clinics)
                .setTotalAppointments(appts)
                .setRegistrationRatePerDay(doctors / 30.0)
                .setAppointmentVolumePerDay(appts / 30.0)
                .build();
    }

    private long getStat(String key, java.util.function.LongSupplier fallback) {//lazy+don't waste gc (supplier<long>
        String val = redisUtil.get(key, String.class);
        if (val != null) {
            try {
                long count = Long.parseLong(val);
                if (count > 0) {
                    return count;
                }
                log.info("Redis value for {} is 0, checking DB for potential updates", key);
            } catch (NumberFormatException e) {
                log.warn("Invalid stat in Redis for key {}: {}", key, val);
            }
        }
        
        // Fallback to DB and sync Redis
        long count = fallback.getAsLong();
        log.info("Stat key {} fallback to DB returned: {}", key, count);
        redisUtil.set(key, String.valueOf(count), 0);
        return count;
    }
}

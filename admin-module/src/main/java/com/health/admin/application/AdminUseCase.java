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

    public AdminUseCase(AdminRepositoryPort repo, JwtProvider jwtProvider) {
        this.repo = repo;
        this.jwtProvider = jwtProvider;
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
}

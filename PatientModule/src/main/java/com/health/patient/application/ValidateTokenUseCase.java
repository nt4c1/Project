package com.health.patient.application;

import com.health.grpc.auth.ValidateTokenResponse;
import com.health.patient.infrastructure.JwtProvider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ValidateTokenUseCase implements ValidateTokenUseCaseInterface {

    private final JwtProvider jwtProvider;

    public ValidateTokenUseCase(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public ValidateTokenResponse execute(String token) {
        try {
            String userId = jwtProvider.extractUserId(token);
            String role = jwtProvider.extractRole(token);
            boolean valid = jwtProvider.isValid(token);
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
}
package com.health.admin.application;

import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.ValidateTokenResponse;
import jakarta.validation.constraints.NotBlank;

public interface AdminInterface {
    TokenResponse loginAdmin(@NotBlank String email, @NotBlank String password);
    ValidateTokenResponse validateAdmin(@NotBlank String token);
}

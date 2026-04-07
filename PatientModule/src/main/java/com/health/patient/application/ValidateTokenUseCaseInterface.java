package com.health.patient.application;

import com.health.grpc.auth.ValidateTokenResponse;

public interface ValidateTokenUseCaseInterface {
    ValidateTokenResponse execute(String token);
}
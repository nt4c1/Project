package com.health.doctor.application.usecase.interfaces;

import com.health.grpc.auth.ValidateTokenResponse;

public interface ValidateDoctorTokenUseCaseInterface {
    ValidateTokenResponse execute(String token);
}

package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.usecase.interfaces.ValidateDoctorTokenUseCaseInterface;
import com.health.doctor.infrastructure.JwtProvider;
import com.health.grpc.auth.ValidateTokenResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ValidateDoctorTokenUseCase implements ValidateDoctorTokenUseCaseInterface {

    private static final Logger log = LoggerFactory.getLogger(ValidateDoctorTokenUseCase.class);
    private final JwtProvider jwtProvider;

    public ValidateDoctorTokenUseCase(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public ValidateTokenResponse execute(String token) {
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
}

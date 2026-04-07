package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.usecase.interfaces.DoctorLoginUseCaseInterface;
import com.health.doctor.domain.exception.InvalidArgumentException;
import com.health.doctor.domain.exception.NotFoundException;
import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import com.health.doctor.infrastructure.JwtProvider;
import com.health.grpc.doctor.DoctorLoginResponse;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

@Slf4j
@Singleton
public class DoctorLoginUseCase implements DoctorLoginUseCaseInterface {

    private final DoctorRepositoryPort repo;
    private final JwtProvider jwtProvider;

    public DoctorLoginUseCase(DoctorRepositoryPort repo, JwtProvider jwtProvider) {
        this.repo = repo;
        this.jwtProvider = jwtProvider;
    }

    @Override
    public DoctorLoginResponse execute(String email, String password) {
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
}

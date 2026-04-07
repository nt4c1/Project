package com.health.doctor.application.usecase.interfaces;

import com.health.grpc.doctor.DoctorLoginResponse;

public interface DoctorLoginUseCaseInterface {
    DoctorLoginResponse execute(String email,String password);
}

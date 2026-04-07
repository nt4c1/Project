package com.health.patient.application;

import com.health.grpc.patient.PatientLoginResponse;

import java.util.UUID;

public interface RegisterPatientUseCaseInterface {
    public UUID execute(String name, String email, String password, String phone);
    public PatientLoginResponse login(String email, String password);
}

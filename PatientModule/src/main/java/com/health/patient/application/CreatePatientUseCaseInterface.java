package com.health.patient.application;

import java.util.UUID;

public interface CreatePatientUseCaseInterface {
    UUID execute(String name,String email,String Phone,String password);
}

package com.health.doctor.application.usecase.interfaces;

import java.util.UUID;

public interface CreateClinicUseCaseInterface {
    UUID execute(String name, String locationText);
}

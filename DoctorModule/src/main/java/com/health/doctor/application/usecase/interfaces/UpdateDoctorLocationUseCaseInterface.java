package com.health.doctor.application.usecase.interfaces;

import java.util.UUID;

public interface UpdateDoctorLocationUseCaseInterface {
    void execute(UUID doctorId, String text);
}

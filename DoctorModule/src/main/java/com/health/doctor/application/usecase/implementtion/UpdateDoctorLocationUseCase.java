package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.service.LocationService;
import com.health.doctor.application.usecase.interfaces.UpdateDoctorLocationUseCaseInterface;
import com.health.doctor.domain.model.Location;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class UpdateDoctorLocationUseCase implements UpdateDoctorLocationUseCaseInterface {
    private final LocationService locationService;
    private final DoctorRepositoryPort repo;

    public UpdateDoctorLocationUseCase(LocationService locationService, DoctorRepositoryPort repo) {
        this.locationService = locationService;
        this.repo = repo;
    }

    public void execute(UUID doctorId,String text){
        Location location = locationService.resolve(text);
        repo.updateLocation(doctorId,location);
    }
}

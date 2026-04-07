package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.service.LocationService;
import com.health.doctor.application.usecase.interfaces.CreateClinicUseCaseInterface;
import com.health.doctor.domain.model.Clinic;
import com.health.doctor.domain.model.Location;
import com.health.doctor.domain.ports.ClinicRepositoryPort;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class CreateClinicUseCase implements CreateClinicUseCaseInterface {

    private final ClinicRepositoryPort repo;
    private final LocationService locationService;

    public CreateClinicUseCase(ClinicRepositoryPort repo,
                               LocationService locationService) {
        this.repo = repo;
        this.locationService = locationService;
    }

    public UUID execute(String name, String locationText) {

        Location location = locationService.resolve(locationText);

        UUID clinicId = UUID.randomUUID();

        Clinic clinic = new Clinic(
                clinicId,
                name,
                location,
                true
        );

        repo.save(clinic);

        return clinicId;
    }
}
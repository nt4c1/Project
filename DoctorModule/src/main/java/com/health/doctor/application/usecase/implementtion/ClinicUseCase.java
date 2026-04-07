package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.service.LocationService;
import com.health.doctor.application.usecase.interfaces.ClinicInterface;
import com.health.doctor.domain.model.Clinic;
import com.health.doctor.domain.model.Location;
import com.health.doctor.domain.ports.ClinicRepositoryPort;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;

@Singleton
public class ClinicUseCase implements ClinicInterface {
    private final ClinicRepositoryPort repo;
    private final LocationService locationService;

    public ClinicUseCase(ClinicRepositoryPort repo, LocationService locationService) {
        this.repo = repo;
        this.locationService = locationService;
    }

    @Override
    public UUID createClinic(String name, String locationText) {
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

    @Override
    public List<Clinic> getClinicsByLocationText(String locationText) {
        return List.of();
    }

    @Override
    public List<Clinic> getClinicsByLocationGeohash(String geohashPrefix) {
        return List.of();
    }

    @Override
    public Clinic getClinicById(UUID clinicId) {
        return repo.findById(clinicId);
    }
}
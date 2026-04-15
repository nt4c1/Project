package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.service.LocationService;
import com.health.doctor.application.usecase.interfaces.ClinicInterface;
import com.health.doctor.domain.exception.InvalidArgumentException;
import com.health.doctor.domain.model.Clinic;
import com.health.doctor.domain.model.Location;
import com.health.doctor.domain.ports.ClinicRepositoryPort;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
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
        if (name == null || locationText == null)
            throw new RuntimeException("Name or location is required");

        Clinic existing = repo.findByLocationText(locationText);
        if (existing != null && locationText.equals(existing.getLocationText()))
            throw new RuntimeException("Clinic already exists");

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
        Clinic clinic = repo.findByLocationText(locationText);
        return clinic != null ? List.of(clinic) : List.of();
    }

    @Override
    public List<Clinic> getClinicsByLocationGeohash(String geohashPrefix) {
        Clinic clinic = repo.findByLocationGeohash(geohashPrefix);
        return clinic != null ? List.of(clinic) : List.of();
    }



    @Override
    public Clinic getClinicById(UUID clinicId) {
        return repo.findById(clinicId);
    }

    @Override
    public List<Clinic> findByName(String name) {
        return List.of(repo.findByName(name));

    }

    @Override
    public List<Clinic> getNearbyClinicsByLocationText(String locationText) {
        if (locationText == null || locationText.isBlank())
            throw new InvalidArgumentException("Location text is required");

        Location location = locationService.resolve(locationText);
        String   geohash  = location.getGeohash().substring(0, 5);

        log.info("Searching clinics near geohash={} lat={} lon={}",
                geohash, location.getLatitude(), location.getLongitude());

        return repo.findNearby(geohash, location.getLatitude(), location.getLongitude());
    }

}
package com.health.doctor.application.usecase.implementtion;

import com.health.doctor.application.service.LocationService;
import com.health.doctor.application.usecase.interfaces.GetDoctorsByLocationUseCaseInterface;
import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.Location;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
@Slf4j
@Singleton
public class GetDoctorsByLocationUseCase implements GetDoctorsByLocationUseCaseInterface {

    private final DoctorRepositoryPort repo;
    private final LocationService locationService;

    public GetDoctorsByLocationUseCase(DoctorRepositoryPort repo, LocationService locationService) {
        this.repo = repo;
        this.locationService = locationService;
    }

    public List<Doctor> executeNear(String locationText){
        Location location = locationService.resolve(locationText);
        String geohash = location.getGeohash().substring(0,5);
        log.info("Searching geohash: {}", geohash);
        log.info("Neighbors will also be searched");
        return repo.findNearby(geohash);
    }

    public List<Doctor> execute(String geohashPrefix) {
        return repo.findByGeohashPrefix(geohashPrefix);
    }
}
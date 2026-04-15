package com.health.doctor.domain.ports;

import com.health.doctor.domain.model.Clinic;
import com.health.doctor.domain.model.Location;

import java.util.List;
import java.util.UUID;

public interface ClinicRepositoryPort {
    void save(Clinic clinic);
    Clinic findById(UUID clinicId);
    Clinic findByName(String name);
    Clinic findByLocationText(String locationText);
    Clinic findByLocationGeohash(String geohash);
    Location getLocation(UUID clinicId);
    Clinic findDoctorAndClinic(UUID doctorId);
    List<Clinic> findNearby(String geohash, double latitude, double longitude);
}

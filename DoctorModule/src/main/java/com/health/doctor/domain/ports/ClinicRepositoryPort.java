package com.health.doctor.domain.ports;

import com.health.doctor.domain.model.Clinic;

import java.util.UUID;

public interface ClinicRepositoryPort {
    void save(Clinic clinic);
    Clinic findById(UUID clinicId);
    Clinic findByName(String name);
    Clinic findByLocationText(String locationText);
    Clinic findByLocationGeohash(String geohash);
}

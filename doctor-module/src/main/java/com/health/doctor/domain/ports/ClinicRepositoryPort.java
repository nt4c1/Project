package com.health.doctor.domain.ports;

import com.health.doctor.domain.model.Clinic;
import com.health.doctor.domain.model.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicRepositoryPort {
    void save(Clinic clinic);

    Optional<Clinic> findById(UUID clinicId);

    Clinic findByName(String name);

    List<Clinic> findByLocationText(String locationText);

    List<Clinic> findByLocationGeohash(String geohash);

    Location getLocation(UUID clinicId);

    Clinic findDoctorAndClinic(UUID doctorId);

    List<Clinic> findNearby(String geohash, double latitude, double longitude);

    List<Clinic> searchByName(String name, int page, int size);

    long countByName(String name);

    void update(Clinic clinic);

    void delete(UUID clinicId);

    void updateLocation(UUID clinicId, com.health.doctor.domain.model.Location location);

    void saveCredentials(com.health.doctor.domain.model.ClinicCredentials creds);

    Optional<com.health.doctor.domain.model.ClinicCredentials> findCredentialsByEmail(String email);

    void updatePassword(UUID clinicId, String passwordHash);
}

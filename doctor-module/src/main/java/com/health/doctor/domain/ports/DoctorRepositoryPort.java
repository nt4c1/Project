package com.health.doctor.domain.ports;

import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.DoctorCredentials;
import com.health.doctor.domain.model.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorRepositoryPort {

    void save(Doctor doctor);

    void updateLocation(UUID doctorId, UUID clinicId, Location location);

    Optional<Doctor> findById(UUID doctorId);

    List<Doctor> findByGeohashPrefix(String prefix);

    List<Doctor> findNearby(String geohash, double lat, double lon);

    List<Doctor> findByClinicId(UUID clinicId);

    void updateDoctor(UUID doctorId, String email, String password, String phone);

    void deleteDoctor(UUID doctorId, String email, String password);

    List<UUID> findClinicIds(UUID doctorId);

    void addClinicId(UUID doctorId, UUID clinicId);

    Optional<Doctor> findActive(UUID doctorId);

}
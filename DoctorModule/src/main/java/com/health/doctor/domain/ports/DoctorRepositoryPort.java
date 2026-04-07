package com.health.doctor.domain.ports;

import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.Location;

import javax.print.Doc;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorRepositoryPort {
    void save(Doctor doctor);
    void updateLocation (UUID doctorId, Location location);
    Optional<Doctor> findById(UUID doctorId);
    List<Doctor> findByGeohashPrefix(String prefix);
    List<Doctor> findNearby (String geohash);
    List<Doctor> findByClinicId(UUID clinicId);
    Optional<Doctor> findByEmail(String email);
}

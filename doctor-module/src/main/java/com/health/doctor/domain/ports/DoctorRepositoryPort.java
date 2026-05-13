package com.health.doctor.domain.ports;

import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorRepositoryPort {

    UUID NO_CLINIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    void save(Doctor doctor);

    void updateLocation(UUID doctorId, UUID clinicId, Location location);

    Optional<Doctor> findById(UUID doctorId);

    List<Doctor> findByLocationText(String locationText);

    List<Doctor> findNearby(String geohash, double lat, double lon);

    List<Doctor> findByClinicId(UUID clinicId);

    void updateDoctor(UUID doctorId, String email, String password, String phone);

    void deleteDoctor(UUID doctorId, String email, String password);

    void addClinicId(UUID doctorId, UUID clinicId);

    void removeClinicId(UUID doctorId, UUID clinicId);

    void removeIndividualPractice(UUID doctorId);

    void updateType(UUID doctorId, com.health.doctor.domain.model.DoctorType type);

    boolean isDeleted(UUID doctorId);

    void reactivate(Doctor doctor);
}
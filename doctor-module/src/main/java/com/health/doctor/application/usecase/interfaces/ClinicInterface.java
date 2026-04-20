package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.Clinic;

import java.util.List;
import java.util.UUID;

public interface ClinicInterface {
    UUID createClinic(String name, String locationText);
    List<Clinic> getClinicsByLocationText(String locationText);
    List<Clinic> getClinicsByLocationGeohash(String geohashPrefix);
    Clinic getClinicById(UUID clinicId);
    List<Clinic> findByName(String name);
    List<Clinic> getNearbyClinicsByLocationText(String locationText);
    List<Clinic> searchClinics(String name, int page, int size);
    long countClinicsByName(String name);
}

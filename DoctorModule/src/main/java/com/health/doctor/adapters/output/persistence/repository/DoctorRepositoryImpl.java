package com.health.doctor.adapters.output.persistence.repository;

import ch.hsr.geohash.GeoHash;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.print.Doc;
import java.util.*;

@Slf4j
@Serdeable
@Introspected
@Serdeable.Deserializable
@Singleton
public class DoctorRepositoryImpl implements DoctorRepositoryPort {

    private final CqlSession session;

    public DoctorRepositoryImpl(CqlSession session) {
        this.session = session;
    }

    @Override
    public void save(Doctor d) {

        session.execute(
                "INSERT INTO doctor_service.doctors (doctor_id, name, clinic_id, type, specialization, is_active, email, password_hash) VALUES (?,?,?,?,?,?,?,?)",
                d.getId(),
                d.getName(),
                d.getClinicId(),
                d.getType().name(),
                d.getSpecialization(),
                d.isActive(),
                d.getEmail(),
                d.getPasswordHash()
        );

        if (d.getClinicId() == null) {
            session.execute(
                    "INSERT INTO doctor_service.doctors_by_individual (doctor_id, name, type, specialization, is_active,email,password_hash) VALUES (?,?,?,?,?,?,?)",
                    d.getId(), d.getName(), d.getType().name(), d.getSpecialization(), d.isActive(),d.getEmail(),d.getPasswordHash()
            );
        } else {
            session.execute(
                    "INSERT INTO doctor_service.doctors_by_clinic (clinic_id, doctor_id, name, type, specialization, is_active,email,password_hash) VALUES (?,?,?,?,?,?,?,?)",
                    d.getClinicId(), d.getId(), d.getName(),
                    d.getType().name(), d.getSpecialization(), d.isActive(),
                    d.getEmail(),d.getPasswordHash()
            );
        }
    }

    @Override
    public void updateLocation(UUID doctorId, Location location) {

        String prefix = location.getGeohash().substring(0, 5);

        session.execute(
                "INSERT INTO doctor_service.doctors_by_location (geohash_prefix, doctor_id, latitude, longitude, location_text) VALUES (?,?,?,?,?)",
                prefix,
                doctorId,
                location.getLatitude(),
                location.getLongitude(),
                location.getLocationText()
        );
    }

    @Override
    public Optional<Doctor> findById(UUID doctorId) {

        Row r = session.execute(
                "SELECT * FROM doctor_service.doctors WHERE doctor_id=?",
                doctorId
        ).one();

        if (r == null) return Optional.empty();

        return Optional.of(new Doctor(
                r.getUuid("doctor_id"),
                r.getString("name"),
                r.getUuid("clinic_id"),
                DoctorType.valueOf(r.getString("type")),
                r.getString("specialization"),
                r.getBoolean("is_active"),
                r.getString("email"),
                r.getString("password_hash")
        ));
    }

    @Override
    public List<Doctor> findByGeohashPrefix(String prefix) {

        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.doctors_by_location WHERE geohash_prefix=?",
                prefix
        );

        List<Doctor> list = new ArrayList<>();

        for (Row r : rs) {
            list.add(new Doctor(
                    r.getUuid("doctor_id"),
                    null,
                    null,
                    null,
                    null,
                    true,
                    "email",
                    "password_hash"
            ));
        }

        return list;
    }
    @Override
    public List<Doctor> findNearby(String geohash) {
        // Get the geohash and its 8 neighbors
        GeoHash center = GeoHash.fromGeohashString(geohash);
        GeoHash[] neighbors = center.getAdjacent();

        Set<String> prefixes = new HashSet<>();
        prefixes.add(center.toBase32().substring(0, 5));
        for (GeoHash neighbor : neighbors) {
            prefixes.add(neighbor.toBase32().substring(0, 5));
        }

        log.info("Searching geohash prefixes: {}", prefixes);

        List<Doctor> list = new ArrayList<>();
        for (String prefix : prefixes) {
            ResultSet rs = session.execute(
                    "SELECT * FROM doctor_service.doctors_by_location WHERE geohash_prefix=?",
                    prefix
            );
            for (Row r : rs) {
                list.add(new Doctor(
                        r.getUuid("doctor_id"),
                        null, null, null, null, true, "email", "password_hash"
                ));
            }
        }
        return list;
    }

    @Override
    public List<Doctor> findByClinicId(UUID clinicId) {
        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.doctors_by_clinic WHERE clinic_id=?",
                clinicId
        );

        List<Doctor> list = new ArrayList<>();
        for (Row r : rs) {
            list.add(new Doctor(
                    r.getUuid("doctor_id"),
                    r.getString("name"),
                    clinicId,
                    DoctorType.valueOf(r.getString("type")),
                    r.getString("specialization"),
                    r.getBoolean("is_active"),
                    r.getString("email"),
                    r.getString("password_hash")
            ));
        }
        return list;
    }

    @Override
    public Optional<Doctor> findByEmail(String email){
        Row r =session.execute(
                "SELECT * FROM doctor_service.doctors WHERE email=?",
                email
        ).one();
        if (r==null) return Optional.empty();
        return Optional.of(new Doctor(
                r.getUuid("doctor_id"),
                r.getString("name"),
                r.getUuid("clinic_id"),
                DoctorType.valueOf(r.getString("type")),
                r.getString("specialization"),
                r.getBoolean("is_active"),
                r.getString("email"),
                r.getString("password_hash")
                )
        );
    }
}
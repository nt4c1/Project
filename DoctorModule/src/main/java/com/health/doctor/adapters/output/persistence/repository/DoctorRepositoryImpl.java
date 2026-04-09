package com.health.doctor.adapters.output.persistence.repository;

import ch.hsr.geohash.GeoHash;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;

@Slf4j
@Singleton
public class DoctorRepositoryImpl implements DoctorRepositoryPort {

    private final CqlSession session;

    public DoctorRepositoryImpl(CqlSession session) {
        this.session = session;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @Override
    public void save(Doctor d) {
        Instant now = Instant.now();

        // doctors — canonical profile (no credentials)
        session.execute(
                "INSERT INTO doctor_service.doctors " +
                        "(doctor_id, name, clinic_id, type, specialization, " +
                        " is_active, is_deleted, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                d.getId(), d.getName(), d.getClinicId(),
                d.getType().name(), d.getSpecialization(),
                d.isActive(), false, now, now
        );

        if (d.getClinicId() == null) {
            // doctors_by_individual — standalone doctor
            session.execute(
                    "INSERT INTO doctor_service.doctors_by_individual " +
                            "(doctor_id, name, type, specialization, " +
                            " is_active, is_deleted, updated_at) " +
                            "VALUES (?,?,?,?,?,?,?)",
                    d.getId(), d.getName(), d.getType().name(),
                    d.getSpecialization(), d.isActive(), false, now
            );
        } else {
            // doctors_by_clinic — clinic roster
            session.execute(
                    "INSERT INTO doctor_service.doctors_by_clinic " +
                            "(clinic_id, doctor_id, name, type, specialization, " +
                            " is_active, is_deleted, updated_at) " +
                            "VALUES (?,?,?,?,?,?,?,?)",
                    d.getClinicId(), d.getId(), d.getName(),
                    d.getType().name(), d.getSpecialization(),
                    d.isActive(), false, now
            );
        }
    }

    // ── Location ──────────────────────────────────────────────────────────────

    @Override
    public void updateLocation(UUID doctorId, Location location) {
        String prefix = location.getGeohash().substring(0, 5);

        // Fetch name + specialization to denormalize into the location table
        Row profileRow = session.execute(
                "SELECT name, specialization, clinic_id FROM doctor_service.doctors WHERE doctor_id=?",
                doctorId
        ).one();

        String name           = profileRow != null ? profileRow.getString("name")           : null;
        String specialization = profileRow != null ? profileRow.getString("specialization") : null;
        UUID   clinicId       = profileRow != null ? profileRow.getUuid("clinic_id")        : null;

        session.execute(
                "INSERT INTO doctor_service.doctors_by_location " +
                        "(geohash_prefix, doctor_id, name, specialization, clinic_id, " +
                        " latitude, longitude, location_text, is_active) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                prefix, doctorId, name, specialization, clinicId,
                location.getLatitude(), location.getLongitude(),
                location.getLocationText(), true
        );
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Override
    public Optional<Doctor> findById(UUID doctorId) {
        Row r = session.execute(
                "SELECT * FROM doctor_service.doctors WHERE doctor_id=?",
                doctorId
        ).one();
        if (r == null) return Optional.empty();
        return Optional.of(mapProfileRow(r));
    }

    @Override
    public List<Doctor> findByGeohashPrefix(String prefix) {
        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.doctors_by_location WHERE geohash_prefix=?",
                prefix
        );
        List<Doctor> list = new ArrayList<>();
        for (Row r : rs) list.add(mapLocationRow(r));
        return list;
    }

    @Override
    public List<Doctor> findNearby(String geohash) {
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
            for (Row r : rs) list.add(mapLocationRow(r));
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
            Doctor d = new Doctor(
                    r.getUuid("doctor_id"),
                    r.getString("name"),
                    clinicId,
                    DoctorType.valueOf(r.getString("type")),
                    r.getString("specialization"),
                    r.getBoolean("is_active")
            );
            list.add(d);
        }
        return list;
    }


    // ── Row mappers ───────────────────────────────────────────────────────────

    private Doctor mapProfileRow(Row r) {
        return new Doctor(
                r.getUuid("doctor_id"),
                r.getString("name"),
                r.getUuid("clinic_id"),
                DoctorType.valueOf(r.getString("type")),
                r.getString("specialization"),
                r.getBoolean("is_active"),
                r.getBoolean("is_deleted"),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        );
    }

    /** Maps a doctors_by_location row — name + specialization already denormalized. */
    private Doctor mapLocationRow(Row r) {
        return new Doctor(
                r.getUuid("doctor_id"),
                r.getString("name"),
                r.getUuid("clinic_id"),
                null,   // type not stored in location table
                r.getString("specialization"),
                r.getBoolean("is_active")
        );
    }
}
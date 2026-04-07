package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.ports.ClinicRepositoryPort;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class ClinicRepositoryImpl implements ClinicRepositoryPort {

    private final CqlSession session;

    public ClinicRepositoryImpl(CqlSession session) {
        this.session = session;
    }

    @Override
    public void save(Clinic c) {

        Location l = c.getLocation();

        session.execute(
                "INSERT INTO doctor_service.clinics (clinic_id, name, latitude, longitude, geohash, location_text, is_active) VALUES (?,?,?,?,?,?,?)",
                c.getId(),
                c.getName(),
                l.getLatitude(),
                l.getLongitude(),
                l.getGeohash(),
                l.getLocationText(),
                c.isActive()
        );
    }

    @Override
    public Clinic findById(UUID clinicId) {

        Row r = session.execute(
                "SELECT * FROM doctor_service.clinics WHERE clinic_id=?",
                clinicId
        ).one();

        if (r == null) return null;

        Location loc = new Location(
                r.getDouble("latitude"),
                r.getDouble("longitude"),
                r.getString("geohash"),
                r.getString("location_text")
        );

        return new Clinic(
                r.getUuid("clinic_id"),
                r.getString("name"),
                loc,
                r.getBoolean("is_active")
        );
    }
}
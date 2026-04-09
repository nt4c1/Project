package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.doctor.domain.model.Clinic;
import com.health.doctor.domain.model.Location;
import com.health.doctor.domain.ports.ClinicRepositoryPort;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.UUID;

@Singleton
public class ClinicRepositoryImpl implements ClinicRepositoryPort {

    private final CqlSession session;

    public ClinicRepositoryImpl(CqlSession session) {
        this.session = session;
    }

    @Override
    public void save(Clinic c) {
        Location l   = c.getLocation();
        Instant  now = Instant.now();
        String   prefix = l.getGeohash() != null && l.getGeohash().length() >= 5
                ? l.getGeohash().substring(0, 5)
                : l.getGeohash();

        // clinics — canonical row
        session.execute(
                "INSERT INTO doctor_service.clinics " +
                        "(clinic_id, name, latitude, longitude, geohash, location_text, " +
                        " is_active, is_deleted, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?) IF NOT EXISTS",
                c.getId(), c.getName(),
                l.getLatitude(), l.getLongitude(),
                l.getGeohash(), l.getLocationText(),
                c.isActive(), false, now, now
        );

        // clinics_by_geohash — replaces secondary index on geohash
        session.execute(
                "INSERT INTO doctor_service.clinics_by_geohash " +
                        "(geohash, clinic_id, name, latitude, longitude, is_active) " +
                        "VALUES (?,?,?,?,?,?)",
                prefix, c.getId(), c.getName(),
                l.getLatitude(), l.getLongitude(), c.isActive()
        );

        // clinics_by_location_text — replaces secondary index on location_text
        session.execute(
                "INSERT INTO doctor_service.clinics_by_location_text " +
                        "(location_text, clinic_id, name, is_active) " +
                        "VALUES (?,?,?,?)",
                l.getLocationText(), c.getId(), c.getName(), c.isActive()
        );
    }

    @Override
    public Clinic findById(UUID clinicId) {
        Row r = session.execute(
                "SELECT * FROM doctor_service.clinics WHERE clinic_id=?",
                clinicId
        ).one();
        return mapRowToClinic(r);
    }

    @Override
    public Clinic findByName(String name) {
        // No lookup table for name — ALLOW FILTERING is acceptable for low-frequency
        // admin queries. Add a clinics_by_name table if this becomes a hot path.
        Row r = session.execute(
                "SELECT * FROM doctor_service.clinics WHERE name=? ALLOW FILTERING",
                name
        ).one();
        return mapRowToClinic(r);
    }

    @Override
    public Clinic findByLocationText(String locationText) {
        // Use lookup table — O(1) partition read, no scatter-gather
        Row lookup = session.execute(
                "SELECT clinic_id FROM doctor_service.clinics_by_location_text " +
                        "WHERE location_text=? LIMIT 1",
                locationText
        ).one();
        if (lookup == null) return null;
        return findById(lookup.getUuid("clinic_id"));
    }

    @Override
    public Clinic findByLocationGeohash(String geohash) {
        String prefix = geohash.length() >= 5 ? geohash.substring(0, 5) : geohash;
        // Use lookup table — O(1) partition read
        Row lookup = session.execute(
                "SELECT clinic_id FROM doctor_service.clinics_by_geohash " +
                        "WHERE geohash=? LIMIT 1",
                prefix
        ).one();
        if (lookup == null) return null;
        return findById(lookup.getUuid("clinic_id"));
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private Clinic mapRowToClinic(Row r) {
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
                r.getBoolean("is_active"),
                r.getBoolean("is_deleted"),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        );
    }
}
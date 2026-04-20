package com.health.doctor.adapters.output.persistence.repository;

import ch.hsr.geohash.GeoHash;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.doctor.domain.model.Clinic;
import com.health.doctor.domain.model.Location;
import com.health.doctor.domain.ports.ClinicRepositoryPort;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.health.doctor.mapper.MapperClass.mapRowToClinic;

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
        String   prefix = l.getGeohash() != null && l.getGeohash().length() >= 6
                ? l.getGeohash().substring(0, 6)
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
                        "(geohash, clinic_id, name, latitude, longitude, is_active, is_deleted) " +
                        "VALUES (?,?,?,?,?,?,?)",
                prefix, c.getId(), c.getName(),
                l.getLatitude(), l.getLongitude(), c.isActive(), false
        );

        // clinics_by_location_text — replaces secondary index on location_text
        session.execute(
                "INSERT INTO doctor_service.clinics_by_location_text " +
                        "(location_text, clinic_id, name, is_active, is_deleted) " +
                        "VALUES (?,?,?,?,?)",
                l.getLocationText(), c.getId(), c.getName(), c.isActive(), false
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
        String prefix = geohash.length() >= 6 ? geohash.substring(0, 6) : geohash;
        // Use lookup table — O(1) partition read
        Row lookup = session.execute(
                "SELECT clinic_id FROM doctor_service.clinics_by_geohash " +
                        "WHERE geohash=? LIMIT 1",
                prefix
        ).one();
        if (lookup == null) return null;
        return findById(lookup.getUuid("clinic_id"));
    }

    @Override
    public Location getLocation(UUID clinicId) {
        Row r = session.execute(
                "SELECT latitude, longitude, geohash, location_text " +
                        "FROM doctor_service.clinics WHERE clinic_id=?",
                clinicId
        ).one();
        if (r == null) return null;
        return new Location(
                r.getDouble("latitude"),
                r.getDouble("longitude"),
                r.getString("geohash"),
                r.getString("location_text")
        );
    }

    @Override
    public Clinic findDoctorAndClinic(UUID doctorId) {
        Row r = session.execute(
                "SELECT clinic_id, doctor_id FROM doctor_service.doctors_by_clinic WHERE doctor_id=?",
                doctorId
        ).one();
        if (r == null) return null;
        return r.getUuid("clinic_id") != null ? findById(r.getUuid("clinic_id")) : null;
    }

    @Override
    public List<Clinic> findNearby(String geohash, double lat, double lon) {
        // Zoom out strategy for clinics
        for (int precision = 6; precision >= 4; precision--) {
            String prefix = geohash.substring(0, Math.min(geohash.length(), precision));
            List<Clinic> found = searchClinicsInPrefix(prefix, lat, lon);
            if (!found.isEmpty()) return found;
        }
        return Collections.emptyList();
    }

    private List<Clinic> searchClinicsInPrefix(String prefix, double lat, double lon) {
        GeoHash center    = GeoHash.fromGeohashString(prefix);
        GeoHash[] neighbors = center.getAdjacent();

        Set<String> prefixes = new HashSet<>();
        prefixes.add(prefix);
        for (GeoHash n : neighbors) prefixes.add(n.toBase32().substring(0, prefix.length()));

        record ClinicWithDistance(Clinic clinic, double distanceKm) {}
        List<ClinicWithDistance> candidates = new ArrayList<>();

        for (String p : prefixes) {
            ResultSet rs = session.execute(
                    "SELECT clinic_id, name, latitude, longitude, is_active " +
                            "FROM doctor_service.clinics_by_geohash WHERE geohash=?",
                    p
            );
            for (Row r : rs) {
                double cLat = r.getDouble("latitude");
                double cLon = r.getDouble("longitude");
                double dist = haversineKm(lat, lon, cLat, cLon);
                // Reuse the full clinic from canonical table to get all fields
                Clinic clinic = findById(r.getUuid("clinic_id"));
                if (clinic != null && clinic.isActive()) {
                    candidates.add(new ClinicWithDistance(clinic, dist));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(ClinicWithDistance::distanceKm));
        return candidates.stream().map(ClinicWithDistance::clinic).collect(Collectors.toList());
    }

    @Override
    public List<Clinic> searchByName(String name, int page, int size) {
        String query = "SELECT * FROM doctor_service.clinics WHERE name LIKE ? ALLOW FILTERING";

        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.clinics WHERE is_deleted=false ALLOW FILTERING"
        );
        
        return rs.all().stream()
                .map(com.health.doctor.mapper.MapperClass::mapRowToClinic)
                .filter(c -> c.getName().toLowerCase().contains(name.toLowerCase()))
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    @Override
    public long countByName(String name) {
        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.clinics WHERE is_deleted=false ALLOW FILTERING"
        );
        return rs.all().stream()
                .map(com.health.doctor.mapper.MapperClass::mapRowToClinic)
                .filter(c -> c.getName().toLowerCase().contains(name.toLowerCase()))
                .count();
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
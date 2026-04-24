package com.health.doctor.adapters.output.persistence.repository;

import ch.hsr.geohash.GeoHash;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.doctor.domain.model.Clinic;
import com.health.doctor.domain.model.Location;
import com.health.doctor.domain.ports.ClinicRepositoryPort;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import com.health.doctor.mapper.MapperClass;

@Singleton
public class ClinicRepositoryImpl implements ClinicRepositoryPort {

    private final CqlSession session;
    private final PreparedStatement insertClinic;
    private final PreparedStatement insertClinicByGeohash;
    private final PreparedStatement insertClinicByLocationText;
    private final PreparedStatement selectClinicById;
    private final PreparedStatement selectClinicByName;
    private final PreparedStatement selectIdByLocationText;
    private final PreparedStatement selectIdByGeohash;
    private final PreparedStatement selectByGeohash;
    private final PreparedStatement selectClinicFromDoctorsByClinic;
    private final PreparedStatement selectAllClinics;

    public ClinicRepositoryImpl(CqlSession session) {
        this.session = session;
        this.insertClinic = session.prepare(
                "INSERT INTO doctor_service.clinics " +
                        "(clinic_id, name, latitude, longitude, geohash, location_text, " +
                        " is_active, is_deleted, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?) IF NOT EXISTS"
        );
        this.insertClinicByGeohash = session.prepare(
                "INSERT INTO doctor_service.clinics_by_geohash " +
                        "(geohash, clinic_id, name, latitude, longitude, is_active, is_deleted) " +
                        "VALUES (?,?,?,?,?,?,?)"
        );
        this.insertClinicByLocationText = session.prepare(
                "INSERT INTO doctor_service.clinics_by_location_text " +
                        "(location_text, clinic_id, name, is_active, is_deleted) " +
                        "VALUES (?,?,?,?,?)"
        );
        this.selectClinicById = session.prepare(
                "SELECT * FROM doctor_service.clinics WHERE clinic_id=?"
        );
        this.selectClinicByName = session.prepare(
                "SELECT * FROM doctor_service.clinics WHERE name=? ALLOW FILTERING"
        );
        this.selectIdByLocationText = session.prepare(
                "SELECT clinic_id FROM doctor_service.clinics_by_location_text " +
                        "WHERE location_text=? LIMIT 1"
        );
        this.selectIdByGeohash = session.prepare(
                "SELECT clinic_id FROM doctor_service.clinics_by_geohash " +
                        "WHERE geohash=? LIMIT 1"
        );
        this.selectByGeohash = session.prepare(
                "SELECT clinic_id, name, latitude, longitude, is_active " +
                        "FROM doctor_service.clinics_by_geohash WHERE geohash=?"
        );
        this.selectClinicFromDoctorsByClinic = session.prepare(
                "SELECT clinic_id, doctor_id FROM doctor_service.doctors_by_clinic WHERE doctor_id=?"
        );
        this.selectAllClinics = session.prepare(
                "SELECT * FROM doctor_service.clinics WHERE is_deleted=false ALLOW FILTERING"
        );
    }

    @Override
    public void save(Clinic c) {
        Location l   = c.getLocation();
        Instant  now = Instant.now();
        String   prefix = l.getGeohash() != null && l.getGeohash().length() >= 6
                ? l.getGeohash().substring(0, 6)
                : l.getGeohash();

        // clinics — canonical row
        session.execute(insertClinic.bind(
                c.getId(), c.getName(),
                l.getLatitude(), l.getLongitude(),
                l.getGeohash(), l.getLocationText(),
                c.isActive(), false, now, now
        ));

        // clinics_by_geohash
        session.execute(insertClinicByGeohash.bind(
                prefix, c.getId(), c.getName(),
                l.getLatitude(), l.getLongitude(), c.isActive(), false
        ));

        // clinics_by_location_text
        session.execute(insertClinicByLocationText.bind(
                l.getLocationText(), c.getId(), c.getName(), c.isActive(), false
        ));
    }

    @Override
    public Optional<Clinic> findById(UUID clinicId) {
        Row r = session.execute(selectClinicById.bind(clinicId)).one();
        if (r == null) return Optional.empty();
        return Optional.of(MapperClass.mapRowToClinic(r));
    }

    @Override
    public Clinic findByName(String name) {
        Row r = session.execute(selectClinicByName.bind(name)).one();
        if (r == null) return null;
        return MapperClass.mapRowToClinic(r);
    }

    @Override
    public Clinic findByLocationText(String locationText) {
        // Use lookup table
        Row lookup = session.execute(selectIdByLocationText.bind(locationText)).one();
        if (lookup == null) return null;
        return findById(lookup.getUuid("clinic_id")).orElse(null);
    }

    @Override
    public Clinic findByLocationGeohash(String geohash) {
        String prefix = geohash.length() >= 6 ? geohash.substring(0, 6) : geohash;
        // Use lookup table
        Row lookup = session.execute(selectIdByGeohash.bind(prefix)).one();
        if (lookup == null) return null;
        return findById(lookup.getUuid("clinic_id")).orElse(null);
    }

    @Override
    public Location getLocation(UUID clinicId) {
        Row r = session.execute(selectClinicById.bind(clinicId)).one();
        if (r == null) return null;
        return MapperClass.mapRowToLocation(r);
    }

    @Override
    public Clinic findDoctorAndClinic(UUID doctorId) {
        Row r = session.execute(selectClinicFromDoctorsByClinic.bind(doctorId)).one();
        if (r == null) return null;
        return r.getUuid("clinic_id") != null ? findById(r.getUuid("clinic_id")).orElse(null) : null;
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
            ResultSet rs = session.execute(selectByGeohash.bind(p));
            for (Row r : rs) {
                double cLat = r.getDouble("latitude");
                double cLon = r.getDouble("longitude");
                double dist = haversineKm(lat, lon, cLat, cLon);
                Clinic clinic = findById(r.getUuid("clinic_id")).orElse(null);
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
        ResultSet rs = session.execute(selectAllClinics.bind());
        
        return rs.all().stream()
                .map(MapperClass::mapRowToClinic)
                .filter(c -> c.getName().toLowerCase().contains(name.toLowerCase()))
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    @Override
    public long countByName(String name) {
        ResultSet rs = session.execute(selectAllClinics.bind());
        return rs.all().stream()
                .map(MapperClass::mapRowToClinic)
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
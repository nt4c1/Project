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
    private final PreparedStatement insertClinicCredentials;
    private final PreparedStatement insertClinicEmailLookup;
    private final PreparedStatement selectClinicIdByEmail;
    private final PreparedStatement selectClinicCredentialsByClinicId;
    private final PreparedStatement updateClinic;
    private final PreparedStatement updateClinicCredentials;
    private final PreparedStatement softDeleteClinic;
    private final PreparedStatement softDeleteClinicByGeohash;
    private final PreparedStatement softDeleteClinicByLocationText;
    private final PreparedStatement deleteClinicByGeohash;
    private final PreparedStatement deleteClinicByLocationText;

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
        this.insertClinicCredentials = session.prepare(
                "INSERT INTO doctor_service.clinic_credentials " +
                        "(clinic_id, email, password_hash, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?) IF NOT EXISTS"
        );
        this.insertClinicEmailLookup = session.prepare(
                "INSERT INTO doctor_service.clinics_by_email (email, clinic_id) " +
                        "VALUES (?,?) IF NOT EXISTS"
        );
        this.selectClinicIdByEmail = session.prepare(
                "SELECT clinic_id FROM doctor_service.clinics_by_email WHERE email=?"
        );
        this.selectClinicCredentialsByClinicId = session.prepare(
                "SELECT * FROM doctor_service.clinic_credentials WHERE clinic_id=?"
        );
        this.updateClinic = session.prepare(
                "UPDATE doctor_service.clinics SET name=?, updated_at=? WHERE clinic_id=?"
        );
        this.updateClinicCredentials = session.prepare(
                "UPDATE doctor_service.clinic_credentials SET password_hash=?, updated_at=? WHERE clinic_id=?"
        );
        this.softDeleteClinic = session.prepare(
                "UPDATE doctor_service.clinics SET is_deleted=true, updated_at=? WHERE clinic_id=?"
        );
        this.softDeleteClinicByGeohash = session.prepare(
                "UPDATE doctor_service.clinics_by_geohash SET is_deleted=true WHERE geohash=? AND clinic_id=?"
        );
        this.softDeleteClinicByLocationText = session.prepare(
                "UPDATE doctor_service.clinics_by_location_text SET is_deleted=true WHERE location_text=? AND clinic_id=?"
        );
        this.deleteClinicByGeohash = session.prepare(
                "DELETE FROM doctor_service.clinics_by_geohash WHERE geohash=? AND clinic_id=?"
        );
        this.deleteClinicByLocationText = session.prepare(
                "DELETE FROM doctor_service.clinics_by_location_text WHERE location_text=? AND clinic_id=?"
        );
    }

    @Override
    public void saveCredentials(com.health.doctor.domain.model.ClinicCredentials creds) {
        Instant now = Instant.now();
        session.execute(insertClinicCredentials.bind(
                creds.getClinicId(), creds.getEmail(),
                creds.getPasswordHash(), now, now
        ));
        session.execute(insertClinicEmailLookup.bind(
                creds.getEmail(), creds.getClinicId()
        ));
    }

    @Override
    public Optional<com.health.doctor.domain.model.ClinicCredentials> findCredentialsByEmail(String email) {
        Row lookup = session.execute(selectClinicIdByEmail.bind(email)).one();
        if (lookup == null) return Optional.empty();

        UUID clinicId = lookup.getUuid("clinic_id");
        Row r = session.execute(selectClinicCredentialsByClinicId.bind(clinicId)).one();
        if (r == null) return Optional.empty();

        return Optional.of(new com.health.doctor.domain.model.ClinicCredentials(
                r.getUuid("clinic_id"),
                r.getString("email"),
                r.getString("password_hash"),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        ));
    }

    @Override
    public void updatePassword(UUID clinicId, String passwordHash) {
        session.execute(updateClinicCredentials.bind
                (passwordHash, Instant.now(), clinicId));
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

    @Override
    public void update(Clinic c) {
        Instant now = Instant.now();
        session.execute(updateClinic.bind(c.getName(), now, c.getId()));

        Location l = c.getLocation();
        String prefix = l.getGeohash() != null && l.getGeohash().length() >= 6
                ? l.getGeohash().substring(0, 6)
                : l.getGeohash();

        // Upsert denormalized tables
        session.execute(insertClinicByGeohash.bind(
                prefix, c.getId(), c.getName(),
                l.getLatitude(), l.getLongitude(), c.isActive(), c.isDeleted()
        ));

        session.execute(insertClinicByLocationText.bind(
                l.getLocationText(), c.getId(), c.getName(), c.isActive(), c.isDeleted()
        ));
    }

    @Override
    public void delete(UUID clinicId) {
        Optional<Clinic> opt = findById(clinicId);
        if (opt.isEmpty()) return;
        Clinic c = opt.get();
        Instant now = Instant.now();

        session.execute(softDeleteClinic.bind(now, clinicId));

        Location l = c.getLocation();
        String prefix = l.getGeohash() != null && l.getGeohash().length() >= 6
                ? l.getGeohash().substring(0, 6)
                : l.getGeohash();

        session.execute(softDeleteClinicByGeohash.bind(prefix, clinicId));
        session.execute(softDeleteClinicByLocationText.bind(l.getLocationText(), clinicId));
    }

    @Override
    public void updateLocation(UUID clinicId, Location newLoc) {
        Optional<Clinic> opt = findById(clinicId);
        if (opt.isEmpty()) return;
        Clinic old = opt.get();
        Instant now = Instant.now();

        Location oldLoc = old.getLocation();
        String oldPrefix = oldLoc.getGeohash() != null && oldLoc.getGeohash().length() >= 6
                ? oldLoc.getGeohash().substring(0, 6)
                : oldLoc.getGeohash();

        // 1. Delete old denormalized entries
        session.execute(deleteClinicByGeohash.bind(oldPrefix, clinicId));
        session.execute(deleteClinicByLocationText.bind(oldLoc.getLocationText(), clinicId));

        // 2. Update main table and insert new denormalized entries
        old.setLocation(newLoc);
        old.setUpdatedAt(now);
        
        // Manual update for main table since save() uses IF NOT EXISTS
        session.execute(session.prepare("UPDATE doctor_service.clinics SET latitude=?, longitude=?, geohash=?, location_text=?, updated_at=? WHERE clinic_id=?")
                .bind(newLoc.getLatitude(), newLoc.getLongitude(), newLoc.getGeohash(), newLoc.getLocationText(), now, clinicId));

        String newPrefix = newLoc.getGeohash() != null && newLoc.getGeohash().length() >= 6
                ? newLoc.getGeohash().substring(0, 6)
                : newLoc.getGeohash();

        session.execute(insertClinicByGeohash.bind(
                newPrefix, clinicId, old.getName(),
                newLoc.getLatitude(), newLoc.getLongitude(), old.isActive(), old.isDeleted()
        ));

        session.execute(insertClinicByLocationText.bind(
                newLoc.getLocationText(), clinicId, old.getName(), old.isActive(), old.isDeleted()
        ));
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
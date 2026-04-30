package com.health.doctor.adapters.output.persistence.repository;

import ch.hsr.geohash.GeoHash;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.fasterxml.jackson.core.type.TypeReference;
import com.health.common.redis.RedisUtil;
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
    private final RedisUtil redisUtil;

    private static final String CACHE_PREFIX       = "clinic:";
    private static final String CACHE_PREFIX_LT    = "clinic:lt:";
    private static final String CACHE_PREFIX_GH    = "clinic:gh:";
    private static final long   TTL                = 3600; // 1h

    // TypeReferences for generic list deserialization (fixes Clinic[].class anti-pattern)
    private static final TypeReference<List<Clinic>> CLINIC_LIST_TYPE = new TypeReference<>() {};

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

    public ClinicRepositoryImpl(CqlSession session, RedisUtil redisUtil) {
        this.session   = session;
        this.redisUtil = redisUtil;

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
                        "WHERE location_text=?"
        );
        this.selectIdByGeohash = session.prepare(
                "SELECT clinic_id FROM doctor_service.clinics_by_geohash " +
                        "WHERE geohash=?"
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

    // -------------------------------------------------------------------------
    // Credentials
    // -------------------------------------------------------------------------

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
        session.execute(updateClinicCredentials.bind(passwordHash, Instant.now(), clinicId));
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    @Override
    public void save(Clinic c) {
        Location l   = c.getLocation();
        Instant  now = Instant.now();
        String   prefix = geohashPrefix(l.getGeohash());

        session.execute(insertClinic.bind(
                c.getId(), c.getName(),
                l.getLatitude(), l.getLongitude(),
                l.getGeohash(), l.getLocationText(),
                c.isActive(), false, now, now
        ));
        session.execute(insertClinicByGeohash.bind(
                prefix, c.getId(), c.getName(),
                l.getLatitude(), l.getLongitude(), c.isActive(), false
        ));
        session.execute(insertClinicByLocationText.bind(
                l.getLocationText(), c.getId(), c.getName(), c.isActive(), false
        ));

        // Invalidate any stale location-index caches that cover this clinic
        redisUtil.deleteAsync(CACHE_PREFIX_LT + l.getLocationText());
        redisUtil.deleteAsync(CACHE_PREFIX_GH + prefix);
    }

    @Override
    public void update(Clinic c) {
        Instant  now    = Instant.now();
        Location l      = c.getLocation();
        String   prefix = geohashPrefix(l.getGeohash());

        session.execute(updateClinic.bind(c.getName(), now, c.getId()));
        session.execute(insertClinicByGeohash.bind(
                prefix, c.getId(), c.getName(),
                l.getLatitude(), l.getLongitude(), c.isActive(), c.isDeleted()
        ));
        session.execute(insertClinicByLocationText.bind(
                l.getLocationText(), c.getId(), c.getName(), c.isActive(), c.isDeleted()
        ));

        // Update the clinic's own cache entry and bust location-index caches
        redisUtil.setAsync(CACHE_PREFIX + c.getId(), c, TTL);
        redisUtil.deleteAsync(CACHE_PREFIX_LT + l.getLocationText());
        redisUtil.deleteAsync(CACHE_PREFIX_GH + prefix);
    }

    @Override
    public void delete(UUID clinicId) {
        Optional<Clinic> opt = findById(clinicId);
        if (opt.isEmpty()) return;
        Clinic  c      = opt.get();
        Instant now    = Instant.now();
        String  prefix = geohashPrefix(c.getLocation().getGeohash());

        session.execute(softDeleteClinic.bind(now, clinicId));
        session.execute(softDeleteClinicByGeohash.bind(prefix, clinicId));
        session.execute(softDeleteClinicByLocationText.bind(c.getLocation().getLocationText(), clinicId));

        redisUtil.deleteAsync(CACHE_PREFIX + clinicId);
        redisUtil.deleteAsync(CACHE_PREFIX_LT + c.getLocation().getLocationText());
        redisUtil.deleteAsync(CACHE_PREFIX_GH + prefix);
    }

    @Override
    public void updateLocation(UUID clinicId, Location newLoc) {
        Optional<Clinic> opt = findById(clinicId);
        if (opt.isEmpty()) return;
        Clinic  old       = opt.get();
        Instant now       = Instant.now();
        Location oldLoc   = old.getLocation();
        String  oldPrefix = geohashPrefix(oldLoc.getGeohash());
        String  newPrefix = geohashPrefix(newLoc.getGeohash());

        // Remove old denormalized entries
        session.execute(deleteClinicByGeohash.bind(oldPrefix, clinicId));
        session.execute(deleteClinicByLocationText.bind(oldLoc.getLocationText(), clinicId));

        // Update main table
        session.execute(session.prepare(
                "UPDATE doctor_service.clinics SET latitude=?, longitude=?, geohash=?, location_text=?, updated_at=? WHERE clinic_id=?"
        ).bind(newLoc.getLatitude(), newLoc.getLongitude(), newLoc.getGeohash(), newLoc.getLocationText(), now, clinicId));

        // Insert new denormalized entries
        session.execute(insertClinicByGeohash.bind(
                newPrefix, clinicId, old.getName(),
                newLoc.getLatitude(), newLoc.getLongitude(), old.isActive(), old.isDeleted()
        ));
        session.execute(insertClinicByLocationText.bind(
                newLoc.getLocationText(), clinicId, old.getName(), old.isActive(), old.isDeleted()
        ));

        old.setLocation(newLoc);
        old.setUpdatedAt(now);

        // Bust all affected cache entries
        redisUtil.setAsync(CACHE_PREFIX + clinicId, old, TTL);
        redisUtil.deleteAsync(CACHE_PREFIX_LT + oldLoc.getLocationText());
        redisUtil.deleteAsync(CACHE_PREFIX_LT + newLoc.getLocationText());
        redisUtil.deleteAsync(CACHE_PREFIX_GH + oldPrefix);
        redisUtil.deleteAsync(CACHE_PREFIX_GH + newPrefix);
    }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    @Override
    public Optional<Clinic> findById(UUID clinicId) {
        String cacheKey = CACHE_PREFIX + clinicId;
        Clinic cached = redisUtil.get(cacheKey, Clinic.class);
        if (cached != null) return Optional.of(cached);

        Row r = session.execute(selectClinicById.bind(clinicId)).one();
        if (r == null) return Optional.empty();

        Clinic clinic = MapperClass.mapRowToClinic(r);
        redisUtil.setAsync(cacheKey, clinic, TTL);
        return Optional.of(clinic);
    }

    @Override
    public Clinic findByName(String name) {
        Row r = session.execute(selectClinicByName.bind(name)).one();
        if (r == null) return null;
        return MapperClass.mapRowToClinic(r);
    }

    @Override
    public List<Clinic> findByLocationText(String locationText) {
        String cacheKey = CACHE_PREFIX_LT + locationText;

        // Fix: use TypeReference<List<Clinic>> instead of Clinic[].class
        List<Clinic> cached = redisUtil.get(cacheKey, CLINIC_LIST_TYPE);
        if (cached != null) return cached;

        ResultSet rs = session.execute(selectIdByLocationText.bind(locationText));
        List<Clinic> list = new ArrayList<>();
        for (Row row : rs) {
            findById(row.getUuid("clinic_id")).ifPresent(list::add);
        }

        redisUtil.setAsync(cacheKey, list, TTL);
        return list;
    }

    @Override
    public List<Clinic> findByLocationGeohash(String geohash) {
        String prefix   = geohashPrefix(geohash);
        String cacheKey = CACHE_PREFIX_GH + prefix;

        // Fix: use TypeReference<List<Clinic>> instead of Clinic[].class
        List<Clinic> cached = redisUtil.get(cacheKey, CLINIC_LIST_TYPE);
        if (cached != null) return cached;

        ResultSet rs = session.execute(selectIdByGeohash.bind(prefix));
        List<Clinic> list = new ArrayList<>();
        for (Row row : rs) {
            findById(row.getUuid("clinic_id")).ifPresent(list::add);
        }

        redisUtil.setAsync(cacheKey, list, TTL);
        return list;
    }

    @Override
    public Location getLocation(UUID clinicId) {
        // Fix: reuse findById which is already cached — no separate Cassandra call needed
        return findById(clinicId)
                .map(Clinic::getLocation)
                .orElse(null);
    }

    @Override
    public Clinic findDoctorAndClinic(UUID doctorId) {
        Row r = session.execute(selectClinicFromDoctorsByClinic.bind(doctorId)).one();
        if (r == null) return null;
        UUID clinicId = r.getUuid("clinic_id");
        return clinicId != null ? findById(clinicId).orElse(null) : null;
    }

    @Override
    public List<Clinic> findNearby(String geohash, double lat, double lon) {
        for (int precision = 6; precision >= 4; precision--) {
            String prefix = geohash.substring(0, Math.min(geohash.length(), precision));
            List<Clinic> found = searchClinicsInPrefix(prefix, lat, lon);
            if (!found.isEmpty()) return found;
        }
        return Collections.emptyList();
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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<Clinic> searchClinicsInPrefix(String prefix, double lat, double lon) {
        GeoHash   center    = GeoHash.fromGeohashString(prefix);
        GeoHash[] neighbors = center.getAdjacent();

        Set<String> prefixes = new HashSet<>();
        prefixes.add(prefix);
        for (GeoHash n : neighbors) prefixes.add(n.toBase32().substring(0, prefix.length()));

        record ClinicWithDistance(Clinic clinic, double distanceKm) {}
        List<ClinicWithDistance> candidates = new ArrayList<>();

        for (String p : prefixes) {
            ResultSet rs = session.execute(selectByGeohash.bind(p));
            for (Row r : rs) {
                double cLat  = r.getDouble("latitude");
                double cLon  = r.getDouble("longitude");
                double dist  = haversineKm(lat, lon, cLat, cLon);
                Clinic clinic = findById(r.getUuid("clinic_id")).orElse(null);
                if (clinic != null && clinic.isActive()) {
                    candidates.add(new ClinicWithDistance(clinic, dist));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(ClinicWithDistance::distanceKm));
        return candidates.stream().map(ClinicWithDistance::clinic).collect(Collectors.toList());
    }

    /** Returns a 6-char geohash prefix, or the full hash if shorter than 6. */
    private static String geohashPrefix(String geohash) {
        return (geohash != null && geohash.length() >= 6) ? geohash.substring(0, 6) : geohash;
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
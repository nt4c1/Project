package com.health.doctor.adapters.output.persistence.repository;

import ch.hsr.geohash.GeoHash;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.common.exception.BookingException;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import com.health.doctor.domain.ports.ScheduleRepositoryPort;
import com.health.common.redis.RedisUtil;
import com.health.doctor.mapper.MapperClass;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.*;

@Slf4j
@Singleton
public class DoctorRepositoryImpl implements DoctorRepositoryPort {

    public static final UUID NO_CLINIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final String CACHE_DOCTOR_PREFIX = "doctor:";
    private static final String CACHE_LOCATION_PREFIX = "doctor-location:";
    private static final long TTL_DOCTOR = 3600; // 1h
    private static final long TTL_LOCATION = 600; // 10m

    private final CqlSession session;
    private final RedisUtil redisUtil;
    private final Provider<AppointmentRepositoryPort> appointmentRepoProvider;
    private final ScheduleRepositoryPort scheduleRepo;

    // ── Prepared statements ────────────────────────
    private final PreparedStatement insertDoctor;
    private final PreparedStatement insertDoctorByIndividual;
    private final PreparedStatement insertDoctorByClinic;
    private final PreparedStatement insertDoctorByLocation;
    private final PreparedStatement selectDoctorById;
    private final PreparedStatement selectDoctorProfileForLocation;
    private final PreparedStatement selectByGeohash;
    private final PreparedStatement selectByClinic;
    private final PreparedStatement updateDoctorCredentials;
    private final PreparedStatement updateDoctorUpdatedAt;
    private final PreparedStatement updateDoctorIndividual;
    private final PreparedStatement updateDoctorClinic;
    private final PreparedStatement deleteDoctorByIndividual;
    private final PreparedStatement selectAppointmentByDoctorStatus;
    private final PreparedStatement selectAppointmentsByDoctor;
    private final PreparedStatement selectGeoPrefix;
    private final PreparedStatement softDeleteDoctor;
    private final PreparedStatement softDeleteDoctorClinic;
    private final PreparedStatement softDeleteDoctorIndividual;
    private final PreparedStatement softDeleteDoctorLocation;
    private final PreparedStatement selectGeoPrefixByDoctor;
    private final PreparedStatement selectDoctorByIdWithDeleted;
    private final PreparedStatement reactivateDoctor;
    private final PreparedStatement reactivateDoctorIndividual;
    private final PreparedStatement reactivateDoctorClinic;
    // ─────────────────────────────────────────────



    public DoctorRepositoryImpl(CqlSession session, RedisUtil redisUtil, Provider<AppointmentRepositoryPort> appointmentRepoProvider, ScheduleRepositoryPort scheduleRepo) {
        this.session = session;
        this.redisUtil = redisUtil;
        this.appointmentRepoProvider = appointmentRepoProvider;
        this.scheduleRepo = scheduleRepo;

        // doctors — canonical profile
        insertDoctor = session.prepare(
                insertInto("doctor_service", "doctors")
                        .value("doctor_id", bindMarker("doctor_id"))
                        .value("name", bindMarker("name"))
                        .value("clinic_ids", bindMarker("clinic_ids"))
                        .value("type", bindMarker("type"))
                        .value("specialization", bindMarker("specialization"))
                        .value("phone", bindMarker("phone"))
                        .value("is_active", bindMarker("is_active"))
                        .value("is_deleted", bindMarker("is_deleted"))
                        .value("created_at", bindMarker("created_at"))
                        .value("updated_at", bindMarker("updated_at"))
                        .build()
        );

        // doctors_by_individual — standalone doctor read model
        insertDoctorByIndividual = session.prepare(
                insertInto("doctor_service", "doctors_by_individual")
                        .value("doctor_id", bindMarker("doctor_id"))
                        .value("name", bindMarker("name"))
                        .value("type", bindMarker("type"))
                        .value("specialization", bindMarker("specialization"))
                        .value("phone", bindMarker("phone"))
                        .value("is_active", bindMarker("is_active"))
                        .value("is_deleted", bindMarker("is_deleted"))
                        .value("updated_at", bindMarker("updated_at"))
                        .build()
        );

        // doctors_by_clinic — clinic roster read model
        insertDoctorByClinic = session.prepare(
                insertInto("doctor_service", "doctors_by_clinic")
                        .value("clinic_id", bindMarker("clinic_id"))
                        .value("doctor_id", bindMarker("doctor_id"))
                        .value("name", bindMarker("name"))
                        .value("type", bindMarker("type"))
                        .value("specialization", bindMarker("specialization"))
                        .value("phone", bindMarker("phone"))
                        .value("is_active", bindMarker("is_active"))
                        .value("is_deleted", bindMarker("is_deleted"))
                        .value("created_at", bindMarker("created_at"))
                        .value("updated_at", bindMarker("updated_at"))
                        .build()
        );

        // doctors_by_location — geohash-based location index
        insertDoctorByLocation = session.prepare(
                insertInto("doctor_service", "doctors_by_location")
                        .value("geohash_prefix", bindMarker("geohash_prefix"))
                        .value("geohash_prefixes", bindMarker("geohash_prefixes"))
                        .value("clinic_id", bindMarker("clinic_id"))
                        .value("doctor_id", bindMarker("doctor_id"))
                        .value("name", bindMarker("name"))
                        .value("type", bindMarker("type"))
                        .value("specialization", bindMarker("specialization"))
                        .value("phone", bindMarker("phone"))
                        .value("latitude", bindMarker("latitude"))
                        .value("longitude", bindMarker("longitude"))
                        .value("location_text", bindMarker("location_text"))
                        .value("is_active", bindMarker("is_active"))
                        .value("is_deleted", bindMarker("is_deleted"))
                        .build()
        );

        // SELECT from doctors by PK
        selectDoctorById = session.prepare(
                selectFrom("doctor_service", "doctors")
                        .all()
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .whereColumn("is_deleted").isEqualTo(literal(false))
                        .allowFiltering()
                        .build()
        );

        selectDoctorByIdWithDeleted = session.prepare(
                selectFrom("doctor_service", "doctors")
                        .all()
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        reactivateDoctor = session.prepare(
                update("doctor_service", "doctors")
                        .setColumn("name", bindMarker("name"))
                        .setColumn("clinic_ids", bindMarker("clinic_ids"))
                        .setColumn("type", bindMarker("type"))
                        .setColumn("specialization", bindMarker("specialization"))
                        .setColumn("phone", bindMarker("phone"))
                        .setColumn("is_active", bindMarker("is_active"))
                        .setColumn("is_deleted", literal(false))
                        .setColumn("updated_at", bindMarker("updated_at"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        reactivateDoctorIndividual = session.prepare(
                update("doctor_service", "doctors_by_individual")
                        .setColumn("name", bindMarker("name"))
                        .setColumn("type", bindMarker("type"))
                        .setColumn("specialization", bindMarker("specialization"))
                        .setColumn("phone", bindMarker("phone"))
                        .setColumn("is_active", bindMarker("is_active"))
                        .setColumn("updated_at", bindMarker("updated_at"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        reactivateDoctorClinic = session.prepare(
                update("doctor_service", "doctors_by_clinic")
                        .setColumn("name", bindMarker("name"))
                        .setColumn("type", bindMarker("type"))
                        .setColumn("specialization", bindMarker("specialization"))
                        .setColumn("phone", bindMarker("phone"))
                        .setColumn("is_active", bindMarker("is_active"))
                        .setColumn("is_deleted", literal(false))
                        .setColumn("updated_at", bindMarker("updated_at"))
                        .whereColumn("clinic_id").isEqualTo(bindMarker("clinic_id"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        // SELECT name/specialization/clinic_ids from doctors (for location denormalization)
        selectDoctorProfileForLocation = session.prepare(
                selectFrom("doctor_service", "doctors")
                        .columns("name", "type", "specialization", "phone", "clinic_ids")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .whereColumn("is_deleted").isEqualTo(literal(false))
                        .allowFiltering()
                        .build()
        );

        // SELECT from doctors_by_location by geohash prefix
        selectByGeohash = session.prepare(
                "SELECT doctor_id, clinic_id, name, type, specialization, phone, latitude, longitude, is_active " +
                        "FROM doctor_service.doctors_by_location " +
                        "WHERE geohash_prefixes CONTAINS ? AND is_active = ? AND is_deleted = false ALLOW FILTERING"
        );

        // SELECT from doctors_by_clinic by clinic_id
        selectByClinic = session.prepare(
                selectFrom("doctor_service", "doctors_by_clinic")
                        .all()
                        .whereColumn("clinic_id").isEqualTo(bindMarker("clinic_id"))
                        .whereColumn("is_active").isEqualTo(bindMarker("is_active"))
                        .whereColumn("is_deleted").isEqualTo(literal(false))
                        .allowFiltering()
                        .build()
        );


        //SELECT from appointments_by_doctor_status (for appointment checking)
        selectAppointmentByDoctorStatus = session.prepare(
                selectFrom("doctor_service", "appointments_by_doctor_status")
                        .column("status")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .allowFiltering()
                        .build()
        );

        //SELECT from appointments_by_doctor (for all appointments)
        selectAppointmentsByDoctor = session.prepare(
                selectFrom("doctor_service", "appointments_by_doctor")
                        .columns("appointment_id", "appointment_date", "scheduled_time", "patient_id", "status")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .allowFiltering()
                        .build()
        );

        //UPDATE doctor_credentials
        updateDoctorCredentials = session.prepare(
                update("doctor_service", "doctor_credentials")
                        .setColumn("email", bindMarker("email"))
                        .setColumn("phone", bindMarker("phone"))
                        .setColumn("password_hash", bindMarker("password_hash"))
                        .setColumn("updated_at", bindMarker("updated_at"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        //Update doctor(updated_at)
        updateDoctorUpdatedAt = session.prepare(
                update("doctor_service", "doctors")
                        .setColumn("updated_at", bindMarker("updated_at"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        //Update doctor(updated_at) for individual
        updateDoctorIndividual = session.prepare(
                update("doctor_service", "doctors_by_individual")
                        .setColumn("updated_at", bindMarker("updated_at"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        //Update doctor(updated_at) for clinic
        updateDoctorClinic = session.prepare(
                update("doctor_service", "doctors_by_clinic")
                        .setColumn("updated_at", bindMarker("updated_at"))
                        .whereColumn("clinic_id").isEqualTo(bindMarker("clinic_id"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        deleteDoctorByIndividual = session.prepare(
                deleteFrom("doctor_service", "doctors_by_individual")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        //SELECT from doctors_location
        selectGeoPrefix = session.prepare(
                selectFrom("doctor_service", "doctors_by_location")
                        .column("geohash_prefix")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .whereColumn("clinic_id").isEqualTo(bindMarker("clinic_id"))
                        .allowFiltering()
                        .build()
        );

        softDeleteDoctor = session.prepare(
                update("doctor_service", "doctors")
                        .setColumn("is_deleted", literal(true))
                        .setColumn("updated_at", bindMarker("updated_at"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        softDeleteDoctorClinic = session.prepare(
                update("doctor_service", "doctors_by_clinic")
                        .setColumn("is_deleted", literal(true))
                        .setColumn("updated_at", bindMarker("updated_at"))
                        .whereColumn("clinic_id").isEqualTo(bindMarker("clinic_id"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        softDeleteDoctorIndividual = session.prepare(
                update("doctor_service", "doctors_by_individual")
                        .setColumn("is_deleted", literal(true))
                        .setColumn("updated_at", bindMarker("updated_at"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        softDeleteDoctorLocation = session.prepare(
                update("doctor_service", "doctors_by_location")
                        .setColumn("is_deleted", literal(true))
                        .whereColumn("geohash_prefix").isEqualTo(bindMarker("geohash_prefix"))
                        .whereColumn("clinic_id").isEqualTo(bindMarker("clinic_id"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        //For Delete
        selectGeoPrefixByDoctor = session.prepare(
                selectFrom("doctor_service", "doctors_by_location")
                        .columns("geohash_prefix", "clinic_id")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .allowFiltering()
                        .build()
        );
    }


    // ── Save ──────────────────────────────────────────────────────────────────

    @Override
    public void save(Doctor d) {
        Instant now = Instant.now();
        String cacheKey = CACHE_DOCTOR_PREFIX + d.getId();
        redisUtil.deleteAsync(cacheKey);

        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(insertDoctor.bind()
                        .setUuid("doctor_id", d.getId())
                        .setString("name", d.getName())
                        .setList("clinic_ids", d.getClinicIds(), UUID.class)
                        .setString("type", d.getType().name())
                        .setString("specialization", d.getSpecialization())
                        .setString("phone", d.getPhone())
                        .setBoolean("is_active", d.isActive())
                        .setBoolean("is_deleted", false)
                        .setInstant("created_at", now)
                        .setInstant("updated_at", now));

        if (d.getClinicIds() == null || d.getClinicIds().isEmpty()) {
            batch.addStatement(insertDoctorByIndividual.bind()
                    .setUuid("doctor_id", d.getId())
                    .setString("name", d.getName())
                    .setString("type", d.getType().name())
                    .setString("specialization", d.getSpecialization())
                    .setString("phone", d.getPhone())
                    .setBoolean("is_active", d.isActive())
                    .setBoolean("is_deleted", false)
                    .setInstant("updated_at", now));
        } else {
            for (UUID clinicId : d.getClinicIds()) {
                batch.addStatement(insertDoctorByClinic.bind()
                        .setUuid("clinic_id", clinicId)
                        .setUuid("doctor_id", d.getId())
                        .setString("name", d.getName())
                        .setString("type", d.getType().name())
                        .setString("specialization", d.getSpecialization())
                        .setString("phone", d.getPhone())
                        .setBoolean("is_active", d.isActive())
                        .setBoolean("is_deleted", false)
                        .setInstant("created_at", now)
                        .setInstant("updated_at", now));
            }
        }

        session.execute(batch.build());
        redisUtil.setAsync(cacheKey, d, TTL_DOCTOR);
    }

    // ── Location ──────────────────────────────────────────────────────────────

    @Override
    public void updateLocation(UUID doctorId, UUID clinicId, Location location) {
        clearLocationCache();

        UUID effectiveClinicId = (clinicId !=null) ? clinicId : NO_CLINIC_ID;
        String fullGeohash = location.getGeohash();

        List<String> prefixes = new ArrayList<>();
        for (int precision = 4; precision <= 6; precision++) {
            prefixes.add(fullGeohash.substring(0, precision));
        }

        // 1. Find and delete old geohash entry for this doctor/clinic combo
        ResultSet rs = session.execute(selectGeoPrefix.bind()
                .setUuid("doctor_id", doctorId)
                .setUuid("clinic_id", effectiveClinicId));
        
        BatchStatementBuilder deleteBatch = BatchStatement.builder(DefaultBatchType.LOGGED);
        boolean hasOld = false;
        for (Row row : rs) {
            String oldPrefix = row.getString("geohash_prefix");
            deleteBatch.addStatement(softDeleteDoctorLocation.bind()
                    .setString("geohash_prefix", oldPrefix)
                    .setUuid("clinic_id", effectiveClinicId)
                    .setUuid("doctor_id", doctorId));
            hasOld = true;
        }
        if (hasOld) session.execute(deleteBatch.build());

        // 2. Fetch profile info for denormalization
        Row profileRow = session.execute(
                selectDoctorProfileForLocation.bind()
                        .setUuid("doctor_id", doctorId)
        ).one();

        if (profileRow == null) {
            log.warn("Cannot update location: doctor profile not found for {}", doctorId);
            return;
        }

        // 3. Insert new entry
        session.execute(insertDoctorByLocation.bind()
                .setString("geohash_prefix", fullGeohash.substring(0,6))
                .setList("geohash_prefixes", prefixes, String.class)
                .setUuid("clinic_id", effectiveClinicId)
                .setUuid("doctor_id", doctorId)
                .setString("name", profileRow.getString("name"))
                .setString("type", profileRow.getString("type"))
                .setString("specialization", profileRow.getString("specialization"))
                .setString("phone", profileRow.getString("phone"))
                .setDouble("latitude", location.getLatitude())
                .setDouble("longitude", location.getLongitude())
                .setString("location_text", location.getLocationText())
                .setBoolean("is_active", true)
                .setBoolean("is_deleted", false)
        );

        String cacheKey = CACHE_LOCATION_PREFIX + "nb:" + fullGeohash + ":" + location.getLatitude() + ":" + location.getLongitude();
        redisUtil.setAsync(cacheKey, location, TTL_LOCATION);
        log.info("Location updated for doctor {}", doctorId);

    }
    
    // ── Reads ─────────────────────────────────────────────────────────────────

    @Override
    public Optional<Doctor> findById(UUID doctorId) {
        String cacheKey = CACHE_DOCTOR_PREFIX + doctorId;
        try {
            Doctor cached = redisUtil.get(cacheKey, Doctor.class);
            if (cached != null) return Optional.of(cached);
        } catch (Exception e) {
            log.warn("Redis GET failed for findById: {}", e.getMessage());
        }

        Row r = session.execute(
                selectDoctorById.bind()
                        .setUuid("doctor_id", doctorId)
        ).one();
        log.debug("Database hit for findById: {}", doctorId);
        if (r == null) return Optional.empty();

        Doctor doctor = MapperClass.mapProfileRow(r);
        redisUtil.setAsync(cacheKey, doctor, TTL_DOCTOR);
        return Optional.of(doctor);
    }

    @Override
    public List<Doctor> findByGeohashPrefix(String prefix) {
        String cacheKey = CACHE_LOCATION_PREFIX + "gh:" + prefix;
        try {
            Doctor[] cached = redisUtil.get(cacheKey, Doctor[].class);
            if (cached != null) return Arrays.asList(cached);
        } catch (Exception e) {
            log.warn("Redis GET failed for findByGeohashPrefix: {}", e.getMessage());
        }

        ResultSet rs = session.execute(
                selectByGeohash.bind(prefix,true)
        );

        List<Doctor> list = new ArrayList<>();
        for (Row r : rs) {
            String typeStr = r.getString("type");
            DoctorType type = typeStr != null ? DoctorType.valueOf(typeStr) : null;
            list.add(MapperClass.mapLocationRow(r, type, 0.0));
        }

        redisUtil.setAsync(cacheKey, list, TTL_LOCATION);
        return list;
    }

    @Override
    public List<Doctor> findNearby(String geohash, double lat, double lon) {
        String cacheKey = CACHE_LOCATION_PREFIX + "nb:" + geohash + ":" + lat + ":" + lon;
        try {
            Doctor[] cached = redisUtil.get(cacheKey, Doctor[].class);
            if (cached != null) return Arrays.asList(cached);
        } catch (Exception e) {
            log.warn("Redis GET failed for findNearby: {}", e.getMessage());
        }

        Map<UUID, Doctor> allFound = new LinkedHashMap<>();
        for (int precision = 6; precision >= 4; precision--) {
            String prefix = geohash.substring(0, Math.min(geohash.length(), precision));
            List<Doctor> found = searchInPrefixAndNeighbors(prefix, lat, lon);
            for (Doctor d : found) {
                allFound.putIfAbsent(d.getId(), d);
            }
        }
        List<Doctor> sorted = allFound.values().stream()
                .sorted(Comparator.comparingDouble(Doctor::getDistance))
                .toList();

        redisUtil.setAsync(cacheKey, sorted, TTL_LOCATION);
        return sorted;
    }


    private List<Doctor> searchInPrefixAndNeighbors(String prefix, double lat, double lon) {
        GeoHash center = GeoHash.fromGeohashString(prefix);
        GeoHash[] neighbors = center.getAdjacent();

        Set<String> prefixes = new HashSet<>();
        prefixes.add(prefix);
        for (GeoHash neighbor : neighbors) {
            prefixes.add(neighbor.toBase32().substring(0, prefix.length()));
        }

        record DoctorWithDistance(Doctor doctor, double distanceKm) {}
        Map<UUID, DoctorWithDistance> seen = new LinkedHashMap<>();

        for (String p : prefixes) {
            ResultSet rs = session.execute(
                    selectByGeohash.bind(p,true)
            );
            for (Row r : rs) {
                UUID doctorId = r.getUuid("doctor_id");
                if(seen.containsKey(doctorId)) continue;

                double dLat = r.getDouble("latitude");
                double dLon = r.getDouble("longitude");
                double dist = haversineKm(lat, lon, dLat, dLon);

                String typeStr = r.getString("type");
                DoctorType type = typeStr != null ? DoctorType.valueOf(typeStr) : null;
                seen.put(doctorId, new DoctorWithDistance(MapperClass.mapLocationRow(r, type, dist), dist));
            }
        }

        return seen.values().stream()
                .sorted(Comparator.comparingDouble(DoctorWithDistance::distanceKm))
                .map(DoctorWithDistance::doctor)
                .toList();
    }

    @Override
    public List<Doctor> findByClinicId(UUID clinicId) {
        ResultSet rs = session.execute(
                selectByClinic.bind()
                        .setUuid("clinic_id", clinicId)
                        .setBoolean("is_active", true)
        );
        List<Doctor> list = new ArrayList<>();
        for (Row r : rs) {
            list.add(new Doctor(
                    r.getUuid("doctor_id"),
                    r.getString("name"),
                    Collections.singletonList(clinicId),
                    DoctorType.valueOf(r.getString("type")),
                    r.getString("specialization"),
                    r.getString("phone"),
                    r.getBoolean("is_active")
            ));
        }
        return list;
    }

    @Override
    public void updateDoctor(UUID doctorId, String email, String password, String phone) {
        redisUtil.deleteAsync(CACHE_DOCTOR_PREFIX + doctorId);
        Instant now = Instant.now();
        String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());

        Row r = session.execute(
                selectDoctorById.bind()
                        .setUuid("doctor_id", doctorId)
        ).one();
        List<UUID> clinicIds = r != null ? r.getList("clinic_ids", UUID.class) : null;

        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(updateDoctorCredentials.bind()
                        .setString("email", email)
                        .setString("phone", phone)
                        .setString("password_hash", passwordHash)
                        .setInstant("updated_at", now)
                        .setUuid("doctor_id", doctorId))
                .addStatement(updateDoctorUpdatedAt.bind()
                        .setInstant("updated_at", now)
                        .setUuid("doctor_id", doctorId));

        if (clinicIds == null || clinicIds.isEmpty()) {
            batch.addStatement(updateDoctorIndividual.bind()
                    .setInstant("updated_at", now)
                    .setUuid("doctor_id", doctorId));
        } else {
            for (UUID clinicId : clinicIds) {
                batch.addStatement(updateDoctorClinic.bind()
                        .setInstant("updated_at", now)
                        .setUuid("clinic_id", clinicId)
                        .setUuid("doctor_id", doctorId));
            }
        }
        session.execute(batch.build());

        findById(doctorId).ifPresent(updated ->
                redisUtil.setAsync(CACHE_DOCTOR_PREFIX + doctorId, updated, TTL_DOCTOR));
    }

    @Override
    public void deleteDoctor(UUID doctorId, String email, String password) {
        Instant now = Instant.now();

        ResultSet rs = session.execute(
                selectAppointmentsByDoctor.bind()
                        .setUuid("doctor_id", doctorId)
        );

        for (Row r : rs) {
            String status = r.getString("status");
            if (AppointmentStatus.APPOINTMENT_STATUS_ACCEPTED.name().equals(status) || 
                AppointmentStatus.APPOINTMENT_STATUS_POSTPONED.name().equals(status)) {
                throw new BookingException("Cannot delete doctor with Accepted or Postponed appointments");
            }
        }

        Row doc = session.execute(selectDoctorById.bind().setUuid("doctor_id", doctorId)).one();
        List<UUID> clinicIds = doc != null ? doc.getList("clinic_ids", UUID.class) : null;

        ResultSet locs = session.execute(selectGeoPrefixByDoctor.bind().setUuid("doctor_id", doctorId));

        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(softDeleteDoctor.bind()
                        .setInstant("updated_at", now)
                        .setUuid("doctor_id", doctorId));

        if (clinicIds == null || clinicIds.isEmpty()) {
            batch.addStatement(softDeleteDoctorIndividual.bind()
                    .setInstant("updated_at", now)
                    .setUuid("doctor_id", doctorId));
        } else {
            for (UUID clinicId : clinicIds) {
                batch.addStatement(softDeleteDoctorClinic.bind()
                        .setInstant("updated_at", now)
                        .setUuid("clinic_id", clinicId)
                        .setUuid("doctor_id", doctorId));
            }
        }

        for (Row row : locs) {
            batch.addStatement(softDeleteDoctorLocation.bind()
                    .setString("geohash_prefix", row.getString("geohash_prefix"))
                    .setUuid("clinic_id", row.getUuid("clinic_id"))
                    .setUuid("doctor_id", doctorId));
        }

        session.execute(batch.build());

        // Hard delete the schedule
        scheduleRepo.hardDeleteByDoctor(doctorId);

        // Update all associated appointments status to DELETED
        appointmentRepoProvider.get().deleteAppointmentsByDoctor(doctorId);

        redisUtil.deleteAsync(CACHE_DOCTOR_PREFIX + doctorId);
        clearLocationCache();
    }

    @Override
    public boolean isDeleted(UUID doctorId) {
        Row r = session.execute(selectDoctorByIdWithDeleted.bind().setUuid("doctor_id", doctorId)).one();
        return r != null && r.getBoolean("is_deleted");
    }

    @Override
    public void reactivate(Doctor d) {
        Instant now = Instant.now();
        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(reactivateDoctor.bind()
                        .setUuid("doctor_id", d.getId())
                        .setString("name", d.getName())
                        .setList("clinic_ids", d.getClinicIds(), UUID.class)
                        .setString("type", d.getType().name())
                        .setString("specialization", d.getSpecialization())
                        .setString("phone", d.getPhone())
                        .setBoolean("is_active", d.isActive())
                        .setInstant("updated_at", now));

        if (d.getClinicIds() == null || d.getClinicIds().isEmpty()) {
            batch.addStatement(reactivateDoctorIndividual.bind()
                    .setUuid("doctor_id", d.getId())
                    .setString("name", d.getName())
                    .setString("type", d.getType().name())
                    .setString("specialization", d.getSpecialization())
                    .setString("phone", d.getPhone())
                    .setBoolean("is_active", d.isActive())
                    .setInstant("updated_at", now));
        } else {
            for (UUID clinicId : d.getClinicIds()) {
                batch.addStatement(reactivateDoctorClinic.bind()
                        .setUuid("clinic_id", clinicId)
                        .setUuid("doctor_id", d.getId())
                        .setString("name", d.getName())
                        .setString("type", d.getType().name())
                        .setString("specialization", d.getSpecialization())
                        .setString("phone", d.getPhone())
                        .setBoolean("is_active", d.isActive())
                        .setInstant("updated_at", now));
            }
        }
        session.execute(batch.build());
        redisUtil.deleteAsync(CACHE_DOCTOR_PREFIX + d.getId());
    }

    @Override
    public List<UUID> findClinicIds(UUID doctorId) {
        Row r = session.execute(selectDoctorById.bind()
                .setUuid("doctor_id", doctorId)
        ).one();
        return r != null ? r.getList("clinic_ids", UUID.class) : Collections.emptyList();
    }

    @Override
    public void addClinicId(UUID doctorId, UUID clinicId) {
        Optional<Doctor> docOpt = findById(doctorId);
        if (docOpt.isEmpty()) return;
        Doctor d = docOpt.get();

        Instant now = Instant.now();
        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED);

        //Update primary doctor table
        batch.addStatement(
                update("doctor_service", "doctors")
                        .append("clinic_ids", literal(Collections.singletonList(clinicId)))
                        .setColumn("type", literal(DoctorType.DOCTOR_TYPE_CLINIC_DOCTOR.name()))
                        .setColumn("updated_at", literal(now))
                        .whereColumn("doctor_id").isEqualTo(literal(doctorId))
                        .build()
        );

        // If individual doctor, delete from doctors_by_individual
        if (d.getClinicIds() == null || d.getClinicIds().isEmpty()) {
            batch.addStatement(deleteDoctorByIndividual.bind()
                    .setUuid("doctor_id", doctorId));
        }

        // Add to doctors_by_clinic
        batch.addStatement(insertDoctorByClinic.bind()
                .setUuid("clinic_id", clinicId)
                .setUuid("doctor_id", d.getId())
                .setString("name", d.getName())
                .setString("type", d.getType().name())
                .setString("specialization", d.getSpecialization())
                .setString("phone", d.getPhone())
                .setBoolean("is_active", d.isActive())
                .setBoolean("is_deleted", false)
                .setInstant("created_at", now)
                .setInstant("updated_at", now));

        session.execute(batch.build());
        redisUtil.deleteAsync(CACHE_DOCTOR_PREFIX + doctorId);
    }

    @Override
    public Optional<Doctor> findActive(UUID doctorId) {
        return findById(doctorId).filter(Doctor::isActive);
    }

    private void clearLocationCache() {
        log.debug("Location cache invalidation requested.");
        try {
            Set<String> keys = redisUtil.getKeys(CACHE_LOCATION_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    redisUtil.deleteAsync(key);
                }
                log.debug("Location cache invalidation completed.");
            }
            log.debug("No location cache to clear.");
        } catch (Exception e) {
            log.warn("Failed to clear location cache: {}", e.getMessage());
        }
    }
    // ── Haversine ─────────────────────────────────────────────────────────────

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

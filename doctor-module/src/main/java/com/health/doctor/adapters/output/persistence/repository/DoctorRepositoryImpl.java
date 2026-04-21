package com.health.doctor.adapters.output.persistence.repository;

import ch.hsr.geohash.GeoHash;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.doctor.domain.exception.BookingException;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import com.health.doctor.mapper.MapperClass;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.*;

@Slf4j
@Singleton
public class DoctorRepositoryImpl implements DoctorRepositoryPort {

    public static final UUID NO_CLINIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final CqlSession session;

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
    private final PreparedStatement selectAppointmentByDoctorStatus;
    private final PreparedStatement selectAppointmentsByDoctor;
    private final PreparedStatement deleteAppointmentsById;
    private final PreparedStatement deleteAppointmentsByDoctor;
    private final PreparedStatement deleteAppointmentByDoctorStatus;
    private final PreparedStatement deleteAppointmentsByPatient;
    private final PreparedStatement decreaseCounter;
    private final PreparedStatement deleteAppointmentsByDoctorDate;
    private final PreparedStatement deleteDoctors;
    private final PreparedStatement selectGeoPrefix;
    private final PreparedStatement deleteDoctorCredentials;
    private final PreparedStatement deleteDoctorLocation;
    private final PreparedStatement deleteDoctorLookup;
    private final PreparedStatement deleteDoctorClinic;
    private final PreparedStatement deleteDoctorIndividual;
    private final PreparedStatement deleteDoctorSchedules;
    private final PreparedStatement GetDoctorType;
    private final PreparedStatement selectGeoPrefixByDoctor;
    // ─────────────────────────────────────────────



    public DoctorRepositoryImpl(CqlSession session) {
        this.session = session;

        // doctors — canonical profile
        insertDoctor = session.prepare(
                insertInto("doctor_service", "doctors")
                        .value("doctor_id", bindMarker("doctor_id"))
                        .value("name", bindMarker("name"))
                        .value("clinic_ids", bindMarker("clinic_ids"))
                        .value("type", bindMarker("type"))
                        .value("specialization", bindMarker("specialization"))
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
                        .value("specialization", bindMarker("specialization"))
                        .value("latitude", bindMarker("latitude"))
                        .value("longitude", bindMarker("longitude"))
                        .value("location_text", bindMarker("location_text"))
                        .value("is_active", bindMarker("is_active"))
                        .build()
        );

        // SELECT from doctors by PK
        selectDoctorById = session.prepare(
                selectFrom("doctor_service", "doctors")
                        .all()
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        // SELECT name/specialization/clinic_ids from doctors (for location denormalization)
        selectDoctorProfileForLocation = session.prepare(
                selectFrom("doctor_service", "doctors")
                        .columns("name", "specialization", "clinic_ids")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        // SELECT from doctors_by_location by geohash prefix
        selectByGeohash = session.prepare(
                "SELECT doctor_id, clinic_id, name, specialization, latitude, longitude, is_active " +
                        "FROM doctor_service.doctors_by_location " +
                        "WHERE geohash_prefixes CONTAINS ? AND is_active = ? ALLOW FILTERING"
        );

        // SELECT from doctors_by_clinic by clinic_id
        selectByClinic = session.prepare(
                selectFrom("doctor_service", "doctors_by_clinic")
                        .all()
                        .whereColumn("clinic_id").isEqualTo(bindMarker("clinic_id"))
                        .whereColumn("is_active").isEqualTo(bindMarker("is_active"))
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

        //DELETE from appointments_by_id
        deleteAppointmentsById = session.prepare(
                deleteFrom("doctor_service", "appointments_by_id")
                        .whereColumn("appointment_id").isEqualTo(bindMarker("appointment_id"))
                        .build()
        );

        // Delete from appointments_by_doctor
        deleteAppointmentsByDoctor = session.prepare(
                deleteFrom("doctor_service", "appointments_by_doctor")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .whereColumn("appointment_date").isEqualTo(bindMarker("appointment_date"))
                        .whereColumn("scheduled_time").isEqualTo(bindMarker("scheduled_time"))
                        .whereColumn("appointment_id").isEqualTo(bindMarker("appointment_id"))
                        .build()
        );

        // Delete from appointments_by_doctor_status
        deleteAppointmentByDoctorStatus = session.prepare(
                deleteFrom("doctor_service", "appointments_by_doctor_status")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .whereColumn("status").isEqualTo(bindMarker("status"))
                        .whereColumn("appointment_date").isEqualTo(bindMarker("appointment_date"))
                        .whereColumn("scheduled_time").isEqualTo(bindMarker("scheduled_time"))
                        .whereColumn("appointment_id").isEqualTo(bindMarker("appointment_id"))
                        .build()

        );
        // Delete from appointments_by_patient
        deleteAppointmentsByPatient = session.prepare(
                deleteFrom("doctor_service", "appointments_by_patient")
                        .whereColumn("patient_id").isEqualTo(bindMarker("patient_id"))
                        .whereColumn("appointment_date").isEqualTo(bindMarker("appointment_date"))
                        .whereColumn("scheduled_time").isEqualTo(bindMarker("scheduled_time"))
                        .whereColumn("appointment_id").isEqualTo(bindMarker("appointment_id"))
                        .build()
        );
        //Decrease counter
        decreaseCounter = session.prepare(
                update("doctor_service", "appointment_count_by_doctor_date")
                        .decrement("count")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .whereColumn("appointment_date").isEqualTo(bindMarker("appointment_date"))
                        .build()
        );

        //DELETE from appointment_count_by_doctor_date
        deleteAppointmentsByDoctorDate = session.prepare(
                deleteFrom("doctor_service", "appointment_count_by_doctor_date")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );
        //DELETE from doctors
        deleteDoctors = session.prepare(
                deleteFrom("doctor_service", "doctors")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );
        //DELETE from doctor_credentials
        deleteDoctorCredentials = session.prepare(
                deleteFrom("doctor_service", "doctor_credentials")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );
        //DELETE from doctor Lookup
        deleteDoctorLookup = session.prepare(
                deleteFrom("doctor_service", "doctors_by_email")
                        .whereColumn("email").isEqualTo(bindMarker("email"))
                        .build()
        );

        //DELETE from doctor_location
        deleteDoctorLocation = session.prepare(
                deleteFrom("doctor_service", "doctors_by_location")
                        .whereColumn("geohash_prefix").isEqualTo(bindMarker("geohash_prefix"))
                        .whereColumn("clinic_id").isEqualTo(bindMarker("clinic_id"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        //DELETE from doctor_clinic
        deleteDoctorClinic = session.prepare(
                deleteFrom("doctor_service", "doctors_by_clinic")
                        .whereColumn("clinic_id").isEqualTo(bindMarker("clinic_id"))
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        //DELETE from doctor_individual
        deleteDoctorIndividual = session.prepare(
                deleteFrom("doctor_service", "doctors_by_individual")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        //DELETE from doctor_schedules
        deleteDoctorSchedules = session.prepare(
                deleteFrom("doctor_service", "doctor_schedules")
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
        
        //GET Doctor Type
        GetDoctorType = session.prepare(
                selectFrom("doctor_service", "doctors")
                        .column("type")
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

        session.execute(insertDoctor.bind()
                .setUuid("doctor_id", d.getId())
                .setString("name", d.getName())
                .setList("clinic_ids", d.getClinicIds(), UUID.class)
                .setString("type", d.getType().name())
                .setString("specialization", d.getSpecialization())
                .setBoolean("is_active", d.isActive())
                .setBoolean("is_deleted", false)
                .setInstant("created_at", now)
                .setInstant("updated_at", now)
        );

        if (d.getClinicIds() == null || d.getClinicIds().isEmpty()) {
            session.execute(insertDoctorByIndividual.bind()
                    .setUuid("doctor_id", d.getId())
                    .setString("name", d.getName())
                    .setString("type", d.getType().name())
                    .setString("specialization", d.getSpecialization())
                    .setBoolean("is_active", d.isActive())
                    .setBoolean("is_deleted", false)
                    .setInstant("updated_at", now)
            );
        } else {
            for (UUID clinicId : d.getClinicIds()) {
                session.execute(insertDoctorByClinic.bind()
                        .setUuid("clinic_id", clinicId)
                        .setUuid("doctor_id", d.getId())
                        .setString("name", d.getName())
                        .setString("type", d.getType().name())
                        .setString("specialization", d.getSpecialization())
                        .setBoolean("is_active", d.isActive())
                        .setBoolean("is_deleted", false)
                        .setInstant("created_at", now)
                        .setInstant("updated_at", now)
                );
            }
        }
    }

    // ── Location ──────────────────────────────────────────────────────────────

    @Override
    public void updateLocation(UUID doctorId, UUID clinicId, Location location) {

        UUID effectiveClinicId = (clinicId !=null) ? clinicId : NO_CLINIC_ID;
        String fullGeohash = location.getGeohash();
//        String newPrefix = location.getGeohash().substring(0, 6);

        List<String> prefixes = new ArrayList<>();
        for (int precision = 4; precision <= 6; precision++) {
            prefixes.add(fullGeohash.substring(0, precision));
        }

        // 1. Find and delete old geohash entry for this doctor/clinic combo
        ResultSet rs = session.execute(selectGeoPrefix.bind()
                .setUuid("doctor_id", doctorId)
                .setUuid("clinic_id", effectiveClinicId));
        
        for (Row row : rs) {
            String oldPrefix = row.getString("geohash_prefix");
            // If the prefix changed (or just to be safe), delete the old row
            session.execute(deleteDoctorLocation.bind()
                    .setString("geohash_prefix", oldPrefix)
                    .setUuid("clinic_id", effectiveClinicId)
                    .setUuid("doctor_id", doctorId));
        }

        // 2. Fetch profile info for denormalization
        Row profileRow = session.execute(
                selectDoctorProfileForLocation.bind()
                        .setUuid("doctor_id", doctorId)
        ).one();

        String name = profileRow != null ? profileRow.getString("name") : null;
        String specialization = profileRow != null ? profileRow.getString("specialization") : null;

        // 3. Insert new entry
        session.execute(insertDoctorByLocation.bind()
                .setString("geohash_prefix", fullGeohash.substring(0,6))
                .setList("geohash_prefixes", prefixes, String.class)
                .setUuid("clinic_id", effectiveClinicId)
                .setUuid("doctor_id", doctorId)
                .setString("name", name)
                .setString("specialization", specialization)
                .setDouble("latitude", location.getLatitude())
                .setDouble("longitude", location.getLongitude())
                .setString("location_text", location.getLocationText())
                .setBoolean("is_active", true)
        );
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Override
    public Optional<Doctor> findById(UUID doctorId) {
        Row r = session.execute(
                selectDoctorById.bind()
                        .setUuid("doctor_id", doctorId)
        ).one();
        if (r == null) return Optional.empty();
        return Optional.of(MapperClass.mapProfileRow(r));
    }

    @Override
    public List<Doctor> findByGeohashPrefix(String prefix) {
        ResultSet rs = session.execute(
                selectByGeohash.bind(prefix,true)
        );

        List<Doctor> list = new ArrayList<>();
        for (Row r : rs) {
            UUID doctorId = r.getUuid("doctor_id");
            Row rt = session.execute(GetDoctorType.bind().setUuid("doctor_id", doctorId)).one();
            DoctorType type = (rt != null) ? DoctorType.valueOf(rt.getString("type")) : null;
            list.add(MapperClass.mapLocationRow(r, type, 0.0));
        }
        return list;
    }

    @Override
    public List<Doctor> findNearby(String geohash, double lat, double lon) {
        Map<UUID, Doctor> allFound = new LinkedHashMap<>();
        for (int precision = 6; precision >= 4; precision--) {
            String prefix = geohash.substring(0, Math.min(geohash.length(), precision));
            log.info("Trying precision {} with prefix: {}", precision, prefix);
            List<Doctor> found = searchInPrefixAndNeighbors(prefix, lat, lon);
            log.info("Found {} doctors at precision {}", found.size(), precision);
            for (Doctor d : found) {
                allFound.putIfAbsent(d.getId(), d);
            }
        }
        List<Doctor> sorted = allFound.values().stream()
                .sorted(Comparator.comparingDouble(Doctor::getDistance))
                .toList();

        log.info("Final sorted nearby doctors: {}", sorted.stream()
                .map(d -> d.getName() + " (" + d.getDistance() + " km)")
                .toList());

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

        log.info("Searching prefixes: {}",prefixes);

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

                log.info("Doctor ID: {} | Search coords: [{}, {}] | Doctor coords: [{}, {}] | Calculated Dist: {} km",
                        doctorId, lat, lon, dLat, dLon, dist);

                Row rt = session.execute(GetDoctorType.bind().setUuid("doctor_id", doctorId)).one();
                DoctorType type = (rt != null) ? DoctorType.valueOf(rt.getString("type")) : null;
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
        );
        List<Doctor> list = new ArrayList<>();
        for (Row r : rs) {
            list.add(new Doctor(
                    r.getUuid("doctor_id"),
                    r.getString("name"),
                    Collections.singletonList(clinicId),
                    DoctorType.valueOf(r.getString("type")),
                    r.getString("specialization"),
                    r.getBoolean("is_active")
            ));
        }
        return list;
    }

    @Override
    public void updateDoctor(UUID doctorId, String email, String password) {
        Instant now = Instant.now();
        String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());

        Row r = session.execute(
                selectDoctorById.bind()
                        .setUuid("doctor_id", doctorId)
        ).one();
        List<UUID> clinicIds = r != null ? r.getList("clinic_ids", UUID.class) : null;

        session.execute(updateDoctorCredentials.bind()
                .setString("email", email)
                .setString("password_hash", passwordHash)
                .setInstant("updated_at", now)
                .setUuid("doctor_id", doctorId)
        );

        session.execute(updateDoctorUpdatedAt.bind()
                .setInstant("updated_at", now)
                .setUuid("doctor_id", doctorId)
        );

        if (clinicIds == null || clinicIds.isEmpty()) {
            session.execute(updateDoctorIndividual.bind()
                    .setInstant("updated_at", now)
                    .setUuid("doctor_id", doctorId)
            );
        } else {
            for (UUID clinicId : clinicIds) {
                session.execute(updateDoctorClinic.bind()
                        .setInstant("updated_at", now)
                        .setUuid("clinic_id", clinicId)
                        .setUuid("doctor_id", doctorId)
                );
            }
        }
    }

    @Override
    public void deleteDoctor(UUID doctorId, String email, String password) {

        ResultSet rs = session.execute(
                selectAppointmentByDoctorStatus.bind()
                        .setUuid("doctor_id", doctorId)
        );

        for (Row r : rs) {
            String status = r.getString("status");
            if ("PENDING".equals(status) || "ACCEPTED".equals(status)) {
                throw new BookingException("Cannot delete doctor with Pending or Accepted appointments");
            }
        }

        ResultSet allAppt = session.execute(
                selectAppointmentsByDoctor.bind()
                        .setUuid("doctor_id", doctorId)
        );

        for (Row r1 : allAppt) {
            UUID apptId = r1.getUuid("appointment_id");
            LocalDate apptDate = r1.getLocalDate("appointment_date");
            Instant apptTime = r1.getInstant("scheduled_time");
            UUID patientId = r1.getUuid("patient_id");
            String status = r1.getString("status");

            session.execute(deleteAppointmentsById.bind().setUuid("appointment_id", apptId));
            session.execute(deleteAppointmentsByDoctor.bind()
                    .setUuid("doctor_id", doctorId)
                    .setLocalDate("appointment_date", apptDate)
                    .setInstant("scheduled_time", apptTime)
                    .setUuid("appointment_id", apptId));
            session.execute(deleteAppointmentByDoctorStatus.bind()
                    .setUuid("doctor_id", doctorId)
                    .setString("status", status)
                    .setLocalDate("appointment_date", apptDate)
                    .setInstant("scheduled_time", apptTime)
                    .setUuid("appointment_id", apptId));
            session.execute(deleteAppointmentsByPatient.bind()
                    .setUuid("patient_id", patientId)
                    .setLocalDate("appointment_date", apptDate)
                    .setInstant("scheduled_time", apptTime)
                    .setUuid("appointment_id", apptId));
            session.execute(decreaseCounter.bind()
                    .setUuid("doctor_id", doctorId)
                    .setLocalDate("appointment_date", apptDate));
        }

        session.execute(deleteAppointmentsByDoctorDate.bind().setUuid("doctor_id", doctorId));

        Row doc = session.execute(selectDoctorById.bind().setUuid("doctor_id", doctorId)).one();
        List<UUID> clinicIds = doc != null ? doc.getList("clinic_ids", UUID.class) : null;

        ResultSet locs = session.execute(selectGeoPrefixByDoctor.bind().setUuid("doctor_id", doctorId));

        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(deleteDoctorCredentials.bind().setUuid("doctor_id", doctorId))
                .addStatement(deleteDoctors.bind().setUuid("doctor_id", doctorId))
                .addStatement(deleteDoctorSchedules.bind().setUuid("doctor_id", doctorId));

        if (clinicIds == null || clinicIds.isEmpty()) {
            batch.addStatement(deleteDoctorIndividual.bind().setUuid("doctor_id", doctorId));
        } else {
            for (UUID clinicId : clinicIds) {
                batch.addStatement(deleteDoctorClinic.bind().setUuid("clinic_id", clinicId).setUuid("doctor_id", doctorId));
            }
        }

        for (Row row : locs) {
            batch.addStatement(deleteDoctorLocation.bind()
                    .setString("geohash_prefix", row.getString("geohash_prefix"))
                    .setUuid("clinic_id", row.getUuid("clinic_id"))
                    .setUuid("doctor_id", doctorId));
        }

        batch.addStatement(deleteDoctorLookup.bind().setString("email", email));

        session.execute(batch.build());
    }

    @Override
    public List<UUID> findClinicIds(UUID doctorId) {
        Row r = session.execute(selectDoctorById.bind()
                .setUuid("doctor_id", doctorId)
        ).one();
        return r != null ? r.getList("clinic_ids", UUID.class) : Collections.emptyList();
    }

    @Override
    public Optional<Doctor> findActive(UUID doctorId) {
        Row r = session.execute(selectDoctorById.bind()
                .setUuid("doctor_id", doctorId)
        ).one();

        if (r != null && r.getBoolean("is_active")) {
            return Optional.of(MapperClass.mapProfileRow(r));
        }
        return Optional.empty();
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

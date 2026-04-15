package com.health.doctor.adapters.output.persistence.repository;

import ch.hsr.geohash.GeoHash;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.doctor.domain.exception.BookingException;
import com.health.doctor.domain.exception.InvalidArgumentException;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.health.doctor.mapper.MapperClass.mapLocationRow;
import static com.health.doctor.mapper.MapperClass.mapProfileRow;

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
    public List<Doctor> findNearby(String geohash, double lat, double lon) {
        GeoHash center    = GeoHash.fromGeohashString(geohash);
        GeoHash[] neighbors = center.getAdjacent();

        Set<String> prefixes = new HashSet<>();
        prefixes.add(center.toBase32().substring(0, 5));
        for (GeoHash neighbor : neighbors) {
            prefixes.add(neighbor.toBase32().substring(0, 5));
        }

        log.info("Searching geohash prefixes: {}", prefixes);

        // Collect all candidates with their stored coordinates
        List<DoctorWithDistance> candidates = new ArrayList<>();
        for (String prefix : prefixes) {
            ResultSet rs = session.execute(
                    "SELECT * FROM doctor_service.doctors_by_location WHERE geohash_prefix=?",
                    prefix
            );
            for (Row r : rs) {
                double dLat = r.getDouble("latitude");
                double dLon = r.getDouble("longitude");
                double dist = haversineKm(lat, lon, dLat, dLon);
                UUID clinicId = r.getUuid("clinic_id");
                DoctorType type = (clinicId == null) ? DoctorType.INDIVIDUAL : DoctorType.CLINIC_DOCTOR;
                
                Doctor doctor = mapLocationRow(r);
                doctor.setType(type);
                doctor.setClinicId(clinicId);
                candidates.add(new DoctorWithDistance(doctor, dist));
            }
        }

        // Sort by distance ascending — nearest first
        candidates.sort(Comparator.comparingDouble(DoctorWithDistance::distanceKm));

        return candidates.stream()
                .map(DoctorWithDistance::doctor)
                .collect(Collectors.toList());
    }

// ── Haversine ─────────────────────────────────────────────────────────────────

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // Earth radius km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record DoctorWithDistance(Doctor doctor, double distanceKm) {}

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

    @Override
    public void updateDoctor(UUID doctorId, String email, String password) {
        Instant now = Instant.now();
        String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());

        Row r = session.execute("SELECT clinic_id FROM doctor_service.doctors WHERE doctor_id=?", doctorId).one();
        UUID clinicId = r != null ? r.getUuid("clinic_id") : null;

        // Update credentials
        session.execute(
                "UPDATE doctor_service.doctor_credentials " +
                        "SET email = ?, password_hash = ?, updated_at = ? " +
                        "WHERE doctor_id = ?",
                email, passwordHash, now, doctorId
        );

        // Update audit in main table
        session.execute(
                "UPDATE doctor_service.doctors SET updated_at = ? WHERE doctor_id = ?",
                now, doctorId
        );

        // Update denormalized audit timestamps
        if (clinicId == null) {
            session.execute("UPDATE doctor_service.doctors_by_individual SET updated_at = ? WHERE doctor_id = ?", now, doctorId);
        } else {
            session.execute("UPDATE doctor_service.doctors_by_clinic SET updated_at = ? WHERE clinic_id = ? AND doctor_id = ?", now, clinicId, doctorId);
        }
    }

    @Override
    public void deleteDoctor(UUID doctorId, String email, String password) {
        // Check for active appointments across all dates (simplified for this context)
        ResultSet rs = session.execute(
                "SELECT status FROM doctor_service.appointments_by_doctor_status WHERE doctor_id=?",
                doctorId
        );

        for (Row r : rs) {
            String status = r.getString("status");
            if ("PENDING".equals(status) || "ACCEPTED".equals(status)) {
                throw new BookingException("Cannot delete doctor with Pending or Accepted appointments");
            }
        }

        ResultSet allAppts = session.execute(
                "SELECT appointment_id, appointment_date, scheduled_time, patient_id, status " +
                        "FROM doctor_service.appointments_by_doctor WHERE doctor_id=?",
                doctorId
        );

        for (Row r1: allAppts) {
            UUID apptId = r1.getUuid("appointment_id");
            LocalDate apptDate = r1.getLocalDate("appointment_date");
            Instant apptTime = r1.getInstant("scheduled_time");
            UUID patientId = r1.getUuid("patient_id");
            String status = r1.getString("status");

            // Delete from appointments_by_id
            session.execute(
                    "DELETE FROM doctor_service.appointments_by_id WHERE appointment_id=?",
                    apptId);

            // Delete from appointments_by_doctor (current row)
            session.execute(
                    "DELETE FROM doctor_service.appointments_by_doctor " +
                            "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                    doctorId, apptDate, apptTime, apptId);

            // Delete from appointments_by_doctor_status
            session.execute(
                    "DELETE FROM doctor_service.appointments_by_doctor_status " +
                            "WHERE doctor_id=? AND status=? AND appointment_date=? " +
                            "AND scheduled_time=? AND appointment_id=?",
                    doctorId, status, apptDate, apptTime, apptId);

            // Delete from appointments_by_patient
            session.execute(
                    "DELETE FROM doctor_service.appointments_by_patient " +
                            "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                    patientId, apptDate, apptTime, apptId);

            // Decrement counter for the date
            session.execute(
                    "UPDATE doctor_service.appointment_count_by_doctor_date " +
                            "SET count = count - 1 WHERE doctor_id=? AND appointment_date=?",
                    doctorId, apptDate);
        }

        session.execute(
                "DELETE FROM doctor_service.appointment_count_by_doctor_date WHERE doctor_id=?",
                doctorId);

        Row doc = session.execute(
                "DELETE FROM doctor_service.doctors WHERE doctor_id=?",doctorId
        ).one();

        UUID clinicId = doc !=null ? doc.getUuid("clinic_id") : null;

        Row loc = session.execute(
                "SELECT geohash_prefix FROM doctor_service.doctors_by_location " +
                        "WHERE doctor_id=? ALLOW FILTERING",
                doctorId).one();
        String prefix = loc != null ? loc.getString("geohash_prefix") : null;

        //Delete doctor profile rows in a batch
        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(SimpleStatement.newInstance(
                        "DELETE FROM doctor_service.doctors WHERE doctor_id=?", doctorId))
                .addStatement(SimpleStatement.newInstance(
                        "DELETE FROM doctor_service.doctor_credentials WHERE doctor_id=?", doctorId))
                .addStatement(SimpleStatement.newInstance(
                        "DELETE FROM doctor_service.doctor_schedules WHERE doctor_id=?", doctorId));

        if (clinicId != null) {
            batch.addStatement(SimpleStatement.newInstance(
                    "DELETE FROM doctor_service.doctors_by_clinic " +
                            "WHERE clinic_id=? AND doctor_id=?", clinicId, doctorId));
        } else {
            batch.addStatement(SimpleStatement.newInstance(
                    "DELETE FROM doctor_service.doctors_by_individual WHERE doctor_id=?", doctorId));
        }
        if (prefix != null) {
            batch.addStatement(SimpleStatement.newInstance(
                    "DELETE FROM doctor_service.doctors_by_location " +
                            "WHERE geohash_prefix=? AND doctor_id=?", prefix, doctorId));
        }
        // Also delete the email lookup row
        batch.addStatement(SimpleStatement.newInstance(
                "DELETE FROM doctor_service.doctors_by_email WHERE email=?", email));

        session.execute(batch.build());


    }

    @Override
    public UUID findClinicId(UUID doctorId) {
        Row profileRow = session.execute(
                "SELECT clinic_id FROM doctor_service.doctors WHERE doctor_id=?",
                doctorId
        ).one();

        return profileRow != null ? profileRow.getUuid("clinic_id") : null;

    }

}
package com.health.patient.adapters.output.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.common.exception.BookingException;
import com.health.common.redis.RedisUtil;
import com.health.doctor.domain.model.AppointmentStatus;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import com.health.patient.domain.model.Patient;
import com.health.patient.domain.ports.PatientRepositoryPort;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.health.patient.mapper.Mapper.mapRow;

@Slf4j
@Singleton
public class PatientRepositoryImpl implements PatientRepositoryPort {

    private final CqlSession session;
    private final RedisUtil redisUtil;
    private final Provider<AppointmentRepositoryPort> appointmentRepoProvider; // Circular dependency

    private static final String CACHE_PREFIX = "patient:";
    private static final long   TTL          = 3600L; // 1h

    private final PreparedStatement insertPatient;
    private final PreparedStatement insertEmailLookup;
    private final PreparedStatement selectPatientById;
    private final PreparedStatement selectPatientByIdWithDeleted;
    private final PreparedStatement selectIdByEmail;
    private final PreparedStatement deleteEmailLookup;
    private final PreparedStatement updatePatient;
    private final PreparedStatement updatePassword;
    private final PreparedStatement softDeletePatient;
    private final PreparedStatement reactivatePatient;
    private final PreparedStatement selectAllAppointmentsByPatient;

    public PatientRepositoryImpl(CqlSession session,
                                 RedisUtil redisUtil,
                                 Provider<AppointmentRepositoryPort> appointmentRepoProvider) {
        this.session                 = session;
        this.redisUtil               = redisUtil;
        this.appointmentRepoProvider = appointmentRepoProvider;

        this.insertPatient = session.prepare(
                "INSERT INTO doctor_service.patients " +
                        "(patient_id, name, email, phone, password_hash, " +
                        " is_deleted, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?,?,?,?) IF NOT EXISTS"
        );
        this.insertEmailLookup = session.prepare(
                "INSERT INTO doctor_service.patients_by_email (email, patient_id) " +
                        "VALUES (?,?) IF NOT EXISTS"
        );
        this.selectPatientById = session.prepare(
                "SELECT * FROM doctor_service.patients WHERE patient_id=? AND is_deleted=false ALLOW FILTERING"
        );
        this.selectPatientByIdWithDeleted = session.prepare(
                "SELECT * FROM doctor_service.patients WHERE patient_id=?"
        );
        this.selectIdByEmail = session.prepare(
                "SELECT patient_id FROM doctor_service.patients_by_email WHERE email=?"
        );
        this.deleteEmailLookup = session.prepare(
                "DELETE FROM doctor_service.patients_by_email WHERE email=?"
        );
        this.updatePatient = session.prepare(
                "UPDATE doctor_service.patients SET email=?, password_hash=?, updated_at=? WHERE patient_id=?"
        );
        this.updatePassword = session.prepare(
                "UPDATE doctor_service.patients SET password_hash=?, updated_at=? WHERE patient_id=?"
        );
        this.softDeletePatient = session.prepare(
                "UPDATE doctor_service.patients SET is_deleted=true, updated_at=? WHERE patient_id=?"
        );
        this.reactivatePatient = session.prepare(
                "UPDATE doctor_service.patients SET name=?, email=?, phone=?, password_hash=?, " +
                        "is_deleted=false, updated_at=? WHERE patient_id=?"
        );
        this.selectAllAppointmentsByPatient = session.prepare(
                "SELECT appointment_id, appointment_date, scheduled_time, doctor_id, status " +
                        "FROM doctor_service.appointments_by_patient_all WHERE patient_id=?"
        );
    }

    @Override
    public void save(Patient patient) {
        Instant now = Instant.now();

        session.execute(insertPatient.bind(
                patient.getId(), patient.getName(),
                patient.getEmail(), patient.getPhone(),
                patient.getPasswordHash(),
                false, now, now
        ));
        session.execute(insertEmailLookup.bind(
                patient.getEmail(), patient.getId()
        ));

        redisUtil.setAsync(CACHE_PREFIX + patient.getId(), patient, TTL);
    }

    @Override
    public Optional<Patient> findById(UUID patientId) {
        String cacheKey = CACHE_PREFIX + patientId;
        try {
            Patient cached = redisUtil.get(cacheKey, Patient.class);
            if (cached != null) return Optional.of(cached);
        } catch (Exception e) {
            log.warn("Redis GET failed for patient findById {}: {}", patientId, e.getMessage());
        }

        Row r = session.execute(selectPatientById.bind(patientId)).one();
        if (r == null) return Optional.empty();

        Patient patient = mapRow(r);
        redisUtil.setAsync(cacheKey, patient, TTL);
        return Optional.of(patient);
    }

    private Optional<Patient> findByIdWithDeleted(UUID patientId) {
        Row r = session.execute(selectPatientByIdWithDeleted.bind(patientId)).one();
        if (r == null) return Optional.empty();
        return Optional.of(mapRow(r));
    }

    @Override
    public Optional<Patient> findByEmail(String email) {
        Row lookup = session.execute(selectIdByEmail.bind(email)).one();
        if (lookup == null) return Optional.empty();
        // Intentionally bypasses cache — fetches including deleted rows for reactivation logic
        return findByIdWithDeleted(lookup.getUuid("patient_id"));
    }

    @Override
    public void updatePatient(UUID patientId, String email, String password) {
        Instant now          = Instant.now();
        String  passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());

        // Bust cache before read so we don't serve stale data if anything below fails
        redisUtil.deleteAsync(CACHE_PREFIX + patientId);

        Optional<Patient> oldProfile = findById(patientId);
        if (oldProfile.isEmpty()) return;

        Patient old = oldProfile.get();
        if (!old.getEmail().equals(email)) {
            session.execute(deleteEmailLookup.bind(old.getEmail()));
            session.execute(insertEmailLookup.bind(email, patientId));
        }

        session.execute(updatePatient.bind(email, passwordHash, now, patientId));

        // Build and cache the updated patient from known state — avoids an extra Cassandra read
        Patient updated = new Patient(patientId, old.getName(), email, old.getPhone(), passwordHash);
        redisUtil.setAsync(CACHE_PREFIX + patientId, updated, TTL);
    }

    @Override
    public void updatePassword(UUID patientId, String passwordHash) {
        session.execute(updatePassword.bind(passwordHash, Instant.now(), patientId));
        // Bust cache — password hash changed, cached Patient object is now stale
        redisUtil.deleteAsync(CACHE_PREFIX + patientId);
    }

    @Override
    public void deletePatient(UUID patientId) {
        Instant   now = Instant.now();
        ResultSet rs  = session.execute(selectAllAppointmentsByPatient.bind(patientId));

        for (Row r : rs) {
            String status = r.getString("status");
            if (AppointmentStatus.APPOINTMENT_STATUS_ACCEPTED.name().equals(status) ||
                    AppointmentStatus.APPOINTMENT_STATUS_POSTPONED.name().equals(status)) {
                throw new BookingException(
                        "Cannot delete patient with Accepted or Postponed appointments. " +
                                "Cancel or complete them first.");
            }
        }

        session.execute(softDeletePatient.bind(now, patientId));
        appointmentRepoProvider.get().deleteAppointmentsByPatient(patientId);

        // Bust cache — patient is now soft-deleted, findById should return empty
        redisUtil.deleteAsync(CACHE_PREFIX + patientId);
    }

    @Override
    public boolean isDeleted(UUID patientId) {
        Row r = session.execute(selectPatientByIdWithDeleted.bind(patientId)).one();
        return r != null && r.getBoolean("is_deleted");
    }

    @Override
    public void reactivate(Patient patient) {
        session.execute(reactivatePatient.bind(
                patient.getName(), patient.getEmail(), patient.getPhone(),
                patient.getPasswordHash(), Instant.now(), patient.getId()
        ));
        // Warm the cache with the reactivated patient so the next findById is a cache hit
        redisUtil.setAsync(CACHE_PREFIX + patient.getId(), patient, TTL);
    }
}
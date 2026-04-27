package com.health.patient.adapters.output.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.common.exception.BookingException;
import com.health.doctor.domain.model.AppointmentStatus;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import com.health.patient.domain.model.Patient;
import com.health.patient.domain.ports.PatientRepositoryPort;
import com.health.patient.mapper.Mapper;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static com.health.patient.mapper.Mapper.mapRow;

@Singleton
public class PatientRepositoryImpl implements PatientRepositoryPort {

    private final CqlSession session;
    private final Provider<AppointmentRepositoryPort> appointmentRepoProvider;
    private final PreparedStatement insertPatient;
    private final PreparedStatement insertEmailLookup;
    private final PreparedStatement selectPatientById;
    private final PreparedStatement selectIdByEmail;
    private final PreparedStatement deleteEmailLookup;
    private final PreparedStatement updatePatient;
    private final PreparedStatement updatePassword;
    private final PreparedStatement selectAppointmentsByPatient;
    private final PreparedStatement selectAllAppointmentsByPatient;
    private final PreparedStatement softDeletePatient;

    public PatientRepositoryImpl(CqlSession session, Provider<AppointmentRepositoryPort> appointmentRepoProvider) {
        this.session = session;
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
                "SELECT * FROM doctor_service.patients WHERE patient_id=? AND is_deleted = false ALLOW FILTERING"
        );
        this.selectIdByEmail = session.prepare(
                "SELECT patient_id FROM doctor_service.patients_by_email WHERE email=?"
        );
        this.deleteEmailLookup = session.prepare(
                "DELETE FROM doctor_service.patients_by_email WHERE email = ?"
        );
        this.updatePatient = session.prepare(
                "UPDATE doctor_service.patients SET email = ?, password_hash = ?, updated_at = ? WHERE patient_id = ?"
        );
        this.updatePassword = session.prepare(
                "UPDATE doctor_service.patients SET password_hash=?, updated_at=? WHERE patient_id=?"
        );
        this.softDeletePatient = session.prepare(
                "UPDATE doctor_service.patients SET is_deleted = true, updated_at = ? WHERE patient_id = ?"
        );
        this.selectAppointmentsByPatient = session.prepare(
                "SELECT appointment_id, appointment_date, scheduled_time, doctor_id, status " +
                        "FROM doctor_service.appointments_by_patient WHERE patient_id=? AND appointment_date=?"
        );
        this.selectAllAppointmentsByPatient = session.prepare(
                "SELECT appointment_id, appointment_date, scheduled_time, doctor_id, status " +
                        "FROM doctor_service.appointments_by_patient_all WHERE patient_id=?"
        );
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @Override
    public void save(Patient patient) {
        Instant now = Instant.now();

        // patients — canonical row with audit fields
        session.execute(insertPatient.bind(
                patient.getId(), patient.getName(),
                patient.getEmail(), patient.getPhone(),
                patient.getPasswordHash(),
                false, now, now
        ));

        // patients_by_email — O(1) login lookup
        session.execute(insertEmailLookup.bind(
                patient.getEmail(), patient.getId()
        ));
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Override
    public Optional<Patient> findById(UUID patientId) {
        Row r = session.execute(selectPatientById.bind(patientId)).one();
        if (r == null) return Optional.empty();
        return Optional.of(mapRow(r));
    }

    @Override
    public Optional<Patient> findByEmail(String email) {
        // Step 1: O(1) partition read to resolve patient_id
        Row lookup = session.execute(selectIdByEmail.bind(email)).one();
        if (lookup == null) return Optional.empty();

        // Step 2: fetch full patient row
        return findById(lookup.getUuid("patient_id"));
    }

    @Override
    public void updatePatient(UUID patientID, String email, String password) {
        Instant now = Instant.now();
        String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());

        Optional<Patient> oldProfile = findById(patientID);
        if (oldProfile.isEmpty()) return;

        // If email changed, update the lookup table
        if (!oldProfile.get().getEmail().equals(email)) {
            session.execute(deleteEmailLookup.bind(oldProfile.get().getEmail()));
            session.execute(insertEmailLookup.bind(email, patientID));
        }

        session.execute(updatePatient.bind(
                email, passwordHash, now, patientID
        ));
    }

    @Override
    public void updatePassword(UUID patientId, String passwordHash) {
        session.execute(updatePassword.bind(
                passwordHash, Instant.now(), patientId
        ));
    }

    @Override
    public void deletePatient(UUID patientId) {
        Instant now = Instant.now();
        // Updated requirement: block if status is ACCEPTED or POSTPONED
        ResultSet rs = session.execute(selectAllAppointmentsByPatient.bind(patientId));
        for (Row r : rs) {
            String status = r.getString("status");
            if (AppointmentStatus.APPOINTMENT_STATUS_ACCEPTED.name().equals(status) || 
                AppointmentStatus.APPOINTMENT_STATUS_POSTPONED.name().equals(status)) {
                throw new BookingException(
                        "Cannot delete patient with Accepted or Postponed appointments. " +
                                "Cancel or complete them first.");
            }
        }

        // 3. Soft delete the patient profile row
        session.execute(softDeletePatient.bind(now, patientId));

        // Update all associated appointments status to DELETED
        appointmentRepoProvider.get().deleteAppointmentsByPatient(patientId);
    }

}
package com.health.patient.adapters.output.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.common.exception.BookingException;
import com.health.patient.domain.model.Patient;
import com.health.patient.domain.ports.PatientRepositoryPort;
import com.health.patient.mapper.Mapper;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static com.health.patient.mapper.Mapper.mapRow;

@Singleton
public class PatientRepositoryImpl implements PatientRepositoryPort {

    private final CqlSession session;
    private final PreparedStatement insertPatient;
    private final PreparedStatement insertEmailLookup;
    private final PreparedStatement selectPatientById;
    private final PreparedStatement selectIdByEmail;
    private final PreparedStatement deleteEmailLookup;
    private final PreparedStatement updatePatient;
    private final PreparedStatement updatePassword;
    private final PreparedStatement selectAppointmentsByPatient;
    private final PreparedStatement selectAllAppointmentsByPatient;
    private final PreparedStatement insertByPatient;
    private final PreparedStatement insertByPatientAll;
    private final PreparedStatement deleteByPatient;
    private final PreparedStatement deleteByPatientAll;
    private final PreparedStatement deleteAppointmentById;
    private final PreparedStatement deleteAppointmentByPatient;
    private final PreparedStatement deleteAppointmentByPatientAll;
    private final PreparedStatement deleteAppointmentByDoctor;
    private final PreparedStatement deleteAppointmentByDoctorAll;
    private final PreparedStatement deleteAppointmentByDoctorStatus;
    private final PreparedStatement decrementAppointmentCount;
    private final PreparedStatement deletePatient;

    public PatientRepositoryImpl(CqlSession session) {
        this.session = session;
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
                "SELECT * FROM doctor_service.patients WHERE patient_id=?"
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
        this.selectAppointmentsByPatient = session.prepare(
                "SELECT appointment_id, appointment_date, scheduled_time, doctor_id, status " +
                        "FROM doctor_service.appointments_by_patient WHERE patient_id=? AND appointment_date=?"
        );
        this.selectAllAppointmentsByPatient = session.prepare(
                "SELECT appointment_id, appointment_date, scheduled_time, doctor_id, status " +
                        "FROM doctor_service.appointments_by_patient_all WHERE patient_id=?"
        );
        this.insertByPatient = session.prepare(
                "INSERT INTO doctor_service.appointments_by_patient " +
                        "(patient_id, appointment_date, scheduled_time, appointment_id, " +
                        " doctor_id, clinic_id, doctor_name, clinic_name, specialization, " +
                        " status, reason_for_visit, cancellation_reason) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"
        );
        this.insertByPatientAll = session.prepare(
                "INSERT INTO doctor_service.appointments_by_patient_all " +
                        "(patient_id, appointment_date, scheduled_time, appointment_id, " +
                        " doctor_id, clinic_id, doctor_name, clinic_name, specialization, " +
                        " status, reason_for_visit, cancellation_reason) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"
        );
        this.deleteByPatient = session.prepare(
                "DELETE FROM doctor_service.appointments_by_patient WHERE patient_id=? AND appointment_date=?"
        );
        this.deleteByPatientAll = session.prepare(
                "DELETE FROM doctor_service.appointments_by_patient_all WHERE patient_id=?"
        );
        this.deleteAppointmentById = session.prepare(
                "DELETE FROM doctor_service.appointments_by_id WHERE appointment_id=?"
        );
        this.deleteAppointmentByPatient = session.prepare(
                "DELETE FROM doctor_service.appointments_by_patient " +
                        "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?"
        );
        this.deleteAppointmentByPatientAll = session.prepare(
                "DELETE FROM doctor_service.appointments_by_patient_all " +
                        "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?"
        );
        this.deleteAppointmentByDoctor = session.prepare(
                "DELETE FROM doctor_service.appointments_by_doctor " +
                        "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?"
        );
        this.deleteAppointmentByDoctorAll = session.prepare(
                "DELETE FROM doctor_service.appointments_by_doctor_all " +
                        "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?"
        );
        this.deleteAppointmentByDoctorStatus = session.prepare(
                "DELETE FROM doctor_service.appointments_by_doctor_status " +
                        "WHERE doctor_id=? AND status=? AND appointment_date=? " +
                        "AND scheduled_time=? AND appointment_id=?"
        );
        this.decrementAppointmentCount = session.prepare(
                "UPDATE doctor_service.appointment_count_by_doctor_date " +
                        "SET count = count - 1 WHERE doctor_id=? AND appointment_date=?"
        );
        this.deletePatient = session.prepare(
                "DELETE FROM doctor_service.patients WHERE patient_id=?"
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
        if (r == null || r.getBoolean("is_deleted")) return Optional.empty();
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
        //Appointment Search - Check for active appointments across all dates
        ResultSet activeRs = session.execute(selectAllAppointmentsByPatient.bind(patientId));
        for (Row r : activeRs) {
            String status = r.getString("status");
            if ("PENDING".equals(status) || "ACCEPTED".equals(status)) {
                throw new BookingException(
                        "Cannot delete patient with pending or accepted appointments. " +
                                "Cancel all appointments first.");
            }
        }


        ResultSet allAppts = session.execute(selectAllAppointmentsByPatient.bind(patientId));

        for (Row r : allAppts) {
            UUID      apptId   = r.getUuid("appointment_id");
            LocalDate apptDate = r.getLocalDate("appointment_date");
            Instant   apptTime = r.getInstant("scheduled_time");
            UUID      doctorId = r.getUuid("doctor_id");
            String    status   = r.getString("status");

            // Delete from appointments_by_id
            session.execute(deleteAppointmentById.bind(apptId));

            // Delete from appointments_by_patient
            session.execute(deleteAppointmentByPatient.bind(
                    patientId, apptDate, apptTime, apptId));

            // Delete from appointments_by_patient_all
            session.execute(deleteAppointmentByPatientAll.bind(
                    patientId, apptDate, apptTime, apptId));

            // Delete from appointments_by_doctor
            session.execute(deleteAppointmentByDoctor.bind(
                    doctorId, apptDate, apptTime, apptId));

            // Delete from appointments_by_doctor_all
            session.execute(deleteAppointmentByDoctorAll.bind(
                    doctorId, apptDate, apptTime, apptId));

            // Delete from appointments_by_doctor_status
            session.execute(deleteAppointmentByDoctorStatus.bind(
                    doctorId, status, apptDate, apptTime, apptId));

            // Decrement counter
            session.execute(decrementAppointmentCount.bind(
                    doctorId, apptDate));
        }

        // 3. Delete the patient profile rows
        Optional<Patient> p = findById(patientId);
        if (p.isPresent()) {
            BatchStatement batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                    .addStatement(deletePatient.bind(patientId))
                    .addStatement(deleteEmailLookup.bind(p.get().getEmail()))
                    .addStatement(deleteByPatientAll.bind(patientId)) // Cleanup any leftovers
                    .build();
            session.execute(batch);
        }
    }

}
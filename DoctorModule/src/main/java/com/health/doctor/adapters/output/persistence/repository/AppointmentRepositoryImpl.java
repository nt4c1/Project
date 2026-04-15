package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.AppointmentStatus;
import com.health.doctor.domain.ports.*;
import com.health.doctor.mapper.MapperClass;
import jakarta.inject.Singleton;

import java.time.*;
import java.util.*;

import static com.health.doctor.mapper.MapperClass.*;

@Singleton
public class AppointmentRepositoryImpl implements AppointmentRepositoryPort {

    private final CqlSession           session;
    private final DoctorRepositoryPort doctorRepo;
    private final ClinicRepositoryPort clinicRepo;
    private final PatientLookUpPort    patientLookup;

    public AppointmentRepositoryImpl(CqlSession session,
                                     DoctorRepositoryPort doctorRepo,
                                     ClinicRepositoryPort clinicRepo,
                                     PatientLookUpPort patientLookup) {
        this.session       = session;
        this.doctorRepo    = doctorRepo;
        this.clinicRepo    = clinicRepo;
        this.patientLookup = patientLookup;
    }

    @Override
    public void save(Appointment a) {
        Row countRow = session.execute(
                "SELECT count FROM doctor_service.appointment_count_by_doctor_date " +
                        "WHERE doctor_id=? AND appointment_date=?",
                a.getDoctorId(), a.getAppointmentDate()
        ).one();
        long current = countRow != null ? countRow.getLong("count") : 0L;
        if (current >= 50) throw new RuntimeException("Doctor fully booked for the day");

        Instant now              = Instant.now();
        Instant scheduledInstant = toInstant(a.getAppointmentDate(), a.getScheduleTime());

        if (a.getPatientName() == null || a.getPatientPhone() == null) {
            patientLookup.findById(a.getPatientId()).ifPresent(p -> {
                a.setPatientName(p.name());
                a.setPatientPhone(p.phone());
            });
        }
        if (a.getDoctorName() == null) {
            doctorRepo.findById(a.getDoctorId()).ifPresent(d -> {
                a.setDoctorName(d.getName());
                a.setSpecialization(d.getSpecialization());
                if (d.getClinicId() != null && a.getClinicName() == null) {
                    var clinic = clinicRepo.findById(d.getClinicId());
                    if (clinic != null) a.setClinicName(clinic.getName());
                }
            });
        }

        BatchStatement batch = BatchStatement.newInstance(DefaultBatchType.LOGGED)
                .add(SimpleStatement.newInstance(
                        "INSERT INTO doctor_service.appointments_by_id " +
                                "(appointment_id, doctor_id, patient_id, appointment_date, " +
                                " scheduled_time, status, reason_for_visit, " +
                                " cancellation_reason, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        a.getId(), a.getDoctorId(), a.getPatientId(),
                        a.getAppointmentDate(), scheduledInstant,
                        a.getStatus().name(), a.getReasonForVisit(), null, now, now))
                .add(SimpleStatement.newInstance(
                        "INSERT INTO doctor_service.appointments_by_doctor " +
                                "(doctor_id, appointment_date, scheduled_time, appointment_id, " +
                                " patient_id, patient_name, patient_phone, status, " +
                                " reason_for_visit, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        a.getDoctorId(), a.getAppointmentDate(), scheduledInstant,
                        a.getId(), a.getPatientId(), a.getPatientName(), a.getPatientPhone(),
                        a.getStatus().name(), a.getReasonForVisit(), now, now))
                .add(SimpleStatement.newInstance(
                        "INSERT INTO doctor_service.appointments_by_doctor_status " +
                                "(doctor_id, status, appointment_date, scheduled_time, " +
                                " appointment_id, patient_id, patient_name) VALUES (?,?,?,?,?,?,?)",
                        a.getDoctorId(), a.getStatus().name(), a.getAppointmentDate(), scheduledInstant,
                        a.getId(), a.getPatientId(), a.getPatientName()))
                .add(SimpleStatement.newInstance(
                        "INSERT INTO doctor_service.appointments_by_patient " +
                                "(patient_id, appointment_date, scheduled_time, appointment_id, " +
                                " doctor_id, doctor_name, clinic_name, specialization, " +
                                " status, reason_for_visit) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        a.getPatientId(), a.getAppointmentDate(), scheduledInstant,
                        a.getId(), a.getDoctorId(), a.getDoctorName(), a.getClinicName(),
                        a.getSpecialization(), a.getStatus().name(), a.getReasonForVisit()));

        session.execute(batch);
        try {
            session.execute(
                    "UPDATE doctor_service.appointment_count_by_doctor_date " +
                            "SET count = count + 1 WHERE doctor_id=? AND appointment_date=?",
                    a.getDoctorId(), a.getAppointmentDate());
        } catch (Exception ignored) { }
    }

    @Override
    public List<Appointment> findByDoctorAndDate(UUID doctorId, LocalDate date) {
        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.appointments_by_doctor WHERE doctor_id=? AND appointment_date=?",
                doctorId, date);
        Row r1 = session.execute(
                "SELECT * FROM doctor_service.doctors WHERE doctor_id = ?",
                doctorId
        ).one();
        assert r1 != null;
        UUID clinicId = r1.getUuid("clinic_id");
        String doctorName = r1.getString("name");
        String specialization = r1.getString("specialization");

        String clinicName = null;
        if (clinicId != null) {
            Row r2 = session.execute(
                    "SELECT name FROM doctor_service.clinics WHERE clinic_id=?",
                    clinicId
            ).one();
            if (r2 != null) clinicName = r2.getString("name");
        }

        List<Appointment> list = new ArrayList<>();
        for (Row r : rs) {
            Appointment a = mapDoctorRow(r);
            a.setDoctorName(doctorName);
            a.setClinicName(clinicName);
            a.setSpecialization(specialization);
            list.add(a);
        }

        return list;
    }

    @Override
    public List<Appointment> findDoctorAndStatus(UUID doctorId, String status, LocalDate date) {
        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.appointments_by_doctor_status " +
                        "WHERE doctor_id=? AND status=? AND appointment_date=?",
                doctorId, status, date);
        List<Appointment> list = new ArrayList<>();
        for (Row r : rs) {
            Appointment a = new Appointment(
                    r.getUuid("appointment_id"), r.getUuid("doctor_id"),
                    r.getUuid("patient_id"), r.getLocalDate("appointment_date"),
                    toLocalTime(r.getInstant("scheduled_time")),
                    AppointmentStatus.valueOf(status), null, null);
            a.setPatientName(r.getString("patient_name"));
            list.add(a);
        }
        return list;
    }

    @Override
    public List<Appointment> findPending(UUID doctorId, LocalDate date) {
        return findDoctorAndStatus(doctorId, "PENDING", date);
    }

    @Override
    public List<Appointment> findByPatientAndDate(UUID patientId, LocalDate date) {
        ResultSet rs = session.execute(
                "SELECT * FROM doctor_service.appointments_by_patient WHERE patient_id=? AND appointment_date=?",
                patientId, date);
        List<Appointment> list = new ArrayList<>();
        for (Row r : rs) {
            Appointment a = new Appointment(
                    r.getUuid("appointment_id"), r.getUuid("doctor_id"),
                    r.getUuid("patient_id"), r.getLocalDate("appointment_date"),
                    toLocalTime(r.getInstant("scheduled_time")),
                    AppointmentStatus.valueOf(r.getString("status")), null, null);
            a.setDoctorName(r.getString("doctor_name"));
            a.setClinicName(r.getString("clinic_name"));
            a.setSpecialization(r.getString("specialization"));
            a.setReasonForVisit(r.getString("reason_for_visit"));
            list.add(a);
        }
        return list;
    }

    @Override
    public Optional<Appointment> findById(UUID id) {
        Row r = session.execute(
                "SELECT * FROM doctor_service.appointments_by_id WHERE appointment_id=?", id).one();
        return Optional.ofNullable(r).map(MapperClass::mapIdRow);
    }

    @Override
    public void updateStatus(Appointment a, String newStatus) {
        Instant now              = Instant.now();
        Instant scheduledInstant = toInstant(a.getAppointmentDate(), a.getScheduleTime());

        session.execute(
                "UPDATE doctor_service.appointments_by_id SET status=?, updated_at=? WHERE appointment_id=?",
                newStatus, now, a.getId());
        session.execute(
                "UPDATE doctor_service.appointments_by_doctor SET status=?, updated_at=? " +
                        "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                newStatus, now, a.getDoctorId(), a.getAppointmentDate(), scheduledInstant, a.getId());
        // status is partition key — delete old, insert new
        session.execute(
                "DELETE FROM doctor_service.appointments_by_doctor_status " +
                        "WHERE doctor_id=? AND status=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                a.getDoctorId(), a.getStatus().name(),
                a.getAppointmentDate(), scheduledInstant, a.getId());
        session.execute(
                "INSERT INTO doctor_service.appointments_by_doctor_status " +
                        "(doctor_id, status, appointment_date, scheduled_time, appointment_id, patient_id, patient_name) " +
                        "VALUES (?,?,?,?,?,?,?)",
                a.getDoctorId(), newStatus, a.getAppointmentDate(), scheduledInstant,
                a.getId(), a.getPatientId(), a.getPatientName());
        session.execute(
                "UPDATE doctor_service.appointments_by_patient SET status=? " +
                        "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                newStatus, a.getPatientId(), a.getAppointmentDate(), scheduledInstant, a.getId());
    }

    /**
     * FIX: old code hard-coded status='PENDING' in the DELETE, so ACCEPTED
     * appointments were never removed from appointments_by_doctor_status on cancel.
     * Now reads the current status from appointments_by_id first.
     */
    @Override
    public void cancel(UUID appointmentId, UUID patientId, UUID doctorId,
                       LocalDate date, LocalTime time, String cancellationReason) {
        Instant now              = Instant.now();
        Instant scheduledInstant = toInstant(date, time);

        // Load current status so we delete the right partition
        Row current = session.execute(
                "SELECT status FROM doctor_service.appointments_by_id WHERE appointment_id=?",
                appointmentId).one();
        String currentStatus = current != null ? current.getString("status") : "PENDING";

        session.execute(
                "UPDATE doctor_service.appointments_by_id " +
                        "SET status='CANCELLED', cancellation_reason=?, updated_at=? WHERE appointment_id=?",
                cancellationReason, now, appointmentId);
        session.execute(
                "UPDATE doctor_service.appointments_by_doctor SET status='CANCELLED', updated_at=? " +
                        "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                now, doctorId, date, scheduledInstant, appointmentId);
        session.execute(
                "UPDATE doctor_service.appointments_by_patient SET status='CANCELLED' " +
                        "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                patientId, date, scheduledInstant, appointmentId);
        // FIX: use actual currentStatus, not hard-coded 'PENDING'
        session.execute(
                "DELETE FROM doctor_service.appointments_by_doctor_status " +
                        "WHERE doctor_id=? AND status=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                doctorId, currentStatus, date, scheduledInstant, appointmentId);
        session.execute(
                "INSERT INTO doctor_service.appointments_by_doctor_status " +
                        "(doctor_id, status, appointment_date, scheduled_time, appointment_id, patient_id) " +
                        "VALUES (?,?,?,?,?,?)",
                doctorId, "CANCELLED", date, scheduledInstant, appointmentId, patientId);
        decrementCount(doctorId, date);
    }

    @Override
    public void decrementCount(UUID doctorId, LocalDate date) {
        session.execute(
                "UPDATE doctor_service.appointment_count_by_doctor_date " +
                        "SET count = count - 1 WHERE doctor_id=? AND appointment_date=?",
                doctorId, date);
    }

    @Override
    public void postpone(LocalDate oldDate, Appointment a) {
        Instant now        = Instant.now();
        Instant oldInstant = toInstant(oldDate, a.getScheduleTime());
        Instant newInstant = toInstant(a.getAppointmentDate(), a.getScheduleTime());
        String  newStatus  = AppointmentStatus.POSTPONED.name();

        //patient name + patient phone
        if (a.getPatientName() == null || a.getPatientPhone() == null) {
            Row doctorRow = session.execute(
                    "SELECT patient_name, patient_phone " +
                            "FROM doctor_service.appointments_by_doctor " +
                            "WHERE doctor_id=? AND appointment_date=? " +
                            "  AND scheduled_time=? AND appointment_id=?",
                    a.getDoctorId(), oldDate, oldInstant, a.getId()
            ).one();
            if (doctorRow != null) {
                a.setPatientName(doctorRow.getString("patient_name"));
                a.setPatientPhone(doctorRow.getString("patient_phone"));
            }
            // Fallback: look up patient if row already gone
            if (a.getPatientName() == null) {
                patientLookup.findById(a.getPatientId()).ifPresent(p -> {
                    a.setPatientName(p.name());
                    a.setPatientPhone(p.phone());
                });
            }
        }
        
        //doctor name + clinic name
        if (a.getDoctorName() == null || a.getClinicName() == null) {
            Row patientRow = session.execute(
                    "SELECT doctor_name, clinic_name, specialization " +
                            "FROM doctor_service.appointments_by_patient " +
                            "WHERE patient_id=? AND appointment_date=? " +
                            "  AND scheduled_time=? AND appointment_id=?",
                    a.getPatientId(), oldDate, oldInstant, a.getId()
            ).one();
            if (patientRow != null) {
                a.setDoctorName(patientRow.getString("doctor_name"));
                a.setClinicName(patientRow.getString("clinic_name"));
                a.setSpecialization(patientRow.getString("specialization"));
            }
            // Fallback: resolve from doctor + clinic tables
            if (a.getDoctorName() == null) {
                doctorRepo.findById(a.getDoctorId()).ifPresent(d -> {
                    a.setDoctorName(d.getName());
                    a.setSpecialization(d.getSpecialization());
                    if (d.getClinicId() != null && a.getClinicName() == null) {
                        var clinic = clinicRepo.findById(d.getClinicId());
                        if (clinic != null) a.setClinicName(clinic.getName());
                    }
                });
            }
        }
        
        

        // appointments_by_id — PK is appointment_id
        session.execute(
                "UPDATE doctor_service.appointments_by_id " +
                        "SET status=?, appointment_date=?, scheduled_time=?, updated_at=? WHERE appointment_id=?",
                newStatus, a.getAppointmentDate(), newInstant, now, a.getId());

        // appointments_by_doctor — partition includes date, must delete + reinsert
        session.execute(
                "DELETE FROM doctor_service.appointments_by_doctor " +
                        "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                a.getDoctorId(), oldDate, oldInstant, a.getId());
        session.execute(
                "INSERT INTO doctor_service.appointments_by_doctor " +
                        "(doctor_id, appointment_date, scheduled_time, appointment_id, " +
                        " patient_id, patient_name, patient_phone, status, " +
                        " reason_for_visit, created_at, updated_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                a.getDoctorId(), a.getAppointmentDate(), newInstant,
                a.getId(), a.getPatientId(),
                a.getPatientName(), a.getPatientPhone(),    
                newStatus, a.getReasonForVisit(),
                a.getCreatedAt(), now);

        // a.getStatus() is the CURRENT (old) status — correctly set because
        // DoctorGrpcApi loads the appointment from DB before calling the use case.
        session.execute(
                "DELETE FROM doctor_service.appointments_by_doctor_status " +
                        "WHERE doctor_id=? AND status=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                a.getDoctorId(), a.getStatus().name(), oldDate, oldInstant, a.getId());
        session.execute(
                "INSERT INTO doctor_service.appointments_by_doctor_status " +
                        "(doctor_id, status, appointment_date, scheduled_time, appointment_id, patient_id, patient_name) " +
                        "VALUES (?,?,?,?,?,?,?)",
                a.getDoctorId(), newStatus, a.getAppointmentDate(), newInstant,
                a.getId(), a.getPatientId(), a.getPatientName());

        // appointments_by_patient — partition includes date, must delete + reinsert
        session.execute(
                "DELETE FROM doctor_service.appointments_by_patient " +
                        "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                a.getPatientId(), oldDate, oldInstant, a.getId());
        session.execute(
                "INSERT INTO doctor_service.appointments_by_patient " +
                        "(patient_id, appointment_date, scheduled_time, appointment_id, " +
                        " doctor_id, doctor_name, clinic_name, specialization, status, reason_for_visit) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?)",
                a.getPatientId(), a.getAppointmentDate(), newInstant, a.getId(), a.getDoctorId(),
                a.getDoctorName(), a.getClinicName(), a.getSpecialization(),
                newStatus, a.getReasonForVisit());
    }
}
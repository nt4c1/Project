package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.health.common.utils.DateTimeUtils;
import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.AppointmentStatus;
import com.health.doctor.domain.ports.*;
import com.health.doctor.domain.model.Clinic;
import com.health.doctor.mapper.MapperClass;
import jakarta.inject.Singleton;

import java.time.*;
import java.util.*;

import static com.health.doctor.mapper.MapperClass.*;

@Singleton
public class AppointmentRepositoryImpl implements AppointmentRepositoryPort {

    public static final UUID NO_CLINIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final CqlSession session;
    private final DoctorRepositoryPort doctorRepo;
    private final ClinicRepositoryPort clinicRepo;
    private final PatientLookUpPort patientLookup;

    private final PreparedStatement insertById;
    private final PreparedStatement insertByDoctor;
    private final PreparedStatement insertByDoctorStatus;
    private final PreparedStatement insertByPatient;
    private final PreparedStatement insertByPatientAll;
    private final PreparedStatement updateCount;
    private final PreparedStatement selectByDoctorAndDate;
    private final PreparedStatement selectDoctorProfile;
    private final PreparedStatement selectClinicName;
    private final PreparedStatement selectByDoctorStatusAndDate;
    private final PreparedStatement selectByPatientAndDate;
    private final PreparedStatement selectById;
    private final PreparedStatement selectCount;
    private final PreparedStatement selectStatusByDoctorSlot;
    private final PreparedStatement selectStatusByPatientDoctorDate;
    private final PreparedStatement updateStatusById;
    private final PreparedStatement updateStatusByDoctor;
    private final PreparedStatement deleteByDoctorStatus;
    private final PreparedStatement updateStatusByPatient;
    private final PreparedStatement updateStatusByPatientAll;
    private final PreparedStatement updateCancelById;
    private final PreparedStatement updateCancelByDoctor;
    private final PreparedStatement updateCancelByPatient;
    private final PreparedStatement updateCancelByPatientAll;

    private final PreparedStatement deleteByDoctor;
    private final PreparedStatement deleteByPatient;
    private final PreparedStatement deleteByPatientAll;

    public AppointmentRepositoryImpl(CqlSession session,
                                     DoctorRepositoryPort doctorRepo,
                                     ClinicRepositoryPort clinicRepo,
                                     PatientLookUpPort patientLookup) {
        this.session = session;
        this.doctorRepo = doctorRepo;
        this.clinicRepo = clinicRepo;
        this.patientLookup = patientLookup;

        this.insertById = session.prepare("INSERT INTO doctor_service.appointments_by_id " +
                "(appointment_id, doctor_id, patient_id, clinic_id, appointment_date, " +
                " scheduled_time, status, reason_for_visit, " +
                " cancellation_reason, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)");

        this.insertByDoctor = session.prepare("INSERT INTO doctor_service.appointments_by_doctor " +
                "(doctor_id, appointment_date, scheduled_time, appointment_id, " +
                " patient_id, clinic_id, patient_name, patient_phone, status, " +
                " reason_for_visit, doctor_notes, cancellation_reason, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

        this.insertByDoctorStatus = session.prepare("INSERT INTO doctor_service.appointments_by_doctor_status " +
                "(doctor_id, status, appointment_date, scheduled_time, " +
                " appointment_id, patient_id, clinic_id, patient_name, patient_phone, reason_for_visit) VALUES (?,?,?,?,?,?,?,?,?,?)");

        this.insertByPatient = session.prepare("INSERT INTO doctor_service.appointments_by_patient " +
                "(patient_id, appointment_date, scheduled_time, appointment_id, " +
                " doctor_id, clinic_id, doctor_name, clinic_name, specialization, " +
                " status, reason_for_visit, cancellation_reason) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");

        this.insertByPatientAll = session.prepare("INSERT INTO doctor_service.appointments_by_patient_all " +
                "(patient_id, appointment_date, scheduled_time, appointment_id, " +
                " doctor_id, clinic_id, doctor_name, clinic_name, specialization, " +
                " status, reason_for_visit, cancellation_reason) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");

        this.updateCount = session.prepare("UPDATE doctor_service.appointment_count_by_doctor_date " +
                "SET count = count + ? WHERE doctor_id=? AND appointment_date=?");

        this.selectByDoctorAndDate = session.prepare("SELECT * FROM doctor_service.appointments_by_doctor WHERE doctor_id=? AND appointment_date=?");
        this.selectDoctorProfile = session.prepare("SELECT name, specialization, clinic_ids FROM doctor_service.doctors WHERE doctor_id = ?");
        this.selectClinicName = session.prepare("SELECT name FROM doctor_service.clinics WHERE clinic_id=?");
        this.selectByDoctorStatusAndDate = session.prepare("SELECT * FROM doctor_service.appointments_by_doctor_status " +
                "WHERE doctor_id=? AND status=? AND appointment_date=?");
        this.selectByPatientAndDate = session.prepare("SELECT * FROM doctor_service.appointments_by_patient WHERE patient_id=? AND appointment_date=?");
        this.selectById = session.prepare("SELECT * FROM doctor_service.appointments_by_id WHERE appointment_id=?");
        this.selectCount = session.prepare("SELECT count FROM doctor_service.appointment_count_by_doctor_date " +
                "WHERE doctor_id=? AND appointment_date=?");
        this.selectStatusByDoctorSlot = session.prepare("SELECT status FROM doctor_service.appointments_by_doctor " +
                "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=?");
        this.selectStatusByPatientDoctorDate = session.prepare("SELECT status, doctor_id FROM doctor_service.appointments_by_patient " +
                "WHERE patient_id=? AND appointment_date=?");

        this.updateStatusById = session.prepare("UPDATE doctor_service.appointments_by_id SET status=?, updated_at=? WHERE appointment_id=?");
        this.updateStatusByDoctor = session.prepare("UPDATE doctor_service.appointments_by_doctor SET status=?, updated_at=? " +
                "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?");
        this.deleteByDoctorStatus = session.prepare("DELETE FROM doctor_service.appointments_by_doctor_status " +
                "WHERE doctor_id=? AND status=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?");
        this.updateStatusByPatient = session.prepare("UPDATE doctor_service.appointments_by_patient SET status=? " +
                "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?");

        this.updateStatusByPatientAll = session.prepare("UPDATE doctor_service.appointments_by_patient_all SET status=? " +
                "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?");

        this.updateCancelById = session.prepare("UPDATE doctor_service.appointments_by_id " +
                "SET status='CANCELLED', cancellation_reason=?, updated_at=? WHERE appointment_id=?");
        this.updateCancelByDoctor = session.prepare("UPDATE doctor_service.appointments_by_doctor SET status='CANCELLED', cancellation_reason=?, updated_at=? " +
                "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?");
        this.updateCancelByPatient = session.prepare("UPDATE doctor_service.appointments_by_patient SET status='CANCELLED', cancellation_reason=? " +
                "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?");

        this.updateCancelByPatientAll = session.prepare("UPDATE doctor_service.appointments_by_patient_all SET status='CANCELLED', cancellation_reason=? " +
                "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?");

        this.deleteByDoctor = session.prepare("DELETE FROM doctor_service.appointments_by_doctor " +
                "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?");
        this.deleteByPatient = session.prepare("DELETE FROM doctor_service.appointments_by_patient " +
                "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?");
        this.deleteByPatientAll = session.prepare("DELETE FROM doctor_service.appointments_by_patient_all " +
                "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?");
    }

    @Override
    public void save(Appointment a) {
        Instant now = Instant.now();
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
                if (d.getClinicIds() != null && !d.getClinicIds().isEmpty()) {
                    if (a.getClinicId() == null || NO_CLINIC_ID.equals(a.getClinicId())) {
                        a.setClinicId(d.getClinicIds().get(0));
                    }
                } else if (a.getClinicId() == null) {
                    a.setClinicId(NO_CLINIC_ID);
                }

                if (a.getClinicId() != null && !NO_CLINIC_ID.equals(a.getClinicId())) {
                    Optional<Clinic> clinic = clinicRepo.findById(a.getClinicId());
                    clinic.ifPresent(c -> a.setClinicName(c.getName()));
                }
            });
        }
        
        if (a.getClinicId() == null) a.setClinicId(NO_CLINIC_ID);

        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(insertById.bind(a.getId(), a.getDoctorId(), a.getPatientId(), a.getClinicId(),
                        a.getAppointmentDate(), scheduledInstant,
                        a.getStatus().name(), a.getReasonForVisit(), null, now, now))
                .addStatement(insertByDoctor.bind(a.getDoctorId(), a.getAppointmentDate(), scheduledInstant,
                        a.getId(), a.getPatientId(), a.getClinicId(), a.getPatientName(), a.getPatientPhone(),
                        a.getStatus().name(), a.getReasonForVisit(), null, null, now, now))
                .addStatement(insertByDoctorStatus.bind(a.getDoctorId(), a.getStatus().name(), a.getAppointmentDate(), scheduledInstant,
                        a.getId(), a.getPatientId(), a.getClinicId(), a.getPatientName(), a.getPatientPhone(), a.getReasonForVisit()))
                .addStatement(insertByPatient.bind(a.getPatientId(), a.getAppointmentDate(), scheduledInstant,
                        a.getId(), a.getDoctorId(), a.getClinicId(), a.getDoctorName(), a.getClinicName(),
                        a.getSpecialization(), a.getStatus().name(), a.getReasonForVisit(), null))
                .addStatement(insertByPatientAll.bind(a.getPatientId(), a.getAppointmentDate(), scheduledInstant,
                        a.getId(), a.getDoctorId(), a.getClinicId(), a.getDoctorName(), a.getClinicName(),
                        a.getSpecialization(), a.getStatus().name(), a.getReasonForVisit(), null))
                .addStatement(updateCount.bind(1L, a.getDoctorId(), a.getAppointmentDate()));

        session.execute(batch.build());
    }

    @Override
    public List<Appointment> findByDoctorAndDate(UUID doctorId, LocalDate date) {
        ResultSet rs = session.execute(selectByDoctorAndDate.bind(doctorId, date));
        
        Row profileRow = session.execute(selectDoctorProfile.bind(doctorId)).one();
        if (profileRow == null) return Collections.emptyList();

        List<UUID> clinicIds = profileRow.getList("clinic_ids", UUID.class);
        String doctorName = profileRow.getString("name");
        String specialization = profileRow.getString("specialization");

        String clinicName = null;
        if (clinicIds != null && !clinicIds.isEmpty()) {
            Row clinicRow = session.execute(selectClinicName.bind(clinicIds.get(0))).one();
            if (clinicRow != null) clinicName = clinicRow.getString("name");
        }

        List<Appointment> list = new ArrayList<>();
        for (Row r : rs) {
            Appointment a = MapperClass.mapDoctorRow(r);
            a.setDoctorName(doctorName);
            a.setClinicName(clinicName);
            a.setSpecialization(specialization);
            if (a.getClinicId() == null) a.setClinicId(NO_CLINIC_ID);
            list.add(a);
        }
        return list;
    }

    @Override
    public List<Appointment> findDoctorAndStatus(UUID doctorId, String status, LocalDate date) {
        ResultSet rs = session.execute(selectByDoctorStatusAndDate.bind(doctorId, status, date));
        List<Appointment> list = new ArrayList<>();
        for (Row r : rs) {
            Appointment a = new Appointment(
                    r.getUuid("appointment_id"), r.getUuid("doctor_id"),
                    r.getUuid("patient_id"), r.getLocalDate("appointment_date"),
                    toLocalTime(r.getInstant("scheduled_time")),
                    AppointmentStatus.valueOf(status), null, null);
            a.setPatientName(r.getString("patient_name"));
            UUID cid = r.getUuid("clinic_id");
            a.setClinicId(cid != null ? cid : NO_CLINIC_ID);
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
        ResultSet rs = session.execute(selectByPatientAndDate.bind(patientId, date));
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
            UUID cid = r.getUuid("clinic_id");
            a.setClinicId(cid != null ? cid : NO_CLINIC_ID);
            list.add(a);
        }
        return list;
    }

    @Override
    public Optional<Appointment> findById(UUID id) {
        Row r = session.execute(selectById.bind(id)).one();
        return Optional.ofNullable(r).map(row -> {
            Appointment a = MapperClass.mapIdRow(row);
            if (a.getClinicId() == null) a.setClinicId(NO_CLINIC_ID);
            return a;
        });
    }

    @Override
    public long countByDoctorAndDate(UUID doctorId, LocalDate date) {
        Row r = session.execute(selectCount.bind(doctorId, date)).one();
        return r != null ? r.getLong("count") : 0L;
    }

    @Override
    public boolean existsByDoctorAndSlot(UUID doctorId, LocalDate date, LocalTime time) {
        Instant scheduledInstant = toInstant(date, time);
        ResultSet rs = session.execute(selectStatusByDoctorSlot.bind(doctorId, date, scheduledInstant));
        for (Row row : rs) {
            if (!"CANCELLED".equals(row.getString("status"))) return true;
        }
        return false;
    }

    @Override
    public boolean existsByPatientDoctorAndDate(UUID patientId, UUID doctorId, LocalDate date) {
        ResultSet rs = session.execute(selectStatusByPatientDoctorDate.bind(patientId, date));
        for (Row row : rs) {
            if (doctorId.equals(row.getUuid("doctor_id")) && !"CANCELLED".equals(row.getString("status"))) return true;
        }
        return false;
    }

    @Override
    public void updateStatus(Appointment a, String newStatus) {
        Instant now = Instant.now();
        Instant scheduledInstant = toInstant(a.getAppointmentDate(), a.getScheduleTime());
        UUID clinicId = a.getClinicId() != null ? a.getClinicId() : NO_CLINIC_ID;

        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(updateStatusById.bind(newStatus, now, a.getId()))
                .addStatement(updateStatusByDoctor.bind(newStatus, now, a.getDoctorId(), a.getAppointmentDate(), scheduledInstant, a.getId()))
                .addStatement(deleteByDoctorStatus.bind(a.getDoctorId(), a.getStatus().name(), a.getAppointmentDate(), scheduledInstant, a.getId()))
                .addStatement(insertByDoctorStatus.bind(a.getDoctorId(), newStatus, a.getAppointmentDate(), scheduledInstant,
                        a.getId(), a.getPatientId(), clinicId, a.getPatientName(), a.getPatientPhone(), a.getReasonForVisit()))
                .addStatement(updateStatusByPatient.bind(newStatus, a.getPatientId(), a.getAppointmentDate(), scheduledInstant, a.getId()))
                .addStatement(updateStatusByPatientAll.bind(newStatus, a.getPatientId(), a.getAppointmentDate(), scheduledInstant, a.getId()));

        session.execute(batch.build());
    }

    @Override
    public void cancel(UUID appointmentId, UUID patientId, UUID doctorId,
                       LocalDate date, LocalTime time, String cancellationReason) {
        Instant now = Instant.now();
        Instant scheduledInstant = toInstant(date, time);

        Row current = session.execute(selectById.bind(appointmentId)).one();
        String currentStatus = current != null ? current.getString("status") : "PENDING";
        UUID clinicId = current != null ? current.getUuid("clinic_id") : NO_CLINIC_ID;
        if (clinicId == null) clinicId = NO_CLINIC_ID;

        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(updateCancelById.bind(cancellationReason, now, appointmentId))
                .addStatement(updateCancelByDoctor.bind(cancellationReason, now, doctorId, date, scheduledInstant, appointmentId))
                .addStatement(updateCancelByPatient.bind(cancellationReason, patientId, date, scheduledInstant, appointmentId))
                .addStatement(updateCancelByPatientAll.bind(cancellationReason, patientId, date, scheduledInstant, appointmentId))
                .addStatement(deleteByDoctorStatus.bind(doctorId, currentStatus, date, scheduledInstant, appointmentId))
                .addStatement(insertByDoctorStatus.bind(doctorId, "CANCELLED", date, scheduledInstant, appointmentId, patientId, clinicId, null, null, null))
                .addStatement(updateCount.bind(-1L, doctorId, date));

        session.execute(batch.build());
    }

    @Override
    public void decrementCount(UUID doctorId, LocalDate date) {
        session.execute(updateCount.bind(-1L, doctorId, date));
    }

    @Override
    public void postpone(LocalDate oldDate, Appointment a) {
        Instant now = Instant.now();
        Instant oldInstant = toInstant(oldDate, a.getScheduleTime());
        Instant newInstant = toInstant(a.getAppointmentDate(), a.getScheduleTime());
        String newStatus = AppointmentStatus.APPOINTMENT_STATUS_POSTPONED.name();

        if (a.getPatientName() == null || a.getPatientPhone() == null) {
            Row doctorRow = session.execute(
                    "SELECT patient_name, patient_phone, clinic_id FROM doctor_service.appointments_by_doctor " +
                    "WHERE doctor_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                    a.getDoctorId(), oldDate, oldInstant, a.getId()).one();
            if (doctorRow != null) {
                a.setPatientName(doctorRow.getString("patient_name"));
                a.setPatientPhone(doctorRow.getString("patient_phone"));
                if (a.getClinicId() == null || NO_CLINIC_ID.equals(a.getClinicId())) {
                    a.setClinicId(doctorRow.getUuid("clinic_id") != null ? doctorRow.getUuid("clinic_id") : NO_CLINIC_ID);
                }
            }
        }
        
        if (a.getDoctorName() == null || a.getClinicName() == null) {
            Row patientRow = session.execute(
                    "SELECT doctor_name, clinic_name, specialization, clinic_id FROM doctor_service.appointments_by_patient " +
                    "WHERE patient_id=? AND appointment_date=? AND scheduled_time=? AND appointment_id=?",
                    a.getPatientId(), oldDate, oldInstant, a.getId()).one();
            if (patientRow != null) {
                a.setDoctorName(patientRow.getString("doctor_name"));
                a.setClinicName(patientRow.getString("clinic_name"));
                a.setSpecialization(patientRow.getString("specialization"));
                if (a.getClinicId() == null || NO_CLINIC_ID.equals(a.getClinicId())) {
                    a.setClinicId(patientRow.getUuid("clinic_id") != null ? patientRow.getUuid("clinic_id") : NO_CLINIC_ID);
                }
            }
        }
        
        if (a.getClinicId() == null) a.setClinicId(NO_CLINIC_ID);

        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(updateStatusById.bind(newStatus, now, a.getId()))
                .addStatement(deleteByDoctor.bind(a.getDoctorId(), oldDate, oldInstant, a.getId()))
                .addStatement(insertByDoctor.bind(a.getDoctorId(), a.getAppointmentDate(), newInstant,
                        a.getId(), a.getPatientId(), a.getClinicId(), a.getPatientName(), a.getPatientPhone(),    
                        newStatus, a.getReasonForVisit(), null, null, a.getCreatedAt(), now))
                .addStatement(deleteByDoctorStatus.bind(a.getDoctorId(), a.getStatus().name(), oldDate, oldInstant, a.getId()))
                .addStatement(insertByDoctorStatus.bind(a.getDoctorId(), newStatus, a.getAppointmentDate(), newInstant,
                        a.getId(), a.getPatientId(), a.getClinicId(), a.getPatientName(), a.getPatientPhone(), a.getReasonForVisit()))
                .addStatement(deleteByPatient.bind(a.getPatientId(), oldDate, oldInstant, a.getId()))
                .addStatement(insertByPatient.bind(a.getPatientId(), a.getAppointmentDate(), newInstant, a.getId(), a.getDoctorId(), a.getClinicId(),
                        a.getDoctorName(), a.getClinicName(), a.getSpecialization(), newStatus, a.getReasonForVisit(), null))
                .addStatement(deleteByPatientAll.bind(a.getPatientId(), oldDate, oldInstant, a.getId()))
                .addStatement(insertByPatientAll.bind(a.getPatientId(), a.getAppointmentDate(), newInstant, a.getId(), a.getDoctorId(), a.getClinicId(),
                        a.getDoctorName(), a.getClinicName(), a.getSpecialization(), newStatus, a.getReasonForVisit(), null));

        session.execute(batch.build());
    }
}
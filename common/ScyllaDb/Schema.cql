-- ============================================================
--  FULL SCHEMA — doctor_service keyspace
-- ============================================================

DROP KEYSPACE IF EXISTS doctor_service;

CREATE KEYSPACE doctor_service
    WITH replication = {
        'class': 'SimpleStrategy',
        'replication_factor': 1
        }
     AND durable_writes = true;

USE doctor_service;

-- ============================================================
--  CLINICS
-- ============================================================
CREATE TABLE clinics (
                         clinic_id     UUID,
                         name          TEXT,
                         latitude      DOUBLE,
                         longitude     DOUBLE,
                         geohash       TEXT,
                         location_text TEXT,
                         is_active     BOOLEAN,
                         is_deleted    BOOLEAN,
                         created_at    TIMESTAMP,
                         updated_at    TIMESTAMP,
                         PRIMARY KEY (clinic_id)
);

-- Lookup tables
CREATE TABLE clinics_by_geohash (
                                    geohash    TEXT,
                                    clinic_id  UUID,
                                    name       TEXT,
                                    latitude   DOUBLE,
                                    longitude  DOUBLE,
                                    is_active  BOOLEAN,
                                    is_deleted BOOLEAN,
                                    PRIMARY KEY (geohash, clinic_id)
);

CREATE TABLE clinics_by_location_text (
                                          location_text TEXT,
                                          clinic_id     UUID,
                                          name          TEXT,
                                          is_active     BOOLEAN,
                                          is_deleted    BOOLEAN,
                                          PRIMARY KEY (location_text, clinic_id)
);

-- ============================================================
--  DOCTOR CREDENTIALS  (single source of truth for auth)
-- ============================================================
CREATE TABLE doctor_credentials (
                                    doctor_id     UUID PRIMARY KEY,
                                    email         TEXT,
                                    phone         TEXT,
                                    password_hash TEXT,
                                    created_at    TIMESTAMP,
                                    updated_at    TIMESTAMP
);

-- Email → doctor_id lookup
CREATE TABLE doctors_by_email (
                                  email      TEXT PRIMARY KEY,
                                  doctor_id  UUID
);

-- ============================================================
--  DOCTORS
-- ============================================================
CREATE TABLE doctors (
                         doctor_id      UUID PRIMARY KEY,
                         name           TEXT,
                         clinic_ids     LIST<UUID>,
                         type           TEXT,
                         specialization TEXT,
                         phone          TEXT,
                         is_active      BOOLEAN,
                         is_deleted     BOOLEAN,
                         created_at     TIMESTAMP,
                         updated_at     TIMESTAMP
);

CREATE TABLE doctors_by_clinic (
                                   clinic_id      UUID,
                                   doctor_id      UUID,
                                   name           TEXT,
                                   type           TEXT,
                                   specialization TEXT,
                                   phone          TEXT,
                                   is_active      BOOLEAN,
                                   is_deleted     BOOLEAN,
                                   created_at     TIMESTAMP,
                                   updated_at     TIMESTAMP,
                                   PRIMARY KEY (clinic_id, doctor_id)
);

CREATE TABLE doctors_by_individual (
                                       doctor_id      UUID PRIMARY KEY,
                                       name           TEXT,
                                       type           TEXT,
                                       specialization TEXT,
                                       phone          TEXT,
                                       is_active      BOOLEAN,
                                       is_deleted     BOOLEAN,
                                       updated_at     TIMESTAMP
);

CREATE TABLE doctors_by_location (
                                     geohash_prefix TEXT,
                                     geohash_prefixes LIST<TEXT>,
                                     clinic_id      UUID,
                                     doctor_id      UUID,
                                     name           TEXT,
                                     type           TEXT,
                                     specialization TEXT,
                                     phone          TEXT,
                                     latitude       DOUBLE,
                                     longitude      DOUBLE,
                                     location_text  TEXT,
                                     is_active      BOOLEAN,
                                     PRIMARY KEY (geohash_prefix, clinic_id, doctor_id)
);


CREATE INDEX ON doctor_service.doctors_by_location (VALUES(geohash_prefixes));

-- ============================================================
--  DOCTOR SCHEDULES
-- ============================================================

-- Base weekly schedule (defaults)
CREATE TABLE doctor_schedules (
                                  doctor_id             UUID,
                                  clinic_id             UUID,
                                  working_days          SET<TEXT>,
                                  start_time            TIME,
                                  end_time              TIME,
                                  slot_duration_minutes INT,
                                  max_appointments_day  INT,
                                  created_at            TIMESTAMP,
                                  updated_at            TIMESTAMP,
                                  PRIMARY KEY (doctor_id, clinic_id)
);

-- Per-day override (holiday, different hours, partial day off)
CREATE TABLE doctor_schedule_overrides (
                                           doctor_id   UUID,
                                           override_date DATE,
                                           is_available  BOOLEAN,
                                           start_time    TIME,
                                           end_time      TIME,
                                           max_appointments INT,
                                           reason        TEXT,
                                           created_at    TIMESTAMP,
                                           PRIMARY KEY (doctor_id, override_date)
) WITH CLUSTERING ORDER BY (override_date ASC);

-- Blocked ranges (vacation, leave)
CREATE TABLE doctor_unavailability (
                                       doctor_id  UUID,
                                       start_date DATE,
                                       end_date   DATE,
                                       reason     TEXT,
                                       created_at TIMESTAMP,
                                       PRIMARY KEY (doctor_id, start_date)
) WITH CLUSTERING ORDER BY (start_date ASC);

-- Clinic-level schedule view (for admin)
CREATE TABLE schedules_by_clinic (
                                     clinic_id             UUID,
                                     doctor_id             UUID,
                                     doctor_name           TEXT,
                                     working_days          SET<TEXT>,
                                     start_time            TIME,
                                     end_time              TIME,
                                     slot_duration_minutes INT,
                                     PRIMARY KEY (clinic_id, doctor_id)
);

-- ============================================================
--  PATIENTS
-- ============================================================
CREATE TABLE patients (
                          patient_id    UUID PRIMARY KEY,
                          name          TEXT,
                          email         TEXT,
                          phone         TEXT,
                          password_hash TEXT,
                          is_deleted    BOOLEAN,
                          created_at    TIMESTAMP,
                          updated_at    TIMESTAMP
);

-- Lookup table for login
CREATE TABLE patients_by_email (
                                   email      TEXT PRIMARY KEY,
                                   patient_id UUID
);

-- ============================================================
--  APPOINTMENTS
-- ============================================================

-- Used for cancellations, status checks, reminders, callbacks
CREATE TABLE appointments_by_id (
                                    appointment_id   UUID PRIMARY KEY,
                                    doctor_id        UUID,
                                    patient_id       UUID,
                                    clinic_id        UUID,
                                    appointment_date DATE,
                                    scheduled_time   TIMESTAMP,
                                    status           TEXT,        -- SCHEDULED | CONFIRMED | CANCELLED | COMPLETED | NO_SHOW
                                    reason_for_visit TEXT,
                                    doctor_notes     TEXT,
                                    cancellation_reason TEXT,
                                    created_at       TIMESTAMP,
                                    updated_at       TIMESTAMP
);

-- Doctor's daily schedule view
CREATE TABLE appointments_by_doctor (
                                        doctor_id        UUID,
                                        appointment_date DATE,
                                        scheduled_time   TIMESTAMP,
                                        appointment_id   UUID,
                                        patient_id       UUID,
                                        clinic_id        UUID,
                                        patient_name     TEXT,
                                        patient_phone    TEXT,
                                        status           TEXT,
                                        reason_for_visit TEXT,
                                        doctor_notes     TEXT,
                                        cancellation_reason TEXT,
                                        created_at       TIMESTAMP,
                                        updated_at       TIMESTAMP,
                                        PRIMARY KEY ((doctor_id, appointment_date), scheduled_time, appointment_id)
) WITH CLUSTERING ORDER BY (scheduled_time ASC, appointment_id ASC)
   AND default_time_to_live = 15552000;

-- Doctor + status filter
CREATE TABLE appointments_by_doctor_status (
                                               doctor_id        UUID,
                                               status           TEXT,
                                               appointment_date DATE,
                                               scheduled_time   TIMESTAMP,
                                               appointment_id   UUID,
                                               patient_id       UUID,
                                               clinic_id        UUID,
                                               patient_name     TEXT,
                                               patient_phone    TEXT,
                                               reason_for_visit TEXT,
                                               PRIMARY KEY ((doctor_id, status, appointment_date), scheduled_time, appointment_id)
) WITH CLUSTERING ORDER BY (scheduled_time ASC, appointment_id ASC)
   AND default_time_to_live = 15552000;

-- Patient appointment history
CREATE TABLE appointments_by_patient (
                                         patient_id       UUID,
                                         appointment_date DATE,
                                         scheduled_time   TIMESTAMP,
                                         appointment_id   UUID,
                                         doctor_id        UUID,
                                         clinic_id        UUID,
                                         doctor_name      TEXT,
                                         clinic_name      TEXT,
                                         specialization   TEXT,
                                         status           TEXT,
                                         reason_for_visit TEXT,
                                         cancellation_reason TEXT,
                                         PRIMARY KEY ((patient_id, appointment_date), scheduled_time, appointment_id)
) WITH CLUSTERING ORDER BY (scheduled_time ASC, appointment_id ASC)
   AND default_time_to_live = 15552000;

-- Patient appointment history (all dates)
CREATE TABLE appointments_by_patient_all (
                                         patient_id       UUID,
                                         appointment_date DATE,
                                         scheduled_time   TIMESTAMP,
                                         appointment_id   UUID,
                                         doctor_id        UUID,
                                         clinic_id        UUID,
                                         doctor_name      TEXT,
                                         clinic_name      TEXT,
                                         specialization   TEXT,
                                         status           TEXT,
                                         reason_for_visit TEXT,
                                         cancellation_reason TEXT,
                                         PRIMARY KEY (patient_id, appointment_date, scheduled_time, appointment_id)
) WITH CLUSTERING ORDER BY (appointment_date DESC, scheduled_time DESC, appointment_id ASC)
   AND default_time_to_live = 15552000;

-- ============================================================
--  APPOINTMENT COUNT
-- ============================================================
CREATE TABLE appointment_count_by_doctor_date (
                                                  doctor_id        UUID,
                                                  appointment_date DATE,
                                                  count            COUNTER,
                                                  PRIMARY KEY (doctor_id, appointment_date)
);

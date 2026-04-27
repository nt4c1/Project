package com.health.patient.adapters.input.nats;

import io.micronaut.nats.annotation.NatsListener;
import io.micronaut.nats.annotation.Subject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NatsListener
public class PatientNatsListener {

    @Subject("patient.created")
    public void onPatientCreated(String patientId) {
        log.info("Received NATS message: Patient created with ID: {}", patientId);
    }

    @Subject("patient.updated")
    public void onPatientUpdated(String patientId) {
        log.info("Received NATS message: Patient updated with ID: {}", patientId);
    }

    @Subject("patient.deleted")
    public void onPatientDeleted(String patientId) {
        log.info("Received NATS message: Patient deleted with ID: {}", patientId);
    }

    @Subject("appointment.created")
    public void onAppointmentCreated(String appointmentId) {
        log.info("Received NATS message: Appointment created with ID: {}", appointmentId);
    }

    @Subject("appointment.updated")
    public void onAppointmentUpdated(String appointmentId) {
        log.info("Received NATS message: Appointment updated with ID: {}", appointmentId);
    }
}
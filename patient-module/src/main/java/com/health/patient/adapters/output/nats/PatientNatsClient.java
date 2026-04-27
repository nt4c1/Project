package com.health.patient.adapters.output.nats;

import io.micronaut.nats.annotation.NatsClient;
import io.micronaut.nats.annotation.Subject;

@NatsClient
public interface PatientNatsClient {

    @Subject("patient.created")
    void sendPatientCreated(String patientId);

    @Subject("patient.updated")
    void sendPatientUpdated(String patientId);

    @Subject("patient.deleted")
    void sendPatientDeleted(String patientId);

    @Subject("appointment.created")
    void sendAppointmentCreated(String appointmentId);

    @Subject("appointment.updated")
    void sendAppointmentUpdated(String appointmentId);

}
package com.health.doctor.adapters.output.nats;

import io.micronaut.nats.jetstream.annotation.JetStreamClient;
import io.micronaut.nats.annotation.Subject;
import io.micronaut.serde.annotation.Serdeable;

@JetStreamClient
public interface DoctorNatsClient {

    @Subject("doctor.created")
    void sendDoctorCreated(String doctorId);

    @Subject("doctor.updated")
    void sendDoctorUpdated(String doctorId);

    @Subject("doctor.deleted")
    void sendDoctorDeleted(String doctorId);

    @Subject("clinic.created")
    void sendClinicCreated(String clinicId);

    @Subject("clinic.updated")
    void sendClinicUpdated(String clinicId);

    @Subject("clinic.deleted")
    void sendClinicDeleted(String clinicId);

    @Subject("appointment.created")
    void sendAppointmentCreated(byte[] payload);

    @Subject("appointment.accepted")
    void sendAppointmentAccepted(byte[] payload);

    @Subject("appointment.postponed")
    void sendAppointmentPostponed(byte[] payload);

    @Subject("appointment.cancelled")
    void sendAppointmentCancelled(byte[] payload);

    @Subject("appointment.completed")
    void sendAppointmentCompleted(byte[] payload);

    @Subject("appointment.no-show")
    void sendAppointmentNoShow(byte[] payload);
}

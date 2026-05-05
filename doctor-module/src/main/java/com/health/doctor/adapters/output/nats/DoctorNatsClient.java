package com.health.doctor.adapters.output.nats;

import io.micronaut.nats.annotation.NatsClient;
import io.micronaut.nats.annotation.Subject;
import io.micronaut.serde.annotation.Serdeable;

@NatsClient
@Serdeable.Serializable
public interface DoctorNatsClient {

    @Subject("doctor.created")
    void sendDoctorCreated(String doctorId);

    @Subject("doctor.updated")
    void sendDoctorUpdated(String doctorId);

    @Subject("doctor.deleted")
    void sendDoctorDeleted(String doctorId);

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

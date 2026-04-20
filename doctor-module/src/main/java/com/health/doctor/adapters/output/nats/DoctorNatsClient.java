package com.health.doctor.adapters.output.nats;

import io.micronaut.nats.annotation.NatsClient;
import io.micronaut.nats.annotation.Subject;

@NatsClient
public interface DoctorNatsClient {

    @Subject("doctor.created")
    void sendDoctorCreated(String doctorId);

    @Subject("doctor.updated")
    void sendDoctorUpdated(String doctorId);

    @Subject("appointment.created")
    void sendAppointmentCreated(String appointmentId);

    @Subject("appointment.updated")
    void sendAppointmentUpdated(String appointmentId);

    @Subject("appointment.accepted")
    void sendAppointmentAccepted(String appointmentId);

    @Subject("appointment.postponed")
    void sendAppointmentPostponed(String appointmentId);

    @Subject("appointment.cancelled")
    void sendAppointmentCancelled(String appointmentId);
}

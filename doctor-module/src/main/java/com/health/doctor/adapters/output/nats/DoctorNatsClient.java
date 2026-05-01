package com.health.doctor.adapters.output.nats;

import com.health.grpc.notification.AppointmentAcceptedEvent;
import com.health.grpc.notification.AppointmentBookedEvent;
import com.health.grpc.notification.AppointmentCancelledEvent;
import com.health.grpc.notification.AppointmentPostponedEvent;
import io.micronaut.nats.annotation.NatsClient;
import io.micronaut.nats.annotation.Subject;

@NatsClient
public interface DoctorNatsClient {

    @Subject("doctor.created")
    void sendDoctorCreated(String doctorId);

    @Subject("doctor.updated")
    void sendDoctorUpdated(String doctorId);

    @Subject("doctor.deleted")
    void sendDoctorDeleted(String doctorId);

    @Subject("appointment.created")
    void sendAppointmentCreated(AppointmentBookedEvent event);

    @Subject("appointment.accepted")
    void sendAppointmentAccepted(AppointmentAcceptedEvent event);

    @Subject("appointment.postponed")
    void sendAppointmentPostponed(AppointmentPostponedEvent event);

    @Subject("appointment.cancelled")
    void sendAppointmentCancelled(AppointmentCancelledEvent event);
}

package com.health.patient.adapters.input.nats;

import com.health.grpc.notification.AppointmentAcceptedEvent;
import com.health.grpc.notification.AppointmentBookedEvent;
import com.health.grpc.notification.AppointmentCancelledEvent;
import com.health.grpc.notification.AppointmentCompletedEvent;
import com.health.grpc.notification.AppointmentNoShowEvent;
import com.health.grpc.notification.AppointmentPostponedEvent;
import io.micronaut.nats.jetstream.annotation.JetStreamListener;
import io.micronaut.nats.jetstream.annotation.PushConsumer;
import io.micronaut.nats.annotation.Subject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@JetStreamListener
@PushConsumer("HEALTH")
public class PatientNatsListener {

    @Subject("patient.created")
    @PushConsumer(value = "HEALTH", durable = "patient-created-listener-v2")
    public void onPatientCreated(String patientId) {
        log.info("Received NATS message: Patient created with ID: {}", patientId);
    }

    @Subject("patient.updated")
    @PushConsumer(value = "HEALTH", durable = "patient-updated-listener-v2")
    public void onPatientUpdated(String patientId) {
        log.info("Received NATS message: Patient updated with ID: {}", patientId);
    }

    @Subject("patient.deleted")
    @PushConsumer(value = "HEALTH", durable = "patient-deleted-listener-v2")
    public void onPatientDeleted(String patientId) {
        log.info("Received NATS message: Patient deleted with ID: {}", patientId);
    }

    @Subject("appointment.created")
    @PushConsumer(value = "HEALTH", durable = "patient-appointment-created-listener-v2")
    public void onAppointmentCreated(byte[] payload) {
        try {
            AppointmentBookedEvent event = AppointmentBookedEvent.parseFrom(payload);
            var appt = event.getAppointment();
            log.info("NOTIFICATION: New Appointment Booked! ID: {}, Patient: {}, Doctor: {}, Date: {}, Time: {}",
                    appt.getAppointmentId(), appt.getPatientName(), appt.getDoctorName(), appt.getDate(), appt.getTime());
        } catch (Exception e) {
            log.error("Failed to parse AppointmentBookedEvent", e);
        }
    }

    @Subject("appointment.accepted")
    @PushConsumer(value = "HEALTH", durable = "patient-appointment-accepted-listener-v2")
    public void onAppointmentAccepted(byte[] payload) {
        try {
            AppointmentAcceptedEvent event = AppointmentAcceptedEvent.parseFrom(payload);
            var appt = event.getAppointment();
            log.info("NOTIFICATION: Appointment ACCEPTED! ID: {}, Patient: {}, Doctor: {}, Date: {}, Time: {}",
                    appt.getAppointmentId(), appt.getPatientName(), appt.getDoctorName(), appt.getDate(), appt.getTime());
        } catch (Exception e) {
            log.error("Failed to parse AppointmentAcceptedEvent", e);
        }
    }

    @Subject("appointment.postponed")
    @PushConsumer(value = "HEALTH", durable = "patient-appointment-postponed-listener-v2")
    public void onAppointmentPostponed(byte[] payload) {
        try {
            AppointmentPostponedEvent event = AppointmentPostponedEvent.parseFrom(payload);
            var appt = event.getAppointment();
            log.info("NOTIFICATION: Appointment POSTPONED! ID: {}, From {} To {}, Patient: {}, Doctor: {}",
                    appt.getAppointmentId(), event.getOldDate(), appt.getDate(), appt.getPatientName(), appt.getDoctorName());
        } catch (Exception e) {
            log.error("Failed to parse AppointmentPostponedEvent", e);
        }
    }

    @Subject("appointment.cancelled")
    @PushConsumer(value = "HEALTH", durable = "patient-appointment-cancelled-listener-v2")
    public void onAppointmentCancelled(byte[] payload) {
        try {
            AppointmentCancelledEvent event = AppointmentCancelledEvent.parseFrom(payload);
            var appt = event.getAppointment();
            log.info("NOTIFICATION: Appointment CANCELLED! ID: {}, Reason: {}, CancelledBy: {}",
                    appt.getAppointmentId(), appt.getCancellationReason(), event.getCancelledBy());
        } catch (Exception e) {
            log.error("Failed to parse AppointmentCancelledEvent", e);
        }
    }

    @Subject("appointment.completed")
    @PushConsumer(value = "HEALTH", durable = "patient-appointment-completed-listener-v2")
    public void onAppointmentCompleted(byte[] payload) {
        try {
            AppointmentCompletedEvent event = AppointmentCompletedEvent.parseFrom(payload);
            var appt = event.getAppointment();
            log.info("NOTIFICATION: Appointment COMPLETED! ID: {}, Patient: {}, Doctor: {}, Date: {}",
                    appt.getAppointmentId(), appt.getPatientName(), appt.getDoctorName(), appt.getDate());
        } catch (Exception e) {
            log.error("Failed to parse AppointmentCompletedEvent", e);
        }
    }

    @Subject("appointment.no-show")
    @PushConsumer(value = "HEALTH", durable = "patient-appointment-noshow-listener-v2")
    public void onAppointmentNoShow(byte[] payload) {
        try {
            AppointmentNoShowEvent event = AppointmentNoShowEvent.parseFrom(payload);
            var appt = event.getAppointment();
            log.info("NOTIFICATION: Appointment NO-SHOW! ID: {}, Patient: {}, Doctor: {}, Date: {}",
                    appt.getAppointmentId(), appt.getPatientName(), appt.getDoctorName(), appt.getDate());
        } catch (Exception e) {
            log.error("Failed to parse AppointmentNoShowEvent", e);
        }
    }
}
package com.health.notification.adapter.input.nats;

import com.health.grpc.notification.AppointmentAcceptedEvent;
import com.health.grpc.notification.AppointmentBookedEvent;
import com.health.grpc.notification.AppointmentCancelledEvent;
import com.health.grpc.notification.AppointmentCompletedEvent;
import com.health.grpc.notification.AppointmentNoShowEvent;
import com.health.grpc.notification.AppointmentPostponedEvent;
import com.health.notification.application.service.NotificationService;
import io.micronaut.nats.jetstream.annotation.JetStreamListener;
import io.micronaut.nats.jetstream.annotation.PushConsumer;
import io.micronaut.nats.annotation.Subject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@JetStreamListener
@PushConsumer("HEALTH")
public class NotificationNatsListener {

    private final NotificationService notificationService;

    public NotificationNatsListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Subject("appointment.created")
    @PushConsumer(value = "HEALTH", durable = "notification-appointment-created-listener-v2")
    public void onAppointmentCreated(byte[] payload) {
        try {
            AppointmentBookedEvent event = AppointmentBookedEvent.parseFrom(payload);
            log.info("Received appointment.created event for patient: {}", event.getAppointment().getPatientId());
            String message = String.format("Dear Patient, your appointment for %s has been booked successfully.", 
                    event.getAppointment().getDate());
            notificationService.sendSms("Patient-" + event.getAppointment().getPatientId(), message);
            notificationService.sendEmail("Doctor-", event.getAppointment().getDoctorId(), message);
        } catch (Exception e) {
            log.error("Failed to parse AppointmentBookedEvent", e);
        }
    }

    @Subject("appointment.accepted")
    @PushConsumer(value = "HEALTH", durable = "notification-appointment-accepted-listener-v2")
    public void onAppointmentAccepted(byte[] payload) {
        try {
            AppointmentAcceptedEvent event = AppointmentAcceptedEvent.parseFrom(payload);
            log.info("Received appointment.accepted event for patient: {}", event.getAppointment().getPatientId());
            String message = String.format("Great news! Your appointment for %s has been ACCEPTED by the doctor.", 
                    event.getAppointment().getDate());
            notificationService.sendSms("Patient-" + event.getAppointment().getPatientId(), message);
            notificationService.sendEmail("Patient-", event.getAppointment().getDoctorId(), message);
        } catch (Exception e) {
            log.error("Failed to parse AppointmentAcceptedEvent", e);
        }
    }

    @Subject("appointment.postponed")
    @PushConsumer(value = "HEALTH", durable = "notification-appointment-postponed-listener-v2")
    public void onAppointmentPostponed(byte[] payload) {
        try {
            AppointmentPostponedEvent event = AppointmentPostponedEvent.parseFrom(payload);
            log.info("Received appointment.postponed event for patient: {}", event.getAppointment().getPatientId());
            String message = String.format("Your appointment has been postponed from %s to %s.", 
                    event.getOldDate(), event.getAppointment().getDate());
            notificationService.sendEmail("Doctor-", event.getAppointment().getDoctorId(), message);
            notificationService.sendSms("Patient-" + event.getAppointment().getPatientId(), message);
        } catch (Exception e) {
            log.error("Failed to parse AppointmentPostponedEvent", e);
        }
    }

    @Subject("appointment.cancelled")
    @PushConsumer(value = "HEALTH", durable = "notification-appointment-cancelled-listener-v2")
    public void onAppointmentCancelled(byte[] payload) {
        try {
            AppointmentCancelledEvent event = AppointmentCancelledEvent.parseFrom(payload);
            log.info("Received appointment.cancelled event for patient: {}", event.getAppointment().getPatientId());
            String message = String.format("Your appointment for %s has been CANCELLED by %s.", 
                    event.getAppointment().getDate(), event.getCancelledBy());
            notificationService.sendSms("Patient-" + event.getAppointment().getPatientId(), message);
        } catch (Exception e) {
            log.error("Failed to parse AppointmentCancelledEvent", e);
        }
    }

    @Subject("appointment.completed")
    @PushConsumer(value = "HEALTH", durable = "notification-appointment-completed-listener-v2")
    public void onAppointmentCompleted(byte[] payload) {
        try {
            AppointmentCompletedEvent event = AppointmentCompletedEvent.parseFrom(payload);
            log.info("Received appointment.completed event for patient: {}", event.getAppointment().getPatientId());
            String message = String.format("Your appointment for %s has been marked as COMPLETED. Thank you!", 
                    event.getAppointment().getDate());
            notificationService.sendSms("Patient-" + event.getAppointment().getPatientId(), message);
        } catch (Exception e) {
            log.error("Failed to parse AppointmentCompletedEvent", e);
        }
    }

    @Subject("appointment.no-show")
    @PushConsumer(value = "HEALTH", durable = "notification-appointment-noshow-listener-v2")
    public void onAppointmentNoShow(byte[] payload) {
        try {
            AppointmentNoShowEvent event = AppointmentNoShowEvent.parseFrom(payload);
            log.info("Received appointment.no-show event for patient: {}", event.getAppointment().getPatientId());
            String message = String.format("Your appointment for %s was marked as NO-SHOW.", 
                    event.getAppointment().getDate());
            notificationService.sendSms("Patient-" + event.getAppointment().getPatientId(), message);
        } catch (Exception e) {
            log.error("Failed to parse AppointmentNoShowEvent", e);
        }
    }
}

package com.health.patient.adapters.input.nats;

import com.health.grpc.notification.AppointmentAcceptedEvent;
import com.health.grpc.notification.AppointmentBookedEvent;
import com.health.grpc.notification.AppointmentCancelledEvent;
import com.health.grpc.notification.AppointmentCompletedEvent;
import com.health.grpc.notification.AppointmentNoShowEvent;
import com.health.grpc.notification.AppointmentPostponedEvent;
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
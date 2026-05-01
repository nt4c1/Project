package com.health.patient.adapters.input.nats;

import com.health.grpc.notification.AppointmentAcceptedEvent;
import com.health.grpc.notification.AppointmentBookedEvent;
import com.health.grpc.notification.AppointmentCancelledEvent;
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
    public void onAppointmentCreated(AppointmentBookedEvent event) {
        var appt = event.getAppointment();
        log.info("NOTIFICATION: New Appointment Booked! ID: {}, Patient: {}, Doctor: {}, Date: {}, Time: {}",
                appt.getAppointmentId(), appt.getPatientName(), appt.getDoctorName(), appt.getDate(), appt.getTime());
    }

    @Subject("appointment.accepted")
    public void onAppointmentAccepted(AppointmentAcceptedEvent event) {
        var appt = event.getAppointment();
        log.info("NOTIFICATION: Appointment ACCEPTED! ID: {}, Patient: {}, Doctor: {}, Date: {}, Time: {}",
                appt.getAppointmentId(), appt.getPatientName(), appt.getDoctorName(), appt.getDate(), appt.getTime());
    }

    @Subject("appointment.postponed")
    public void onAppointmentPostponed(AppointmentPostponedEvent event) {
        var appt = event.getAppointment();
        log.info("NOTIFICATION: Appointment POSTPONED! ID: {}, From {} To {}, Patient: {}, Doctor: {}",
                appt.getAppointmentId(), event.getOldDate(), appt.getDate(), appt.getPatientName(), appt.getDoctorName());
    }

    @Subject("appointment.cancelled")
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        var appt = event.getAppointment();
        log.info("NOTIFICATION: Appointment CANCELLED! ID: {}, Reason: {}, CancelledBy: {}",
                appt.getAppointmentId(), appt.getCancellationReason(), event.getCancelledBy());
    }
}
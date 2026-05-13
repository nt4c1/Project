package com.health.admin.adapter.input.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.redis.RedisUtil;
import com.health.grpc.notification.AppointmentBookedEvent;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.nats.jetstream.annotation.JetStreamListener;
import io.micronaut.nats.jetstream.annotation.PushConsumer;
import io.micronaut.nats.annotation.Subject;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;

@Slf4j
@Singleton
@JetStreamListener
@PushConsumer("HEALTH")
public class AdminNatsListener {

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private static final String KEY_EVENTS = "stats:events";
    private static final int MAX_EVENTS = 100;
    private volatile boolean shuttingDown = false;

    public AdminNatsListener(RedisUtil redisUtil, 
                             ObjectMapper objectMapper,
                             @Named(TaskExecutors.IO) ExecutorService executorService) {
        this.redisUtil = redisUtil;
        this.objectMapper = objectMapper;
        this.executorService = executorService;
        log.info("AdminNatsListener constructor called");
    }

    @Subject("doctor.created")
    @PushConsumer(value = "HEALTH", durable = "admin-doctor-created-listener-v2")
    public void onDoctorCreated(String doctorId) {
        log.info("NATS Received [doctor.created]: {}", doctorId);
        redisUtil.increment("stats:total:doctors", 0);
        addEvent("DOCTOR_CREATED", "Doctor ID: " + doctorId);
    }

    @Subject("doctor.updated")
    @PushConsumer(value = "HEALTH", durable = "admin-doctor-updated-listener-v2")
    public void onDoctorUpdated(String doctorId) {
        log.info("NATS Received [doctor.updated]: {}", doctorId);
        addEvent("DOCTOR_UPDATED", "Doctor ID: " + doctorId);
    }

    @Subject("doctor.deleted")
    @PushConsumer(value = "HEALTH", durable = "admin-doctor-deleted-listener-v2")
    public void onDoctorDeleted(String doctorId) {
        log.info("NATS Received [doctor.deleted]: {}", doctorId);
        redisUtil.decrement("stats:total:doctors");
        addEvent("DOCTOR_DELETED", "Doctor ID: " + doctorId);
    }

    @Subject("patient.created")
    @PushConsumer(value = "HEALTH", durable = "admin-patient-created-listener-v2")
    public void onPatientCreated(String patientId) {
        log.info("NATS Received [patient.created]: {}", patientId);
        Long newVal = redisUtil.increment("stats:total:patients", 0);
        log.info("Incremented stats:total:patients. New value: {}", newVal);
        addEvent("PATIENT_CREATED", "Patient ID: " + patientId);
    }

    @Subject("patient.updated")
    @PushConsumer(value = "HEALTH", durable = "admin-patient-updated-listener-v2")
    public void onPatientUpdated(String patientId) {
        log.info("NATS Received [patient.updated]: {}", patientId);
        addEvent("PATIENT_UPDATED", "Patient ID: " + patientId);
    }

    @Subject("patient.deleted")
    @PushConsumer(value = "HEALTH", durable = "admin-patient-deleted-listener-v2")
    public void onPatientDeleted(String patientId) {
        log.info("NATS Received [patient.deleted]: {}", patientId);
        redisUtil.decrement("stats:total:patients");
        addEvent("PATIENT_DELETED", "Patient ID: " + patientId);
    }

    @Subject("clinic.created")
    @PushConsumer(value = "HEALTH", durable = "admin-clinic-created-listener-v2")
    public void onClinicCreated(String clinicId) {
        log.info("NATS Received [clinic.created]: {}", clinicId);
        redisUtil.increment("stats:total:clinics", 0);
        addEvent("CLINIC_CREATED", "Clinic ID: " + clinicId);
    }

    @Subject("clinic.updated")
    @PushConsumer(value = "HEALTH", durable = "admin-clinic-updated-listener-v2")
    public void onClinicUpdated(String clinicId) {
        log.info("NATS Received [clinic.updated]: {}", clinicId);
        addEvent("CLINIC_UPDATED", "Clinic ID: " + clinicId);
    }

    @Subject("clinic.deleted")
    @PushConsumer(value = "HEALTH", durable = "admin-clinic-deleted-listener-v2")
    public void onClinicDeleted(String clinicId) {
        log.info("NATS Received [clinic.deleted]: {}", clinicId);
        redisUtil.decrement("stats:total:clinics");
        addEvent("CLINIC_DELETED", "Clinic ID: " + clinicId);
    }

    @Subject("appointment.created")
    @PushConsumer(value = "HEALTH", durable = "admin-appointment-created-listener-v2")
    public void onAppointmentCreated(byte[] payload) {
        try {
            AppointmentBookedEvent event = AppointmentBookedEvent.parseFrom(payload);
            String clinicId = event.getAppointment().getClinicId();
            String patientId = event.getAppointment().getPatientId();
            
            log.info("NATS Received [appointment.created] ID: {} for Clinic: {}", 
                    event.getAppointment().getAppointmentId(), clinicId);

            redisUtil.increment("stats:total:appointments", 0);
            if (clinicId != null && !clinicId.isBlank()) {
                redisUtil.increment("stats:clinic:" + clinicId + ":appointments", 0);
                if (patientId != null && !patientId.isBlank()) {
                    redisUtil.sadd("stats:clinic:" + clinicId + ":patients", patientId);
                }
            }

            addEvent("APPOINTMENT_CREATED", "Appt ID: " + event.getAppointment().getAppointmentId());
        } catch (Exception e) {
            log.error("Failed to process appointment.created event", e);
        }
    }

    @Subject("appointment.accepted")
    @PushConsumer(value = "HEALTH", durable = "admin-appointment-accepted-listener-v2")
    public void onAppointmentAccepted(byte[] payload) {
        try {
            com.health.grpc.notification.AppointmentAcceptedEvent event = com.health.grpc.notification.AppointmentAcceptedEvent.parseFrom(payload);
            log.info("NATS Received [appointment.accepted] ID: {}", event.getAppointment().getAppointmentId());
            addEvent("APPOINTMENT_ACCEPTED", "Appt ID: " + event.getAppointment().getAppointmentId());
        } catch (Exception e) {
            log.error("Failed to process appointment.accepted event", e);
        }
    }

    @Subject("appointment.postponed")
    @PushConsumer(value = "HEALTH", durable = "admin-appointment-postponed-listener-v2")
    public void onAppointmentPostponed(byte[] payload) {
        try {
            com.health.grpc.notification.AppointmentPostponedEvent event = com.health.grpc.notification.AppointmentPostponedEvent.parseFrom(payload);
            log.info("NATS Received [appointment.postponed] ID: {}", event.getAppointment().getAppointmentId());
            addEvent("APPOINTMENT_POSTPONED", "Appt ID: " + event.getAppointment().getAppointmentId());
        } catch (Exception e) {
            log.error("Failed to process appointment.postponed event", e);
        }
    }

    @Subject("appointment.cancelled")
    @PushConsumer(value = "HEALTH", durable = "admin-appointment-cancelled-listener-v2")
    public void onAppointmentCancelled(byte[] payload) {
        try {
            com.health.grpc.notification.AppointmentCancelledEvent event = com.health.grpc.notification.AppointmentCancelledEvent.parseFrom(payload);
            log.info("NATS Received [appointment.cancelled] ID: {} by {}", 
                    event.getAppointment().getAppointmentId(), event.getCancelledBy());
            
            redisUtil.decrement("stats:total:appointments");
            String clinicId = event.getAppointment().getClinicId();
            if (clinicId != null && !clinicId.isBlank()) {
                redisUtil.decrement("stats:clinic:" + clinicId + ":appointments");
            }

            addEvent("APPOINTMENT_CANCELLED", "Appt ID: " + event.getAppointment().getAppointmentId() + " by " + event.getCancelledBy());
        } catch (Exception e) {
            log.error("Failed to process appointment.cancelled event", e);
        }
    }

    @Subject("appointment.completed")
    @PushConsumer(value = "HEALTH", durable = "admin-appointment-completed-listener-v2")
    public void onAppointmentCompleted(byte[] payload) {
        try {
            com.health.grpc.notification.AppointmentCompletedEvent event = com.health.grpc.notification.AppointmentCompletedEvent.parseFrom(payload);
            log.info("NATS Received [appointment.completed] ID: {}", event.getAppointment().getAppointmentId());
            addEvent("APPOINTMENT_COMPLETED", "Appt ID: " + event.getAppointment().getAppointmentId());
        } catch (Exception e) {
            log.error("Failed to process appointment.completed event", e);
        }
    }

    @Subject("appointment.no-show")
    @PushConsumer(value = "HEALTH", durable = "admin-appointment-noshow-listener-v2")
    public void onAppointmentNoShow(byte[] payload) {
        try {
            com.health.grpc.notification.AppointmentNoShowEvent event = com.health.grpc.notification.AppointmentNoShowEvent.parseFrom(payload);
            log.info("NATS Received [appointment.no-show] ID: {}", event.getAppointment().getAppointmentId());
            addEvent("APPOINTMENT_NO_SHOW", "Appt ID: " + event.getAppointment().getAppointmentId());
        } catch (Exception e) {
            log.error("Failed to process appointment.no-show event", e);
        }
    }

    @Subject("appointment.updated")
    @PushConsumer(value = "HEALTH", durable = "admin-appointment-updated-listener-v2")
    public void onAppointmentUpdated(String appointmentId) {
        log.info("NATS Received [appointment.updated]: {}", appointmentId);
        addEvent("APPOINTMENT_UPDATED", "Appt ID: " + appointmentId);
    }

    private void addEvent(String type, String details) {
        if (shuttingDown) {
            log.warn("Skipping event logging, system is shutting down: {} - {}", type, details);
            return;
        }
        log.debug("Queueing event to Redis: {} - {}", type, details);
        executorService.submit(() -> {
            try {
                if (shuttingDown) return;
                Map<String, String> eventMap = new HashMap<>();
                eventMap.put("timestamp", Instant.now().toString());
                eventMap.put("event_type", type);
                eventMap.put("details", details);
                String json = objectMapper.writeValueAsString(eventMap);
                redisUtil.lpush(KEY_EVENTS, json, MAX_EVENTS);
                log.info("Successfully added event to Redis: {}", type);
            } catch (Exception e) {
                log.warn("Logging to Redis failed for event {}: {}", type, e.getMessage());
            }
        });
    }
}
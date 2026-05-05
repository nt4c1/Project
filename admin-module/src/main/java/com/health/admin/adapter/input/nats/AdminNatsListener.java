package com.health.admin.adapter.input.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.redis.RedisUtil;
import com.health.grpc.notification.AppointmentBookedEvent;
import io.micronaut.nats.annotation.NatsListener;
import io.micronaut.nats.annotation.Subject;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@NatsListener
public class AdminNatsListener {

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;
    private static final String KEY_TOTAL_DOCTORS = "stats:total:doctors";
    private static final String KEY_TOTAL_PATIENTS = "stats:total:patients";
    private static final String KEY_TOTAL_APPTS = "stats:total:appointments";
    private static final String KEY_EVENTS = "stats:events";
    private static final int MAX_EVENTS = 100;

    public AdminNatsListener(RedisUtil redisUtil, ObjectMapper objectMapper) {
        this.redisUtil = redisUtil;
        this.objectMapper = objectMapper;
    }

    @Subject("doctor.created")
    public void onDoctorCreated(String doctorId) {
        redisUtil.increment(KEY_TOTAL_DOCTORS, 0);
        addEvent("DOCTOR_CREATED", "Doctor ID: " + doctorId);
    }

    @Subject("patient.created")
    public void onPatientCreated(String patientId) {
        redisUtil.increment(KEY_TOTAL_PATIENTS, 0);
        addEvent("PATIENT_CREATED", "Patient ID: " + patientId);
    }

    @Subject("appointment.created")
    public void onAppointmentCreated(byte[] payload) {
        try {
            AppointmentBookedEvent event = AppointmentBookedEvent.parseFrom(payload);
            redisUtil.increment(KEY_TOTAL_APPTS, 0);
            addEvent("APPOINTMENT_CREATED", "Appt ID: " + event.getAppointment().getAppointmentId());
        } catch (Exception e) {
            log.error("Failed to parse AppointmentBookedEvent", e);
        }
    }

    private void addEvent(String type, String details) {
        try {
            Map<String, String> eventMap = new HashMap<>();
            eventMap.put("timestamp", Instant.now().toString());
            eventMap.put("event_type", type);
            eventMap.put("details", details);
            
            String json = objectMapper.writeValueAsString(eventMap);
            redisUtil.lpush(KEY_EVENTS, json, MAX_EVENTS);
            log.debug("Admin Event added: {}", json);
        } catch (Exception e) {
            log.error("Failed to store event in Redis", e);
        }
    }
}


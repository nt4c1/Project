package com.health.notification.application.service;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class NotificationService {

    public void sendEmail(String to, String subject, String body) {
        log.info("Sending EMAIL to: {} | Subject: {} | Body: {}", to, subject, body);
    }

    public void sendSms(String phone, String message) {
        log.info("Sending SMS to: {} | Message: {}", phone, message);
    }
}

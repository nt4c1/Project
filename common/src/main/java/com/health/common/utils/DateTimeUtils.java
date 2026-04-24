package com.health.common.utils;

import jakarta.inject.Singleton;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Singleton
public class DateTimeUtils {
    public static final ZoneId NPT = ZoneId.of("Asia/Kathmandu");

    public static ZonedDateTime now() {
        return ZonedDateTime.now(NPT);
    }
}

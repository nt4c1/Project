package com.health.patient.mapper;

import com.datastax.oss.driver.api.core.cql.Row;
import com.health.patient.domain.model.Patient;

import java.time.Instant;
import java.time.ZoneId;

public class Mapper {

    private static final ZoneId NPT = ZoneId.of("Asia/Kathmandu");

    public static Patient mapRow(Row r) {
        return new Patient(
                r.getUuid("patient_id"),
                r.getString("name"),
                r.getString("email"),
                r.getString("phone"),
                r.getString("password_hash"),
                r.getBoolean("is_deleted"),
                r.getInstant("created_at"),
                r.getInstant("updated_at")
        );
    }

    public static String ToNptTime (Instant time){
        if (time == null)
            return "";
        return time.atZone(NPT).toLocalTime().toString();
    }

    public static String toNptDate (Instant instant){
        if (instant == null)
            return "";
        return instant.atZone(NPT).toLocalDate().toString();
    }
}

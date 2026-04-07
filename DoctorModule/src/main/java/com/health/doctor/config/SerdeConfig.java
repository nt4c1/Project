package com.health.doctor.config;

import com.health.doctor.domain.model.*;
import io.micronaut.context.annotation.Factory;
import io.micronaut.serde.annotation.SerdeImport;

@Factory
@SerdeImport(Doctor.class)
@SerdeImport(DoctorSchedule.class)
@SerdeImport(Appointment.class)
@SerdeImport(Location.class)
public class SerdeConfig {
}
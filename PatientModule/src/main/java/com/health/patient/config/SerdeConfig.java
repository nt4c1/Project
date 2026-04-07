package com.health.patient.config;

import com.health.patient.domain.model.Patient;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(Patient.class)
public class SerdeConfig {
}
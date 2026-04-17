package com.health.doctor.domain.ports;

import java.util.Optional;
import java.util.UUID;

public interface PatientLookUpPort {

        Optional<PatientSummary> findById(UUID patientId);

        record PatientSummary(UUID id, String name, String phone) {}

}

package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.doctor.domain.ports.PatientLookUpPort;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class PatientLookUpImpl implements PatientLookUpPort {
    private final CqlSession session;

    public PatientLookUpImpl(CqlSession session) {
        this.session = session;
    }


    @Override
    public Optional<PatientSummary> findById(UUID patientId) {
        Row r = session.execute(
                "SELECT patient_id, name, phone FROM doctor_service.patients WHERE patient_id=?",
                patientId
        ).one();
        if (r == null) return Optional.empty();
        return Optional.of(new PatientSummary(
                r.getUuid("patient_id"),
                r.getString("name"),
                r.getString("phone")
        ));
    }
}

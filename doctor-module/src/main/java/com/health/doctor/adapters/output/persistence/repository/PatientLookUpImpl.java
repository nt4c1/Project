package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.doctor.domain.ports.PatientLookUpPort;
import com.health.doctor.mapper.MapperClass;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class PatientLookUpImpl implements PatientLookUpPort {
    private final CqlSession session;
    private final PreparedStatement selectPatientSummary;

    public PatientLookUpImpl(CqlSession session) {
        this.session = session;
        this.selectPatientSummary = session.prepare(
                "SELECT patient_id, name, phone FROM doctor_service.patients WHERE patient_id=?"
        );
    }


    @Override
    public Optional<PatientSummary> findById(UUID patientId) {
        Row r = session.execute(selectPatientSummary.bind(patientId)).one();
        return Optional.ofNullable(MapperClass.mapRowToPatientSummary(r));
    }
}

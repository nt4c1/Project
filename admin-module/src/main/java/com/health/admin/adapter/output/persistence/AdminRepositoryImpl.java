package com.health.admin.adapter.output.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.admin.domain.model.Admin;
import com.health.admin.domain.ports.AdminRepositoryPort;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class AdminRepositoryImpl implements AdminRepositoryPort {

    private final CqlSession session;
    private final PreparedStatement selectByEmail;
    private final PreparedStatement selectById;
    private final PreparedStatement countDoctors;
    private final PreparedStatement countPatients;
    private final PreparedStatement countClinics;
    private final PreparedStatement countAppointments;

    public AdminRepositoryImpl(CqlSession session) {
        this.session = session;
        this.selectByEmail = session.prepare("SELECT admin_id FROM doctor_service.admins_by_email WHERE email = ?");
        this.selectById = session.prepare("SELECT * FROM doctor_service.admins WHERE admin_id = ?");
        this.countDoctors = session.prepare("SELECT count(*) FROM doctor_service.doctors");
        this.countPatients = session.prepare("SELECT count(*) FROM doctor_service.patients");
        this.countClinics = session.prepare("SELECT count(*) FROM doctor_service.clinics");
        this.countAppointments = session.prepare("SELECT count(*) FROM doctor_service.appointments_by_id");
    }

    @Override
    public Optional<Admin> findByEmail(String email) {
        ResultSet rs = session.execute(selectByEmail.bind(email));
        Row row = rs.one();
        if (row == null) return Optional.empty();

        return findById(row.getUuid("admin_id"));
    }

    @Override
    public Optional<Admin> findById(java.util.UUID id) {
        ResultSet rs = session.execute(selectById.bind(id));
        Row row = rs.one();
        if (row == null) return Optional.empty();

        return Optional.of(new Admin(
                row.getUuid("admin_id"),
                row.getString("name"),
                row.getString("email"),
                row.getString("password_hash"),
                row.getInstant("created_at"),
                row.getInstant("updated_at")
        ));
    }

    @Override
    public long countDoctors() {
        ResultSet rs = session.execute(countDoctors.bind());
        Row row = rs.one();
        return row != null ? row.getLong(0) : 0L;
    }

    @Override
    public long countPatients() {
        ResultSet rs = session.execute(countPatients.bind());
        Row row = rs.one();
        return row != null ? row.getLong(0) : 0L;
    }

    @Override
    public long countClinics() {
        ResultSet rs = session.execute(countClinics.bind());
        Row row = rs.one();
        return row != null ? row.getLong(0) : 0L;
    }

    @Override
    public long countAppointments() {
        ResultSet rs = session.execute(countAppointments.bind());
        Row row = rs.one();
        return row != null ? row.getLong(0) : 0L;
    }
}

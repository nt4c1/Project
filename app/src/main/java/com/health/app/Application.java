package com.health.app;

import io.micronaut.runtime.Micronaut;

/**
 * Single entry point for the modular monolith.
 * What runs on startup:
 *   - One gRPC server on port 50051 (configured in application.yml)
 *   - DoctorGrpcApi    registered as a gRPC service
 *   - PatientGrpcApi   registered as a gRPC service
 *   - One CqlSession   shared by both modules
 *   - One JwtProvider  shared by both modules
 */
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}

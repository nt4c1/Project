# Health Service (gRPC + Micronaut + ScyllaDB)

This is a multi-module Micronaut project providing Healthcare services via gRPC.

## Modules
- `common`: Proto definitions, shared models, and security utilities.
- `doctor-module`: Management of doctors, clinics, schedules, and nearby search.
- `patient-module`: Management of patients and appointment booking.

## Prerequisites
- **JDK 17+**
- **ScyllaDB / Cassandra** (Running on `172.17.0.3:9042` or update `application.properties`)
- **NATS** (Running on `localhost:4223`)
- **Redis** (Running on `localhost:6379`)

## Running the Application

To clean and run the application from the root directory:

```bash
./gradlew clean run
```

The server will start on:
- **gRPC**: `localhost:50051`
- **HTTP**: `localhost:8080`

## gRPC Connection (Kreya / Postman)
Ensure that you connect using **Plaintext (h2c)** as TLS is not enabled by default.

- **URL**: `http://localhost:50051`
- **Encryption**: `None` or `Plaintext`
- **Prior Knowledge**: Enabled (if using Kreya)

## Nearby Doctor Search Fix
The `GetNearbyDoctors` RPC has been updated to:
1. Search across multiple geohash precisions (6 down to 4).
2. Return all doctors found, sorted by distance in kilometers.
3. Fix the "zero distance" bug by using high-precision coordinates from the database.

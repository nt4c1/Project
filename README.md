# Health Service (gRPC + Micronaut + ScyllaDB)

This is a multi-module Micronaut project providing Healthcare services via gRPC.

## Modules
- `common`: Proto definitions, shared models, and security utilities.
- `doctor-module`: Management of doctors, clinics, schedules, and nearby search.
- `patient-module`: Management of patients and appointment booking.

## Prerequisites
- **JDK 17+**
- **ScyllaDB / Cassandra** (Running on `localhost:9042` or update `application.properties`)
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

## Security: Basic Authentication
Authentication for `GenerateToken` and `PatientLogin` has been moved from the request body to gRPC **Basic Auth metadata**.
- **Username**: User email
- **Password**: User password
- The `TokenRequest` message in `.proto` files is now empty.

## Redis Caching Strategy
The system uses a "cache-aside" pattern for high-performance data retrieval:
- **`doctors` Cache**: Stores doctor profile information (1-hour TTL). Automatically invalidated on profile updates.
- **`doctor-locations` Cache**: Stores results for nearby and geohash-based searches (10-minute TTL). Invalidated whenever any doctor's location is updated to ensure search accuracy.

## Nearby Doctor Search
The `GetNearbyDoctors` RPC features:
1. Multi-precision geohash search (6 down to 4).
2. Result accumulation and sorting by distance in kilometers.
3. High-precision coordinate resolution for accurate proximity calculations.

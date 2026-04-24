# Health Management System (HMS)

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/your-repo/health-service)
[![Micronaut](https://img.shields.io/badge/framework-Micronaut%204.x-blue)](https://micronaut.io/)
[![gRPC](https://img.shields.io/badge/protocol-gRPC-orange)](https://grpc.io/)
[![ScyllaDB](https://img.shields.io/badge/database-ScyllaDB-cyan)](https://www.scylladb.com/)
[![Redis](https://img.shields.io/badge/cache-Redis-red)](https://redis.io/)
[![NATS](https://img.shields.io/badge/messaging-NATS-purple)](https://nats.io/)

A high-performance, distributed Healthcare Management System engineered for scalability and low-latency doctor-patient interactions. Built on a modern **Cloud-Native** stack using **Micronaut**, **gRPC**, and **ScyllaDB**, this system handles real-time doctor discovery, clinic orchestration, and intelligent appointment scheduling.

## 🏛️ System Architecture

The project adheres to **Hexagonal Architecture (Ports and Adapters)** and **Domain-Driven Design (DDD)** principles, ensuring a clean separation of concerns and high testability.

```mermaid
graph TD
    subgraph "Clients"
        A[Mobile App] --> |gRPC / JWT| G
        B[Web Dashboard] --> |gRPC / JWT| G
    end

    subgraph "gRPC Gateway"
        G[Doctor & Patient Grpc Services]
    end

    subgraph "Core Business Logic"
        G --> |Use Cases| UC[Application Layer]
        UC --> |Enrichment| LS[Location Service]
    end

    subgraph "Infrastructure & Persistence"
        UC --> |CQL| DB[(ScyllaDB Cluster)]
        UC --> |Async| RD[(Redis Cache)]
        UC --> |Pub/Sub| NT((NATS JetStream))
        LS --> |HTTP| PH[Photon Geocoding]
    end
```

### Module Breakdown
- **`common`**: Shared Protobuf definitions, security utilities (JWT, BCrypt), and cross-cutting concerns (Validation, DateTime).
- **`doctor-module`**: Manages doctor profiles, clinic rosters, complex schedules, and geographic search indices.
- **`patient-module`**: Handles patient lifecycle, appointment history, and profile management.
- **`app`**: Main entry point for the Micronaut application, responsible for DI container initialization and global configuration.

---

## 🚀 Key Features

### 👨‍⚕️ Intelligent Doctor Discovery
- **Geographic Search**: Real-time nearby doctor lookup using multi-precision **Geohash indexing** (4-6 characters) for optimized spatial queries.
- **Dynamic Availability**: Automated "Available Today" status and "Next Possible Date" calculations based on complex weekly schedules and overrides.
- **Proximity Ranking**: Accurate distance calculations using the **Haversine formula** for localized results.

### 📅 Advanced Scheduling Engine
- **Atomic Bookings**: Prevents double-booking at the database layer using ScyllaDB's high-concurrency model.
- **Capacity Controls**: Enforces `maxAppointmentsPerDay` and strict `slotDuration` alignment.
- **Status Lifecycle**: Full appointment lifecycle management: `PENDING` → `ACCEPTED` → `COMPLETED` / `CANCELLED` / `POSTPONED`.

### ⚡ Performance & Scalability
- **Event-Driven Core**: Decoupled micro-modules communicating via **NATS** for asynchronous state updates.
- **Optimized Caching**: Cache-Aside pattern with Redis for doctor profiles (1h TTL) and location results (10m TTL).
    - *Update*: Now supports **Java 8 Date/Time types** (JSR-310) for seamless serialization of `Instant` and `LocalDate`.
    - Features **Asynchronous Redis operations** for non-blocking I/O.
- **Database Optimization**: 
    - Full implementation of **Prepared Statements** across all repositories (`Doctor`, `Patient`, `Clinic`, `Credentials`) to minimize query parsing overhead on ScyllaDB.
    - **Advanced Schema Design**: Implemented a dual-table strategy (`appointments_by_patient` and `appointments_by_patient_all`) to provide optimized views for both date-specific and patient-wide history lookups, eliminating the need for expensive `ALLOW FILTERING` queries.
    - Optimized use of `BatchStatement` for atomic, multi-table denormalized writes, ensuring consistency across replicated views.

---

## 🗺️ Future Roadmap
- [ ] **MapStruct Integration**: Replace manual boilerplate mappers with compile-time generated mappers for better performance and maintainability.
- [ ] **Observability**: Integrate **OpenTelemetry** for distributed tracing across gRPC and NATS boundaries.
- [ ] **Resilience**: Implement `@CircuitBreaker` and `@Retryable` patterns for external HTTP clients (Photon).
- [ ] **Centralized Config**: Migrate to Gradle Version Catalogs (`libs.versions.toml`) for standardized dependency management.

---

## 🛠️ Technology Stack

| Component | Technology | Purpose |
| :--- | :--- | :--- |
| **Framework** | Micronaut | Lightweight, AOT-compiled JVM framework |
| **API Protocol** | gRPC | High-performance, type-safe binary communication |
| **Primary DB** | ScyllaDB | Cassandra-compatible, low-latency NoSQL database |
| **Caching** | Redis (Lettuce) | Distributed caching and locking |
| **Messaging** | NATS | High-speed, lightweight messaging system |
| **Geocoding** | Photon (Client) | Address-to-coordinate resolution |
| **Security** | JWT & BCrypt | Token-based auth and secure password hashing |

---

## ⚙️ Development Guide

### Prerequisites
- **JDK 17+**
- **Docker & Docker Compose**
- **Gradle 8.x**

### Infrastructure Setup
Spin up the required infrastructure (ScyllaDB, Redis, NATS):
```bash
docker compose up -d
```

### Running the Application
```bash
./gradlew run
```

### Testing with gRPC
The gRPC server starts on `localhost:50051`. You can use tools like **Kreya**, **Postman**, or `grpcurl` to interact with the services.

Example `grpcurl` command:
```bash
grpcurl -plaintext localhost:50051 list
```

---

## 🔐 Security Model
1. **Initial Login**: Basic Authentication via gRPC metadata.
2. **Session Persistence**: JWT-based Bearer Token required for all protected RPCs.
3. **Reset Flow**: Secure 15-minute one-time tokens for password recovery.
4. **Context Injection**: Authentication context (User ID, Role) is propagated via gRPC Interceptors.

---

## 📈 Monitoring & Maintenance
- **NATS Health**: Access `http://localhost:8222/varz` for server stats.
- **ScyllaDB Monitoring**: Use `nodetool status` to check cluster health.
- **Logging**: Centralized logging via SLF4J and Logback (configured in `src/main/resources/logback.xml`).

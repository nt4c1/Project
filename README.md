# Health Service (gRPC + Micronaut + ScyllaDB)

A high-performance, distributed Healthcare Management System built with **Micronaut**, **gRPC**, and **ScyllaDB**. The system provides real-time doctor discovery, clinic management, and appointment scheduling with high availability and geographic proximity features.

## 🏛️ Architecture
The project follows **Hexagonal Architecture (Ports and Adapters)** and **Domain-Driven Design (DDD)** principles to ensure high maintainability and testability:
- **Domain Layer**: Contains business models, logic, and port interfaces.
- **Application Layer**: Orchestrates use cases and coordinates between domain and infrastructure.
- **Adapters Layer**: 
    - **Input**: gRPC Services, NATS Listeners.
    - **Output**: ScyllaDB/Cassandra Repositories, Photon API Client, NATS Clients, Redis Caching.

## 🚀 Key Features
### 👨‍⚕️ Doctor & Clinic Management
- **Canonical Profiles**: Unified management of doctors and their clinic affiliations.
- **Geographic Discovery**: Nearby doctor search using multi-precision Geohashes (4 to 6).
- **Dynamic Scheduling**: Weekly working hours with slot-based booking support.

### 📅 Advanced Appointment System
- **Slot Alignment**: Enforces strict booking intervals based on doctor-defined `slotDurationMinutes`.
- **Capacity Management**: Automatic rejection of bookings exceeding `maxAppointmentsPerDay`.
- **Conflict Prevention**: Native double-booking prevention at the data layer.
- **Life-cycle Management**: Integrated flow for Pending, Accepted, Postponed, and Cancelled statuses.

### 🌍 Location Intelligence
- **Photon Geocoding**: Integrated with the Photon API for resolving natural language addresses into high-precision coordinates.
- **Proximity Sorting**: Haversine formula implementation for accurate distance-based sorting.

### ⚡ Performance & Scalability
- **Event-Driven**: NATS messaging for asynchronous updates and decoupling between Doctor and Patient modules.
- **Cache-Aside Pattern**: Redis-backed caching for doctor profiles (1h TTL) and location-based searches (10m TTL).
- **Distributed Database**: ScyllaDB for low-latency, high-throughput persistence.

## 🛠️ Technology Stack
- **Framework**: Micronaut 4.x (Java 17+)
- **Communication**: gRPC (Protobuf 3)
- **Database**: ScyllaDB (Cassandra compatible)
- **Messaging**: NATS
- **Caching**: Redis (Lettuce)
- **Security**: JWT (Access/Refresh Tokens) + gRPC Basic Auth

## 📦 Project Structure
- `common`: Shared Proto definitions, security utilities, and shared models.
- `doctor-module`: Core logic for doctors, clinics, schedules, and geographic search.
- `patient-module`: Patient profiles, appointment history, and discovery proxies.
- `src/main`: Application entry point and global configuration.

## ⚙️ Prerequisites
- **JDK 17+**
- **ScyllaDB / Cassandra** (Default: `localhost:9042`)
- **NATS** (Default: `localhost:4223`)
- **Redis** (Default: `localhost:6379`)

## 🏃 Running the Application
```bash
./gradlew clean run
```
- **gRPC Server**: `http://localhost:50051` (Plaintext/h2c)
- **HTTP/Management**: `http://localhost:8080`

## 🔐 Security & Authentication
Authentication for token generation uses **gRPC Basic Auth metadata**:
- **Header**: `Authorization: Basic <base64(email:password)>`
- **Modules**: Both `DoctorGrpcService` and `PatientGrpcService` utilize this for initial login.
- Subsequent requests use **JWT Tokens** provided in the `Authorization: Bearer <token>` header.

## 📡 Redis Caching Strategy
- **`doctor:*`**: Stores full doctor profiles. Invalidated on profile/schedule updates.
- **`doctor-location:*`**: Stores results for nearby searches. Invalidated when any doctor updates their clinic location to ensure search integrity.

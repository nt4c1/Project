# Healthcare Appointment System

A high-performance, modular healthcare appointment system built with Micronaut, ScyllaDB, gRPC, and NATS.

## Architecture Overview

The project follows **Hexagonal Architecture** (Ports and Adapters) and is structured as a Gradle multi-module project:

- **`app`**: The main entry point that bootstraps the Micronaut application and aggregates all modules.
- **`common`**: Contains shared resources, including gRPC Protobuf definitions, JWT providers, and shared auth interceptors.
- **`doctor-module`**: Handles doctor profiles, clinic management, availability schedules, and doctor-side appointment management.
- **`patient-module`**: Handles patient registration, doctor discovery, and appointment booking.

## Tech Stack
- **Framework:** Micronaut 4.6.2
- **Database:** ScyllaDB (Cassandra-compatible)
- **Communication:** gRPC (Internal/External APIs), NATS (Event-driven notifications)
- **Security:** JWT-based Authentication & Role-based Authorization
- **Build Tool:** Gradle

---

## System Workflow

### 1. Authentication & Authorization
Both Doctors and Patients must authenticate to access protected services.
- **Registration**: Users register via gRPC. Passwords are hashed using BCrypt.
- **Login**: Users receive a JWT containing their `user_id` and `role` ("Doctor" or "Patient").
- **Interception**: Every gRPC call is validated by `GrpcAuthInterceptor`, populating the gRPC `Context` with identity and role metadata.

### 2. Doctor Onboarding & Multi-Clinic Scheduling
- **Profile Creation**: Doctors specify specialization and affiliated `clinic_ids`.
- **Location Resolution**: Location text is resolved to Geohashes via Photon API. The system uses a **high-precision 6-character Geohash** (approx. 1.2km resolution) for indexing.
- **Multi-Clinic Schedules**: Supports distinct working hours for each clinic.

### 3. Patient Discovery & Adaptive Search
- **Proximity Search**: Patients search for doctors near a location or Geohash.
- **Adaptive Zoom-Out**: The system uses an intelligent search strategy:
    1. It first searches at **Level 6 precision** (1.2km) including all 8 neighboring cells.
    2. If no doctors are found, it automatically "zooms out" to **Level 5** (approx. 5km).
    3. If still empty, it expands to **Level 4** (approx. 20km).
- **Availability**: Patients fetch clinic-specific schedules to view available slots.

### 4. Appointment Lifecycle & Asynchronous Events
1. **Booking**: A patient requests an appointment for a specific date, time, and **clinic**.
2. **NATS Notifications**: The system uses NATS as an **asynchronous backbone**. Every lifecycle event triggers a message:
    - `patient.created` / `patient.updated`
    - `doctor.created` / `doctor.updated`
    - `appointment.created`, `accepted`, `postponed`, `cancelled`
3. **Resilience**: This allows the core booking logic to remain fast while background tasks (notifications, logs, analytics) process independently.
4. **Data Consistency**: Automated synchronization across denormalized ScyllaDB tables ensures O(1) read performance for all agenda and history views.

---

## gRPC API Reference (Protos)

### Patient Service (`PatientGrpcService`)
Used by patient-facing applications.

| RPC Method | Input (`message`) | Output (`message`) | Description |
|:--- |:--- |:--- |:--- |
| `PatientLogin` | `TokenRequest` | `TokenResponse` | Authenticate patient |
| `RegisterPatient` | `RegisterPatientRequest` | `RegisterPatientResponse` | Create new account |
| `GetNearbyDoctors`| `NearbyDoctorsProxyRequest` | `NearbyDoctorsProxyResponse`| Find doctors (with zoom-out) |
| `BookAppointment` | `BookAppointmentRequest` | `BookAppointmentResponse` | Request a new booking |
| `CancelAppointment`|`CancelAppointmentRequest`| `CancelAppointmentResponse`| Cancel a booking |

### Doctor Service (`DoctorGrpcService`)
Used by doctor-facing applications.

| RPC Method | Input (`message`) | Output (`message`) | Description |
|:--- |:--- |:--- |:--- |
| `GenerateToken` | `TokenRequest` | `TokenResponse` | Authenticate doctor |
| `UpdateLocation` | `UpdateLocationRequest` | `UpdateLocationResponse` | Update current location |
| `CreateSchedule` | `CreateScheduleRequest` | `CreateScheduleResponse` | Set hours per clinic |
| `GetAppointments` | `GetAppointmentsRequest` | `GetAppointmentsResponse` | View daily agenda |
| `AcceptAppointment`| `AppointmentActionRequest`| `AppointmentActionResponse` | Confirm a request |
| `PostponeAppointment`| `AppointmentActionRequest`| `AppointmentActionResponse`| Move to next working day |

---

## Database Schema (ScyllaDB)

The system uses a heavily denormalized schema in the `doctor_service` keyspace:

- **`doctor_schedules`**: Composite PK `(doctor_id, clinic_id)` for location-specific availability.
- **`doctors_by_location`**: Partitioned by `geohash_prefix` (length 6) for fast spatial lookups.
- **`appointments_by_doctor`**: Partition key `(doctor_id, appointment_date)` for instant agenda views.
- **`appointments_by_patient`**: Partition key `(patient_id, appointment_date)` for patient history.
- **`appointment_count_by_doctor_date`**: Tracks and limits daily bookings (default limit: 50).

## Setup & Running

### Prerequisites
- Java 17+
- ScyllaDB / Cassandra (running on `localhost:9042`)
- NATS Server (running on `localhost:4222`)

### Commands
```bash
# Build the project
./gradlew build

# Run the application
./gradlew :app:run
```

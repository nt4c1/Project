package com.health.patient.adapters.input.grpc;

import com.health.common.auth.GrpcAuthInterceptor;
import com.health.doctor.DoctorModuleApi;
import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.DoctorSchedule;

import com.health.doctor.mapper.MapperClass;
import com.health.grpc.auth.TokenRequest;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.ValidateTokenRequest;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.grpc.common.AppointmentMessage;
import com.health.grpc.common.DoctorMessage;
import com.health.grpc.doctor.*;
import com.health.grpc.patient.*;
import com.health.patient.application.PatientInterface;
import com.health.patient.domain.exception.DomainException;
import com.health.patient.domain.model.Patient;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micronaut.grpc.annotation.GrpcService;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.health.common.auth.GrpcAuthInterceptor.*;
import static com.health.common.auth.GrpcAuthInterceptor.ROLE_KEY;
import static com.health.common.auth.GrpcAuthInterceptor.USER_ID_KEY;

@Slf4j
@Singleton
@GrpcService
public class PatientGrpcServerImpl extends PatientGrpcServiceGrpc.PatientGrpcServiceImplBase {

    private final PatientInterface patientUseCase;
    private final DoctorModuleApi  doctorModule;
    private static final UUID NIL_UUID = new UUID(0, 0);

    public PatientGrpcServerImpl(PatientInterface patientUseCase,
                                 DoctorModuleApi doctorModule) {
        this.patientUseCase = patientUseCase;
        this.doctorModule   = doctorModule;
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Override
    public void patientLogin(TokenRequest request,
                             StreamObserver<TokenResponse> observer) {
        handle(observer, () -> {
            String email = EMAIL_KEY.get();
            String password = PASSWORD_KEY.get();

            if (email == null || password == null) {
                throw new DomainException("Credentials missing from basic auth", Status.UNAUTHENTICATED);
            }
            return patientUseCase.loginPatient(email, password);
        });
    }

    @Override
    public void validatePatientToken(ValidateTokenRequest request,
                                     StreamObserver<ValidateTokenResponse> observer) {
        handle(observer, () ->
                patientUseCase.validatePatient(request.getToken())
        );
    }

    @Override
    public void registerPatient(RegisterPatientRequest request,
                                StreamObserver<RegisterPatientResponse> observer) {
        handle(observer, () -> {
            if (request.getName().isBlank()) throw new DomainException("Name is required", Status.INVALID_ARGUMENT);
            if (request.getEmail().isBlank()) throw new DomainException("Email is required", Status.INVALID_ARGUMENT);
            if (request.getPassword().length() < 6) throw new DomainException("Password must be at least 6 characters", Status.INVALID_ARGUMENT);

            UUID id = patientUseCase.registerPatient(
                    request.getName(), request.getEmail(),
                    request.getPassword(), request.getPhone()
            );
            return RegisterPatientResponse.newBuilder()
                    .setPatientId(id.toString())
                    .setMessage("Patient registered successfully")
                    .build();
        });
    }

    // ── Patient ───────────────────────────────────────────────────────────────

    @Override
    public void getPatient(GetPatientRequest request,
                           StreamObserver<GetPatientResponse> observer) {
        handle(observer, () -> {
            if (request.getPatientId().isBlank()) throw new DomainException("Patient ID is required", Status.INVALID_ARGUMENT);
            ensureSelf(request.getPatientId());
            Patient p = patientUseCase.getPatient(UUID.fromString(request.getPatientId()))
                    .orElseThrow(() -> new DomainException("Patient not found", Status.NOT_FOUND));
            return GetPatientResponse.newBuilder()
                    .setPatientId(p.getId().toString())
                    .setName(p.getName())
                    .setEmail(p.getEmail())
                    .setPhone(p.getPhone())
                    .build();
        });
    }

    @Override
    public void updatePatient(UpdatePatientRequest request,
                              StreamObserver<UpdatePatientResponse> observer) {
        handle(observer, () -> {
            if (request.getPatientId().isBlank()) throw new DomainException("Patient ID is required", Status.INVALID_ARGUMENT);
            ensureSelf(request.getPatientId());
            patientUseCase.updatePatient(
                    UUID.fromString(request.getPatientId()),
                    request.getEmail(),
                    request.getPassword()
            );
            return UpdatePatientResponse.newBuilder()
                    .setPatientId(request.getPatientId())
                    .setMessage("Patient updated successfully")
                    .build();
        });
    }

    @Override
    public void deletePatient(DeletePatientRequest request,
                              StreamObserver<DeletePatientResponse> observer) {
        handle(observer, () -> {
            if (request.getPatientId().isBlank()) throw new DomainException("Patient ID is required", Status.INVALID_ARGUMENT);
            ensureSelf(request.getPatientId());
            patientUseCase.deletePatient(
                    UUID.fromString(request.getPatientId()),
                    request.getEmail(),
                    request.getPassword()
            );
            return DeletePatientResponse.newBuilder()
                    .setMessage("Patient deleted successfully")
                    .build();
        });
    }

    @Override
    public void patientExists(PatientExistsRequest request,
                              StreamObserver<PatientExistsResponse> observer) {
        handle(observer, () -> {
            if (request.getPatientId().isBlank()) throw new DomainException("Patient ID is required", Status.INVALID_ARGUMENT);
            return PatientExistsResponse.newBuilder()
                    .setExists(patientUseCase.getPatient(
                            UUID.fromString(request.getPatientId())).isPresent())
                    .build();
        });
    }

    // ── Doctor discovery (delegated to doctor module directly) ────────────────

    @Override
    public void getNearbyDoctors(NearbyDoctorsProxyRequest request,
                                 StreamObserver<NearbyDoctorsProxyResponse> observer) {
        handle(observer, () -> {
            if (request.getLocationText().isBlank()) throw new DomainException("Location is required", Status.INVALID_ARGUMENT);
            ensurePatient();
            return NearbyDoctorsProxyResponse.newBuilder()
                    .addAllDoctors(doctorModule.getNearbyDoctors(request.getLocationText())
                            .stream().map(MapperClass::toMsg).collect(Collectors.toList()))
                    .build();
        });
    }

    @Override
    public void getDoctorsByLocation(ByLocationProxyRequest request,
                                     StreamObserver<ByLocationProxyResponse> observer) {
        handle(observer, () -> {
            if (request.getGeohashPrefix().isBlank()) throw new DomainException("Geohash prefix is required", Status.INVALID_ARGUMENT);
            ensurePatient();
            return ByLocationProxyResponse.newBuilder()
                    .addAllDoctors(doctorModule.getDoctorsByGeohash(request.getGeohashPrefix())
                            .stream().map(MapperClass::toMsg).collect(Collectors.toList()))
                    .build();
        });
    }

    @Override
    public void getDoctorSchedule(ScheduleProxyRequest request,
                                  StreamObserver<ScheduleProxyResponse> observer) {
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            ensurePatient();

            UUID doctorId = UUID.fromString(request.getDoctorId());
            UUID clinicId = NIL_UUID;
            if (!request.getClinicId().isBlank()) {
                clinicId = UUID.fromString(request.getClinicId());
            }

            DoctorSchedule s = doctorModule
                    .getDoctorSchedule(doctorId, clinicId)
                    .orElseThrow(() -> new DomainException("Schedule not found", Status.NOT_FOUND));
            return ScheduleProxyResponse.newBuilder()
                    .addAllWorkingDays(s.getWorkingDays().stream()
                            .map(Enum::name).collect(Collectors.toList()))
                    .setStartTime(s.getStartTime().toString())
                    .setEndTime(s.getEndTime().toString())
                    .setSlotDurationMinutes(s.getSlotDurationMinutes())
                    .setMaxAppointmentsDay(s.getMaxAppointmentsPerDay())
                    .build();
        });
    }

    // ── Appointments (delegated to doctor module directly) ────────────────────

    @Override
    public void bookAppointment(BookAppointmentRequest request,
                                StreamObserver<BookAppointmentResponse> observer) {
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            if (request.getPatientId().isBlank()) throw new DomainException("Patient ID is required", Status.INVALID_ARGUMENT);
            if (request.getDate().isBlank()) throw new DomainException("Date is required", Status.INVALID_ARGUMENT);
            if (request.getTime().isBlank()) throw new DomainException("Time is required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getPatientId());

            UUID doctorId = UUID.fromString(request.getDoctorId());
            UUID clinicId = NIL_UUID;
            if (!request.getClinicId().isBlank()) {
                clinicId = UUID.fromString(request.getClinicId());
            }

            UUID id = doctorModule.bookAppointment(
                    doctorId,
                    UUID.fromString(request.getPatientId()),
                    clinicId,
                    LocalDate.parse(request.getDate()),
                    LocalTime.parse(request.getTime()),
                    request.getReasonForVisit()
            );
            return BookAppointmentResponse.newBuilder()
                    .setSuccess(true)
                    .setAppointmentId(id.toString())
                    .setMessage("Appointment booked successfully")
                    .build();
        });
    }

    @Override
    public void cancelAppointment(CancelAppointmentRequest request,
                                  StreamObserver<CancelAppointmentResponse> observer) {
        handle(observer, () -> {
            if (request.getAppointmentId().isBlank()) throw new DomainException("Appointment ID is required", Status.INVALID_ARGUMENT);
            if (request.getPatientId().isBlank()) throw new DomainException("Patient ID is required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getPatientId());
            doctorModule.cancelAppointment(
                    UUID.fromString(request.getAppointmentId()),
                    UUID.fromString(request.getPatientId()),
                    UUID.fromString(request.getDoctorId()),
                    LocalDate.parse(request.getDate()),
                    LocalTime.parse(request.getTime()),
                    request.getCancellationReason()
            );
            return CancelAppointmentResponse.newBuilder()
                    .setSuccess(true).setMessage("Cancelled").build();
        });
    }

    @Override
    public void getMyAppointments(MyAppointmentsRequest request,
                                  StreamObserver<MyAppointmentsResponse> observer) {
        handle(observer, () -> {
            if (request.getPatientId().isBlank()) throw new DomainException("Patient ID is required", Status.INVALID_ARGUMENT);
            if (request.getDate().isBlank()) throw new DomainException("Date is required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getPatientId());
            return MyAppointmentsResponse.newBuilder()
                    .addAllAppointments(
                            doctorModule.getPatientAppointments(
                                            UUID.fromString(request.getPatientId()),
                                            LocalDate.parse(request.getDate()))
                                    .stream().map(MapperClass::toApptMsg)
                                    .collect(Collectors.toList()))
                    .build();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensurePatient() {
        String role = ROLE_KEY.get();
        if (role == null || !role.equalsIgnoreCase("Patient"))
            throw new SecurityException("Access denied: Patients only.");
    }

    private void ensureSelf(String requestedId) {
        ensurePatient();
        String authId = USER_ID_KEY.get();
        if (authId == null || !authId.equals(requestedId))
            throw new SecurityException("Access denied: you can only access your own data.");
    }

    private <T> void handle(StreamObserver<T> observer, Supplier<T> supplier) {
        try {
            observer.onNext(supplier.get());
            observer.onCompleted();
        } catch (DomainException e) {
            log.warn("Domain error: {}", e.getMessage());
            observer.onError(e.getGrpcStatus().withDescription(e.getMessage()).asRuntimeException());
        } catch (SecurityException e) {
            log.warn("Security: {}", e.getMessage());
            observer.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Unexpected error", e);
            observer.onError(Status.INTERNAL.withDescription("Internal error").asRuntimeException());
        }
    }
}
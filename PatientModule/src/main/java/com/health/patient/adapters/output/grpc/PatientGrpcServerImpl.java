package com.health.patient.adapters.output.grpc;

import com.health.grpc.auth.ValidateTokenRequest;
import com.health.grpc.auth.ValidateTokenResponse;
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

import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@GrpcService
public class PatientGrpcServerImpl extends PatientGrpcServiceGrpc.PatientGrpcServiceImplBase {

    private final PatientInterface  patientUseCase;
    private final DoctorGrpcClient  doctorGrpcClient;

    public PatientGrpcServerImpl(PatientInterface patientUseCase,
                                 DoctorGrpcClient doctorGrpcClient) {
        this.patientUseCase   = patientUseCase;
        this.doctorGrpcClient = doctorGrpcClient;
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Override
    public void patientLogin(PatientLoginRequest request,
                             StreamObserver<PatientLoginResponse> observer) {
        handle(observer, () ->
                patientUseCase.loginPatient(request.getEmail(), request.getPassword())
        );
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

    // ── Patient CRUD ──────────────────────────────────────────────────────────

    @Override
    public void createPatient(CreatePatientRequest request,
                              StreamObserver<CreatePatientResponse> observer) {
        handle(observer, () -> {
            UUID id = patientUseCase.createPatient(
                    request.getName(), request.getEmail(),
                    request.getPhone(), request.getPassword()
            );
            return CreatePatientResponse.newBuilder()
                    .setPatientId(id.toString())
                    .setMessage("Patient created successfully")
                    .build();
        });
    }

    @Override
    public void getPatient(GetPatientRequest request,
                           StreamObserver<GetPatientResponse> observer) {
        handle(observer, () -> {
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
    public void patientExists(PatientExistsRequest request,
                              StreamObserver<PatientExistsResponse> observer) {
        handle(observer, () -> {
            boolean exists = patientUseCase
                    .getPatient(UUID.fromString(request.getPatientId()))
                    .isPresent();
            return PatientExistsResponse.newBuilder().setExists(exists).build();
        });
    }

    // ── Doctor discovery proxy ────────────────────────────────────────────────

    @Override
    public void getNearbyDoctors(NearbyDoctorsProxyRequest request,
                                 StreamObserver<NearbyDoctorsProxyResponse> observer) {
        handle(observer, () -> {
            var doctors = doctorGrpcClient.getNearbyDoctors(request.getLocationText())
                    .stream().map(this::mapDoctor).collect(Collectors.toList());
            return NearbyDoctorsProxyResponse.newBuilder().addAllDoctors(doctors).build();
        });
    }

    @Override
    public void getDoctorsByLocation(ByLocationProxyRequest request,
                                     StreamObserver<ByLocationProxyResponse> observer) {
        handle(observer, () -> {
            var doctors = doctorGrpcClient.getDoctorsByLocation(request.getGeohashPrefix())
                    .stream().map(this::mapDoctor).collect(Collectors.toList());
            return ByLocationProxyResponse.newBuilder().addAllDoctors(doctors).build();
        });
    }

    @Override
    public void getDoctorSchedule(ScheduleProxyRequest request,
                                  StreamObserver<ScheduleProxyResponse> observer) {
        handle(observer, () -> {
            GetScheduleResponse s = doctorGrpcClient.getDoctorSchedule(request.getDoctorId());
            return ScheduleProxyResponse.newBuilder()
                    .addAllWorkingDays(s.getWorkingDaysList())
                    .setStartTime(s.getStartTime())
                    .setEndTime(s.getEndTime())
                    .setSlotDurationMinutes(s.getSlotDurationMinutes())
                    .setMaxAppointmentsDay(s.getMaxAppointmentsDay())
                    .build();
        });
    }

    // ── Appointments proxy ────────────────────────────────────────────────────

    @Override
    public void bookAppointment(BookAppointmentRequest request,
                                StreamObserver<BookAppointmentResponse> observer) {
        handle(observer, () -> {
            ensureSelf(request.getPatientId());
            CreateAppointmentResponse res = doctorGrpcClient.createAppointment(
                    request.getDoctorId(),
                    request.getPatientId(),
                    request.getDate(),
                    request.getTime(),
                    request.getReasonForVisit()
            );
            return BookAppointmentResponse.newBuilder()
                    .setSuccess(res.getSuccess())
                    .setAppointmentId(res.getAppointment().getAppointmentId())
                    .setMessage(res.getMessage())
                    .build();
        });
    }

    @Override
    public void cancelAppointment(CancelAppointmentRequest request,
                                  StreamObserver<CancelAppointmentResponse> observer) {
        handle(observer, () -> {
            ensureSelf(request.getPatientId());
            CancelAppointmentResponse res = doctorGrpcClient.cancelAppointment(
                    request.getAppointmentId(),
                    request.getPatientId(),
                    request.getDoctorId(),
                    request.getDate(),
                    request.getTime(),
                    request.getCancellationReason()
            );
            return CancelAppointmentResponse.newBuilder()
                    .setSuccess(res.getSuccess())
                    .setMessage(res.getMessage())
                    .build();
        });
    }

    @Override
    public void getMyAppointments(MyAppointmentsRequest request,
                                  StreamObserver<MyAppointmentsResponse> observer) {
        handle(observer, () -> {
            ensureSelf(request.getPatientId());
            // AppointmentMessage now carries doctor_name + clinic_name
            var appointments = doctorGrpcClient
                    .getMyAppointments(request.getPatientId(), request.getDate())
                    .stream()
                    .map(this::mapAppointment)
                    .collect(Collectors.toList());
            return MyAppointmentsResponse.newBuilder()
                    .addAllAppointments(appointments).build();
        });
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private DoctorInfo mapDoctor(DoctorMessage d) {
        return DoctorInfo.newBuilder()
                .setDoctorId(d.getDoctorId())
                .setName(d.getName())
                .setType(DoctorInfo.Type.valueOf(d.getType().name()))
                .setSpecialization(d.getSpecialization())
                .setIsActive(d.getIsActive())
                .build();
    }

    private AppointmentInfo mapAppointment(AppointmentMessage a) {
        AppointmentInfo.Builder b = AppointmentInfo.newBuilder()
                .setAppointmentId(a.getAppointmentId())
                .setDoctorId(a.getDoctorId())
                .setPatientId(a.getPatientId())
                .setDate(a.getDate())
                .setTime(a.getTime())
                .setStatus(AppointmentInfo.Status.valueOf(a.getStatus().name()));
        // Denormalized fields — populated from appointments_by_patient row
        if (!a.getDoctorName().isBlank())         b.setDoctorName(a.getDoctorName());
        if (!a.getClinicName().isBlank())         b.setClinicName(a.getClinicName());
        if (!a.getReasonForVisit().isBlank())     b.setReasonForVisit(a.getReasonForVisit());
        if (!a.getCancellationReason().isBlank()) b.setCancellationReason(a.getCancellationReason());
        return b.build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureSelf(String requestedPatientId) {
        String authenticatedUserId = AuthInterceptor.USER_ID_KEY.get();
        if (authenticatedUserId == null || !authenticatedUserId.equals(requestedPatientId)) {
            throw new SecurityException("Access denied: you can only access your own information.");
        }
    }

    private <T> void handle(StreamObserver<T> observer, Supplier<T> supplier) {
        try {
            T response = supplier.get();
            observer.onNext(response);
            observer.onCompleted();
        } catch (DomainException e) {
            log.warn("Domain error: {}", e.getMessage());
            observer.onError(e.getGrpcStatus()
                    .withDescription(e.getMessage()).asRuntimeException());
        } catch (SecurityException e) {
            log.warn("Security error: {}", e.getMessage());
            observer.onError(Status.PERMISSION_DENIED
                    .withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Unexpected error", e);
            observer.onError(Status.INTERNAL
                    .withDescription("An internal error occurred")
                    .asRuntimeException());
        }
    }
}
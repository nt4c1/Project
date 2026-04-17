package com.health.patient.adapters.input.grpc;

import com.health.doctor.DoctorModuleApi;
import com.health.doctor.domain.model.Appointment;
import com.health.doctor.domain.model.Doctor;
import com.health.doctor.domain.model.DoctorSchedule;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@GrpcService
public class PatientGrpcServerImpl extends PatientGrpcServiceGrpc.PatientGrpcServiceImplBase {

    private final PatientInterface patientUseCase;
    private final DoctorModuleApi  doctorModule;   // direct call — no network hop

    public PatientGrpcServerImpl(PatientInterface patientUseCase,
                                 DoctorModuleApi doctorModule) {
        this.patientUseCase = patientUseCase;
        this.doctorModule   = doctorModule;
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

    // ── Patient ───────────────────────────────────────────────────────────────

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
        handle(observer, () -> PatientExistsResponse.newBuilder()
                .setExists(patientUseCase.getPatient(
                        UUID.fromString(request.getPatientId())).isPresent())
                .build());
    }

    // ── Doctor discovery (delegated to doctor module directly) ────────────────

    @Override
    public void getNearbyDoctors(NearbyDoctorsProxyRequest request,
                                 StreamObserver<NearbyDoctorsProxyResponse> observer) {
        handle(observer, () -> NearbyDoctorsProxyResponse.newBuilder()
                .addAllDoctors(doctorModule.getNearbyDoctors(request.getLocationText())
                        .stream().map(this::mapDoctor).collect(Collectors.toList()))
                .build());
    }

    @Override
    public void getDoctorsByLocation(ByLocationProxyRequest request,
                                     StreamObserver<ByLocationProxyResponse> observer) {
        handle(observer, () -> ByLocationProxyResponse.newBuilder()
                .addAllDoctors(doctorModule.getDoctorsByGeohash(request.getGeohashPrefix())
                        .stream().map(this::mapDoctor).collect(Collectors.toList()))
                .build());
    }

    @Override
    public void getDoctorSchedule(ScheduleProxyRequest request,
                                  StreamObserver<ScheduleProxyResponse> observer) {
        handle(observer, () -> {
            DoctorSchedule s = doctorModule
                    .getDoctorSchedule(UUID.fromString(request.getDoctorId()))
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
            ensureSelf(request.getPatientId());
            UUID id = doctorModule.bookAppointment(
                    UUID.fromString(request.getDoctorId()),
                    UUID.fromString(request.getPatientId()),
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
            ensureSelf(request.getPatientId());
            return MyAppointmentsResponse.newBuilder()
                    .addAllAppointments(
                            doctorModule.getPatientAppointments(
                                    UUID.fromString(request.getPatientId()),
                                    LocalDate.parse(request.getDate()))
                                    .stream().map(this::mapAppointment)
                                    .collect(Collectors.toList()))
                    .build();
        });
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private DoctorInfo mapDoctor(Doctor d) {
        DoctorInfo.Builder b = DoctorInfo.newBuilder()
                .setDoctorId(d.getId().toString())
                .setName(d.getName() != null ? d.getName() : "")
                .setSpecialization(d.getSpecialization() != null ? d.getSpecialization() : "")
                .setIsActive(d.isActive());
        if (d.getType() != null)
            b.setType(DoctorInfo.Type.valueOf(d.getType().name()));
        return b.build();
    }

    private AppointmentInfo mapAppointment(Appointment a) {
        AppointmentInfo.Builder b = AppointmentInfo.newBuilder()
                .setAppointmentId(a.getId().toString())
                .setDoctorId(a.getDoctorId().toString())
                .setPatientId(a.getPatientId().toString())
                .setDate(a.getAppointmentDate().toString())
                .setTime(a.getScheduleTime().toString())
                .setStatus(AppointmentInfo.Status.valueOf(a.getStatus().name()));
        if (a.getDoctorName()         != null) b.setDoctorName(a.getDoctorName());
        if (a.getClinicName()         != null) b.setClinicName(a.getClinicName());
        if (a.getReasonForVisit()     != null) b.setReasonForVisit(a.getReasonForVisit());
        if (a.getCancellationReason() != null) b.setCancellationReason(a.getCancellationReason());
        return b.build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureSelf(String requestedId) {
        String authId = AuthInterceptor.USER_ID_KEY.get();
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

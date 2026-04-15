package com.health.doctor.adapters.input.grpc;

import com.health.doctor.application.usecase.interfaces.*;
import com.health.doctor.domain.exception.DomainException;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.model.DoctorType;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import com.health.doctor.mapper.MapperClass;
import com.health.grpc.auth.ValidateTokenRequest;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.grpc.doctor.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micronaut.grpc.annotation.GrpcService;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;



@Slf4j
@GrpcService
@Singleton
public class DoctorGrpcApi extends DoctorGrpcServiceGrpc.DoctorGrpcServiceImplBase {

    private final AppointmentInterface      appointmentsUseCase;
    private final ClinicInterface           clinicUseCase;
    private final DoctorInterface           doctorUseCase;
    private final ScheduleInterface         scheduleUseCase;
    private final DoctorRepositoryPort      doctorRepo;
    private final AppointmentRepositoryPort appointmentRepo;
    public DoctorGrpcApi(AppointmentInterface appointmentsUseCase,
                         ClinicInterface clinicUseCase,
                         DoctorInterface doctorUseCase,
                         ScheduleInterface scheduleUseCase,
                         DoctorRepositoryPort doctorRepo,
                         AppointmentRepositoryPort appointmentRepo) {
        this.appointmentsUseCase = appointmentsUseCase;
        this.clinicUseCase       = clinicUseCase;
        this.doctorUseCase       = doctorUseCase;
        this.scheduleUseCase     = scheduleUseCase;
        this.doctorRepo          = doctorRepo;
        this.appointmentRepo     = appointmentRepo;
    }

    // ── Doctor ────────────────────────────────────────────────────────────────

    @Override
    public void createDoctor(CreateDoctorRequest request,
                             StreamObserver<CreateDoctorResponse> observer) {
        handle(observer, () -> {
            UUID clinicId = null;
            String raw = request.getClinicId();
            if (raw != null && !raw.isBlank()) {
                try { clinicId = UUID.fromString(raw.trim()); }
                catch (IllegalArgumentException ignored) { }
            }
            DoctorType type = DoctorType.valueOf(request.getType().toString());
            UUID id = doctorUseCase.createDoctor(
                    request.getName(), clinicId, type,
                    request.getSpecialization(),
                    request.getEmail(), request.getPassword()
            );
            Doctor doctor = doctorRepo.findById(id)
                    .orElseThrow(() -> new DomainException("Doctor creation failed", Status.INTERNAL));
            observer.onNext(CreateDoctorResponse.newBuilder()
                    .setDoctor(MapperClass.toMsg(doctor))
                    .setMessage("Doctor created successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void getDoctor(GetDoctorRequest request,
                          StreamObserver<GetDoctorResponse> observer) {
        handle(observer, () -> {
            Doctor doctor = doctorRepo.findById(UUID.fromString(request.getDoctorId()))
                    .orElseThrow(() -> new DomainException("Doctor not found", Status.NOT_FOUND));
            observer.onNext(GetDoctorResponse.newBuilder().setDoctor(MapperClass.toMsg(doctor)).build());
            observer.onCompleted();
        });
    }

    @Override
    public void doctorExists(DoctorExistsRequest request,
                             StreamObserver<DoctorExistsResponse> observer) {
        handle(observer, () -> {
            boolean exists = doctorRepo.findById(UUID.fromString(request.getDoctorId())).isPresent();
            observer.onNext(DoctorExistsResponse.newBuilder().setExists(exists).build());
            observer.onCompleted();
        });
    }

    @Override
    public void updateDoctor(UpdateDoctorRequest request,
                             StreamObserver<UpdateDoctorResponse> observer){
        handle(observer, () -> {
            UUID doctorId = UUID.fromString(request.getDoctorId());
            doctorUseCase.updateDoctor(doctorId, request.getEmail(), request.getPassword());
            
            Doctor updated = doctorRepo.findById(doctorId)
                    .orElseThrow(() -> new DomainException("Doctor not found after update", Status.NOT_FOUND));

            observer.onNext(UpdateDoctorResponse.newBuilder()
                    .setMessage("Doctor updated successfully")
                    .setDoctor(MapperClass.toMsg(updated))
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void deleteDoctor (DeleteDoctorRequest request,
                              StreamObserver<DeleteDoctorResponse> observer){
        handle(observer, () -> {
            doctorUseCase.deleteDoctor(
                    UUID.fromString(request.getDoctorId()),
                    request.getEmail(),
                    request.getPassword()
            );
            observer.onNext(DeleteDoctorResponse.newBuilder()
                    .setMessage("Doctor deleted successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void updateLocation(UpdateLocationRequest request,
                               StreamObserver<UpdateLocationResponse> observer) {
        handle(observer, () -> {
            doctorUseCase.updateDoctorLocation(
                    UUID.fromString(request.getDoctorId()),
                    request.getLocationText()
            );
            observer.onNext(UpdateLocationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Location updated successfully")
                    .build());
            observer.onCompleted();
        });
    }

    // ── Clinic ────────────────────────────────────────────────────────────────

    @Override
    public void createClinic(CreateClinicRequest request,
                             StreamObserver<CreateClinicResponse> observer) {
        handle(observer, () -> {
            UUID id = clinicUseCase.createClinic(request.getName(), request.getLocationText());
            Clinic clinic = clinicUseCase.getClinicById(id);
            observer.onNext(CreateClinicResponse.newBuilder()
                    .setClinic(ClinicMessage.newBuilder()
                            .setClinicId(clinic.getId().toString())
                            .setName(clinic.getName())
                            .setLocationText(clinic.getLocationText())
                            .build())
                    .setMessage("Clinic created successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void getDoctorsByClinic(GetByClinicRequest request,
                                   StreamObserver<GetByClinicResponse> observer) {
        handle(observer, () -> {
            List<Doctor> doctors = doctorUseCase.getDoctorsByClinic(
                    UUID.fromString(request.getClinicId()));
            observer.onNext(GetByClinicResponse.newBuilder()
                    .addAllDoctors(doctors.stream().map(MapperClass::toMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    // ── Schedule ──────────────────────────────────────────────────────────────

    @Override
    public void createSchedule(CreateScheduleRequest request,
                               StreamObserver<CreateScheduleResponse> observer) {
        handle(observer, () -> {
            scheduleUseCase.createSchedule(
                    UUID.fromString(request.getDoctorId()),
                    new HashSet<>(request.getWorkingDaysList()),
                    LocalTime.parse(request.getStartTime()),
                    LocalTime.parse(request.getEndTime()),
                    request.getSlotDurationMinutes(),
                    request.getMaxAppointmentsDay()
            );
            observer.onNext(CreateScheduleResponse.newBuilder()
                    .setMessage("Schedule created successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void getDoctorSchedule(GetScheduleRequest request,
                                  StreamObserver<GetScheduleResponse> observer) {
        handle(observer, () -> {
            DoctorSchedule s = scheduleUseCase.getSchedule(UUID.fromString(request.getDoctorId()))
                    .orElseThrow(() -> new com.health.doctor.domain.exception
                            .NotFoundException("No schedule for doctor: " + request.getDoctorId()));
            observer.onNext(GetScheduleResponse.newBuilder()
                    .setDoctorId(s.getDoctorId().toString())
                    .addAllWorkingDays(s.getWorkingDays().stream()
                            .map(Enum::name).collect(Collectors.toList()))
                    .setStartTime(s.getStartTime().toString())
                    .setEndTime(s.getEndTime().toString())
                    .setSlotDurationMinutes(s.getSlotDurationMinutes())
                    .setMaxAppointmentsDay(s.getMaxAppointmentsPerDay())
                    .build());
            observer.onCompleted();
        });
    }

    // ── Location ──────────────────────────────────────────────────────────────

    @Override
    public void getNearbyDoctors(NearbyDoctorsRequest request,
                                 StreamObserver<NearbyDoctorsResponse> observer) {
        handle(observer, () -> {
            List<Doctor> doctors = doctorUseCase.getDoctorsByLocationText(request.getLocationText());
            observer.onNext(NearbyDoctorsResponse.newBuilder()
                    .addAllDoctors(doctors.stream().map(MapperClass::toMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void getDoctorsByLocation(ByLocationRequest request,
                                     StreamObserver<ByLocationResponse> observer) {
        handle(observer, () -> {
            List<Doctor> doctors = doctorUseCase.getDoctorsByLocationGeohash(request.getGeohashPrefix());
            observer.onNext(ByLocationResponse.newBuilder()
                    .addAllDoctors(doctors.stream().map(MapperClass::toMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    // ── Appointments ──────────────────────────────────────────────────────────

    @Override
    public void createAppointment(CreateAppointmentRequest request,
                                  StreamObserver<CreateAppointmentResponse> observer) {
        handle(observer, () -> {
            UUID id = appointmentsUseCase.createAppointment(
                    UUID.fromString(request.getDoctorId()),
                    UUID.fromString(request.getPatientId()),
                    LocalDate.parse(request.getDate()),
                    LocalTime.parse(request.getTime()),
                    request.getReasonForVisit()
            );
            Appointment appt = appointmentRepo.findById(id)
                    .orElseThrow(() -> new DomainException("Appointment creation failed", Status.INTERNAL));
            observer.onNext(CreateAppointmentResponse.newBuilder()
                    .setSuccess(true)
                    .setAppointment(MapperClass.toApptMsg(appt))
                    .setMessage("Appointment created successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void cancelAppointment(CancelAppointmentRequest request,
                                  StreamObserver<CancelAppointmentResponse> observer) {
        handle(observer, () -> {
            appointmentsUseCase.cancelAppointment(
                    UUID.fromString(request.getAppointmentId()),
                    UUID.fromString(request.getPatientId()),
                    UUID.fromString(request.getDoctorId()),
                    LocalDate.parse(request.getDate()),
                    LocalTime.parse(request.getTime()),
                    request.getCancellationReason()
            );
            observer.onNext(CancelAppointmentResponse.newBuilder()
                    .setSuccess(true).setMessage("Cancelled").build());
            observer.onCompleted();
        });
    }

    @Override
    public void getAppointments(GetAppointmentsRequest request,
                                StreamObserver<GetAppointmentsResponse> observer) {
        handle(observer, () -> {
            List<Appointment> list = appointmentsUseCase.getAppointment(
                    UUID.fromString(request.getDoctorId()),
                    LocalDate.parse(request.getDate())
            );
            observer.onNext(GetAppointmentsResponse.newBuilder()
                    .addAllAppointments(list.stream().map(MapperClass::toApptMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void getPendingAppointments(GetAppointmentsRequest request,
                                       StreamObserver<GetAppointmentsResponse> observer) {
        handle(observer, () -> {
            List<Appointment> list = appointmentsUseCase.pendingAppointment(
                    UUID.fromString(request.getDoctorId()),
                    LocalDate.parse(request.getDate())
            );
            observer.onNext(GetAppointmentsResponse.newBuilder()
                    .addAllAppointments(list.stream().map(MapperClass::toApptMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void getMyAppointments(GetMyAppointmentsRequest request,
                                  StreamObserver<GetMyAppointmentsResponse> observer) {
        handle(observer, () -> {
            List<Appointment> list = appointmentRepo.findByPatientAndDate(
                    UUID.fromString(request.getPatientId()),
                    LocalDate.parse(request.getDate())
            );
            observer.onNext(GetMyAppointmentsResponse.newBuilder()
                    .addAllAppointments(list.stream().map(MapperClass::toApptMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void acceptAppointment(AppointmentActionRequest request,
                                  StreamObserver<AppointmentActionResponse> observer) {
        handle(observer, () -> {
            // Load current state from DB
            Appointment current = appointmentRepo.findById(
                            UUID.fromString(request.getAppointmentId()))
                    .orElseThrow(() -> new DomainException("Appointment not found", Status.NOT_FOUND));

            appointmentsUseCase.acceptAppointment(current);
            observer.onNext(AppointmentActionResponse.newBuilder()
                    .setSuccess(true).setMessage("Accepted").build());
            observer.onCompleted();
        });
    }

    @Override
    public void postponeAppointment(AppointmentActionRequest request,
                                    StreamObserver<AppointmentActionResponse> observer) {
        handle(observer, () -> {
            // Load current state — we need current status + current date as oldDate
            Appointment current = appointmentRepo.findById(
                            UUID.fromString(request.getAppointmentId()))
                    .orElseThrow(() -> new DomainException("Appointment not found", Status.NOT_FOUND));

            appointmentsUseCase.postponeAppointment(current);
            observer.onNext(AppointmentActionResponse.newBuilder()
                    .setSuccess(true).setMessage("Postponed").build());
            observer.onCompleted();
        });
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Override
    public void doctorLogin(DoctorLoginRequest request,
                            StreamObserver<DoctorLoginResponse> observer) {
        handle(observer, () -> {
            DoctorLoginResponse response = doctorUseCase.loginDoctor(
                    request.getEmail(), request.getPassword());
            observer.onNext(response);
            observer.onCompleted();
        });
    }

    @Override
    public void validateDoctorToken(ValidateTokenRequest request,
                                    StreamObserver<ValidateTokenResponse> observer) {
        handle(observer, () -> {
            ValidateTokenResponse response = doctorUseCase.validateDoctor(request.getToken());
            observer.onNext(response);
            observer.onCompleted();
        });
    }

    // ── Error handler ─────────────────────────────────────────────────────────

    private <T> void handle(StreamObserver<T> observer, Runnable action) {
        try {
            action.run();
        } catch (DomainException e) {
            log.warn("Domain error: {}", e.getMessage());
            observer.onError(e.getGrpcStatus()
                    .withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument: {}", e.getMessage());
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Unexpected gRPC error", e);
            observer.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getMessage())
                    .asRuntimeException());
        }
    }
}
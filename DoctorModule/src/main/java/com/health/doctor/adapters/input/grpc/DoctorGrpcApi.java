package com.health.doctor.adapters.input.grpc;

import com.health.doctor.application.service.LocationService;
import com.health.doctor.application.usecase.implementtion.*;
import com.health.doctor.application.usecase.interfaces.*;
import com.health.doctor.domain.exception.DomainException;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import com.health.grpc.auth.ValidateTokenRequest;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.grpc.doctor.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class DoctorGrpcApi extends DoctorGrpcServiceGrpc.DoctorGrpcServiceImplBase {


    private final AppointmentInterface appointmentsUseCase;
    private final ClinicInterface clinicUseCase;
    private final DoctorInterface doctorUseCase;
    private final ScheduleInterface scheduleUseCase;
    private final DoctorRepositoryPort doctorRepo;
    private final AppointmentRepositoryPort appointmentRepo;

    public DoctorGrpcApi(AppointmentInterface appointmentsUseCase, ClinicInterface clinicUseCase, DoctorInterface doctorUseCase, ScheduleInterface scheduleUseCase, DoctorRepositoryPort doctorRepo, AppointmentRepositoryPort appointmentRepo) {
        this.appointmentsUseCase = appointmentsUseCase;
        this.clinicUseCase = clinicUseCase;
        this.doctorUseCase = doctorUseCase;
        this.scheduleUseCase = scheduleUseCase;
        this.doctorRepo = doctorRepo;
        this.appointmentRepo = appointmentRepo;
    }


    @Override
    public void createDoctor(CreateDoctorRequest request,
                             StreamObserver<CreateDoctorResponse> observer){

        handle(observer, ()-> {


            UUID clinicId = null;
            String clinicIdString = request.getClinicId();
            if (clinicIdString != null && !clinicIdString.trim().isEmpty()) {
                try {
                    clinicId = UUID.fromString(clinicIdString.trim());
                } catch (IllegalArgumentException e) {
                    // Handle invalid UUID format
                    clinicId = null; // or log an error
                }
            }

            DoctorType type = DoctorType.valueOf(request.getType().name());
            UUID id = doctorUseCase.createDoctor(
                    request.getName(),
                    clinicId,
                    type,
                    request.getSpecialization(),
                    request.getEmail(),
                    request.getPassword()
            );
            observer.onNext(CreateDoctorResponse.newBuilder()
                    .setDoctorId(id.toString())
                    .setMessage("Doctor Created Successfully")
                    .build()
            );
            observer.onCompleted();
        });
    }

    @Override
    public void createClinic(CreateClinicRequest request,
                             StreamObserver<CreateClinicResponse> observer){
        handle(observer, ()-> {
            UUID id = clinicUseCase.createClinic(
                    request.getName(), request.getLocationText()
            );
            observer.onNext(CreateClinicResponse.newBuilder()
                    .setClinicId(id.toString())
                    .setMessage("Clinic Created Successfully")
                    .build()
            );
            observer.onCompleted();
        });
    }

    @Override
    public void createSchedule(CreateScheduleRequest request,
                               StreamObserver<CreateScheduleResponse> observer){
        handle(observer, () -> {
            scheduleUseCase.createSchedule(
                    UUID.fromString(request.getDoctorId()),
                    new HashSet<>(request.getWorkingDaysList()),
                    Instant.parse(request.getStartTime()),
                    Instant.parse(request.getEndTime()),
                    request.getSlotDurationMinutes(),
                    request.getMaxAppointmentsDay()
            );
            observer.onNext(CreateScheduleResponse.newBuilder()
                    .setMessage("Schedule Created Successfully")
                    .build());
            observer.onCompleted();
        });
    }



    @Override
    public void getNearbyDoctors(NearbyDoctorsRequest request,
                                 StreamObserver<NearbyDoctorsResponse> responseObserver) {
        handle(responseObserver, () -> {
            List<Doctor> doctors = doctorUseCase.getDoctorsByLocationText(request.getLocationText());
            responseObserver.onNext(NearbyDoctorsResponse.newBuilder()
                    .addAllDoctors(doctors.stream().map(this::toMsg).collect(Collectors.toList()))
                    .build()
            );
            responseObserver.onCompleted();
        });
    }

    @Override
    public void getDoctorsByLocation(ByLocationRequest request,
                                     StreamObserver<ByLocationResponse> responseObserver) {
        handle(responseObserver, () -> {
            List<Doctor> doctors = doctorUseCase.getDoctorsByLocationGeohash(request.getGeohashPrefix());
            responseObserver.onNext(ByLocationResponse.newBuilder()
                    .addAllDoctors(doctors.stream().map(this::toMsg).collect(Collectors.toList()))
                    .build()
            );
            responseObserver.onCompleted();
        });
    }

    @Override
    public void getDoctorsByClinic(GetByClinicRequest request,
                                   StreamObserver<GetByClinicResponse> observer) {
        handle(observer, () -> {
            List<Doctor> doctors = doctorUseCase.getDoctorsByClinic(
                    UUID.fromString(request.getClinicId()));
            observer.onNext(GetByClinicResponse.newBuilder()
                    .addAllDoctors(doctors.stream().map(this::toMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }


    @Override
    public void getDoctorSchedule(GetScheduleRequest request,
                                  StreamObserver<GetScheduleResponse> observer) {
        handle(observer, () -> {
            DoctorSchedule s = scheduleUseCase.getSchedule(
                            UUID.fromString(request.getDoctorId()))
                    .orElseThrow(() -> new com.health.doctor.domain.exception
                            .NotFoundException("No schedule for doctor: "
                            + request.getDoctorId()));
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



    @Override
    public void getAppointments(GetAppointmentsRequest request,
                                StreamObserver<GetAppointmentsResponse> observer) {
        handle(observer, () -> {
            List<Appointment> list = appointmentsUseCase.getAppointment(
                    UUID.fromString(request.getDoctorId()),
                    LocalDate.parse(request.getDate())
            );
            observer.onNext(GetAppointmentsResponse.newBuilder()
                    .addAllAppointments(list.stream()
                            .map(this::toApptMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void updateLocation(UpdateLocationRequest request,
                               StreamObserver<UpdateLocationResponse> responseObserver) {
       handle(responseObserver, () -> {
           doctorUseCase.UpdateDoctorLocation(
                   UUID.fromString(request.getDoctorId()),
                   request.getLocationText()
           );
           responseObserver.onNext(UpdateLocationResponse.newBuilder()
                   .setSuccess(true)
                   .setMessage("Location Updated SuccessFully")
                   .build());
           responseObserver.onCompleted();
       });
    }

    @Override
    public void doctorExists(DoctorExistsRequest request,
                             StreamObserver<DoctorExistsResponse> observer) {
        handle(observer, () -> {
            Optional<Doctor> d = doctorRepo.findById(UUID.fromString(request.getDoctorId()));
            observer.onNext(DoctorExistsResponse.newBuilder()
                    .setExists(d.isPresent()).build());
            observer.onCompleted();
        });
    }

    @Override
    public void createAppointment(CreateAppointmentRequest request,
                                  StreamObserver<CreateAppointmentResponse> observer) {
        handle(observer, () -> {
            UUID id = appointmentsUseCase.createAppointment(
                    UUID.fromString(request.getDoctorId()),
                    UUID.fromString(request.getPatientId()),
                    LocalDate.parse(request.getDate()),
                    LocalTime.parse(request.getTime())
            );
            observer.onNext(CreateAppointmentResponse.newBuilder()
                    .setSuccess(true)
                    .setAppointmentId(id.toString())
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
                    Instant.parse(request.getTime())
            );
            observer.onNext(CancelAppointmentResponse.newBuilder()
                    .setSuccess(true).setMessage("Cancelled").build());
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
                    .addAllAppointments(list.stream()
                            .map(this::toApptMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void acceptAppointment(AppointmentActionRequest request,
                                  StreamObserver<AppointmentActionResponse> observer) {
        handle(observer, () -> {
            appointmentsUseCase.acceptAppointment(toAppointment(request));
            observer.onNext(AppointmentActionResponse.newBuilder()
                    .setSuccess(true).setMessage("Accepted").build());
            observer.onCompleted();
        });
    }

    @Override
    public void postponeAppointment(AppointmentActionRequest request,
                                    StreamObserver<AppointmentActionResponse> observer) {
        handle(observer, () -> {
            appointmentsUseCase.postponeAppointment(toAppointment(request));
            observer.onNext(AppointmentActionResponse.newBuilder()
                    .setSuccess(true).setMessage("Postponed").build());
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
                    .addAllAppointments(list.stream()
                            .map(this::toApptMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void doctorLogin(DoctorLoginRequest request,
                            StreamObserver<DoctorLoginResponse> observer) {
        handle(observer ,() -> {
            DoctorLoginResponse response = doctorUseCase.loginDoctor(
                    request.getEmail(),request.getPassword());
            observer.onNext(response);
            observer.onCompleted();
        });
    }

    @Override
    public void validateDoctorToken(ValidateTokenRequest request,
                                    StreamObserver<ValidateTokenResponse> observer) {
        handle(observer, () -> {
            ValidateTokenResponse response = doctorUseCase.validateDoctor(
                    request.getToken());
            observer.onNext(response);
            observer.onCompleted();
        });
    }

    private DoctorMessage toMsg(Doctor d) {
        return DoctorMessage.newBuilder()
                .setDoctorId(d.getId() != null ? d.getId().toString() : "")
                .setName(d.getName() != null ? d.getName() : "")
                .setType(DoctorMessage.Type.valueOf(d.getType().name()))
                .setSpecialization(d.getSpecialization() != null ? d.getSpecialization() : "")
                .setIsActive(d.isActive())
                .build();
    }

    private AppointmentMessage toApptMsg(Appointment a) {
        return AppointmentMessage.newBuilder()
                .setAppointmentId(a.getId().toString())
                .setDoctorId(a.getDoctorId().toString())
                .setPatientId(a.getPatientId().toString())
                .setDate(a.getAppointmentDate().toString())
                .setTime(a.getScheduleTime().toString())
                .setStatus(AppointmentMessage.Status.valueOf(a.getStatus().name()))
                .build();
    }

    private Appointment toAppointment(AppointmentActionRequest r) {
        return new Appointment(
                UUID.fromString(r.getAppointmentId()),
                UUID.fromString(r.getDoctorId()),
                UUID.fromString(r.getPatientId()),
                LocalDate.parse(r.getDate()),
                LocalTime.parse(r.getTime()),
                AppointmentStatus.valueOf(r.getStatus().name()),
                null, null
        );
    }

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
                    .withDescription(e.getCause()+e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Unexpected gRPC error", e);
            observer.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getMessage())
                    .asRuntimeException());
        }
    }
}
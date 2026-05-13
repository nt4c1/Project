package com.health.doctor.adapters.input.grpc;

import com.health.common.exception.DomainException;
import com.health.doctor.application.usecase.interfaces.*;
import com.health.doctor.domain.model.*;
import com.health.doctor.domain.model.DoctorType;
import com.health.doctor.domain.ports.AppointmentRepositoryPort;
import com.health.doctor.domain.ports.DoctorRepositoryPort;
import com.health.doctor.mapper.MapperClass;
import com.health.grpc.auth.*;
import com.health.grpc.common.DoctorMessage;
import com.health.grpc.doctor.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micronaut.grpc.annotation.GrpcService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.health.common.auth.JwtAuthInterceptor.*;

@Slf4j
@GrpcService
public class DoctorGrpcApi extends DoctorGrpcServiceGrpc.DoctorGrpcServiceImplBase {

    private final AppointmentInterface      appointmentsUseCase;
    private final DoctorInterface           doctorUseCase;
    private final ScheduleInterface         scheduleUseCase;
    private final DoctorRepositoryPort      doctorRepo;
    private final AppointmentRepositoryPort appointmentRepo;

    public DoctorGrpcApi(AppointmentInterface appointmentsUseCase,
                         DoctorInterface doctorUseCase,
                         ScheduleInterface scheduleUseCase,
                         DoctorRepositoryPort doctorRepo,
                         AppointmentRepositoryPort appointmentRepo) {
        this.appointmentsUseCase = appointmentsUseCase;
        this.doctorUseCase       = doctorUseCase;
        this.scheduleUseCase     = scheduleUseCase;
        this.doctorRepo          = doctorRepo;
        this.appointmentRepo     = appointmentRepo;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureDoctor() {
        String role = ROLE_KEY.get();
        if (role == null || !role.equalsIgnoreCase("Doctor"))
            throw new SecurityException("Access denied: Doctors only.");
    }

    private void ensurePatient() {
        String role = ROLE_KEY.get();
        if (role == null || !role.equalsIgnoreCase("Patient"))
            throw new SecurityException("Access denied: Patients only.");
    }

    private void ensureSelfPatient(String requestedId) {
        ensurePatient();
        String authId = USER_ID_KEY.get();
        if (authId == null || !authId.equals(requestedId))
            throw new SecurityException("Access denied: you can only leave reviews for yourself.");
    }

    private void ensureSelf(String requestedId) {
        ensureDoctor();
        String authId = USER_ID_KEY.get();
        if (authId == null || !authId.equals(requestedId))
            throw new SecurityException("Access denied: you can only access your own data.");
    }

    // ── Doctor ────────────────────────────────────────────────────────────────

    @Override
    public void createDoctor(CreateDoctorRequest request,
                             StreamObserver<CreateDoctorResponse> observer) {
        handle(observer, () -> {
            if (request.getName().isBlank()) throw new DomainException("Name is required", Status.INVALID_ARGUMENT);
            if (request.getEmail().isBlank()) throw new DomainException("Email is required", Status.INVALID_ARGUMENT);

            List<UUID> clinicIds = new ArrayList<>();
            for (String raw : request.getClinicIdsList()) {
                if (raw != null && !raw.isBlank()) {
                    try { clinicIds.add(UUID.fromString(raw.trim())); }
                    catch (IllegalArgumentException ignored) { }
                }
            }
            DoctorType type = DoctorType.valueOf(request.getType().toString());
            UUID id = doctorUseCase.createDoctor(
                    request.getName(), clinicIds, type,
                    request.getSpecialization(),
                    request.getEmail(), request.getPassword(),
                    request.getPhone()
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
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            Doctor doctor = doctorRepo.findById(UUID.fromString(request.getDoctorId()))
                    .orElseThrow(() -> new DomainException("Doctor not found", Status.NOT_FOUND));
            observer.onNext(GetDoctorResponse.newBuilder().setDoctor(MapperClass.toMsg(doctor)).build());
            observer.onCompleted();
        });
    }

    @Override
    public void isDoctorActive(IsActiveRequest request,
                             StreamObserver<DoctorActiveResponse> observer) {
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);

            UUID doctorId = UUID.fromString(request.getDoctorId());
            UUID clinicId = request.getClinicId().isBlank() ? null : UUID.fromString(request.getClinicId());
            
            DoctorActiveResponse response = doctorUseCase.isDoctorActive(doctorId, clinicId);
            observer.onNext(response);
            observer.onCompleted();
        });
    }

    @Override
    public void updateDoctor(UpdateDoctorRequest request,
                             StreamObserver<UpdateDoctorResponse> observer){
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            if (request.getEmail().isBlank()) throw new DomainException("Email is required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getDoctorId());
            UUID doctorId = UUID.fromString(request.getDoctorId());
            doctorUseCase.updateDoctor(doctorId, request.getEmail(), request.getPassword(), request.getPhone());
            
            Doctor updated = doctorRepo.findById(doctorId)
                    .orElseThrow(() -> new DomainException("Doctor not found after update", Status.NOT_FOUND));

            observer.onNext(UpdateDoctorResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Doctor updated successfully")
                    .setDoctor(MapperClass.toMsg(updated))
                    .build());
            observer.onCompleted();
            });
            }

    @Override
    public void deleteDoctor(DeleteDoctorRequest request,
                              StreamObserver<DeleteDoctorResponse> observer){
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            ensureSelf(request.getDoctorId());
            doctorUseCase.deleteDoctor(
                    UUID.fromString(request.getDoctorId()),
                    request.getEmail(),
                    request.getPassword()
            );
            observer.onNext(DeleteDoctorResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Doctor deleted successfully")
                    .build());
            observer.onCompleted();
        });
    }

    private static final UUID NIL_UUID = new UUID(0, 0);

    @Override
    public void updateDoctorLocation(UpdateDoctorLocationRequest request,
                               StreamObserver<UpdateDoctorLocationResponse> observer) {
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            if (request.getLocationText().isBlank()) throw new DomainException("Location text is required", Status.INVALID_ARGUMENT);

            String role = ROLE_KEY.get();
            String authId = USER_ID_KEY.get();
            UUID doctorId = UUID.fromString(request.getDoctorId());
            UUID clinicId = request.getClinicId().isBlank() ? null : UUID.fromString(request.getClinicId());

            if (clinicId != null) {
                // Clinic-based update: Only the clinic itself can perform this
                if (role == null || !role.equalsIgnoreCase("Clinic")) {
                    throw new DomainException("Access denied: Only Clinics can update location for a specific clinic ID.", Status.PERMISSION_DENIED);
                }
                if (authId == null || !authId.equals(clinicId.toString())) {
                    throw new DomainException("Access denied: You can only update locations for your own clinic.", Status.PERMISSION_DENIED);
                }
                // Verify doctor belongs to this clinic
                Doctor doc = doctorRepo.findById(doctorId)
                        .orElseThrow(() -> new DomainException("Doctor not found", Status.NOT_FOUND));
                if (doc.getClinicIds() == null || !doc.getClinicIds().contains(clinicId)) {
                    throw new DomainException("Doctor does not belong to this clinic", Status.PERMISSION_DENIED);
                }
            } else {
                // Individual doctor update: Only the doctor themselves can perform this
                ensureSelf(request.getDoctorId());
                Doctor doc = doctorRepo.findById(doctorId)
                        .orElseThrow(() -> new DomainException("Doctor not found", Status.NOT_FOUND));
            }

            doctorUseCase.updateDoctorLocation(doctorId, clinicId, request.getLocationText());

            observer.onNext(UpdateDoctorLocationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Location updated successfully")
                    .build());
            observer.onCompleted();
        });
    }

    // ── Schedule ──────────────────────────────────────────────────────────────

    @Override
    public void createSchedule(CreateScheduleRequest request,
                               StreamObserver<CreateScheduleResponse> observer) {
        handle(observer, () -> {
            if (request.getSchedule().getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            if (request.getSchedule().getWorkingDaysList().isEmpty()) throw new DomainException("Working days are required", Status.INVALID_ARGUMENT);
            if (request.getSchedule().getStartTime().isBlank()) throw new DomainException("Start time is required", Status.INVALID_ARGUMENT);
            if (request.getSchedule().getEndTime().isBlank()) throw new DomainException("End time is required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getSchedule().getDoctorId());
            
            UUID doctorId = UUID.fromString(request.getSchedule().getDoctorId());
            UUID clinicId = NIL_UUID;
            if (!request.getSchedule().getClinicId().isBlank()) {
                clinicId = UUID.fromString(request.getSchedule().getClinicId());
            } else {
                Doctor doc = doctorRepo.findById(doctorId)
                        .orElseThrow(() -> new DomainException("Doctor not found", Status.NOT_FOUND));
                if (doc.getClinicIds() != null && !doc.getClinicIds().isEmpty()) {
                    throw new DomainException("Clinic ID is required for clinic-affiliated doctors", Status.INVALID_ARGUMENT);
                }
            }

            scheduleUseCase.createSchedule(
                    doctorId,
                    clinicId,
                    new HashSet<>(request.getSchedule().getWorkingDaysList()),
                    LocalTime.parse(request.getSchedule().getStartTime()),
                    LocalTime.parse(request.getSchedule().getEndTime()),
                    request.getSchedule().getSlotDurationMinutes(),
                    request.getSchedule().getMaxAppointmentsDay()
            );
            observer.onNext(CreateScheduleResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Schedule created successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void updateSchedule(UpdateScheduleRequest request,
                               StreamObserver<UpdateScheduleResponse> observer) {
        handle(observer, () -> {
            if (request.getSchedule().getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            if (request.getSchedule().getWorkingDaysList().isEmpty()) throw new DomainException("Working days are required", Status.INVALID_ARGUMENT);
            if (request.getSchedule().getStartTime().isBlank()) throw new DomainException("Start time is required", Status.INVALID_ARGUMENT);
            if (request.getSchedule().getEndTime().isBlank()) throw new DomainException("End time is required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getSchedule().getDoctorId());

            UUID doctorId = UUID.fromString(request.getSchedule().getDoctorId());
            UUID clinicId = NIL_UUID;
            if (!request.getSchedule().getClinicId().isBlank()) {
                clinicId = UUID.fromString(request.getSchedule().getClinicId());
            }

            scheduleUseCase.updateSchedule(
                    doctorId,
                    clinicId,
                    new HashSet<>(request.getSchedule().getWorkingDaysList()),
                    LocalTime.parse(request.getSchedule().getStartTime()),
                    LocalTime.parse(request.getSchedule().getEndTime()),
                    request.getSchedule().getSlotDurationMinutes(),
                    request.getSchedule().getMaxAppointmentsDay()
            );
            observer.onNext(UpdateScheduleResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Schedule updated successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void getDoctorSchedule(GetScheduleRequest request,
                                  StreamObserver<GetScheduleResponse> observer) {
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            
            UUID doctorId = UUID.fromString(request.getDoctorId());
            UUID clinicId;
            if (!request.getClinicId().isBlank()) {
                clinicId = UUID.fromString(request.getClinicId());
            } else {
                clinicId = NIL_UUID;
            }

            DoctorSchedule s = scheduleUseCase.getSchedule(doctorId, clinicId)
                    .orElseThrow(() -> new com.health.common.exception
                            .NotFoundException("No schedule for doctor: " + doctorId + (clinicId.equals(NIL_UUID) ? "" : " at clinic: " + clinicId)));
            observer.onNext(GetScheduleResponse.newBuilder()
                    .setSchedule(com.health.grpc.common.ScheduleMessage.newBuilder()
                        .setDoctorId(s.getDoctorId().toString())
                        .addAllWorkingDays(s.getWorkingDays().stream()
                                .map(Enum::name).collect(Collectors.toList()))
                        .setStartTime(s.getStartTime().toString())
                        .setEndTime(s.getEndTime().toString())
                        .setSlotDurationMinutes(s.getSlotDurationMinutes())
                        .setMaxAppointmentsDay(s.getMaxAppointmentsPerDay())
                        .build())
                    .build());
            observer.onCompleted();
        });
    }

    // ── Reviews ──────────────────────────────────────────────────────────────

    @Override
    public void getReviews(GetReviewsRequest request,
                                 StreamObserver<GetReviewsResponse> observer) {
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);

            UUID doctorId = UUID.fromString(request.getDoctorId());
            List<Review> reviews = doctorUseCase.getDoctorReviews(doctorId);
            double avg = doctorUseCase.getAverageRating(doctorId);

            List<com.health.grpc.common.ReviewMessage> msgs = reviews.stream()
                    .map(r -> com.health.grpc.common.ReviewMessage.newBuilder()
                            .setDoctorId(r.getDoctorId().toString())
                            .setPatientId(r.getPatientId().toString())
                            .setPatientName(r.getPatientName())
                            .setRating(r.getRating())
                            .setComment(r.getComment())
                            .setCreatedAt(r.getCreatedAt().toString())
                            .build())
                    .toList();

            observer.onNext(GetReviewsResponse.newBuilder()
                    .addAllReviews(msgs)
                    .setAverageRating(avg)
                    .build());
            observer.onCompleted();
        });
    }

    // ── Location ──────────────────────────────────────────────────────────────

    @Override
    public void getDoctorsByLocation(com.health.grpc.common.LocationSearchRequest request,
                                     StreamObserver<FindNearByDoctorResponse> observer) {
        handle(observer, () -> {
            if (request.getLocation().isBlank()) throw new DomainException("Location is required", Status.INVALID_ARGUMENT);
            List<Doctor> doctors = doctorUseCase.getDoctorsByLocation(request.getLocation(), request.getFilter());
            List<DoctorMessage> doctorsMsg = doctors.stream().map(MapperClass::toMsg).toList();
            observer.onNext(FindNearByDoctorResponse.newBuilder()
                    .addAllDoctors(doctorsMsg)
                    .build());
            observer.onCompleted();
        });
    }

    // ── Appointments ──────────────────────────────────────────────────────────

    @Override
    public void getDoctorAppointments(GetDoctorAppointmentsRequest request,
                                StreamObserver<GetDoctorAppointmentsResponse> observer) {
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getDoctorId());
            // Need a default or date range? Proto doesn't have date.
            // Let's assume today or all?
            List<Appointment> list = appointmentsUseCase.getDoctorAppointments(UUID.fromString(request.getDoctorId()));
            observer.onNext(GetDoctorAppointmentsResponse.newBuilder()
                    .addAllAppointments(list.stream().map(MapperClass::toApptMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void getAppointmentsByStatus(GetByStatusRequest request,
                                       StreamObserver<GetDoctorAppointmentsResponse> observer) {
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getDoctorId());
            List<Appointment> list = appointmentsUseCase.getAppointmentsByStatus(
                    UUID.fromString(request.getDoctorId()),
                    request.getStatus(),
                    request.getDate().isBlank() ? null : LocalDate.parse(request.getDate())
            );
            observer.onNext(GetDoctorAppointmentsResponse.newBuilder()
                    .addAllAppointments(list.stream().map(MapperClass::toApptMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void acceptAppointment(AcceptAppointmentRequest request,
                                  StreamObserver<AcceptAppointmentResponse> observer) {
        handle(observer, () -> {
            if (request.getAppointmentId().isBlank()) throw new DomainException("Appointment ID is required", Status.INVALID_ARGUMENT);

            // Load current state from DB
            Appointment current = appointmentRepo.findById(
                            UUID.fromString(request.getAppointmentId()))
                    .orElseThrow(() -> new DomainException("Appointment not found", Status.NOT_FOUND));

            ensureSelf(current.getDoctorId().toString());

            appointmentsUseCase.acceptAppointment(current, ""); // Meeting link?
            observer.onNext(AcceptAppointmentResponse.newBuilder()
                    .setSuccess(true).setMessage("Accepted").build());
            observer.onCompleted();
        });
    }

    @Override
    public void postponeAppointment(PostponeAppointmentRequest request,
                                    StreamObserver<PostponeAppointmentResponse> observer) {
        handle(observer, () -> {
            if (request.getAppointmentId().isBlank()) throw new DomainException("Appointment ID is required", Status.INVALID_ARGUMENT);

            Appointment current = appointmentRepo.findById(
                            UUID.fromString(request.getAppointmentId()))
                    .orElseThrow(() -> new DomainException("Appointment not found", Status.NOT_FOUND));

            ensureSelf(current.getDoctorId().toString());

            appointmentsUseCase.postponeAppointment(current);
            observer.onNext(PostponeAppointmentResponse.newBuilder()
                    .setSuccess(true).setMessage("Postponed").build());
            observer.onCompleted();
        });
    }

    @Override
    public void completeAppointment(CompleteAppointmentRequest request,
                                    StreamObserver<CompleteAppointmentResponse> observer) {
        handle(observer, () -> {
            if (request.getAppointmentId().isBlank()) throw new DomainException("Appointment ID is required", Status.INVALID_ARGUMENT);

            Appointment current = appointmentRepo.findById(
                            UUID.fromString(request.getAppointmentId()))
                    .orElseThrow(() -> new DomainException("Appointment not found", Status.NOT_FOUND));

            ensureSelf(current.getDoctorId().toString());

            appointmentsUseCase.completeAppointment(current);
            observer.onNext(CompleteAppointmentResponse.newBuilder()
                    .setSuccess(true).setMessage("Completed").build());
            observer.onCompleted();
        });
    }

    @Override
    public void markNoShow(MarkNoShowRequest request,
                                  StreamObserver<MarkNoShowResponse> observer) {
        handle(observer, () -> {
            if (request.getAppointmentId().isBlank()) throw new DomainException("Appointment ID is required", Status.INVALID_ARGUMENT);

            Appointment current = appointmentRepo.findById(
                            UUID.fromString(request.getAppointmentId()))
                    .orElseThrow(() -> new DomainException("Appointment not found", Status.NOT_FOUND));

            ensureSelf(current.getDoctorId().toString());

            appointmentsUseCase.noShowAppointment(current);
            observer.onNext(MarkNoShowResponse.newBuilder()
                    .setSuccess(true).setMessage("No-Show").build());
            observer.onCompleted();
        });
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Override
    public void refreshToken(RefreshTokenRequest request,
                             StreamObserver<TokenResponse> observer) {
        handle(observer, () -> {
            TokenResponse response = doctorUseCase.refreshToken(request.getRefreshToken());
            observer.onNext(response);
            observer.onCompleted();
        });
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request,
                               StreamObserver<ForgotPasswordResponse> observer) {
        handle(observer, () -> {
            if (request.getEmail().isBlank()) throw new DomainException("Email is required", Status.INVALID_ARGUMENT);
            String token = doctorUseCase.forgotPassword(request.getEmail());
            observer.onNext(ForgotPasswordResponse.newBuilder()
                    .setSuccess(true)
                    .setResetToken(token)
                    .setMessage("Reset token generated successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void resetPassword(ResetPasswordRequest request,
                              StreamObserver<ResetPasswordResponse> observer) {
        handle(observer, () -> {
            if (request.getNewPassword().length() <8) throw new DomainException("Password must be at least 8 characters", Status.INVALID_ARGUMENT);

            doctorUseCase.resetPassword(request.getNewPassword());
            observer.onNext(ResetPasswordResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Password reset successfully")
                    .build());
            observer.onCompleted();
        });
    }

    // ── Error handler ─────────────────────────────────────────────────────────

    private <T> void handle(StreamObserver<T> observer, Runnable action) {
        try {
            action.run();
        } catch (com.health.common.exception.DomainException e) {
            log.warn("Domain error: {}", e.getMessage());
            observer.onError(e.getGrpcStatus()
                    .withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            log.warn("Invalid argument or parse error: {}", e.getMessage());
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid format: " + e.getMessage()).asRuntimeException());
        } catch (jakarta.validation.ConstraintViolationException e) {
            log.warn("Validation error: {}", e.getMessage());
            String desc = e.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription("Validation failed: " + desc).asRuntimeException());
        } catch (Exception e) {
            log.error("Unexpected gRPC error", e);
            observer.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getClass().getSimpleName() + " - " + e.getMessage())
                    .asRuntimeException());
        }
    }
}

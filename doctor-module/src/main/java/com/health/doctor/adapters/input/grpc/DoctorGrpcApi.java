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

import static com.health.common.auth.GrpcAuthInterceptor.*;

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
    public void doctorActive(DoctorActiveRequest request,
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
                    .setMessage("Doctor updated successfully")
                    .setDoctor(MapperClass.toMsg(updated))
                    .build());
            observer.onCompleted();
            });
            }

    @Override
    public void addClinic(AddClinicRequest request,
                          StreamObserver<AddClinicResponse> observer) {
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            if (request.getClinicIdsList().isEmpty()) throw new DomainException("Clinic IDs are required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getDoctorId());
            List<UUID> clinicIds = request.getClinicIdsList().stream()
                    .filter(raw -> raw != null && !raw.isBlank())
                    .map(raw -> UUID.fromString(raw.trim()))
                    .collect(Collectors.toList());

            doctorUseCase.addClinicToDoctor(UUID.fromString(request.getDoctorId()), clinicIds);

            observer.onNext(AddClinicResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Clinics added successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void removeClinic(RemoveClinicRequest request,
                             StreamObserver<RemoveClinicResponse> observer) {
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            if (request.getClinicIdsList().isEmpty()) throw new DomainException("Clinic IDs are required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getDoctorId());
            List<UUID> clinicIds = request.getClinicIdsList().stream()
                    .filter(raw -> raw != null && !raw.isBlank())
                    .map(raw -> UUID.fromString(raw.trim()))
                    .collect(Collectors.toList());

            doctorUseCase.removeClinicFromDoctor(UUID.fromString(request.getDoctorId()), clinicIds);

            observer.onNext(RemoveClinicResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Clinics removed successfully")
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
                    .setMessage("Doctor deleted successfully")
                    .build());
            observer.onCompleted();
        });
    }

    private static final UUID NIL_UUID = new UUID(0, 0);

    @Override
    public void updateLocation(UpdateLocationRequest request,
                               StreamObserver<UpdateLocationResponse> observer) {
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
                // Verify they are indeed an individual doctor (no clinics)
                Doctor doc = doctorRepo.findById(doctorId)
                        .orElseThrow(() -> new DomainException("Doctor not found", Status.NOT_FOUND));
                if (doc.getClinicIds() != null && !doc.getClinicIds().isEmpty()) {
                    throw new DomainException("Clinic ID is required for clinic-affiliated doctors. Location cannot be updated as an individual.", Status.INVALID_ARGUMENT);
                }
            }

            doctorUseCase.updateDoctorLocation(doctorId, clinicId, request.getLocationText());

            observer.onNext(UpdateLocationResponse.newBuilder()
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
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            if (request.getWorkingDaysList().isEmpty()) throw new DomainException("Working days are required", Status.INVALID_ARGUMENT);
            if (request.getStartTime().isBlank()) throw new DomainException("Start time is required", Status.INVALID_ARGUMENT);
            if (request.getEndTime().isBlank()) throw new DomainException("End time is required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getDoctorId());
            
            UUID doctorId = UUID.fromString(request.getDoctorId());
            UUID clinicId = NIL_UUID;
            if (!request.getClinicId().isBlank()) {
                clinicId = UUID.fromString(request.getClinicId());
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
    public void updateSchedule(UpdateScheduleRequest request,
                               StreamObserver<UpdateScheduleResponse> observer) {
        handle(observer, () -> {
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            if (request.getWorkingDaysList().isEmpty()) throw new DomainException("Working days are required", Status.INVALID_ARGUMENT);
            if (request.getStartTime().isBlank()) throw new DomainException("Start time is required", Status.INVALID_ARGUMENT);
            if (request.getEndTime().isBlank()) throw new DomainException("End time is required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getDoctorId());

            UUID doctorId = UUID.fromString(request.getDoctorId());
            UUID clinicId = NIL_UUID;
            if (!request.getClinicId().isBlank()) {
                clinicId = UUID.fromString(request.getClinicId());
            }

            scheduleUseCase.updateSchedule(
                    doctorId,
                    clinicId,
                    new HashSet<>(request.getWorkingDaysList()),
                    LocalTime.parse(request.getStartTime()),
                    LocalTime.parse(request.getEndTime()),
                    request.getSlotDurationMinutes(),
                    request.getMaxAppointmentsDay()
            );
            observer.onNext(UpdateScheduleResponse.newBuilder()
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
            if (request.getLocationText().isBlank()) throw new DomainException("Location text is required", Status.INVALID_ARGUMENT);
            List<Doctor> doctors = doctorUseCase.getDoctorsByLocationText(request.getLocationText());
            List<DoctorMessage> doctorsMsg = doctors.stream().map(MapperClass::toMsg).toList();
            observer.onNext(NearbyDoctorsResponse.newBuilder()
                    .addAllDoctors(doctorsMsg)
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void getDoctorsByLocation(ByLocationRequest request,
                                     StreamObserver<ByLocationResponse> observer) {
        handle(observer, () -> {
            if (request.getGeohashPrefix().isBlank()) throw new DomainException("Geohash prefix is required", Status.INVALID_ARGUMENT);
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
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            if (request.getPatientId().isBlank()) throw new DomainException("Patient ID is required", Status.INVALID_ARGUMENT);
            if (request.getDate().isBlank()) throw new DomainException("Date is required", Status.INVALID_ARGUMENT);
            if (request.getTime().isBlank()) throw new DomainException("Time is required", Status.INVALID_ARGUMENT);

            UUID doctorId = UUID.fromString(request.getDoctorId());
            UUID clinicId = NIL_UUID;
            if (!request.getClinicId().isBlank()) {
                clinicId = UUID.fromString(request.getClinicId());
            } else {
                Doctor doc = doctorRepo.findById(doctorId)
                        .orElseThrow(() -> new DomainException("Doctor not found", Status.NOT_FOUND));
                if (doc.getClinicIds() != null && !doc.getClinicIds().isEmpty()) {
                    throw new DomainException("Clinic ID is required for clinic-affiliated doctors", Status.INVALID_ARGUMENT);
                }
            }

            UUID id = appointmentsUseCase.createAppointment(
                    doctorId,
                    UUID.fromString(request.getPatientId()),
                    clinicId,
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
            if (request.getAppointmentId().isBlank()) throw new DomainException("Appointment ID is required", Status.INVALID_ARGUMENT);
            if (request.getPatientId().isBlank()) throw new DomainException("Patient ID is required", Status.INVALID_ARGUMENT);

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
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            if (request.getDate().isBlank()) throw new DomainException("Date is required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getDoctorId());
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
            if (request.getDoctorId().isBlank()) throw new DomainException("Doctor ID is required", Status.INVALID_ARGUMENT);
            if (request.getDate().isBlank()) throw new DomainException("Date is required", Status.INVALID_ARGUMENT);

            ensureSelf(request.getDoctorId());
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
            if (request.getPatientId().isBlank()) throw new DomainException("Patient ID is required", Status.INVALID_ARGUMENT);
            if (request.getDate().isBlank()) throw new DomainException("Date is required", Status.INVALID_ARGUMENT);

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
            if (request.getAppointmentId().isBlank()) throw new DomainException("Appointment ID is required", Status.INVALID_ARGUMENT);

            // Load current state from DB
            Appointment current = appointmentRepo.findById(
                            UUID.fromString(request.getAppointmentId()))
                    .orElseThrow(() -> new DomainException("Appointment not found", Status.NOT_FOUND));

            ensureSelf(current.getDoctorId().toString());

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
            if (request.getAppointmentId().isBlank()) throw new DomainException("Appointment ID is required", Status.INVALID_ARGUMENT);

            // Load current state — we need current status + current date as oldDate
            Appointment current = appointmentRepo.findById(
                            UUID.fromString(request.getAppointmentId()))
                    .orElseThrow(() -> new DomainException("Appointment not found", Status.NOT_FOUND));

            ensureSelf(current.getDoctorId().toString());

            appointmentsUseCase.postponeAppointment(current);
            observer.onNext(AppointmentActionResponse.newBuilder()
                    .setSuccess(true).setMessage("Postponed").build());
            observer.onCompleted();
        });
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Override
    public void generateToken(TokenRequest request,
                              StreamObserver<TokenResponse> observer) {
        handle(observer, () -> {
            String email = EMAIL_KEY.get();
            String password = PASSWORD_KEY.get();

            if (email == null || password == null) {
                throw new DomainException("Credentials missing from basic auth", Status.UNAUTHENTICATED);
            }

            TokenResponse response = doctorUseCase.loginDoctor(email, password);
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

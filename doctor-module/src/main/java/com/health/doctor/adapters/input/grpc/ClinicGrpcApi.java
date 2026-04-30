package com.health.doctor.adapters.input.grpc;

import com.health.common.exception.DomainException;
import com.health.doctor.application.usecase.interfaces.ClinicInterface;
import com.health.doctor.application.usecase.interfaces.DoctorInterface;
import com.health.doctor.domain.model.Clinic;
import com.health.doctor.domain.model.Doctor;
import com.health.doctor.mapper.MapperClass;
import com.health.grpc.auth.ForgotPasswordRequest;
import com.health.grpc.auth.ForgotPasswordResponse;
import com.health.grpc.auth.ResetPasswordRequest;
import com.health.grpc.auth.ResetPasswordResponse;
import com.health.grpc.auth.TokenRequest;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.clinic.*;
import com.health.grpc.common.ClinicMessage;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micronaut.grpc.annotation.GrpcService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.health.common.auth.GrpcAuthInterceptor.*;

@Slf4j
@GrpcService
public class ClinicGrpcApi extends ClinicGrpcServiceGrpc.ClinicGrpcServiceImplBase {

    private final ClinicInterface clinicUseCase;
    private final DoctorInterface doctorUseCase;

    public ClinicGrpcApi(ClinicInterface clinicUseCase, DoctorInterface doctorUseCase) {
        this.clinicUseCase = clinicUseCase;
        this.doctorUseCase = doctorUseCase;
    }

    private void ensureClinic(String requestedId) {
        String role = ROLE_KEY.get();
        if (role == null || !role.equalsIgnoreCase("Clinic"))
            throw new SecurityException("Access denied: Clinics only.");
        String authId = USER_ID_KEY.get();
        if (authId == null || !authId.equals(requestedId))
            throw new SecurityException("Access denied: you can only access your own data.");
    }

    @Override
    public void generateClinicToken(TokenRequest request, StreamObserver<TokenResponse> observer) {
        handle(observer, () -> {
            String email = EMAIL_KEY.get();
            String password = PASSWORD_KEY.get();
            if (email == null || password == null) {
                throw new DomainException("Credentials missing from basic auth", Status.UNAUTHENTICATED);
            }
            TokenResponse response = clinicUseCase.loginClinic(email, password);
            observer.onNext(response);
            observer.onCompleted();
        });
    }

    @Override
    public void createClinic(CreateClinicRequest request, StreamObserver<CreateClinicResponse> observer) {
        handle(observer, () -> {
            if (request.getName().isBlank()) throw new DomainException("Clinic Name is required", Status.INVALID_ARGUMENT);
            if (request.getLocationText().isBlank()) throw new DomainException("Location is required", Status.INVALID_ARGUMENT);

            UUID id = clinicUseCase.createClinic(request.getName(), request.getLocationText(), request.getEmail(), request.getPassword(), request.getPhone());
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
    public void updateClinic(UpdateClinicRequest request, StreamObserver<UpdateClinicResponse> observer) {
        handle(observer, () -> {
            if (request.getClinicId().isBlank()) throw new DomainException("Clinic ID is required", Status.INVALID_ARGUMENT);
            if (request.getName().isBlank()) throw new DomainException("Name is required", Status.INVALID_ARGUMENT);

            ensureClinic(request.getClinicId());
            clinicUseCase.updateClinic(UUID.fromString(request.getClinicId()), request.getName());

            observer.onNext(UpdateClinicResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Clinic updated successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void deleteClinic(DeleteClinicRequest request, StreamObserver<DeleteClinicResponse> observer) {
        handle(observer, () -> {
            if (request.getClinicId().isBlank()) throw new DomainException("Clinic ID is required", Status.INVALID_ARGUMENT);

            ensureClinic(request.getClinicId());
            clinicUseCase.deleteClinic(UUID.fromString(request.getClinicId()));

            observer.onNext(DeleteClinicResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Clinic deleted successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void updateClinicLocation(UpdateClinicLocationRequest request, StreamObserver<UpdateClinicLocationResponse> observer) {
        handle(observer, () -> {
            if (request.getClinicId().isBlank()) throw new DomainException("Clinic ID is required", Status.INVALID_ARGUMENT);
            if (request.getLocationText().isBlank()) throw new DomainException("Location text is required", Status.INVALID_ARGUMENT);

            ensureClinic(request.getClinicId());
            clinicUseCase.updateClinicLocation(UUID.fromString(request.getClinicId()), request.getLocationText());

            observer.onNext(UpdateClinicLocationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Clinic location updated successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void getDoctorsByClinic(GetByClinicRequest request, StreamObserver<GetByClinicResponse> observer) {
        handle(observer, () -> {
            if (request.getClinicId().isBlank()) throw new DomainException("Clinic ID is required", Status.INVALID_ARGUMENT);
            List<Doctor> doctors = doctorUseCase.getDoctorsByClinic(UUID.fromString(request.getClinicId()));
            observer.onNext(GetByClinicResponse.newBuilder()
                    .addAllDoctors(doctors.stream().map(MapperClass::toMsg).collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void getClinicsByLocation(com.health.grpc.common.LocationSearchRequest request, StreamObserver<FindNearByClinicResponse> observer) {
        handle(observer, () -> {
            if (request.getLocation().isBlank()) throw new DomainException("Location is required", Status.INVALID_ARGUMENT);
            List<Clinic> clinics = clinicUseCase.getClinicsByLocation(request.getLocation());
            observer.onNext(FindNearByClinicResponse.newBuilder()
                    .addAllClinics(clinics.stream()
                            .map(c -> ClinicMessage.newBuilder()
                                    .setClinicId(c.getId().toString())
                                    .setName(c.getName())
                                    .setLocationText(c.getLocationText())
                                    .build())
                            .collect(Collectors.toList()))
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void searchClinics(SearchClinicRequest request, StreamObserver<SearchClinicResponse> observer) {
        handle(observer, () -> {
            String name = request.getName();
            int page = request.getPage();
            int size = request.getSize() > 0 ? request.getSize() : 10;

            List<Clinic> clinics = clinicUseCase.searchClinics(name, page, size);
            long totalElements = clinicUseCase.countClinicsByName(name);
            int totalPages = (int) Math.ceil((double) totalElements / size);

            observer.onNext(SearchClinicResponse.newBuilder()
                    .addAllClinics(clinics.stream()
                            .map(c -> ClinicMessage.newBuilder()
                                    .setClinicId(c.getId().toString())
                                    .setName(c.getName())
                                    .setLocationText(c.getLocationText())
                                    .build())
                            .collect(Collectors.toList()))
                    .setTotalPages(totalPages)
                    .setTotalElements(totalElements)
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request, StreamObserver<ForgotPasswordResponse> observer) {
        handle(observer,() -> {
            if(request.getEmail().isBlank()) throw new DomainException("Email is required", Status.INVALID_ARGUMENT);
            String token = clinicUseCase.forgotPassword(request.getEmail());
            observer.onNext(ForgotPasswordResponse.newBuilder()
                    .setSuccess(true)
                    .setResetToken(token)
                    .setMessage("Reset token generated successfully")
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void resetPassword(ResetPasswordRequest request, StreamObserver<ResetPasswordResponse> observer) {
        handle(observer, () -> {
            if (request.getNewPassword().length() < 8) throw new DomainException("Password must be at least 8 characters", Status.INVALID_ARGUMENT);

            clinicUseCase.resetPassword(request.getNewPassword());
            observer.onNext(ResetPasswordResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Password Reset Successfully")
                    .build());
            observer.onCompleted();
        });
        }

    private <T> void handle(StreamObserver<T> observer, Runnable action) {
        try {
            action.run();
        } catch (com.health.common.exception.DomainException e) {
            log.warn("Domain error: {}", e.getMessage());
            observer.onError(e.getGrpcStatus()
                    .withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Unexpected gRPC error", e);
            observer.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getClass().getSimpleName() + " - " + e.getMessage())
                    .asRuntimeException());
        }
    }
}

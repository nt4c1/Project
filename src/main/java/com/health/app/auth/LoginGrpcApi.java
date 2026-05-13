package com.health.app.auth;

import com.health.grpc.auth.TokenResponse;
import com.health.grpc.login.LoginServiceGrpc;
import com.health.grpc.login.LoginRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micronaut.grpc.annotation.GrpcService;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;

import static com.health.common.auth.BasicAuthInterceptor.EMAIL_KEY;
import static com.health.common.auth.BasicAuthInterceptor.PASSWORD_KEY;
import static com.health.common.auth.BasicAuthInterceptor.AUTH_ROLE_KEY;

@Slf4j
@GrpcService
public class LoginGrpcApi extends LoginServiceGrpc.LoginServiceImplBase {

    //Circular Dependency Resolving
    private final Provider<com.health.admin.application.AdminInterface> adminUseCase;
    private final Provider<com.health.doctor.application.usecase.interfaces.ClinicInterface> clinicUseCase;
    private final Provider<com.health.doctor.application.usecase.interfaces.DoctorInterface> doctorUseCase;
    private final Provider<com.health.patient.application.PatientInterface> patientUseCase;

    public LoginGrpcApi(Provider<com.health.admin.application.AdminInterface> adminUseCase,
                        Provider<com.health.doctor.application.usecase.interfaces.ClinicInterface> clinicUseCase,
                        Provider<com.health.doctor.application.usecase.interfaces.DoctorInterface> doctorUseCase,
                        Provider<com.health.patient.application.PatientInterface> patientUseCase) {
        this.adminUseCase = adminUseCase;
        this.clinicUseCase = clinicUseCase;
        this.doctorUseCase = doctorUseCase;
        this.patientUseCase = patientUseCase;
    }

    @Override
    public void login(LoginRequest request, StreamObserver<TokenResponse> observer) {
        String email = EMAIL_KEY.get();
        String password = PASSWORD_KEY.get();

        if (email == null || password == null) {
            observer.onError(Status.UNAUTHENTICATED.withDescription("Credentials missing").asRuntimeException());
            return;
        }

        String role = AUTH_ROLE_KEY.get();
        if (role == null) {
             observer.onError(Status.INTERNAL.withDescription("Internal Error: Role missing from context").asRuntimeException());
             return;
        }

        try {
            TokenResponse response;
            switch (role) {
                case "Admin" -> response = adminUseCase.get().loginAdmin(email, password);
                case "Clinic" -> response = clinicUseCase.get().loginClinic(email, password);
                case "Doctor" -> response = doctorUseCase.get().loginDoctor(email, password);
                case "Patient" -> response = patientUseCase.get().loginPatient(email, password);
                default -> {
                    observer.onError(Status.INVALID_ARGUMENT.withDescription("Invalid role").asRuntimeException());
                    return;
                }
            }
            observer.onNext(response);
            observer.onCompleted();
        } catch (Exception e) {
            log.error("Login failed for role {}: {}", role, e.getMessage());
            observer.onError(Status.UNAUTHENTICATED.
                    withDescription(e.getMessage()).
                    asRuntimeException());
        }
    }
}

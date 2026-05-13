package com.health.common.auth;

import io.grpc.Metadata;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GrpcAuthConstants {

    private GrpcAuthConstants (){}

    public static final Metadata.Key<String> AUTHORIZATION_HEADER_DOCTOR =
            Metadata.Key.of("Authorization_Doctor", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> AUTHORIZATION_HEADER_PATIENT =
            Metadata.Key.of("Authorization_Patient", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> AUTHORIZATION_HEADER_CLINIC =
            Metadata.Key.of("Authorization_Clinic", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> AUTHORIZATION_HEADER_ADMIN =
            Metadata.Key.of("Authorization_Admin", Metadata.ASCII_STRING_MARSHALLER);
    
    //----------------------------------Test Bypass Header---------------------------------------------------------------------------------------
    public static final Metadata.Key<String> TEST_BYPASS_HEADER =
            Metadata.Key.of("test-bypass-token", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> TEST_EMAIL_HEADER =
            Metadata.Key.of("test-email", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> TEST_PASSWORD_HEADER =
            Metadata.Key.of("test-password", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> TEST_ROLE_HEADER =
            Metadata.Key.of("test-role", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> TEST_USER_ID_HEADER =
            Metadata.Key.of("test-user-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> TEST_TOKEN_TYPE_HEADER =
            Metadata.Key.of("test-token-type", Metadata.ASCII_STRING_MARSHALLER);
    
    //--------------------------------------------------------------------------------------------------------------------------------------

    public static final Map<Metadata.Key<String>, String> HEADER_TO_ROLE = Map.of(
            AUTHORIZATION_HEADER_DOCTOR, "Doctor",
            AUTHORIZATION_HEADER_PATIENT, "Patient",
            AUTHORIZATION_HEADER_CLINIC, "Clinic",
            AUTHORIZATION_HEADER_ADMIN, "Admin"
    );

    public static final Set<String> LOGIN_METHODS = Set.of(
            "Login"
    );

    public static final Set<String> PUBLIC_METHODS = Set.of(
            "CreateDoctor",
            "RegisterPatient",
            "CreateClinic",
            "ForgotPassword",
            "GetDoctorsByLocation",
            "GetDoctorSchedule",
            "GetDoctorReviews",
            "GetDoctor",
            "RefreshToken",
            "GetClinicsByLocation",
            "SearchClinics"
    );

    public static final Set<String> BYPASS_METHODS = Stream
            .concat(PUBLIC_METHODS.stream(),LOGIN_METHODS.stream())
            .collect(Collectors.toUnmodifiableSet());


}

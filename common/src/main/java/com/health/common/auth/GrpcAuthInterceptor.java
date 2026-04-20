package com.health.common.auth;

import io.grpc.*;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GrpcAuthInterceptor implements ServerInterceptor {

    public static final Context.Key<String> USER_ID_KEY = Context.key("user-id");
    public static final Context.Key<String> ROLE_KEY    = Context.key("role");

    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final JwtProvider jwtProvider;

    public GrpcAuthInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        log.debug("Intercepting call: {}", methodName);

        if (isPublicMethod(methodName)) {
            return next.startCall(call, headers);
        }

        String token = headers.get(AUTHORIZATION_HEADER);

        if (token == null || !token.startsWith("Bearer ")) {
            call.close(Status.UNAUTHENTICATED.withDescription("Authorization token is required"), headers);
            return new ServerCall.Listener<>() {};
        }

        token = token.substring(7);

        try {
            if (jwtProvider.isValid(token)) {
                String userId = jwtProvider.extractUserId(token);
                String role   = jwtProvider.extractRole(token);

                Context context = Context.current()
                        .withValue(USER_ID_KEY, userId)
                        .withValue(ROLE_KEY, role);

                return Contexts.interceptCall(context, call, headers, next);
            }
        } catch (Exception e) {
            log.warn("Authentication failed for {}: {}", methodName, e.getMessage());
        }

        call.close(Status.UNAUTHENTICATED.withDescription("Invalid or expired token"), headers);
        return new ServerCall.Listener<>() {};
    }

    private boolean isPublicMethod(String methodName) {
        return methodName.endsWith("/GenerateToken") ||
               methodName.endsWith("/CreateDoctor") ||
               methodName.endsWith("/ValidateDoctorToken") ||
               methodName.endsWith("/PatientLogin") ||
               methodName.endsWith("/RegisterPatient") ||
                methodName.endsWith("/CreateClinic") ||
               methodName.endsWith("/ValidatePatientToken");
    }
}

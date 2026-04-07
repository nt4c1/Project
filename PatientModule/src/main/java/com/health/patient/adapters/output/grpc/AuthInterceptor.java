package com.health.patient.adapters.output.grpc;

import com.health.infrastructure.JwtProvider;
import io.grpc.*;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AuthInterceptor implements ServerInterceptor {

    public static final Context.Key<String> USER_ID_KEY = Context.key("userId");
    public static final Context.Key<String> USER_ROLE_KEY = Context.key("userRole");

    private final JwtProvider jwtProvider;

    public AuthInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();

        if (methodName.endsWith("PatientLogin") || 
            methodName.endsWith("RegisterPatient") || 
            methodName.endsWith("CreatePatient") ||
            methodName.endsWith("ValidatePatientToken")) {
            return next.startCall(call, headers);
        }

        String authHeader = headers.get(
                Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
        );

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.close(Status.UNAUTHENTICATED.withDescription("Authorization token is required for this action"), headers);
            return new ServerCall.Listener<>() {};
        }

        String token = authHeader.substring(7);

        try {
            if (!jwtProvider.isValid(token)) {
                call.close(Status.UNAUTHENTICATED.withDescription("Invalid or expired token"), headers);
                return new ServerCall.Listener<>() {};
            }

            String userId = jwtProvider.extractUserId(token);
            String role = jwtProvider.extractRole(token);

            Context context = Context.current()
                    .withValue(USER_ID_KEY, userId)
                    .withValue(USER_ROLE_KEY, role);

            return Contexts.interceptCall(context, call, headers, next);

        } catch (Exception e) {
            log.error("Auth error during token validation", e);
            call.close(Status.UNAUTHENTICATED.withDescription("Token validation failed"), headers);
            return new ServerCall.Listener<>() {};
        }
    }
}
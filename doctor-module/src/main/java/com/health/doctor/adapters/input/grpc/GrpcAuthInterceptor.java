package com.health.doctor.adapters.input.grpc;



import com.health.doctor.infrastructure.JwtProvider;
import io.grpc.*;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GrpcAuthInterceptor implements ServerInterceptor {

    public static final Context.Key<String> USER_ID_KEY = Context.key("userId");
    public static final Context.Key<String> USER_ROLE_KEY = Context.key("userRole");

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

        // If it's not a doctor service call, let it pass to the next interceptor
        if (!methodName.startsWith("com.health.grpc.doctor.DoctorGrpcService/")) {
            return next.startCall(call, headers);
        }

        if (methodName.endsWith("DoctorLogin") ||
                methodName.endsWith("CreateDoctor") ||
                methodName.endsWith("ValidateDoctorToken") ||
                methodName.endsWith("GetNearbyDoctors") ||
                methodName.endsWith("GetDoctorsByLocation") ||
                methodName.endsWith("GetDoctorSchedule") ||
                methodName.endsWith("DoctorExists") ||
                methodName.endsWith("GetDoctorsByClinic")) {
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
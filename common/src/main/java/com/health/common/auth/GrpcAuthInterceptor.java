package com.health.common.auth;

import io.grpc.*;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

@Slf4j
@Singleton
public class GrpcAuthInterceptor implements ServerInterceptor {

    public static final Context.Key<String> USER_ID_KEY  = Context.key("user-id");
    public static final Context.Key<String> ROLE_KEY     = Context.key("role");
    public static final Context.Key<String> EMAIL_KEY    = Context.key("email");
    public static final Context.Key<String> PASSWORD_KEY = Context.key("password");

    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final Set<String> LOGIN_METHODS = Set.of(
            "GenerateToken",
            "PatientLogin"
    );

    private static final Set<String> PUBLIC_METHODS = Set.of(
            "CreateDoctor",
            "RegisterPatient",
            "CreateClinic"
    );

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

        String authHeader = headers.get(AUTHORIZATION_HEADER);

        if (isLoginMethod(methodName)) {
            return handleBasicAuth(call, headers, next, authHeader);
        }

        if (isPublicMethod(methodName)) {
            return next.startCall(call, headers);
        }

        return handleBearerAuth(call, headers, next, authHeader, methodName);
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> handleBasicAuth(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next,
            String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            log.debug("Missing Basic auth header");
            return closeCall(call, headers, Status.UNAUTHENTICATED
                    .withDescription("Basic authentication required"));
        }

        try {
            String[] credentials = decodeBasicAuth(authHeader);

            Context context = Context.current()
                    .withValue(EMAIL_KEY, credentials[0])
                    .withValue(PASSWORD_KEY, credentials[1]);

            return Contexts.interceptCall(context, call, headers, next);

        } catch (Exception e) {
            log.debug("Invalid Basic auth header: {}", e.getMessage());
            return closeCall(call, headers, Status.UNAUTHENTICATED
                    .withDescription("Invalid Basic authentication header"));
        }
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> handleBearerAuth(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next,
            String authHeader,
            String methodName) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("Missing Bearer token for method: {}", methodName);
            return closeCall(call, headers, Status.UNAUTHENTICATED
                    .withDescription("Bearer token required"));
        }

        try {
            String token = authHeader.substring(7);

            if (!jwtProvider.isValid(token)) {
                log.warn("Invalid or expired token for method: {}", methodName);
                return closeCall(call, headers, Status.UNAUTHENTICATED
                        .withDescription("Invalid or expired token"));
            }

            Context context = Context.current()
                    .withValue(USER_ID_KEY, jwtProvider.extractUserId(token))
                    .withValue(ROLE_KEY, jwtProvider.extractRole(token));

            return Contexts.interceptCall(context, call, headers, next);

        } catch (Exception e) {
            log.warn("Authentication failed for {}: {}", methodName, e.getMessage(), e);
            return closeCall(call, headers, Status.UNAUTHENTICATED
                    .withDescription("Authentication failed"));
        }
    }

    private String[] decodeBasicAuth(String authHeader) {
        String base64Credentials = authHeader.substring(6);
        byte[] decoded = Base64.getDecoder().decode(base64Credentials);
        String credentials = new String(decoded, StandardCharsets.UTF_8);
        String[] values = credentials.split(":", 2);

        if (values.length != 2) {
            throw new IllegalArgumentException("Invalid Basic auth format");
        }
        return values;
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> closeCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            Status status) {
        call.close(status, headers);
        return new ServerCall.Listener<>() {};
    }

    private boolean isLoginMethod(String methodName) {
        return LOGIN_METHODS.stream().anyMatch(methodName::endsWith);
    }

    private boolean isPublicMethod(String methodName) {
        return PUBLIC_METHODS.stream().anyMatch(methodName::endsWith);
    }
}